package com.example.data.generator

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

enum class LocalModelTier(val tierLabel: String, val shortTierName: String) {
    TIER_1_LITE("Tier 1 — Lite / Fast", "Lite Tier"),
    TIER_2_BALANCED("Tier 2 — Balanced Coder", "Medium Tier"),
    TIER_3_PRO("Tier 3 — Pro / High Accuracy", "Pro Tier")
}

enum class LocalModelSpecialty(val label: String) {
    ALL("All Models"),
    BEST_FIT("Best for RAM"),
    CODING("Kotlin & Code"),
    REASONING("Reasoning"),
    TOOL_CALLING("JSON & Actions"),
    ULTRA_LITE("Ultra-Lite")
}

data class LocalAiTip(
    val id: String,
    val title: String,
    val detail: String
)

data class LocalAiSuggestionsReport(
    val filteredModels: List<LocalModelPreset>,
    val topPick: LocalModelPreset,
    val codingPick: LocalModelPreset,
    val fastPick: LocalModelPreset,
    val tips: List<LocalAiTip>
)

data class LocalModelPreset(
    val id: String,
    val tier: LocalModelTier,
    val tierTitle: String,
    val displayName: String,
    val targetRamHint: String,
    val approxSizeLabel: String,
    val approxSizeBytes: Long,
    val minRecommendedTotalRamGb: Double,
    val minRecommendedFreeRamGb: Double,
    val badge: String,
    val repoId: String,
    val downloadUrl: String,
    val fileName: String,
    val isGated: Boolean = false,
    val preferredVariantHint: String = "",
    val specialty: LocalModelSpecialty = LocalModelSpecialty.CODING,
    val bestForSummary: String = "",
    val contextWindowHint: String = "1.2K–2K ctx"
)

data class DeviceHardwareProfile(
    val totalRamGb: Double,
    val availableRamGb: Double,
    val cpuAbi: String,
    val isLowMemory: Boolean,
    val recommendedTier: LocalModelTier
) {
    fun formatSummaryPill(): String {
        val totalStr = String.format(Locale.US, "%.1f GB", totalRamGb)
        val freeStr = String.format(Locale.US, "%.1f GB", availableRamGb)
        return "Device: $totalStr RAM ($freeStr Free) • $cpuAbi • Recommended: ${recommendedTier.shortTierName}"
    }
}

object LocalModelCatalog {
    const val CUSTOM_URL_OPTION_ID = "custom_url"

