package com.example.data.generator

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface BundledModelState {
    data object Idle : BundledModelState
    data class Checking(val message: String = "Checking built-in offline AI model…") : BundledModelState
    data class Extracting(
        val copiedBytes: Long,
        val totalBytes: Long,
        val percent: Int
    ) : BundledModelState
    data class AssetMissingNeedsDownload(
        val message: String = "Built-in offline AI model is not bundled in this APK. Tap to download (~380 MB) with automatic multi-mirror fallback."
    ) : BundledModelState
    data class DownloadingFromMirror(
        val mirrorIndex: Int,
        val totalMirrors: Int,
        val mirrorLabel: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int
    ) : BundledModelState
    data class Initializing(
        val message: String = "Initializing MediaPipe LLM (MaxTokens: 2048, Temp: 0.15)…"
    ) : BundledModelState
    data class Ready(
        val modelPath: String,
        val displayName: String,
        val sizeBytes: Long
    ) : BundledModelState
    data class Unavailable(val reason: String) : BundledModelState
}

class BundledAssetMissingException(message: String) : IllegalStateException(message)

data class BuiltInModelMirror(
    val id: String,
    val label: String,
    val url: String,
    val sendHfBearerToken: Boolean = true
)

data class BuiltInMirrorDownloadResult(
    val file: File,
    val sizeBytes: Long,
    val mirrorUsed: BuiltInModelMirror,
    val attemptedMirrors: Int
)

data class ModelIntegrityCheckResult(
    val isValid: Boolean,
    val filePath: String,
    val actualSizeBytes: Long,
    val expectedSizeBytes: Long,
    val failureReason: String? = null
)

object BundledModelManager {

    const val BUNDLED_ASSET_PATH = "models/built_in_coder.task"
    const val BUNDLED_MODEL_FILE_NAME = "built_in_coder.task"
    const val BUNDLED_DISPLAY_NAME = "Qwen 2.5 0.5B Coder (Built-in Offline)"
    const val DOWNLOAD_BUTTON_LABEL = "Download Built-in AI (~380 MB)"
    const val DEFAULT_MAX_TOKENS = 2048
    const val DEFAULT_TEMPERATURE = 0.15f
    const val DEFAULT_TOP_K = 40
    const val MIN_VALID_MODEL_BYTES = 100L * 1024L * 1024L
    const val MIN_FALLBACK_DOWNLOAD_BYTES = 50L * 1024L * 1024L
    const val MIN_CUSTOM_MODEL_BYTES = 1024L * 1024L

    val BUILT_IN_FALLBACK_MIRRORS: List<BuiltInModelMirror> = listOf(
        BuiltInModelMirror(
            id = "hf_cdn",
            label = "Hugging Face CDN",
            url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            sendHfBearerToken = true
        ),
        BuiltInModelMirror(
            id = "hf_mirror",
            label = "HF-Mirror CDN",
            url = "https://hf-mirror.com/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            sendHfBearerToken = true
        ),
        BuiltInModelMirror(
            id = "modelscope",
            label = "ModelScope Mirror",
            url = "https://modelscope.cn/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sendHfBearerToken = false
        )
    )

