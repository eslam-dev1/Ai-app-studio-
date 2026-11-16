package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.CodeQuickActionEngine
import com.example.diagnostics.ProjectStaticAnalyzer
import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.model.StudioProject
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette

@Composable
fun PreBuildLinterCard(
    project: StudioProject,
    onAutoFixProject: () -> Unit,
    onNavigateToFile: (filePath: String, lineNumber: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var isExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val diagnostics = remember(project.files, project.updatedAtEpochMs, refreshKey) {
        ProjectStaticAnalyzer.analyzeProject(project)
    }

    val issueEntries = remember(diagnostics) {
        diagnostics.filter { it.level == ConsoleLogLevel.ERROR || it.level == ConsoleLogLevel.WARN }
    }

    val errorCount = issueEntries.count { it.level == ConsoleLogLevel.ERROR }
    val warnCount = issueEntries.count { it.level == ConsoleLogLevel.WARN }
    val isClean = errorCount == 0

    val filteredIssues = remember(issueEntries, selectedFilter) {
        when (selectedFilter) {
            "ERRORS" -> issueEntries.filter { it.level == ConsoleLogLevel.ERROR }
            "WARNINGS" -> issueEntries.filter { it.level == ConsoleLogLevel.WARN }
            else -> issueEntries
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pre_build_linter_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (isClean) palette.accentEmerald.copy(alpha = 0.6f) else palette.cardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isClean) palette.accentEmerald.copy(alpha = 0.16f) else Color(0xFFEF4444).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, if (isClean) palette.accentEmerald else Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = if (isClean) Icons.Default.Verified else Icons.Default.BugReport,
                            contentDescription = null,
                            tint = if (isClean) palette.accentEmerald else Color(0xFFEF4444),
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Pre-Build Static Linter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isClean) "Syntax & compiler validation passed" else "Found issues requiring review before compile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isClean) palette.accentEmerald.copy(alpha = 0.16f) else Color(0xFFEF4444).copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (isClean) "0 Errors" else "$errorCount Errors",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isClean) palette.accentEmerald else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.cardSurfaceHigh,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isClean) "🟢 Ready to Compile" else "🔴 Compilation Blocked",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isClean) palette.accentEmerald else Color(0xFFEF4444)
                        )
                        if (warnCount > 0) {
                            Text(
                                text = "• $warnCount warning(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { refreshKey++ },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-analyze", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-Check", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Quick Auto-Fix Action Button if issues exist
            if (!isClean || warnCount > 0) {
                Button(
                    onClick = onAutoFixProject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentCyan,
                        contentColor = Color(0xFF04120B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("autofix_linter_issues_button")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚡ Auto-Fix All Issues (Imports & Manifest)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Expandable details toggle
            if (issueEntries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide Issues Breakdown" else "View All Issues (${issueEntries.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accentCyan
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = palette.accentCyan
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Filter row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedFilter == "ALL",
                                onClick = { selectedFilter = "ALL" },
                                label = { Text("All (${issueEntries.size})") }
                            )
                            FilterChip(
                                selected = selectedFilter == "ERRORS",
                                onClick = { selectedFilter = "ERRORS" },
                                label = { Text("Errors ($errorCount)") }
                            )
                            FilterChip(
                                selected = selectedFilter == "WARNINGS",
                                onClick = { selectedFilter = "WARNINGS" },
                                label = { Text("Warnings ($warnCount)") }
                            )
                        }

                        // Issue items
                        filteredIssues.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = palette.cardSurfaceHigh,
                                border = BorderStroke(1.dp, palette.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val path = entry.filePath
                                        if (path != null) {
                                            onNavigateToFile(path, entry.lineNumber ?: 1)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (entry.level == ConsoleLogLevel.ERROR) Icons.Default.Error else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (entry.level == ConsoleLogLevel.ERROR) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = entry.filePath?.substringAfterLast('/') ?: entry.tag,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.accentCyan
                                            )
                                            if (entry.lineNumber != null) {
                                                Text(
                                                    text = "Line ${entry.lineNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            text = entry.message,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkResourceAnalyzerCard(
    project: StudioProject,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    val metrics = remember(project.files, project.appName) {
        CodeQuickActionEngine.analyzeApkMetrics(project)
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("apk_size_analyzer_card"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "APK Analyzer & Metrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Estimated size: ~${metrics.estimatedApkSizeMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accentEmerald
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.accentPurple.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, palette.accentPurple)
                ) {
                    Text(
                        text = "${metrics.totalLinesOfCode} Lines",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (palette.isDark) Color(0xFFC084FC) else Color(0xFF7E22CE),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = palette.cardBorder)

            // Composition Bar (DEX / Res / Assets)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Payload Breakdown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "DEX: ${metrics.dexPercentage}% • RES: ${metrics.resPercentage}% • ASSETS: ${metrics.assetsPercentage}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = palette.accentCyan
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(metrics.dexPercentage.toFloat())
                            .fillMaxWidth()
                            .background(palette.accentCyan)
                    )
                    Box(
                        modifier = Modifier
                            .weight(metrics.resPercentage.toFloat())
                            .fillMaxWidth()
                            .background(palette.accentPurple)
                    )
                    Box(
                        modifier = Modifier
                            .weight(metrics.assetsPercentage.toFloat())
                            .fillMaxWidth()
                            .background(palette.accentEmerald)
                    )
                }
            }

            // Key Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardSurfaceHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Kotlin Sources", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${metrics.kotlinFileCount} Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardSurfaceHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Composable Screens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${metrics.composableCount} Screens", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.accentCyan)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardSurfaceHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Gradle Deps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${metrics.gradleDependenciesCount} Libs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Declared Permissions
            if (metrics.declaredPermissions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = palette.accentEmerald)
                        Text(
                            text = "Declared Permissions (${metrics.declaredPermissions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        metrics.declaredPermissions.forEach { perm ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.cardSurfaceHigh,
                                border = BorderStroke(1.dp, palette.cardBorder)
                            ) {
                                Text(
                                    text = perm,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