    val presets: List<LocalModelPreset> = listOf(
        LocalModelPreset(
            id = "gemma3_1b_it_int4",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Gemma 3 — Lite / Fast",
            displayName = "Gemma 3 1B IT (Int4 Lite - Official Google)",
            targetRamHint = "4GB–6GB RAM",
            approxSizeLabel = "~700 MB",
            approxSizeBytes = 700L * 1024L * 1024L,
            minRecommendedTotalRamGb = 3.5,
            minRecommendedFreeRamGb = 1.0,
            badge = "Official Google • Fast Lite",
            repoId = "litert-community/Gemma3-1B-IT",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            fileName = "gemma3-1b-it-int4.task",
            isGated = true,
            preferredVariantHint = "int4",
            specialty = LocalModelSpecialty.ULTRA_LITE,
            bestForSummary = "Fast Int4 Google Gemma for compact Compose screens & quick edits"
        ),
        LocalModelPreset(
            id = "gemma2_2b_it_gpu_int4",
            tier = LocalModelTier.TIER_2_BALANCED,
            tierTitle = "Gemma 2 — GPU Balanced",
            displayName = "Gemma 2 2B IT (GPU Int4)",
            targetRamHint = "6GB–8GB RAM",
            approxSizeLabel = "~1.3 GB",
            approxSizeBytes = 1300L * 1024L * 1024L,
            minRecommendedTotalRamGb = 5.5,
            minRecommendedFreeRamGb = 1.8,
            badge = "GPU Accelerated • Balanced",
            repoId = "litert-community/Gemma2-2B-IT",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            fileName = "gemma2-2b-it-gpu-int4.task",
            isGated = true,
            preferredVariantHint = "int4",
            specialty = LocalModelSpecialty.REASONING,
            bestForSummary = "GPU-accelerated balanced generation for mid-range phones"
        ),
        LocalModelPreset(
            id = "gemma2_2b_it_gpu_int8",
            tier = LocalModelTier.TIER_3_PRO,
            tierTitle = "Gemma 2 — High Accuracy",
            displayName = "Gemma 2 2B IT (GPU Int8 - High Accuracy)",
            targetRamHint = "8GB+ RAM",
            approxSizeLabel = "~2.5 GB",
            approxSizeBytes = 2500L * 1024L * 1024L,
            minRecommendedTotalRamGb = 7.5,
            minRecommendedFreeRamGb = 2.6,
            badge = "High Accuracy • 8GB+ RAM",
            repoId = "litert-community/Gemma2-2B-IT",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            isGated = true,
            preferredVariantHint = "q8",
            specialty = LocalModelSpecialty.REASONING,
            bestForSummary = "Higher-precision Q8 Gemma 2 for cleaner structured JSON & UI code"
        ),
        LocalModelPreset(
            id = "model1_ultra_lite",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Model 1 — Ultra-Lite",
            displayName = "SmolLM2 360M Instruct (Ultra-Lite)",
            targetRamHint = "3GB–4GB RAM",
            approxSizeLabel = "~380 MB",
            approxSizeBytes = 380L * 1024L * 1024L,
            minRecommendedTotalRamGb = 3.0,
            minRecommendedFreeRamGb = 0.8,
            badge = "Tiny • Fastest on Low RAM",
            repoId = "litert-community/SmolLM2-360M-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/main/SmolLM2-360M-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "SmolLM2-360M-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.ULTRA_LITE,
            bestForSummary = "Minimal memory footprint for 3GB–4GB devices & rapid drafts"
        ),
        LocalModelPreset(
            id = "model2_lite_fast",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Model 2 — Lite Fast",
            displayName = "SmolLM2 1.7B Instruct (Lite)",
            targetRamHint = "4GB–6GB RAM",
            approxSizeLabel = "~900 MB",
            approxSizeBytes = 900L * 1024L * 1024L,
            minRecommendedTotalRamGb = 3.5,
            minRecommendedFreeRamGb = 1.1,
            badge = "Balanced Lite • No Auth",
            repoId = "litert-community/SmolLM2-1.7B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/SmolLM2-1.7B-Instruct/resolve/main/SmolLM2-1.7B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "SmolLM2-1.7B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.ULTRA_LITE,
            bestForSummary = "No-login balanced assistant for 4GB–6GB everyday coding"
        ),
        LocalModelPreset(
            id = "model3_micro_coder",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Model 3 — Micro Coder",
            displayName = "Qwen 2.5 0.5B Instruct (Micro)",
            targetRamHint = "4GB–6GB RAM",
            approxSizeLabel = "~520 MB",
            approxSizeBytes = 520L * 1024L * 1024L,
            minRecommendedTotalRamGb = 3.5,
            minRecommendedFreeRamGb = 0.9,
            badge = "Ultra Fast • Low Memory",
            repoId = "litert-community/Qwen2.5-0.5B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.CODING,
            bestForSummary = "Very fast Kotlin starter code generation under tight RAM"
        ),
        LocalModelPreset(
            id = "model4_balanced_coder",
            tier = LocalModelTier.TIER_2_BALANCED,
            tierTitle = "Model 4 — Balanced Coder",
            displayName = "Qwen 2.5 1.5B Instruct (Q8)",
            targetRamHint = "6GB–8GB RAM",
            approxSizeLabel = "~1.6 GB",
            approxSizeBytes = 1600L * 1024L * 1024L,
            minRecommendedTotalRamGb = 5.5,
            minRecommendedFreeRamGb = 1.8,
            badge = "Best for Kotlin Code",
            repoId = "litert-community/Qwen2.5-1.5B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.CODING,
            bestForSummary = "Reliable Compose + ViewModel code generation on 6GB–8GB devices"
        ),
        LocalModelPreset(
            id = "model5_reasoning",
            tier = LocalModelTier.TIER_2_BALANCED,
            tierTitle = "Model 5 — Reasoning & Logic",
            displayName = "DeepSeek R1 Distill Qwen 1.5B",
            targetRamHint = "6GB–8GB RAM",
            approxSizeLabel = "~1.6 GB",
            approxSizeBytes = 1600L * 1024L * 1024L,
            minRecommendedTotalRamGb = 5.5,
            minRecommendedFreeRamGb = 1.8,
            badge = "Step-by-Step Logic",
            repoId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.REASONING,
            bestForSummary = "Strong chain-of-thought reasoning for complex business logic"
        ),
        LocalModelPreset(
            id = "model6_medium_pro",
            tier = LocalModelTier.TIER_3_PRO,
            tierTitle = "Model 6 — Medium-Pro Coder",
            displayName = "Qwen 2.5 3B Instruct (Q8)",
            targetRamHint = "8GB+ RAM",
            approxSizeLabel = "~3.1 GB",
            approxSizeBytes = 3100L * 1024L * 1024L,
            minRecommendedTotalRamGb = 7.5,
            minRecommendedFreeRamGb = 3.2,
            badge = "Pro Code Quality",
            repoId = "litert-community/Qwen2.5-3B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/resolve/main/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.CODING,
            bestForSummary = "High-accuracy multi-file Kotlin/Compose synthesis on 8GB+ phones"
        ),
        LocalModelPreset(
            id = "model7_flagship",
            tier = LocalModelTier.TIER_3_PRO,
            tierTitle = "Model 7 — Flagship On-Device",
            displayName = "Phi-4 Mini Instruct (Q8)",
            targetRamHint = "8GB–12GB+ RAM",
            approxSizeLabel = "~2.4 GB",
            approxSizeBytes = 2400L * 1024L * 1024L,
            minRecommendedTotalRamGb = 6.0,
            minRecommendedFreeRamGb = 2.4,
            badge = "High Accuracy • Microsoft",
            repoId = "litert-community/Phi-4-mini-instruct",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.REASONING,
            bestForSummary = "Microsoft Phi-4 dense reasoning for clean architecture & refactoring"
        )
    )

