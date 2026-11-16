package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.model.GenerationUiState
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.StarterTemplatesScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object GeneratorRoute

@Serializable
object StarterTemplatesRoute

@Serializable
data class WorkspaceRoute(val projectId: String)

@Composable
fun AppNavGraph(
    studioViewModel: StudioViewModel = viewModel()
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val settings by studioViewModel.settings.collectAsStateWithLifecycle()
    val projects by studioViewModel.projects.collectAsStateWithLifecycle()
    val generationState by studioViewModel.generationState.collectAsStateWithLifecycle()
    val activeProject by studioViewModel.activeProject.collectAsStateWithLifecycle()
    val gitState by studioViewModel.gitState.collectAsStateWithLifecycle()
    val isGitBusy by studioViewModel.isGitBusy.collectAsStateWithLifecycle()
    val isCloudBuildBusy by studioViewModel.isCloudBuildBusy.collectAsStateWithLifecycle()
    val cloudBuildProgress by studioViewModel.cloudBuildProgress.collectAsStateWithLifecycle()
    val isImportingModel by studioViewModel.isImportingModel.collectAsStateWithLifecycle()
    val deviceHardwareProfile by studioViewModel.deviceHardwareProfile.collectAsStateWithLifecycle()
    val localModelDownloadState by studioViewModel.localModelDownloadState.collectAsStateWithLifecycle()
    val bundledModelState by studioViewModel.bundledModelState.collectAsStateWithLifecycle()
    val modelBenchmarkState by studioViewModel.modelBenchmarkState.collectAsStateWithLifecycle()
    val generatorConfig by studioViewModel.generatorConfig.collectAsStateWithLifecycle()
    val modelVerificationStates by studioViewModel.modelVerificationStates.collectAsStateWithLifecycle()
    val autoSaveStatus by studioViewModel.autoSaveStatus.collectAsStateWithLifecycle()
    val consoleLogs by studioViewModel.consoleLogs.collectAsStateWithLifecycle()
    val refactorState by studioViewModel.refactorState.collectAsStateWithLifecycle()
    val sqlProjects by studioViewModel.sqlProjects.collectAsStateWithLifecycle()
    val activeProjectSqlTables by studioViewModel.activeProjectSqlTables.collectAsStateWithLifecycle()
    val snapshots by studioViewModel.projectSnapshots.collectAsStateWithLifecycle()
    val snackbarMessage by studioViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val errorDialogMessage by studioViewModel.errorDialogMessage.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        studioViewModel.consumeSnackbar()
    }

    LaunchedEffect(generationState) {
        val success = generationState as? GenerationUiState.Success ?: return@LaunchedEffect
        studioViewModel.resetGenerationState()
        navController.navigate(WorkspaceRoute(success.projectId))
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            settings = settings,
            isImportingModel = isImportingModel,
            bundledModelState = bundledModelState,
            generatorConfig = generatorConfig,
            hardwareProfile = deviceHardwareProfile,
            localModelDownloadState = localModelDownloadState,
            modelVerificationStates = modelVerificationStates,
            onThemeModeChange = studioViewModel::updateThemeMode,
            onEditorThemeModeChange = studioViewModel::updateEditorThemeMode,
            onEngineModeChange = studioViewModel::updateEngineMode,
            onImportModelUri = studioViewModel::importLocalModelFile,
            onRestoreBuiltInModel = studioViewModel::restoreBuiltInBundledModel,
            onReinitializeModel = studioViewModel::reinitializeLocalModel,
            onDownloadBuiltInFromMirrors = studioViewModel::downloadBuiltInModelFromMirrors,
            onCancelBuiltInMirrorDownload = studioViewModel::cancelBuiltInMirrorDownload,
            onVerifyAllModelLinks = studioViewModel::verifyAllPresetModelLinks,
            onVerifySingleModelLink = studioViewModel::verifyPresetModelLink,
            onDownloadLocalModel = studioViewModel::downloadAndInitializeLocalModel,
            onCancelLocalModelDownload = studioViewModel::cancelLocalModelDownload,
            onDismissLocalModelDownloadError = studioViewModel::dismissLocalModelDownloadError,
            onRefreshHardwareProfile = studioViewModel::refreshDeviceHardwareProfile,
            onUpdateHuggingFaceToken = studioViewModel::updateHuggingFaceToken,
            onUnloadLocalModel = studioViewModel::unloadAndDeleteLocalModel,
            benchmarkState = modelBenchmarkState,
            onTestModel = studioViewModel::testBundledModel,
            onCancelTestModel = studioViewModel::cancelModelBenchmark,
            onSaveSettings = studioViewModel::saveSettings,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (errorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = studioViewModel::dismissErrorDialog,
            title = { Text("Action Error") },
            text = { Text(errorDialogMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = studioViewModel::dismissErrorDialog) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = GeneratorRoute
            ) {
                composable<GeneratorRoute> {
                    GeneratorScreen(
                        settings = settings,
                        projects = projects,
                        generationState = generationState,
                        isImportingModel = isImportingModel,
                        deviceHardwareProfile = deviceHardwareProfile,
                        localModelDownloadState = localModelDownloadState,
                        bundledModelState = bundledModelState,
                        modelVerificationStates = modelVerificationStates,
                        onEngineModeChange = studioViewModel::updateEngineMode,
                        onCloudModelChange = studioViewModel::updateCloudModel,
                        onImportLocalModelUri = studioViewModel::importLocalModelFile,
                        onRestoreBuiltInModel = studioViewModel::restoreBuiltInBundledModel,
                        onDownloadBuiltInFromMirrors = studioViewModel::downloadBuiltInModelFromMirrors,
                        onCancelBuiltInMirrorDownload = studioViewModel::cancelBuiltInMirrorDownload,
                        onVerifyAllModelLinks = studioViewModel::verifyAllPresetModelLinks,
                        onVerifySingleModelLink = studioViewModel::verifyPresetModelLink,
                        onDownloadLocalModel = studioViewModel::downloadAndInitializeLocalModel,
                        onCancelLocalModelDownload = studioViewModel::cancelLocalModelDownload,
                        onDismissLocalModelDownloadError = studioViewModel::dismissLocalModelDownloadError,
                        onRefreshHardwareProfile = studioViewModel::refreshDeviceHardwareProfile,
                        onUpdateHuggingFaceToken = studioViewModel::updateHuggingFaceToken,
                        onUnloadLocalModel = studioViewModel::unloadAndDeleteLocalModel,
                        onOpenSettings = { showSettingsSheet = true },
                        onOpenStarterTemplates = {
                            navController.navigate(StarterTemplatesRoute)
                        },
                        onGenerateApp = studioViewModel::generateApp,
                        onCancelGeneration = studioViewModel::cancelGeneration,
                        onOpenProject = { projectId ->
                            studioViewModel.openProject(projectId)
                            navController.navigate(WorkspaceRoute(projectId))
                        },
                        onDeleteProject = studioViewModel::deleteProject
                    )
                }

                composable<StarterTemplatesRoute> {
                    StarterTemplatesScreen(
                        onBack = { navController.popBackStack() },
                        onCreateFromTemplate = { template, appName, packageName, onComplete ->
                            studioViewModel.createProjectFromTemplate(
                                template = template,
                                customAppName = appName,
                                customPackageName = packageName,
                                onCreated = { projectId ->
                                    onComplete()
                                    navController.navigate(WorkspaceRoute(projectId)) {
                                        popUpTo(GeneratorRoute)
                                    }
                                }
                            )
                        }
                    )
                }

                composable<WorkspaceRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<WorkspaceRoute>()
                    LaunchedEffect(route.projectId) {
                        if (activeProject?.id != route.projectId) {
                            studioViewModel.openProject(route.projectId)
                        }
                    }

                    WorkspaceScreen(
                        project = activeProject,
                        settings = settings,
                        gitState = gitState,
                        autoSaveStatus = autoSaveStatus,
                        consoleLogs = consoleLogs,
                        refactorState = refactorState,
                        sqlProjects = sqlProjects,
                        customSqlTables = activeProjectSqlTables,
                        snapshots = snapshots,
                        isGitBusy = isGitBusy,
                        isCloudBuildBusy = isCloudBuildBusy,
                        cloudBuildProgress = cloudBuildProgress,
                        onBack = {
                            studioViewModel.flushPendingAutoSave()
                            navController.popBackStack()
                        },
                        onOpenSettings = { showSettingsSheet = true },
                        onSaveFile = studioViewModel::saveEditedFile,
                        onLivePreviewEdit = studioViewModel::updateFileInMemoryForPreview,
                        onFlushAutoSave = studioViewModel::flushPendingAutoSave,
                        onCreateCheckpoint = studioViewModel::createProjectSnapshot,
                        onRestoreSnapshot = studioViewModel::restoreProjectSnapshot,
                        onUpdateBranding = studioViewModel::updateProjectBranding,
                        onAddNewFile = studioViewModel::addNewFile,
                        onApplyThemeSeedColor = studioViewModel::applyThemeSeedColor,
                        onApplyDependencyChanges = studioViewModel::applyDependencyChanges,
                        onInjectRoomSqlTable = studioViewModel::injectRoomSqlTable,
                        onDeleteCustomSqlTable = studioViewModel::deleteCustomSqlTable,
                        onAnalyzeFileForRefactor = studioViewModel::analyzeCurrentFileForRefactor,
                        onApplyRefactoredCode = studioViewModel::applyRefactoredFile,
                        onDismissRefactorSheet = studioViewModel::dismissRefactorSheet,
                        onRunCompilerCheck = studioViewModel::runStaticCompilerCheck,
                        onClearConsoleLogs = studioViewModel::clearConsoleLogs,
                        onPackageZip = { studioViewModel.exportActiveProjectZip() },
                        onExportBuildLogs = { studioViewModel.exportBuildLogs() },
                        onInitGit = studioViewModel::initGitRepository,
                        onRefreshGit = { studioViewModel.refreshGitStatus() },
                        onStageFile = studioViewModel::stageGitFile,
                        onUnstageFile = studioViewModel::unstageGitFile,
                        onStageAll = studioViewModel::stageAllGitFiles,
                        onCommitGit = studioViewModel::commitGitChanges,
                        onConfigureRemote = studioViewModel::configureGitRemote,
                        onPushGit = studioViewModel::pushGitToGitHub,
                        onPullGit = studioViewModel::pullGitFromGitHub,
                        onTriggerCloudBuild = studioViewModel::triggerCloudApkBuild,
                        onRefreshCloudBuild = studioViewModel::refreshCloudApkBuildStatus,
                        onRenameResAsset = studioViewModel::renameResAsset,
                        onDeleteResAsset = studioViewModel::deleteResAsset,
                        onImportResImageUri = studioViewModel::importResImageFromUri,
                        onImportResFontUri = studioViewModel::importResFontFromUri,
                        onAddResTextAsset = studioViewModel::addResTextAsset,
                        onShowToast = { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    )
                }
            }
        }
    }
}


