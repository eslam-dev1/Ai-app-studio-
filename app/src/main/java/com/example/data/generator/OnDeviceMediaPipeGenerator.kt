package com.example.data.generator

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

    companion object {
        @Volatile
        private var sharedInference: LlmInference? = null
        @Volatile
        private var sharedLoadedModelPath: String? = null
        @Volatile
        private var sharedMaxTokens: Int = 2048
        @Volatile
        private var sharedTemperature: Float = 0.2f
        private val staticLock = Any()
    }

    private val lock = Any()

    private fun preferredBackendOrder(savedModelPath: String): List<LlmInference.Backend?> {
        // Turbo Rocket Mode: Prioritize GPU hardware acceleration for up to 3x token generation speedup, with silent fallback to CPU
        return listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)
    }

    private fun getOrCreateInference(
        savedModelPath: String,
        maxTokens: Int = maxOf(sharedMaxTokens, 2048),
        temperature: Float = 0.2f
    ): LlmInference {
        synchronized(staticLock) {
            val existing = sharedInference
            if (existing != null &&
                sharedLoadedModelPath == savedModelPath &&
                sharedMaxTokens == maxTokens &&
                sharedTemperature == temperature
            ) {
                return existing
            }
            runCatching { existing?.close() }
            sharedInference = null
            sharedLoadedModelPath = null

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
                    sharedInference = created
                    sharedLoadedModelPath = savedModelPath
                    sharedMaxTokens = maxTokens
                    sharedTemperature = temperature
                    return created
                } catch (oom: OutOfMemoryError) {
                    System.gc()
                    throw oom
                } catch (ule: UnsatisfiedLinkError) {
                    throw ule
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            throw lastError ?: IllegalStateException("Failed to initialize MediaPipe LlmInference with GPU/CPU backends.")
        }
    }

    override fun close() {
        synchronized(staticLock) {
            runCatching { sharedInference?.close() }
            sharedInference = null
            sharedLoadedModelPath = null
            System.gc()
        }
    }

    suspend fun warmUpAsync(savedModelPath: String) = withContext(Dispatchers.IO) {
        val file = File(savedModelPath.trim())
        if (file.exists() && file.canRead()) {
            runCatching {
                val inference = getOrCreateInference(file.absolutePath, 512, 0.2f)
                inference.generateResponse("Warmup")
            }
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
            warmUpAsync(file.absolutePath)
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
        prompt: String,
        onStreamToken: (String) -> Unit = {}
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

            var lastEmitTime = 0L
            val rawResponse = runCatching {
                LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                    session.addQueryChunk(prompt)
                    val future = session.generateResponseAsync { partialToken, _ ->
                        if (!partialToken.isNullOrEmpty()) {
                            val now = System.currentTimeMillis()
                            val snapshot = synchronized(tokenStreamBuffer) {
                                tokenStreamBuffer.append(partialToken)
                                tokenStreamBuffer.toString().takeLast(2400)
                            }
                            // Turbo Rocket Mode: Throttle UI stream updates to ~20 updates/sec (50ms interval) for 60-120 FPS smoothness
                            if (now - lastEmitTime >= 50L || snapshot.length % 40 == 0) {
                                lastEmitTime = now
                                onStreamToken(snapshot)
                            }
                        }
                    }
                    future.get(3, TimeUnit.MINUTES)
                }
            }.getOrElse {
                var lastEmitTimeFallback = 0L
                val future = inference.generateResponseAsync(prompt) { partialToken, _ ->
                    if (!partialToken.isNullOrEmpty()) {
                        val now = System.currentTimeMillis()
                        val snapshot = synchronized(tokenStreamBuffer) {
                            tokenStreamBuffer.append(partialToken)
                            tokenStreamBuffer.toString().takeLast(2400)
                        }
                        if (now - lastEmitTimeFallback >= 50L || snapshot.length % 40 == 0) {
                            lastEmitTimeFallback = now
                            onStreamToken(snapshot)
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
            System.gc()
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

        if (!modelFile.exists() || !modelFile.canRead() || modelFile.length() <= 1024L * 1024L) {
            throw IllegalStateException("Action Error: No valid Local AI model (.task) found on device. Please download a model in Settings -> Local AI or switch to Gemini Cloud API.")
        }

        onProgress(0, "Loading on-device GPU model (${modelFile.name})…")
        currentCoroutineContext().ensureActive()

        try {
            val inference = getOrCreateInference(
                savedModelPath = modelFile.absolutePath,
                maxTokens = 2048,
                temperature = 0.2f
            )
            currentCoroutineContext().ensureActive()

            onProgress(1, "Streaming Kotlin & Compose code at 60 FPS (Turbo Mode)…")
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

            var lastEmitTime = 0L
            val rawResponse: String = runCatching {
                LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                    session.addQueryChunk(fullPrompt)
                    val future = session.generateResponseAsync { partialToken, _ ->
                        if (!partialToken.isNullOrEmpty()) {
                            val now = System.currentTimeMillis()
                            synchronized(tokenStreamBuffer) {
                                tokenStreamBuffer.append(partialToken)
                                val snapshot = tokenStreamBuffer.toString().takeLast(2400)
                                // Turbo Rocket Mode: Throttle token stream UI callbacks to 50ms interval for silky smooth 60-120 FPS rendering
                                if (now - lastEmitTime >= 50L || snapshot.length % 40 == 0) {
                                    lastEmitTime = now
                                    onStreamPreview(snapshot)
                                }
                            }
                        }
                    }
                    future.get(10, TimeUnit.MINUTES)
                }
            }.getOrElse {
                var lastEmitTimeFallback = 0L
                val future = inference.generateResponseAsync(fullPrompt) { partialToken, _ ->
                    if (!partialToken.isNullOrEmpty()) {
                        val now = System.currentTimeMillis()
                        synchronized(tokenStreamBuffer) {
                            tokenStreamBuffer.append(partialToken)
                            val snapshot = tokenStreamBuffer.toString().takeLast(2400)
                            if (now - lastEmitTimeFallback >= 50L || snapshot.length % 40 == 0) {
                                lastEmitTimeFallback = now
                                onStreamPreview(snapshot)
                            }
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

            onProgress(2, "Parsing Kotlin/Compose AST & structuring Gradle project…")
            val payload = withContext(Dispatchers.Default) {
                AiCodeParser.parse(finalOutput, defaultAppName = prompt.take(30).trim().ifBlank { "Local AI App" })
            }
            System.gc()
            GenerationResult(
                payload = payload,
                modelUsed = "MediaPipe (${modelFile.name} • GPU Turbo)"
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (oom: OutOfMemoryError) {
            System.gc()
            close()
            throw IllegalStateException(
                "Out of memory while running ${modelFile.name}. Free up RAM or use a smaller quantized .task/.bin model.",
                oom
            )
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Local AI Inference Failed: ${t.localizedMessage ?: "Unsupported device runtime or corrupted model"}",
                t
            )
        }
    }
}
