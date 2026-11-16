package com.example.data.generator

import android.app.ActivityManager
import android.content.Context
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
    private var activeMaxTokens: Int = BundledModelManager.DEFAULT_MAX_TOKENS
    private var activeTemperature: Float = BundledModelManager.DEFAULT_TEMPERATURE

    private fun preferredBackendOrder(savedModelPath: String): List<LlmInference.Backend?> {
        return if (savedModelPath.contains("gpu", ignoreCase = true)) {
            listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)
        } else {
            // Use the modern LiteRT CPU path first (supports multi-prefill / input mask .task models),
            // then fall back to GPU if needed.
            listOf(LlmInference.Backend.CPU, LlmInference.Backend.GPU)
        }
    }

    private fun getOrCreateInference(
        savedModelPath: String,
        maxTokens: Int = activeMaxTokens,
        temperature: Float = activeTemperature
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
        maxTokens: Int = BundledModelManager.DEFAULT_MAX_TOKENS,
        temperature: Float = BundledModelManager.DEFAULT_TEMPERATURE
    ) = withContext(Dispatchers.IO) {
        val file = File(savedModelPath.trim())
        if (!file.exists() || !file.canRead()) {
            throw IllegalStateException("Model file not found at $savedModelPath")
        }
        try {
            getOrCreateInference(file.absolutePath, maxTokens, temperature)
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

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager != null) {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / (1024L * 1024L)
            if (memInfo.lowMemory || availMb < 250L) {
                throw IllegalStateException(
                    "Insufficient free device RAM (${availMb} MB available) to benchmark local model."
                )
            }
        }

        currentCoroutineContext().ensureActive()
        try {
            val inference = getOrCreateInference(
                savedModelPath = modelFile.absolutePath,
                maxTokens = activeMaxTokens,
                temperature = activeTemperature
            )
            currentCoroutineContext().ensureActive()

            val startNs = System.nanoTime()
            val tokenStreamBuffer = StringBuilder()
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(activeTemperature)
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
        if (savedModelPath.isBlank()) {
            throw IllegalArgumentException(
                "No on-device model selected. Select or restore the built-in .task model in the Local AI Model Manager."
            )
        }

        val modelFile = File(savedModelPath)
        if (!modelFile.exists() || !modelFile.canRead()) {
            throw IllegalStateException(
                "Model file not found or unreadable at: $savedModelPath. Please re-select the .task/.bin model from storage."
            )
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager != null) {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / (1024L * 1024L)
            val modelMb = modelFile.length() / (1024L * 1024L)
            if (memInfo.lowMemory || (modelMb > 0 && availMb < 300L)) {
                throw IllegalStateException(
                    "Insufficient free device RAM (${availMb} MB available) to load on-device model (${modelMb} MB). Close background apps or switch to Cloud AI."
                )
            }
        }

        onProgress(0, "Loading on-device model (${modelFile.name})…")
        currentCoroutineContext().ensureActive()

        try {
            val inference = getOrCreateInference(
                savedModelPath = modelFile.absolutePath,
                maxTokens = activeMaxTokens,
                temperature = activeTemperature
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
                .setTemperature(activeTemperature)
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
                // Fallback to direct LlmInference.generateResponseAsync(prompt, progressListener)
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

            if (finalOutput.isBlank()) {
                throw IllegalStateException("On-device MediaPipe model returned an empty output.")
            }

            onProgress(2, "Extracting Kotlin/Compose files & structuring Gradle…")
            val payload = JsonProjectExtractor.extract(finalOutput)
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
        } catch (ule: UnsatisfiedLinkError) {
            throw IllegalStateException(
                "MediaPipe native runtime could not initialize on this device CPU/ABI: ${ule.localizedMessage}",
                ule
            )
        } catch (ex: Exception) {
            throw IllegalStateException(
                "On-device inference failed: ${ex.localizedMessage ?: "Verify this is a compatible MediaPipe .task or .bin LLM bundle."}",
                ex
            )
        }
    }
}