    fun getTargetModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        return File(modelsDir, BUNDLED_MODEL_FILE_NAME)
    }

    fun resolveAssetSizeBytes(context: Context): Long {
        return runCatching {
            context.assets.openFd(BUNDLED_ASSET_PATH).use { fd ->
                fd.length
            }
        }.getOrDefault(0L)
    }

    fun hasBundledModelAsset(context: Context): Boolean {
        val assetFiles = runCatching {
            context.assets.list("models")?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return BUNDLED_MODEL_FILE_NAME in assetFiles || resolveAssetSizeBytes(context) > 0L
    }

    fun verifyModelFileIntegrity(
        file: File,
        expectedSizeBytes: Long = 0L,
        minValidBytes: Long = MIN_VALID_MODEL_BYTES
    ): ModelIntegrityCheckResult {
        if (!file.exists() || !file.isFile) {
            return ModelIntegrityCheckResult(
                isValid = false,
                filePath = file.absolutePath,
                actualSizeBytes = 0L,
                expectedSizeBytes = expectedSizeBytes,
                failureReason = "Model file does not exist on disk."
            )
        }
        if (!file.canRead()) {
            return ModelIntegrityCheckResult(
                isValid = false,
                filePath = file.absolutePath,
                actualSizeBytes = file.length(),
                expectedSizeBytes = expectedSizeBytes,
                failureReason = "Model file is unreadable."
            )
        }
        val actualLength = file.length()
        if (actualLength < minValidBytes) {
            return ModelIntegrityCheckResult(
                isValid = false,
                filePath = file.absolutePath,
                actualSizeBytes = actualLength,
                expectedSizeBytes = expectedSizeBytes,
                failureReason = "Model file is truncated or incomplete ($actualLength bytes)."
            )
        }
        if (expectedSizeBytes > 0L && actualLength != expectedSizeBytes) {
            return ModelIntegrityCheckResult(
                isValid = false,
                filePath = file.absolutePath,
                actualSizeBytes = actualLength,
                expectedSizeBytes = expectedSizeBytes,
                failureReason = "Model file size mismatch ($actualLength != $expectedSizeBytes bytes)."
            )
        }

        val headerCheck = runCatching {
            val header = ByteArray(64)
            FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 16 || header.take(read).none { it != 0.toByte() }) {
                    false
                } else {
                    val headerText = String(header, 0, read, Charsets.UTF_8).trimStart().lowercase()
                    !headerText.startsWith("<!doctype") &&
                        !headerText.startsWith("<html") &&
                        !headerText.startsWith("{\"error")
                }
            }
        }.getOrDefault(false)

        if (!headerCheck) {
            return ModelIntegrityCheckResult(
                isValid = false,
                filePath = file.absolutePath,
                actualSizeBytes = actualLength,
                expectedSizeBytes = expectedSizeBytes,
                failureReason = "Model file header appears corrupted, HTML, or empty."
            )
        }

        return ModelIntegrityCheckResult(
            isValid = true,
            filePath = file.absolutePath,
            actualSizeBytes = actualLength,
            expectedSizeBytes = expectedSizeBytes,
            failureReason = null
        )
    }

    suspend fun ensureExtractedToFilesDir(
        context: Context,
        onProgress: (copiedBytes: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<Pair<File, Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val destFile = getTargetModelFile(context)
            val isAssetBundled = hasBundledModelAsset(context)
            val expectedAssetSize = if (isAssetBundled) resolveAssetSizeBytes(context) else 0L
            val minBytes = if (isAssetBundled) MIN_VALID_MODEL_BYTES else MIN_FALLBACK_DOWNLOAD_BYTES

            val integrity = verifyModelFileIntegrity(
                file = destFile,
                expectedSizeBytes = expectedAssetSize,
                minValidBytes = minBytes
            )
            if (integrity.isValid) {
                val len = integrity.actualSizeBytes
                onProgress(len, len, 100)
                return@runCatching destFile to len
            }

            if (destFile.exists()) {
                runCatching { destFile.delete() }
            }

            if (!isAssetBundled) {
                throw BundledAssetMissingException(
                    "Bundled model asset $BUNDLED_ASSET_PATH is not bundled in this APK."
                )
            }

            val modelsDir = destFile.parentFile ?: File(context.filesDir, "models").apply { mkdirs() }
            modelsDir.mkdirs()
            val partFile = File(modelsDir, "$BUNDLED_MODEL_FILE_NAME.part")

            var totalCopied = 0L
            var lastReported = 0L
            val buffer = ByteArray(128 * 1024)

            try {
                onProgress(0L, expectedAssetSize, 0)
                context.assets.open(BUNDLED_ASSET_PATH).use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalCopied += read

                            if (totalCopied - lastReported >= 512 * 1024L ||
                                (expectedAssetSize > 0L && totalCopied >= expectedAssetSize)
                            ) {
                                lastReported = totalCopied
                                val pct = if (expectedAssetSize > 0L) {
                                    ((totalCopied * 100L) / expectedAssetSize).toInt().coerceIn(0, 100)
                                } else 0
                                onProgress(totalCopied, expectedAssetSize, pct)
                            }
                        }
                        output.flush()
                    }
                }

                val finalTotal = if (expectedAssetSize > 0L) expectedAssetSize else totalCopied
                onProgress(totalCopied, finalTotal, 100)

                if (totalCopied <= 0L) {
                    throw IllegalStateException("Extracted bundled model was 0 bytes.")
                }

                if (destFile.exists()) destFile.delete()
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }

                destFile to destFile.length()
            } catch (t: Throwable) {
                runCatching { partFile.delete() }
                throw t
            }
        }
    }

    fun buildMirrorDownloadRequest(
        mirror: BuiltInModelMirror,
        huggingFaceToken: String = ""
    ): Request {
        val builder = Request.Builder()
            .url(mirror.url.trim())
            .header("User-Agent", "AIAppStudio-Android/1.0")
            .get()

        val cleanToken = huggingFaceToken.trim()
        if (mirror.sendHfBearerToken && cleanToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $cleanToken")
        }
        return builder.build()
    }

    suspend fun downloadBuiltInModelWithMirrorFallback(
        context: Context,
        huggingFaceToken: String = "",
        mirrors: List<BuiltInModelMirror> = BUILT_IN_FALLBACK_MIRRORS,
        minValidBytes: Long = MIN_FALLBACK_DOWNLOAD_BYTES,
        okHttpClient: OkHttpClient = defaultMirrorHttpClient(),
        onMirrorAttempt: (mirrorIndex: Int, totalMirrors: Int, mirror: BuiltInModelMirror) -> Unit = { _, _, _ -> },
        onProgress: (
            mirrorIndex: Int,
            totalMirrors: Int,
            mirror: BuiltInModelMirror,
            downloadedBytes: Long,
            totalBytes: Long,
            percent: Int
        ) -> Unit = { _, _, _, _, _, _ -> }
    ): Result<BuiltInMirrorDownloadResult> = downloadBuiltInModelToFileWithMirrorFallback(
        targetFile = getTargetModelFile(context),
        huggingFaceToken = huggingFaceToken,
        mirrors = mirrors,
        minValidBytes = minValidBytes,
        okHttpClient = okHttpClient,
        onMirrorAttempt = onMirrorAttempt,
        onProgress = onProgress
    )

    suspend fun downloadBuiltInModelToFileWithMirrorFallback(
        targetFile: File,
        huggingFaceToken: String = "",
        mirrors: List<BuiltInModelMirror> = BUILT_IN_FALLBACK_MIRRORS,
        minValidBytes: Long = MIN_FALLBACK_DOWNLOAD_BYTES,
        okHttpClient: OkHttpClient = defaultMirrorHttpClient(),
        onMirrorAttempt: (mirrorIndex: Int, totalMirrors: Int, mirror: BuiltInModelMirror) -> Unit = { _, _, _ -> },
        onProgress: (
            mirrorIndex: Int,
            totalMirrors: Int,
            mirror: BuiltInModelMirror,
            downloadedBytes: Long,
            totalBytes: Long,
            percent: Int
        ) -> Unit = { _, _, _, _, _, _ -> }
    ): Result<BuiltInMirrorDownloadResult> = withContext(Dispatchers.IO) {
        if (mirrors.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No download mirrors configured."))
        }

        val destFile = targetFile
        destFile.parentFile?.mkdirs()
        val partFile = File(destFile.parentFile, "${destFile.name}.mirror.part")
        val mirrorFailures = mutableListOf<String>()

        for ((index, mirror) in mirrors.withIndex()) {
            val mirrorNumber = index + 1
            currentCoroutineContext().ensureActive()
            onMirrorAttempt(mirrorNumber, mirrors.size, mirror)

            val request = buildMirrorDownloadRequest(
                mirror = mirror,
                huggingFaceToken = huggingFaceToken
            )

            try {
                if (partFile.exists()) partFile.delete()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code != 200) {
                        val reason = "${mirror.label}: HTTP ${response.code}"
                        mirrorFailures += reason
                        return@use
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.startsWith("text/html") || contentType.contains("xhtml")) {
                        mirrorFailures += "${mirror.label}: Server returned HTML page instead of model binary"
                        return@use
                    }

                    val body = response.body
                    if (body == null) {
                        mirrorFailures += "${mirror.label}: Empty HTTP response body"
                        return@use
                    }

                    val contentLength = body.contentLength().takeIf { it > 0L } ?: 0L
                    var totalDownloaded = 0L
                    var lastReported = 0L
                    val buffer = ByteArray(64 * 1024)

                    onProgress(mirrorNumber, mirrors.size, mirror, 0L, contentLength, 0)

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                totalDownloaded += read

                                if (totalDownloaded - lastReported >= 262_144L ||
                                    (contentLength > 0L && totalDownloaded >= contentLength)
                                ) {
                                    lastReported = totalDownloaded
                                    val pct = if (contentLength > 0L) {
                                        ((totalDownloaded * 100L) / contentLength).toInt().coerceIn(0, 100)
                                    } else 0
                                    onProgress(
                                        mirrorNumber,
                                        mirrors.size,
                                        mirror,
                                        totalDownloaded,
                                        contentLength,
                                        pct
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    val finalTotal = contentLength.takeIf { it > 0L } ?: totalDownloaded
                    onProgress(mirrorNumber, mirrors.size, mirror, totalDownloaded, finalTotal, 100)

                    val integrity = verifyModelFileIntegrity(
                        file = partFile,
                        expectedSizeBytes = contentLength,
                        minValidBytes = minValidBytes
                    )
                    if (!integrity.isValid) {
                        runCatching { partFile.delete() }
                        mirrorFailures += "${mirror.label}: ${integrity.failureReason ?: "Invalid model file"}"
                        return@use
                    }

                    if (destFile.exists()) destFile.delete()
                    if (!partFile.renameTo(destFile)) {
                        partFile.copyTo(destFile, overwrite = true)
                        partFile.delete()
                    }

                    return@withContext Result.success(
                        BuiltInMirrorDownloadResult(
                            file = destFile,
                            sizeBytes = destFile.length(),
                            mirrorUsed = mirror,
                            attemptedMirrors = mirrorNumber
                        )
                    )
                }
            } catch (ce: CancellationException) {
                runCatching { partFile.delete() }
                throw ce
            } catch (t: Throwable) {
                runCatching { partFile.delete() }
                mirrorFailures += "${mirror.label}: ${t.localizedMessage ?: t.javaClass.simpleName}"
            }
        }

        runCatching { partFile.delete() }
        Result.failure(
            IllegalStateException(
                "All ${mirrors.size} mirrors failed:\n• " + mirrorFailures.joinToString("\n• ")
            )
        )
    }

    private fun defaultMirrorHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
}