    val extendedSuggestions: List<LocalModelPreset> = listOf(
        LocalModelPreset(
            id = "qwen25_coder_15b",
            tier = LocalModelTier.TIER_2_BALANCED,
            tierTitle = "Suggested Coder — Kotlin Specialized",
            displayName = "Qwen 2.5 Coder 1.5B Instruct",
            targetRamHint = "6GB–8GB RAM",
            approxSizeLabel = "~1.5 GB",
            approxSizeBytes = 1500L * 1024L * 1024L,
            minRecommendedTotalRamGb = 5.5,
            minRecommendedFreeRamGb = 1.8,
            badge = "Dedicated Code Weights",
            repoId = "litert-community/Qwen2.5-Coder-1.5B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-Coder-1.5B-Instruct/resolve/main/Qwen2.5-Coder-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen2.5-Coder-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.CODING,
            bestForSummary = "Specialized coding weights for Jetpack Compose UI & Kotlin fixes"
        ),
        LocalModelPreset(
            id = "qwen25_coder_3b",
            tier = LocalModelTier.TIER_3_PRO,
            tierTitle = "Suggested Pro Coder — Flagship Code",
            displayName = "Qwen 2.5 Coder 3B Instruct",
            targetRamHint = "8GB–12GB+ RAM",
            approxSizeLabel = "~3.0 GB",
            approxSizeBytes = 3000L * 1024L * 1024L,
            minRecommendedTotalRamGb = 7.5,
            minRecommendedFreeRamGb = 3.1,
            badge = "Top Kotlin Architecture",
            repoId = "litert-community/Qwen2.5-Coder-3B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-Coder-3B-Instruct/resolve/main/Qwen2.5-Coder-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen2.5-Coder-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.CODING,
            bestForSummary = "Multi-file ViewModel + Repository + Material 3 generation offline"
        ),
        LocalModelPreset(
            id = "qwen3_06b",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Suggested Next-Gen — Nano Fast",
            displayName = "Qwen 3 0.6B (LiteRT)",
            targetRamHint = "3GB–4GB RAM",
            approxSizeLabel = "~580 MB",
            approxSizeBytes = 580L * 1024L * 1024L,
            minRecommendedTotalRamGb = 3.0,
            minRecommendedFreeRamGb = 0.9,
            badge = "Next-Gen Qwen3 • Fast",
            repoId = "litert-community/Qwen3-0.6B",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen3-0.6B_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.ULTRA_LITE,
            bestForSummary = "Low-latency Qwen3 generation with minimal battery & RAM usage"
        ),
        LocalModelPreset(
            id = "qwen3_17b",
            tier = LocalModelTier.TIER_2_BALANCED,
            tierTitle = "Suggested Next-Gen — Hybrid Thinking",
            displayName = "Qwen 3 1.7B (Thinking & Code)",
            targetRamHint = "6GB–8GB RAM",
            approxSizeLabel = "~1.7 GB",
            approxSizeBytes = 1700L * 1024L * 1024L,
            minRecommendedTotalRamGb = 5.5,
            minRecommendedFreeRamGb = 1.9,
            badge = "Hybrid Thinking • No Auth",
            repoId = "litert-community/Qwen3-1.7B",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Qwen3-1.7B_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.REASONING,
            bestForSummary = "Next-gen Qwen3 reasoning & structured JSON project planning"
        ),
        LocalModelPreset(
            id = "hammer21_15b",
            tier = LocalModelTier.TIER_2_BALANCED,
            tierTitle = "Suggested Agent — Tool & JSON",
            displayName = "Hammer 2.1 1.5B (Action & JSON)",
            targetRamHint = "6GB–8GB RAM",
            approxSizeLabel = "~1.6 GB",
            approxSizeBytes = 1600L * 1024L * 1024L,
            minRecommendedTotalRamGb = 5.5,
            minRecommendedFreeRamGb = 1.8,
            badge = "Strict JSON & Tool Calls",
            repoId = "litert-community/Hammer2.1-1.5b",
            downloadUrl = "https://huggingface.co/litert-community/Hammer2.1-1.5b/resolve/main/Hammer2.1-1.5b_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Hammer2.1-1.5b_multi-prefill-seq_q8_ekv1280.task",
            specialty = LocalModelSpecialty.TOOL_CALLING,
            bestForSummary = "Trained for reliable JSON project bundles & API tool schemas"
        ),
        LocalModelPreset(
            id = "llama32_1b_instruct",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Suggested Meta — Mobile Lite",
            displayName = "Llama 3.2 1B Instruct (Meta)",
            targetRamHint = "4GB–6GB RAM",
            approxSizeLabel = "~1.1 GB",
            approxSizeBytes = 1100L * 1024L * 1024L,
            minRecommendedTotalRamGb = 4.0,
            minRecommendedFreeRamGb = 1.3,
            badge = "Meta Open Weights • Lite",
            repoId = "litert-community/Llama-3.2-1B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct/resolve/main/Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            isGated = true,
            preferredVariantHint = "q8",
            specialty = LocalModelSpecialty.ULTRA_LITE,
            bestForSummary = "Compact Meta Llama for fast instruction following (HF license)"
        ),
        LocalModelPreset(
            id = "llama32_3b_instruct",
            tier = LocalModelTier.TIER_3_PRO,
            tierTitle = "Suggested Meta — Flagship 3B",
            displayName = "Llama 3.2 3B Instruct (Meta Pro)",
            targetRamHint = "8GB–12GB+ RAM",
            approxSizeLabel = "~2.9 GB",
            approxSizeBytes = 2900L * 1024L * 1024L,
            minRecommendedTotalRamGb = 7.5,
            minRecommendedFreeRamGb = 3.0,
            badge = "Meta Pro • HF License",
            repoId = "litert-community/Llama-3.2-3B-Instruct",
            downloadUrl = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct/resolve/main/Llama-3.2-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "Llama-3.2-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            isGated = true,
            preferredVariantHint = "int4",
            specialty = LocalModelSpecialty.REASONING,
            bestForSummary = "Strong multi-screen reasoning & prose on 8GB–12GB+ phones"
        ),
        LocalModelPreset(
            id = "gemma3_270m_it",
            tier = LocalModelTier.TIER_1_LITE,
            tierTitle = "Suggested Google — Nano Speed",
            displayName = "Gemma 3 270M IT (Nano)",
            targetRamHint = "3GB+ RAM",
            approxSizeLabel = "~290 MB",
            approxSizeBytes = 290L * 1024L * 1024L,
            minRecommendedTotalRamGb = 2.5,
            minRecommendedFreeRamGb = 0.6,
            badge = "Smallest Gemma • Instant",
            repoId = "litert-community/gemma-3-270m-it",
            downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task",
            fileName = "gemma3-270m-it-q8.task",
            isGated = true,
            preferredVariantHint = "q8",
            specialty = LocalModelSpecialty.ULTRA_LITE,
            bestForSummary = "Ultra-compact official Google Gemma for 3GB phones & fast snippets"
        )
    )

