package com.example.data.generator

import com.example.data.remote.ChatCompletionRequest
import com.example.data.remote.ChatMessage
import com.example.data.remote.OpenAiChatApi
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class LocalServerGenerator : CodeGeneratorDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun buildApiForUrl(rawBaseUrl: String): OpenAiChatApi {
        val normalizedUrl = normalizeBaseUrl(rawBaseUrl)
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        // Extended 5-minute read/write timeout for local CPU / Termux inference
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(5, TimeUnit.MINUTES)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiChatApi::class.java)
    }

    private fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().ifBlank { "http://127.0.0.1:11434/v1/" }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    override suspend fun generateApp(
        prompt: String,
        settings: StudioSettings,
        onProgress: suspend (stepIndex: Int, message: String) -> Unit,
        onStreamPreview: (String) -> Unit
    ): GenerationResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(settings.localServerBaseUrl)
        val modelName = settings.localServerModel.trim().ifBlank { "qwen2.5-coder:3b" }

        onProgress(0, "Connecting to local server…")
        val api = try {
            buildApiForUrl(baseUrl)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid local server URL '$baseUrl': ${ex.localizedMessage}")
        }

        onProgress(1, "Writing Kotlin & Compose code on $modelName…")

        try {
            val response = api.createChatCompletion(
                authorization = null,
                referer = null,
                title = null,
                request = ChatCompletionRequest(
                    model = modelName,
                    messages = listOf(
                        ChatMessage(role = "system", content = AppGenerationPrompt.SYSTEM_PROMPT),
                        ChatMessage(role = "user", content = AppGenerationPrompt.buildUserMessage(prompt))
                    ),
                    temperature = 0.2,
                    maxTokens = 8192
                )
            )

            response.error?.message?.let { errMsg ->
                throw IllegalStateException("Local server error: $errMsg")
            }

            val rawContent = response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("Local server returned an empty response.")

            onProgress(2, "Structuring Gradle files…")
            val payload = JsonProjectExtractor.extract(rawContent)
            GenerationResult(
                payload = payload,
                modelUsed = response.model ?: modelName
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: ConnectException) {
            throw IllegalStateException(
                "Local server unreachable at $baseUrl. Start Ollama (`ollama serve`) or llama.cpp in Termux/LAN and verify the IP & port.",
                ex
            )
        } catch (ex: UnknownHostException) {
            throw IllegalStateException(
                "Cannot resolve host in '$baseUrl'. Check the IP address or localhost configuration.",
                ex
            )
        } catch (ex: SocketTimeoutException) {
            throw IllegalStateException(
                "Local server timed out after 5 minutes while generating. Try a smaller quantized model (e.g., 1.5B–3B).",
                ex
            )
        } catch (he: HttpException) {
            val errBody = runCatching { he.response()?.errorBody()?.string() }.getOrNull()
            throw IllegalStateException(
                "Local server HTTP ${he.code()}: ${errBody ?: he.message()}. Confirm model '$modelName' is pulled/loaded.",
                he
            )
        } catch (io: IOException) {
            throw IllegalStateException(
                "Connection failed to $baseUrl: ${io.localizedMessage ?: "Ensure server is running."}",
                io
            )
        }
    }
}
