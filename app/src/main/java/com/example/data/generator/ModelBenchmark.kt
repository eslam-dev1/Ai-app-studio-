package com.example.data.generator

data class BenchmarkPromptSpec(
    val id: String,
    val label: String,
    val prompt: String
)

data class BenchmarkPromptResult(
    val id: String,
    val label: String,
    val prompt: String,
    val durationMs: Long,
    val responsePreview: String,
    val charCount: Int,
    val succeeded: Boolean,
    val errorMessage: String? = null
)

data class ModelBenchmarkSummary(
    val modelDisplayName: String,
    val modelPath: String,
    val results: List<BenchmarkPromptResult>,
    val averageMs: Long,
    val minMs: Long,
    val maxMs: Long,
    val totalMs: Long
)

sealed interface ModelBenchmarkUiState {
    data object Idle : ModelBenchmarkUiState
    data class Running(
        val currentIndex: Int,
        val totalPrompts: Int,
        val currentLabel: String,
        val completedResults: List<BenchmarkPromptResult> = emptyList()
    ) : ModelBenchmarkUiState

    data class Completed(val summary: ModelBenchmarkSummary) : ModelBenchmarkUiState
    data class Error(
        val message: String,
        val partialResults: List<BenchmarkPromptResult> = emptyList()
    ) : ModelBenchmarkUiState
}

object ModelBenchmarkDefaults {
    val prompts: List<BenchmarkPromptSpec> = listOf(
        BenchmarkPromptSpec(
            id = "compose_button",
            label = "Compose Button",
            prompt = "Reply in one short sentence: Which Material 3 composable creates a clickable button in Jetpack Compose?"
        ),
        BenchmarkPromptSpec(
            id = "compose_state",
            label = "State Snippet",
            prompt = "Write a 1-line Kotlin snippet using remember and mutableIntStateOf for a counter."
        ),
        BenchmarkPromptSpec(
            id = "coroutine_io",
            label = "Coroutine IO",
            prompt = "Answer in one short phrase: Which Kotlin Coroutine Dispatcher is intended for disk file I/O?"
        )
    )
}

object ModelBenchmarkCalculator {
    fun summarize(
        modelDisplayName: String,
        modelPath: String,
        results: List<BenchmarkPromptResult>
    ): ModelBenchmarkSummary {
        val successfulTimings = results.filter { it.succeeded }.map { it.durationMs.coerceAtLeast(0L) }
        val effectiveTimings = successfulTimings.ifEmpty { results.map { it.durationMs.coerceAtLeast(0L) } }
        val totalMs = results.sumOf { it.durationMs.coerceAtLeast(0L) }
        val minMs = effectiveTimings.minOrNull() ?: 0L
        val maxMs = effectiveTimings.maxOrNull() ?: 0L
        val averageMs = if (effectiveTimings.isNotEmpty()) {
            effectiveTimings.sum() / effectiveTimings.size
        } else {
            0L
        }
        return ModelBenchmarkSummary(
            modelDisplayName = modelDisplayName,
            modelPath = modelPath,
            results = results,
            averageMs = averageMs,
            minMs = minMs,
            maxMs = maxMs,
            totalMs = totalMs
        )
    }

    fun formatDurationLabel(durationMs: Long): String {
        return if (durationMs >= 1000L) {
            String.format(java.util.Locale.US, "%.2f s (%d ms)", durationMs / 1000.0, durationMs)
        } else {
            "$durationMs ms"
        }
    }
}
