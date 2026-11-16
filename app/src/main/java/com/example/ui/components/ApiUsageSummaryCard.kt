package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.ApiUsageSummary
import com.example.data.preferences.MonthlyUsageAlertState
import com.example.model.ApiUsageLimitMetric
import com.example.model.StudioSettings
import com.example.ui.theme.LocalStudioCyberPalette
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ApiUsageSummaryCard(
    usageSummary: ApiUsageSummary,
    hasConfiguredKey: Boolean,
    isChecking: Boolean,
    onRefreshUsageCheck: () -> Unit,
    onResetUsageStats: () -> Unit,
    alertEnabled: Boolean = true,
    limitMetric: ApiUsageLimitMetric = ApiUsageLimitMetric.REQUESTS,
    limitInput: String = StudioSettings.DEFAULT_MONTHLY_REQUEST_LIMIT.toString(),
    warningThresholdPercent: Int = StudioSettings.DEFAULT_WARNING_THRESHOLD_PERCENT,
    savedAlertEnabled: Boolean = true,
    savedMetric: ApiUsageLimitMetric = ApiUsageLimitMetric.REQUESTS,
    savedLimit: Long = StudioSettings.DEFAULT_MONTHLY_REQUEST_LIMIT,
    savedWarningThresholdPercent: Int = StudioSettings.DEFAULT_WARNING_THRESHOLD_PERCENT,
    liveAlertState: MonthlyUsageAlertState = MonthlyUsageAlertState(),
    onAlertEnabledChange: (Boolean) -> Unit = {},
    onLimitMetricChange: (ApiUsageLimitMetric) -> Unit = {},
    onLimitInputChange: (String) -> Unit = {},
    onWarningThresholdPercentChange: (Int) -> Unit = {},
    onApplyThreshold: (Boolean, ApiUsageLimitMetric, Long, Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    var showRawHeaders by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("api_usage_summary_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.settings_usage_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (usageSummary.providerName.isNotBlank()) {
                                "${usageSummary.providerName} • Parsed from validation headers"
                            } else {
                                stringResource(R.string.settings_usage_subtitle)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (usageSummary.hasAnyTelemetry) {
                        IconButton(
                            onClick = onResetUsageStats,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reset_usage_stats_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Reset API usage statistics",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (hasConfiguredKey) {
                        IconButton(
                            onClick = onRefreshUsageCheck,
                            enabled = !isChecking,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_usage_stats_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Run validation request to refresh usage headers",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!usageSummary.hasAnyTelemetry) {
                // Empty state before any validation HTTP call runs
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.cardSurfaceHigh,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("usage_empty_state")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DataUsage,
                                contentDescription = null,
                                tint = palette.accentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "No validation telemetry captured yet",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Test or save an API key to parse provider rate-limit, token usage, and request quota headers.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasConfiguredKey) {
                            FilledTonalButton(
                                onClick = onRefreshUsageCheck,
                                enabled = !isChecking,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("usage_empty_verify_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Validation Check")
                            }
                        }
                    }
                }
            } else {
                // 2x2 Stat Tile Grid
                val tokensSubtext = when {
                    usageSummary.lastCallTokensUsed != null ->
                        "Latest call: +${formatUsageCount(usageSummary.lastCallTokensUsed)}"
                    usageSummary.tokensLimit != null ->
                        "Limit: ${formatUsageCount(usageSummary.tokensLimit)}"
                    else -> "From usage headers"
                }

                val requestsSubtext = buildString {
                    append("${usageSummary.successfulValidations} ok")
                    if (usageSummary.failedValidations > 0) {
                        append(" • ${usageSummary.failedValidations} err")
                    }
                    usageSummary.headerReportedRequestsUsed?.let { used ->
                        append(" • Header: ${formatUsageCount(used)}")
                    }
                }

                val quotaRemainingText = when {
                    usageSummary.requestsRemaining != null && usageSummary.requestsLimit != null ->
                        "${formatUsageCount(usageSummary.requestsRemaining)} / ${formatUsageCount(usageSummary.requestsLimit)}"
                    usageSummary.requestsRemaining != null ->
                        formatUsageCount(usageSummary.requestsRemaining)
                    else -> "—"
                }
                val quotaSubtext = when {
                    usageSummary.requestsReset != null -> "Resets: ${usageSummary.requestsReset}"
                    usageSummary.requestsLimit != null -> "Remaining / limit"
                    else -> "Not in response headers"
                }

                val tokenQuotaOrHttpText = when {
                    usageSummary.tokensRemaining != null && usageSummary.tokensLimit != null ->
                        "${formatUsageCount(usageSummary.tokensRemaining)} / ${formatUsageCount(usageSummary.tokensLimit)}"
                    usageSummary.lastHttpStatusCode != null ->
                        "HTTP ${usageSummary.lastHttpStatusCode}"
                    else -> "—"
                }
                val tokenQuotaOrHttpLabel = if (usageSummary.tokensLimit != null) {
                    "Token Quota Left"
                } else {
                    "Last Response"
                }
                val tokenQuotaOrHttpSubtext = when {
                    usageSummary.tokensReset != null -> "Resets: ${usageSummary.tokensReset}"
                    usageSummary.lastLatencyMs != null -> "${usageSummary.lastLatencyMs} ms latency"
                    else -> "Validation telemetry"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageStatTile(
                        icon = Icons.Default.Token,
                        accentColor = palette.accentCyan,
                        label = "Total Tokens Used",
                        value = formatUsageCount(usageSummary.totalTokensUsed),
                        subtext = tokensSubtext,
                        valueTestTag = "usage_total_tokens_value",
                        modifier = Modifier.weight(1f)
                    )
                    UsageStatTile(
                        icon = Icons.Default.Http,
                        accentColor = palette.accentEmerald,
                        label = "Requests Made",
                        value = formatUsageCount(usageSummary.validationRequestsMade.toLong()),
                        subtext = requestsSubtext,
                        valueTestTag = "usage_requests_made_value",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageStatTile(
                        icon = Icons.Default.DataUsage,
                        accentColor = MaterialTheme.colorScheme.primary,
                        label = "Requests Remaining",
                        value = quotaRemainingText,
                        subtext = quotaSubtext,
                        valueTestTag = "usage_requests_remaining_value",
                        modifier = Modifier.weight(1f)
                    )
                    UsageStatTile(
                        icon = Icons.Default.Speed,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        label = tokenQuotaOrHttpLabel,
                        value = tokenQuotaOrHttpText,
                        subtext = tokenQuotaOrHttpSubtext,
                        valueTestTag = "usage_token_quota_value",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Visual Quota Bar when rate-limit capacity headers are available
                val reqLimit = usageSummary.requestsLimit
                val reqRem = usageSummary.requestsRemaining
                if (reqLimit != null && reqLimit > 0 && reqRem != null) {
                    val remainingFraction = (reqRem.toFloat() / reqLimit.toFloat()).coerceIn(0f, 1f)
                    val barColor = when {
                        remainingFraction < 0.2f -> MaterialTheme.colorScheme.error
                        else -> palette.accentEmerald
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Request Quota Available",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(remainingFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = barColor
                            )
                        }
                        LinearProgressIndicator(
                            progress = { remainingFraction },
                            color = barColor,
                            trackColor = palette.cardSurfaceHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("usage_quota_progress_bar")
                        )
                    }
                }

                // Response Headers Breakdown
                if (usageSummary.parsedHeaders.isNotEmpty()) {
                    HorizontalDivider(color = palette.cardBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Parsed Response Headers (${usageSummary.parsedHeaders.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showRawHeaders = !showRawHeaders },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("toggle_parsed_headers_button")
                        ) {
                            Text(if (showRawHeaders) "Hide" else "Inspect")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showRawHeaders) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showRawHeaders) "Hide parsed headers" else "Inspect parsed headers",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showRawHeaders) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.cardSurfaceHigh,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("usage_parsed_headers_section")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                usageSummary.parsedHeaders.entries.forEach { (headerKey, headerValue) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = headerKey,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = palette.accentCyan,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = headerValue,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MonthlyUsageThresholdControls(
                alertEnabled = alertEnabled,
                limitMetric = limitMetric,
                limitInput = limitInput,
                warningThresholdPercent = warningThresholdPercent,
                savedAlertEnabled = savedAlertEnabled,
                savedMetric = savedMetric,
                savedLimit = savedLimit,
                savedWarningThresholdPercent = savedWarningThresholdPercent,
                liveAlertState = liveAlertState,
                onAlertEnabledChange = onAlertEnabledChange,
                onLimitMetricChange = onLimitMetricChange,
                onLimitInputChange = onLimitInputChange,
                onWarningThresholdPercentChange = onWarningThresholdPercentChange,
                onApplyThreshold = onApplyThreshold
            )
        }
    }
}

@Composable
private fun UsageStatTile(
    icon: ImageVector,
    accentColor: Color,
    label: String,
    value: String,
    subtext: String,
    valueTestTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.cardSurfaceHigh,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(valueTestTag)
            )

            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatUsageCount(value: Long): String {
    return NumberFormat.getIntegerInstance(Locale.US).format(value)
}
