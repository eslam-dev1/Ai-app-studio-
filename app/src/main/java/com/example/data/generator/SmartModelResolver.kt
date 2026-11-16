package com.example.data.generator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class ResolvedModelAsset(
    val primaryDownloadUrl: String,
    val mirrorFallbackUrl: String,
    val fileName: String,
    val exactSizeBytes: Long,
    val sourceOrigin: String,
    val isGated: Boolean = false
) {
    val exactSizeLabel: String
        get() = when {
            exactSizeBytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", exactSizeBytes / (1024.0 * 1024.0 * 1024.0))
            exactSizeBytes > 0L -> String.format(Locale.US, "%.0f MB", exactSizeBytes / (1024.0 * 1024.0))
            else -> "~1 GB"
        }
}

/**
 * Universal Multi-Source Link Resolver for LiteRT & MediaPipe On-Device AI Models.
 * Resolves models across:
 * 1. Official open LiteRT community repo (https://huggingface.co/api/models/litert-community/<repo>)
 * 2. Hugging Face Global Search API (https://huggingface.co/api/models?search=...) & hf-mirror.com search
 * 3. Fallback direct preset mirror with automatic anonymous download for non-gated models
 * 4. Automatic CDN failover from huggingface.co to hf-mirror.com on timeouts
 */
object SmartModelResolver {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resolves the highest-quality, verified working download URL for any given model preset or custom URL.
     */
    suspend fun resolveModelDownloadUrl(
        preset: LocalModelPreset,
        customUrl: String = "",
        huggingFaceToken: String = "",
        httpClient: OkHttpClient = client
    ): ResolvedModelAsset = withContext(Dispatchers.IO) {
        val cleanCustom = customUrl.trim()
        if (cleanCustom.isNotBlank() && (cleanCustom.startsWith("http://") || cleanCustom.startsWith("https://"))) {
            val inferredName = cleanCustom.substringBefore('?').substringAfterLast('/').ifBlank { preset.fileName }
            val probeSize = probeFileSizeBytes(cleanCustom, httpClient)
            val size = if (probeSize > 0L) probeSize else preset.approxSizeBytes
            return@withContext ResolvedModelAsset(
                primaryDownloadUrl = cleanCustom,
                mirrorFallbackUrl = cleanCustom,
                fileName = inferredName,
                exactSizeBytes = size,
                sourceOrigin = "Custom Direct Link",
                isGated = false
            )
        }

        val cleanToken = huggingFaceToken.trim()
        val shouldAttachToken = preset.isGated && cleanToken.isNotBlank() && cleanToken.startsWith("hf_")

        // Step 1: Query official LiteRT community repository
        val step1Asset = queryRepoSiblings(
            repoId = preset.repoId,
            token = if (shouldAttachToken) cleanToken else "",
            preferredVariantHint = preset.preferredVariantHint,
            isGated = preset.isGated,
            httpClient = httpClient
        )
        if (step1Asset != null) {
            return@withContext step1Asset
        }

        // Step 2: Global Hugging Face Search API + hf-mirror search
        val searchTerm = extractSearchKeywords(preset)
        val step2Asset = queryGlobalSearch(
            searchQuery = searchTerm,
            token = if (shouldAttachToken) cleanToken else "",
            preferredVariantHint = preset.preferredVariantHint,
            preset = preset,
            httpClient = httpClient
        )
        if (step2Asset != null) {
            return@withContext step2Asset
        }

        // Step 3: Preset Direct Fallback with CDN mirror counterpart
        val mirrorUrl = preset.downloadUrl.replace("https://huggingface.co", "https://hf-mirror.com")
        val fallbackProbe = probeFileSizeBytes(preset.downloadUrl, httpClient)
        val finalSize = if (fallbackProbe > 0L) fallbackProbe else preset.approxSizeBytes

        ResolvedModelAsset(
            primaryDownloadUrl = preset.downloadUrl,
            mirrorFallbackUrl = mirrorUrl,
            fileName = preset.fileName,
            exactSizeBytes = finalSize,
            sourceOrigin = "Direct Preset Mirror",
            isGated = preset.isGated
        )
    }

    /**
     * Converts any Hugging Face model URL to its hf-mirror.com CDN mirror equivalent.
     */
    fun toMirrorUrl(url: String): String =
        url.replace("https://huggingface.co", "https://hf-mirror.com")

    private fun extractSearchKeywords(preset: LocalModelPreset): String {
        val repoLeaf = preset.repoId.substringAfterLast('/')
        return when {
            repoLeaf.contains("SmolLM2-360M", ignoreCase = true) -> "SmolLM2 360M task litert"
            repoLeaf.contains("SmolLM2-1.7B", ignoreCase = true) -> "SmolLM2 1.7B task litert"
            repoLeaf.contains("Qwen2.5-0.5B", ignoreCase = true) -> "Qwen2.5 0.5B task litert"
            repoLeaf.contains("Qwen2.5-1.5B", ignoreCase = true) -> "Qwen2.5 1.5B task litert"
            repoLeaf.contains("Qwen2.5-3B", ignoreCase = true) -> "Qwen2.5 3B task litert"
            repoLeaf.contains("DeepSeek-R1", ignoreCase = true) -> "DeepSeek R1 Distill Qwen 1.5B task"
            repoLeaf.contains("Phi-4", ignoreCase = true) -> "Phi-4 mini task litert"
            repoLeaf.contains("Llama-3.2-1B", ignoreCase = true) -> "Llama 3.2 1B task"
            repoLeaf.contains("Llama-3.2-3B", ignoreCase = true) -> "Llama 3.2 3B task"
            repoLeaf.contains("Hammer2.1", ignoreCase = true) -> "Hammer2.1 task"
            repoLeaf.contains("Gemma3-1B", ignoreCase = true) -> "Gemma3 1B task"
            repoLeaf.contains("Gemma2-2B", ignoreCase = true) -> "Gemma2 2B task"
            else -> "$repoLeaf task litert"
        }
    }

