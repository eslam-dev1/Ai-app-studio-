package com.example.data.preferences

import com.example.model.ApiUsageLimitMetric
import com.example.model.StudioSettings
import kotlinx.serialization.Serializable
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@Serializable
data class ParsedUsageHeaders(
    val requestsLimit: Long? = null,
    val requestsRemaining: Long? = null,
    val requestsUsedFromHeader: Long? = null,
    val requestsReset: String? = null,
    val tokensLimit: Long? = null,
    val tokensRemaining: Long? = null,
    val tokensUsedFromHeader: Long? = null,
    val promptTokensFromHeader: Long? = null,
    val completionTokensFromHeader: Long? = null,
    val tokensReset: String? = null,
    val rawDetectedHeaders: Map<String, String> = emptyMap()
) {
    val computedHeaderRequestsUsed: Long?
        get() = requestsUsedFromHeader
            ?: if (requestsLimit != null && requestsRemaining != null) {
                (requestsLimit - requestsRemaining).coerceAtLeast(0L)
            } else {
                null
            }

    val computedHeaderTokensUsed: Long?
        get() = tokensUsedFromHeader
            ?: if (promptTokensFromHeader != null || completionTokensFromHeader != null) {
                (promptTokensFromHeader ?: 0L) + (completionTokensFromHeader ?: 0L)
            } else if (tokensLimit != null && tokensRemaining != null) {
                (tokensLimit - tokensRemaining).coerceAtLeast(0L)
            } else {
                null
            }
}

@Serializable
data class ApiUsageSummary(
    val providerName: String = "",
    val lastHttpStatusCode: Int? = null,
    val lastLatencyMs: Long? = null,
    val validationRequestsMade: Int = 0,
    val successfulValidations: Int = 0,
    val failedValidations: Int = 0,
    val totalTokensUsed: Long = 0L,
    val lastCallTokensUsed: Long? = null,
    val headerReportedRequestsUsed: Long? = null,
    val requestsLimit: Long? = null,
    val requestsRemaining: Long? = null,
    val requestsReset: String? = null,
    val tokensLimit: Long? = null,
    val tokensRemaining: Long? = null,
    val tokensReset: String? = null,
    val parsedHeaders: Map<String, String> = emptyMap(),
    val lastUpdatedEpochMs: Long? = null,
    val trackingYearMonth: String = "",
    val monthlyRequestsMade: Int = 0,
    val monthlyTokensUsed: Long = 0L
) {
    val hasAnyTelemetry: Boolean
        get() = validationRequestsMade > 0 || monthlyRequestsMade > 0 || parsedHeaders.isNotEmpty()
}

enum class UsageThresholdSeverity {
    NONE,
    APPROACHING,
    LIMIT_REACHED
}

data class MonthlyUsageAlertState(
    val isEnabled: Boolean = true,
    val severity: UsageThresholdSeverity = UsageThresholdSeverity.NONE,
    val metric: ApiUsageLimitMetric = ApiUsageLimitMetric.REQUESTS,
    val currentUsage: Long = 0L,
    val monthlyLimit: Long = StudioSettings.DEFAULT_MONTHLY_REQUEST_LIMIT,
    val warningThresholdPercent: Int = StudioSettings.DEFAULT_WARNING_THRESHOLD_PERCENT,
    val triggerThresholdValue: Long = 80L,
    val usagePercent: Int = 0,
    val yearMonth: String = ""
) {
    val shouldShowWarning: Boolean
        get() = isEnabled && monthlyLimit > 0L && severity != UsageThresholdSeverity.NONE
}

object ApiUsageThresholdEvaluator {

    fun yearMonthFromEpochMs(epochMs: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyy-MM", Locale.US)
        return formatter.format(Date(epochMs))
    }

