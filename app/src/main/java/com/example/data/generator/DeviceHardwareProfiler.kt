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
    val preferredVariantHint: String = ""
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
            preferredVariantHint = "int4"
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
            preferredVariantHint = "int4"
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
            preferredVariantHint = "q8"
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
            fileName = "SmolLM2-360M-Instruct_multi-prefill-seq_q8_ekv1280.task"
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
            fileName = "SmolLM2-1.7B-Instruct_multi-prefill-seq_q8_ekv1280.task"
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
            fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
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
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
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
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task"
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
            fileName = "Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv1280.task"
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
            fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task"
        )
    )

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
        presets.firstOrNull { it.id == id }
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
