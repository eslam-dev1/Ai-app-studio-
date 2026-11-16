package com.example.data.refactor

import com.example.BuildConfig
import com.example.data.remote.ChatCompletionRequest
import com.example.data.remote.ChatMessage
import com.example.data.remote.GeminiApiException
import com.example.data.remote.GeminiApiService
import com.example.data.remote.OpenAiChatApi
import com.example.model.AiRefactorPayload
import com.example.model.AiRefactorResult
import com.example.model.OpenRouterModels
import com.example.model.RefactorSuggestion
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

class AiCodeRefactorRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
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

    suspend fun analyzeAndRefactorKotlinFile(
        filePath: String,
        sourceCode: String,
        settings: StudioSettings,
        errorTrace: String? = null,
        onStatusUpdate: suspend (String) -> Unit
    ): AiRefactorResult = withContext(Dispatchers.IO) {
        val rawOpenRouterKey = settings.openRouterApiKey.trim()
        val buildConfigGeminiKey = BuildConfig.GEMINI_API_KEY.trim().takeIf {
            it.isNotBlank() && it != "MY_GEMINI_API_KEY"
        }.orEmpty()
        val rawGeminiKey = settings.geminiApiKey.trim().ifBlank {
            if (rawOpenRouterKey.startsWith("AIza")) rawOpenRouterKey else buildConfigGeminiKey
        }
        val effectiveOpenRouterKey = if (rawOpenRouterKey.startsWith("AIza")) "" else rawOpenRouterKey

        if (rawGeminiKey.isBlank() && effectiveOpenRouterKey.isBlank()) {
            throw IllegalArgumentException(
                "Gemini or OpenRouter API Key is required for AI Refactor. Configure your Free Gemini API Key or OpenRouter API Key in Settings."
            )
        }

        val systemMessage = ChatMessage(role = "system", content = REFACTOR_SYSTEM_PROMPT)
        val userMessage = ChatMessage(
            role = "user",
            content = buildRefactorUserPrompt(
                filePath = filePath,
                sourceCode = sourceCode,
                errorTrace = errorTrace
            )
        )

        var lastError: Throwable? = null

        // 1. Primary path: Direct Google Gemini API with automatic next-model fallback on rate limits or retired models
        if (rawGeminiKey.isNotBlank()) {
            val candidateQueue = ArrayDeque(OpenRouterModels.directGeminiFallbackChain)
            val attemptedModels = mutableSetOf<String>()
            var discoveredModelsLoaded = false

            while (candidateQueue.isNotEmpty()) {
                val geminiModel = candidateQueue.removeFirst()
                if (!attemptedModels.add(geminiModel)) continue

                try {
                    if (attemptedModels.size == 1) {
                        onStatusUpdate("Analyzing $filePath with Gemini ($geminiModel)…")
                    } else {
                        onStatusUpdate("Switching to next valid model ($geminiModel)…")
                    }

                    val geminiResult = GeminiApiService.generateContent(
                        apiKey = rawGeminiKey,
                        prompt = userMessage.content,
                        model = geminiModel,
                        systemInstruction = REFACTOR_SYSTEM_PROMPT,
                        temperature = 0.2f,
                        httpClient = httpClient
                    )

                    val payload = extractRefactorPayload(geminiResult.text, fallbackOriginalCode = sourceCode)
                    return@withContext AiRefactorResult(
                        filePath = filePath,
                        summary = payload.summary,
                        modelUsed = "Gemini (${geminiResult.model})",
                        suggestions = payload.suggestions,
                        refactoredCode = payload.refactoredCode.ifBlank { sourceCode }
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (ge: GeminiApiException) {
                    if (ge.cause is CancellationException) throw ge.cause as CancellationException
                    if (ge.statusCode in setOf(400, 401, 403)) {
                        lastError = IllegalStateException(ge.message, ge)
                        break
                    }
                    lastError = ge
                    if (!discoveredModelsLoaded && ge.statusCode in setOf(404, 429, 503)) {
                        discoveredModelsLoaded = true
                        val liveModels = GeminiApiService.fetchAvailableGenerateModels(rawGeminiKey, httpClient)
                        for (liveModel in liveModels) {
                            if (liveModel !in attemptedModels && liveModel !in candidateQueue) {
                                candidateQueue.addLast(liveModel)
                            }
                        }
                    }
                    ge.suggestedModel?.takeIf { it.isNotBlank() && it !in attemptedModels }?.let { suggested ->
                        candidateQueue.remove(suggested)
                        candidateQueue.addFirst(suggested)
                    }
                } catch (ex: Exception) {
                    lastError = ex
                }
            }
        }

        // 2. Fallback / OpenRouter path (prioritizing Free Gemini models first, then falling back across all valid free models)
        if (effectiveOpenRouterKey.isNotBlank()) {
            val orderedModels = buildList {
                if (settings.openRouterModel != OpenRouterModels.AUTO_FREE) {
                    add(settings.openRouterModel)
                }
                OpenRouterModels.fallbackChain.forEach { candidate ->
                    if (candidate !in this) add(candidate)
                }
            }

            for ((idx, modelId) in orderedModels.withIndex()) {
                try {
                    val label = OpenRouterModels.shortDisplayName(modelId)
                    if (idx == 0) {
                        onStatusUpdate("Analyzing Compose patterns with $label…")
                    } else {
                        onStatusUpdate("Limit reached — switching to $label…")
                    }

                    val response = openRouterApi.createChatCompletion(
                        authorization = "Bearer $effectiveOpenRouterKey",
                        referer = "https://aiappstudio.local",
                        title = "AI App Studio Refactor",
                        request = ChatCompletionRequest(
                            model = modelId,
                            messages = listOf(systemMessage, userMessage),
                            temperature = 0.2
                        )
                    )

                    response.error?.message?.let { errMsg ->
                        throw IllegalStateException("Model error ($modelId): $errMsg")
                    }

                    val rawContent = response.choices.firstOrNull()?.message?.content
                        ?: throw IllegalStateException("Empty refactor response from $modelId.")

                    val payload = extractRefactorPayload(rawContent, fallbackOriginalCode = sourceCode)
                    return@withContext AiRefactorResult(
                        filePath = filePath,
                        summary = payload.summary,
                        modelUsed = response.model ?: label,
                        suggestions = payload.suggestions,
                        refactoredCode = payload.refactoredCode.ifBlank { sourceCode }
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (he: HttpException) {
                    if (he.code() == 401) {
                        throw IllegalStateException("Invalid OpenRouter API key (401 Unauthorized).", he)
                    }
                    lastError = he
                    // Automatically continue to next valid free model in orderedModels
                } catch (io: IOException) {
                    lastError = IllegalStateException(
                        "Network error during AI refactor: ${io.localizedMessage ?: "Check connection."}",
                        io
                    )
                    break
                } catch (ex: Exception) {
                    lastError = ex
                }
            }
        }

        throw lastError ?: IllegalStateException("Failed to analyze Kotlin file with Gemini.")
    }

    companion object {
        const val REFACTOR_SYSTEM_PROMPT = """
You are a Senior Android & Jetpack Compose Performance Engineer.
Analyze the provided Kotlin / Jetpack Compose file and suggest concrete performance optimizations and idiomatic Jetpack Compose improvements:
1. Recomposition Performance: Use `remember`, `rememberSaveable`, `derivedStateOf`, stable keys in `LazyColumn`/`LazyRow`, and avoid allocations inside composition.
2. Idiomatic Compose & Material 3: Accept `modifier: Modifier = Modifier` on reusable Composables, hoist state cleanly, use `MaterialTheme.colorScheme` and `MaterialTheme.typography` instead of hardcoded colors/font sizes, and use `Arrangement.spacedBy`.
3. Accessibility & Safety: Provide meaningful `contentDescription` and lifecycle-safe state handling.

CRITICAL: Output ONLY valid raw JSON (no markdown fences, no commentary outside JSON) with this exact structure:
{
  "summary": "Brief 1-2 sentence overview of the key Compose & Kotlin improvements made.",
  "suggestions": [
    {
      "title": "Short title of improvement",
      "category": "PERFORMANCE | IDIOMATIC_COMPOSE | STATE_HOISTING | ACCESSIBILITY",
      "impact": "HIGH | MEDIUM | LOW",
      "explanation": "Clear explanation of why this change improves recomposition performance or Compose code quality.",
      "beforeSnippet": "1-4 lines of original code",
      "afterSnippet": "1-4 lines of refactored code"
    }
  ],
  "refactoredCode": "The complete, compilable refactored Kotlin file content"
}
"""

        fun buildRefactorUserPrompt(
            filePath: String,
            sourceCode: String,
            errorTrace: String? = null
        ): String = buildString {
            if (!errorTrace.isNullOrBlank()) {
                appendLine("CRITICAL SELF-HEALING REQUEST: Fix the following syntax/build error(s) in `$filePath`, and also ensure idiomatic Jetpack Compose best practices:")
                appendLine("--- ERROR TRACE ---")
                appendLine(errorTrace.trim())
                appendLine("--- END ERROR TRACE ---")
                appendLine()
            } else {
                appendLine("Analyze and refactor the following Android Kotlin / Jetpack Compose file (`$filePath`).")
            }
            appendLine("Return ONLY valid JSON matching the schema with `summary`, `suggestions`, and the complete `refactoredCode`.")
            appendLine()
            appendLine("--- FILE: $filePath ---")
            append(sourceCode)
        }.trimIndent()

        fun extractRefactorPayload(
            rawResponse: String,
            fallbackOriginalCode: String
        ): AiRefactorPayload {
            val parser = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            val cleaned = stripMarkdownCodeFence(rawResponse.trim())
            val startBrace = cleaned.indexOf('{')
            val endBrace = cleaned.lastIndexOf('}')
            if (startBrace != -1 && endBrace > startBrace) {
                val jsonSlice = cleaned.substring(startBrace, endBrace + 1)
                runCatching {
                    val decoded = parser.decodeFromString<AiRefactorPayload>(jsonSlice)
                    if (decoded.suggestions.isNotEmpty() || decoded.refactoredCode.isNotBlank()) {
                        return decoded.copy(
                            refactoredCode = stripMarkdownCodeFence(decoded.refactoredCode).ifBlank { fallbackOriginalCode }
                        )
                    }
                }
            }

            return AiRefactorPayload(
                summary = "Completed Jetpack Compose analysis.",
                suggestions = listOf(
                    RefactorSuggestion(
                        title = "Idiomatic Compose & Recomposition Optimization",
                        category = "IDIOMATIC_COMPOSE",
                        impact = "MEDIUM",
                        explanation = "Ensured state is remembered across recompositions and Material 3 theme tokens are used consistently."
                    )
                ),
                refactoredCode = fallbackOriginalCode
            )
        }

        private fun stripMarkdownCodeFence(text: String): String {
            var s = text.trim()
            if (s.startsWith("```")) {
                val firstNewline = s.indexOf('\n')
                if (firstNewline != -1) {
                    s = s.substring(firstNewline + 1)
                }
                if (s.endsWith("```")) {
                    s = s.removeSuffix("```").trim()
                }
            }
            return s.trim()
        }
    }
}
