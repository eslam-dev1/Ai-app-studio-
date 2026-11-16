package com.example.data.remote

import com.example.data.generator.AppGenerationPrompt
import com.example.data.preferences.ApiKeyVerificationStatus
import com.example.data.preferences.ApiUsageHeaderParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiApiException(
    val statusCode: Int,
    override val message: String,
    cause: Throwable? = null,
    val suggestedModel: String? = null
) : IOException(message, cause)

@Serializable
data class GeminiGenerateResult(
    val text: String,
    val model: String,
    val tokensUsed: Long? = null,
    val httpStatusCode: Int = 200,
    val finishReason: String? = null
)

/**
 * Official Google Generative Language REST API Client for Google Gemini.
 * Direct endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={GEMINI_API_KEY}
 * Verification / Discovery endpoint: GET https://generativelanguage.googleapis.com/v1beta/models?key={GEMINI_API_KEY}
 */
object GeminiApiService {

    const val MODEL_FLASH_38 = "gemini-3.8-flash"
    const val MODEL_PRO_38 = "gemini-3.8-pro"
    const val MODEL_FLASH_25 = "gemini-2.5-flash"
    const val MODEL_PRO_25 = "gemini-2.5-pro"

    val supportedModels = listOf(
        MODEL_FLASH_38,
        MODEL_PRO_38,
        MODEL_FLASH_25,
        MODEL_PRO_25
    )

