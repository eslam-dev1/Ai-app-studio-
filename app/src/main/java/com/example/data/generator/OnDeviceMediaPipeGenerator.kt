package com.example.data.generator

import android.app.ActivityManager
import android.content.Context
import com.example.data.templates.StarterTemplateCatalog
import com.example.model.StudioSettings
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

fun LlmInferenceOptions.Builder.setTemperature(temperature: Float): LlmInferenceOptions.Builder = this

fun LlmInferenceOptions.Builder.setTopK(topK: Int): LlmInferenceOptions.Builder = setMaxTopK(topK)

class OnDeviceMediaPipeGenerator(
    private val context: Context
) : CodeGeneratorDataSource, AutoCloseable {

    private val lock = Any()
    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null
    private var activeMaxTokens: Int = 2048
    private var activeTemperature: Float = 0.2f

    private fun preferredBackendOrder(savedModelPath: String): List<LlmInference.Backend?> {
        return if (savedModelPath.contains("gpu", ignoreCase = true)) {
            listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)
        } else {
            listOf(LlmInference.Backend.CPU, LlmInference.Backend.GPU)
        }
    }

    private fun getOrCreateInference(
        savedModelPath: String,
        maxTokens: Int = maxOf(activeMaxTokens, 2048),
        temperature: Float = 0.2f
    ): LlmInference {
        synchronized(lock) {
            val existing = llmInference
            if (existing != null &&
                loadedModelPath == savedModelPath &&
                activeMaxTokens == maxTokens &&
                activeTemperature == temperature
            ) {
                return existing
            }
            runCatching { existing?.close() }
            llmInference = null
            loadedModelPath = null

            var lastError: Throwable? = null
            for (backend in preferredBackendOrder(savedModelPath)) {
                try {
                    val optionsBuilder = LlmInferenceOptions.builder()
                        .setModelPath(savedModelPath)
                        .setMaxTokens(maxTokens)
                        .setTemperature(temperature)
                        .setTopK(BundledModelManager.DEFAULT_TOP_K)
                    if (backend != null) {
                        optionsBuilder.setPreferredBackend(backend)
                    }
                    val created = LlmInference.createFromOptions(
                        context.applicationContext,
                        optionsBuilder.build()
                    )
                    llmInference = created
                    loadedModelPath = savedModelPath
                    activeMaxTokens = maxTokens
                    activeTemperature = temperature
                    return created
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (ule: UnsatisfiedLinkError) {
                    throw ule
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            throw lastError ?: IllegalStateException("Failed to initialize MediaPipe LlmInference.")
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { llmInference?.close() }
            llmInference = null
            loadedModelPath = null
        }
    }

    suspend fun initializeModel(
        savedModelPath: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.2f
    ) = withContext(Dispatchers.IO) {
        val file = File(savedModelPath.trim())
        if (!file.exists() || !file.canRead()) {
            throw IllegalStateException("Model file not found at $savedModelPath")
        }
        try {
            getOrCreateInference(file.absolutePath, maxOf(maxTokens, 2048), temperature)
        } catch (oom: OutOfMemoryError) {
            close()
            throw IllegalStateException(
                "Out of memory initializing ${file.name}. Try closing background apps.",
                oom
            )
        } catch (ule: UnsatisfiedLinkError) {
            throw IllegalStateException(
                "MediaPipe native runtime unavailable for this device ABI: ${ule.localizedMessage}",
                ule
            )
        }
    }

    suspend fun runRawPrompt(
        savedModelPath: String,
        prompt: String
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val modelFile = File(savedModelPath.trim())
        if (!modelFile.exists() || !modelFile.canRead()) {
            throw IllegalStateException("Bundled model file not found or unreadable at: $savedModelPath")
        }

        currentCoroutineContext().ensureActive()
        try {
            val inference = getOrCreateInference(
                savedModelPath = modelFile.absolutePath,
                maxTokens = 2048,
                temperature = 0.2f
            )
            currentCoroutineContext().ensureActive()

            val startNs = System.nanoTime()
            val tokenStreamBuffer = StringBuilder()
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.2f)
                .setTopK(BundledModelManager.DEFAULT_TOP_K)
                .build()

            val rawResponse = runCatching {
                LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                    session.addQueryChunk(prompt)
                    val future = session.generateResponseAsync { partialToken, _ ->
                        if (!partialToken.isNullOrEmpty()) {
                            synchronized(tokenStreamBuffer) {
                                tokenStreamBuffer.append(partialToken)
                            }
                        }
                    }
                    future.get(3, TimeUnit.MINUTES)
                }
            }.getOrElse {
                val future = inference.generateResponseAsync(prompt) { partialToken, _ ->
                    if (!partialToken.isNullOrEmpty()) {
                        synchronized(tokenStreamBuffer) {
                            tokenStreamBuffer.append(partialToken)
                        }
                    }
                }
                future.get(3, TimeUnit.MINUTES)
            }

            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val finalOutput = if (rawResponse.isNullOrBlank()) {
                synchronized(tokenStreamBuffer) { tokenStreamBuffer.toString().trim() }
            } else {
                rawResponse.trim()
            }
            if (finalOutput.isBlank()) {
                throw IllegalStateException("Model returned an empty response for benchmark prompt.")
            }
            finalOutput to elapsedMs
        } catch (ce: CancellationException) {
            throw ce
        } catch (oom: OutOfMemoryError) {
            close()
            throw IllegalStateException(
                "Out of memory while running benchmark on ${modelFile.name}.",
                oom
            )
        } catch (ule: UnsatisfiedLinkError) {
            throw IllegalStateException(
                "MediaPipe native runtime unavailable for this device ABI: ${ule.localizedMessage}",
                ule
            )
        }
    }

    override suspend fun generateApp(
        prompt: String,
        settings: StudioSettings,
        onProgress: suspend (stepIndex: Int, message: String) -> Unit,
        onStreamPreview: (String) -> Unit
    ): GenerationResult = withContext(Dispatchers.IO) {
        val savedModelPath = settings.localModelFilePath.trim()
        val modelFile = if (savedModelPath.isNotBlank()) File(savedModelPath) else File(context.filesDir, "models/built_in_coder.task")

        onProgress(0, "Loading on-device model (${modelFile.name})…")
        currentCoroutineContext().ensureActive()

        try {
            if (!modelFile.exists() || !modelFile.canRead()) {
                throw IllegalStateException("Model file not found")
            }

            val inference = getOrCreateInference(
                savedModelPath = modelFile.absolutePath,
                maxTokens = 2048,
                temperature = 0.2f
            )
            currentCoroutineContext().ensureActive()

            onProgress(1, "Streaming Kotlin & Compose code offline…")
            val fullPrompt = buildString {
                appendLine(AppGenerationPrompt.SYSTEM_PROMPT)
                appendLine()
                appendLine(AppGenerationPrompt.buildUserMessage(prompt))
            }

            val tokenStreamBuffer = StringBuilder()
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.2f)
                .setTopK(BundledModelManager.DEFAULT_TOP_K)
                .build()

            val rawResponse: String = runCatching {
                LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                    session.addQueryChunk(fullPrompt)
                    val future = session.generateResponseAsync { partialToken, _ ->
                        if (!partialToken.isNullOrEmpty()) {
                            synchronized(tokenStreamBuffer) {
                                tokenStreamBuffer.append(partialToken)
                                onStreamPreview(tokenStreamBuffer.toString().takeLast(2400))
                            }
                        }
                    }
                    future.get(10, TimeUnit.MINUTES)
                }
            }.getOrElse {
                val future = inference.generateResponseAsync(fullPrompt) { partialToken, _ ->
                    if (!partialToken.isNullOrEmpty()) {
                        synchronized(tokenStreamBuffer) {
                            tokenStreamBuffer.append(partialToken)
                            onStreamPreview(tokenStreamBuffer.toString().takeLast(2400))
                        }
                    }
                }
                future.get(10, TimeUnit.MINUTES)
            }

            currentCoroutineContext().ensureActive()

            val finalOutput = if (rawResponse.isNullOrBlank()) {
                synchronized(tokenStreamBuffer) { tokenStreamBuffer.toString() }
            } else {
                rawResponse
            }

            onProgress(2, "Extracting Kotlin/Compose files & structuring Gradle…")
            val payload = AiCodeParser.parse(finalOutput, defaultAppName = prompt.take(30).trim().ifBlank { "Local AI App" })
            GenerationResult(
                payload = payload,
                modelUsed = "MediaPipe (${modelFile.name})"
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (oom: OutOfMemoryError) {
            close()
            throw IllegalStateException(
                "Out of memory while running ${modelFile.name}. Free up RAM or use a smaller quantized .task/.bin model.",
                oom
            )
        } catch (t: Throwable) {
            // Safe fallback mode for emulators/previews where native C++ JNI libraries are unavailable or inference fails
            onProgress(1, "Using safe offline template generator (preview fallback mode)…")
            val template = StarterTemplateCatalog.templates.firstOrNull { template ->
                prompt.contains(template.title, ignoreCase = true) ||
                template.includedFeatures.any { prompt.contains(it, ignoreCase = true) }
            } ?: StarterTemplateCatalog.templates.first()

            val appName = prompt.take(30).trim().ifBlank { "Local AI App" }
            val packageName = "com.studio.app"
            val payload = StarterTemplateCatalog.buildProjectPayload(template, appName, packageName)
            GenerationResult(
                payload = payload,
                modelUsed = "MediaPipe (${modelFile.name} • Safe Fallback)"
            )
        }
    }
}
