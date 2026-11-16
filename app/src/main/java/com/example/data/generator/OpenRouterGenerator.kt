package com.example.data.generator

import com.example.data.remote.ChatCompletionRequest
import com.example.data.remote.ChatMessage
import com.example.data.remote.OpenAiChatApi
import com.example.data.remote.OpenAiHttpStatusException
import com.example.data.remote.OpenAiSseClient
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterGenerator : CodeGeneratorDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private val openRouterApi: OpenAiChatApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiChatApi::class.java)
    }

    private val googleGeminiApi: OpenAiChatApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiChatApi::class.java)
    }

    override suspend fun generateApp(
        prompt: String,
        settings: StudioSettings,
        onProgress: suspend (stepIndex: Int, message: String) -> Unit,
        onStreamPreview: (String) -> Unit
    ): GenerationResult = withContext(Dispatchers.IO) {
        val rawOpenRouterKey = settings.openRouterApiKey.trim()
        val buildConfigGeminiKey = com.example.BuildConfig.GEMINI_API_KEY.trim().takeIf {
            it.isNotBlank() && it != "MY_GEMINI_API_KEY"
        }.orEmpty()
        val rawGeminiKey = settings.geminiApiKey.trim().ifBlank {
            if (rawOpenRouterKey.startsWith("AIza")) rawOpenRouterKey else buildConfigGeminiKey
        }
        val effectiveOpenRouterKey = if (rawOpenRouterKey.startsWith("AIza")) "" else rawOpenRouterKey

        if (effectiveOpenRouterKey.isBlank() && rawGeminiKey.isBlank()) {
            throw IllegalArgumentException(
                "Cloud API key is missing. Open Settings to enter a Free Gemini API Key (Google AI Studio) or an OpenRouter API Key."
            )
        }

        onProgress(0, "Designing architecture…")
        var lastError: Throwable? = null

        val wantsGeminiFirst = OpenRouterModels.isGeminiModel(settings.openRouterModel) ||
            settings.openRouterModel == OpenRouterModels.AUTO_FREE ||
            effectiveOpenRouterKey.isBlank()

        val systemAndUserMessages = listOf(
            ChatMessage(role = "system", content = AppGenerationPrompt.SYSTEM_PROMPT),
            ChatMessage(role = "user", content = AppGenerationPrompt.buildUserMessage(prompt))
        )

        if (rawGeminiKey.isNotBlank() && wantsGeminiFirst) {
            for ((idx, geminiModel) in OpenRouterModels.directGeminiFallbackChain.withIndex()) {
                try {
                    if (idx == 0) {
                        onProgress(1, "Streaming Kotlin & Compose code ($geminiModel)…")
                    } else {
                        onProgress(1, "Limit reached — switching to $geminiModel…")
                    }

                    val streamed = runCatching {
                        OpenAiSseClient.streamChatCompletion(
                            client = httpClient,
                            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                            model = geminiModel,
                            messages = systemAndUserMessages,
                            temperature = 0.25,
                            maxTokens = 8192,
                            authorization = "Bearer $rawGeminiKey",
                            onTokenChunk = onStreamPreview
                        )
                    }.getOrElse { sseErr ->
                        if (sseErr is CancellationException) throw sseErr
                        if (sseErr is OpenAiHttpStatusException && sseErr.statusCode in setOf(401, 403, 429)) {
                            throw sseErr
                        }
                        val fallbackResp = googleGeminiApi.createChatCompletion(
                            authorization = "Bearer $rawGeminiKey",
                            referer = null,
                            title = null,
                            request = ChatCompletionRequest(
                                model = geminiModel,
                                messages = systemAndUserMessages
                            )
                        )
                        val text = fallbackResp.choices.firstOrNull()?.message?.content.orEmpty()
                        if (text.isNotBlank()) onStreamPreview(text)
                        com.example.data.remote.StreamCompletionOutput(
                            content = text,
                            modelUsed = fallbackResp.model,
                            tokensUsed = fallbackResp.usage?.totalTokens?.takeIf { it > 0L }
                        )
                    }

                    val rawContent = streamed.content
                    if (rawContent.isBlank()) {
                        throw IllegalStateException("Gemini returned an empty response for $geminiModel.")
                    }

                    onProgress(2, "Extracting files & structuring Gradle…")
                    val payload = JsonProjectExtractor.extract(rawContent)
                    return@withContext GenerationResult(
                        payload = payload,
                        modelUsed = "Gemini Free ($geminiModel)",
                        tokensUsed = streamed.tokensUsed
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (se: OpenAiHttpStatusException) {
                    if (se.statusCode == 401 || se.statusCode == 403) {
                        lastError = IllegalStateException("Invalid Google Gemini API key (${se.statusCode}).", se)
                        break
                    }
                    lastError = se
                } catch (he: HttpException) {
                    if (he.code() == 401 || he.code() == 403) {
                        lastError = IllegalStateException("Invalid Google Gemini API key (${he.code()}).", he)
                        break
                    }
                    lastError = he
                } catch (ex: Exception) {
                    lastError = ex
                }
            }
        }

        if (effectiveOpenRouterKey.isNotBlank()) {
            val orderedModels = if (settings.openRouterModel == OpenRouterModels.AUTO_FREE) {
                OpenRouterModels.fallbackChain
            } else {
                listOf(settings.openRouterModel) +
                    OpenRouterModels.fallbackChain.filterNot { it == settings.openRouterModel }
            }

            for ((idx, modelId) in orderedModels.withIndex()) {
                try {
                    val label = OpenRouterModels.shortDisplayName(modelId)
                    if (idx > 0) {
                        onProgress(1, "Limit reached — switching to $label…")
                    } else {
                        onProgress(1, "Streaming Kotlin & Compose code ($label)…")
                    }

                    val streamed = runCatching {
                        OpenAiSseClient.streamChatCompletion(
                            client = httpClient,
                            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                            model = modelId,
                            messages = systemAndUserMessages,
                            temperature = 0.25,
                            maxTokens = 8192,
                            authorization = "Bearer $effectiveOpenRouterKey",
                            referer = "https://aiappstudio.local",
                            title = "AI App Studio",
                            onTokenChunk = onStreamPreview
                        )
                    }.getOrElse { sseErr ->
                        if (sseErr is CancellationException) throw sseErr
                        if (sseErr is OpenAiHttpStatusException && sseErr.statusCode in setOf(401, 402, 429)) {
                            throw sseErr
                        }
                        val fallbackResp = openRouterApi.createChatCompletion(
                            authorization = "Bearer $effectiveOpenRouterKey",
                            referer = "https://aiappstudio.local",
                            title = "AI App Studio",
                            request = ChatCompletionRequest(
                                model = modelId,
                                messages = systemAndUserMessages
                            )
                        )
                        fallbackResp.error?.message?.let { errMsg ->
                            throw IllegalStateException("OpenRouter error ($modelId): $errMsg")
                        }
                        val text = fallbackResp.choices.firstOrNull()?.message?.content.orEmpty()
                        if (text.isNotBlank()) onStreamPreview(text)
                        com.example.data.remote.StreamCompletionOutput(
                            content = text,
                            modelUsed = fallbackResp.model,
                            tokensUsed = fallbackResp.usage?.totalTokens?.takeIf { it > 0L }
                        )
                    }

                    val rawContent = streamed.content
                    if (rawContent.isBlank()) {
                        throw IllegalStateException("OpenRouter returned an empty response for $modelId.")
                    }

                    onProgress(2, "Extracting files & structuring Gradle…")
                    val payload = JsonProjectExtractor.extract(rawContent)
                    return@withContext GenerationResult(
                        payload = payload,
                        modelUsed = streamed.modelUsed ?: modelId,
                        tokensUsed = streamed.tokensUsed
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (se: OpenAiHttpStatusException) {
                    val msg = when (se.statusCode) {
                        401 -> "Invalid OpenRouter API key (401 Unauthorized)."
                        402 -> "Insufficient OpenRouter credits (402)."
                        429 -> "Rate limit reached on $modelId (429)."
                        else -> "OpenRouter HTTP ${se.statusCode}: ${se.message}"
                    }
                    lastError = IllegalStateException(msg, se)
                    if (se.statusCode == 401) throw lastError
                } catch (he: HttpException) {
                    val errorBody = runCatching { he.response()?.errorBody()?.string() }.getOrNull()
                    val msg = when (he.code()) {
                        401 -> "Invalid OpenRouter API key (401 Unauthorized)."
                        402 -> "Insufficient OpenRouter credits (402)."
                        429 -> "Rate limit reached on $modelId (429)."
                        else -> "OpenRouter HTTP ${he.code()}: ${errorBody ?: he.message()}"
                    }
                    lastError = IllegalStateException(msg, he)
                    if (he.code() == 401) throw lastError
                } catch (io: IOException) {
                    lastError = IllegalStateException(
                        "Network error reaching Cloud AI: ${io.localizedMessage ?: "Check internet connection."}",
                        io
                    )
                    break
                } catch (ex: Exception) {
                    lastError = ex
                }
            }
        }

        throw lastError ?: IllegalStateException("Failed to generate project via Cloud AI.")
    }
}