    private val excludedModelTokens = listOf(
        "embedding",
        "imagen",
        "veo",
        "lyria",
        "tts",
        "audio",
        "aqa",
        "robotics",
        "computer-use",
        "deep-research",
        "live"
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
            .readTimeout(120, TimeUnit.SECONDS)
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
                        val firstModel = parseFirstModelName(bodyString) ?: MODEL_FLASH_38
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
     * Discovers live text/code-generation Gemini models from Google's official /v1beta/models endpoint
     * so model limit exhaustion (HTTP 429) or retired model IDs (HTTP 404) automatically switch to valid models.
     */
    suspend fun fetchAvailableGenerateModels(
        apiKey: String,
        httpClient: OkHttpClient = client
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext emptyList()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100&key=$cleanKey")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val bodyString = response.body?.string().orEmpty()
                parseAvailableGenerateModels(bodyString)
            }
        }.getOrDefault(emptyList())
    }

    internal fun parseAvailableGenerateModels(bodyString: String): List<String> = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val models = root["models"]?.jsonArray ?: return emptyList()
        val discovered = models.mapNotNull { element ->
            val obj = element.jsonObject
            val rawName = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val modelId = rawName.removePrefix("models/").trim()
            if (!modelId.startsWith("gemini-", ignoreCase = true)) return@mapNotNull null
            val lower = modelId.lowercase()
            if (excludedModelTokens.any { lower.contains(it) }) return@mapNotNull null

            val methods = (obj["supportedGenerationMethods"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.content }
                .orEmpty()
            if (methods.isNotEmpty() && "generateContent" !in methods) {
                return@mapNotNull null
            }
            modelId
        }.distinct()

        discovered.sortedWith(
            compareByDescending<String> { it == MODEL_FLASH_38 }
                .thenByDescending { modelVersionScore(it) }
                .thenByDescending { it.contains("flash", ignoreCase = true) }
                .thenBy { it.contains("lite", ignoreCase = true) }
                .thenBy { it.contains("preview", ignoreCase = true) || it.contains("exp", ignoreCase = true) }
                .thenBy { it.length }
        )
    }.getOrDefault(emptyList())

    private fun modelVersionScore(modelId: String): Double {
        val match = Regex("""gemini-(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(modelId)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    }

    /**
     * Executes real code generation via Google Generative Language REST API:
     * Streams live chunks via POST .../models/{model}:streamGenerateContent?alt=sse&key={GEMINI_API_KEY}
     * when onTokenChunk is provided, with automatic fallback to :generateContent if needed.
     */
    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        model: String = MODEL_FLASH_38,
        systemInstruction: String = AppGenerationPrompt.SYSTEM_PROMPT,
        temperature: Float = 0.2f,
        maxOutputTokens: Int = 32768,
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

        val cleanModel = model.removePrefix("models/").trim()
        val targetModel = if (cleanModel.startsWith("gemini-")) cleanModel else MODEL_FLASH_38

        if (onTokenChunk != null) {
            val streamAttempt = runCatching {
                streamGenerateContentInternal(
                    cleanKey = cleanKey,
                    targetModel = targetModel,
                    requestPayload = requestPayload,
                    onTokenChunk = onTokenChunk,
                    httpClient = httpClient
                )
            }
            streamAttempt.onSuccess { return@withContext it }
            val streamErr = streamAttempt.exceptionOrNull()
            if (streamErr is CancellationException) throw streamErr
            if (streamErr is GeminiApiException && streamErr.statusCode in setOf(400, 401, 403, 404, 429)) {
                throw streamErr
            }
        }

        generateContentUnaryInternal(
            cleanKey = cleanKey,
            targetModel = targetModel,
            requestPayload = requestPayload,
            onTokenChunk = onTokenChunk,
            httpClient = httpClient
        )
    }

    private suspend fun streamGenerateContentInternal(
        cleanKey: String,
        targetModel: String,
        requestPayload: String,
        onTokenChunk: (String) -> Unit,
        httpClient: OkHttpClient
    ): GeminiGenerateResult {
        val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:streamGenerateContent?alt=sse&key=$cleanKey"
        val request = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("User-Agent", "AIAppStudio-Android/1.0")
            .post(requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    throw mapHttpError(response.code, bodyString, targetModel)
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val body = response.body ?: throw GeminiApiException(200, "Empty response body from $targetModel.")

                if (contentType.contains("application/json")) {
                    val bodyString = body.string()
                    val generatedText = parseCandidatesText(bodyString)
                    if (generatedText.isBlank()) {
                        throw GeminiApiException(200, "Gemini returned an empty candidate response for model $targetModel.")
                    }
                    val tokensUsed = parseTotalTokenCount(bodyString)
                    val finishReason = parseFinishReason(bodyString)
                    onTokenChunk(generatedText)
                    return GeminiGenerateResult(
                        text = generatedText,
                        model = targetModel,
                        tokensUsed = tokensUsed,
                        httpStatusCode = response.code,
                        finishReason = finishReason
                    )
                }

                val accumulated = StringBuilder()
                var resolvedTokens: Long? = null
                var resolvedFinishReason: String? = null
                var lastEmitLength = 0

                body.charStream().buffered().use { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                        if (!trimmed.startsWith("data:")) continue

                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break

                        parseErrorMessage(data)?.let { errMsg ->
                            val errCode = parseErrorCode(data) ?: 500
                            throw GeminiApiException(
                                statusCode = errCode,
                                message = "Gemini stream error ($targetModel): $errMsg",
                                suggestedModel = extractSuggestedModelFromError(errMsg)
                            )
                        }

                        val deltaText = parseCandidatesText(data)
                        if (deltaText.isNotEmpty()) {
                            accumulated.append(deltaText)
                            if (accumulated.length - lastEmitLength >= 24 || deltaText.contains('\n')) {
                                lastEmitLength = accumulated.length
                                onTokenChunk(accumulated.toString())
                            }
                        }
                        parseTotalTokenCount(data)?.let { resolvedTokens = it }
                        parseFinishReason(data)?.let { resolvedFinishReason = it }
                    }
                }

                val finalContent = accumulated.toString()
                if (finalContent.isBlank()) {
                    throw GeminiApiException(200, "Gemini returned an empty stream response for model $targetModel.")
                }
                if (finalContent.length != lastEmitLength) {
                    onTokenChunk(finalContent)
                }

                return GeminiGenerateResult(
                    text = finalContent,
                    model = targetModel,
                    tokensUsed = resolvedTokens,
                    httpStatusCode = response.code,
                    finishReason = resolvedFinishReason
                )
            }
        } catch (ce: CancellationException) {
            runCatching { call.cancel() }
            throw ce
        } catch (ge: GeminiApiException) {
            throw ge
        } catch (t: Throwable) {
            throw GeminiApiException(500, "Failed to stream from Google Gemini API: ${t.localizedMessage ?: "Unknown error"}", t)
        }
    }

    private fun generateContentUnaryInternal(
        cleanKey: String,
        targetModel: String,
        requestPayload: String,
        onTokenChunk: ((String) -> Unit)?,
        httpClient: OkHttpClient
    ): GeminiGenerateResult {
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
                    throw mapHttpError(response.code, bodyString, targetModel)
                }

                val generatedText = parseCandidatesText(bodyString)
                if (generatedText.isBlank()) {
                    throw GeminiApiException(200, "Gemini returned an empty candidate response for model $targetModel.")
                }

                val tokensUsed = parseTotalTokenCount(bodyString)
                val finishReason = parseFinishReason(bodyString)
                onTokenChunk?.invoke(generatedText)

                return GeminiGenerateResult(
                    text = generatedText,
                    model = targetModel,
                    tokensUsed = tokensUsed,
                    httpStatusCode = response.code,
                    finishReason = finishReason
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ge: GeminiApiException) {
            throw ge
        } catch (t: Throwable) {
            throw GeminiApiException(500, "Failed to connect to Google Gemini API: ${t.localizedMessage ?: "Unknown error"}", t)
        }
    }

    private fun mapHttpError(
        statusCode: Int,
        bodyString: String,
        targetModel: String
    ): GeminiApiException {
        val errorMsg = parseErrorMessage(bodyString) ?: "HTTP $statusCode"
        val suggestedModel = extractSuggestedModelFromError(errorMsg)
        return when (statusCode) {
            400, 403 -> GeminiApiException(
                statusCode = statusCode,
                message = "Gemini API rejected request ($errorMsg). Ensure your key is from aistudio.google.com/apikey."
            )
            429 -> GeminiApiException(
                statusCode = 429,
                message = "Gemini Quota Exceeded (HTTP 429) on $targetModel. Switching to next available model."
            )
            else -> GeminiApiException(
                statusCode = statusCode,
                message = "Gemini API failed with HTTP $statusCode: $errorMsg",
                suggestedModel = suggestedModel
            )
        }
    }

    private fun buildRequestJson(
        prompt: String,
        systemInstruction: String,
        temperature: Float,
        maxOutputTokens: Int
    ): String = buildJsonObject {
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", prompt) })
                    }
                }
            )
        }
        putJsonObject("systemInstruction") {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", systemInstruction) })
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", JsonPrimitive(temperature))
            put("maxOutputTokens", JsonPrimitive(maxOutputTokens))
        }
    }.toString()

    internal fun extractSuggestedModelFromError(errorMsg: String): String? {
        val match = Regex("""(?:use|to)\s+(?:models/)?(gemini-[a-zA-Z0-9._-]+)""", RegexOption.IGNORE_CASE)
            .find(errorMsg)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseCandidatesText(bodyString: String): String = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return ""
        val firstCandidate = candidates.firstOrNull()?.jsonObject ?: return ""
        val content = firstCandidate["content"]?.jsonObject ?: return ""
        val parts = content["parts"]?.jsonArray ?: return ""
        parts.mapNotNull { partEl ->
            val obj = partEl.jsonObject
            if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
            obj["text"]?.jsonPrimitive?.content
        }.joinToString("")
    }.getOrDefault("")

    private fun parseTotalTokenCount(bodyString: String): Long? = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val usage = root["usageMetadata"]?.jsonObject ?: return null
        usage["totalTokenCount"]?.jsonPrimitive?.content?.toLongOrNull()
    }.getOrNull()

    private fun parseFinishReason(bodyString: String): String? = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return null
        candidates.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.content
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

    private fun parseErrorCode(bodyString: String): Int? = runCatching {
        val root = json.parseToJsonElement(bodyString).jsonObject
        val error = root["error"]?.jsonObject ?: return null
        error["code"]?.jsonPrimitive?.content?.toIntOrNull()
    }.getOrNull()
}
