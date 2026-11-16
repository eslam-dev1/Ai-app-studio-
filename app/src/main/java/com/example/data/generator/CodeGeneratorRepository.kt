package com.example.data.generator

import android.content.Context
import com.example.BuildConfig
import com.example.model.AiEngineMode
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GenerationEngineTarget(val displayTitle: String) {
    LOCAL_AI_OFFLINE("Local AI (Offline)"),
    GEMINI_CLOUD_API("Gemini Cloud API"),
    LOCAL_SERVER("Local Server (Ollama / Termux)")
}

data class GenerationRepositoryConfig(
    val engineMode: AiEngineMode = AiEngineMode.ON_DEVICE_MEDIAPIPE,
    val target: GenerationEngineTarget = GenerationEngineTarget.LOCAL_AI_OFFLINE,
    val activeDataSourceName: String = "OnDeviceMediaPipeGenerator",
    val activeModelLabel: String = BundledModelManager.BUNDLED_DISPLAY_NAME,
    val isConfiguredReady: Boolean = false,
    val statusDetail: String = "Using on-device MediaPipe LLM inference"
) {
    companion object {
        fun fromSettings(
            settings: StudioSettings,
            buildConfigGeminiKey: String = ""
        ): GenerationRepositoryConfig {
            val validBuildConfigGemini = buildConfigGeminiKey.trim()
                .takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
                .orEmpty()

            return when (settings.aiEngineMode) {
                AiEngineMode.ON_DEVICE_MEDIAPIPE -> {
                    val hasModelPath = settings.localModelFilePath.isNotBlank()
                    val modelLabel = settings.localModelDisplayName.ifBlank {
                        BundledModelManager.BUNDLED_DISPLAY_NAME
                    }
                    GenerationRepositoryConfig(
                        engineMode = settings.aiEngineMode,
                        target = GenerationEngineTarget.LOCAL_AI_OFFLINE,
                        activeDataSourceName = "OnDeviceMediaPipeGenerator",
                        activeModelLabel = modelLabel,
                        isConfiguredReady = hasModelPath,
                        statusDetail = if (hasModelPath) {
                            "Offline ready • $modelLabel"
                        } else {
                            "Preparing bundled offline model…"
                        }
                    )
                }

                AiEngineMode.CLOUD_OPENROUTER -> {
                    val hasGeminiKey = settings.geminiApiKey.isNotBlank() ||
                        settings.openRouterApiKey.trim().startsWith("AIza") ||
                        validBuildConfigGemini.isNotBlank()
                    val hasOpenRouterKey = settings.openRouterApiKey.isNotBlank() &&
                        !settings.openRouterApiKey.trim().startsWith("AIza")
                    val modelLabel = if (hasGeminiKey && OpenRouterModels.isGeminiModel(settings.openRouterModel)) {
                        "Gemini (${OpenRouterModels.directGeminiFallbackChain.first()})"
                    } else {
                        OpenRouterModels.shortDisplayName(settings.openRouterModel)
                    }
                    val detail = when {
                        hasGeminiKey -> "Gemini Cloud API active ($modelLabel)"
                        hasOpenRouterKey -> "OpenRouter Cloud active ($modelLabel)"
                        else -> "Enter a Gemini API Key or OpenRouter Key"
                    }
                    GenerationRepositoryConfig(
                        engineMode = settings.aiEngineMode,
                        target = GenerationEngineTarget.GEMINI_CLOUD_API,
                        activeDataSourceName = "OpenRouterGenerator (Gemini Cloud)",
                        activeModelLabel = modelLabel,
                        isConfiguredReady = hasGeminiKey || hasOpenRouterKey,
                        statusDetail = detail
                    )
                }

                AiEngineMode.LOCAL_SERVER -> {
                    val hasEndpoint = settings.localServerBaseUrl.isNotBlank() &&
                        settings.localServerModel.isNotBlank()
                    GenerationRepositoryConfig(
                        engineMode = settings.aiEngineMode,
                        target = GenerationEngineTarget.LOCAL_SERVER,
                        activeDataSourceName = "LocalServerGenerator",
                        activeModelLabel = settings.localServerModel.ifBlank { "Custom Endpoint" },
                        isConfiguredReady = hasEndpoint,
                        statusDetail = "LAN/Termux endpoint • ${settings.localServerBaseUrl}"
                    )
                }
            }
        }
    }
}

class CodeGeneratorRepository(context: Context) : AutoCloseable {

    private val openRouterGenerator: CodeGeneratorDataSource = OpenRouterGenerator()
    private val localServerGenerator: CodeGeneratorDataSource = LocalServerGenerator()
    private val onDeviceGenerator = OnDeviceMediaPipeGenerator(context)

    private val _configuration = MutableStateFlow(
        GenerationRepositoryConfig.fromSettings(
            settings = StudioSettings(),
            buildConfigGeminiKey = BuildConfig.GEMINI_API_KEY
        )
    )
    val configuration: StateFlow<GenerationRepositoryConfig> = _configuration.asStateFlow()

    fun updateConfiguration(settings: StudioSettings): GenerationRepositoryConfig {
        val updated = GenerationRepositoryConfig.fromSettings(
            settings = settings,
            buildConfigGeminiKey = BuildConfig.GEMINI_API_KEY
        )
        _configuration.value = updated
        return updated
    }

    private fun resolveDataSource(target: GenerationEngineTarget): CodeGeneratorDataSource = when (target) {
        GenerationEngineTarget.GEMINI_CLOUD_API -> openRouterGenerator
        GenerationEngineTarget.LOCAL_SERVER -> localServerGenerator
        GenerationEngineTarget.LOCAL_AI_OFFLINE -> onDeviceGenerator
    }

    suspend fun generateApp(
        prompt: String,
        settings: StudioSettings,
        onProgress: suspend (stepIndex: Int, message: String) -> Unit,
        onStreamPreview: (String) -> Unit = {}
    ): GenerationResult {
        val config = updateConfiguration(settings)
        val dataSource = resolveDataSource(config.target)
        return dataSource.generateApp(prompt, settings, onProgress, onStreamPreview)
    }

    suspend fun initializeLocalModel(
        savedModelPath: String,
        maxTokens: Int = BundledModelManager.DEFAULT_MAX_TOKENS,
        temperature: Float = BundledModelManager.DEFAULT_TEMPERATURE
    ) {
        onDeviceGenerator.initializeModel(
            savedModelPath = savedModelPath,
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    suspend fun runLocalPromptBenchmarkStep(
        savedModelPath: String,
        prompt: String,
        onStreamToken: (String) -> Unit = {}
    ): Pair<String, Long> = onDeviceGenerator.runRawPrompt(
        savedModelPath = savedModelPath,
        prompt = prompt,
        onStreamToken = onStreamToken
    )

    override fun close() {
        onDeviceGenerator.close()
    }
}