    val allPresets: List<LocalModelPreset>
        get() = presets + extendedSuggestions

    fun presetForTier(tier: LocalModelTier): LocalModelPreset = when (tier) {
        LocalModelTier.TIER_1_LITE -> presets.first { it.id == "model2_lite_fast" }
        LocalModelTier.TIER_2_BALANCED -> presets.first { it.id == "model4_balanced_coder" }
        LocalModelTier.TIER_3_PRO -> presets.first { it.id == "model7_flagship" }
    }

    fun recommendPreset(profile: DeviceHardwareProfile): LocalModelPreset = when {
        profile.isLowMemory || profile.totalRamGb < 4.2 || profile.availableRamGb < 1.2 ->
            presets.first { it.id == "model1_ultra_lite" }
        profile.recommendedTier == LocalModelTier.TIER_1_LITE ->
            presets.first { it.id == "model2_lite_fast" }
        profile.recommendedTier == LocalModelTier.TIER_2_BALANCED ->
            presets.first { it.id == "model4_balanced_coder" }
        else ->
            presets.first { it.id == "model7_flagship" }
    }

    fun findById(id: String): LocalModelPreset? =
        allPresets.firstOrNull { it.id == id }

    fun isSafeForDevice(profile: DeviceHardwareProfile, preset: LocalModelPreset): Boolean {
        return profile.totalRamGb >= preset.minRecommendedTotalRamGb &&
            profile.availableRamGb >= preset.minRecommendedFreeRamGb &&
            (!profile.isLowMemory || preset.tier == LocalModelTier.TIER_1_LITE)
    }

