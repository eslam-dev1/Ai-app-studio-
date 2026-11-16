package com.example.model

import androidx.compose.runtime.Immutable
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class AiEngineMode(val label: String, val shortLabel: String) {
    ON_DEVICE_MEDIAPIPE("Local AI (Built-in Offline)", "Local AI"),
    CLOUD_OPENROUTER("Gemini Cloud API", "Gemini Cloud"),
    LOCAL_SERVER("Local Server (Ollama / Termux)", "Local Server")
}

@Serializable
enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Serializable
enum class EditorThemeMode(val label: String, val subtitle: String) {
    CYBER_NIGHT("Cyber Night", "Deep obsidian background with glowing neon syntax"),
    GLACIER_DAY("Glacier Day", "Clean white background with crisp high-contrast syntax")
}

@Serializable
enum class ApiUsageLimitMetric(val label: String, val unitLabel: String) {
    REQUESTS("Requests", "requests"),
    TOKENS("Tokens", "tokens")
}

object OpenRouterModels {
    const val GEMINI_20_FLASH_FREE = "google/gemini-2.0-flash-exp:free"
    const val GEMINI_25_PRO_FREE = "google/gemini-2.5-pro-exp-03-25:free"
    const val QWEN_CODER_FREE = "qwen/qwen3-coder:free"
    const val LLAMA_33_FREE = "meta-llama/llama-3.3-70b-instruct:free"
    const val GEMMA_3_27B_FREE = "google/gemma-3-27b-it:free"
    const val AUTO_FREE = "Auto Free"

    val allOptions = listOf(
        GEMINI_20_FLASH_FREE,
        GEMINI_25_PRO_FREE,
        QWEN_CODER_FREE,
        LLAMA_33_FREE,
        GEMMA_3_27B_FREE,
        AUTO_FREE
    )

    val fallbackChain = listOf(
        GEMINI_20_FLASH_FREE,
        GEMINI_25_PRO_FREE,
        QWEN_CODER_FREE,
        LLAMA_33_FREE,
        GEMMA_3_27B_FREE
    )

    val directGeminiFallbackChain = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

    fun shortDisplayName(modelId: String): String = when (modelId) {
        GEMINI_20_FLASH_FREE -> "Free Gemini 2.0 Flash"
        GEMINI_25_PRO_FREE -> "Free Gemini 2.5 Pro"
        QWEN_CODER_FREE -> "Qwen3 Coder (Free)"
        LLAMA_33_FREE -> "Llama 3.3 70B (Free)"
        GEMMA_3_27B_FREE -> "Gemma 3 27B (Free)"
        AUTO_FREE -> "Auto Free Fallback"
        else -> modelId
    }

    fun isGeminiModel(modelId: String): Boolean =
        modelId.contains("gemini", ignoreCase = true)
}

@Immutable
@Serializable
data class GeneratedFile(
    val path: String,
    val content: String
)

@Immutable
@Serializable
data class GeneratedProjectPayload(
    val appName: String,
    val packageName: String = "com.generated.app",
    val files: List<GeneratedFile> = emptyList()
)

@Immutable
@Serializable
data class StudioProject(
    val id: String,
    val appName: String,
    val packageName: String,
    val prompt: String,
    val engineMode: AiEngineMode = AiEngineMode.ON_DEVICE_MEDIAPIPE,
    val modelUsed: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val files: List<GeneratedFile> = emptyList(),
    val gitHubRepoOwner: String? = null,
    val gitHubRepoName: String? = null,
    val gitHubRepoUrl: String? = null,
    val lastWorkflowRunId: Long? = null,
    val lastBuildStatus: String? = null,
    val lastArtifactUrl: String? = null,
    val lastActionsUrl: String? = null,
    val lastGenerationLatencyMs: Long = 0L
)

