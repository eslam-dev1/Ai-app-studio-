package com.example.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.example.data.build.LocalMediaPipeBuildPipeline
import com.example.data.build.OfflineApkBuildAndSignEngine
import com.example.data.github.GitHubBuildRepository
import com.example.data.storage.PublicDownloadResult
import com.example.diagnostics.FactoryTelemetryCalculator
import com.example.model.GitRepoState
import com.example.model.OfflineSigningStatus
import com.example.model.StudioProject
import com.example.model.StudioSettings
import com.example.ui.components.AiPerformanceDashboardCard
import com.example.ui.components.ApkBrandingStudioCard
import com.example.ui.components.ApkResourceAnalyzerCard
import com.example.ui.components.CyberCodeFactoryCard
import com.example.ui.components.CyberGradientCard
import com.example.ui.components.PreBuildLinterCard
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
    isOfflineBuildBusy: Boolean = false,
    offlineBuildProgress: String? = null,
    offlineBuildStreamPreview: String = "",
    offlineSigningStatus: OfflineSigningStatus = OfflineSigningStatus(),
    isAutoFixingBuild: Boolean = false,
    onTriggerCloudBuild: () -> Unit,
    onRefreshCloudBuildStatus: () -> Unit,
    onDownloadAndInstallApk: suspend () -> Uri? = { null },
    onTriggerOfflineAiBuildAndSign: (keyAlias: String, storePassword: String, commonName: String) -> Unit = { _, _, _ -> },
    onSignExternalApkOffline: suspend (apkUri: Uri, keyAlias: String, storePassword: String, commonName: String) -> Uri? = { _, _, _, _ -> null },
    onGetOfflineSignedApkUri: () -> Uri? = { null },
    onSaveOfflineSignedApkToDownloads: suspend () -> PublicDownloadResult = { PublicDownloadResult.Failed("Unavailable") },
    onSaveCloudApkToDownloads: suspend () -> PublicDownloadResult = { PublicDownloadResult.Failed("Unavailable") },
    onGetCloudApkShareUri: suspend () -> Uri? = { null },
    onPackageZip: suspend () -> Uri?,
    onSaveProjectZipToDownloads: suspend () -> PublicDownloadResult = { PublicDownloadResult.Failed("Unavailable") },
    onCompleteSafSave: suspend (sourceFile: File, destinationUri: Uri) -> Boolean = { _, _ -> false },
    onExportBuildLogs: suspend () -> Uri?,
    onAutoFixProject: () -> Unit = {},
    onOptimizeProject: () -> Unit = {},
    onFixBuildWithAi: () -> Unit = {},
    onNavigateToFile: (filePath: String, lineNumber: Int) -> Unit = { _, _ -> },
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
    var isSavingZipToDownloads by remember { mutableStateOf(false) }
    var isSavingApkToDownloads by remember { mutableStateOf(false) }
    var isSharingCloudApk by remember { mutableStateOf(false) }
    var pendingSafSourceFile by remember { mutableStateOf<File?>(null) }
    var showGitSection by rememberSaveable { mutableStateOf(false) }
    var showWorkflowPreview by rememberSaveable { mutableStateOf(false) }
    // 0 = AI Build & Sign (Local AI or Cloud AI + apksig), 1 = GitHub Fallback (Last Choice)
    var buildModeIndex by rememberSaveable(project.id) {
        mutableStateOf(0)
    }
    var signingAlias by rememberSaveable(project.id) {
        mutableStateOf(offlineSigningStatus.keyAlias.ifBlank { OfflineApkBuildAndSignEngine.DEFAULT_KEY_ALIAS })
    }
    var signingPassword by rememberSaveable(project.id) {
        mutableStateOf(OfflineApkBuildAndSignEngine.DEFAULT_KEY_PASSWORD)
    }
    var signingCommonName by rememberSaveable(project.id) {
        mutableStateOf(offlineSigningStatus.commonName.ifBlank { project.appName })
    }
    var showSigningConfigFields by rememberSaveable(project.id) { mutableStateOf(false) }
    var showPipelineTasksCode by rememberSaveable(project.id) { mutableStateOf(false) }

    val saveApkDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destUri ->
        val src = pendingSafSourceFile
        pendingSafSourceFile = null
        if (destUri != null && src != null) {
            scope.launch {
                onCompleteSafSave(src, destUri)
            }
        }
    }

    val saveZipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        val src = pendingSafSourceFile
        pendingSafSourceFile = null
        if (destUri != null && src != null) {
            scope.launch {
                onCompleteSafSave(src, destUri)
            }
        }
    }

    val launchApkInstaller: (Uri) -> Unit = { apkUri ->
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(installIntent) }
    }

    val launchApkShareSheet: (Uri) -> Unit = { apkUri ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_SUBJECT, "${project.appName}.apk")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(shareIntent, "Share ${project.appName}.apk")
            )
        }
    }

    val handlePublicDownloadResult: (PublicDownloadResult, Boolean) -> Unit = { result, isApk ->
        if (result is PublicDownloadResult.RequiresSafDocument) {
            pendingSafSourceFile = result.sourceFile
            if (isApk) {
                saveApkDocumentLauncher.launch(result.sourceFile.name)
            } else {
                saveZipDocumentLauncher.launch(result.sourceFile.name)
            }
        }
    }

    val pickApkToSignLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri ->
        if (selectedUri != null) {
            scope.launch {
                onOpenConsole()
                val signedUri = onSignExternalApkOffline(
                    selectedUri,
                    signingAlias,
                    signingPassword,
                    signingCommonName
                )
                if (signedUri != null) {
                    launchApkInstaller(signedUri)
                }
            }
        }
    }

    val workflowPreview = remember(project.appName) {
        GitHubBuildRepository.buildApkWorkflowYaml(project.appName)
    }
    val hasArtifactReady = !project.lastArtifactUrl.isNullOrBlank()
    val hasOfflineSignedApk = !offlineSigningStatus.signedApkPath.isNullOrBlank()
    val isAnyBuildBusy = isCloudBuildBusy || isOfflineBuildBusy
    val buildStatusText = when {
        isOfflineBuildBusy -> offlineBuildProgress ?: "Running Offline AI Build & PKCS12 Signing…"
        isCloudBuildBusy -> cloudBuildProgress ?: "Running Cloud Gradle Build…"
        hasOfflineSignedApk && buildModeIndex == 0 -> "Offline Signed APK Ready (v1/v2/v3 Verified)"
        hasArtifactReady -> "APK Ready"
        !project.lastBuildStatus.isNullOrBlank() -> project.lastBuildStatus
        else -> "Ready to Build & Sign APK"
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
        if (hasBuildError || isAutoFixingBuild) {
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
                            if (isAutoFixingBuild) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.accentPurple
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Build issue detected",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isAutoFixingBuild) "Auto-fixing issues with AI…" else "Build Issue Detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentCyan
                                )
                                Text(
                                    text = project.lastBuildStatus ?: "Build failure detected. Run self-healing to repair.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                onOpenConsole()
                                onFixBuildWithAi()
                            },
                            enabled = !isAutoFixingBuild && !isAnyBuildBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentCyan,
                                contentColor = Color(0xFF031018)
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("apk_builder_fix_with_ai_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isAutoFixingBuild) "Fixing…" else "Fix with AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Pre-Build Static Linter & APK Metrics Analyzer
        item {
            PreBuildLinterCard(
                project = project,
                onAutoFixProject = onAutoFixProject,
                onNavigateToFile = onNavigateToFile
            )
        }

        item {
            ApkResourceAnalyzerCard(project = project)
        }

        // Real-Time RAM & Storage Monitor Overlay for MediaPipe / Build performance
        item {
            ModelPerformanceMonitorCard(settings = settings)
        }

        // AI Performance Dashboard & Latency Monitor
        item {
            AiPerformanceDashboardCard(
                project = project,
                onOptimizeProject = onOptimizeProject
            )
        }

        // 1. Live Animated Code Factory Card while Offline AI or Cloud CI APK build/sign is active
        if (isAnyBuildBusy) {
            item {
                val activeProgressMsg = if (isOfflineBuildBusy) offlineBuildProgress else cloudBuildProgress
                val telemetry = FactoryTelemetryCalculator.resolveCloudApkBuildTelemetry(activeProgressMsg)
                val offlineCodePreview = remember(
                    offlineBuildStreamPreview,
                    project.appName,
                    signingAlias,
                    offlineSigningStatus.sha256Fingerprint
                ) {
                    offlineBuildStreamPreview.ifBlank {
                        """
                        # MediaPipe LLM Local Build Pipeline (Zero GitHub Actions)
                        signingConfigs.release {
                            storeFile = file("signing-release.p12")
                            storeType = "PKCS12"
                            keyAlias = "$signingAlias"
                        }
                        tasks.register("offlineCompileReleaseApk") { /* --offline */ }
                        tasks.register("offlineSignReleaseApk") { /* apksig v1+v2+v3 */ }
                        """.trimIndent()
                    }
                }
                CyberCodeFactoryCard(
                    projectName = project.appName,
                    stepIndex = telemetry.buildPhase.nodeIndex,
                    totalSteps = 3,
                    stepMessage = telemetry.statusTicker,
                    engineLabel = if (isOfflineBuildBusy) "MediaPipe LLM • Offline Pipeline" else "GitHub Actions • Gradle 8",
                    streamingCode = if (isOfflineBuildBusy) offlineCodePreview else workflowPreview,
                    buildPhase = telemetry.buildPhase,
                    progress = telemetry.progress,
                    onCancel = null
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
                            color = if (hasOfflineSignedApk || hasArtifactReady) {
                                palette.accentEmerald.copy(alpha = 0.16f)
                            } else {
                                palette.cardSurfaceHigh
                            },
                            border = BorderStroke(
                                1.dp,
                                if (hasOfflineSignedApk || hasArtifactReady) palette.accentEmerald else palette.cardBorder
                            )
                        ) {
                            Text(
                                text = when {
                                    hasOfflineSignedApk && buildModeIndex == 0 -> "Signed Offline"
                                    offlineSigningStatus.hasKeystore && buildModeIndex == 0 -> "PKCS12 Ready"
                                    buildModeIndex == 0 -> "Offline AI"
                                    hasArtifactReady -> "APK Ready"
                                    else -> "Cloud CI"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasOfflineSignedApk || hasArtifactReady) palette.accentEmerald else palette.accentCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Build Engine Switcher: Offline AI & Signer vs GitHub Cloud CI
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("build_engine_selector")
                    ) {
                        SegmentedButton(
                            selected = buildModeIndex == 0,
                            onClick = { buildModeIndex = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("build_mode_offline_ai")
                        ) {
                            Text("AI Build & Sign", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = buildModeIndex == 1,
                            onClick = { buildModeIndex = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("build_mode_github_cloud")
                        ) {
                            Text("GitHub Fallback", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                color = if (hasOfflineSignedApk || hasArtifactReady) palette.accentEmerald else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (buildModeIndex == 1 && project.gitHubRepoUrl != null) {
                                OutlinedButton(
                                    onClick = onRefreshCloudBuildStatus,
                                    enabled = !isAnyBuildBusy,
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

                    if (buildModeIndex == 0) {
                        // Offline AI Readiness + Keystore Certificate Summary
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.cardSurfaceHigh.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.fillMaxWidth()
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = palette.accentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = offlineSigningStatus.aiEngineStatus.ifBlank { "Offline AI & Static Analyzer Ready" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { showSigningConfigFields = !showSigningConfigFields },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showSigningConfigFields) "Hide Key" else "Key Config", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                if (offlineSigningStatus.hasKeystore && offlineSigningStatus.sha256Fingerprint.isNotBlank()) {
                                    Text(
                                        text = "PKCS12 Alias: ${offlineSigningStatus.keyAlias} • SHA-256: ${offlineSigningStatus.sha256Fingerprint.take(35)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = palette.accentEmerald,
                                        modifier = Modifier.testTag("offline_keystore_fingerprint_text")
                                    )
                                }

                                AnimatedVisibility(visible = showSigningConfigFields) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = signingAlias,
                                                onValueChange = { signingAlias = it },
                                                label = { Text("Key Alias") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("offline_signing_alias_input")
                                            )
                                            OutlinedTextField(
                                                value = signingPassword,
                                                onValueChange = { signingPassword = it },
                                                label = { Text("Keystore Pass") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("offline_signing_password_input")
                                            )
                                        }
                                        OutlinedTextField(
                                            value = signingCommonName,
                                            onValueChange = { signingCommonName = it },
                                            label = { Text("Certificate Owner (CN)") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("offline_signing_owner_input")
                                        )
                                    }
                                }

                                if (hasOfflineSignedApk && offlineSigningStatus.isSignatureVerified) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOfNotNull(
                                            if (offlineSigningStatus.v1Signed) "v1 JAR" else null,
                                            if (offlineSigningStatus.v2Signed) "v2 APK" else null,
                                            if (offlineSigningStatus.v3Signed) "v3 APK" else null
                                        ).forEach { scheme ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = palette.accentEmerald.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, palette.accentEmerald.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "$scheme ✓",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = palette.accentEmerald,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${offlineSigningStatus.signedApkSizeBytes / 1024} KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Primary Offline AI Build, Repair & Signing CTA
                        Button(
                            onClick = {
                                onOpenConsole()
                                onTriggerOfflineAiBuildAndSign(signingAlias, signingPassword, signingCommonName)
                            },
                            enabled = !isAnyBuildBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accentCyan,
                                contentColor = Color(0xFF031018)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("offline_ai_build_sign_button")
                        ) {
                            if (isOfflineBuildBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF031018)
                                )
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOfflineBuildBusy) {
                                    offlineBuildProgress ?: "Building & Signing with AI…"
                                } else {
                                    "Build, Fix & Sign (AI)"
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    pickApkToSignLauncher.launch(
                                        arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")
                                    )
                                },
                                enabled = !isAnyBuildBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("pick_apk_sign_offline_button")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign .APK File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            if (hasOfflineSignedApk) {
                                Button(
                                    onClick = {
                                        val uri = onGetOfflineSignedApkUri()
                                        if (uri != null) launchApkInstaller(uri)
                                    },
                                    enabled = !isAnyBuildBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accentEmerald,
                                        contentColor = Color(0xFF04120B)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("install_offline_signed_apk_button")
                                ) {
                                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install Signed", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
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
                        }

                        if (hasOfflineSignedApk) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isSavingApkToDownloads = true
                                            val result = onSaveOfflineSignedApkToDownloads()
                                            isSavingApkToDownloads = false
                                            handlePublicDownloadResult(result, true)
                                        }
                                    },
                                    enabled = !isAnyBuildBusy && !isSavingApkToDownloads,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("save_offline_apk_downloads_button")
                                ) {
                                    if (isSavingApkToDownloads) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSavingApkToDownloads) "Saving…" else "Save to Downloads",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val uri = onGetOfflineSignedApkUri()
                                        if (uri != null) launchApkShareSheet(uri)
                                    },
                                    enabled = !isAnyBuildBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("share_offline_apk_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share .APK", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            if (offlineSigningStatus.hasKeystore) {
                                Text(
                                    text = "No compiled .apk binary yet — Save/Share the Project .ZIP (includes signing-release.p12), compile via GitHub Cloud, or tap Sign .APK File.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isSavingZipToDownloads = true
                                            val result = onSaveProjectZipToDownloads()
                                            isSavingZipToDownloads = false
                                            handlePublicDownloadResult(result, false)
                                        }
                                    },
                                    enabled = !isAnyBuildBusy && !isSavingZipToDownloads && !isPackagingZip,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("offline_card_save_zip_button")
                                ) {
                                    if (isSavingZipToDownloads) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSavingZipToDownloads) "Saving…" else "Save .ZIP",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                                runCatching {
                                                    context.startActivity(
                                                        Intent.createChooser(shareIntent, "Share ${project.appName}.zip")
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isAnyBuildBusy && !isPackagingZip && !isSavingZipToDownloads,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("offline_card_share_zip_button")
                                ) {
                                    if (isPackagingZip) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share .ZIP", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        if (!offlineSigningStatus.aiEngineStatus.startsWith("Offline AI Ready") &&
                            !offlineSigningStatus.aiEngineStatus.startsWith("Cloud AI Ready")
                        ) {
                            TextButton(onClick = onOpenSettings) {
                                Text("Configure Local AI or Cloud AI in Settings")
                            }
                        }
                    } else {
                        // Prominent "Build & Install APK" CTA (GitHub Actions Cloud CI)
                        Button(
                            onClick = {
                                if (hasArtifactReady) {
                                    scope.launch {
                                        onOpenConsole()
                                        val apkUri = onDownloadAndInstallApk()
                                        if (apkUri != null) {
                                            launchApkInstaller(apkUri)
                                        } else {
                                            uriHandler.openUri(project.lastArtifactUrl!!)
                                        }
                                    }
                                } else {
                                    onOpenConsole()
                                    onTriggerCloudBuild()
                                }
                            },
                            enabled = !isAnyBuildBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasArtifactReady) palette.accentEmerald else MaterialTheme.colorScheme.primary,
                                contentColor = if (hasArtifactReady) Color(0xFF04120B) else MaterialTheme.colorScheme.onPrimary
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
                                    isCloudBuildBusy -> cloudBuildProgress ?: "Building APK…"
                                    hasArtifactReady -> "Download & Install APK"
                                    else -> "Build & Install APK"
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (hasArtifactReady) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            isSavingApkToDownloads = true
                                            val result = onSaveCloudApkToDownloads()
                                            isSavingApkToDownloads = false
                                            handlePublicDownloadResult(result, true)
                                        }
                                    },
                                    enabled = !isAnyBuildBusy && !isSavingApkToDownloads,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("save_cloud_apk_downloads_button")
                                ) {
                                    if (isSavingApkToDownloads) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSavingApkToDownloads) "Saving…" else "Save to Downloads",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            isSharingCloudApk = true
                                            val shareUri = onGetCloudApkShareUri()
                                            isSharingCloudApk = false
                                            if (shareUri != null) {
                                                launchApkShareSheet(shareUri)
                                            }
                                        }
                                    },
                                    enabled = !isAnyBuildBusy && !isSharingCloudApk,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("share_cloud_apk_button")
                                ) {
                                    if (isSharingCloudApk) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSharingCloudApk) "Preparing…" else "Share .APK",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
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
                                    enabled = !isAnyBuildBusy,
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

                        if (!hasGitHubPat) {
                            TextButton(onClick = onOpenSettings) {
                                Text("GitHub is optional fallback — add PAT in Settings or use AI Build & Sign")
                            }
                        }
                    }
                }
            }
        }

        if (buildModeIndex == 0 && offlineSigningStatus.pipelineTasks.isNotEmpty()) {
            item {
                CyberGradientCard(
                    cornerRadius = 18.dp,
                    borderWidth = 1.4.dp,
                    containerColor = palette.cardSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_pipeline_tasks_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Compilation & Signing Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buildString {
                                    append(
                                        offlineSigningStatus.pipelineOriginLabel
                                            .ifBlank { "Offline Gradle + PKCS12 Pipeline" }
                                    )
                                    if (offlineSigningStatus.pipelineLatencyMs > 0L) {
                                        append(" • ${offlineSigningStatus.pipelineLatencyMs} ms")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accentCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.accentEmerald.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, palette.accentEmerald.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "0 GitHub",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accentEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            items(offlineSigningStatus.pipelineTasks.size) { index ->
                val task = offlineSigningStatus.pipelineTasks[index]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.cardSurfaceHigh,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_task_${task.name}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ":${task.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (task.generatedByMediaPipe) {
                                    "${task.category} • AI"
                                } else {
                                    task.category
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (task.generatedByMediaPipe) palette.accentCyan else palette.accentEmerald
                            )
                        }
                        Text(
                            text = task.command,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = palette.accentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPipelineTasksCode = !showPipelineTasksCode },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("toggle_offline_tasks_preview_button")
                        ) {
                            Icon(
                                imageVector = if (showPipelineTasksCode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showPipelineTasksCode) "Hide Tasks Script" else "Inspect Gradle Tasks",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                onNavigateToFile(LocalMediaPipeBuildPipeline.GRADLE_PIPELINE_FILE_PATH, 1)
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("open_offline_gradle_tasks_file_button")
                        ) {
                            Text("Open File")
                        }
                    }

                    AnimatedVisibility(
                        visible = showPipelineTasksCode && offlineSigningStatus.pipelineTasksScript.isNotBlank()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.cardSurface,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_pipeline_script_preview")
                        ) {
                            Text(
                                text = offlineSigningStatus.pipelineTasksScript,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
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

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isSavingZipToDownloads = true
                                val result = onSaveProjectZipToDownloads()
                                isSavingZipToDownloads = false
                                handlePublicDownloadResult(result, false)
                            }
                        },
                        enabled = !isSavingZipToDownloads && !isPackagingZip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_zip_downloads_button")
                    ) {
                        if (isSavingZipToDownloads) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSavingZipToDownloads) "Saving ZIP…" else "Save .ZIP to Downloads")
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
                        enabled = !isPackagingZip && !isSavingZipToDownloads,
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
