package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gradle.GradleDependencyManager
import com.example.data.local.CustomSqlTableEntity
import com.example.data.local.ProjectSqlEntity
import com.example.data.local.SqlColumnSpec
import com.example.diagnostics.ProjectStaticAnalyzer
import com.example.model.AutoSaveStatus
import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.model.EditorThemeMode
import com.example.model.GeneratedFile
import com.example.model.GitRepoState
import com.example.model.OfflineSigningStatus
import com.example.model.ProjectSnapshot
import com.example.model.RefactorUiState
import com.example.model.StudioProject
import com.example.model.StudioSettings
import com.example.preview.ComposePreviewEngine
import com.example.preview.LivePreviewDeviceFrame
import com.example.preview.Material3SeedPaletteGenerator
import com.example.ui.assets.ProjectResAssetManager
import com.example.ui.components.AiRefactorBottomSheet
import com.example.ui.components.AssetManagerTab
import com.example.ui.components.CyberGradientCard
import com.example.ui.components.DependencyManagerBottomSheet
import com.example.ui.components.DockedOutputConsoleBar
import com.example.ui.components.SqlDatabaseBottomSheet
import com.example.ui.components.ThemeColorPickerDialog
import com.example.ui.components.VersionHistoryBottomSheet
import com.example.ui.components.WorkspaceFileExplorerSidebar
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette

