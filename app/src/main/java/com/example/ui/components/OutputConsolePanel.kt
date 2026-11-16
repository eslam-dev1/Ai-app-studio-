package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DockedOutputConsoleBar(
    logs: List<ConsoleLogEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRunCompilerCheck: () -> Unit,
    onClearLogs: () -> Unit,
    onSelectFileFromLog: (filePath: String) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorCount = remember(logs) { logs.count { it.level == ConsoleLogLevel.ERROR } }
    val warnCount = remember(logs) { logs.count { it.level == ConsoleLogLevel.WARN } }
    val latestLog = logs.lastOrNull()

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .testTag("docked_output_console_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Compact Status Header Bar (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("output_console_header_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Output Console",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Console",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Error Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (errorCount > 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "E: $errorCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = if (errorCount > 0) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("console_error_count_badge")
                        )
                    }

                    // Warning Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (warnCount > 0) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "W: $warnCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = if (warnCount > 0) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("console_warn_count_badge")
                        )
                    }

                    if (!isExpanded && latestLog != null) {
                        Text(
                            text = "${latestLog.level.badge}/${latestLog.tag}: ${latestLog.message}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = onRunCompilerCheck,
                        label = { Text("Check Code", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("console_quick_check_button")
                    )

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("output_console_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (isExpanded) "Collapse Output Console" else "Expand Output Console"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                OutputConsoleContent(
                    logs = logs,
                    onRunCompilerCheck = onRunCompilerCheck,
                    onClearLogs = onClearLogs,
                    onSelectFileFromLog = onSelectFileFromLog,
                    onShowToast = onShowToast,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }
        }
    }
}

@Composable
fun OutputConsoleContent(
    logs: List<ConsoleLogEntry>,
    onRunCompilerCheck: () -> Unit,
    onClearLogs: () -> Unit,
    onSelectFileFromLog: (filePath: String) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedLevelFilter by rememberSaveable { mutableStateOf<ConsoleLogLevel?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredLogs = remember(logs, selectedLevelFilter, searchQuery) {
        logs.filter { entry ->
            val matchesLevel = selectedLevelFilter == null || entry.level == selectedLevelFilter
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                entry.message.contains(q, ignoreCase = true) ||
                entry.tag.contains(q, ignoreCase = true) ||
                (entry.filePath?.contains(q, ignoreCase = true) == true)
            matchesLevel && matchesQuery
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Filter Chips + Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    FilterChip(
                        selected = selectedLevelFilter == null,
                        onClick = { selectedLevelFilter = null },
                        label = { Text("All (${logs.size})", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("console_filter_all")
                    )
                }
                items(ConsoleLogLevel.entries) { level ->
                    val count = logs.count { it.level == level }
                    FilterChip(
                        selected = selectedLevelFilter == level,
                        onClick = {
                            selectedLevelFilter = if (selectedLevelFilter == level) null else level
                        },
                        label = {
                            Text("${level.badge}: $count", style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.testTag("console_filter_${level.name.lowercase()}")
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onRunCompilerCheck,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("run_compiler_check_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run Compiler Check",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val formatted = filteredLogs.joinToString("\n") { formatLogLineText(it) }
                        clipboardManager.setText(AnnotatedString(formatted))
                        onShowToast("Copied ${filteredLogs.size} console log lines")
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_console_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Console Logs",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("clear_console_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Clear Console Logs",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Compact Search / Tag Filter Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Filter logcat by tag, file, or compiler error…",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("console_search_input")
        )

        // Logcat Monospace Stream
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No console entries match current filter. Tap 'Check Code' to run static compiler analysis.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("console_log_list"),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    ConsoleLogRow(
                        entry = entry,
                        onClickFile = { path -> onSelectFileFromLog(path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(
    entry: ConsoleLogEntry,
    onClickFile: (String) -> Unit
) {
    val timeStr = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampMs))
    }

    val (badgeBg, badgeFg, textColor) = when (entry.level) {
        ConsoleLogLevel.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        ConsoleLogLevel.WARN -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        ConsoleLogLevel.SUCCESS -> Triple(
            Color(0xFF1B5E20),
            Color(0xFFA5D6A7),
            Color(0xFF66BB6A)
        )
        ConsoleLogLevel.INFO -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.onSurface
        )
        ConsoleLogLevel.DEBUG -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val rowModifier = if (entry.filePath != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClickFile(entry.filePath) }
            .padding(vertical = 2.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = timeStr,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline
        )

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeBg
        ) {
            Text(
                text = "${entry.level.badge}/${entry.tag}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badgeFg,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.message,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = textColor
            )
            if (entry.filePath != null) {
                Text(
                    text = "Tap to open ${entry.filePath}" +
                        (entry.lineNumber?.let { " (line $it)" } ?: ""),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatLogLineText(entry: ConsoleLogEntry): String {
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampMs))
    return "$timeStr ${entry.level.badge}/${entry.tag}: ${entry.message}"
}
