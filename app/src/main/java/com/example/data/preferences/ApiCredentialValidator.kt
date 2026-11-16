package com.example.data.preferences

import com.example.BuildConfig
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface ApiKeyVerificationStatus {
    data object Idle : ApiKeyVerificationStatus
    data object Verifying : ApiKeyVerificationStatus
    data class Valid(
        val provider: String,
        val detail: String,
        val latencyMs: Long = 0L,
        val httpStatusCode: Int? = null,
        val usageHeaders: ParsedUsageHeaders? = null
    ) : ApiKeyVerificationStatus
    data class Invalid(
        val errorMessage: String,
        val latencyMs: Long? = null,
        val httpStatusCode: Int? = null,
        val usageHeaders: ParsedUsageHeaders? = null
    ) : ApiKeyVerificationStatus
}

sealed interface SavedApiKeyHealthState {
    data object NotConfigured : SavedApiKeyHealthState
    data class Checking(val providerLabel: String, val maskedKey: String) : SavedApiKeyHealthState
    data class Verified(
        val providerLabel: String,
        val maskedKey: String,
        val detail: String,
        val latencyMs: Long,
        val httpStatusCode: Int? = null,
        val usageHeaders: ParsedUsageHeaders? = null
    ) : SavedApiKeyHealthState
    data class Error(
        val providerLabel: String,
        val maskedKey: String,
        val message: String,
        val latencyMs: Long? = null,
        val httpStatusCode: Int? = null,
        val usageHeaders: ParsedUsageHeaders? = null
    ) : SavedApiKeyHealthState
}

enum class ConfiguredCloudProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENROUTER("OpenRouter")
}

data class ResolvedProviderCredential(
    val provider: ConfiguredCloudProvider,
    val apiKey: String,
    val maskedKey: String,
    val isFromBuildConfig: Boolean = false
)

data class KeyFormatValidation(
    val isValidShape: Boolean,
    val warningMessage: String? = null,
    val helperHint: String? = null
)

object ApiCredentialValidator {

    const val GEMINI_MIN_LENGTH = 30
    const val GEMINI_MAX_LENGTH = 128
    const val OPENROUTER_MIN_LENGTH = 24
    const val OPENROUTER_MAX_LENGTH = 256

    private val allowedKeyCharRegex = Regex("^[A-Za-z0-9_-]+$")
    private val placeholderValues = setOf(
        "your_api_key_here",
        "your_gemini_api_key_here",
        "my_gemini_api_key",
        "sk-or-v1-...",
        "aizasy..."
    )

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun resolveConfiguredProvider(
        settings: StudioSettings,
        buildConfigGeminiKey: String = BuildConfig.GEMINI_API_KEY
    ): ResolvedProviderCredential? {
        val rawOpenRouter = settings.openRouterApiKey.trim()
        val validBuildConfigGemini = buildConfigGeminiKey.trim()
            .takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            .orEmpty()

        val directGeminiKey = settings.geminiApiKey.trim().ifBlank {
            if (rawOpenRouter.startsWith("AIza")) rawOpenRouter else ""
        }
        val effectiveOpenRouterKey = if (rawOpenRouter.startsWith("AIza")) "" else rawOpenRouter

        val preferGemini = OpenRouterModels.isGeminiModel(settings.openRouterModel) ||
            settings.openRouterModel == OpenRouterModels.AUTO_FREE

        return when {
            directGeminiKey.isNotBlank() && (preferGemini || effectiveOpenRouterKey.isBlank()) -> {
                ResolvedProviderCredential(
                    provider = ConfiguredCloudProvider.GEMINI,
                    apiKey = directGeminiKey,
                    maskedKey = SecureCredentialCipher.maskSecret(directGeminiKey)
                )
            }
            effectiveOpenRouterKey.isNotBlank() -> {
                ResolvedProviderCredential(
                    provider = ConfiguredCloudProvider.OPENROUTER,
                    apiKey = effectiveOpenRouterKey,
                    maskedKey = SecureCredentialCipher.maskSecret(effectiveOpenRouterKey)
                )
            }
            validBuildConfigGemini.isNotBlank() -> {
                ResolvedProviderCredential(
                    provider = ConfiguredCloudProvider.GEMINI,
                    apiKey = validBuildConfigGemini,
                    maskedKey = SecureCredentialCipher.maskSecret(validBuildConfigGemini),
                    isFromBuildConfig = true
                )
            }
            else -> null
        }
    }