    private fun queryRepoSiblings(
        repoId: String,
        token: String,
        preferredVariantHint: String,
        isGated: Boolean,
        httpClient: OkHttpClient
    ): ResolvedModelAsset? {
        val cleanRepo = repoId.trim().trim('/')
        if (cleanRepo.isBlank()) return null

        val endpoints = listOf(
            "https://huggingface.co/api/models/$cleanRepo",
            "https://hf-mirror.com/api/models/$cleanRepo"
        )

        for (endpoint in endpoints) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "AIAppStudio-Android/1.0")
                    .apply {
                        if (token.isNotBlank()) header("Authorization", "Bearer $token")
                    }
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val filename = pickBestModelFile(body, preferredVariantHint)
                        if (filename != null) {
                            val primary = "https://huggingface.co/$cleanRepo/resolve/main/$filename"
                            val mirror = "https://hf-mirror.com/$cleanRepo/resolve/main/$filename"
                            val size = probeFileSizeBytes(primary, httpClient)
                            return ResolvedModelAsset(
                                primaryDownloadUrl = primary,
                                mirrorFallbackUrl = mirror,
                                fileName = filename,
                                exactSizeBytes = size,
                                sourceOrigin = "LiteRT Community ($cleanRepo)",
                                isGated = isGated
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint or fall back
            }
        }
        return null
    }

    private fun queryGlobalSearch(
        searchQuery: String,
        token: String,
        preferredVariantHint: String,
        preset: LocalModelPreset,
        httpClient: OkHttpClient
    ): ResolvedModelAsset? {
        val encodedQuery = searchQuery.replace(" ", "+")
        val searchUrls = listOf(
            "https://huggingface.co/api/models?search=$encodedQuery&sort=downloads&direction=-1&limit=5",
            "https://hf-mirror.com/api/models?search=$encodedQuery&sort=downloads&direction=-1&limit=5"
        )

        for (searchUrl in searchUrls) {
            try {
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "AIAppStudio-Android/1.0")
                    .apply {
                        if (token.isNotBlank()) header("Authorization", "Bearer $token")
                    }
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val candidateRepos = parseCandidateRepoIds(body)
                        for (candidate in candidateRepos) {
                            val asset = queryRepoSiblings(
                                repoId = candidate,
                                token = token,
                                preferredVariantHint = preferredVariantHint,
                                isGated = preset.isGated,
                                httpClient = httpClient
                            )
                            if (asset != null) return asset
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue
            }
        }
        return null
    }

    private fun parseCandidateRepoIds(jsonBody: String): List<String> = runCatching {
        val array = json.parseToJsonElement(jsonBody).jsonArray
        array.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
    }.getOrDefault(emptyList())

    private fun pickBestModelFile(metadataJson: String, preferredVariantHint: String): String? = runCatching {
        val root = json.parseToJsonElement(metadataJson).jsonObject
        val siblings = root["siblings"]?.jsonArray ?: return null
        val filenames = siblings.mapNotNull { it.jsonObject["rfilename"]?.jsonPrimitive?.content }

        // Find .task files first, then .bin
        val taskFiles = filenames.filter { it.endsWith(".task", ignoreCase = true) }
        val binFiles = filenames.filter { it.endsWith(".bin", ignoreCase = true) }

        val pool = taskFiles.ifEmpty { binFiles }
        if (pool.isEmpty()) return null

        if (preferredVariantHint.isNotBlank()) {
            val variantMatch = pool.firstOrNull { it.contains(preferredVariantHint, ignoreCase = true) }
            if (variantMatch != null) return variantMatch
        }
        pool.firstOrNull { it.contains("q8", ignoreCase = true) }
            ?: pool.firstOrNull { it.contains("int4", ignoreCase = true) }
            ?: pool.first()
    }.getOrNull()

    private fun probeFileSizeBytes(url: String, httpClient: OkHttpClient): Long {
        return try {
            val headReq = Request.Builder()
                .url(url)
                .header("User-Agent", "AIAppStudio-Android/1.0")
                .head()
                .build()

            httpClient.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    if (len > 0L) return len
                }
            }

            // Fallback: 1-byte GET Range request to probe content-range
            val rangeReq = Request.Builder()
                .url(url)
                .header("User-Agent", "AIAppStudio-Android/1.0")
                .header("Range", "bytes=0-0")
                .get()
                .build()

            httpClient.newCall(rangeReq).execute().use { resp ->
                val cr = resp.header("Content-Range") // e.g. "bytes 0-0/123456789"
                if (cr != null && cr.contains('/')) {
                    val total = cr.substringAfterLast('/').trim().toLongOrNull()
                    if (total != null && total > 0L) return total
                }
                resp.body?.contentLength() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