    fun evaluate(
        summary: ApiUsageSummary,
        settings: StudioSettings,
        currentYearMonth: String = yearMonthFromEpochMs()
    ): MonthlyUsageAlertState {
        val effectiveLimit = settings.monthlyUsageLimit.coerceAtLeast(0L)
        val thresholdPercent = settings.monthlyUsageWarningThresholdPercent.coerceIn(10, 100)
        val metric = settings.monthlyUsageLimitMetric

        val currentUsage = when {
            summary.trackingYearMonth.isBlank() -> {
                if (metric == ApiUsageLimitMetric.REQUESTS) {
                    maxOf(summary.monthlyRequestsMade, summary.validationRequestsMade).toLong()
                } else {
                    maxOf(summary.monthlyTokensUsed, summary.totalTokensUsed)
                }
            }
            summary.trackingYearMonth == currentYearMonth -> {
                if (metric == ApiUsageLimitMetric.REQUESTS) {
                    summary.monthlyRequestsMade.toLong()
                } else {
                    summary.monthlyTokensUsed
                }
            }
            else -> 0L
        }

        if (!settings.monthlyUsageAlertEnabled || effectiveLimit <= 0L) {
            return MonthlyUsageAlertState(
                isEnabled = settings.monthlyUsageAlertEnabled,
                severity = UsageThresholdSeverity.NONE,
                metric = metric,
                currentUsage = currentUsage,
                monthlyLimit = effectiveLimit,
                warningThresholdPercent = thresholdPercent,
                triggerThresholdValue = effectiveLimit,
                usagePercent = 0,
                yearMonth = currentYearMonth
            )
        }

        val triggerThresholdValue = ceil(effectiveLimit * (thresholdPercent / 100.0))
            .toLong()
            .coerceIn(1L, effectiveLimit)
        val usagePercent = ((currentUsage.toDouble() / effectiveLimit.toDouble()) * 100.0)
            .toInt()
            .coerceAtLeast(0)

        val severity = when {
            currentUsage >= effectiveLimit -> UsageThresholdSeverity.LIMIT_REACHED
            currentUsage >= triggerThresholdValue -> UsageThresholdSeverity.APPROACHING
            else -> UsageThresholdSeverity.NONE
        }

        return MonthlyUsageAlertState(
            isEnabled = true,
            severity = severity,
            metric = metric,
            currentUsage = currentUsage,
            monthlyLimit = effectiveLimit,
            warningThresholdPercent = thresholdPercent,
            triggerThresholdValue = triggerThresholdValue,
            usagePercent = usagePercent,
            yearMonth = currentYearMonth
        )
    }
}

object ApiUsageHeaderParser {

    private val REQUESTS_LIMIT_KEYS = listOf(
        "x-ratelimit-limit-requests",
        "x-rate-limit-requests-limit",
        "x-ratelimit-limit",
        "x-rate-limit-limit",
        "ratelimit-limit"
    )

    private val REQUESTS_REMAINING_KEYS = listOf(
        "x-ratelimit-remaining-requests",
        "x-rate-limit-requests-remaining",
        "x-ratelimit-remaining",
        "x-rate-limit-remaining",
        "ratelimit-remaining"
    )

    private val REQUESTS_USED_KEYS = listOf(
        "x-ratelimit-used-requests",
        "x-rate-limit-used-requests",
        "x-ratelimit-used",
        "x-rate-limit-used",
        "x-requests-used"
    )

    private val REQUESTS_RESET_KEYS = listOf(
        "x-ratelimit-reset-requests",
        "x-rate-limit-reset-requests",
        "x-ratelimit-reset",
        "x-rate-limit-reset",
        "ratelimit-reset",
        "retry-after"
    )

    private val TOKENS_LIMIT_KEYS = listOf(
        "x-ratelimit-limit-tokens",
        "x-rate-limit-tokens-limit",
        "x-rate-limit-limit-tokens",
        "ratelimit-tokens-limit",
        "ratelimit-limit-tokens"
    )

    private val TOKENS_REMAINING_KEYS = listOf(
        "x-ratelimit-remaining-tokens",
        "x-rate-limit-tokens-remaining",
        "x-rate-limit-remaining-tokens",
        "ratelimit-tokens-remaining",
        "ratelimit-remaining-tokens"
    )

    private val TOKENS_USED_KEYS = listOf(
        "x-usage-total-tokens",
        "x-tokens-used",
        "x-total-tokens",
        "x-openai-usage-total-tokens",
        "x-ratelimit-used-tokens"
    )

    private val PROMPT_TOKENS_KEYS = listOf(
        "x-usage-prompt-tokens",
        "x-prompt-tokens"
    )

    private val COMPLETION_TOKENS_KEYS = listOf(
        "x-usage-completion-tokens",
        "x-completion-tokens"
    )

