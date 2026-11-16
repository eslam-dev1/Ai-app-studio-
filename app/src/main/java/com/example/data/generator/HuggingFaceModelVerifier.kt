package com.example.data.generator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface ModelLinkVerificationState {
    data object Idle : ModelLinkVerificationState
    data object Verifying : ModelLinkVerificationState
    data class Verified(
        val resolvedUrl: String,
        val resolvedFileName: String,
        val exactSizeBytes: Long,
        val exactSizeLabel: String
    ) : ModelLinkVerificationState
    data class Unreachable(
        val reason: String,
        val requiresLicenseAcceptance: Boolean = false
    ) : ModelLinkVerificationState
}

@Serializable
internal data class HfModelMetadataDto(
    val id: String = "",
    val siblings: List<HfSiblingDto> = emptyList()
)

@Serializable
internal data class HfSiblingDto(
    val rfilename: String = ""
)

object HuggingFaceModelVerifier {

    const val GATED_LICENSE_ERROR_MESSAGE =
        "Please ensure you have accepted the Gemma license agreement on Hugging Face for your account."

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Queries the Hugging Face Models API for [repoId] to resolve the exact `.task` / `.bin`
     * filename (preferring `.task` and requested quantization variant), then verifies HTTP 200 OK
     * and exact file size via an OkHttp HEAD request (falling back to a 1-byte Range GET if needed).
     */
    suspend fun verifyRepoAndResolveLink(
        repoId: String,
        huggingFaceToken: String = "",
        preferredVariantHint: String = ""
    ): ModelLinkVerificationState = withContext(Dispatchers.IO) {
        val cleanRepoId = repoId.trim().trim('/')
        if (cleanRepoId.isBlank()) {
            return@withContext ModelLinkVerificationState.Unreachable("Invalid Hugging Face repository ID")
        }

        val cleanToken = huggingFaceToken.trim()
        val metadataUrl = "https://huggingface.co/api/models/$cleanRepoId"
        val metadataRequest = Request.Builder()
            .url(metadataUrl)
            .header("User-Agent", "AIAppStudio-Android/1.0")
            .apply {
                if (cleanToken.isNotBlank()) {
                    header("Authorization", "Bearer $cleanToken")
                }
            }
            .get()
            .build()

        val resolvedFilename: String = try {
            client.newCall(metadataRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val isAuthError = response.code == 401 || response.code == 403
                    val reason = when (response.code) {
                        401, 403 -> GATED_LICENSE_ERROR_MESSAGE
                        404 -> "HTTP 404 (Repo not found)"
                        else -> "HTTP ${response.code}"
                    }
                    return@withContext ModelLinkVerificationState.Unreachable(
                        reason = reason,
                        requiresLicenseAcceptance = isAuthError
                    )
                }
                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    return@withContext ModelLinkVerificationState.Unreachable("Empty metadata response")
                }
                val metadata = json.decodeFromString<HfModelMetadataDto>(bodyString)
                val filenames = metadata.siblings.map { it.rfilename }
                selectBestModelFilename(filenames, preferredVariantHint = preferredVariantHint)
                    ?: return@withContext ModelLinkVerificationState.Unreachable("No .task or .bin file in repo")
            }
        } catch (ex: Exception) {
            return@withContext ModelLinkVerificationState.Unreachable(
                ex.localizedMessage?.take(60) ?: "Network error"
            )
        }

        val resolvedUrl = "https://huggingface.co/$cleanRepoId/resolve/main/$resolvedFilename"
        return@withContext verifyDirectModelUrl(
            url = resolvedUrl,
            fileNameHint = resolvedFilename,
            huggingFaceToken = cleanToken
        )
    }

    suspend fun verifyDirectModelUrl(
        url: String,
        fileNameHint: String = "",
        huggingFaceToken: String = ""
    ): ModelLinkVerificationState = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val cleanToken = huggingFaceToken.trim()
        val resolvedName = fileNameHint.ifBlank {
            cleanUrl.substringBefore('?').substringAfterLast('/').ifBlank { "model.task" }
        }

        val headRequest = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", "AIAppStudio-Android/1.0")
            .apply {
                if (cleanToken.isNotBlank()) {
                    header("Authorization", "Bearer $cleanToken")
                }
            }
            .head()
            .build()

        try {
            var sizeBytes: Long? = null
            var verifiedOk = false
            var failureReason = ""
            var isAuthFailure = false

            client.newCall(headRequest).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        verifiedOk = true
                        sizeBytes = response.header("X-Linked-Size")?.toLongOrNull()
                            ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                            ?: response.body?.contentLength()?.takeIf { it > 0L }
                    }
                    response.code == 405 -> {
                        // Some CDN mirrors reject HEAD; fall back to 1-byte Range GET check below
                        failureReason = "HEAD_405"
                    }
                    else -> {
                        isAuthFailure = response.code == 401 || response.code == 403
                        failureReason = when (response.code) {
                            401, 403 -> GATED_LICENSE_ERROR_MESSAGE
                            404 -> "HTTP 404 (File not found)"
                            else -> "HTTP ${response.code}"
                        }
                    }
                }
            }

            if ((!verifiedOk && failureReason == "HEAD_405") || (verifiedOk && (sizeBytes == null || sizeBytes <= 0L))) {
                val rangeRequest = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "AIAppStudio-Android/1.0")
                    .header("Range", "bytes=0-0")
                    .apply {
                        if (cleanToken.isNotBlank()) {
                            header("Authorization", "Bearer $cleanToken")
                        }
                    }
                    .get()
                    .build()

                client.newCall(rangeRequest).execute().use { rangeResponse ->
                    if (rangeResponse.isSuccessful || rangeResponse.code == 206) {
                        verifiedOk = true
                        sizeBytes = parseContentRangeTotalBytes(rangeResponse.header("Content-Range"))
                            ?: rangeResponse.header("X-Linked-Size")?.toLongOrNull()
                            ?: rangeResponse.header("Content-Length")?.toLongOrNull()?.takeIf { it > 1L }
                            ?: sizeBytes
                    } else if (!verifiedOk) {
                        isAuthFailure = rangeResponse.code == 401 || rangeResponse.code == 403
                        failureReason = if (isAuthFailure) {
                            GATED_LICENSE_ERROR_MESSAGE
                        } else {
                            "HTTP ${rangeResponse.code}"
                        }
                    }
                }
            }

            if (!verifiedOk) {
                return@withContext ModelLinkVerificationState.Unreachable(
                    reason = failureReason.ifBlank { "Link unreachable" },
                    requiresLicenseAcceptance = isAuthFailure
                )
            }

            val finalBytes = sizeBytes?.coerceAtLeast(0L) ?: 0L
            val label = if (finalBytes > 0L) formatExactSize(finalBytes) else "Verified"
            ModelLinkVerificationState.Verified(
                resolvedUrl = cleanUrl,
                resolvedFileName = resolvedName,
                exactSizeBytes = finalBytes,
                exactSizeLabel = label
            )
        } catch (ex: Exception) {
            ModelLinkVerificationState.Unreachable(
                ex.localizedMessage?.take(60) ?: "Connection failed"
            )
        }
    }

    /**
     * Selects the highest-priority `.task` or `.bin` bundle from Hugging Face `siblings[].rfilename`,
     * strongly preferring `.task` bundles and `int4` or `q8` quantized variants.
     */
    fun selectBestModelFilename(
        siblings: List<String>,
        preferredVariantHint: String = ""
    ): String? {
        val candidates = siblings
            .map { it.trim() }
            .filter { name ->
                val lower = name.lowercase(Locale.US)
                (lower.endsWith(".task") || lower.endsWith(".bin")) &&
                    !lower.contains("tokenizer") &&
                    !lower.contains("vocab") &&
                    !lower.contains("-web.")
            }

        if (candidates.isEmpty()) return null

        val variantHint = preferredVariantHint.trim().lowercase(Locale.US)
        return candidates.maxByOrNull { name ->
            val lower = name.lowercase(Locale.US)
            var score = 0
            if (lower.endsWith(".task")) score += 100
            if (variantHint.isNotBlank() && lower.contains(variantHint)) score += 60
            if (lower.contains("int4") || lower.contains("q4")) score += 45
            if (lower.contains("q8") || lower.contains("int8")) score += 40
            if (lower.contains("multi-prefill-seq")) score += 15
            if (lower.contains("ekv")) score += 5
            if (lower.contains("gpu")) score += 4
            if (lower.contains("cpu")) score += 2
            score
        }
    }

    fun parseContentRangeTotalBytes(contentRangeHeader: String?): Long? {
        val raw = contentRangeHeader?.trim() ?: return null
        val totalPart = raw.substringAfter('/', missingDelimiterValue = "").trim()
        return totalPart.toLongOrNull()?.takeIf { it > 0L }
    }

    fun formatExactSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "Unknown size"
        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
