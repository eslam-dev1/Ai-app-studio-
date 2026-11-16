package com.example.data.generator

import com.example.data.remote.ChatCompletionRequest
import com.example.data.remote.ChatMessage
import com.example.data.remote.OpenAiChatApi
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

        // 1. If a Gemini model (or Auto Free) is selected and user has a direct Google Gemini API key,
        // try Google's official Free Gemini endpoint with automatic model fallback on rate limits.
        val wantsGeminiFirst = OpenRouterModels.isGeminiModel(settings.openRouterModel) ||
            settings.openRouterModel == OpenRouterModels.AUTO_FREE ||
            effectiveOpenRouterKey.isBlank()

        if (rawGeminiKey.isNotBlank() && wantsGeminiFirst) {
            for ((idx, geminiModel) in OpenRouterModels.directGeminiFallbackChain.withIndex()) {
                try {
                    if (idx == 0) {
                        onProgress(1, "Writing Kotlin & Compose code with Free Gemini ($geminiModel)…")
                    } else {
                        onProgress(1, "Limit reached — switching to $geminiModel…")
                    }

                    val response = googleGeminiApi.createChatCompletion(
                        authorization = "Bearer $rawGeminiKey",
                        referer = null,
                        title = null,
                        request = ChatCompletionRequest(
                            model = geminiModel,
                            messages = listOf(
                                ChatMessage(role = "system", content = AppGenerationPrompt.SYSTEM_PROMPT),
                                ChatMessage(role = "user", content = AppGenerationPrompt.buildUserMessage(prompt))
                            )
                        )
                    )

                    val rawContent = response.choices.firstOrNull()?.message?.content
                        ?: throw IllegalStateException("Gemini returned an empty response for $geminiModel.")

                    onProgress(2, "Structuring Gradle files…")
                    val payload = JsonProjectExtractor.extract(rawContent)
                    return@withContext GenerationResult(
                        payload = payload,
                        modelUsed = "Gemini Free ($geminiModel)"
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (he: HttpException) {
                    if (he.code() == 401 || he.code() == 403) {
                        lastError = IllegalStateException("Invalid Google Gemini API key (${he.code()}).", he)
                        break
                    }
                    lastError = he
                    // Continue to next valid Gemini model in fallback chain on 429 / 503
                } catch (ex: Exception) {
                    lastError = ex
                }
            }
        }

        // 2. Use OpenRouter with automatic model fallback across all valid free models whenever
        // a model's rate limit/quota is finished.
        if (effectiveOpenRouterKey.isNotBlank()) {
            val orderedModels = if (settings.openRouterModel == OpenRouterModels.AUTO_FREE) {
                OpenRouterModels.fallbackChain
            } else {
                // Start with the user's selected model, then automatically fall back to the remaining free models if rate-limited
                listOf(settings.openRouterModel) +
                    OpenRouterModels.fallbackChain.filterNot { it == settings.openRouterModel }
            }

            for ((idx, modelId) in orderedModels.withIndex()) {
                try {
                    val label = OpenRouterModels.shortDisplayName(modelId)
                    if (idx > 0) {
                        onProgress(1, "Limit reached — switching to $label…")
                    } else {
                        onProgress(1, "Writing Kotlin & Compose code ($label)…")
                    }

                    val response = openRouterApi.createChatCompletion(
                        authorization = "Bearer $effectiveOpenRouterKey",
                        referer = "https://aiappstudio.local",
                        title = "AI App Studio",
                        request = ChatCompletionRequest(
                            model = modelId,
                            messages = listOf(
                                ChatMessage(role = "system", content = AppGenerationPrompt.SYSTEM_PROMPT),
                                ChatMessage(role = "user", content = AppGenerationPrompt.buildUserMessage(prompt))
                            )
                        )
                    )

                    response.error?.message?.let { errMsg ->
                        throw IllegalStateException("OpenRouter error ($modelId): $errMsg")
                    }

                    val rawContent = response.choices.firstOrNull()?.message?.content
                        ?: throw IllegalStateException("OpenRouter returned an empty response for $modelId.")

                    onProgress(2, "Structuring Gradle files…")
                    val payload = JsonProjectExtractor.extract(rawContent)
                    return@withContext GenerationResult(
                        payload = payload,
                        modelUsed = response.model ?: modelId
                    )
                } catch (ce: CancellationException) {
                    throw ce
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
