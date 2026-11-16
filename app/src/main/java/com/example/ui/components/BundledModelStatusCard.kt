package com.example.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.generator.BundledModelManager
import com.example.data.generator.BundledModelState
import com.example.data.generator.ModelBenchmarkCalculator
import com.example.data.generator.ModelBenchmarkUiState
import com.example.model.StudioSettings
import java.io.File
import java.util.Locale

@Composable
fun BundledModelStatusCard(
    bundledModelState: BundledModelState,
    settings: StudioSettings,
    onRetryOrRestore: () -> Unit,
    onReinitializeModel: () -> Unit = onRetryOrRestore,
    onDownloadBuiltInFromMirrors: () -> Unit = {},
    onCancelBuiltInMirrorDownload: () -> Unit = {},
    benchmarkState: ModelBenchmarkUiState = ModelBenchmarkUiState.Idle,
    onTestModel: () -> Unit = {},
    onCancelTestModel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (badgeText, badgeContainer, badgeContent) = when (bundledModelState) {
        is BundledModelState.Checking -> Triple(
            "Checking",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        is BundledModelState.Extracting -> Triple(
            "${bundledModelState.percent}%",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is BundledModelState.AssetMissingNeedsDownload -> Triple(
            "Not Bundled",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        is BundledModelState.DownloadingFromMirror -> Triple(
            "Mirror ${bundledModelState.mirrorIndex}/${bundledModelState.totalMirrors}",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is BundledModelState.Initializing -> Triple(
            "Initializing",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is BundledModelState.Ready -> Triple(
            "Ready Offline",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        is BundledModelState.Unavailable -> Triple(
            "Deferred",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        BundledModelState.Idle -> Triple(
            "Standby",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val leadingIcon = when (bundledModelState) {
        is BundledModelState.Checking,
        is BundledModelState.Extracting,
        is BundledModelState.DownloadingFromMirror,
        is BundledModelState.Initializing -> Icons.Default.Downloading
        is BundledModelState.AssetMissingNeedsDownload -> Icons.Default.CloudDownload
        is BundledModelState.Ready -> Icons.Default.CheckCircle
        is BundledModelState.Unavailable -> Icons.Default.Warning
        BundledModelState.Idle -> Icons.Default.Memory
    }

    val iconTint = when (bundledModelState) {
        is BundledModelState.Ready -> MaterialTheme.colorScheme.secondary
        is BundledModelState.Unavailable -> MaterialTheme.colorScheme.error
        is BundledModelState.AssetMissingNeedsDownload -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_bundled_model_status_card"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when (bundledModelState) {
                is BundledModelState.Extracting,
                is BundledModelState.DownloadingFromMirror,
                is BundledModelState.Initializing -> MaterialTheme.colorScheme.primary
                is BundledModelState.Unavailable -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
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
                        color = iconTint.copy(alpha = 0.14f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = "Bundled model extraction status icon",
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Built-in Offline AI Extraction",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = BundledModelManager.BUNDLED_DISPLAY_NAME,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeContainer,
                    modifier = Modifier.testTag("settings_bundled_model_status_badge")
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeContent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            when (bundledModelState) {
                is BundledModelState.Checking -> {
                    Text(
                        text = bundledModelState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_bundled_extraction_progress")
                    )
                }

                is BundledModelState.Extracting -> {
                    val copiedMb = bundledModelState.copiedBytes.toDouble() / (1024.0 * 1024.0)
                    val totalMb = bundledModelState.totalBytes.toDouble() / (1024.0 * 1024.0)
                    val detailText = if (totalMb > 0.0) {
                        String.format(
                            Locale.US,
                            "Extracting built_in_coder.task: %.1f MB / %.1f MB (%d%%)",
                            copiedMb,
                            totalMb,
                            bundledModelState.percent
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "Extracting built_in_coder.task: %.1f MB copied…",
                            copiedMb
                        )
                    }
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (bundledModelState.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { (bundledModelState.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_bundled_extraction_progress")
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_bundled_extraction_progress")
                        )
                    }
                    Text(
                        text = "Streaming APK asset (${BundledModelManager.BUNDLED_ASSET_PATH}) to app storage (filesDir/models/)…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is BundledModelState.AssetMissingNeedsDownload -> {
                    Text(
                        text = bundledModelState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onDownloadBuiltInFromMirrors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_download_builtin_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(BundledModelManager.DOWNLOAD_BUTTON_LABEL)
                    }
                }

                is BundledModelState.DownloadingFromMirror -> {
                    val dlMb = bundledModelState.downloadedBytes.toDouble() / (1024.0 * 1024.0)
                    val totalMb = bundledModelState.totalBytes.toDouble() / (1024.0 * 1024.0)
                    val dlText = if (totalMb > 0.0) {
                        String.format(
                            Locale.US,
                            "Mirror %d/%d (%s): %.1f MB / %.1f MB (%d%%)",
                            bundledModelState.mirrorIndex,
                            bundledModelState.totalMirrors,
                            bundledModelState.mirrorLabel,
                            dlMb,
                            totalMb,
                            bundledModelState.percent
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "Trying Mirror %d/%d (%s): %.1f MB…",
                            bundledModelState.mirrorIndex,
                            bundledModelState.totalMirrors,
                            bundledModelState.mirrorLabel,
                            dlMb
                        )
                    }
                    Text(
                        text = dlText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (bundledModelState.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { (bundledModelState.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_mirror_download_progress")
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_mirror_download_progress")
                        )
                    }
                    OutlinedButton(
                        onClick = onCancelBuiltInMirrorDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_cancel_mirror_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Mirror Download")
                    }
                }

                is BundledModelState.Initializing -> {
                    Text(
                        text = bundledModelState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_bundled_init_progress")
                    )
                }

                is BundledModelState.Ready -> {
                    val formattedSize = Formatter.formatShortFileSize(context, bundledModelState.sizeBytes)
                    val fileName = File(bundledModelState.modelPath).name
                    val isBuiltInActive = settings.localModelFilePath.trim().endsWith(BundledModelManager.BUNDLED_MODEL_FILE_NAME)
                    Text(
                        text = "Extracted ($fileName • $formattedSize) • MaxTokens: ${BundledModelManager.DEFAULT_MAX_TOKENS} • Temp: ${BundledModelManager.DEFAULT_TEMPERATURE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isBuiltInActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom active: ${settings.localModelDisplayName.ifBlank { "External Model" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onRetryOrRestore,
                                modifier = Modifier.testTag("settings_switch_to_builtin_button")
                            ) {
                                Text("Use Built-in")
                            }
                        }
                    }
                }

                is BundledModelState.Unavailable -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bundledModelState.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (!BundledModelManager.hasBundledModelAsset(context)) {
                        Button(
                            onClick = onDownloadBuiltInFromMirrors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_retry_mirror_download_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(BundledModelManager.DOWNLOAD_BUTTON_LABEL)
                        }
                    }
                }

                BundledModelState.Idle -> {
                    Text(
                        text = "Bundled asset: ${BundledModelManager.BUNDLED_MODEL_FILE_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isBusy = bundledModelState is BundledModelState.Checking ||
                bundledModelState is BundledModelState.Extracting ||
                bundledModelState is BundledModelState.DownloadingFromMirror ||
                bundledModelState is BundledModelState.Initializing
            val isBenchmarkRunning = benchmarkState is ModelBenchmarkUiState.Running

            OutlinedButton(
                onClick = onReinitializeModel,
                enabled = !isBusy && !isBenchmarkRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("settings_reinitialize_model_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBusy) "Re-initializing Model…" else "Re-initialize Model"
                )
            }

            FilledTonalButton(
                onClick = {
                    if (isBenchmarkRunning) onCancelTestModel() else onTestModel()
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("settings_test_model_button")
            ) {
                Icon(
                    imageVector = if (isBenchmarkRunning) Icons.Default.Stop else Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBenchmarkRunning) {
                        val running = benchmarkState as ModelBenchmarkUiState.Running
                        "Stop Test (${running.currentIndex}/${running.totalPrompts})"
                    } else {
                        "Test Model"
                    }
                )
            }

            when (benchmarkState) {
                is ModelBenchmarkUiState.Running -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_model_benchmark_running")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Running prompt ${benchmarkState.currentIndex}/${benchmarkState.totalPrompts}: ${benchmarkState.currentLabel}…",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val progress = (benchmarkState.completedResults.size.toFloat() /
                                benchmarkState.totalPrompts.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            benchmarkState.completedResults.forEach { step ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = step.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = ModelBenchmarkCalculator.formatDurationLabel(step.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                is ModelBenchmarkUiState.Completed -> {
                    val summary = benchmarkState.summary
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_model_benchmark_results")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Benchmark Results (${summary.results.size} prompts)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Avg ${ModelBenchmarkCalculator.formatDurationLabel(summary.averageMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BenchmarkMetricPill(
                                    title = "Fastest",
                                    value = ModelBenchmarkCalculator.formatDurationLabel(summary.minMs),
                                    modifier = Modifier.weight(1f)
                                )
                                BenchmarkMetricPill(
                                    title = "Slowest",
                                    value = ModelBenchmarkCalculator.formatDurationLabel(summary.maxMs),
                                    modifier = Modifier.weight(1f)
                                )
                                BenchmarkMetricPill(
                                    title = "Total",
                                    value = ModelBenchmarkCalculator.formatDurationLabel(summary.totalMs),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            summary.results.forEach { step ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = step.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = ModelBenchmarkCalculator.formatDurationLabel(step.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (step.responsePreview.isNotBlank()) {
                                        Text(
                                            text = step.responsePreview,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is ModelBenchmarkUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_model_benchmark_error")
                    ) {
                        Text(
                            text = benchmarkState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                ModelBenchmarkUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun BenchmarkMetricPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
