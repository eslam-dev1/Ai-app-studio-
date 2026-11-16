package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StudioProject
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette
import java.util.Locale

@Composable
fun AiPerformanceDashboardCard(
    project: StudioProject,
    onOptimizeProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    var isExpanded by rememberSaveable(project.id) { mutableStateOf(false) }

    val totalFiles = project.files.size
    val profile = remember(project.files, project.lastGenerationLatencyMs, project.updatedAtEpochMs) {
        com.example.diagnostics.CodeQuickActionEngine.analyzeProjectPerformance(project)
    }
    val totalLines = profile.totalLinesOfCode
    val composableCount = profile.composableCount
    val isMeasuredLatency = project.lastGenerationLatencyMs > 0L

    CyberGradientCard(
        cornerRadius = 18.dp,
        borderWidth = 1.5.dp,
        containerColor = palette.cardSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_performance_dashboard_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Expand / Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "AI Performance & Latency Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Efficiency Score: ${profile.efficiencyScore}% • ${totalFiles} files • $totalLines LOC • ${profile.complexityLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.accentCyan.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, palette.accentCyan)
                ) {
                    Text(
                        text = "${profile.efficiencyScore}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("toggle_performance_dashboard_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse dashboard" else "Expand dashboard"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(color = palette.cardBorder)

                    // Metrics Grid Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metric 1: Generation Latency
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.cardSurfaceHigh,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_latency_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = palette.accentPurple, modifier = Modifier.size(14.dp))
                                    Text("Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${profile.generationLatencyMs} ms",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isMeasuredLatency) "Measured generation time" else "Estimated AST pass",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = palette.accentEmerald
                                )
                            }
                        }

                        // Metric 2: Inference Speed / Frame Budget
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.cardSurfaceHigh,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("metric_speed_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(14.dp))
                                    Text("Throughput", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1f tps", profile.tokenThroughputTps),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(Locale.US, "Est. frame %.1f ms", profile.estimatedFrameTimeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = palette.accentCyan
                                )
                            }
                        }
                    }

                    // Complexity Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Complexity (${profile.complexityLabel}: ${profile.complexityScore}/100)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$composableCount @Composables • ${profile.stateHolderCount} States",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                        val complexityFraction = (profile.complexityScore / 100f).coerceIn(0.08f, 1f)
                        LinearProgressIndicator(
                            progress = { complexityFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("complexity_progress_bar")
                        )
                    }

                    // Suggestions Section
                    Text(
                        text = "AST Optimization Insights (${profile.insights.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile.insights.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.cardSurfaceHigh,
                                border = BorderStroke(1.dp, palette.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✅ Codebase follows Compose skippability, LazyList keys, and remember best practices.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            profile.insights.take(5).forEach { insight ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.cardSurfaceHigh,
                                    border = BorderStroke(1.dp, palette.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "[${insight.impactLevel}] ${insight.filePath.substringAfterLast('/')}:${insight.line} — ${insight.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.accentCyan
                                        )
                                        Text(
                                            text = insight.recommendation,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action CTA
                    Button(
                        onClick = onOptimizeProject,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentCyan,
                            contentColor = Color(0xFF03111C)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("apply_ai_performance_optimizations_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply AI Performance Optimizations", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