private enum class WorkspaceBottomTab(val title: String) {
    PREVIEW("Preview"),
    CODE("Code"),
    BUILD_APK("Build APK")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    project: StudioProject?,
    settings: StudioSettings,
    gitState: GitRepoState,
    autoSaveStatus: AutoSaveStatus = AutoSaveStatus.IDLE,
    consoleLogs: List<ConsoleLogEntry> = emptyList(),
    refactorState: RefactorUiState = RefactorUiState.Idle,
    sqlProjects: List<ProjectSqlEntity> = emptyList(),
    customSqlTables: List<CustomSqlTableEntity> = emptyList(),
    snapshots: List<ProjectSnapshot> = emptyList(),
    isGitBusy: Boolean,
    isCloudBuildBusy: Boolean,
    cloudBuildProgress: String?,
    isOfflineBuildBusy: Boolean = false,
    offlineBuildProgress: String? = null,
    offlineBuildStreamPreview: String = "",
    offlineSigningStatus: OfflineSigningStatus = OfflineSigningStatus(),
    isAutoFixingBuild: Boolean = false,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveFile: (path: String, content: String) -> Unit,
    onLivePreviewEdit: (path: String, content: String) -> Unit,
    onFlushAutoSave: () -> Unit = {},
    onCreateCheckpoint: (summary: String, label: String?) -> Unit = { _, _ -> },
    onRestoreSnapshot: (ProjectSnapshot) -> Unit = {},
    onUpdateBranding: (
        appName: String,
        packageId: String,
        versionName: String,
        launcherIconBase64: String?,
        launcherIconVectorXml: String?
    ) -> Unit = { _, _, _, _, _ -> },
    onAddNewFile: (path: String, content: String) -> Unit,
    onApplyThemeSeedColor: (
        seedColor: Color,
        onUpdated: (targetPath: String, newContent: String) -> Unit
    ) -> Unit,
    onApplyDependencyChanges: (
        enabledGroupIds: Set<String>,
        enabledCustomDeclarations: Set<String>,
        onGradleUpdated: (targetPath: String, newContent: String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onInjectRoomSqlTable: (
        entityName: String,
        tableName: String,
        columns: List<SqlColumnSpec>,
        onInjected: (generatedFilePath: String, generatedCode: String) -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onDeleteCustomSqlTable: (tableId: Int) -> Unit = {},
    onAnalyzeFileForRefactor: (filePath: String, currentCode: String) -> Unit = { _, _ -> },
    onApplyRefactoredCode: (
        filePath: String,
        refactoredCode: String,
        onApplied: (targetPath: String, updatedContent: String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onDismissRefactorSheet: () -> Unit = {},
    onRunCompilerCheck: () -> Unit = {},
    onClearConsoleLogs: () -> Unit = {},
    onAutoFixProject: () -> Unit = {},
    onOptimizeProject: () -> Unit = {},
    onFixBuildWithAi: () -> Unit = {},
    onDownloadAndInstallApk: suspend () -> android.net.Uri? = { null },
    onTriggerOfflineAiBuildAndSign: (keyAlias: String, storePassword: String, commonName: String) -> Unit = { _, _, _ -> },
    onSignExternalApkOffline: suspend (apkUri: Uri, keyAlias: String, storePassword: String, commonName: String) -> Uri? = { _, _, _, _ -> null },
    onGetOfflineSignedApkUri: () -> Uri? = { null },
    onPackageZip: suspend () -> android.net.Uri?,
    onExportBuildLogs: suspend () -> android.net.Uri?,
    onInitGit: () -> Unit,
    onRefreshGit: () -> Unit,
    onStageFile: (String) -> Unit,
    onUnstageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommitGit: (String) -> Unit,
    onConfigureRemote: (String) -> Unit,
    onPushGit: () -> Unit,
    onPullGit: () -> Unit,
    onTriggerCloudBuild: () -> Unit,
    onRefreshCloudBuild: () -> Unit,
    onRenameResAsset: (
        oldPath: String,
        newBaseName: String,
        updateReferences: Boolean,
        onRenamed: (String) -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onDeleteResAsset: (path: String) -> Unit = {},
    onImportResImageUri: (
        uri: Uri,
        targetResFolder: String,
        onImported: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onImportResFontUri: (
        uri: Uri,
        onImported: (String) -> Unit
    ) -> Unit = { _, _ -> },
    onAddResTextAsset: (
        path: String,
        content: String,
        onAdded: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onShowToast: (String) -> Unit
) {
    val palette = LocalStudioCyberPalette.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(WorkspaceBottomTab.PREVIEW.ordinal) }
    var isConsoleExpanded by rememberSaveable { mutableStateOf(false) }
    var showAssetsSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var requestedFileToOpen by rememberSaveable { mutableStateOf<String?>(null) }

    val tabs = WorkspaceBottomTab.entries
    val saveableStateHolder = rememberSaveableStateHolder()
    val previewStateStore = remember(project?.id) { mutableStateMapOf<String, Any>() }
    val previewNavStack = remember(project?.id) { mutableStateListOf<String>() }

    if (showHistorySheet) {
        VersionHistoryBottomSheet(
            snapshots = snapshots,
            onCreateCheckpoint = onCreateCheckpoint,
            onRestoreSnapshot = { snapshot ->
                onRestoreSnapshot(snapshot)
                showHistorySheet = false
                onShowToast("Restored project to ${snapshot.versionLabel}")
            },
            onDismiss = { showHistorySheet = false }
        )
    }

    if (refactorState !is RefactorUiState.Idle) {
        AiRefactorBottomSheet(
            state = refactorState,
            onApplyRefactoredCode = { targetPath, newCode ->
                onApplyRefactoredCode(targetPath, newCode) { appliedPath, _ ->
                    requestedFileToOpen = appliedPath
                }
            },
            onDismiss = onDismissRefactorSheet,
            onOpenSettings = {
                onDismissRefactorSheet()
                onOpenSettings()
            }
        )
    }

    if (showAssetsSheet && project != null) {
        ModalBottomSheet(
            onDismissRequest = { showAssetsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.cardSurface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(540.dp)
                    .navigationBarsPadding()
            ) {
                AssetManagerTab(
                    project = project,
                    onRenameAsset = onRenameResAsset,
                    onDeleteAsset = onDeleteResAsset,
                    onImportImageUri = onImportResImageUri,
                    onImportFontUri = onImportResFontUri,
                    onAddVectorAsset = onAddResTextAsset,
                    onOpenXmlInEditor = { xmlPath ->
                        requestedFileToOpen = xmlPath
                        selectedTabIndex = WorkspaceBottomTab.CODE.ordinal
                        showAssetsSheet = false
                    },
                    onShowToast = onShowToast
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("workspace_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Generator"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = project?.appName ?: "Loading Project…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (project != null) {
                            Text(
                                text = project.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accentCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = palette.cardSurfaceHigh,
                        border = BorderStroke(1.dp, palette.accentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { showHistorySheet = true }
                            .testTag("open_history_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Version History",
                                tint = palette.accentCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val currentVer = snapshots.firstOrNull()?.versionLabel ?: "v1.0"
                            Text(
                                text = "History ($currentVer) ⌄",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = { isConsoleExpanded = !isConsoleExpanded },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("topbar_console_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Toggle Output Console",
                            tint = if (isConsoleExpanded) palette.accentCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.cardSurface
                )
            )
        },
        bottomBar = {
            if (project != null) {
                NavigationBar(
                    containerColor = palette.cardSurface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier
                        .border(1.dp, palette.cardBorder)
                        .testTag("workspace_bottom_nav_bar")
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTabIndex == index
                        val icon = when (tab) {
                            WorkspaceBottomTab.PREVIEW -> Icons.Default.PhoneAndroid
                            WorkspaceBottomTab.CODE -> Icons.Default.Code
                            WorkspaceBottomTab.BUILD_APK -> Icons.Default.BuildCircle
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (selectedTabIndex != index) {
                                    onFlushAutoSave()
                                    selectedTabIndex = index
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.accentCyan,
                                selectedTextColor = palette.accentCyan,
                                indicatorColor = palette.accentCyan.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("workspace_bottom_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (project == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val activeTab = tabs[selectedTabIndex]
                saveableStateHolder.SaveableStateProvider(activeTab.name) {
                    when (activeTab) {
                        WorkspaceBottomTab.PREVIEW -> {
                            WorkspaceLivePreviewTab(
                                project = project,
                                autoSaveStatus = autoSaveStatus,
                                previewStateStore = previewStateStore,
                                previewNavStack = previewNavStack,
                                onLivePreviewEdit = onLivePreviewEdit,
                                onFlushAutoSave = onFlushAutoSave,
                                onApplyThemeSeedColor = onApplyThemeSeedColor,
                                onAnalyzeFileForRefactor = onAnalyzeFileForRefactor,
                                onShowToast = onShowToast
                            )
                        }
                        WorkspaceBottomTab.CODE -> {
                            WorkspaceCodeTab(
                                project = project,
                                settings = settings,
                                autoSaveStatus = autoSaveStatus,
                                sqlProjects = sqlProjects,
                                customSqlTables = customSqlTables,
                                persistedLogCount = consoleLogs.size,
                                requestedFileToOpen = requestedFileToOpen,
                                onConsumedRequestedFile = { requestedFileToOpen = null },
                                onSaveFile = onSaveFile,
                                onLivePreviewEdit = onLivePreviewEdit,
                                onFlushAutoSave = onFlushAutoSave,
                                onAddNewFile = onAddNewFile,
                                onApplyThemeSeedColor = onApplyThemeSeedColor,
                                onApplyDependencyChanges = onApplyDependencyChanges,
                                onInjectRoomSqlTable = onInjectRoomSqlTable,
                                onDeleteCustomSqlTable = onDeleteCustomSqlTable,
                                onAnalyzeFileForRefactor = onAnalyzeFileForRefactor,
                                onOpenAssetsModal = { showAssetsSheet = true },
                                onShowToast = onShowToast
                            )
                        }
                        WorkspaceBottomTab.BUILD_APK -> {
                            WorkspaceBuildApkTab(
                                project = project,
                                settings = settings,
                                gitState = gitState,
                                hasGitHubPat = settings.gitHubPat.isNotBlank(),
                                isGitBusy = isGitBusy,
                                isCloudBuildBusy = isCloudBuildBusy,
                                cloudBuildProgress = cloudBuildProgress,
                                isOfflineBuildBusy = isOfflineBuildBusy,
                                offlineBuildProgress = offlineBuildProgress,
                                offlineBuildStreamPreview = offlineBuildStreamPreview,
                                offlineSigningStatus = offlineSigningStatus,
                                isAutoFixingBuild = isAutoFixingBuild,
                                onTriggerCloudBuild = onTriggerCloudBuild,
                                onRefreshCloudBuildStatus = onRefreshCloudBuild,
                                onDownloadAndInstallApk = onDownloadAndInstallApk,
                                onTriggerOfflineAiBuildAndSign = onTriggerOfflineAiBuildAndSign,
                                onSignExternalApkOffline = onSignExternalApkOffline,
                                onGetOfflineSignedApkUri = onGetOfflineSignedApkUri,
                                onPackageZip = onPackageZip,
                                onExportBuildLogs = onExportBuildLogs,
                                onAutoFixProject = onAutoFixProject,
                                onOptimizeProject = onOptimizeProject,
                                onFixBuildWithAi = onFixBuildWithAi,
                                onNavigateToFile = { filePath, _ ->
                                    if (project.files.any { it.path == filePath }) {
                                        requestedFileToOpen = filePath
                                        selectedTabIndex = WorkspaceBottomTab.CODE.ordinal
                                    }
                                },
                                onSaveBranding = onUpdateBranding,
                                onInitGit = onInitGit,
                                onRefreshGit = onRefreshGit,
                                onStageFile = onStageFile,
                                onUnstageFile = onUnstageFile,
                                onStageAll = onStageAll,
                                onCommitGit = onCommitGit,
                                onConfigureRemote = onConfigureRemote,
                                onPushGit = onPushGit,
                                onPullGit = onPullGit,
                                onOpenConsole = { isConsoleExpanded = true },
                                onOpenSettings = onOpenSettings
                            )
                        }
                    }
                }
            }

            // Docked Output Console Panel
            DockedOutputConsoleBar(
                logs = consoleLogs,
                isExpanded = isConsoleExpanded,
                onToggleExpand = { isConsoleExpanded = !isConsoleExpanded },
                onRunCompilerCheck = {
                    isConsoleExpanded = true
                    onRunCompilerCheck()
                },
                onClearLogs = onClearConsoleLogs,
                onSelectFileFromLog = { filePath ->
                    if (project.files.any { it.path == filePath }) {
                        requestedFileToOpen = filePath
                        selectedTabIndex = WorkspaceBottomTab.CODE.ordinal
                    }
                },
                onShowToast = onShowToast
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceCodeTab(
    project: StudioProject,
    settings: StudioSettings,
    autoSaveStatus: AutoSaveStatus,
    sqlProjects: List<ProjectSqlEntity>,
    customSqlTables: List<CustomSqlTableEntity>,
    persistedLogCount: Int,
    requestedFileToOpen: String? = null,
    onConsumedRequestedFile: () -> Unit = {},
    onSaveFile: (path: String, content: String) -> Unit,
    onLivePreviewEdit: (path: String, content: String) -> Unit,
    onFlushAutoSave: () -> Unit,
    onAddNewFile: (path: String, content: String) -> Unit,
    onApplyThemeSeedColor: (
        seedColor: Color,
        onUpdated: (targetPath: String, newContent: String) -> Unit
    ) -> Unit,
    onApplyDependencyChanges: (
        enabledGroupIds: Set<String>,
        enabledCustomDeclarations: Set<String>,
        onGradleUpdated: (targetPath: String, newContent: String) -> Unit
    ) -> Unit,
    onInjectRoomSqlTable: (
        entityName: String,
        tableName: String,
        columns: List<SqlColumnSpec>,
        onInjected: (generatedFilePath: String, generatedCode: String) -> Unit
    ) -> Unit,
    onDeleteCustomSqlTable: (tableId: Int) -> Unit,
    onAnalyzeFileForRefactor: (filePath: String, currentCode: String) -> Unit,
    onOpenAssetsModal: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val palette = LocalStudioCyberPalette.current
    val clipboardManager = LocalClipboardManager.current

    val defaultFile = remember(project.id) {
        project.files.firstOrNull { it.path.endsWith("MainActivity.kt") }
            ?: project.files.firstOrNull { it.path.endsWith(".kt") }
            ?: project.files.firstOrNull()
    }

    var selectedPath by rememberSaveable(project.id) {
        mutableStateOf(defaultFile?.path ?: "")
    }
    var editorText by remember(project.id, selectedPath) {
        mutableStateOf(project.files.firstOrNull { it.path == selectedPath }?.content ?: "")
    }
    var hasUnsavedChanges by remember(project.id, selectedPath) {
        mutableStateOf(false)
    }
    var fileDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(requestedFileToOpen) {
        val target = requestedFileToOpen ?: return@LaunchedEffect
        val matched = project.files.firstOrNull { it.path == target }
        if (matched != null) {
            selectedPath = matched.path
            editorText = matched.content
            hasUnsavedChanges = false
        }
        onConsumedRequestedFile()
    }

    LaunchedEffect(autoSaveStatus) {
        if (autoSaveStatus == AutoSaveStatus.SAVED) {
            hasUnsavedChanges = false
        }
    }

    LaunchedEffect(project.updatedAtEpochMs, selectedPath) {
        val currentDisk = project.files.firstOrNull { it.path == selectedPath }?.content
        if (currentDisk != null && !hasUnsavedChanges) {
            editorText = currentDisk
        }
    }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showDependencyManager by remember { mutableStateOf(false) }
    var showSqlDatabaseSheet by remember { mutableStateOf(false) }
    var isSidebarExpanded by rememberSaveable(project.id) { mutableStateOf(false) }

    if (showSqlDatabaseSheet) {
        SqlDatabaseBottomSheet(
            project = project,
            customTables = customSqlTables,
            allSqlProjects = sqlProjects,
            persistedLogCount = persistedLogCount,
            onInjectRoomTable = { entityName, tableName, cols, onDone ->
                onInjectRoomSqlTable(entityName, tableName, cols) { genPath, genCode ->
                    selectedPath = genPath
                    editorText = genCode
                    hasUnsavedChanges = false
                    onDone(genPath, genCode)
                }
            },
            onOpenGeneratedFile = { genPath ->
                val matched = project.files.firstOrNull { it.path == genPath }
                if (matched != null) {
                    selectedPath = matched.path
                    editorText = matched.content
                    hasUnsavedChanges = false
                }
            },
            onDeleteCustomTable = onDeleteCustomSqlTable,
            onDismiss = { showSqlDatabaseSheet = false }
        )
    }

    if (showDependencyManager) {
        val depState = remember(project.files, selectedPath, editorText) {
            val targetPath = GradleDependencyManager.resolveTargetGradlePath(project)
            val currentGradleContent = if (selectedPath == targetPath) editorText
            else project.files.firstOrNull { it.path == targetPath }?.content.orEmpty()
            GradleDependencyManager.inspectGradleContent(targetPath, currentGradleContent)
        }
        DependencyManagerBottomSheet(
            state = depState,
            onApplyChanges = { enabledGroupIds, enabledCustomDecls ->
                onApplyDependencyChanges(enabledGroupIds, enabledCustomDecls) { updatedGradlePath, newGradleContent ->
                    selectedPath = updatedGradlePath
                    editorText = newGradleContent
                    hasUnsavedChanges = false
                }
                showDependencyManager = false
            },
            onDismiss = { showDependencyManager = false }
        )
    }

    if (showThemePickerDialog) {
        val targetThemePath = remember(project.files, project.packageName) {
            Material3SeedPaletteGenerator.resolveTargetThemePath(project)
        }
        ThemeColorPickerDialog(
            targetThemePath = targetThemePath,
            onApplySeedColor = { chosenSeed ->
                onApplyThemeSeedColor(chosenSeed) { updatedPath, newContent ->
                    selectedPath = updatedPath
                    editorText = newContent
                    hasUnsavedChanges = false
                }
                showThemePickerDialog = false
            },
            onDismiss = { showThemePickerDialog = false }
        )
    }

    if (showNewFileDialog) {
        NewFileDialog(
            packageName = project.packageName,
            onConfirm = { newPath, template ->
                onAddNewFile(newPath, template)
                selectedPath = newPath
                editorText = template
                hasUnsavedChanges = false
                showNewFileDialog = false
            },
            onDismiss = { showNewFileDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Primary Code Header: File Dropdown Selector + Copy + Save
        Surface(
            color = palette.cardSurface,
            border = BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Dropdown Selector
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { fileDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.cardSurfaceHigh,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("code_file_dropdown_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedPath.substringAfterLast('/').ifBlank { "Select file" } +
                                        if (hasUnsavedChanges) " •" else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = autoSaveStatus.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accentCyan,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Select project file",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = fileDropdownExpanded,
                        onDismissRequest = { fileDropdownExpanded = false }
                    ) {
                        project.files.forEach { file ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = file.path.substringAfterLast('/'),
                                            fontWeight = if (file.path == selectedPath) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = file.path,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    fileDropdownExpanded = false
                                    if (hasUnsavedChanges) onFlushAutoSave()
                                    selectedPath = file.path
                                    editorText = file.content
                                    hasUnsavedChanges = false
                                }
                            )
                        }
                    }
                }

                // Copy Button
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(editorText))
                        onShowToast("Copied ${selectedPath.substringAfterLast('/')}")
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("copy_code_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy file", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }

                // Save Button
                Button(
                    onClick = {
                        onSaveFile(selectedPath, editorText)
                        hasUnsavedChanges = false
                    },
                    enabled = selectedPath.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_code_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save file", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Secondary Tools Strip (Explorer Tree, Assets, Dependencies, SQL, AI Refactor, M3 Theme, New File)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = isSidebarExpanded,
                    onClick = { isSidebarExpanded = !isSidebarExpanded },
                    label = { Text(if (isSidebarExpanded) "Hide Tree" else "File Tree") },
                    leadingIcon = {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("toggle_file_explorer_button")
                )
            }
            item {
                AssistChip(
                    onClick = onOpenAssetsModal,
                    label = { Text("Assets") },
                    leadingIcon = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("open_assets_manager_chip")
                )
            }
            item {
                AssistChip(
                    onClick = { showDependencyManager = true },
                    label = { Text("Dependencies") },
                    leadingIcon = {
                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("dependency_manager_button")
                )
            }
            item {
                AssistChip(
                    onClick = { showSqlDatabaseSheet = true },
                    label = { Text("SQL") },
                    leadingIcon = {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("sql_database_button")
                )
            }
            if (selectedPath.endsWith(".kt", ignoreCase = true)) {
                item {
                    AssistChip(
                        onClick = { onAnalyzeFileForRefactor(selectedPath, editorText) },
                        label = { Text("AI Refactor") },
                        leadingIcon = {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("ai_refactor_button")
                    )
                }
            }
            item {
                AssistChip(
                    onClick = { showThemePickerDialog = true },
                    label = { Text("M3 Theme") },
                    leadingIcon = {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("theme_picker_button")
                )
            }
            item {
                AssistChip(
                    onClick = { showNewFileDialog = true },
                    label = { Text("New File") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        // Collapsible Tree Sidebar + Line-Numbered Code Editor
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isCompactScreen = maxWidth < 600.dp
            val targetSidebarWidth = if (isSidebarExpanded) {
                if (isCompactScreen) 186.dp else 252.dp
            } else {
                0.dp
            }
            val animatedSidebarWidth by animateDpAsState(
                targetValue = targetSidebarWidth,
                label = "workspace_file_explorer_width"
            )

            Row(modifier = Modifier.fillMaxSize()) {
                if (animatedSidebarWidth > 1.dp) {
                    WorkspaceFileExplorerSidebar(
                        files = project.files,
                        selectedPath = selectedPath,
                        onSelectFile = { file ->
                            if (hasUnsavedChanges) onFlushAutoSave()
                            selectedPath = file.path
                            editorText = file.content
                            hasUnsavedChanges = false
                        },
                        onNewFileClick = { showNewFileDialog = true },
                        onCollapseSidebar = { isSidebarExpanded = false },
                        modifier = Modifier.width(animatedSidebarWidth)
                    )
                    VerticalDivider(color = palette.cardBorder)
                }

                val isBinaryAsset = remember(selectedPath, editorText) {
                    ProjectResAssetManager.isBinaryAssetPath(selectedPath) ||
                        ProjectResAssetManager.isBinaryEncodedContent(editorText)
                }

                if (isBinaryAsset) {
                    Surface(
                        color = palette.cardSurfaceHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = palette.accentCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "Binary Resource Asset", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Open Asset Manager to inspect, preview, or rename images and fonts in res/.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onOpenAssetsModal) {
                                Text("Open Asset Manager")
                            }
                        }
                    }
                } else {
                    LineNumberedCodeEditor(
                        code = editorText,
                        onCodeChange = { updated ->
                            editorText = updated
                            hasUnsavedChanges = true
                            onLivePreviewEdit(selectedPath, updated)
                        },
                        editorThemeMode = settings.editorThemeMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun LineNumberedCodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    editorThemeMode: EditorThemeMode = EditorThemeMode.CYBER_NIGHT,
    modifier: Modifier = Modifier
) {
    val isCyberNight = editorThemeMode == EditorThemeMode.CYBER_NIGHT
    val editorBg = if (isCyberNight) Color(0xFF0A0E1A) else Color(0xFFF8FAFC)
    val gutterBg = if (isCyberNight) Color(0xFF13192E) else Color(0xFFE2E8F0)
    val textColor = if (isCyberNight) Color(0xFF00F0FF) else Color(0xFF0F172A)
    val borderColor = if (isCyberNight) Color(0xFF1C2440) else Color(0xFFCBD5E1)
    val lineNumberColor = if (isCyberNight) Color(0xFF94A3B8) else Color(0xFF64748B)

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    Row(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(editorBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        // Line numbers gutter
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(gutterBg.copy(alpha = 0.85f))
                .verticalScroll(verticalScrollState)
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Text(
                text = lineNumbersText,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = lineNumberColor,
                    textAlign = TextAlign.End
                )
            )
        }

        VerticalDivider(color = borderColor)

        // Editable monospace code field
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = textColor
                ),
                cursorBrush = SolidColor(textColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minWidth = 340.dp, minHeight = 420.dp)
                    .testTag("code_editor_textfield")
            )
        }
    }
}

@Composable
private fun NewFileDialog(
    packageName: String,
    onConfirm: (path: String, template: String) -> Unit,
    onDismiss: () -> Unit
) {
    val packagePath = packageName.replace('.', '/')
    var fileName by remember { mutableStateOf("DetailScreen.kt") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Project File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Will be created under app/src/main/java/$packagePath/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = fileName.trim().ifBlank { "CustomScreen.kt" }
                    val fullPath = if (clean.contains('/')) clean else "app/src/main/java/$packagePath/$clean"
                    val composableName = clean.substringAfterLast('/').substringBefore('.').ifBlank { "CustomScreen" }
                    val template = """
                        package $packageName

                        import androidx.compose.foundation.layout.*
                        import androidx.compose.material3.*
                        import androidx.compose.runtime.*
                        import androidx.compose.ui.Modifier
                        import androidx.compose.ui.unit.dp

                        @Composable
                        fun $composableName() {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "$composableName",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Edit this Composable and watch Live Preview update!",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    """.trimIndent()
                    onConfirm(fullPath, template)
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceLivePreviewTab(
    project: StudioProject,
    autoSaveStatus: AutoSaveStatus,
    previewStateStore: SnapshotStateMap<String, Any>,
    previewNavStack: SnapshotStateList<String>,
    onLivePreviewEdit: (path: String, content: String) -> Unit,
    onFlushAutoSave: () -> Unit,
    onApplyThemeSeedColor: (
        seedColor: Color,
        onUpdated: (targetPath: String, newContent: String) -> Unit
    ) -> Unit,
    onAnalyzeFileForRefactor: (filePath: String, currentCode: String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val palette = LocalStudioCyberPalette.current
    var darkPreview by rememberSaveable(project.id) { mutableStateOf(true) }
    var isCompactPhone by rememberSaveable(project.id) { mutableStateOf(true) }
    var isLandscape by rememberSaveable(project.id) { mutableStateOf(false) }
    var previewFontScale by rememberSaveable(project.id) { mutableFloatStateOf(1.0f) }
    var selectedComposableName by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var showQuickEditor by rememberSaveable(project.id) { mutableStateOf(false) }
    var refreshNonce by rememberSaveable(project.id) { mutableIntStateOf(0) }
    var showThemePickerDialog by remember { mutableStateOf(false) }

    val liveFilesList = remember(project.files) {
        project.files.map { it.path to it.content }
    }

    val customColorSchemes = remember(liveFilesList) {
        Material3SeedPaletteGenerator.extractCustomSchemesFromFiles(liveFilesList)
    }

    val parseResult = remember(liveFilesList, selectedComposableName, refreshNonce) {
        ComposePreviewEngine.parseProjectSources(
            files = liveFilesList,
            preferredComposableName = selectedComposableName
        )
    }

    val activeComposable = parseResult.selectedComposable
    val activeFilePath = activeComposable?.filePath
        ?: project.files.firstOrNull { it.path.endsWith("MainActivity.kt") }?.path
        ?: project.files.firstOrNull { it.path.endsWith(".kt") }?.path
        ?: ""

    var quickEditSource by remember(project.id, activeFilePath, project.updatedAtEpochMs) {
        mutableStateOf(project.files.firstOrNull { it.path == activeFilePath }?.content ?: "")
    }

    val currentActiveSource = if (showQuickEditor && activeFilePath.isNotBlank()) {
        quickEditSource
    } else {
        project.files.firstOrNull { it.path == activeFilePath }?.content.orEmpty()
    }

    val syntaxErrorMessage = remember(activeFilePath, currentActiveSource, parseResult.warningMessage, activeComposable) {
        if (activeFilePath.isBlank()) {
            null
        } else {
            val fileErrors = ProjectStaticAnalyzer
                .analyzeSingleFile(GeneratedFile(activeFilePath, currentActiveSource))
                .filter { it.level == ConsoleLogLevel.ERROR }
            when {
                fileErrors.isNotEmpty() -> fileErrors.joinToString("\n") { it.message }
                activeComposable == null && !parseResult.warningMessage.isNullOrBlank() -> parseResult.warningMessage
                else -> null
            }
        }
    }

    if (showThemePickerDialog) {
        val targetThemePath = remember(project.files, project.packageName) {
            Material3SeedPaletteGenerator.resolveTargetThemePath(project)
        }
        ThemeColorPickerDialog(
            targetThemePath = targetThemePath,
            onApplySeedColor = { chosenSeed ->
                onApplyThemeSeedColor(chosenSeed) { _, _ -> }
                showThemePickerDialog = false
            },
            onDismiss = { showThemePickerDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Auto-Fix Error Loop ("Smart Self-Healing") Banner for Live Preview
        if (syntaxErrorMessage != null) {
            CyberGradientCard(
                cornerRadius = 14.dp,
                borderWidth = 1.4.dp,
                containerColor = palette.cardSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preview_auto_fix_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Syntax issue detected",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Syntax Issue Detected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentCyan
                            )
                            Text(
                                text = syntaxErrorMessage.lines().firstOrNull() ?: "Syntax or preview compilation error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (activeFilePath.isNotBlank()) {
                                onAnalyzeFileForRefactor(activeFilePath, currentActiveSource)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentCyan,
                            contentColor = Color(0xFF031018)
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("preview_fix_with_ai_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fix with AI", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Primary Live Preview Toolbar: Refresh, Dark/Light Switch, Compact vs Large Phone toggle
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onFlushAutoSave()
                        refreshNonce++
                        onShowToast("Live Preview reloaded")
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("live_preview_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Live Preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelMedium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (darkPreview) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(text = if (darkPreview) "Dark" else "Light", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = darkPreview,
                        onCheckedChange = { darkPreview = it },
                        modifier = Modifier.testTag("live_preview_tab_dark_toggle")
                    )
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.testTag("live_preview_size_toggle")
                ) {
                    SegmentedButton(
                        selected = isCompactPhone,
                        onClick = { isCompactPhone = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("live_preview_size_compact")
                    ) {
                        Text("Compact", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = !isCompactPhone,
                        onClick = { isCompactPhone = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("live_preview_size_large")
                    ) {
                        Text("Large", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Secondary Row: M3 Theme, Quick Edit, AI Fix, Auto-save badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = isLandscape,
                onClick = { isLandscape = !isLandscape },
                label = { Text(if (isLandscape) "Landscape" else "Portrait") },
                leadingIcon = {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate preview device", modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("live_preview_rotate_toggle")
            )

            AssistChip(
                onClick = {
                    previewFontScale = when (previewFontScale) {
                        1.0f -> 1.15f
                        1.15f -> 0.85f
                        else -> 1.0f
                    }
                },
                label = { Text("${previewFontScale}x") },
                leadingIcon = {
                    Icon(Icons.Default.FormatSize, contentDescription = "Cycle preview font scale", modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("live_preview_font_scale_button")
            )

            AssistChip(
                onClick = { showThemePickerDialog = true },
                label = { Text("M3 Theme") },
                leadingIcon = {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("live_preview_tab_theme_button")
            )

            FilterChip(
                selected = showQuickEditor,
                onClick = { showQuickEditor = !showQuickEditor },
                label = { Text(if (showQuickEditor) "Hide Code" else "Quick Edit") },
                leadingIcon = {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("live_preview_quick_edit_toggle")
            )

            if (activeFilePath.endsWith(".kt", ignoreCase = true)) {
                AssistChip(
                    onClick = { onAnalyzeFileForRefactor(activeFilePath, currentActiveSource) },
                    label = { Text("AI Fix") },
                    leadingIcon = {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("live_preview_ai_fix_chip")
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = autoSaveStatus.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (parseResult.composables.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(parseResult.composables, key = { "${it.filePath}:${it.name}" }) { comp ->
                    val isSelected = activeComposable?.name == comp.name
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (activeComposable != null && activeComposable.name != comp.name) {
                                previewNavStack.add(activeComposable.name)
                            }
                            selectedComposableName = comp.name
                        },
                        label = { Text("@${comp.name}", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(if (showQuickEditor) 0.56f else 1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val availableWidth = (maxWidth - 8.dp).coerceAtLeast(180.dp)
            val availableHeight = (maxHeight - 4.dp).coerceAtLeast(140.dp)
            val targetPhoneWidth = if (isLandscape) {
                (if (isCompactPhone) 540.dp else 660.dp).coerceAtLeast(240.dp).coerceAtMost(availableWidth)
            } else {
                (if (isCompactPhone) 300.dp else 404.dp).coerceAtLeast(220.dp).coerceAtMost(availableWidth)
            }
            val targetPhoneHeight = if (isLandscape) {
                (targetPhoneWidth * 0.58f).coerceAtLeast(140.dp).coerceAtMost(availableHeight)
            } else {
                availableHeight.coerceAtMost(if (isCompactPhone) 640.dp else 760.dp)
            }

            val animatedPhoneWidth by animateDpAsState(
                targetValue = targetPhoneWidth,
                label = "preview_phone_width"
            )
            val animatedPhoneHeight by animateDpAsState(
                targetValue = targetPhoneHeight,
                label = "preview_phone_height"
            )

            LivePreviewDeviceFrame(
                composableInfo = activeComposable,
                allComposables = parseResult.composables,
                darkPreview = darkPreview,
                appName = project.appName,
                customColorSchemes = customColorSchemes,
                syntaxErrorMessage = syntaxErrorMessage,
                onFixWithAi = if (activeFilePath.endsWith(".kt", ignoreCase = true)) {
                    { onAnalyzeFileForRefactor(activeFilePath, currentActiveSource) }
                } else null,
                onNavigateScreen = { nextScreen ->
                    val currentName = activeComposable?.name
                    if (currentName != null && currentName != nextScreen) {
                        previewNavStack.add(currentName)
                    }
                    selectedComposableName = nextScreen
                },
                canNavigateBack = previewNavStack.isNotEmpty(),
                onNavigateBack = {
                    if (previewNavStack.isNotEmpty()) {
                        selectedComposableName = previewNavStack.removeAt(previewNavStack.lastIndex)
                    }
                },
                refreshNonce = refreshNonce,
                sharedStateStore = previewStateStore,
                fontScale = previewFontScale,
                isLandscape = isLandscape,
                modifier = Modifier
                    .width(animatedPhoneWidth)
                    .height(animatedPhoneHeight)
                    .testTag("live_preview_frame")
            )
        }

        if (showQuickEditor && activeFilePath.isNotBlank()) {
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Live Editing: ${activeFilePath.substringAfterLast('/')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = quickEditSource,
                    onValueChange = { updated ->
                        quickEditSource = updated
                        onLivePreviewEdit(activeFilePath, updated)
                    },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("live_preview_quick_editor_input")
                )
            }
        }
    }
}