    fun validateGeminiKeyFormat(key: String): KeyFormatValidation {
        if (key.isEmpty()) {
            return KeyFormatValidation(
                isValidShape = true,
                helperHint = "Format: starts with 'AIza' • $GEMINI_MIN_LENGTH–$GEMINI_MAX_LENGTH chars • A–Z, 0–9, '-', '_'"
            )
        }
        if (key.any { it.isWhitespace() }) {
            return KeyFormatValidation(
                isValidShape = false,
                warningMessage = "API key cannot contain spaces, tabs, or line breaks."
            )
        }
        if (key.lowercase() in placeholderValues) {
            return KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Replace the placeholder text with your actual Google AI Studio key."
            )
        }
        return when {
            key.startsWith("sk-or-") || key.startsWith("sk-") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "This looks like an OpenRouter key (sk-...). Paste it in the OpenRouter field below."
            )
            key.startsWith("ghp_") || key.startsWith("github_pat_") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "This looks like a GitHub PAT, not a Gemini API key."
            )
            key.startsWith("hf_") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "This looks like a Hugging Face token, not a Gemini API key."
            )
            !allowedKeyCharRegex.matches(key) -> {
                val badChar = key.first { !it.isLetterOrDigit() && it != '_' && it != '-' }
                KeyFormatValidation(
                    isValidShape = false,
                    warningMessage = "Invalid character '$badChar' — only letters, numbers, '-' and '_' are allowed."
                )
            }
            !key.startsWith("AIza") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Gemini API keys must start with 'AIza' (found '${key.take(4)}')."
            )
            key.length < GEMINI_MIN_LENGTH -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Too short (${key.length}/$GEMINI_MIN_LENGTH chars) — Gemini keys require at least $GEMINI_MIN_LENGTH characters."
            )
            key.length > GEMINI_MAX_LENGTH -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Too long (${key.length}/$GEMINI_MAX_LENGTH max) — check for extra pasted text."
            )
            else -> KeyFormatValidation(
                isValidShape = true,
                helperHint = "Valid Gemini key format (${key.length} chars) • Ready to save"
            )
        }
    }

    fun validateOpenRouterKeyFormat(key: String): KeyFormatValidation {
        if (key.isEmpty()) {
            return KeyFormatValidation(
                isValidShape = true,
                helperHint = "Format: starts with 'sk-or-v1-' or 'sk-' • $OPENROUTER_MIN_LENGTH–$OPENROUTER_MAX_LENGTH chars • A–Z, 0–9, '-', '_'"
            )
        }
        if (key.any { it.isWhitespace() }) {
            return KeyFormatValidation(
                isValidShape = false,
                warningMessage = "API key cannot contain spaces, tabs, or line breaks."
            )
        }
        if (key.lowercase() in placeholderValues) {
            return KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Replace the placeholder text with your actual OpenRouter API key."
            )
        }
        return when {
            key.startsWith("AIza") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "This looks like a Google Gemini key (AIza...). Paste it in the Gemini field above."
            )
            key.startsWith("ghp_") || key.startsWith("github_pat_") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "This looks like a GitHub PAT, not an OpenRouter API key."
            )
            key.startsWith("hf_") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "This looks like a Hugging Face token, not an OpenRouter API key."
            )
            !allowedKeyCharRegex.matches(key) -> {
                val badChar = key.first { !it.isLetterOrDigit() && it != '_' && it != '-' }
                KeyFormatValidation(
                    isValidShape = false,
                    warningMessage = "Invalid character '$badChar' — only letters, numbers, '-' and '_' are allowed."
                )
            }
            !key.startsWith("sk-") -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "OpenRouter API keys must start with 'sk-or-v1-' or 'sk-'."
            )
            key.length < OPENROUTER_MIN_LENGTH -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Too short (${key.length}/$OPENROUTER_MIN_LENGTH chars) — OpenRouter keys require at least $OPENROUTER_MIN_LENGTH characters."
            )
            key.length > OPENROUTER_MAX_LENGTH -> KeyFormatValidation(
                isValidShape = false,
                warningMessage = "Too long (${key.length}/$OPENROUTER_MAX_LENGTH max) — check for extra pasted text."
            )
            else -> KeyFormatValidation(
                isValidShape = true,
                helperHint = "Valid OpenRouter key format (${key.length} chars) • Ready to save"
            )
        }
    }

    suspend fun verifyGeminiApiKey(
        apiKey: String,
        httpClient: OkHttpClient = client
    ): ApiKeyVerificationStatus = withContext(Dispatchers.IO) {
        val clean = apiKey.trim()
        if (clean.isBlank()) {
            return@withContext ApiKeyVerificationStatus.Invalid("Enter a Gemini API key first.")
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=$clean")
            .get()
            .build()

        val startedAt = System.currentTimeMillis()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val bodyString = response.body?.string().orEmpty()
                val parsedHeaders = ApiUsageHeaderParser.parse(response.headers)
                when (response.code) {
                    in 200..299 -> {
                        val firstModel = parseGeminiFirstModel(bodyString)
                        val modelDetail = if (firstModel != null) " • $firstModel" else ""
                        ApiKeyVerificationStatus.Valid(
                            provider = "Google Gemini",
                            detail = "Verified (HTTP ${response.code})$modelDetail",
                            latencyMs = latency,
                            httpStatusCode = response.code,
                            usageHeaders = parsedHeaders
                        )
                    }
                    400, 401, 403 -> ApiKeyVerificationStatus.Invalid(
                        errorMessage = "Gemini key rejected (HTTP ${response.code}). Check that the key is active in Google AI Studio.",
                        latencyMs = latency,
                        httpStatusCode = response.code,
                        usageHeaders = parsedHeaders
                    )
                    429 -> ApiKeyVerificationStatus.Valid(
                        provider = "Google Gemini",
                        detail = "Key accepted (rate limited 429 — auto-fallback active)",
                        latencyMs = latency,
                        httpStatusCode = response.code,
                        usageHeaders = parsedHeaders
                    )
                    else -> ApiKeyVerificationStatus.Invalid(
                        errorMessage = "Gemini verification returned HTTP ${response.code}.",
                        latencyMs = latency,
                        httpStatusCode = response.code,
                        usageHeaders = parsedHeaders
                    )
                }
            }
        }.getOrElse { err ->
            val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            ApiKeyVerificationStatus.Invalid(
                errorMessage = "Network error verifying Gemini key: ${err.localizedMessage ?: "Check connection"}",
                latencyMs = latency
            )
        }
    }

    suspend fun verifyOpenRouterApiKey(
        apiKey: String,
        httpClient: OkHttpClient = client
    ): ApiKeyVerificationStatus = withContext(Dispatchers.IO) {
        val clean = apiKey.trim()
        if (clean.isBlank()) {
            return@withContext ApiKeyVerificationStatus.Invalid("Enter an OpenRouter API key first.")
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/auth/key")
            .header("Authorization", "Bearer $clean")
            .header("HTTP-Referer", "https://aiappstudio.local")
            .header("X-Title", "AI App Studio")
            .get()
            .build()

        val startedAt = System.currentTimeMillis()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val bodyString = response.body?.string().orEmpty()
                val parsedHeaders = ApiUsageHeaderParser.parse(response.headers)
                when (response.code) {
                    in 200..299 -> {
                        val keyDetail = parseOpenRouterKeyDetail(bodyString)
                        val suffix = if (keyDetail != null) " • $keyDetail" else ""
                        ApiKeyVerificationStatus.Valid(
                            provider = "OpenRouter",
                            detail = "Verified (HTTP ${response.code})$suffix",
                            latencyMs = latency,
                            httpStatusCode = response.code,
                            usageHeaders = parsedHeaders
                        )
                    }
                    401, 403 -> ApiKeyVerificationStatus.Invalid(
                        errorMessage = "OpenRouter rejected this key (HTTP ${response.code}).",
                        latencyMs = latency,
                        httpStatusCode = response.code,
                        usageHeaders = parsedHeaders
                    )
                    429 -> ApiKeyVerificationStatus.Valid(
                        provider = "OpenRouter",
                        detail = "Key authenticated (429 rate-limit — fallback chain active)",
                        latencyMs = latency,
                        httpStatusCode = response.code,
                        usageHeaders = parsedHeaders
                    )
                    else -> ApiKeyVerificationStatus.Invalid(
                        errorMessage = "OpenRouter verification returned HTTP ${response.code}.",
                        latencyMs = latency,
                        httpStatusCode = response.code,
                        usageHeaders = parsedHeaders
                    )
                }
            }
        }.getOrElse { err ->
            val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            ApiKeyVerificationStatus.Invalid(
                errorMessage = "Network error verifying OpenRouter key: ${err.localizedMessage ?: "Check connection"}",
                latencyMs = latency
            )
        }
    }

    suspend fun verifySavedProviderHealth(
        settings: StudioSettings,
        buildConfigGeminiKey: String = BuildConfig.GEMINI_API_KEY,
        httpClient: OkHttpClient = client
    ): Pair<ResolvedProviderCredential?, SavedApiKeyHealthState> {
        val resolved = resolveConfiguredProvider(settings, buildConfigGeminiKey)
            ?: return null to SavedApiKeyHealthState.NotConfigured

        val status = when (resolved.provider) {
            ConfiguredCloudProvider.GEMINI -> verifyGeminiApiKey(resolved.apiKey, httpClient)
            ConfiguredCloudProvider.OPENROUTER -> verifyOpenRouterApiKey(resolved.apiKey, httpClient)
        }
        val providerLabel = if (resolved.isFromBuildConfig) {
            "${resolved.provider.displayName} (Env Secret)"
        } else {
            resolved.provider.displayName
        }

        val healthState = when (status) {
            is ApiKeyVerificationStatus.Valid -> SavedApiKeyHealthState.Verified(
                providerLabel = providerLabel,
                maskedKey = resolved.maskedKey,
                detail = status.detail,
                latencyMs = status.latencyMs,
                httpStatusCode = status.httpStatusCode,
                usageHeaders = status.usageHeaders
            )
            is ApiKeyVerificationStatus.Invalid -> SavedApiKeyHealthState.Error(
                providerLabel = providerLabel,
                maskedKey = resolved.maskedKey,
                message = status.errorMessage,
                latencyMs = status.latencyMs,
                httpStatusCode = status.httpStatusCode,
                usageHeaders = status.usageHeaders
            )
            else -> SavedApiKeyHealthState.Checking(providerLabel, resolved.maskedKey)
        }
        return resolved to healthState
    }

    private fun parseGeminiFirstModel(body: String): String? = runCatching {
        if (body.isBlank()) return null
        val root = lenientJson.parseToJsonElement(body).jsonObject
        val firstModel = root["models"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        firstModel["displayName"]?.jsonPrimitive?.content
            ?: firstModel["name"]?.jsonPrimitive?.content?.removePrefix("models/")
    }.getOrNull()

    private fun parseOpenRouterKeyDetail(body: String): String? = runCatching {
        if (body.isBlank()) return null
        val root = lenientJson.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return null
        val label = data["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val isFreeTier = data["is_free_tier"]?.jsonPrimitive?.booleanOrNull
        when {
            label != null && isFreeTier == true -> "$label (Free tier)"
            label != null -> label
            isFreeTier == true -> "Free tier active"
            else -> null
        }
    }.getOrNull()
}
