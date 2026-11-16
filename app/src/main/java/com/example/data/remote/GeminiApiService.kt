package com.example.data.remote

import com.example.data.generator.AppGenerationPrompt
import com.example.data.preferences.ApiKeyVerificationStatus
import com.example.data.preferences.ApiUsageHeaderParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiApiException(
    val statusCode: Int,
    override val message: String,
    cause: Throwable? = null
) : IOException(message, cause)

@Serializable
data class GeminiGenerateResult(
    val text: String,
    val model: String,
    val tokensUsed: Long? = null,
    val httpStatusCode: Int = 200
)

/**
 * Official Google Generative Language REST API Client for Google Gemini.
 * Direct endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={GEMINI_API_KEY}
 * Streaming endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse&key={GEMINI_API_KEY}
 * Verification endpoint: GET https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key={GEMINI_API_KEY}
 */
object GeminiApiService {

    const val MODEL_FLASH_25 = "gemini-2.5-flash"
    const val MODEL_PRO_25 = "gemini-2.5-pro"
    const val MODEL_FLASH_20 = "gemini-2.0-flash"
    const val MODEL_FLASH_15 = "gemini-1.5-flash"

    val supportedModels = listOf(
        MODEL_FLASH_25,
        MODEL_PRO_25,
        MODEL_FLASH_20,
        MODEL_FLASH_15
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Real Status Verification (No False Positives):
     * Never considers a key valid unless a real lightweight GET request to Google's official API returns HTTP 200.
     */
    suspend fun verifyApiKey(
        apiKey: String,
        httpClient: OkHttpClient = client
    ): ApiKeyVerificationStatus = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext ApiKeyVerificationStatus.Invalid("🟡 Gemini: API Key Required")
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=$cleanKey")
            .get()
            .build()

        val startedAt = System.currentTimeMillis()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val bodyString = response.body?.string().orEmpty()
                val parsedHeaders = ApiUsageHeaderParser.parse(response.headers)
                when (response.code) {
                    200 -> {
                        val firstModel = parseFirstModelName(bodyString) ?: MODEL_FLASH_25
                        ApiKeyVerificationStatus.Valid(
                            provider = "Google Gemini",
                            detail = "Verified (HTTP 200) • $firstModel",
                            latencyMs = latency,
                            httpStatusCode = 200,
                            usageHeaders = parsedHeaders
                        )
                    }
                    400, 403 -> {
                        val errorDetail = parseErrorMessage(bodyString)
                            ?: "Invalid API key or API not enabled in Google Cloud Console."
                        ApiKeyVerificationStatus.Invalid(
                            errorMessage = "🔴 Gemini: Invalid Key (HTTP ${response.code}) • $errorDetail",
                            latencyMs = latency,
                            httpStatusCode = response.code,
                            usageHeaders = parsedHeaders
                        )
                    }
                    429 -> {
                        ApiKeyVerificationStatus.Invalid(
                            errorMessage = "🔴 Gemini: Quota Exceeded (HTTP 429) • Rate limit reached in Google AI Studio.",
                            latencyMs = latency,
                            httpStatusCode = 429,
                            usageHeaders = parsedHeaders
                        )
                    }
                    else -> {
                        val errorDetail = parseErrorMessage(bodyString) ?: "HTTP ${response.code}"
                        ApiKeyVerificationStatus.Invalid(
                            errorMessage = "🔴 Gemini Error (HTTP ${response.code}) • $errorDetail",
                            latencyMs = latency,
                            httpStatusCode = response.code,
                            usageHeaders = parsedHeaders
                        )
                    }
                }
            }
        }.getOrElse { err ->
            ApiKeyVerificationStatus.Invalid(
                errorMessage = "🔴 Gemini: Network Error • ${err.localizedMessage ?: "Check Internet connection"}",
                latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            )
        }
    }

    /**
     * Executes code generation through Google's streaming REST endpoint and emits accumulated
     * text as chunks arrive. Cancelling the coroutine cancels the underlying HTTP call.
     */
    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        model: String = MODEL_FLASH_25,
        systemInstruction: String = AppGenerationPrompt.SYSTEM_PROMPT,
        temperature: Float = 0.2f,
        maxOutputTokens: Int = 8192,
        onTokenChunk: ((String) -> Unit)? = null,
        httpClient: OkHttpClient = client
    ): GeminiGenerateResult {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw GeminiApiException(401, "Google Gemini API key is missing. Configure it in Credentials tab.")
        }

        val targetModel = if (model.startsWith("gemini-")) model else MODEL_FLASH_25
        val requestPayload = buildRequestJson(
            prompt = prompt,
            systemInstruction = systemInstruction,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens
        )
        val endpointUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("generativelanguage.googleapis.com")
            .addPathSegments("v1beta/models/$targetModel:streamGenerateContent")
            .addQueryParameter("alt", "sse")
            .addQueryParameter("key", cleanKey)
            .build()

        val request = Request.Builder()
            .url(endpointUrl)
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .header("User-Agent", "AIAppStudio-Android/1.0")
            .post(requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val call = httpClient.newCall(request)

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            GeminiApiException(500, "Failed to connect to Google Gemini API: ${e.localizedMessage ?: "Unknown error"}", e)
                        )
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use { httpResponse ->
                            val body = httpResponse.body
                            if (!httpResponse.isSuccessful) {
                                val bodyString = body?.string().orEmpty()
                                val errorMsg = parseErrorMessage(bodyString) ?: "HTTP ${httpResponse.code}"
                                val message = when (httpResponse.code) {
                                    400, 403 -> "Gemini API rejected request ($errorMsg). Ensure your key is from aistudio.google.com/apikey."
                                    429 -> "Gemini Quota Exceeded (HTTP 429). Daily rate limit reached in Google AI Studio."
                                    else -> "Gemini API failed with HTTP ${httpResponse.code}: $errorMsg"
                                }
                                throw GeminiApiException(httpResponse.code, message)
                            }
                            val source = body?.source()
                                ?: throw GeminiApiException(500, "Gemini returned an empty response body.")
                            val accumulated = StringBuilder()
                            var tokensUsed: Long? = null
                            val reader: BufferedReader = source.inputStream().bufferedReader(Charsets.UTF_8)
                            reader.use { bufferedReader ->
                                while (true) {
                                    if (!continuation.isActive) {
                                        call.cancel()
                                        throw CancellationException("Gemini generation was cancelled.")
                                    }
                                    val line = bufferedReader.readLine() ?: break
                                    if (!line.startsWith("data:")) continue
                                    val data = line.removePrefix("data:").trim()
                                    if (data.isEmpty() || data == "[DONE]") continue
                                    val text = parseCandidatesText(data)
                                    if (text.isNotEmpty()) {
                                        accumulated.append(text)
                                        onTokenChunk?.invoke(accumulated.toString())
                                    }
                                    parseTotalTokenCount(data)?.let { tokensUsed = it }
                                    parseErrorMessage(data)?.let { errorMessage ->
                                        throw GeminiApiException(httpResponse.code, errorMessage)
                                    }
                                }
                            }

                            val generatedText = accumulated.toString()
                            if (generatedText.isBlank()) {
                                throw GeminiApiException(200, "Gemini returned an empty candidate response for model $targetModel.")
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    GeminiGenerateResult(
                                        text = generatedText,
                                        model = targetModel,
                                        tokensUsed = tokensUsed,
                                        httpStatusCode = httpResponse.code
                                    )
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                when (t) {
                                    is CancellationException -> t
                                    is GeminiApiException -> t
                                    else -> GeminiApiException(500, "Failed to read Gemini response: ${t.localizedMessage ?: "Unknown error"}", t)
                                }
                            )
                        }
                    }
                }
            })
        }
    }

    private fun buildRequestJson(
        prompt: String,
        systemInstruction: String,
        temperature: Float,
        maxOutputTokens: Int
    ): String {
        val escapedPrompt = escapeJson(prompt)
        val escapedSys = escapeJson(systemInstruction)
        return """
        {
          "contents": [
            {
              "role": "user",
              "parts": [
                { "text": "$escapedPrompt" }
              ]
            }
          ],
          "systemInstruction": {
            "parts": [
              { "text": "$escapedSys" }
            ]
          },
          "generationConfig": {
            "temperature": $temperature,
            "maxOutputTokens": $maxOutputTokens
          }
        }
        """.trimIndent()
    }

    private fun escapeJson(input: String): String =
        input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun parseCandidatesText(bodyString: String): String = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return ""
        val firstCandidate = candidates.firstOrNull()?.jsonObject ?: return ""
        val content = firstCandidate["content"]?.jsonObject ?: return ""
        val parts = content["parts"]?.jsonArray ?: return ""
        parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
    }.getOrDefault("")

    private fun parseTotalTokenCount(bodyString: String): Long? = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val usage = root["usageMetadata"]?.jsonObject ?: return null
        usage["totalTokenCount"]?.jsonPrimitive?.content?.toLongOrNull()
    }.getOrNull()

    private fun parseFirstModelName(bodyString: String): String? = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val models = root["models"]?.jsonArray ?: return null
        val first = models.firstOrNull()?.jsonObject ?: return null
        first["displayName"]?.jsonPrimitive?.content
            ?: first["name"]?.jsonPrimitive?.content?.removePrefix("models/")
    }.getOrNull()

    private fun parseErrorMessage(bodyString: String): String? = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val error = root["error"]?.jsonObject ?: return null
        error["message"]?.jsonPrimitive?.content
    }.getOrNull()
}
