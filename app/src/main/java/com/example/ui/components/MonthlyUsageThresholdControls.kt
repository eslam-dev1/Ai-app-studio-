package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.preferences.MonthlyUsageAlertState
import com.example.data.preferences.UsageThresholdSeverity
import com.example.model.ApiUsageLimitMetric
import com.example.ui.theme.LocalStudioCyberPalette
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyUsageThresholdControls(
    alertEnabled: Boolean,
    limitMetric: ApiUsageLimitMetric,
    limitInput: String,
    warningThresholdPercent: Int,
    savedAlertEnabled: Boolean,
    savedMetric: ApiUsageLimitMetric,
    savedLimit: Long,
    savedWarningThresholdPercent: Int,
    liveAlertState: MonthlyUsageAlertState,
    onAlertEnabledChange: (Boolean) -> Unit,
    onLimitMetricChange: (ApiUsageLimitMetric) -> Unit,
    onLimitInputChange: (String) -> Unit,
    onWarningThresholdPercentChange: (Int) -> Unit,
    onApplyThreshold: (Boolean, ApiUsageLimitMetric, Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    val parsedLimit = limitInput.trim().toLongOrNull()
    val limitValidationError = when {
        !alertEnabled -> null
        limitInput.isBlank() -> "Enter a monthly ${limitMetric.unitLabel} limit."
        parsedLimit == null -> "Monthly limit must be a whole number."
        parsedLimit <= 0L -> "Monthly limit must be greater than 0."
        else -> null
    }
    val canApply = !alertEnabled || (parsedLimit != null && parsedLimit > 0L)
    val hasPendingChanges = alertEnabled != savedAlertEnabled ||
        limitMetric != savedMetric ||
        (parsedLimit != null && parsedLimit != savedLimit) ||
        warningThresholdPercent != savedWarningThresholdPercent

    val formatter = NumberFormat.getIntegerInstance(Locale.US)
    val previewLimit = parsedLimit ?: savedLimit
    val triggerAtValue = if (previewLimit > 0L) {
        ceil(previewLimit * (warningThresholdPercent / 100.0)).toLong().coerceIn(1L, previewLimit)
    } else {
        0L
    }

    val presetValues = if (limitMetric == ApiUsageLimitMetric.REQUESTS) {
        listOf(10L, 50L, 100L, 500L)
    } else {
        listOf(10_000L, 50_000L, 100_000L, 500_000L)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = palette.cardBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = palette.accentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Monthly Limit Warning",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Show a visual dashboard alert at your monthly cap",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = alertEnabled,
                onCheckedChange = onAlertEnabledChange,
                modifier = Modifier.testTag("monthly_alert_enabled_switch")
            )
        }

        AnimatedVisibility(visible = alertEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_limit_metric_selector")
                ) {
                    ApiUsageLimitMetric.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = limitMetric == option,
                            onClick = { onLimitMetricChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ApiUsageLimitMetric.entries.size
                            ),
                            modifier = Modifier.testTag("monthly_limit_metric_${option.name.lowercase()}")
                        ) {
                            Text(option.label, maxLines = 1)
                        }
                    }
                }

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { raw ->
                        onLimitInputChange(raw.filter { it.isDigit() }.take(12))
                    },
                    label = { Text("Monthly Limit (${limitMetric.unitLabel})") },
                    placeholder = {
                        Text(if (limitMetric == ApiUsageLimitMetric.REQUESTS) "e.g. 100" else "e.g. 100000")
                    },
                    singleLine = true,
                    isError = limitValidationError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monthly_limit_input")
                )

                if (limitValidationError != null) {
                    Text(
                        text = limitValidationError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("monthly_limit_validation_error")
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetValues.forEach { preset ->
                        SuggestionChip(
                            onClick = { onLimitInputChange(preset.toString()) },
                            label = { Text(formatter.format(preset)) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("monthly_limit_preset_$preset")
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alert Threshold",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$warningThresholdPercent% (${formatter.format(triggerAtValue)} ${limitMetric.unitLabel})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentCyan,
                            modifier = Modifier.testTag("monthly_threshold_summary_label")
                        )
                    }

                    Slider(
                        value = warningThresholdPercent.toFloat(),
                        onValueChange = { raw ->
                            val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(50, 100)
                            onWarningThresholdPercentChange(snapped)
                        },
                        valueRange = 50f..100f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("monthly_threshold_slider")
                    )
                }
            }
        }

        if (liveAlertState.shouldShowWarning) {
            val isCap = liveAlertState.severity == UsageThresholdSeverity.LIMIT_REACHED
            val warnTint = if (isCap) MaterialTheme.colorScheme.error else Color(0xFFF59E0B)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = warnTint.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, warnTint.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("settings_monthly_threshold_active_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = warnTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isCap) {
                            "Monthly cap reached (${formatter.format(liveAlertState.currentUsage)} / ${formatter.format(liveAlertState.monthlyLimit)}) — warning active on dashboard."
                        } else {
                            "Threshold reached (${liveAlertState.usagePercent}% of ${formatter.format(liveAlertState.monthlyLimit)}) — warning active on dashboard."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Button(
            onClick = {
                val targetLimit = parsedLimit ?: savedLimit
                onApplyThreshold(
                    alertEnabled,
                    limitMetric,
                    targetLimit,
                    warningThresholdPercent
                )
            },
            enabled = canApply && hasPendingChanges,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("save_monthly_threshold_button")
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (hasPendingChanges) "Apply Monthly Limit" else "Monthly Limit Active"
            )
        }
    }
}