    private val TOKENS_RESET_KEYS = listOf(
        "x-ratelimit-reset-tokens",
        "x-rate-limit-reset-tokens",
        "ratelimit-reset-tokens"
    )

    fun parse(headers: Headers): ParsedUsageHeaders {
        val normalized = linkedMapOf<String, String>()
        for (i in 0 until headers.size) {
            val key = headers.name(i).trim().lowercase(Locale.US)
            val value = headers.value(i).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                normalized[key] = value
            }
        }

        val detected = linkedMapOf<String, String>()
        normalized.forEach { (k, v) ->
            if (isTelemetryHeader(k)) {
                detected[k] = v
            }
        }

        return ParsedUsageHeaders(
            requestsLimit = firstLong(normalized, REQUESTS_LIMIT_KEYS),
            requestsRemaining = firstLong(normalized, REQUESTS_REMAINING_KEYS),
            requestsUsedFromHeader = firstLong(normalized, REQUESTS_USED_KEYS),
            requestsReset = firstString(normalized, REQUESTS_RESET_KEYS),
            tokensLimit = firstLong(normalized, TOKENS_LIMIT_KEYS),
            tokensRemaining = firstLong(normalized, TOKENS_REMAINING_KEYS),
            tokensUsedFromHeader = firstLong(normalized, TOKENS_USED_KEYS),
            promptTokensFromHeader = firstLong(normalized, PROMPT_TOKENS_KEYS),
            completionTokensFromHeader = firstLong(normalized, COMPLETION_TOKENS_KEYS),
            tokensReset = firstString(normalized, TOKENS_RESET_KEYS),
            rawDetectedHeaders = detected
        )
    }

    fun applyValidationResponse(
        current: ApiUsageSummary,
        providerName: String,
        httpStatusCode: Int?,
        latencyMs: Long?,
        parsed: ParsedUsageHeaders?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiUsageSummary {
        val isSuccess = httpStatusCode != null && (httpStatusCode in 200..299 || httpStatusCode == 429)
        val explicitTokensUsed = parsed?.tokensUsedFromHeader
            ?: if (parsed?.promptTokensFromHeader != null || parsed?.completionTokensFromHeader != null) {
                (parsed.promptTokensFromHeader ?: 0L) + (parsed.completionTokensFromHeader ?: 0L)
            } else {
                null
            }
        val quotaDerivedTokensUsed = if (parsed?.tokensLimit != null && parsed.tokensRemaining != null) {
            (parsed.tokensLimit - parsed.tokensRemaining).coerceAtLeast(0L)
        } else {
            null
        }

        val updatedTotalTokens = when {
            explicitTokensUsed != null -> current.totalTokensUsed + explicitTokensUsed
            quotaDerivedTokensUsed != null -> maxOf(current.totalTokensUsed, quotaDerivedTokensUsed)
            else -> current.totalTokensUsed
        }

        val currentYearMonth = ApiUsageThresholdEvaluator.yearMonthFromEpochMs(nowEpochMs)
        val sameMonth = current.trackingYearMonth == currentYearMonth
        val baseMonthlyRequests = when {
            sameMonth -> current.monthlyRequestsMade
            current.trackingYearMonth.isBlank() -> current.validationRequestsMade
            else -> 0
        }
        val baseMonthlyTokens = when {
            sameMonth -> current.monthlyTokensUsed
            current.trackingYearMonth.isBlank() -> current.totalTokensUsed
            else -> 0L
        }
        val updatedMonthlyTokens = when {
            explicitTokensUsed != null -> baseMonthlyTokens + explicitTokensUsed
            quotaDerivedTokensUsed != null -> maxOf(baseMonthlyTokens, quotaDerivedTokensUsed)
            else -> baseMonthlyTokens
        }

        val callTokens = explicitTokensUsed ?: quotaDerivedTokensUsed
        val resolvedRequestsLimit = parsed?.requestsLimit ?: current.requestsLimit
        val resolvedRequestsRemaining = parsed?.requestsRemaining
            ?: if (parsed?.requestsLimit != null && parsed.requestsUsedFromHeader != null) {
                (parsed.requestsLimit - parsed.requestsUsedFromHeader).coerceAtLeast(0L)
            } else {
                current.requestsRemaining
            }

        return current.copy(
            providerName = providerName.ifBlank { current.providerName },
            lastHttpStatusCode = httpStatusCode ?: current.lastHttpStatusCode,
            lastLatencyMs = latencyMs ?: current.lastLatencyMs,
            validationRequestsMade = current.validationRequestsMade + 1,
            successfulValidations = current.successfulValidations + if (isSuccess) 1 else 0,
            failedValidations = current.failedValidations + if (!isSuccess) 1 else 0,
            totalTokensUsed = updatedTotalTokens,
            lastCallTokensUsed = callTokens ?: current.lastCallTokensUsed,
            headerReportedRequestsUsed = parsed?.computedHeaderRequestsUsed ?: current.headerReportedRequestsUsed,
            requestsLimit = resolvedRequestsLimit,
            requestsRemaining = resolvedRequestsRemaining,
            requestsReset = parsed?.requestsReset ?: current.requestsReset,
            tokensLimit = parsed?.tokensLimit ?: current.tokensLimit,
            tokensRemaining = parsed?.tokensRemaining ?: current.tokensRemaining,
            tokensReset = parsed?.tokensReset ?: current.tokensReset,
            parsedHeaders = parsed?.rawDetectedHeaders?.takeIf { it.isNotEmpty() } ?: current.parsedHeaders,
            lastUpdatedEpochMs = nowEpochMs,
            trackingYearMonth = currentYearMonth,
            monthlyRequestsMade = baseMonthlyRequests + 1,
            monthlyTokensUsed = updatedMonthlyTokens
        )
    }

    fun applyGenerationUsage(
        current: ApiUsageSummary,
        providerName: String,
        tokensUsed: Long?,
        latencyMs: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ApiUsageSummary {
        val currentYearMonth = ApiUsageThresholdEvaluator.yearMonthFromEpochMs(nowEpochMs)
        val sameMonth = current.trackingYearMonth == currentYearMonth
        val baseMonthlyRequests = when {
            sameMonth -> current.monthlyRequestsMade
            current.trackingYearMonth.isBlank() -> current.validationRequestsMade
            else -> 0
        }
        val baseMonthlyTokens = when {
            sameMonth -> current.monthlyTokensUsed
            current.trackingYearMonth.isBlank() -> current.totalTokensUsed
            else -> 0L
        }
        val addedTokens = (tokensUsed ?: 0L).coerceAtLeast(0L)
        return current.copy(
            providerName = providerName.ifBlank { current.providerName },
            lastHttpStatusCode = 200,
            lastLatencyMs = latencyMs,
            validationRequestsMade = current.validationRequestsMade + 1,
            successfulValidations = current.successfulValidations + 1,
            totalTokensUsed = current.totalTokensUsed + addedTokens,
            lastCallTokensUsed = tokensUsed ?: current.lastCallTokensUsed,
            lastUpdatedEpochMs = nowEpochMs,
            trackingYearMonth = currentYearMonth,
            monthlyRequestsMade = baseMonthlyRequests + 1,
            monthlyTokensUsed = baseMonthlyTokens + addedTokens
        )
    }

    private fun isTelemetryHeader(name: String): Boolean {
        return name.startsWith("x-ratelimit") ||
            name.startsWith("x-rate-limit") ||
            name.startsWith("ratelimit") ||
            name.startsWith("x-usage") ||
            name.startsWith("x-tokens") ||
            name.startsWith("x-total-tokens") ||
            name.startsWith("x-prompt-tokens") ||
            name.startsWith("x-completion-tokens") ||
            name.startsWith("x-requests-") ||
            name.startsWith("x-openai-usage") ||
            name == "retry-after" ||
            name == "x-request-id" ||
            name == "cf-ray"
    }

    private fun firstString(map: Map<String, String>, keys: List<String>): String? {
        for (key in keys) {
            val v = map[key]
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun firstLong(map: Map<String, String>, keys: List<String>): Long? {
        for (key in keys) {
            val raw = map[key] ?: continue
            val parsed = parseNumericHeaderValue(raw)
            if (parsed != null) return parsed
        }
        return null
    }

    internal fun parseNumericHeaderValue(raw: String): Long? {
        val cleaned = raw.trim().substringBefore(";").replace(",", "")
        val match = Regex("^(\\d+)").find(cleaned) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}
