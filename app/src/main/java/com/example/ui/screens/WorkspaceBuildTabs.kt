package com.example.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.github.GitHubBuildRepository
import com.example.model.GitRepoState
import com.example.model.StudioProject
import com.example.model.StudioSettings
import com.example.ui.components.AiPerformanceDashboardCard
import com.example.ui.components.ApkBrandingStudioCard
import com.example.ui.components.CyberCodeFactoryCard
import com.example.ui.components.CyberGradientCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkspaceBuildApkTab(
    project: StudioProject,
    settings: StudioSettings,
    gitState: GitRepoState,
    hasGitHubPat: Boolean,
    isGitBusy: Boolean,
    isCloudBuildBusy: Boolean,
    cloudBuildProgress: String?,
    onTriggerCloudBuild: () -> Unit,
    onRefreshCloudBuildStatus: () -> Unit,
    onPackageZip: suspend () -> android.net.Uri?,
    onExportBuildLogs: suspend () -> android.net.Uri?,
    onAutoFixProject: () -> Unit = {},
    onSaveBranding: (
        appName: String,
        packageId: String,
        versionName: String,
        launcherIconBase64: String?,
        launcherIconVectorXml: String?
    ) -> Unit = { _, _, _, _, _ -> },
    onInitGit: () -> Unit,
    onRefreshGit: () -> Unit,
    onStageFile: (String) -> Unit,
    onUnstageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommitGit: (String) -> Unit,
    onConfigureRemote: (String) -> Unit,
    onPushGit: () -> Unit,
    onPullGit: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val palette = LocalStudioCyberPalette.current
    val scope = rememberCoroutineScope()

    var isPackagingZip by remember { mutableStateOf(false) }
    var showGitSection by rememberSaveable { mutableStateOf(false) }
    var showWorkflowPreview by rememberSaveable { mutableStateOf(false) }
    var showBuildAnimationPreview by rememberSaveable { mutableStateOf(false) }

    val workflowPreview = remember(project.appName) {
        GitHubBuildRepository.buildApkWorkflowYaml(project.appName)
    }
    val hasArtifactReady = !project.lastArtifactUrl.isNullOrBlank()
    val buildStatusText = when {
        hasArtifactReady -> "APK Ready"
        !project.lastBuildStatus.isNullOrBlank() -> project.lastBuildStatus
        isCloudBuildBusy -> cloudBuildProgress ?: "Running Cloud Gradle Build…"
        else -> "Ready to Build Debug APK"
    }

    val hasBuildError = remember(project.lastBuildStatus) {
        val status = project.lastBuildStatus?.lowercase(Locale.ROOT) ?: ""
        status.contains("fail") || status.contains("error")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("workspace_build_apk_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Auto-Fix Error Loop ("Smart Self-Healing") Banner for APK Builder Pipeline
        if (hasBuildError) {
            item {
                CyberGradientCard(
                    cornerRadius = 16.dp,
                    borderWidth = 1.4.dp,
                    containerColor = palette.cardSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apk_builder_auto_fix_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = palette.accentPurple
                            )
                            Column {
                                Text(
                                    text = "Issue detected — Auto-fixing with AI...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentCyan
                                )
                                Text(
                                    text = project.lastBuildStatus ?: "Build failure detected. Self-healing ready.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onOpenConsole()
                                onTriggerCloudBuild()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentCyan,
                                contentColor = Color(0xFF031018)
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("apk_builder_fix_with_ai_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fix with AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Real-Time RAM & Storage Monitor Overlay for MediaPipe / Build performance
        item {
            ModelPerformanceMonitorCard(settings = settings)
        }

        // AI Performance Dashboard & Latency Monitor
        item {
            AiPerformanceDashboardCard(
                project = project,
                onOptimizeProject = onAutoFixProject
            )
        }

        // 1. Live Animated Code Factory Card while building APK or testing animation preview
        if (isCloudBuildBusy || showBuildAnimationPreview) {
            item {
                val buildStepIdx = when {
                    cloudBuildProgress?.contains("push", ignoreCase = true) == true -> 1
                    cloudBuildProgress?.contains("trigger", ignoreCase = true) == true ||
                        cloudBuildProgress?.contains("workflow", ignoreCase = true) == true -> 2
                    else -> 0
                }
                CyberCodeFactoryCard(
                    projectName = project.appName,
                    stepIndex = if (showBuildAnimationPreview && !isCloudBuildBusy) 1 else buildStepIdx,
                    totalSteps = 3,
                    stepMessage = if (showBuildAnimationPreview && !isCloudBuildBusy) {
                        "Demonstrating Cybernetic Server Factory & Rotating Gears…"
                    } else {
                        cloudBuildProgress ?: "Compiling Debug APK via GitHub Actions…"
                    },
                    engineLabel = "GitHub Actions • Gradle 8",
                    streamingCode = workflowPreview,
                    onCancel = if (showBuildAnimationPreview && !isCloudBuildBusy) {
                        { showBuildAnimationPreview = false }
                    } else null
                )
            }
        }

        // Pre-Build APK Branding Studio (App Display Name, Package ID, Version, Launcher Icon)
        item {
            ApkBrandingStudioCard(
                project = project,
                onSaveBranding = onSaveBranding
            )
        }

        // 2. APK Ready Status + Prominent "Build & Install APK" CTA Card + Demo Preview Toggle
        item {
            CyberGradientCard(
                cornerRadius = 20.dp,
                borderWidth = 1.6.dp,
                containerColor = palette.cardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            Icon(
                                imageVector = if (hasArtifactReady) Icons.Default.CheckCircle else Icons.Default.BuildCircle,
                                contentDescription = null,
                                tint = if (hasArtifactReady) palette.accentEmerald else palette.accentCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = if (hasArtifactReady) "APK Ready" else "Android APK Builder",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${project.appName} • ${project.packageName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (hasArtifactReady) {
                                palette.accentEmerald.copy(alpha = 0.16f)
                            } else {
                                palette.cardSurfaceHigh
                            },
                            border = BorderStroke(
                                1.dp,
                                if (hasArtifactReady) palette.accentEmerald else palette.cardBorder
                            )
                        ) {
                            Text(
                                text = if (hasArtifactReady) "APK Ready" else "Cloud CI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasArtifactReady) palette.accentEmerald else palette.accentCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

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
                            Text(
                                text = buildStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = if (hasArtifactReady) palette.accentEmerald else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (project.gitHubRepoUrl != null) {
                                OutlinedButton(
                                    onClick = onRefreshCloudBuildStatus,
                                    enabled = !isCloudBuildBusy,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh status", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Prominent "Build & Install APK" CTA
                    Button(
                        onClick = {
                            if (hasArtifactReady) {
                                uriHandler.openUri(project.lastArtifactUrl!!)
                            } else {
                                onOpenConsole()
                                onTriggerCloudBuild()
                            }
                        },
                        enabled = !isCloudBuildBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasArtifactReady) palette.accentEmerald else MaterialTheme.colorScheme.primary,
                            contentColor = if (hasArtifactReady) androidx.compose.ui.graphics.Color(0xFF04120B) else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cloud_apk_build_button")
                    ) {
                        if (isCloudBuildBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (hasArtifactReady) Icons.Default.Download else Icons.Default.RocketLaunch,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isCloudBuildBusy -> "Building APK…"
                                hasArtifactReady -> "Build & Install APK (Download)"
                                else -> "Build & Install APK"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (project.gitHubRepoUrl != null) {
                            OutlinedButton(
                                onClick = {
                                    onOpenConsole()
                                    onTriggerCloudBuild()
                                },
                                enabled = !isCloudBuildBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("rebuild_apk_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rebuild APK")
                            }
                            OutlinedButton(
                                onClick = {
                                    val url = project.lastActionsUrl ?: "${project.gitHubRepoUrl}/actions"
                                    uriHandler.openUri(url)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actions")
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenConsole,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("open_build_console_button")
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Console")
                        }
                    }

                    // Demo Preview Toggle for the Build Animation
                    FilledTonalButton(
                        onClick = { showBuildAnimationPreview = !showBuildAnimationPreview },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("preview_build_animation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showBuildAnimationPreview) "Stop Animation Preview" else "Preview Build Animation (Rotating Gears & Code)"
                        )
                    }

                    if (!hasGitHubPat) {
                        TextButton(onClick = onOpenSettings) {
                            Text("GitHub PAT missing — Tap to open Settings")
                        }
                    }
                }
            }
        }

        // 3. Export Project ZIP Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = palette.accentCyan
                        )
                        Column {
                            Text(
                                text = "Export Project ZIP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${project.files.size} Gradle, Kotlin & XML source files ready for Termux or Android Studio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isPackagingZip = true
                                val zipUri = onPackageZip()
                                isPackagingZip = false
                                if (zipUri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, zipUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "${project.appName} Android Source")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share ${project.appName}.zip")
                                    )
                                }
                            }
                        },
                        enabled = !isPackagingZip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_zip_button")
                    ) {
                        if (isPackagingZip) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPackagingZip) "Packaging ZIP…" else "Export & Share .ZIP")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    var isExportingLogs by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isExportingLogs = true
                                val logUri = onExportBuildLogs()
                                isExportingLogs = false
                                if (logUri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, logUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "${project.appName} Build Logs")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Download/Share Build Logs")
                                    )
                                }
                            }
                        },
                        enabled = !isExportingLogs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("download_build_logs_button")
                    ) {
                        if (isExportingLogs) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isExportingLogs) "Generating Logs…" else "Download Build Logs (.txt)")
                    }
                }
            }
        }

        // 4. Collapsible Git Repository & Push/Pull Panel
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.cardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { showGitSection = !showGitSection },
                        color = palette.cardSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Source, contentDescription = null, tint = palette.accentCyan)
                                Column {
                                    Text(
                                        text = "Git Version Control",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (gitState.isInitialized) {
                                            "${gitState.changes.size} local change(s) • ${gitState.commits.size} commit(s)"
                                        } else {
                                            "Initialize local Git, stage, commit, and push/pull"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showGitSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Git Version Control"
                            )
                        }
                    }

                    AnimatedVisibility(visible = showGitSection) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                        ) {
                            WorkspaceGitTab(
                                gitState = gitState,
                                isBusy = isGitBusy,
                                hasGitHubPat = hasGitHubPat,
                                onInitRepo = onInitGit,
                                onRefreshStatus = onRefreshGit,
                                onStageFile = onStageFile,
                                onUnstageFile = onUnstageFile,
                                onStageAll = onStageAll,
                                onCommit = onCommitGit,
                                onConfigureRemote = onConfigureRemote,
                                onPush = onPushGit,
                                onPull = onPullGit,
                                onOpenSettings = onOpenSettings
                            )
                        }
                    }
                }
            }
        }

        // 5. Collapsible GitHub Workflow YAML preview
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.cardBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { showWorkflowPreview = !showWorkflowPreview },
                        color = palette.cardSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ".github/workflows/build-apk.yml",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.accentCyan
                            )
                            Icon(
                                imageVector = if (showWorkflowPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle workflow YAML"
                            )
                        }
                    }
                    AnimatedVisibility(visible = showWorkflowPreview) {
                        Text(
                            text = workflowPreview,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ModelPerformanceMonitorCard(
    settings: StudioSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalStudioCyberPalette.current

    var heapUsedMb by remember { mutableLongStateOf(0L) }
    var heapTotalMb by remember { mutableLongStateOf(0L) }
    var heapMaxMb by remember { mutableLongStateOf(0L) }
    var totalSysRamGb by remember { mutableDoubleStateOf(8.0) }
    var availSysRamGb by remember { mutableDoubleStateOf(4.0) }

    LaunchedEffect(Unit) {
        while (true) {
            val rt = Runtime.getRuntime()
            heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
            heapTotalMb = rt.totalMemory() / (1024L * 1024L)
            heapMaxMb = rt.maxMemory() / (1024L * 1024L)

            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0L) {
                totalSysRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                availSysRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
            }
            delay(1500L)
        }
    }

    val modelPath = settings.localModelFilePath.trim()
    val hasModel = modelPath.isNotBlank() && File(modelPath).exists()
    val modelSizeMb = if (hasModel) File(modelPath).length() / (1024.0 * 1024.0) else 0.0

    CyberGradientCard(
        cornerRadius = 18.dp,
        borderWidth = 1.6.dp,
        containerColor = palette.cardSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("model_performance_monitor_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = palette.accentCyan
                    )
                    Text(
                        text = "Real-Time RAM & Storage Monitor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.cardSurfaceHigh,
                    border = BorderStroke(1.dp, palette.accentEmerald)
                ) {
                    Text(
                        text = "Live • MediaPipe Inference",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accentEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // JVM Heap Progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "JVM Heap Memory:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$heapUsedMb MB / $heapTotalMb MB (Max $heapMaxMb MB)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val heapFraction = if (heapMaxMb > 0L) (heapUsedMb.toFloat() / heapMaxMb.toFloat()).coerceIn(0f, 1f) else 0.2f
                LinearProgressIndicator(
                    progress = { heapFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jvm_heap_progress")
                )
            }

            // System RAM Progress
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "System RAM:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f GB Free / %.1f GB Total", availSysRamGb, totalSysRamGb),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val sysRamFraction = if (totalSysRamGb > 0.0) ((totalSysRamGb - availSysRamGb) / totalSysRamGb).toFloat().coerceIn(0f, 1f) else 0.5f
                LinearProgressIndicator(
                    progress = { sysRamFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("system_ram_progress")
                )
            }

            // Active Model Storage Status
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.cardSurfaceHigh,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasModel) "Model Storage: ${settings.localModelDisplayName.ifBlank { File(modelPath).name }}" else "Model Storage: No local model loaded",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasModel) {
                            Text(
                                text = String.format(Locale.US, "File Size: %.1f MB on disk • Ready for offline AI", modelSizeMb),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accentCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
