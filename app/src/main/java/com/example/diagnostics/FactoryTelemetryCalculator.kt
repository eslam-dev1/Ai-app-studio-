package com.example.diagnostics

import com.example.model.BuildPhase

data class CloudBuildTelemetry(
    val buildPhase: BuildPhase,
    val progress: Float,
    val statusTicker: String
)

object FactoryTelemetryCalculator {

    private val FILE_PATH_REGEX = Regex(""""path"\s*:\s*"([^"]+)"""")
    private val KOTLIN_FILE_HINT_REGEX = Regex("""//\s*([A-Za-z0-9_]+\.(?:kt|kts|xml))""")
    private val UPLOAD_FRACTION_REGEX = Regex("""\((\d+)/(\d+)\)""")
    private val HYPHENATED_REPEAT_REGEX = Regex("""\b([A-Za-z]{2,18})(?:-\1){3,}\b""", RegexOption.IGNORE_CASE)
    private val WORD_REPEAT_REGEX = Regex("""\b([A-Za-z_][A-Za-z0-9_]{1,20})(?:[ \t]+\1){3,}\b""", RegexOption.IGNORE_CASE)

    fun activeNodeForPhase(phase: BuildPhase): Int = phase.nodeIndex.coerceIn(0, 2)

    fun calculateStreamProgress(
        charsReceived: Int,
        baseProgress: Float = 0.16f,
        maxStreamProgress: Float = 0.84f,
        estimatedTotalChars: Int = 7200
    ): Float {
        if (charsReceived <= 0) return baseProgress.coerceIn(0f, 1f)
        val fraction = (charsReceived.toFloat() / estimatedTotalChars.coerceAtLeast(1).toFloat())
            .coerceIn(0f, 1f)
        return (baseProgress + (maxStreamProgress - baseProgress) * fraction)
            .coerceIn(baseProgress, maxStreamProgress)
    }

    fun calculateFileWriteProgress(writtenFiles: Int, totalFiles: Int): Float {
        if (totalFiles <= 0) return 0.90f
        val fraction = (writtenFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        return (0.85f + 0.13f * fraction).coerceIn(0.85f, 0.98f)
    }

    fun calculateDownloadProgress(bytesRead: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        return (bytesRead.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun extractActiveFileLabel(rawStream: String): String? {
        if (rawStream.isBlank()) return null
        val jsonMatch = FILE_PATH_REGEX.findAll(rawStream).lastOrNull()?.groupValues?.getOrNull(1)
        if (!jsonMatch.isNullOrBlank()) {
            return jsonMatch.substringAfterLast('/').takeIf { it.isNotBlank() }
        }
        val commentMatch = KOTLIN_FILE_HINT_REGEX.findAll(rawStream).lastOrNull()?.groupValues?.getOrNull(1)
        return commentMatch?.takeIf { it.isNotBlank() }
    }

    /**
     * Sanitizes live AI token output so runaway repetitive token loops from small quantized
     * models (e.g. `compose-compose-compose-compose`) cannot overflow or stall the UI.
     */
    fun sanitizeStreamingCode(raw: String): String {
        if (raw.isBlank()) return ""
        val cleaned = raw
            .replace("\r\n", "\n")
            .let { text -> HYPHENATED_REPEAT_REGEX.replace(text) { m -> "${m.groupValues[1]}…" } }
            .let { text -> WORD_REPEAT_REGEX.replace(text) { m -> "${m.groupValues[1]} …" } }
        return cleaned.takeLast(3200)
    }

    fun resolveCloudApkBuildTelemetry(progressMessage: String?): CloudBuildTelemetry {
        val msg = progressMessage?.trim().orEmpty()
        if (msg.isBlank()) {
            return CloudBuildTelemetry(
                buildPhase = BuildPhase.PROMPTING,
                progress = 0.12f,
                statusTicker = "Preparing GitHub Actions build…"
            )
        }

        val lower = msg.lowercase()
        val uploadMatch = UPLOAD_FRACTION_REGEX.find(msg)
        if (uploadMatch != null && (lower.contains("upload") || lower.contains("push"))) {
            val current = uploadMatch.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val total = uploadMatch.groupValues[2].toIntOrNull()?.coerceAtLeast(current) ?: current
            val frac = current.toFloat() / total.toFloat()
            val progress = (0.30f + 0.45f * frac).coerceIn(0.30f, 0.78f)
            return CloudBuildTelemetry(
                buildPhase = BuildPhase.GENERATING_CODE,
                progress = progress,
                statusTicker = msg
            )
        }

        return when {
            lower.contains("diagnostic") || lower.contains("authenticat") || lower.contains("preparing github") ->
                CloudBuildTelemetry(BuildPhase.PROMPTING, 0.18f, msg)
            lower.contains("creating github") ->
                CloudBuildTelemetry(BuildPhase.PROMPTING, 0.26f, msg)
            lower.contains("push") || lower.contains("upload") ->
                CloudBuildTelemetry(BuildPhase.GENERATING_CODE, 0.52f, msg)
            lower.contains("download") ->
                CloudBuildTelemetry(BuildPhase.PACKAGING_APK, 0.90f, msg)
            lower.contains("trigger") || lower.contains("workflow") || lower.contains("checking github") ->
                CloudBuildTelemetry(BuildPhase.PACKAGING_APK, 0.84f, msg)
            else ->
                CloudBuildTelemetry(BuildPhase.GENERATING_CODE, 0.42f, msg)
        }
    }
}