    fun buildSuggestionsForProfile(
        profile: DeviceHardwareProfile,
        specialty: LocalModelSpecialty = LocalModelSpecialty.ALL
    ): LocalAiSuggestionsReport {
        val catalog = allPresets
        val filtered = when (specialty) {
            LocalModelSpecialty.ALL -> catalog
            LocalModelSpecialty.BEST_FIT -> catalog.filter { isSafeForDevice(profile, it) }
                .ifEmpty { catalog.filter { it.tier == LocalModelTier.TIER_1_LITE } }
            else -> catalog.filter { it.specialty == specialty }
        }

        val topPick = recommendPreset(profile)
        val codingPick = catalog.lastOrNull {
            it.specialty == LocalModelSpecialty.CODING && isSafeForDevice(profile, it)
        } ?: catalog.first { it.id == "model3_micro_coder" }

        val fastPick = catalog.firstOrNull {
            it.id == "qwen3_06b" && isSafeForDevice(profile, it)
        } ?: catalog.first { it.id == "model1_ultra_lite" }

        val totalRamStr = String.format(Locale.US, "%.1f GB", profile.totalRamGb)
        val tips = listOf(
            LocalAiTip(
                id = "ram_headroom",
                title = "RAM Headroom Rule (1.8× Model Size)",
                detail = "Your device has $totalRamStr total RAM (${profile.recommendedTier.shortTierName}). Keep ~1.8× the model file size free for KV cache so MediaPipe avoids OOM crashes."
            ),
            LocalAiTip(
                id = "quantization_choice",
                title = "Int4 Speed vs Q8 Kotlin Precision",
                detail = "Choose Int4 (.task) models when RAM is tight (<6 GB free) for lower memory load; pick Q8 models like Qwen 2.5 Coder for stricter JSON & Compose syntax."
            ),
            LocalAiTip(
                id = "termux_gguf",
                title = "Need 7B–14B Coding Models Offline?",
                detail = "Switch engine to Termux / Local Server (127.0.0.1:11434/v1) and run Ollama or llama.cpp with qwen2.5-coder:7b GGUF outside MediaPipe's .task limit."
            ),
            LocalAiTip(
                id = "json_reliability",
                title = "JSON & Multi-File Stability",
                detail = "Hammer 2.1 1.5B, Qwen 2.5 Coder, and Qwen 3 1.7B handle structured JSON project outputs best. Pair large prompts with Temperature 0.15."
            )
        )

        return LocalAiSuggestionsReport(
            filteredModels = filtered,
            topPick = topPick,
            codingPick = codingPick,
            fastPick = fastPick,
            tips = tips
        )
    }
}

