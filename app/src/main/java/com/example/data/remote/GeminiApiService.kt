package com.example.data.remote

import com.example.data.generator.AppGenerationPrompt
import com.example.data.preferences.ApiKeyVerificationStatus
import com.example.data.preferences.ApiUsageHeaderParser
import kotlinx.coroutines.Dispatchers
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
import java.io.IOException
import java.util.concurrent.TimeUnit

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
            .readTimeout(60, TimeUnit.SECONDS)
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
            val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            ApiKeyVerificationStatus.Invalid(
                errorMessage = "🔴 Gemini: Network Error • ${err.localizedMessage ?: "Check Internet connection"}",
                latencyMs = latency
            )
        }
    }

    /**
     * Executes real code generation via Google Generative Language REST API:
     * POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={GEMINI_API_KEY}
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
    ): GeminiGenerateResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw GeminiApiException(401, "Google Gemini API key is missing. Configure it in Credentials tab.")
        }

        val requestPayload = buildRequestJson(
            prompt = prompt,
            systemInstruction = systemInstruction,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens
        )

        val targetModel = if (model.startsWith("gemini-")) model else MODEL_FLASH_25
        val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$cleanKey"

        val request = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .header("User-Agent", "AIAppStudio-Android/1.0")
            .post(requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(bodyString) ?: "HTTP ${response.code}"
                    when (response.code) {
                        400, 403 -> throw GeminiApiException(
                            statusCode = response.code,
                            message = "Gemini API rejected request ($errorMsg). Ensure your key is from aistudio.google.com/apikey."
                        )
                        429 -> throw GeminiApiException(
                            statusCode = 429,
                            message = "Gemini Quota Exceeded (HTTP 429). Daily rate limit reached in Google AI Studio."
                        )
                        else -> throw GeminiApiException(
                            statusCode = response.code,
                            message = "Gemini API failed with HTTP ${response.code}: $errorMsg"
                        )
                    }
                }

                val generatedText = parseCandidatesText(bodyString)
                if (generatedText.isBlank()) {
                    throw GeminiApiException(200, "Gemini returned an empty candidate response for model $targetModel.")
                }

                val tokensUsed = parseTotalTokenCount(bodyString)
                onTokenChunk?.invoke(generatedText)

                GeminiGenerateResult(
                    text = generatedText,
                    model = targetModel,
                    tokensUsed = tokensUsed,
                    httpStatusCode = response.code
                )
            }
        } catch (t: Throwable) {
            if (t is GeminiApiException) throw t
            throw GeminiApiException(500, "Failed to connect to Google Gemini API: ${t.localizedMessage ?: "Unknown error"}", t)
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
        parts.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
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
