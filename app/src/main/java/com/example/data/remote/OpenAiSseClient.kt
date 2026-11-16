package com.example.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiHttpStatusException(
    val statusCode: Int,
    override val message: String
) : IllegalStateException(message)

@Serializable
private data class StreamingChatRequestDto(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val stream: Boolean = true
)

@Serializable
private data class SseChunkDto(
    val model: String? = null,
    val choices: List<SseChoiceDto> = emptyList(),
    val usage: ChatUsage? = null,
    val error: ApiErrorDetail? = null
)

@Serializable
private data class SseChoiceDto(
    val delta: SseDeltaDto? = null,
    val message: ChatMessage? = null
)

@Serializable
private data class SseDeltaDto(
    val content: String? = null
)

data class StreamCompletionOutput(
    val content: String,
    val modelUsed: String?,
    val tokensUsed: Long?
)

object OpenAiSseClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun streamChatCompletion(
        client: OkHttpClient,
        endpointUrl: String,
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
        authorization: String? = null,
        referer: String? = null,
        title: String? = null,
        onTokenChunk: (accumulatedPreview: String) -> Unit
    ): StreamCompletionOutput {
        val payloadJson = json.encodeToString(
            StreamingChatRequestDto(
                model = model,
                messages = messages,
                temperature = temperature,
                max_tokens = maxTokens,
                stream = true
            )
        )

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Accept", "text/event-stream")
            .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!authorization.isNullOrBlank()) {
            reqBuilder.header("Authorization", authorization)
        }
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("HTTP-Referer", referer)
        }
        if (!title.isNullOrBlank()) {
            reqBuilder.header("X-Title", title)
        }

        val call = client.newCall(reqBuilder.build())
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = runCatching { response.body?.string() }.getOrNull().orEmpty()
                throw OpenAiHttpStatusException(
                    statusCode = response.code,
                    message = errBody.ifBlank { "HTTP ${response.code}: ${response.message}" }
                )
            }

            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val body = response.body ?: throw IllegalStateException("Empty response body from $model")

            // If server returned regular JSON instead of SSE stream, parse it cleanly
            if (contentType.contains("application/json")) {
                val rawJson = body.string()
                val parsed = json.decodeFromString<ChatCompletionResponse>(rawJson)
                parsed.error?.message?.let { err ->
                    throw IllegalStateException(err)
                }
                val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                if (text.isNotBlank()) {
                    onTokenChunk(text)
                }
                return StreamCompletionOutput(
                    content = text,
                    modelUsed = parsed.model,
                    tokensUsed = parsed.usage?.totalTokens?.takeIf { it > 0L }
                )
            }

            val accumulated = StringBuilder()
            var resolvedModel: String? = null
            var resolvedTokens: Long? = null
            var lastEmitLength = 0

            val reader = body.charStream().buffered()
            reader.use { br ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = br.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) continue

                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val chunk = runCatching { json.decodeFromString<SseChunkDto>(data) }.getOrNull() ?: continue
                    chunk.error?.message?.let { err ->
                        throw IllegalStateException(err)
                    }
                    if (!chunk.model.isNullOrBlank()) {
                        resolvedModel = chunk.model
                    }
                    chunk.usage?.totalTokens?.takeIf { it > 0L }?.let { total ->
                        resolvedTokens = total
                    }
                    val deltaText = chunk.choices.firstOrNull()?.delta?.content
                        ?: chunk.choices.firstOrNull()?.message?.content
                    if (!deltaText.isNullOrEmpty()) {
                        accumulated.append(deltaText)
                        if (accumulated.length - lastEmitLength >= 24 || deltaText.contains('\n')) {
                            lastEmitLength = accumulated.length
                            onTokenChunk(accumulated.toString())
                        }
                    }
                }
            }

            val finalContent = accumulated.toString()
            if (finalContent.isNotBlank() && finalContent.length != lastEmitLength) {
                onTokenChunk(finalContent)
            }
            return StreamCompletionOutput(
                content = finalContent,
                modelUsed = resolvedModel,
                tokensUsed = resolvedTokens
            )
        }
    }
}
