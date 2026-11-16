package com.example.data.generator

import com.example.model.GeneratedProjectPayload
import com.example.model.StudioSettings

data class GenerationResult(
    val payload: GeneratedProjectPayload,
    val modelUsed: String
)

interface CodeGeneratorDataSource {
    suspend fun generateApp(
        prompt: String,
        settings: StudioSettings,
        onProgress: suspend (stepIndex: Int, message: String) -> Unit,
        onStreamPreview: (String) -> Unit = {}
    ): GenerationResult
}