@Immutable
data class StudioSettings(
    val aiEngineMode: AiEngineMode = AiEngineMode.ON_DEVICE_MEDIAPIPE,
    val openRouterApiKey: String = "",
    val geminiApiKey: String = "",
    val openRouterModel: String = OpenRouterModels.GEMINI_20_FLASH_FREE,
    val localServerBaseUrl: String = "http://127.0.0.1:11434/v1/",
    val localServerModel: String = "qwen2.5-coder:3b",
    val localModelFilePath: String = "",
    val localModelDisplayName: String = "",
    val localModelSizeBytes: Long = 0L,
    val huggingFaceToken: String = DEFAULT_HUGGING_FACE_TOKEN,
    val gitHubPat: String = "",
    val gitHubUsername: String = "",
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val editorThemeMode: EditorThemeMode = EditorThemeMode.CYBER_NIGHT,
    val monthlyUsageAlertEnabled: Boolean = true,
    val monthlyUsageLimitMetric: ApiUsageLimitMetric = ApiUsageLimitMetric.REQUESTS,
    val monthlyUsageLimit: Long = DEFAULT_MONTHLY_REQUEST_LIMIT,
    val monthlyUsageWarningThresholdPercent: Int = DEFAULT_WARNING_THRESHOLD_PERCENT
) {
    companion object {
        const val DEFAULT_HUGGING_FACE_TOKEN = "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"
        const val DEFAULT_MONTHLY_REQUEST_LIMIT = 100L
        const val DEFAULT_MONTHLY_TOKEN_LIMIT = 100_000L
        const val DEFAULT_WARNING_THRESHOLD_PERCENT = 80
    }
}

enum class BuildPhase(val label: String, val nodeIndex: Int) {
    PROMPTING("Preparing", 0),
    GENERATING_CODE("Generating Code", 1),
    PARSING_FILES("Writing Files", 2),
    PACKAGING_APK("Packaging APK", 2)
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data class Generating(
        val stepIndex: Int,
        val stepMessage: String,
        val engineLabel: String,
        val streamingPreview: String = "",
        val buildPhase: BuildPhase = BuildPhase.PROMPTING,
        val progress: Float = 0.08f,
        val charsReceived: Int = 0,
        val tokensReceived: Int = 0,
        val activeFileLabel: String? = null
    ) : GenerationUiState
    data class Success(val projectId: String, val appName: String) : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

enum class GitChangeType(val label: String) {
    UNTRACKED("Untracked"),
    MODIFIED("Modified"),
    ADDED("Staged New"),
    STAGED_MODIFIED("Staged"),
    DELETED("Deleted")
}

@Immutable
data class GitFileChange(
    val path: String,
    val status: GitChangeType,
    val isStaged: Boolean
)

@Immutable
data class GitCommitInfo(
    val shortSha: String,
    val message: String,
    val author: String,
    val timestampMs: Long
)

@Immutable
data class GitRepoState(
    val isInitialized: Boolean = false,
    val branch: String = "main",
    val remoteUrl: String? = null,
    val changes: List<GitFileChange> = emptyList(),
    val commits: List<GitCommitInfo> = emptyList(),
    val statusMessage: String? = null
)

enum class AutoSaveStatus(val label: String) {
    IDLE("Saved"),
    PENDING("Editing…"),
    SAVING("Saving…"),
    SAVED("Auto-saved")
}

enum class ConsoleLogLevel(val badge: String, val label: String) {
    ERROR("E", "Errors"),
    WARN("W", "Warnings"),
    INFO("I", "Info"),
    DEBUG("D", "Debug"),
    SUCCESS("S", "Success")
}

@Immutable
data class ConsoleLogEntry(
    val id: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val level: ConsoleLogLevel,
    val tag: String,
    val message: String,
    val filePath: String? = null,
    val lineNumber: Int? = null
)

@Serializable
data class RefactorSuggestion(
    val title: String,
    val category: String = "PERFORMANCE",
    val impact: String = "HIGH",
    val explanation: String,
    val beforeSnippet: String = "",
    val afterSnippet: String = ""
)

@Serializable
data class AiRefactorPayload(
    val summary: String = "Analyzed Kotlin & Jetpack Compose code for performance and idiomatic improvements.",
    val suggestions: List<RefactorSuggestion> = emptyList(),
    val refactoredCode: String = ""
)

data class AiRefactorResult(
    val filePath: String,
    val summary: String,
    val modelUsed: String,
    val suggestions: List<RefactorSuggestion>,
    val refactoredCode: String
)

sealed interface RefactorUiState {
    data object Idle : RefactorUiState
    data class Analyzing(
        val filePath: String,
        val statusMessage: String
    ) : RefactorUiState
    data class ResultReady(val result: AiRefactorResult) : RefactorUiState
    data class Error(val message: String) : RefactorUiState
}

sealed interface LocalModelDownloadState {
    data object Idle : LocalModelDownloadState
    data class Downloading(
        val modelName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int
    ) : LocalModelDownloadState
    data class Initializing(val modelName: String) : LocalModelDownloadState
    data class Error(val message: String) : LocalModelDownloadState
}

@Immutable
@Serializable
data class ProjectSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val versionLabel: String,
    val summary: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val files: List<GeneratedFile> = emptyList()
)