object DeviceHardwareProfiler {

    private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0

    fun inspectDevice(context: Context): DeviceHardwareProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalGb = if (memInfo.totalMem > 0L) memInfo.totalMem / BYTES_PER_GB else 4.0
        val availGb = if (memInfo.availMem > 0L) memInfo.availMem / BYTES_PER_GB else 1.5
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "arm64-v8a" }

        return buildProfile(
            totalRamGb = totalGb,
            availableRamGb = availGb,
            cpuAbi = cpuAbi,
            isLowMemory = memInfo.lowMemory
        )
    }

    fun buildProfile(
        totalRamGb: Double,
        availableRamGb: Double,
        cpuAbi: String = "arm64-v8a",
        isLowMemory: Boolean = false
    ): DeviceHardwareProfile {
        val recommended = recommendTier(
            totalRamGb = totalRamGb,
            availableRamGb = availableRamGb,
            isLowMemory = isLowMemory
        )
        return DeviceHardwareProfile(
            totalRamGb = totalRamGb,
            availableRamGb = availableRamGb,
            cpuAbi = cpuAbi,
            isLowMemory = isLowMemory,
            recommendedTier = recommended
        )
    }

    /**
     * Recommends the optimal MediaPipe model tier based on physical total RAM and currently free RAM.
     * - < 5.5 GB total RAM (e.g., 3-4 GB devices) or low-memory pressure -> Tier 1 Lite
     * - 5.5 GB to < 9.0 GB total RAM (6 GB – 8 GB devices) or 8 GB+ with < 4.5 GB free -> Tier 2 Medium
     * - >= 9.0 GB total RAM (10 GB – 12 GB+ devices) with >= 4.5 GB free -> Tier 3 Pro
     */
    fun recommendTier(
        totalRamGb: Double,
        availableRamGb: Double,
        isLowMemory: Boolean = false
    ): LocalModelTier {
        if (isLowMemory || totalRamGb < 5.5 || availableRamGb < 1.8) {
            return LocalModelTier.TIER_1_LITE
        }
        if (totalRamGb >= 9.0 && availableRamGb >= 4.5) {
            return LocalModelTier.TIER_3_PRO
        }
        return LocalModelTier.TIER_2_BALANCED
    }

    /**
     * Returns an OOM safety warning if the selected preset exceeds safe device memory thresholds.
     */
    fun getOomWarningForSelection(
        profile: DeviceHardwareProfile,
        preset: LocalModelPreset?
    ): String? {
        if (preset == null) return null
        val failsTotalRam = profile.totalRamGb < preset.minRecommendedTotalRamGb
        val failsFreeRam = profile.availableRamGb < preset.minRecommendedFreeRamGb
        return when {
            preset.tier == LocalModelTier.TIER_3_PRO && (failsTotalRam || failsFreeRam) ->
                "Warning: This model may cause Out-Of-Memory (OOM) crashes on your device. Tier 1 Lite is recommended."
            preset.tier == LocalModelTier.TIER_2_BALANCED && (profile.totalRamGb < 4.5 || profile.availableRamGb < 1.5) ->
                "Warning: This model may cause Out-Of-Memory (OOM) crashes on your device. Tier 1 Lite is recommended."
            else -> null
        }
    }
}
