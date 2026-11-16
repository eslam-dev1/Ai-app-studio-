package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiChatApi {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String? = null,
        @Header("HTTP-Referer") referer: String? = null,
        @Header("X-Title") title: String? = null,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
