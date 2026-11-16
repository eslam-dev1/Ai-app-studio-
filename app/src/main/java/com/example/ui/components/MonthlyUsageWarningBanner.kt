package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.MonthlyUsageAlertState
import com.example.data.preferences.UsageThresholdSeverity
import com.example.ui.theme.LocalStudioCyberPalette
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MonthlyUsageWarningBanner(
    alertState: MonthlyUsageAlertState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!alertState.shouldShowWarning) return

    val palette = LocalStudioCyberPalette.current
    val isLimitReached = alertState.severity == UsageThresholdSeverity.LIMIT_REACHED

    val accentColor = if (isLimitReached) {
        MaterialTheme.colorScheme.error
    } else {
        Color(0xFFF59E0B)
    }

    val containerColor = if (isLimitReached) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
    } else {
        Color(0xFFF59E0B).copy(alpha = 0.14f)
    }

    val contentTextColor = if (isLimitReached) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val formatter = NumberFormat.getIntegerInstance(Locale.US)
    val usedFormatted = formatter.format(alertState.currentUsage)
    val limitFormatted = formatter.format(alertState.monthlyLimit)
    val progressFraction = if (alertState.monthlyLimit > 0L) {
        (alertState.currentUsage.toFloat() / alertState.monthlyLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    val titleText = if (isLimitReached) {
        "Monthly API Limit Reached"
    } else {
        "Approaching Monthly API Limit"
    }

    val descriptionText = if (isLimitReached) {
        "Used $usedFormatted of $limitFormatted monthly ${alertState.metric.unitLabel} (${alertState.usagePercent}%). Switch to Local AI or increase your limit in Settings."
    } else {
        "Used $usedFormatted of $limitFormatted monthly ${alertState.metric.unitLabel} (${alertState.usagePercent}% — warning threshold ${alertState.warningThresholdPercent}%)."
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.4.dp, accentColor.copy(alpha = 0.72f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_api_usage_warning_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLimitReached) {
                                    Icons.Default.ErrorOutline
                                } else {
                                    Icons.Default.WarningAmber
                                },
                                contentDescription = titleText,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dashboard_usage_warning_title")
                        )
                        Text(
                            text = "${alertState.metric.label} • ${alertState.yearMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentTextColor.copy(alpha = 0.78f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "${alertState.usagePercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("dashboard_usage_warning_percent")
                    )
                }
            }

            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodySmall,
                color = contentTextColor
            )

            LinearProgressIndicator(
                progress = { progressFraction },
                color = accentColor,
                trackColor = palette.cardSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("dashboard_usage_warning_progress")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("dashboard_usage_warning_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isLimitReached) "Adjust Limit" else "Manage Usage")
                }
            }
        }
    }
}
