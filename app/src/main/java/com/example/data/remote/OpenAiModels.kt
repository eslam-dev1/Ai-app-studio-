package com.example.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.25,
    @SerialName("max_tokens")
    val maxTokens: Int = 8192
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val error: ApiErrorDetail? = null,
    val usage: ChatUsage? = null
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Long = 0L,
    @SerialName("completion_tokens")
    val completionTokens: Long = 0L,
    @SerialName("total_tokens")
    val totalTokens: Long = 0L
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    val code: String? = null
)
