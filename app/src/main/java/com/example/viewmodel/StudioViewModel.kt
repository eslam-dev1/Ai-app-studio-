package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.generator.BundledAssetMissingException
import com.example.data.generator.BundledModelManager
import com.example.data.generator.BundledModelState
import com.example.data.generator.CodeGeneratorRepository
import com.example.data.generator.DeviceHardwareProfile
import com.example.data.generator.DeviceHardwareProfiler
import com.example.data.generator.GenerationRepositoryConfig
import com.example.data.generator.HuggingFaceModelVerifier
import com.example.data.generator.LocalModelCatalog
import com.example.data.generator.ModelBenchmarkCalculator
import com.example.data.generator.ModelBenchmarkDefaults
import com.example.data.generator.ModelBenchmarkUiState
import com.example.data.generator.ModelLinkVerificationState
import com.example.data.generator.BenchmarkPromptResult
import com.example.data.git.GitRepositoryManager
import com.example.data.github.GitHubBuildRepository
import com.example.data.github.GitHubWorkflowInjector
import com.example.data.service.BackgroundFactoryState
import com.example.data.service.BackgroundTaskManager
import com.example.data.service.FactoryBuildService
import com.example.data.local.CustomSqlTableEntity
import com.example.data.local.ProjectSqlEntity
import com.example.data.local.RoomCodeGenerator
import com.example.data.local.SqlColumnSpec
import com.example.data.preferences.ApiCredentialValidator
import com.example.data.preferences.ApiKeyVerificationStatus
import com.example.data.preferences.ApiUsageHeaderParser
import com.example.data.preferences.ApiUsageSummary
import com.example.data.preferences.ApiUsageThresholdEvaluator
import com.example.data.build.LocalMediaPipeBuildPipeline
import com.example.data.build.OfflineApkBuildAndSignEngine
import com.example.data.build.OfflineKeystoreArtifact
import com.example.data.preferences.ConfiguredCloudProvider
import com.example.data.preferences.MonthlyUsageAlertState
import com.example.data.preferences.ParsedUsageHeaders
import com.example.data.preferences.SavedApiKeyHealthState
import com.example.data.preferences.SettingsDataStore
import com.example.data.refactor.AiCodeRefactorRepository
import com.example.data.storage.DebouncedProjectAutoSaver
import com.example.data.storage.ProjectStorageRepository
import com.example.data.templates.StarterTemplate
import com.example.data.templates.StarterTemplateCatalog
import com.example.diagnostics.FactoryTelemetryCalculator
import com.example.diagnostics.ProjectStaticAnalyzer
import com.example.model.AiEngineMode
import com.example.model.ApiUsageLimitMetric
import com.example.model.AppThemeMode
import com.example.model.AutoSaveStatus
import com.example.model.BuildPhase
import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.model.EditorThemeMode
import com.example.model.GeneratedFile
import com.example.model.GenerationUiState
import com.example.model.GitRepoState
import com.example.model.LocalModelDownloadState
import com.example.model.OfflineSigningStatus
import com.example.model.ProjectSnapshot
import com.example.model.RefactorUiState
import com.example.model.StudioProject
import com.example.model.StudioSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val generatorRepository = CodeGeneratorRepository(application)
    private val refactorRepository = AiCodeRefactorRepository()
    private val storageRepository = ProjectStorageRepository(application)
    private val gitManager = GitRepositoryManager()
    private val gitHubRepository = GitHubBuildRepository()

    val settings: StateFlow<StudioSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudioSettings())

    val generatorConfig: StateFlow<GenerationRepositoryConfig> = generatorRepository.configuration

    private val _projects = MutableStateFlow<List<StudioProject>>(emptyList())
    val projects: StateFlow<List<StudioProject>> = _projects.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _activeProject = MutableStateFlow<StudioProject?>(null)
    val activeProject: StateFlow<StudioProject?> = _activeProject.asStateFlow()

    val backgroundTaskState: StateFlow<BackgroundFactoryState> = BackgroundTaskManager.taskState

    private val _gitState = MutableStateFlow(GitRepoState())
    val gitState: StateFlow<GitRepoState> = _gitState.asStateFlow()

    private val _isGitBusy = MutableStateFlow(false)
    val isGitBusy: StateFlow<Boolean> = _isGitBusy.asStateFlow()

    private val _isCloudBuildBusy = MutableStateFlow(false)
    val isCloudBuildBusy: StateFlow<Boolean> = _isCloudBuildBusy.asStateFlow()

    private val _cloudBuildProgress = MutableStateFlow<String?>(null)
    val cloudBuildProgress: StateFlow<String?> = _cloudBuildProgress.asStateFlow()

    private val _isAutoFixingBuild = MutableStateFlow(false)
    val isAutoFixingBuild: StateFlow<Boolean> = _isAutoFixingBuild.asStateFlow()

    private val _isOfflineBuildBusy = MutableStateFlow(false)
    val isOfflineBuildBusy: StateFlow<Boolean> = _isOfflineBuildBusy.asStateFlow()

    private val _offlineBuildProgress = MutableStateFlow<String?>(null)
    val offlineBuildProgress: StateFlow<String?> = _offlineBuildProgress.asStateFlow()

    private val _offlineBuildStreamPreview = MutableStateFlow("")
    val offlineBuildStreamPreview: StateFlow<String> = _offlineBuildStreamPreview.asStateFlow()

    private val _offlineSigningStatus = MutableStateFlow(OfflineSigningStatus())
    val offlineSigningStatus: StateFlow<OfflineSigningStatus> = _offlineSigningStatus.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _consoleLogs.asStateFlow()

    private val _refactorState = MutableStateFlow<RefactorUiState>(RefactorUiState.Idle)
    val refactorState: StateFlow<RefactorUiState> = _refactorState.asStateFlow()

    val sqlProjects: StateFlow<List<ProjectSqlEntity>> = storageRepository.sqlRepository.allProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjectSqlTables: StateFlow<List<CustomSqlTableEntity>> = _activeProject
        .flatMapLatest { proj ->
            if (proj == null) flowOf(emptyList())
            else storageRepository.sqlRepository.getCustomTablesFlow(proj.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImportingModel = MutableStateFlow(false)
    val isImportingModel: StateFlow<Boolean> = _isImportingModel.asStateFlow()

    private val _deviceHardwareProfile = MutableStateFlow(DeviceHardwareProfiler.inspectDevice(application))
    val deviceHardwareProfile: StateFlow<DeviceHardwareProfile> = _deviceHardwareProfile.asStateFlow()

    private val _localModelDownloadState = MutableStateFlow<LocalModelDownloadState>(LocalModelDownloadState.Idle)
    val localModelDownloadState: StateFlow<LocalModelDownloadState> = _localModelDownloadState.asStateFlow()

    private val _modelVerificationStates = MutableStateFlow<Map<String, ModelLinkVerificationState>>(emptyMap())
    val modelVerificationStates: StateFlow<Map<String, ModelLinkVerificationState>> = _modelVerificationStates.asStateFlow()

    private val _bundledModelState = MutableStateFlow<BundledModelState>(BundledModelState.Idle)
    val bundledModelState: StateFlow<BundledModelState> = _bundledModelState.asStateFlow()

    private val _modelBenchmarkState = MutableStateFlow<ModelBenchmarkUiState>(ModelBenchmarkUiState.Idle)
    val modelBenchmarkState: StateFlow<ModelBenchmarkUiState> = _modelBenchmarkState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _projectSnapshots = MutableStateFlow<List<ProjectSnapshot>>(emptyList())
    val projectSnapshots: StateFlow<List<ProjectSnapshot>> = _projectSnapshots.asStateFlow()

    private val _errorDialogMessage = MutableStateFlow<String?>(null)
    val errorDialogMessage: StateFlow<String?> = _errorDialogMessage.asStateFlow()

    private val _geminiVerificationState = MutableStateFlow<ApiKeyVerificationStatus>(ApiKeyVerificationStatus.Idle)
    val geminiVerificationState: StateFlow<ApiKeyVerificationStatus> = _geminiVerificationState.asStateFlow()

    private val _openRouterVerificationState = MutableStateFlow<ApiKeyVerificationStatus>(ApiKeyVerificationStatus.Idle)
    val openRouterVerificationState: StateFlow<ApiKeyVerificationStatus> = _openRouterVerificationState.asStateFlow()

    private val _savedKeyHealthState = MutableStateFlow<SavedApiKeyHealthState>(SavedApiKeyHealthState.NotConfigured)
    val savedKeyHealthState: StateFlow<SavedApiKeyHealthState> = _savedKeyHealthState.asStateFlow()

    val apiUsageSummary: StateFlow<ApiUsageSummary> = settingsDataStore.apiUsageSummaryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ApiUsageSummary())

    val monthlyUsageAlertState: StateFlow<MonthlyUsageAlertState> = combine(
        apiUsageSummary,
        settings
    ) { summary, currentSettings ->
        ApiUsageThresholdEvaluator.evaluate(summary, currentSettings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ApiUsageThresholdEvaluator.evaluate(ApiUsageSummary(), StudioSettings())
    )

    private val usageTelemetryMutex = Mutex()

    val isCredentialStorageHardwareBacked: Boolean by lazy {
        settingsDataStore.isHardwareKeystoreBacked()
    }

    private val autoSaver = DebouncedProjectAutoSaver(
        scope = viewModelScope,
        debounceMs = 650L,
        persistProject = { projectToSave ->
            storageRepository.saveProject(projectToSave)
        },
        onPersisted = { savedProject ->
            // Only update activeProject metadata timestamp so we don't clobber newer in-flight keystrokes
            val current = _activeProject.value
            if (current != null && current.id == savedProject.id) {
                _activeProject.value = current.copy(updatedAtEpochMs = savedProject.updatedAtEpochMs)
            }
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(savedProject.id)
            runIncrementalStaticCheck(savedProject)
        }
    )

    val autoSaveStatus: StateFlow<AutoSaveStatus> = autoSaver.status

    private var generationJob: Job? = null
    private var refactorJob: Job? = null
    private var modelDownloadJob: Job? = null
    private var verifyAllLinksJob: Job? = null
    private var bundledBootstrapJob: Job? = null
    private var builtInMirrorDownloadJob: Job? = null
    private var benchmarkJob: Job? = null
    private var verifySavedKeyJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { latestSettings ->
                generatorRepository.updateConfiguration(latestSettings)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _activeProject
                .map { proj ->
                    proj?.let {
                        Triple(
                            it.id,
                            it.appName,
                            it.files.firstOrNull { f -> f.path == OfflineApkBuildAndSignEngine.KEYSTORE_PROJECT_PATH }?.content
                        )
                    }
                }
                .distinctUntilChanged()
                .collect {
                    refreshOfflineSigningStatus(_activeProject.value)
                }
        }
        refreshProjects()
        bootstrapBundledModelOnLaunch()
    }

    fun restoreBuiltInBundledModel() {
        bootstrapBundledModelOnLaunch(forceRestore = true)
    }

    fun cancelBuiltInMirrorDownload() {
        builtInMirrorDownloadJob?.cancel()
        builtInMirrorDownloadJob = null
        val app = getApplication<Application>()
        BackgroundTaskManager.cancelTask(app)
        val targetFile = BundledModelManager.getTargetModelFile(app)
        val hasValidTarget = BundledModelManager.verifyModelFileIntegrity(
            file = targetFile,
            expectedSizeBytes = 0L,
            minValidBytes = BundledModelManager.MIN_FALLBACK_DOWNLOAD_BYTES
        ).isValid
        _bundledModelState.value = if (hasValidTarget || BundledModelManager.hasBundledModelAsset(app)) {
            BundledModelState.Idle
        } else {
            BundledModelState.AssetMissingNeedsDownload()
        }
    }

    fun downloadBuiltInModelFromMirrors() {
        if (builtInMirrorDownloadJob?.isActive == true) return
        bundledBootstrapJob?.cancel()
        builtInMirrorDownloadJob?.cancel()
        val app = getApplication<Application>()
        BackgroundTaskManager.startTask(app, "Downloading Built-in AI Model", "Contacting mirrors…")
        builtInMirrorDownloadJob = BackgroundTaskManager.scope.launch {
            val totalMirrors = BundledModelManager.BUILT_IN_FALLBACK_MIRRORS.size
            val firstMirrorLabel = BundledModelManager.BUILT_IN_FALLBACK_MIRRORS
                .firstOrNull()?.label ?: "Mirror 1"
            _bundledModelState.value = BundledModelState.DownloadingFromMirror(
                mirrorIndex = 1,
                totalMirrors = totalMirrors,
                mirrorLabel = firstMirrorLabel,
                downloadedBytes = 0L,
                totalBytes = 0L,
                percent = 0
            )

            try {
                val token = settingsDataStore.settingsFlow.first().huggingFaceToken
                val downloadResult = BundledModelManager.downloadBuiltInModelWithMirrorFallback(
                    context = app,
                    huggingFaceToken = token,
                    onMirrorAttempt = { mirrorIdx, total, mirror ->
                        _bundledModelState.value = BundledModelState.DownloadingFromMirror(
                            mirrorIndex = mirrorIdx,
                            totalMirrors = total,
                            mirrorLabel = mirror.label,
                            downloadedBytes = 0L,
                            totalBytes = 0L,
                            percent = 0
                        )
                        BackgroundTaskManager.updateProgress(
                            app,
                            "Downloading Built-in AI Model",
                            "Switching to ${mirror.label} ($mirrorIdx/$total)…",
                            0.05f
                        )
                    },
                    onProgress = { mirrorIdx, total, mirror, downloaded, totalBytes, pct ->
                        _bundledModelState.value = BundledModelState.DownloadingFromMirror(
                            mirrorIndex = mirrorIdx,
                            totalMirrors = total,
                            mirrorLabel = mirror.label,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            percent = pct
                        )
                        val downMb = downloaded / (1024L * 1024L)
                        val totMb = totalBytes / (1024L * 1024L)
                        BackgroundTaskManager.updateProgress(
                            app,
                            "Downloading Built-in AI Model",
                            "${mirror.label} • $downMb MB / $totMb MB ($pct%)",
                            pct / 100f
                        )
                    }
                )

                downloadResult.onSuccess { result ->
                    settingsDataStore.updateOnDeviceModel(
                        filePath = result.file.absolutePath,
                        displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                        sizeBytes = result.sizeBytes
                    )
                    _bundledModelState.value = BundledModelState.Initializing(
                        "Downloaded via ${result.mirrorUsed.label} • Initializing MediaPipe LLM…"
                    )
                    BackgroundTaskManager.updateProgress(
                        app,
                        "Downloading Built-in AI Model",
                        "Initializing MediaPipe LLM…",
                        0.95f
                    )
                    runCatching {
                        generatorRepository.initializeLocalModel(
                            savedModelPath = result.file.absolutePath,
                            maxTokens = BundledModelManager.DEFAULT_MAX_TOKENS,
                            temperature = BundledModelManager.DEFAULT_TEMPERATURE
                        )
                    }.onSuccess {
                        _bundledModelState.value = BundledModelState.Ready(
                            modelPath = result.file.absolutePath,
                            displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                            sizeBytes = result.sizeBytes
                        )
                        _snackbarMessage.value =
                            "Built-in AI downloaded (${result.mirrorUsed.label}) & initialized!"
                        BackgroundTaskManager.finishTask(
                            app,
                            "Built-in AI Model Ready",
                            "Initialized via ${result.mirrorUsed.label} • Ready for offline inference"
                        )
                    }.onFailure { err ->
                        _bundledModelState.value = BundledModelState.Unavailable(
                            err.localizedMessage
                                ?: "Downloaded built-in model, but MediaPipe failed to initialize."
                        )
                        BackgroundTaskManager.failTask(
                            app,
                            "Built-in AI Model",
                            err.localizedMessage ?: "MediaPipe initialization failed"
                        )
                    }
                    refreshDeviceHardwareProfile()
                }.onFailure { err ->
                    _bundledModelState.value = BundledModelState.Unavailable(
                        err.localizedMessage ?: "All fallback mirrors failed to download Built-in AI."
                    )
                    BackgroundTaskManager.failTask(
                        app,
                        "Built-in AI Model",
                        err.localizedMessage ?: "All mirrors failed"
                    )
                }
            } catch (ce: CancellationException) {
                BackgroundTaskManager.cancelTask(app)
                if (!BundledModelManager.hasBundledModelAsset(app)) {
                    _bundledModelState.value = BundledModelState.AssetMissingNeedsDownload()
                } else {
                    _bundledModelState.value = BundledModelState.Idle
                }
                throw ce
            }
        }
    }

    fun cancelModelBenchmark() {
        benchmarkJob?.cancel()
        benchmarkJob = null
        _modelBenchmarkState.value = ModelBenchmarkUiState.Idle
    }

    fun testBundledModel() {
        benchmarkJob?.cancel()
        benchmarkJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val prompts = ModelBenchmarkDefaults.prompts
            val completedResults = mutableListOf<BenchmarkPromptResult>()

            try {
                val bundledTarget = BundledModelManager.getTargetModelFile(app)
                val expectedAssetSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BundledModelManager.resolveAssetSizeBytes(app)
                }
                val integrity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BundledModelManager.verifyModelFileIntegrity(
                        file = bundledTarget,
                        expectedSizeBytes = expectedAssetSize,
                        minValidBytes = BundledModelManager.MIN_VALID_MODEL_BYTES
                    )
                }

                val readyModelFile = if (integrity.isValid) {
                    bundledTarget
                } else {
                    _bundledModelState.value = BundledModelState.Checking("Extracting bundled model for benchmark…")
                    val extracted = BundledModelManager.ensureExtractedToFilesDir(app) { copied, total, pct ->
                        _bundledModelState.value = BundledModelState.Extracting(
                            copiedBytes = copied,
                            totalBytes = total,
                            percent = pct
                        )
                    }.getOrElse { err ->
                        _modelBenchmarkState.value = ModelBenchmarkUiState.Error(
                            message = err.localizedMessage ?: "Bundled model is unavailable for testing."
                        )
                        return@launch
                    }
                    _bundledModelState.value = BundledModelState.Ready(
                        modelPath = extracted.first.absolutePath,
                        displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                        sizeBytes = extracted.second
                    )
                    extracted.first
                }

                prompts.forEachIndexed { index, spec ->
                    _modelBenchmarkState.value = ModelBenchmarkUiState.Running(
                        currentIndex = index + 1,
                        totalPrompts = prompts.size,
                        currentLabel = spec.label,
                        completedResults = completedResults.toList()
                    )

                    val stepResult = runCatching {
                        generatorRepository.runLocalPromptBenchmarkStep(
                            savedModelPath = readyModelFile.absolutePath,
                            prompt = spec.prompt
                        )
                    }

                    stepResult.onSuccess { (output, durationMs) ->
                        val preview = output
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(120)
                        completedResults += BenchmarkPromptResult(
                            id = spec.id,
                            label = spec.label,
                            prompt = spec.prompt,
                            durationMs = durationMs,
                            responsePreview = preview,
                            charCount = output.length,
                            succeeded = true
                        )
                    }.onFailure { err ->
                        if (err is CancellationException) throw err
                        _modelBenchmarkState.value = ModelBenchmarkUiState.Error(
                            message = err.localizedMessage ?: "Benchmark failed on ${spec.label}.",
                            partialResults = completedResults.toList()
                        )
                        return@launch
                    }
                }

                val summary = ModelBenchmarkCalculator.summarize(
                    modelDisplayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                    modelPath = readyModelFile.absolutePath,
                    results = completedResults.toList()
                )
                _modelBenchmarkState.value = ModelBenchmarkUiState.Completed(summary)
                _snackbarMessage.value = "Bundled model benchmark complete • Avg ${summary.averageMs} ms"
            } catch (ce: CancellationException) {
                _modelBenchmarkState.value = ModelBenchmarkUiState.Idle
                throw ce
            } catch (ex: Exception) {
                _modelBenchmarkState.value = ModelBenchmarkUiState.Error(
                    message = ex.localizedMessage ?: "Failed to benchmark bundled model.",
                    partialResults = completedResults.toList()
                )
            }
        }
    }

    fun reinitializeLocalModel() {
        bundledBootstrapJob?.cancel()
        bundledBootstrapJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _bundledModelState.value = BundledModelState.Checking("Checking local model file integrity…")
            generatorRepository.close()

            val currentSettings = settingsDataStore.settingsFlow.first()
            val existingPath = currentSettings.localModelFilePath.trim()
            val bundledTarget = BundledModelManager.getTargetModelFile(app)
            val isCustomModelConfigured = existingPath.isNotBlank() &&
                existingPath != bundledTarget.absolutePath

            if (isCustomModelConfigured) {
                val customIntegrity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BundledModelManager.verifyModelFileIntegrity(
                        file = File(existingPath),
                        expectedSizeBytes = currentSettings.localModelSizeBytes,
                        minValidBytes = BundledModelManager.MIN_CUSTOM_MODEL_BYTES
                    )
                }
                if (customIntegrity.isValid) {
                    val displayName = currentSettings.localModelDisplayName.ifBlank { File(existingPath).name }
                    _bundledModelState.value = BundledModelState.Initializing(
                        "Integrity verified • Re-initializing $displayName…"
                    )
                    runCatching {
                        generatorRepository.initializeLocalModel(
                            savedModelPath = existingPath,
                            maxTokens = BundledModelManager.DEFAULT_MAX_TOKENS,
                            temperature = BundledModelManager.DEFAULT_TEMPERATURE
                        )
                    }.onSuccess {
                        _bundledModelState.value = BundledModelState.Ready(
                            modelPath = existingPath,
                            displayName = displayName,
                            sizeBytes = customIntegrity.actualSizeBytes
                        )
                        _snackbarMessage.value = "Local model integrity verified & re-initialized"
                    }.onFailure { err ->
                        _bundledModelState.value = BundledModelState.Unavailable(
                            err.localizedMessage ?: "Failed to re-initialize custom local model."
                        )
                    }
                    refreshDeviceHardwareProfile()
                    return@launch
                } else {
                    _bundledModelState.value = BundledModelState.Checking(
                        "Custom model failed integrity check — restoring built-in offline model…"
                    )
                }
            } else {
                val isAssetBundled = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BundledModelManager.hasBundledModelAsset(app)
                }
                val expectedAssetSize = if (isAssetBundled) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        BundledModelManager.resolveAssetSizeBytes(app)
                    }
                } else 0L
                val minBytes = if (isAssetBundled) {
                    BundledModelManager.MIN_VALID_MODEL_BYTES
                } else {
                    BundledModelManager.MIN_FALLBACK_DOWNLOAD_BYTES
                }
                val bundledIntegrity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    BundledModelManager.verifyModelFileIntegrity(
                        file = bundledTarget,
                        expectedSizeBytes = expectedAssetSize,
                        minValidBytes = minBytes
                    )
                }
                if (!bundledIntegrity.isValid) {
                    if (!isAssetBundled) {
                        _bundledModelState.value = BundledModelState.AssetMissingNeedsDownload()
                        return@launch
                    }
                    _bundledModelState.value = BundledModelState.Checking(
                        "Model check: ${bundledIntegrity.failureReason ?: "Needs extraction"} Restarting extraction…"
                    )
                }
            }

            val extractResult = BundledModelManager.ensureExtractedToFilesDir(app) { copied, total, pct ->
                _bundledModelState.value = BundledModelState.Extracting(
                    copiedBytes = copied,
                    totalBytes = total,
                    percent = pct
                )
            }

            extractResult.onSuccess { (extractedFile, sizeBytes) ->
                settingsDataStore.updateOnDeviceModel(
                    filePath = extractedFile.absolutePath,
                    displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                    sizeBytes = sizeBytes
                )
                _bundledModelState.value = BundledModelState.Initializing(
                    "Integrity verified • Re-initializing MediaPipe LLM…"
                )
                runCatching {
                    generatorRepository.initializeLocalModel(
                        savedModelPath = extractedFile.absolutePath,
                        maxTokens = BundledModelManager.DEFAULT_MAX_TOKENS,
                        temperature = BundledModelManager.DEFAULT_TEMPERATURE
                    )
                }.onSuccess {
                    _bundledModelState.value = BundledModelState.Ready(
                        modelPath = extractedFile.absolutePath,
                        displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                        sizeBytes = sizeBytes
                    )
                    _snackbarMessage.value = "Local model integrity verified & re-initialized"
                }.onFailure { err ->
                    _bundledModelState.value = BundledModelState.Unavailable(
                        err.localizedMessage ?: "Model extracted to storage; runtime failed to initialize."
                    )
                }
                refreshDeviceHardwareProfile()
            }.onFailure { err ->
                _bundledModelState.value = if (err is BundledAssetMissingException) {
                    BundledModelState.AssetMissingNeedsDownload()
                } else {
                    BundledModelState.Unavailable(
                        err.localizedMessage ?: "Built-in model asset unavailable in this build."
                    )
                }
            }
        }
    }

    private fun bootstrapBundledModelOnLaunch(forceRestore: Boolean = false) {
        if (!forceRestore && bundledBootstrapJob?.isActive == true) return
        bundledBootstrapJob?.cancel()
        bundledBootstrapJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _bundledModelState.value = BundledModelState.Checking()

            val currentSettings = settingsDataStore.settingsFlow.first()
            val existingPath = currentSettings.localModelFilePath.trim()
            val bundledTarget = BundledModelManager.getTargetModelFile(app)
            val hasCustomModel = !forceRestore &&
                existingPath.isNotBlank() &&
                existingPath != bundledTarget.absolutePath &&
                File(existingPath).exists()

            if (hasCustomModel) {
                _bundledModelState.value = BundledModelState.Initializing(
                    "Initializing ${currentSettings.localModelDisplayName.ifBlank { File(existingPath).name }}…"
                )
                runCatching {
                    generatorRepository.initializeLocalModel(
                        savedModelPath = existingPath,
                        maxTokens = BundledModelManager.DEFAULT_MAX_TOKENS,
                        temperature = BundledModelManager.DEFAULT_TEMPERATURE
                    )
                }.onSuccess {
                    _bundledModelState.value = BundledModelState.Ready(
                        modelPath = existingPath,
                        displayName = currentSettings.localModelDisplayName.ifBlank { File(existingPath).name },
                        sizeBytes = currentSettings.localModelSizeBytes
                    )
                }.onFailure { err ->
                    _bundledModelState.value = BundledModelState.Unavailable(
                        err.localizedMessage ?: "Custom model runtime initialization deferred."
                    )
                }
                refreshDeviceHardwareProfile()
                return@launch
            }

            val extractResult = BundledModelManager.ensureExtractedToFilesDir(app) { copied, total, pct ->
                _bundledModelState.value = BundledModelState.Extracting(
                    copiedBytes = copied,
                    totalBytes = total,
                    percent = pct
                )
            }

            extractResult.onSuccess { (extractedFile, sizeBytes) ->
                settingsDataStore.updateOnDeviceModel(
                    filePath = extractedFile.absolutePath,
                    displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                    sizeBytes = sizeBytes
                )
                _bundledModelState.value = BundledModelState.Initializing()
                runCatching {
                    generatorRepository.initializeLocalModel(
                        savedModelPath = extractedFile.absolutePath,
                        maxTokens = BundledModelManager.DEFAULT_MAX_TOKENS,
                        temperature = BundledModelManager.DEFAULT_TEMPERATURE
                    )
                }.onSuccess {
                    _bundledModelState.value = BundledModelState.Ready(
                        modelPath = extractedFile.absolutePath,
                        displayName = BundledModelManager.BUNDLED_DISPLAY_NAME,
                        sizeBytes = sizeBytes
                    )
                    if (forceRestore) {
                        _snackbarMessage.value = "Restored & initialized built-in offline AI model"
                    }
                }.onFailure { err ->
                    _bundledModelState.value = BundledModelState.Unavailable(
                        err.localizedMessage ?: "Model extracted to storage; runtime will retry on generate."
                    )
                }
                refreshDeviceHardwareProfile()
            }.onFailure { err ->
                _bundledModelState.value = if (err is BundledAssetMissingException) {
                    BundledModelState.AssetMissingNeedsDownload()
                } else {
                    BundledModelState.Unavailable(
                        err.localizedMessage ?: "Built-in model asset unavailable in this build."
                    )
                }
            }
        }
    }


    fun appendConsoleLog(
        level: ConsoleLogLevel,
        tag: String,
        message: String,
        filePath: String? = null,
        lineNumber: Int? = null
    ) {
        val entry = ConsoleLogEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            filePath = filePath,
            lineNumber = lineNumber
        )
        appendConsoleEntry(entry)
    }

    fun appendConsoleEntry(entry: ConsoleLogEntry) {
        _consoleLogs.update { existing ->
            (existing + entry).takeLast(300)
        }
        val projectId = _activeProject.value?.id ?: return
        viewModelScope.launch {
            runCatching { storageRepository.sqlRepository.insertLog(projectId, entry) }
        }
    }

    fun appendConsoleEntries(entries: List<ConsoleLogEntry>) {
        if (entries.isEmpty()) return
        _consoleLogs.update { existing ->
            (existing + entries).takeLast(300)
        }
        val projectId = _activeProject.value?.id ?: return
        viewModelScope.launch {
            runCatching { storageRepository.sqlRepository.insertLogs(projectId, entries) }
        }
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
        val projectId = _activeProject.value?.id ?: return
        viewModelScope.launch {
            runCatching { storageRepository.sqlRepository.clearLogs(projectId) }
        }
    }

    fun runStaticCompilerCheck() {
        val current = _activeProject.value ?: return
        val diagnostics = ProjectStaticAnalyzer.analyzeProject(current)
        appendConsoleEntries(diagnostics)
    }

    private fun runIncrementalStaticCheck(project: StudioProject) {
        val issues = project.files.flatMap { ProjectStaticAnalyzer.analyzeSingleFile(it) }
        if (issues.isNotEmpty()) {
            // Replace previous incrementalKotlinCompiler/GradleCompiler file warnings so console stays clean
            _consoleLogs.update { existing ->
                val filtered = existing.filterNot {
                    it.tag in setOf("KotlinCompiler", "GradleCompiler", "ManifestMerger", "Aapt2Resource") &&
                        it.filePath != null
                }
                (filtered + issues).takeLast(300)
            }
        } else {
            appendConsoleLog(
                level = ConsoleLogLevel.DEBUG,
                tag = "AutoSaver",
                message = "Auto-saved '${project.appName}' to filesDir — 0 syntax/compiler errors."
            )
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _projects.value = storageRepository.loadAllProjects()
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun dismissErrorDialog() {
        _errorDialogMessage.value = null
    }

    fun resetGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun updateEngineMode(mode: AiEngineMode) {
        val nextSettings = settings.value.copy(aiEngineMode = mode)
        generatorRepository.updateConfiguration(nextSettings)
        viewModelScope.launch {
            settingsDataStore.updateEngineMode(mode)
        }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsDataStore.updateThemeMode(mode)
        }
    }

    fun updateEditorThemeMode(mode: EditorThemeMode) {
        viewModelScope.launch {
            settingsDataStore.updateEditorThemeMode(mode)
        }
    }

    fun updateCloudModel(modelId: String) {
        val nextSettings = settings.value.copy(openRouterModel = modelId)
        generatorRepository.updateConfiguration(nextSettings)
        viewModelScope.launch {
            settingsDataStore.updateCloudModel(modelId)
        }
    }

    fun saveSettings(updated: StudioSettings) {
        val geminiCheck = ApiCredentialValidator.validateGeminiKeyFormat(updated.geminiApiKey)
        if (!geminiCheck.isValidShape) {
            _snackbarMessage.value = geminiCheck.warningMessage ?: "Invalid Gemini API key format"
            return
        }
        val openRouterCheck = ApiCredentialValidator.validateOpenRouterKeyFormat(updated.openRouterApiKey)
        if (!openRouterCheck.isValidShape) {
            _snackbarMessage.value = openRouterCheck.warningMessage ?: "Invalid OpenRouter API key format"
            return
        }
        generatorRepository.updateConfiguration(updated)
        viewModelScope.launch {
            settingsDataStore.saveAll(updated)
            _snackbarMessage.value = "Settings encrypted & saved to DataStore"
            verifySavedProviderCredentials(updated)
        }
    }

    fun saveCloudCredentials(openRouterKey: String, geminiKey: String, modelId: String) {
        val geminiCheck = ApiCredentialValidator.validateGeminiKeyFormat(geminiKey)
        if (!geminiCheck.isValidShape) {
            _snackbarMessage.value = geminiCheck.warningMessage ?: "Invalid Gemini API key format"
            return
        }
        val openRouterCheck = ApiCredentialValidator.validateOpenRouterKeyFormat(openRouterKey)
        if (!openRouterCheck.isValidShape) {
            _snackbarMessage.value = openRouterCheck.warningMessage ?: "Invalid OpenRouter API key format"
            return
        }
        val cleanOr = openRouterKey.trim()
        val cleanGem = geminiKey.trim()
        val nextSettings = settings.value.copy(
            openRouterApiKey = cleanOr,
            geminiApiKey = cleanGem,
            openRouterModel = modelId
        )
        generatorRepository.updateConfiguration(nextSettings)
        verifySavedProviderCredentials(nextSettings)
        viewModelScope.launch {
            settingsDataStore.updateCloudSettings(cleanOr, cleanGem, modelId)
            _snackbarMessage.value = "API credentials encrypted & saved to DataStore"
        }
    }

    fun clearGeminiApiKey() {
        val nextSettings = settings.value.copy(geminiApiKey = "")
        generatorRepository.updateConfiguration(nextSettings)
        _geminiVerificationState.value = ApiKeyVerificationStatus.Idle
        viewModelScope.launch {
            settingsDataStore.clearGeminiApiKey()
            _snackbarMessage.value = "Gemini API key cleared from DataStore"
            verifySavedProviderCredentials(nextSettings)
        }
    }

    fun clearOpenRouterApiKey() {
        val nextSettings = settings.value.copy(openRouterApiKey = "")
        generatorRepository.updateConfiguration(nextSettings)
        _openRouterVerificationState.value = ApiKeyVerificationStatus.Idle
        viewModelScope.launch {
            settingsDataStore.clearOpenRouterApiKey()
            _snackbarMessage.value = "OpenRouter API key cleared from DataStore"
            verifySavedProviderCredentials(nextSettings)
        }
    }

    fun clearAllCloudCredentials() {
        val nextSettings = settings.value.copy(openRouterApiKey = "", geminiApiKey = "")
        generatorRepository.updateConfiguration(nextSettings)
        _geminiVerificationState.value = ApiKeyVerificationStatus.Idle
        _openRouterVerificationState.value = ApiKeyVerificationStatus.Idle
        viewModelScope.launch {
            settingsDataStore.clearAllCloudCredentials()
            _snackbarMessage.value = "All cloud API credentials cleared"
            verifySavedProviderCredentials(nextSettings)
        }
    }

    fun verifySavedProviderCredentials(targetSettings: StudioSettings = settings.value) {
        verifySavedKeyJob?.cancel()
        val resolved = ApiCredentialValidator.resolveConfiguredProvider(targetSettings)
        if (resolved == null) {
            _savedKeyHealthState.value = SavedApiKeyHealthState.NotConfigured
            return
        }
        val providerLabel = if (resolved.isFromBuildConfig) {
            "${resolved.provider.displayName} (Env Secret)"
        } else {
            resolved.provider.displayName
        }
        _savedKeyHealthState.value = SavedApiKeyHealthState.Checking(
            providerLabel = providerLabel,
            maskedKey = resolved.maskedKey
        )
        verifySavedKeyJob = viewModelScope.launch {
            val (_, healthState) = ApiCredentialValidator.verifySavedProviderHealth(targetSettings)
            _savedKeyHealthState.value = healthState
            when (healthState) {
                is SavedApiKeyHealthState.Verified -> {
                    val validStatus = ApiKeyVerificationStatus.Valid(
                        provider = healthState.providerLabel,
                        detail = healthState.detail,
                        latencyMs = healthState.latencyMs,
                        httpStatusCode = healthState.httpStatusCode,
                        usageHeaders = healthState.usageHeaders
                    )
                    if (resolved.provider == ConfiguredCloudProvider.GEMINI) {
                        _geminiVerificationState.value = validStatus
                    } else {
                        _openRouterVerificationState.value = validStatus
                    }
                    recordApiValidationTelemetry(
                        providerName = healthState.providerLabel,
                        httpStatusCode = healthState.httpStatusCode,
                        latencyMs = healthState.latencyMs,
                        parsedHeaders = healthState.usageHeaders
                    )
                }
                is SavedApiKeyHealthState.Error -> {
                    val invalidStatus = ApiKeyVerificationStatus.Invalid(
                        errorMessage = healthState.message,
                        latencyMs = healthState.latencyMs,
                        httpStatusCode = healthState.httpStatusCode,
                        usageHeaders = healthState.usageHeaders
                    )
                    if (resolved.provider == ConfiguredCloudProvider.GEMINI) {
                        _geminiVerificationState.value = invalidStatus
                    } else {
                        _openRouterVerificationState.value = invalidStatus
                    }
                    if (healthState.latencyMs != null || healthState.httpStatusCode != null) {
                        recordApiValidationTelemetry(
                            providerName = healthState.providerLabel,
                            httpStatusCode = healthState.httpStatusCode,
                            latencyMs = healthState.latencyMs,
                            parsedHeaders = healthState.usageHeaders
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun verifyGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            _geminiVerificationState.value = ApiKeyVerificationStatus.Verifying
            val status = ApiCredentialValidator.verifyGeminiApiKey(apiKey)
            _geminiVerificationState.value = status
            when (status) {
                is ApiKeyVerificationStatus.Valid -> {
                    recordApiValidationTelemetry(
                        providerName = status.provider,
                        httpStatusCode = status.httpStatusCode,
                        latencyMs = status.latencyMs,
                        parsedHeaders = status.usageHeaders
                    )
                }
                is ApiKeyVerificationStatus.Invalid -> {
                    if (status.latencyMs != null || status.httpStatusCode != null) {
                        recordApiValidationTelemetry(
                            providerName = ConfiguredCloudProvider.GEMINI.displayName,
                            httpStatusCode = status.httpStatusCode,
                            latencyMs = status.latencyMs,
                            parsedHeaders = status.usageHeaders
                        )
                    }
                }
                else -> Unit
            }
            if (apiKey.trim() == settings.value.geminiApiKey.trim() && apiKey.isNotBlank()) {
                _savedKeyHealthState.value = when (status) {
                    is ApiKeyVerificationStatus.Valid -> SavedApiKeyHealthState.Verified(
                        providerLabel = ConfiguredCloudProvider.GEMINI.displayName,
                        maskedKey = com.example.data.preferences.SecureCredentialCipher.maskSecret(apiKey),
                        detail = status.detail,
                        latencyMs = status.latencyMs,
                        httpStatusCode = status.httpStatusCode,
                        usageHeaders = status.usageHeaders
                    )
                    is ApiKeyVerificationStatus.Invalid -> SavedApiKeyHealthState.Error(
                        providerLabel = ConfiguredCloudProvider.GEMINI.displayName,
                        maskedKey = com.example.data.preferences.SecureCredentialCipher.maskSecret(apiKey),
                        message = status.errorMessage,
                        latencyMs = status.latencyMs,
                        httpStatusCode = status.httpStatusCode,
                        usageHeaders = status.usageHeaders
                    )
                    else -> _savedKeyHealthState.value
                }
            }
        }
    }

    fun verifyOpenRouterApiKey(apiKey: String) {
        viewModelScope.launch {
            _openRouterVerificationState.value = ApiKeyVerificationStatus.Verifying
            val status = ApiCredentialValidator.verifyOpenRouterApiKey(apiKey)
            _openRouterVerificationState.value = status
            when (status) {
                is ApiKeyVerificationStatus.Valid -> {
                    recordApiValidationTelemetry(
                        providerName = status.provider,
                        httpStatusCode = status.httpStatusCode,
                        latencyMs = status.latencyMs,
                        parsedHeaders = status.usageHeaders
                    )
                }
                is ApiKeyVerificationStatus.Invalid -> {
                    if (status.latencyMs != null || status.httpStatusCode != null) {
                        recordApiValidationTelemetry(
                            providerName = ConfiguredCloudProvider.OPENROUTER.displayName,
                            httpStatusCode = status.httpStatusCode,
                            latencyMs = status.latencyMs,
                            parsedHeaders = status.usageHeaders
                        )
                    }
                }
                else -> Unit
            }
            if (apiKey.trim() == settings.value.openRouterApiKey.trim() && apiKey.isNotBlank()) {
                _savedKeyHealthState.value = when (status) {
                    is ApiKeyVerificationStatus.Valid -> SavedApiKeyHealthState.Verified(
                        providerLabel = ConfiguredCloudProvider.OPENROUTER.displayName,
                        maskedKey = com.example.data.preferences.SecureCredentialCipher.maskSecret(apiKey),
                        detail = status.detail,
                        latencyMs = status.latencyMs,
                        httpStatusCode = status.httpStatusCode,
                        usageHeaders = status.usageHeaders
                    )
                    is ApiKeyVerificationStatus.Invalid -> SavedApiKeyHealthState.Error(
                        providerLabel = ConfiguredCloudProvider.OPENROUTER.displayName,
                        maskedKey = com.example.data.preferences.SecureCredentialCipher.maskSecret(apiKey),
                        message = status.errorMessage,
                        latencyMs = status.latencyMs,
                        httpStatusCode = status.httpStatusCode,
                        usageHeaders = status.usageHeaders
                    )
                    else -> _savedKeyHealthState.value
                }
            }
        }
    }

    private suspend fun recordApiValidationTelemetry(
        providerName: String,
        httpStatusCode: Int?,
        latencyMs: Long?,
        parsedHeaders: ParsedUsageHeaders?
    ) {
        usageTelemetryMutex.withLock {
            val latest = settingsDataStore.apiUsageSummaryFlow.first()
            val updated = ApiUsageHeaderParser.applyValidationResponse(
                current = latest,
                providerName = providerName,
                httpStatusCode = httpStatusCode,
                latencyMs = latencyMs,
                parsed = parsedHeaders
            )
            settingsDataStore.saveApiUsageSummary(updated)
        }
    }

    private suspend fun recordCloudGenerationTelemetry(
        providerName: String,
        tokensUsed: Long?,
        latencyMs: Long
    ) {
        usageTelemetryMutex.withLock {
            val latest = settingsDataStore.apiUsageSummaryFlow.first()
            val updated = ApiUsageHeaderParser.applyGenerationUsage(
                current = latest,
                providerName = providerName,
                tokensUsed = tokensUsed,
                latencyMs = latencyMs
            )
            settingsDataStore.saveApiUsageSummary(updated)
        }
    }

    fun updateMonthlyUsageThreshold(
        enabled: Boolean,
        metric: ApiUsageLimitMetric,
        monthlyLimit: Long,
        warningThresholdPercent: Int
    ) {
        if (monthlyLimit <= 0L) {
            _snackbarMessage.value = "Monthly API limit must be greater than 0."
            return
        }
        viewModelScope.launch {
            settingsDataStore.updateMonthlyUsageThreshold(
                enabled = enabled,
                metric = metric,
                monthlyLimit = monthlyLimit,
                warningThresholdPercent = warningThresholdPercent
            )
            _snackbarMessage.value = if (enabled) {
                "Monthly usage warning set (${warningThresholdPercent}% of $monthlyLimit ${metric.unitLabel})"
            } else {
                "Monthly API usage alerts disabled"
            }
        }
    }

    fun resetApiUsageStats() {
        viewModelScope.launch {
            usageTelemetryMutex.withLock {
                settingsDataStore.clearApiUsageSummary()
            }
            _snackbarMessage.value = "API usage statistics reset"
        }
    }

    fun resetCredentialVerification() {
        _geminiVerificationState.value = ApiKeyVerificationStatus.Idle
        _openRouterVerificationState.value = ApiKeyVerificationStatus.Idle
    }

    fun updateHuggingFaceToken(token: String) {
        viewModelScope.launch {
            settingsDataStore.updateHuggingFaceToken(token)
            _modelVerificationStates.update { map ->
                map.filterValues { it !is ModelLinkVerificationState.Unreachable }
            }
        }
    }

    fun refreshDeviceHardwareProfile() {
        _deviceHardwareProfile.value = DeviceHardwareProfiler.inspectDevice(getApplication())
    }

    fun verifyAllPresetModelLinks(forceRefresh: Boolean = false) {
        if (!forceRefresh && verifyAllLinksJob?.isActive == true) return
        val currentMap = _modelVerificationStates.value
        val presetsToVerify = LocalModelCatalog.allPresets.filter { preset ->
            if (forceRefresh) true
            else {
                val existing = currentMap[preset.id]
                existing == null || existing is ModelLinkVerificationState.Idle
            }
        }
        if (presetsToVerify.isEmpty()) return

        verifyAllLinksJob?.cancel()
        val hfToken = settings.value.huggingFaceToken.trim()
        verifyAllLinksJob = viewModelScope.launch {
            _modelVerificationStates.update { map ->
                map + presetsToVerify.associate { it.id to ModelLinkVerificationState.Verifying }
            }
            coroutineScope {
                presetsToVerify.map { preset ->
                    async {
                        val result = HuggingFaceModelVerifier.verifyRepoAndResolveLink(
                            repoId = preset.repoId,
                            huggingFaceToken = hfToken,
                            preferredVariantHint = preset.preferredVariantHint
                        )
                        _modelVerificationStates.update { map ->
                            map + (preset.id to result)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun verifyPresetModelLink(presetId: String, forceRefresh: Boolean = false) {
        val preset = LocalModelCatalog.findById(presetId) ?: return
        val existing = _modelVerificationStates.value[presetId]
        if (!forceRefresh && (existing is ModelLinkVerificationState.Verified || existing is ModelLinkVerificationState.Verifying)) {
            return
        }
        val hfToken = settings.value.huggingFaceToken.trim()
        viewModelScope.launch {
            _modelVerificationStates.update { map ->
                map + (presetId to ModelLinkVerificationState.Verifying)
            }
            val result = HuggingFaceModelVerifier.verifyRepoAndResolveLink(
                repoId = preset.repoId,
                huggingFaceToken = hfToken,
                preferredVariantHint = preset.preferredVariantHint
            )
            _modelVerificationStates.update { map ->
                map + (presetId to result)
            }
        }
    }

    fun dismissLocalModelDownloadError() {
        if (_localModelDownloadState.value is LocalModelDownloadState.Error) {
            _localModelDownloadState.value = LocalModelDownloadState.Idle
        }
    }

    fun cancelLocalModelDownload() {
        modelDownloadJob?.cancel()
        modelDownloadJob = null
        _localModelDownloadState.value = LocalModelDownloadState.Idle
        _snackbarMessage.value = "Model download cancelled"
        val app = getApplication<Application>()
        BackgroundTaskManager.cancelTask(app)
    }

    fun downloadAndInitializeLocalModel(
        url: String,
        displayName: String,
        fileNameHint: String,
        expectedSizeBytes: Long = 0L,
        presetId: String? = null
    ) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _localModelDownloadState.value = LocalModelDownloadState.Error(
                "Please provide a valid HTTP/HTTPS download URL for the .task or .bin model."
            )
            return
        }
        val hfToken = settings.value.huggingFaceToken.trim()
        val app = getApplication<Application>()
        modelDownloadJob?.cancel()
        BackgroundTaskManager.startTask(app, "Downloading $displayName", "Connecting to repository…")
        modelDownloadJob = BackgroundTaskManager.scope.launch {
            var resolvedUrl = cleanUrl
            var resolvedFileName = fileNameHint
            var resolvedExpectedBytes = expectedSizeBytes

            if (presetId != null) {
                val preset = LocalModelCatalog.findById(presetId)
                if (preset != null) {
                    val cached = _modelVerificationStates.value[presetId]
                    val verifiedState = if (cached is ModelLinkVerificationState.Verified) {
                        cached
                    } else {
                        _modelVerificationStates.update { it + (presetId to ModelLinkVerificationState.Verifying) }
                        val checked = HuggingFaceModelVerifier.verifyRepoAndResolveLink(
                            repoId = preset.repoId,
                            huggingFaceToken = hfToken,
                            preferredVariantHint = preset.preferredVariantHint
                        )
                        _modelVerificationStates.update { it + (presetId to checked) }
                        checked
                    }

                    when (verifiedState) {
                        is ModelLinkVerificationState.Verified -> {
                            resolvedUrl = verifiedState.resolvedUrl
                            resolvedFileName = verifiedState.resolvedFileName
                            if (verifiedState.exactSizeBytes > 0L) {
                                resolvedExpectedBytes = verifiedState.exactSizeBytes
                            }
                        }
                        is ModelLinkVerificationState.Unreachable -> {
                            val errMsg = if (verifiedState.requiresLicenseAcceptance) {
                                verifiedState.reason
                            } else {
                                "Repository ${preset.repoId} is currently unreachable: ${verifiedState.reason}"
                            }
                            _localModelDownloadState.value = LocalModelDownloadState.Error(errMsg)
                            BackgroundTaskManager.failTask(app, "Downloading $displayName", errMsg)
                            return@launch
                        }
                        else -> Unit
                    }
                }
            }

            _localModelDownloadState.value = LocalModelDownloadState.Downloading(
                modelName = displayName,
                downloadedBytes = 0L,
                totalBytes = resolvedExpectedBytes,
                percent = 0
            )
            try {
                generatorRepository.close()
                val (path, _, actualSize) = storageRepository.downloadModelFromUrl(
                    url = resolvedUrl,
                    fileNameHint = resolvedFileName,
                    expectedSizeBytes = resolvedExpectedBytes,
                    huggingFaceToken = hfToken,
                    onProgress = { downloaded, total ->
                        val pct = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                        _localModelDownloadState.value = LocalModelDownloadState.Downloading(
                            modelName = displayName,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            percent = pct
                        )
                        val downMb = downloaded / (1024L * 1024L)
                        val totMb = total / (1024L * 1024L)
                        BackgroundTaskManager.updateProgress(
                            app,
                            "Downloading $displayName",
                            "$downMb MB / $totMb MB ($pct%)",
                            pct / 100f
                        )
                    }
                )
                settingsDataStore.updateOnDeviceModel(path, displayName, actualSize)
                _localModelDownloadState.value = LocalModelDownloadState.Initializing(displayName)
                BackgroundTaskManager.updateProgress(
                    app,
                    "Downloading $displayName",
                    "Initializing MediaPipe LLM…",
                    0.96f
                )
                generatorRepository.initializeLocalModel(path)
                refreshDeviceHardwareProfile()
                _localModelDownloadState.value = LocalModelDownloadState.Idle
                _snackbarMessage.value = "Model downloaded & initialized offline: $displayName"
                BackgroundTaskManager.finishTask(app, "Model Download Ready", "$displayName initialized offline.")
            } catch (ce: CancellationException) {
                _localModelDownloadState.value = LocalModelDownloadState.Idle
                BackgroundTaskManager.cancelTask(app)
                throw ce
            } catch (ex: Exception) {
                val err = ex.localizedMessage ?: "Failed to download or initialize local model."
                _localModelDownloadState.value = LocalModelDownloadState.Error(err)
                BackgroundTaskManager.failTask(app, "Downloading $displayName", err)
            }
        }
    }

    fun importLocalModelFile(uri: Uri) {
        viewModelScope.launch {
            _isImportingModel.value = true
            try {
                generatorRepository.close()
                val (path, displayName, sizeBytes) = storageRepository.importModelFromUri(uri)
                settingsDataStore.updateOnDeviceModel(path, displayName, sizeBytes)
                refreshDeviceHardwareProfile()
                _snackbarMessage.value = "Loaded on-device model: $displayName"
            } catch (ex: Exception) {
                _errorDialogMessage.value = "Failed to import model file: ${ex.localizedMessage ?: "Unknown error"}"
            } finally {
                _isImportingModel.value = false
            }
        }
    }

    fun unloadAndDeleteLocalModel() {
        modelDownloadJob?.cancel()
        modelDownloadJob = null
        _localModelDownloadState.value = LocalModelDownloadState.Idle
        viewModelScope.launch {
            generatorRepository.close()
            storageRepository.deleteLocalModelFiles()
            settingsDataStore.clearOnDeviceModel()
            refreshDeviceHardwareProfile()
            _snackbarMessage.value = "Unloaded & deleted local model from storage"
        }
    }

    fun generateApp(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _snackbarMessage.value = "Describe your Android app idea first."
            return
        }

        generationJob?.cancel()
        val currentSettings = settings.value
        val app = getApplication<Application>()
        val engineDisplayName = when (currentSettings.aiEngineMode) {
            AiEngineMode.ON_DEVICE_MEDIAPIPE ->
                currentSettings.localModelDisplayName.ifBlank { "MediaPipe Local AI" }
            AiEngineMode.CLOUD_OPENROUTER ->
                currentSettings.openRouterModel
            AiEngineMode.LOCAL_SERVER ->
                currentSettings.localServerModel.ifBlank { "Local Server" }
        }
        BackgroundTaskManager.startTask(app, "AI Code Factory", "Initializing $engineDisplayName…")

        generationJob = BackgroundTaskManager.scope.launch {
            val generationStartMs = System.currentTimeMillis()
            _generationState.value = GenerationUiState.Generating(
                stepIndex = 0,
                stepMessage = "Initializing $engineDisplayName…",
                engineLabel = currentSettings.aiEngineMode.shortLabel,
                buildPhase = BuildPhase.PROMPTING,
                progress = 0.06f
            )
            try {
                val result = generatorRepository.generateApp(
                    prompt = cleanPrompt,
                    settings = currentSettings,
                    onProgress = { step, message ->
                        _generationState.update { existing ->
                            val prev = existing as? GenerationUiState.Generating
                            val phase = when {
                                step <= 0 -> BuildPhase.PROMPTING
                                step == 1 -> BuildPhase.GENERATING_CODE
                                else -> BuildPhase.PARSING_FILES
                            }
                            val milestoneProgress = when (phase) {
                                BuildPhase.PROMPTING -> 0.12f
                                BuildPhase.GENERATING_CODE -> 0.18f
                                BuildPhase.PARSING_FILES -> 0.85f
                                BuildPhase.PACKAGING_APK -> 0.94f
                            }
                            BackgroundTaskManager.updateProgress(app, "AI Code Factory", message, milestoneProgress)
                            GenerationUiState.Generating(
                                stepIndex = step,
                                stepMessage = message,
                                engineLabel = currentSettings.aiEngineMode.shortLabel,
                                streamingPreview = prev?.streamingPreview.orEmpty(),
                                buildPhase = phase,
                                progress = maxOf(prev?.progress ?: 0f, milestoneProgress),
                                charsReceived = prev?.charsReceived ?: 0,
                                tokensReceived = prev?.tokensReceived ?: 0,
                                activeFileLabel = prev?.activeFileLabel
                            )
                        }
                    },
                    onStreamPreview = { partialCode ->
                        val sanitized = FactoryTelemetryCalculator.sanitizeStreamingCode(partialCode)
                        val activeFile = FactoryTelemetryCalculator.extractActiveFileLabel(partialCode)
                        val charCount = partialCode.length
                        _generationState.update { existing ->
                            val prev = existing as? GenerationUiState.Generating
                            if (prev != null) {
                                val nextProgress = maxOf(
                                    prev.progress,
                                    FactoryTelemetryCalculator.calculateStreamProgress(charCount)
                                )
                                val dynamicStepMsg = if (activeFile != null) {
                                    "Streaming $activeFile ($charCount chars)…"
                                } else {
                                    "Streaming Kotlin & Compose ($charCount chars)…"
                                }
                                BackgroundTaskManager.updateProgress(app, "AI Code Factory", dynamicStepMsg, nextProgress)
                                prev.copy(
                                    stepIndex = 1,
                                    stepMessage = dynamicStepMsg,
                                    streamingPreview = sanitized,
                                    buildPhase = BuildPhase.GENERATING_CODE,
                                    progress = nextProgress,
                                    charsReceived = charCount,
                                    tokensReceived = prev.tokensReceived + 1,
                                    activeFileLabel = activeFile ?: prev.activeFileLabel
                                )
                            } else {
                                existing
                            }
                        }
                    }
                )

                val totalGenerationLatencyMs = (System.currentTimeMillis() - generationStartMs).coerceAtLeast(150L)

                if (currentSettings.aiEngineMode == AiEngineMode.CLOUD_OPENROUTER) {
                    recordCloudGenerationTelemetry(
                        providerName = result.modelUsed,
                        tokensUsed = result.tokensUsed,
                        latencyMs = totalGenerationLatencyMs
                    )
                }

                val project = GitHubWorkflowInjector.injectBuildApkWorkflow(
                    StudioProject(
                        id = UUID.randomUUID().toString(),
                        appName = result.payload.appName,
                        packageName = result.payload.packageName,
                        prompt = cleanPrompt,
                        engineMode = currentSettings.aiEngineMode,
                        modelUsed = result.modelUsed,
                        files = result.payload.files,
                        lastGenerationLatencyMs = totalGenerationLatencyMs
                    )
                )

                val totalFiles = project.files.size.coerceAtLeast(1)
                project.files.forEachIndexed { idx, file ->
                    val shortName = file.path.substringAfterLast('/')
                    val fileProgress = FactoryTelemetryCalculator.calculateFileWriteProgress(
                        writtenFiles = idx + 1,
                        totalFiles = totalFiles
                    )
                    BackgroundTaskManager.updateProgress(
                        app,
                        "AI Code Factory",
                        "Writing $shortName (${idx + 1}/$totalFiles files)…",
                        fileProgress
                    )
                    _generationState.update { existing ->
                        val prev = existing as? GenerationUiState.Generating
                        prev?.copy(
                            stepIndex = 2,
                            stepMessage = "Writing $shortName (${idx + 1}/$totalFiles files)…",
                            buildPhase = BuildPhase.PARSING_FILES,
                            progress = maxOf(prev.progress, fileProgress),
                            activeFileLabel = shortName
                        ) ?: existing
                    }
                }

                _generationState.update { existing ->
                    val prev = existing as? GenerationUiState.Generating
                    prev?.copy(
                        stepIndex = 2,
                        stepMessage = "Packaging project workspace & static checks…",
                        buildPhase = BuildPhase.PACKAGING_APK,
                        progress = 0.98f
                    ) ?: existing
                }

                val saved = storageRepository.saveProject(project)
                _projects.value = storageRepository.loadAllProjects()
                _activeProject.value = saved
                loadSnapshotsForProject(saved.id)
                _consoleLogs.value = emptyList()
                appendConsoleLog(
                    level = ConsoleLogLevel.INFO,
                    tag = "AIEngine",
                    message = "Generated '${saved.appName}' (${saved.files.size} files) in ${totalGenerationLatencyMs}ms using ${result.modelUsed}."
                )
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(saved))
                refreshGitStatus(saved.id)
                _generationState.value = GenerationUiState.Success(
                    projectId = saved.id,
                    appName = saved.appName
                )
                BackgroundTaskManager.finishTask(app, "AI Code Factory Ready", "Generated '${saved.appName}' successfully.")
            } catch (_: CancellationException) {
                _generationState.value = GenerationUiState.Idle
                _snackbarMessage.value = "Generation cancelled"
                BackgroundTaskManager.cancelTask(app)
            } catch (ex: Exception) {
                val msg = ex.localizedMessage ?: "App generation failed."
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "AIEngine",
                    message = "Generation error: $msg"
                )
                _generationState.value = GenerationUiState.Error(msg)
                _errorDialogMessage.value = msg
                BackgroundTaskManager.failTask(app, "AI Code Factory", msg)
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _generationState.value = GenerationUiState.Idle
        val app = getApplication<Application>()
        BackgroundTaskManager.cancelTask(app)
    }

    fun createProjectFromTemplate(
        template: StarterTemplate,
        customAppName: String,
        customPackageName: String,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val payload = StarterTemplateCatalog.buildProjectPayload(
                    template = template,
                    customAppName = customAppName,
                    customPackageName = customPackageName
                )
                val project = GitHubWorkflowInjector.injectBuildApkWorkflow(
                    StudioProject(
                        id = UUID.randomUUID().toString(),
                        appName = payload.appName,
                        packageName = payload.packageName,
                        prompt = "Starter Template: ${template.title} — ${template.subtitle}",
                        engineMode = settings.value.aiEngineMode,
                        modelUsed = "Starter Template • ${template.title}",
                        files = payload.files
                    )
                )
                val saved = storageRepository.saveProject(project)
                _projects.value = storageRepository.loadAllProjects()
                _activeProject.value = saved
                _consoleLogs.value = emptyList()
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "StarterTemplate",
                    message = "Created '${saved.appName}' (${saved.files.size} files) from '${template.title}'."
                )
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(saved))
                refreshGitStatus(saved.id)
                _snackbarMessage.value = "Created '${saved.appName}' from ${template.title}"
                onCreated(saved.id)
            } catch (ex: Exception) {
                _errorDialogMessage.value = ex.localizedMessage ?: "Failed to create project from template."
            }
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            val loaded = storageRepository.loadProject(projectId)
            if (loaded != null) {
                // Ensure workspace files exist on disk
                val synced = storageRepository.saveProject(loaded)
                _activeProject.value = synced
                _consoleLogs.value = emptyList()
                appendConsoleLog(
                    level = ConsoleLogLevel.INFO,
                    tag = "Workspace",
                    message = "Opened project '${synced.appName}' (${synced.packageName})"
                )
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(synced))
                refreshGitStatus(synced.id)
                loadSnapshotsForProject(synced.id)
            } else {
                _errorDialogMessage.value = "Project not found on disk."
            }
        }
    }

    fun loadSnapshotsForProject(projectId: String) {
        viewModelScope.launch {
            val list = storageRepository.listSnapshots(projectId)
            if (list.isEmpty()) {
                val current = _activeProject.value
                if (current != null && current.id == projectId && current.files.isNotEmpty()) {
                    val initial = storageRepository.saveSnapshot(
                        projectId = projectId,
                        versionLabel = "v1.0",
                        summary = "Initial project generation",
                        files = current.files
                    )
                    _projectSnapshots.value = listOf(initial)
                    return@launch
                }
            }
            _projectSnapshots.value = list
        }
    }

    fun createProjectSnapshot(summary: String, customLabel: String? = null) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val existing = storageRepository.listSnapshots(current.id)
            val nextVersion = customLabel ?: "v1.${existing.size}"
            val snapshot = storageRepository.saveSnapshot(
                projectId = current.id,
                versionLabel = nextVersion,
                summary = summary,
                files = current.files
            )
            _projectSnapshots.value = listOf(snapshot) + existing
            appendConsoleLog(
                level = ConsoleLogLevel.INFO,
                tag = "Snapshot",
                message = "Created snapshot $nextVersion: '$summary' (${current.files.size} files)."
            )
            _snackbarMessage.value = "Saved snapshot $nextVersion"
        }
    }

    fun restoreProjectSnapshot(snapshot: ProjectSnapshot) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val restored = current.copy(
                files = snapshot.files,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            val saved = storageRepository.saveProject(restored)
            _activeProject.value = saved
            appendConsoleLog(
                level = ConsoleLogLevel.WARN,
                tag = "Snapshot",
                message = "Restored project to ${snapshot.versionLabel} ('${snapshot.summary}')."
            )
            appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(saved))
            _snackbarMessage.value = "Restored to ${snapshot.versionLabel}"
        }
    }

    fun updateProjectBranding(
        newAppName: String,
        newPackageId: String,
        newVersionName: String,
        launcherIconBase64: String?,
        launcherIconVectorXml: String?
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            autoSaver.flushNowBlocking()
            val latest = _activeProject.value ?: current
            val cleanAppName = newAppName.trim().ifBlank { latest.appName }
            val cleanPackageId = newPackageId.trim().ifBlank { latest.packageName }
            val cleanVersionName = newVersionName.trim().ifBlank { "1.0.0" }
            val xmlEscapedAppName = cleanAppName
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "\\'")

            val fileMap = latest.files.map { file ->
                var content = file.content
                when {
                    file.path.endsWith("strings.xml", ignoreCase = true) -> {
                        if (content.contains("<string name=\"app_name\">")) {
                            content = content.replace(
                                Regex("<string name=\"app_name\">.*?</string>"),
                                "<string name=\"app_name\">$xmlEscapedAppName</string>"
                            )
                        } else if (content.contains("</resources>")) {
                            content = content.replace(
                                "</resources>",
                                "    <string name=\"app_name\">$xmlEscapedAppName</string>\n</resources>"
                            )
                        }
                    }
                    file.path.endsWith("app/build.gradle.kts") || file.path.endsWith("app/build.gradle") -> {
                        if (cleanPackageId.isNotBlank()) {
                            content = content.replace(
                                Regex("""applicationId\s*=?\s*["'].*?["']"""),
                                if (file.path.endsWith(".kts")) "applicationId = \"$cleanPackageId\""
                                else "applicationId \"$cleanPackageId\""
                            )
                            content = content.replace(
                                Regex("""namespace\s*=?\s*["'].*?["']"""),
                                if (file.path.endsWith(".kts")) "namespace = \"$cleanPackageId\""
                                else "namespace \"$cleanPackageId\""
                            )
                        }
                        if (cleanVersionName.isNotBlank()) {
                            content = content.replace(
                                Regex("""versionName\s*=?\s*["'].*?["']"""),
                                if (file.path.endsWith(".kts")) "versionName = \"$cleanVersionName\""
                                else "versionName \"$cleanVersionName\""
                            )
                        }
                    }
                    file.path.endsWith("AndroidManifest.xml", ignoreCase = true) -> {
                        if (cleanPackageId.isNotBlank() && content.contains("package=")) {
                            content = content.replace(
                                Regex("package=\"[^\"]*\""),
                                "package=\"$cleanPackageId\""
                            )
                        }
                    }
                }
                file.copy(content = content)
            }.associateBy { it.path }.toMutableMap()

            // Ensure strings.xml exists with updated app_name
            if (fileMap.keys.none { it.endsWith("strings.xml", ignoreCase = true) }) {
                fileMap["app/src/main/res/values/strings.xml"] = GeneratedFile(
                    path = "app/src/main/res/values/strings.xml",
                    content = """
                        <resources>
                            <string name="app_name">$xmlEscapedAppName</string>
                        </resources>
                    """.trimIndent()
                )
            }

            var appliedIconLabel = "unchanged"
            val cleanBase64 = launcherIconBase64?.trim()?.takeIf { it.isNotBlank() }
            val cleanVectorXml = launcherIconVectorXml?.trim()?.takeIf { it.startsWith("<vector") }
            if (cleanBase64 != null) {
                val encodedAsset = com.example.ui.assets.ProjectResAssetManager.BINARY_CONTENT_PREFIX + cleanBase64
                fileMap.remove("app/src/main/res/drawable/ic_launcher_foreground.xml")
                fileMap["app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"] = GeneratedFile(
                    path = "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
                    content = encodedAsset
                )
                fileMap["app/src/main/res/drawable/ic_launcher_foreground.png"] = GeneratedFile(
                    path = "app/src/main/res/drawable/ic_launcher_foreground.png",
                    content = encodedAsset
                )
                appliedIconLabel = "custom PNG icon"
            } else if (cleanVectorXml != null) {
                fileMap.remove("app/src/main/res/drawable/ic_launcher_foreground.png")
                fileMap["app/src/main/res/drawable/ic_launcher_foreground.xml"] = GeneratedFile(
                    path = "app/src/main/res/drawable/ic_launcher_foreground.xml",
                    content = cleanVectorXml
                )
                appliedIconLabel = "vector drawable icon"
            }

            val updatedProject = latest.copy(
                appName = cleanAppName,
                packageName = cleanPackageId,
                files = fileMap.values.sortedBy { it.path },
                updatedAtEpochMs = System.currentTimeMillis()
            )

            val saved = storageRepository.saveProject(updatedProject)
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            createProjectSnapshot("Updated branding to '$cleanAppName' ($cleanPackageId v$cleanVersionName, $appliedIconLabel)")
            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "BrandingStudio",
                message = "Updated APK identity: '$cleanAppName', package='$cleanPackageId', version='$cleanVersionName', icon=$appliedIconLabel."
            )
            _snackbarMessage.value = "App branding & icon saved ($appliedIconLabel)"
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            storageRepository.deleteProject(projectId)
            if (_activeProject.value?.id == projectId) {
                _activeProject.value = null
            }
            _projects.value = storageRepository.loadAllProjects()
            _snackbarMessage.value = "Project deleted"
        }
    }

    fun saveEditedFile(path: String, newContent: String) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val updatedFiles = current.files.map { file ->
                if (file.path == path) file.copy(content = newContent) else file
            }
            val updatedDraft = current.copy(files = updatedFiles)
            _activeProject.value = updatedDraft
            autoSaver.scheduleSave(updatedDraft)
            val saved = autoSaver.flushNowBlocking() ?: storageRepository.saveProject(updatedDraft)
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            val fileDiagnostics = ProjectStaticAnalyzer.analyzeSingleFile(GeneratedFile(path, newContent))
            if (fileDiagnostics.isEmpty()) {
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "KotlinCompiler",
                    message = "Saved & verified $path — 0 syntax errors.",
                    filePath = path
                )
            } else {
                appendConsoleEntries(fileDiagnostics)
            }
            _snackbarMessage.value = "Saved $path"
        }
    }

    fun updateFileInMemoryForPreview(path: String, newContent: String) {
        val current = _activeProject.value ?: return
        val existingContent = current.files.firstOrNull { it.path == path }?.content
        if (existingContent == newContent) return

        val updatedFiles = current.files.map { file ->
            if (file.path == path) file.copy(content = newContent) else file
        }
        val updatedProject = current.copy(files = updatedFiles)
        _activeProject.value = updatedProject
        autoSaver.scheduleSave(updatedProject)
    }

    fun flushPendingAutoSave() {
        autoSaver.flushNow()
    }

    fun analyzeCurrentFileForRefactor(filePath: String, currentCode: String) {
        if (!filePath.endsWith(".kt", ignoreCase = true)) {
            _snackbarMessage.value = "Select a Kotlin (.kt) file to run AI Compose Refactor."
            return
        }
        refactorJob?.cancel()
        val currentSettings = settings.value
        refactorJob = viewModelScope.launch {
            _refactorState.value = RefactorUiState.Analyzing(
                filePath = filePath,
                statusMessage = "Analyzing ${filePath.substringAfterLast('/')} with Gemini…"
            )
            appendConsoleLog(
                level = ConsoleLogLevel.INFO,
                tag = "GeminiRefactor",
                message = "Analyzing $filePath for Compose recomposition & idiomatic improvements…",
                filePath = filePath
            )
            try {
                val result = refactorRepository.analyzeAndRefactorKotlinFile(
                    filePath = filePath,
                    sourceCode = currentCode,
                    settings = currentSettings,
                    onStatusUpdate = { statusMsg ->
                        _refactorState.value = RefactorUiState.Analyzing(
                            filePath = filePath,
                            statusMessage = statusMsg
                        )
                    }
                )
                _refactorState.value = RefactorUiState.ResultReady(result)
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "GeminiRefactor",
                    message = "Found ${result.suggestions.size} Compose optimization(s) in ${filePath.substringAfterLast('/')} via ${result.modelUsed}.",
                    filePath = filePath
                )
            } catch (_: CancellationException) {
                _refactorState.value = RefactorUiState.Idle
            } catch (ex: Exception) {
                val msg = ex.localizedMessage ?: "AI Refactor analysis failed."
                _refactorState.value = RefactorUiState.Error(msg)
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "GeminiRefactor",
                    message = "Refactor analysis failed for $filePath: $msg",
                    filePath = filePath
                )
            }
        }
    }

    fun dismissRefactorSheet() {
        refactorJob?.cancel()
        refactorJob = null
        _refactorState.value = RefactorUiState.Idle
    }

    fun applyRefactoredFile(
        filePath: String,
        refactoredCode: String,
        onApplied: (targetPath: String, updatedContent: String) -> Unit = { _, _ -> }
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val updatedFiles = current.files.map { file ->
                if (file.path == filePath) file.copy(content = refactoredCode) else file
            }
            val saved = storageRepository.saveProject(current.copy(files = updatedFiles))
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            _refactorState.value = RefactorUiState.Idle
            onApplied(filePath, refactoredCode)
            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "GeminiRefactor",
                message = "Applied AI refactored Compose code to $filePath",
                filePath = filePath
            )
            appendConsoleEntries(ProjectStaticAnalyzer.analyzeSingleFile(GeneratedFile(filePath, refactoredCode)))
            _snackbarMessage.value = "Applied AI refactor to ${filePath.substringAfterLast('/')}"
        }
    }


    fun addNewFile(path: String, initialContent: String) {
        val current = _activeProject.value ?: return
        val cleanPath = path.trim().removePrefix("/")
        if (cleanPath.isBlank()) return
        if (current.files.any { it.path == cleanPath }) {
            _snackbarMessage.value = "File already exists: $cleanPath"
            return
        }
        viewModelScope.launch {
            val updatedFiles = (current.files + GeneratedFile(cleanPath, initialContent)).sortedBy { it.path }
            val saved = storageRepository.saveProject(current.copy(files = updatedFiles))
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            appendConsoleLog(
                level = ConsoleLogLevel.INFO,
                tag = "Workspace",
                message = "Created new source file: $cleanPath",
                filePath = cleanPath
            )
            _snackbarMessage.value = "Created $cleanPath"
        }
    }

    fun applyThemeSeedColor(
        seedColor: androidx.compose.ui.graphics.Color,
        onThemeUpdated: (targetPath: String, newContent: String) -> Unit = { _, _ -> }
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val (targetPath, themeContent) = com.example.preview.Material3SeedPaletteGenerator.buildThemeKtContent(
                project = current,
                seedColor = seedColor
            )
            val existingIndex = current.files.indexOfFirst {
                it.path.equals(targetPath, ignoreCase = true) ||
                    it.path.substringAfterLast('/').equals("Theme.kt", ignoreCase = true)
            }
            val finalPath = if (existingIndex >= 0) current.files[existingIndex].path else targetPath
            val updatedFiles = if (existingIndex >= 0) {
                current.files.mapIndexed { idx, file ->
                    if (idx == existingIndex) file.copy(content = themeContent) else file
                }
            } else {
                (current.files + GeneratedFile(finalPath, themeContent)).sortedBy { it.path }
            }

            val saved = storageRepository.saveProject(current.copy(files = updatedFiles))
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            onThemeUpdated(finalPath, themeContent)
            val hex = com.example.preview.Material3SeedPaletteGenerator.formatHex6(seedColor)
            appendConsoleLog(
                level = ConsoleLogLevel.INFO,
                tag = "Material3Theme",
                message = "Updated LightColorScheme & DarkColorScheme in $finalPath from seed $hex",
                filePath = finalPath
            )
            _snackbarMessage.value = "Updated $finalPath with seed $hex"
        }
    }

    fun applyDependencyChanges(
        enabledGroupIds: Set<String>,
        enabledCustomDeclarations: Set<String>,
        onGradleUpdated: (targetPath: String, newContent: String) -> Unit = { _, _ -> }
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val targetPath = com.example.data.gradle.GradleDependencyManager.resolveTargetGradlePath(current)
            val existingFile = current.files.firstOrNull { it.path == targetPath }
            val existingContent = existingFile?.content ?: """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("org.jetbrains.kotlin.plugin.compose")
                }

                android {
                    namespace = "${current.packageName}"
                    compileSdk = 35
                }

                dependencies {
                }
            """.trimIndent()

            val updatedGradleContent = com.example.data.gradle.GradleDependencyManager.applyDependencyChanges(
                gradleContent = existingContent,
                enabledGroupIds = enabledGroupIds,
                enabledCustomDeclarations = enabledCustomDeclarations
            )

            val updatedFiles = if (existingFile != null) {
                current.files.map { file ->
                    if (file.path == targetPath) file.copy(content = updatedGradleContent) else file
                }
            } else {
                (current.files + GeneratedFile(targetPath, updatedGradleContent)).sortedBy { it.path }
            }

            val saved = storageRepository.saveProject(current.copy(files = updatedFiles))
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            onGradleUpdated(targetPath, updatedGradleContent)

            val enabledNames = com.example.data.gradle.GradleDependencyManager.curatedGroups
                .filter { it.id in enabledGroupIds }
                .joinToString { it.title.substringBefore(" (") }
                .ifBlank { "Core dependencies only" }

            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "GradleDeps",
                message = "Updated $targetPath — Active bundles (${enabledGroupIds.size}): $enabledNames",
                filePath = targetPath
            )
            _snackbarMessage.value = "Updated dependencies in $targetPath"
        }
    }

    fun injectRoomSqlTable(
        entityName: String,
        tableName: String,
        columns: List<SqlColumnSpec>,
        onInjected: (generatedFilePath: String, generatedCode: String) -> Unit = { _, _ -> }
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val (generatedPath, generatedCode) = RoomCodeGenerator.generateRoomDatabaseFile(
                packageName = current.packageName,
                entityNameInput = entityName,
                tableNameInput = tableName,
                columns = columns
            )

            // Also ensure Room + KSP dependencies are enabled in app/build.gradle.kts
            val gradlePath = com.example.data.gradle.GradleDependencyManager.resolveTargetGradlePath(current)
            val existingGradle = current.files.firstOrNull { it.path == gradlePath }?.content ?: """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("org.jetbrains.kotlin.plugin.compose")
                }

                android {
                    namespace = "${current.packageName}"
                    compileSdk = 35
                }

                dependencies {
                }
            """.trimIndent()

            val inspectedGradle = com.example.data.gradle.GradleDependencyManager.inspectGradleContent(
                targetPath = gradlePath,
                gradleContent = existingGradle
            )
            val currentEnabledGroups = inspectedGradle.groups.filter { it.isEnabled }.map { it.group.id }.toSet()
            val currentEnabledCustom = inspectedGradle.customDependencies.filter { it.isEnabled }.map { it.rawDeclaration }.toSet()
            val updatedGradleContent = com.example.data.gradle.GradleDependencyManager.applyDependencyChanges(
                gradleContent = existingGradle,
                enabledGroupIds = currentEnabledGroups + "room",
                enabledCustomDeclarations = currentEnabledCustom
            )

            val fileMap = current.files.associateBy { it.path }.toMutableMap()
            fileMap[generatedPath] = GeneratedFile(generatedPath, generatedCode)
            fileMap[gradlePath] = GeneratedFile(gradlePath, updatedGradleContent)

            val saved = storageRepository.saveProject(
                current.copy(files = fileMap.values.sortedBy { it.path })
            )
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)

            storageRepository.sqlRepository.recordCustomTable(
                projectId = saved.id,
                entityName = RoomCodeGenerator.sanitizeEntityName(entityName),
                tableName = RoomCodeGenerator.sanitizeTableName(tableName),
                columns = columns,
                generatedFilePath = generatedPath
            )

            onInjected(generatedPath, generatedCode)
            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "RoomSQL",
                message = "Generated Room @Entity/${RoomCodeGenerator.sanitizeEntityName(entityName)} (`${RoomCodeGenerator.sanitizeTableName(tableName)}`) in $generatedPath & enabled Room + KSP in $gradlePath",
                filePath = generatedPath
            )
            _snackbarMessage.value = "Generated $generatedPath & enabled Room SQL"
        }
    }

    fun deleteCustomSqlTable(tableId: Int) {
        viewModelScope.launch {
            storageRepository.sqlRepository.deleteCustomTable(tableId)
            _snackbarMessage.value = "Removed SQL table entry"
        }
    }

    // --- Resource Asset Manager (res/drawable, res/mipmap, res/font) ---

    fun renameResAsset(
        oldPath: String,
        newBaseName: String,
        updateReferences: Boolean = true,
        onRenamed: (String) -> Unit = {}
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            autoSaver.flushNowBlocking()
            val latest = _activeProject.value ?: current
            val renameResult = com.example.ui.assets.ProjectResAssetManager.renameAssetAndReferences(
                files = latest.files,
                oldPath = oldPath,
                newBaseName = newBaseName,
                updateReferences = updateReferences
            )
            renameResult.onSuccess { res ->
                val saved = storageRepository.saveProject(latest.copy(files = res.updatedFiles))
                _activeProject.value = saved
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(saved.id)
                onRenamed(res.newPath)

                val refSummary = if (updateReferences && res.referencesUpdatedCount > 0) {
                    " and updated ${res.referencesUpdatedCount} R.*/@... reference(s)"
                } else ""
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "AssetManager",
                    message = "Renamed ${res.oldPath.substringAfterLast('/')} -> ${res.newPath.substringAfterLast('/')}$refSummary",
                    filePath = res.newPath
                )
                _snackbarMessage.value = "Renamed to ${res.newPath.substringAfterLast('/')}$refSummary"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Unable to rename asset."
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "AssetManager",
                    message = msg,
                    filePath = oldPath
                )
                _snackbarMessage.value = msg
            }
        }
    }

    fun deleteResAsset(path: String) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            autoSaver.flushNowBlocking()
            val latest = _activeProject.value ?: current
            val updatedFiles = latest.files.filterNot { it.path == path }
            if (updatedFiles.size == latest.files.size) return@launch

            val saved = storageRepository.saveProject(latest.copy(files = updatedFiles))
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            appendConsoleLog(
                level = ConsoleLogLevel.INFO,
                tag = "AssetManager",
                message = "Deleted resource asset $path"
            )
            _snackbarMessage.value = "Deleted ${path.substringAfterLast('/')}"
        }
    }

    fun importResImageFromUri(
        uri: Uri,
        targetResFolder: String = "drawable",
        onImported: (String) -> Unit = {}
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                autoSaver.flushNowBlocking()
                val latest = _activeProject.value ?: current
                val (displayName, mimeType, bytes) = storageRepository.readUriAsset(uri)
                val ext = when {
                    displayName.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp") ->
                        displayName.substringAfterLast('.', "").lowercase()
                    mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
                    mimeType?.contains("jpeg", ignoreCase = true) == true ||
                        mimeType?.contains("jpg", ignoreCase = true) == true -> "jpg"
                    else -> "png"
                }
                val safeBase = com.example.ui.assets.ProjectResAssetManager.sanitizeToResourceBaseName(
                    raw = displayName,
                    fallback = "img_imported"
                )
                val folder = targetResFolder.trim().removePrefix("/").ifBlank { "drawable" }
                val finalPath = buildUniqueResPath(
                    existingFiles = latest.files,
                    dir = "app/src/main/res/$folder",
                    baseName = safeBase,
                    ext = ext
                )
                val encoded = com.example.ui.assets.ProjectResAssetManager.encodeBinaryContent(bytes)
                val updatedFiles = (latest.files + GeneratedFile(finalPath, encoded)).sortedBy { it.path }
                val saved = storageRepository.saveProject(latest.copy(files = updatedFiles))
                _activeProject.value = saved
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(saved.id)
                onImported(finalPath)
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "AssetManager",
                    message = "Imported image $finalPath (${com.example.ui.assets.ProjectResAssetManager.formatByteSize(bytes.size.toLong())})",
                    filePath = finalPath
                )
                _snackbarMessage.value = "Imported ${finalPath.substringAfterLast('/')}"
            } catch (ex: Exception) {
                val msg = ex.localizedMessage ?: "Failed to import image asset."
                _errorDialogMessage.value = msg
            }
        }
    }

    fun importResFontFromUri(
        uri: Uri,
        onImported: (String) -> Unit = {}
    ) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                autoSaver.flushNowBlocking()
                val latest = _activeProject.value ?: current
                val (displayName, _, bytes) = storageRepository.readUriAsset(uri)
                val rawExt = displayName.substringAfterLast('.', "").lowercase()
                val ext = when {
                    rawExt in setOf("ttf", "otf", "ttc") -> rawExt
                    else -> "ttf"
                }
                val safeBase = com.example.ui.assets.ProjectResAssetManager.sanitizeToResourceBaseName(
                    raw = displayName,
                    fallback = "custom_font"
                )
                val finalPath = buildUniqueResPath(
                    existingFiles = latest.files,
                    dir = "app/src/main/res/font",
                    baseName = safeBase,
                    ext = ext
                )
                val encoded = com.example.ui.assets.ProjectResAssetManager.encodeBinaryContent(bytes)
                val updatedFiles = (latest.files + GeneratedFile(finalPath, encoded)).sortedBy { it.path }
                val saved = storageRepository.saveProject(latest.copy(files = updatedFiles))
                _activeProject.value = saved
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(saved.id)
                onImported(finalPath)
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "AssetManager",
                    message = "Imported font $finalPath (${com.example.ui.assets.ProjectResAssetManager.formatByteSize(bytes.size.toLong())})",
                    filePath = finalPath
                )
                _snackbarMessage.value = "Imported ${finalPath.substringAfterLast('/')}"
            } catch (ex: Exception) {
                val msg = ex.localizedMessage ?: "Failed to import font asset."
                _errorDialogMessage.value = msg
            }
        }
    }

    fun addResTextAsset(
        path: String,
        content: String,
        onAdded: (String) -> Unit = {}
    ) {
        val current = _activeProject.value ?: return
        val cleanPath = path.trim().removePrefix("/")
        if (cleanPath.isBlank()) return
        viewModelScope.launch {
            autoSaver.flushNowBlocking()
            val latest = _activeProject.value ?: current
            if (latest.files.any { it.path.equals(cleanPath, ignoreCase = true) }) {
                _snackbarMessage.value = "Asset already exists: ${cleanPath.substringAfterLast('/')}"
                return@launch
            }
            val updatedFiles = (latest.files + GeneratedFile(cleanPath, content)).sortedBy { it.path }
            val saved = storageRepository.saveProject(latest.copy(files = updatedFiles))
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            onAdded(cleanPath)
            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "AssetManager",
                message = "Added resource asset $cleanPath",
                filePath = cleanPath
            )
            _snackbarMessage.value = "Added ${cleanPath.substringAfterLast('/')}"
        }
    }

    private fun buildUniqueResPath(
        existingFiles: List<GeneratedFile>,
        dir: String,
        baseName: String,
        ext: String
    ): String {
        var candidate = "$dir/$baseName.$ext"
        var suffix = 2
        while (existingFiles.any { it.path.equals(candidate, ignoreCase = true) }) {
            candidate = "$dir/${baseName}_$suffix.$ext"
            suffix++
        }
        return candidate
    }


    suspend fun exportActiveProjectZip(): Uri? {
        autoSaver.flushNowBlocking()
        val current = _activeProject.value ?: return null
        return try {
            val uri = storageRepository.exportProjectToZip(current)
            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "ZipPackager",
                message = "Packaged ${current.files.size} project files into cacheDir .zip archive."
            )
            uri
        } catch (ex: Exception) {
            appendConsoleLog(
                level = ConsoleLogLevel.ERROR,
                tag = "ZipPackager",
                message = "Failed to package ZIP: ${ex.localizedMessage}"
            )
            _errorDialogMessage.value = "Failed to package ZIP: ${ex.localizedMessage}"
            null
        }
    }

    // --- Git Integration (JGit) ---

    fun refreshGitStatus(projectId: String? = _activeProject.value?.id) {
        val id = projectId ?: return
        viewModelScope.launch {
            runCatching {
                val workspaceDir = storageRepository.getWorkspaceDir(id)
                gitManager.getStatus(workspaceDir)
            }.onSuccess { state ->
                _gitState.value = state
            }
        }
    }

    fun initGitRepository() {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                autoSaver.flushNowBlocking()
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                storageRepository.saveProject(_activeProject.value ?: project)
                _gitState.value = gitManager.initRepo(workspaceDir)
                appendConsoleLog(
                    level = ConsoleLogLevel.INFO,
                    tag = "GitEngine",
                    message = "Initialized local Git repository in ${workspaceDir.name} (branch: main)"
                )
                _snackbarMessage.value = "Git repository initialized (branch: main)"
            } catch (ex: Exception) {
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "GitEngine",
                    message = "Git init failed: ${ex.localizedMessage}"
                )
                _errorDialogMessage.value = "Git init failed: ${ex.localizedMessage}"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun stageGitFile(path: String) {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                _gitState.value = gitManager.stageFile(workspaceDir, path)
            } catch (ex: Exception) {
                _errorDialogMessage.value = "Stage failed: ${ex.localizedMessage}"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun stageAllGitFiles() {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                _gitState.value = gitManager.stageAll(workspaceDir)
                appendConsoleLog(
                    level = ConsoleLogLevel.DEBUG,
                    tag = "GitEngine",
                    message = "git add . -> staged all modified/untracked files"
                )
                _snackbarMessage.value = "Staged all changes"
            } catch (ex: Exception) {
                _errorDialogMessage.value = "Stage all failed: ${ex.localizedMessage}"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun unstageGitFile(path: String) {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                _gitState.value = gitManager.unstageFile(workspaceDir, path)
            } catch (ex: Exception) {
                _errorDialogMessage.value = "Unstage failed: ${ex.localizedMessage}"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun commitGitChanges(message: String) {
        val project = _activeProject.value ?: return
        val username = settings.value.gitHubUsername.ifBlank { "AI App Studio" }
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                val state = gitManager.commit(workspaceDir, message, username)
                _gitState.value = state
                val latestSha = state.commits.firstOrNull()?.shortSha ?: "HEAD"
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "GitEngine",
                    message = "git commit [$latestSha]: $message"
                )
                _snackbarMessage.value = "Committed changes"
            } catch (ex: Exception) {
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "GitEngine",
                    message = "git commit failed: ${ex.localizedMessage}"
                )
                _errorDialogMessage.value = ex.localizedMessage ?: "Commit failed"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun configureGitRemote(remoteUrl: String) {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                _gitState.value = gitManager.setRemoteOrigin(workspaceDir, remoteUrl)
                appendConsoleLog(
                    level = ConsoleLogLevel.INFO,
                    tag = "GitEngine",
                    message = "git remote set-url origin $remoteUrl"
                )
                _snackbarMessage.value = "Remote 'origin' updated"
            } catch (ex: Exception) {
                _errorDialogMessage.value = "Failed to set remote: ${ex.localizedMessage}"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun pushGitToGitHub() {
        val project = _activeProject.value ?: return
        val currentSettings = settings.value
        val pat = currentSettings.gitHubPat.trim()
        if (pat.isBlank()) {
            _errorDialogMessage.value = "GitHub Personal Access Token (PAT) is required to push. Add it in Settings."
            return
        }

        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                var currentGit = gitManager.getStatus(workspaceDir)
                if (!currentGit.isInitialized) {
                    currentGit = gitManager.initRepo(workspaceDir)
                }

                // Auto-create GitHub repository if remote 'origin' isn't configured yet
                var updatedProject = project
                if (currentGit.remoteUrl.isNullOrBlank()) {
                    val (owner, repoName, cloneUrl) = gitHubRepository.ensureGitHubRepo(
                        pat = pat,
                        preferredUsername = currentSettings.gitHubUsername,
                        preferredRepoName = project.gitHubRepoName
                    )
                    currentGit = gitManager.setRemoteOrigin(workspaceDir, cloneUrl)
                    updatedProject = storageRepository.saveProject(
                        project.copy(
                            gitHubRepoOwner = owner,
                            gitHubRepoName = repoName,
                            gitHubRepoUrl = "https://github.com/$owner/$repoName"
                        )
                    )
                    _activeProject.value = updatedProject
                }

                // Commit uncommitted changes automatically if needed before pushing if there are zero commits
                if (currentGit.commits.isEmpty() && currentGit.changes.isNotEmpty()) {
                    gitManager.stageAll(workspaceDir)
                    gitManager.commit(
                        workspaceDir,
                        "Initial commit from AI App Studio",
                        currentSettings.gitHubUsername
                    )
                }

                val pushedState = gitManager.push(
                    workspaceDir = workspaceDir,
                    pat = pat,
                    username = currentSettings.gitHubUsername
                )
                _gitState.value = pushedState
                _projects.value = storageRepository.loadAllProjects()
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "GitEngine",
                    message = pushedState.statusMessage ?: "git push origin main succeeded."
                )
                _snackbarMessage.value = pushedState.statusMessage ?: "Pushed to GitHub"
            } catch (ex: Exception) {
                val errMsg = gitHubRepository.buildErrorMessage(ex)
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "GitEngine",
                    message = "git push failed: $errMsg"
                )
                _errorDialogMessage.value = errMsg
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    fun pullGitFromGitHub() {
        val project = _activeProject.value ?: return
        val currentSettings = settings.value
        val pat = currentSettings.gitHubPat.trim()
        if (pat.isBlank()) {
            _errorDialogMessage.value = "GitHub PAT is required to pull. Add it in Settings."
            return
        }
        viewModelScope.launch {
            _isGitBusy.value = true
            try {
                val workspaceDir = storageRepository.getWorkspaceDir(project.id)
                val pulledState = gitManager.pull(
                    workspaceDir = workspaceDir,
                    pat = pat,
                    username = currentSettings.gitHubUsername
                )
                val synced = storageRepository.syncWorkspaceBackToProject(project)
                _activeProject.value = synced
                _projects.value = storageRepository.loadAllProjects()
                _gitState.value = pulledState
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "GitEngine",
                    message = pulledState.statusMessage ?: "Pulled updates from origin/main"
                )
                _snackbarMessage.value = pulledState.statusMessage ?: "Pulled updates from GitHub"
            } catch (ex: Exception) {
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "GitEngine",
                    message = "git pull failed: ${ex.localizedMessage}"
                )
                _errorDialogMessage.value = ex.localizedMessage ?: "Git pull failed"
            } finally {
                _isGitBusy.value = false
            }
        }
    }

    // --- Cloud APK Build (GitHub Actions) ---

    fun triggerCloudApkBuild() {
        val project = _activeProject.value ?: return
        val currentSettings = settings.value
        val pat = currentSettings.gitHubPat.trim()
        if (pat.isBlank()) {
            appendConsoleLog(
                level = ConsoleLogLevel.ERROR,
                tag = "CloudBuild",
                message = "Build aborted: GitHub Personal Access Token (PAT) is missing in Settings."
            )
            _errorDialogMessage.value = "GitHub Personal Access Token (PAT) is missing. Configure it in AI & GitHub Settings."
            return
        }

        BackgroundTaskManager.scope.launch {
            _isCloudBuildBusy.value = true
            _cloudBuildProgress.value = "Running pre-build compiler diagnostics…"
            BackgroundTaskManager.startTask(
                context = getApplication(),
                title = "Compiling ${project.appName}",
                initialMessage = "Running pre-build diagnostics…"
            )
            try {
                autoSaver.flushNowBlocking()
                val latestProject = _activeProject.value ?: project
                val preBuildDiagnostics = ProjectStaticAnalyzer.analyzeProject(latestProject)
                appendConsoleEntries(preBuildDiagnostics)

                _cloudBuildProgress.value = "Preparing GitHub repository…"
                BackgroundTaskManager.updateProgress(
                    context = getApplication(),
                    title = "Compiling ${latestProject.appName}",
                    message = "Preparing GitHub repository…",
                    progress = 0.25f
                )
                val result = gitHubRepository.pushProjectAndTriggerApkBuild(
                    project = latestProject,
                    pat = pat,
                    preferredUsername = currentSettings.gitHubUsername,
                    onProgress = { msg ->
                        _cloudBuildProgress.value = msg
                        val fraction = when {
                            msg.contains("pushing", ignoreCase = true) || msg.contains("commit", ignoreCase = true) -> 0.50f
                            msg.contains("trigger", ignoreCase = true) || msg.contains("workflow", ignoreCase = true) -> 0.75f
                            else -> 0.40f
                        }
                        BackgroundTaskManager.updateProgress(
                            context = getApplication(),
                            title = "Compiling ${latestProject.appName}",
                            message = msg,
                            progress = fraction
                        )
                    },
                    onConsoleLog = { entry -> appendConsoleEntry(entry) }
                )

                val updated = storageRepository.saveProject(
                    latestProject.copy(
                        gitHubRepoOwner = result.repoOwner,
                        gitHubRepoName = result.repoName,
                        gitHubRepoUrl = result.repoUrl,
                        lastWorkflowRunId = result.workflowRunId,
                        lastBuildStatus = result.buildStatus,
                        lastActionsUrl = result.actionsUrl,
                        lastArtifactUrl = result.artifactDownloadUrl
                    )
                )
                _activeProject.value = updated
                _projects.value = storageRepository.loadAllProjects()

                // Also link origin in local JGit if initialized
                val workspaceDir = storageRepository.getWorkspaceDir(updated.id)
                if (_gitState.value.isInitialized && _gitState.value.remoteUrl.isNullOrBlank()) {
                    _gitState.value = gitManager.setRemoteOrigin(workspaceDir, result.cloneUrl)
                }

                _snackbarMessage.value = "Pushed to ${result.repoOwner}/${result.repoName} & triggered APK build!"
                BackgroundTaskManager.finishTask(
                    context = getApplication(),
                    title = "Cloud APK Build Active",
                    completionMessage = "Pushed to GitHub Actions! Building APK for ${latestProject.appName}."
                )
            } catch (ex: Exception) {
                val errMsg = gitHubRepository.buildErrorMessage(ex)
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "CloudBuild",
                    message = "Cloud APK Build failed: $errMsg"
                )
                _errorDialogMessage.value = errMsg
                BackgroundTaskManager.failTask(
                    context = getApplication(),
                    title = "Compiling ${project.appName}",
                    errorMessage = errMsg
                )
            } finally {
                _isCloudBuildBusy.value = false
                _cloudBuildProgress.value = null
            }
        }
    }

    fun refreshCloudApkBuildStatus() {
        val project = _activeProject.value ?: return
        val pat = settings.value.gitHubPat.trim()
        if (pat.isBlank()) {
            _errorDialogMessage.value = "GitHub PAT is missing."
            return
        }

        viewModelScope.launch {
            _isCloudBuildBusy.value = true
            _cloudBuildProgress.value = "Checking GitHub Actions…"
            try {
                val result = gitHubRepository.refreshBuildStatus(
                    project = project,
                    pat = pat,
                    onConsoleLog = { entry -> appendConsoleEntry(entry) }
                )
                val updated = storageRepository.saveProject(
                    project.copy(
                        lastWorkflowRunId = result.workflowRunId,
                        lastBuildStatus = result.buildStatus,
                        lastActionsUrl = result.actionsUrl,
                        lastArtifactUrl = result.artifactDownloadUrl
                    )
                )
                _activeProject.value = updated
                _projects.value = storageRepository.loadAllProjects()
            } catch (ex: Exception) {
                val errMsg = gitHubRepository.buildErrorMessage(ex)
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "CloudBuild",
                    message = "Status refresh failed: $errMsg"
                )
                _errorDialogMessage.value = errMsg
            } finally {
                _isCloudBuildBusy.value = false
                _cloudBuildProgress.value = null
            }
        }
    }

    fun autoFixActiveProject() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            autoSaver.flushNowBlocking()
            val latest = _activeProject.value ?: current
            val (fixedProject, fixes) = com.example.diagnostics.CodeQuickActionEngine.autoFixProject(latest)
            if (fixes.isNotEmpty()) {
                val saved = storageRepository.saveProject(fixedProject)
                _activeProject.value = saved
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(saved.id)
                fixes.forEach { fixDesc ->
                    appendConsoleLog(
                        level = ConsoleLogLevel.SUCCESS,
                        tag = "AutoFix",
                        message = fixDesc
                    )
                }
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(saved))
                _snackbarMessage.value = "Auto-fixed ${fixes.size} issue(s) across project files"
            } else {
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(latest))
                _snackbarMessage.value = "0 auto-fixable issues found"
            }
        }
    }

    fun optimizeActiveProject() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            autoSaver.flushNowBlocking()
            val latest = _activeProject.value ?: current
            val (optimizedProject, changes) = com.example.diagnostics.CodeQuickActionEngine.optimizeProjectPerformance(latest)
            if (changes.isNotEmpty()) {
                val saved = storageRepository.saveProject(optimizedProject)
                _activeProject.value = saved
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(saved.id)
                changes.forEach { change ->
                    appendConsoleLog(
                        level = ConsoleLogLevel.SUCCESS,
                        tag = "ComposeOptimizer",
                        message = change
                    )
                }
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(saved))
                _snackbarMessage.value = "Applied ${changes.size} Compose performance optimization(s)"
            } else {
                _snackbarMessage.value = "Project is already following Compose performance best practices"
            }
        }
    }

    fun fixBuildIssuesWithAi() {
        val current = _activeProject.value ?: return
        if (_isAutoFixingBuild.value) return
        viewModelScope.launch {
            _isAutoFixingBuild.value = true
            try {
                autoSaver.flushNowBlocking()
                val latest = _activeProject.value ?: current
                val (fixedProject, fixes) = com.example.diagnostics.CodeQuickActionEngine.autoFixProject(latest)
                var workingProject = fixedProject
                val appliedMessages = fixes.toMutableList()

                // Re-check for any remaining Kotlin compiler errors
                val preDiagnostics = ProjectStaticAnalyzer.analyzeProject(workingProject)
                val firstErrorEntry = preDiagnostics.firstOrNull {
                    it.level == ConsoleLogLevel.ERROR && it.filePath?.endsWith(".kt", ignoreCase = true) == true
                }

                if (firstErrorEntry?.filePath != null) {
                    val targetFile = workingProject.files.firstOrNull { it.path == firstErrorEntry.filePath }
                    val currentSettings = settings.value
                    val hasCloudKey = ApiCredentialValidator.resolveConfiguredProvider(currentSettings) != null
                    val offlineModelFile = resolveExistingOfflineModelFile(currentSettings)
                    if (targetFile != null && hasCloudKey) {
                        appendConsoleLog(
                            level = ConsoleLogLevel.INFO,
                            tag = "AutoHealAI",
                            message = "Running Cloud AI repair on ${targetFile.path} for: ${firstErrorEntry.message}",
                            filePath = targetFile.path
                        )
                        runCatching {
                            refactorRepository.analyzeAndRefactorKotlinFile(
                                filePath = targetFile.path,
                                sourceCode = targetFile.content,
                                settings = currentSettings,
                                onStatusUpdate = { msg ->
                                    appendConsoleLog(
                                        level = ConsoleLogLevel.DEBUG,
                                        tag = "AutoHealAI",
                                        message = msg,
                                        filePath = targetFile.path
                                    )
                                }
                            )
                        }.onSuccess { result ->
                            if (result.refactoredCode.isNotBlank() && result.refactoredCode != targetFile.content) {
                                val updatedFiles = workingProject.files.map { f ->
                                    if (f.path == targetFile.path) f.copy(content = result.refactoredCode) else f
                                }
                                workingProject = workingProject.copy(
                                    files = updatedFiles,
                                    updatedAtEpochMs = System.currentTimeMillis()
                                )
                                appliedMessages += "${targetFile.path.substringAfterLast('/')}: AI repaired via ${result.modelUsed}"
                            }
                        }.onFailure { err ->
                            appendConsoleLog(
                                level = ConsoleLogLevel.WARN,
                                tag = "AutoHealAI",
                                message = "Cloud AI repair skipped: ${err.localizedMessage}"
                            )
                        }
                    } else if (targetFile != null && offlineModelFile != null) {
                        appendConsoleLog(
                            level = ConsoleLogLevel.INFO,
                            tag = "OfflineAutoHealAI",
                            message = "Running Offline AI (${offlineModelFile.name}) repair on ${targetFile.path}…",
                            filePath = targetFile.path
                        )
                        runCatching {
                            val repairPrompt = OfflineApkBuildAndSignEngine.buildOfflineKotlinRepairPrompt(
                                file = targetFile,
                                diagnostics = preDiagnostics
                            )
                            val (response, _) = generatorRepository.runLocalPromptBenchmarkStep(
                                savedModelPath = offlineModelFile.absolutePath,
                                prompt = repairPrompt
                            )
                            OfflineApkBuildAndSignEngine.extractRepairedKotlinIfCleaner(targetFile, response)
                        }.onSuccess { repairedFile ->
                            if (repairedFile != null) {
                                workingProject = workingProject.copy(
                                    files = workingProject.files.map { f ->
                                        if (f.path == targetFile.path) repairedFile else f
                                    },
                                    updatedAtEpochMs = System.currentTimeMillis()
                                )
                                appliedMessages += "${targetFile.path.substringAfterLast('/')}: Repaired offline via ${offlineModelFile.name}"
                            }
                        }.onFailure { err ->
                            appendConsoleLog(
                                level = ConsoleLogLevel.WARN,
                                tag = "OfflineAutoHealAI",
                                message = "Offline AI repair skipped: ${err.localizedMessage}"
                            )
                        }
                    }
                }

                val newStatus = if (appliedMessages.isNotEmpty()) {
                    "Auto-fixed ${appliedMessages.size} issue(s) — ready to rebuild APK"
                } else {
                    "Static check complete — review GitHub Actions log & Rebuild"
                }

                val saved = storageRepository.saveProject(
                    workingProject.copy(lastBuildStatus = newStatus)
                )
                _activeProject.value = saved
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(saved.id)

                appliedMessages.forEach { msg ->
                    appendConsoleLog(
                        level = ConsoleLogLevel.SUCCESS,
                        tag = "AutoHeal",
                        message = msg
                    )
                }
                appendConsoleEntries(ProjectStaticAnalyzer.analyzeProject(saved))
                _snackbarMessage.value = newStatus
            } finally {
                _isAutoFixingBuild.value = false
            }
        }
    }

    suspend fun downloadReadyApkUri(): Uri? {
        val project = _activeProject.value ?: return null
        val owner = project.gitHubRepoOwner?.takeIf { it.isNotBlank() }
        val repo = project.gitHubRepoName?.takeIf { it.isNotBlank() }
        val runId = project.lastWorkflowRunId
        val pat = settings.value.gitHubPat.trim()

        if (owner == null || repo == null || runId == null || pat.isBlank()) {
            appendConsoleLog(
                level = ConsoleLogLevel.WARN,
                tag = "ApkDownloader",
                message = "Missing repository link, runId, or GitHub PAT to download APK binary directly."
            )
            return null
        }

        _isCloudBuildBusy.value = true
        _cloudBuildProgress.value = "Fetching compiled APK artifact from GitHub Actions…"
        return try {
            val downloadsDir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
            val apkFile = gitHubRepository.downloadCompiledApkArtifact(
                pat = pat,
                owner = owner,
                repoName = repo,
                runId = runId,
                targetDownloadsDir = downloadsDir,
                appName = project.appName,
                onProgress = { msg -> _cloudBuildProgress.value = msg }
            )
            if (apkFile != null && apkFile.exists() && apkFile.length() > 0L) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    apkFile
                )
                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "ApkInstaller",
                    message = "Downloaded ${apkFile.name} (${apkFile.length() / 1024} KB). Launching Android Package Installer…"
                )
                uri
            } else {
                appendConsoleLog(
                    level = ConsoleLogLevel.WARN,
                    tag = "ApkDownloader",
                    message = "No active .apk artifact found for Run #$runId (may have expired)."
                )
                null
            }
        } catch (ex: Exception) {
            val errMsg = gitHubRepository.buildErrorMessage(ex)
            appendConsoleLog(
                level = ConsoleLogLevel.ERROR,
                tag = "ApkDownloader",
                message = "APK download failed: $errMsg"
            )
            _snackbarMessage.value = "APK download failed — opening Actions page"
            null
        } finally {
            _isCloudBuildBusy.value = false
            _cloudBuildProgress.value = null
        }
    }

    suspend fun exportBuildLogs(): Uri? {
        val project = _activeProject.value ?: return null
        return storageRepository.exportBuildLogsToTxt(project, _consoleLogs.value)
    }

    private fun resolveExistingOfflineModelFile(currentSettings: StudioSettings): File? {
        val configuredPath = currentSettings.localModelFilePath.trim()
        if (configuredPath.isNotBlank()) {
            val f = File(configuredPath)
            if (f.exists() && f.canRead()) return f
        }
        val bundledTarget = BundledModelManager.getTargetModelFile(getApplication())
        return if (bundledTarget.exists() && bundledTarget.canRead()) bundledTarget else null
    }

    private fun safeAppSlug(appName: String): String =
        appName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "app" }

    private fun refreshOfflineSigningStatus(project: StudioProject?) {
        if (project == null) {
            _offlineSigningStatus.value = OfflineSigningStatus()
            return
        }
        val keystore = OfflineApkBuildAndSignEngine.inspectProjectKeystore(project)
        val exportsDir = File(getApplication<Application>().cacheDir, "exports")
        val signedApkFile = File(exportsDir, "${safeAppSlug(project.appName)}-offline-signed.apk")
        val verification = if (signedApkFile.exists() && signedApkFile.length() > 0L) {
            OfflineApkBuildAndSignEngine.verifyApkSignature(signedApkFile)
        } else null

        val offlineModel = resolveExistingOfflineModelFile(settings.value)
        val aiStatus = if (offlineModel != null) {
            "Offline AI Ready (${settings.value.localModelDisplayName.ifBlank { offlineModel.name }})"
        } else {
            "Static Analyzer Ready (Download Local AI model in Settings for LLM repair)"
        }

        val pipelineManifest = LocalMediaPipeBuildPipeline.inspectProjectPipeline(project)
        val gradleTasksScript = project.files
            .firstOrNull { it.path == LocalMediaPipeBuildPipeline.GRADLE_PIPELINE_FILE_PATH }
            ?.content
            .orEmpty()

        _offlineSigningStatus.value = OfflineSigningStatus(
            hasKeystore = keystore != null,
            keyAlias = keystore?.keyAlias ?: OfflineApkBuildAndSignEngine.DEFAULT_KEY_ALIAS,
            commonName = keystore?.commonName ?: project.appName,
            sha256Fingerprint = keystore?.sha256Fingerprint.orEmpty(),
            signedApkPath = signedApkFile.takeIf { verification?.isVerified == true }?.absolutePath,
            signedApkSizeBytes = signedApkFile.takeIf { verification?.isVerified == true }?.length() ?: 0L,
            isSignatureVerified = verification?.isVerified == true,
            v1Signed = verification?.v1Verified == true,
            v2Signed = verification?.v2Verified == true,
            v3Signed = verification?.v3Verified == true,
            aiEngineStatus = aiStatus,
            lastBuiltAtEpochMs = project.updatedAtEpochMs,
            pipelineTasks = pipelineManifest?.tasks.orEmpty(),
            pipelineTasksScript = gradleTasksScript,
            pipelineOriginLabel = pipelineManifest?.originLabel.orEmpty(),
            pipelineLatencyMs = pipelineManifest?.inferenceLatencyMs ?: 0L
        )
    }

    fun runOfflineAiBuildAndSign(
        keyAlias: String = OfflineApkBuildAndSignEngine.DEFAULT_KEY_ALIAS,
        storePassword: String = OfflineApkBuildAndSignEngine.DEFAULT_KEY_PASSWORD,
        commonName: String = ""
    ) {
        val current = _activeProject.value ?: return
        if (_isOfflineBuildBusy.value || _isCloudBuildBusy.value) return

        viewModelScope.launch {
            _isOfflineBuildBusy.value = true
            _offlineBuildProgress.value = "Running pre-build static & Compose checks…"
            _offlineBuildStreamPreview.value = ""
            try {
                autoSaver.flushNowBlocking()
                val latest = _activeProject.value ?: current
                val currentSettings = settings.value
                val offlineModelFile = resolveExistingOfflineModelFile(currentSettings)

                appendConsoleLog(
                    level = ConsoleLogLevel.INFO,
                    tag = "OfflineBuilder",
                    message = "Starting Offline MediaPipe LLM Build & Signing pipeline for '${latest.appName}' (zero GitHub/network dependency)…"
                )

                // Step 1: Deterministic Auto-Fix
                val (autoFixedProject, quickFixes) = withContext(Dispatchers.Default) {
                    com.example.diagnostics.CodeQuickActionEngine.autoFixProject(latest)
                }
                var workingProject = autoFixedProject
                quickFixes.forEach { fix ->
                    appendConsoleLog(level = ConsoleLogLevel.SUCCESS, tag = "OfflineAutoFix", message = fix)
                }

                // Step 2: Static Analysis + Offline MediaPipe LLM Repair if errors remain
                var diagnostics = withContext(Dispatchers.Default) {
                    ProjectStaticAnalyzer.analyzeProject(workingProject)
                }
                val failingKotlinFiles = diagnostics
                    .filter {
                        (it.level == ConsoleLogLevel.ERROR || it.level == ConsoleLogLevel.WARN) &&
                            it.filePath?.endsWith(".kt", ignoreCase = true) == true
                    }
                    .mapNotNull { it.filePath }
                    .distinct()

                if (offlineModelFile != null && failingKotlinFiles.isNotEmpty()) {
                    _offlineBuildProgress.value = "MediaPipe LLM (${offlineModelFile.name}) repairing ${failingKotlinFiles.size} file(s)…"
                    appendConsoleLog(
                        level = ConsoleLogLevel.INFO,
                        tag = "OfflineAI",
                        message = "Invoking on-device MediaPipe LLM (${offlineModelFile.name}) to repair ${failingKotlinFiles.size} file(s)…"
                    )
                    for (path in failingKotlinFiles.take(2)) {
                        val targetFile = workingProject.files.firstOrNull { it.path == path } ?: continue
                        val repairPrompt = OfflineApkBuildAndSignEngine.buildOfflineKotlinRepairPrompt(targetFile, diagnostics)
                        val repaired = runCatching {
                            val (response, durationMs) = generatorRepository.runLocalPromptBenchmarkStep(
                                savedModelPath = offlineModelFile.absolutePath,
                                prompt = repairPrompt,
                                onStreamToken = { stream -> _offlineBuildStreamPreview.value = stream }
                            )
                            appendConsoleLog(
                                level = ConsoleLogLevel.DEBUG,
                                tag = "OfflineAI",
                                message = "MediaPipe LLM processed ${targetFile.path.substringAfterLast('/')} in ${durationMs} ms",
                                filePath = targetFile.path
                            )
                            OfflineApkBuildAndSignEngine.extractRepairedKotlinIfCleaner(targetFile, response)
                        }.getOrElse { err ->
                            appendConsoleLog(
                                level = ConsoleLogLevel.WARN,
                                tag = "OfflineAI",
                                message = "Offline AI repair skipped for ${targetFile.path}: ${err.localizedMessage}",
                                filePath = targetFile.path
                            )
                            null
                        }
                        if (repaired != null) {
                            workingProject = workingProject.copy(
                                files = workingProject.files.map { f -> if (f.path == targetFile.path) repaired else f }
                            )
                            appendConsoleLog(
                                level = ConsoleLogLevel.SUCCESS,
                                tag = "OfflineAI",
                                message = "MediaPipe LLM fixed compiler/Compose issues in ${targetFile.path}",
                                filePath = targetFile.path
                            )
                        }
                    }
                    diagnostics = withContext(Dispatchers.Default) {
                        ProjectStaticAnalyzer.analyzeProject(workingProject)
                    }
                } else if (offlineModelFile != null) {
                    appendConsoleLog(
                        level = ConsoleLogLevel.SUCCESS,
                        tag = "OfflineAI",
                        message = "MediaPipe LLM (${offlineModelFile.name}) ready — 0 compiler errors detected in Kotlin sources."
                    )
                } else {
                    appendConsoleLog(
                        level = ConsoleLogLevel.INFO,
                        tag = "OfflineAI",
                        message = "On-device .task model not downloaded yet; synthesizing deterministic offline compilation & signing tasks. Download a local model in Settings for MediaPipe LLM customization."
                    )
                }

                // Step 3: Generate / load real PKCS12 keystore & inject Gradle release signing
                _offlineBuildProgress.value = "Generating PKCS12 X.509 signing keystore…"
                val resolvedCn = commonName.trim().ifBlank { workingProject.appName }
                val injectionResult = withContext(Dispatchers.IO) {
                    OfflineApkBuildAndSignEngine.applyOfflineSigningConfigToProject(
                        project = workingProject,
                        keyAlias = keyAlias,
                        storePassword = storePassword,
                        commonName = resolvedCn
                    )
                }
                workingProject = injectionResult.updatedProject

                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "OfflineSigner",
                    message = "PKCS12 Keystore ready (alias=${injectionResult.keystoreInfo.keyAlias}, SHA-256=${injectionResult.keystoreInfo.sha256Fingerprint})"
                )

                // Step 4: Generate custom offline APK compilation & signing Gradle tasks via MediaPipe LLM
                _offlineBuildProgress.value = if (offlineModelFile != null) {
                    "MediaPipe LLM generating offline Gradle compilation & signing tasks…"
                } else {
                    "Generating offline Gradle compilation & signing tasks…"
                }
                val pipelineResult = LocalMediaPipeBuildPipeline.generateLocalPipeline(
                    project = workingProject,
                    modelFile = offlineModelFile,
                    repository = generatorRepository,
                    keyAlias = injectionResult.keystoreInfo.keyAlias,
                    storePassword = injectionResult.keystoreInfo.storePassword,
                    commonName = injectionResult.keystoreInfo.commonName,
                    sha256Fingerprint = injectionResult.keystoreInfo.sha256Fingerprint,
                    onProgress = { msg -> _offlineBuildProgress.value = msg },
                    onStreamPreview = { stream -> _offlineBuildStreamPreview.value = stream }
                )
                val (pipelinedProject, pipelinePaths) = withContext(Dispatchers.IO) {
                    LocalMediaPipeBuildPipeline.applyPipelineToProject(workingProject, pipelineResult)
                }
                workingProject = pipelinedProject

                appendConsoleLog(
                    level = ConsoleLogLevel.SUCCESS,
                    tag = "LocalPipeline",
                    message = "${pipelineResult.originLabel}: generated ${pipelineResult.tasks.size} offline tasks -> ${pipelinePaths.joinToString()}"
                )

                // Step 5: If a compiled APK exists locally for this project (e.g. cached debug APK), sign & verify it offline via apksig
                val exportsDir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                val slug = safeAppSlug(workingProject.appName)
                val cachedUnsignedOrDebugApk = File(exportsDir, "$slug-debug.apk")
                val signedOutputApk = File(exportsDir, "$slug-offline-signed.apk")
                var signedReady = false

                if (cachedUnsignedOrDebugApk.exists() && cachedUnsignedOrDebugApk.length() > 0L) {
                    _offlineBuildProgress.value = "Signing APK (v1/v2/v3) with Offline PKCS12 Keystore…"
                    val verifyResult = withContext(Dispatchers.IO) {
                        OfflineApkBuildAndSignEngine.signApkOffline(
                            inputApk = cachedUnsignedOrDebugApk,
                            outputApk = signedOutputApk,
                            keystore = injectionResult.keystoreInfo
                        )
                    }
                    if (verifyResult.isVerified) {
                        signedReady = true
                        appendConsoleLog(
                            level = ConsoleLogLevel.SUCCESS,
                            tag = "ApkSigner",
                            message = "Signed & verified ${signedOutputApk.name} (${signedOutputApk.length() / 1024} KB) • v1=${verifyResult.v1Verified}, v2=${verifyResult.v2Verified}, v3=${verifyResult.v3Verified}"
                        )
                    } else {
                        appendConsoleLog(
                            level = ConsoleLogLevel.WARN,
                            tag = "ApkSigner",
                            message = "APK signing verification errors: ${verifyResult.errors.joinToString()}"
                        )
                    }
                }

                val remainingErrors = diagnostics.count { it.level == ConsoleLogLevel.ERROR }
                val finalStatus = when {
                    signedReady -> "Offline Signed APK Ready (v1/v2/v3 Verified)"
                    remainingErrors > 0 -> "Offline Signing & Tasks Ready • $remainingErrors source error(s) remaining"
                    pipelineResult.generatedByMediaPipe -> "MediaPipe LLM Local Build & Signing Tasks Ready (Zero GitHub)"
                    else -> "Offline Compilation & PKCS12 Signing Tasks Ready (Zero GitHub)"
                }

                val savedProject = storageRepository.saveProject(
                    workingProject.copy(lastBuildStatus = finalStatus)
                )
                _activeProject.value = savedProject
                _projects.value = storageRepository.loadAllProjects()
                refreshGitStatus(savedProject.id)
                refreshOfflineSigningStatus(savedProject)
                appendConsoleEntries(diagnostics)

                _snackbarMessage.value = finalStatus
                FactoryBuildService.showCompletionNotification(
                    getApplication(),
                    "Offline Build & Sign Complete",
                    finalStatus
                )
            } catch (ex: Exception) {
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "OfflineBuilder",
                    message = "Offline build & sign failed: ${ex.localizedMessage ?: "Unknown error"}"
                )
                _errorDialogMessage.value = ex.localizedMessage ?: "Offline build & sign failed."
                FactoryBuildService.showErrorNotification(
                    getApplication(),
                    "Offline Build Failed",
                    ex.localizedMessage ?: "Unknown error"
                )
            } finally {
                _isOfflineBuildBusy.value = false
                _offlineBuildProgress.value = null
                _offlineBuildStreamPreview.value = ""
            }
        }
    }

    suspend fun signExternalApkOffline(
        apkUri: Uri,
        keyAlias: String = OfflineApkBuildAndSignEngine.DEFAULT_KEY_ALIAS,
        storePassword: String = OfflineApkBuildAndSignEngine.DEFAULT_KEY_PASSWORD,
        commonName: String = ""
    ): Uri? {
        val current = _activeProject.value ?: return null
        if (_isOfflineBuildBusy.value) return null

        _isOfflineBuildBusy.value = true
        _offlineBuildProgress.value = "Preparing PKCS12 keystore & reading APK…"
        val app = getApplication<Application>()
        return try {
            val exportsDir = File(app.cacheDir, "exports").apply { mkdirs() }
            val tempInputApk = File(exportsDir, "temp-input-${System.currentTimeMillis()}.apk")
            val copied = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(apkUri)?.use { input ->
                    tempInputApk.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                    true
                } ?: false
            }
            if (!copied || !tempInputApk.exists() || tempInputApk.length() == 0L) {
                runCatching { tempInputApk.delete() }
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "OfflineSigner",
                    message = "Could not read selected .apk file."
                )
                return null
            }

            _offlineBuildProgress.value = "Ensuring PKCS12 X.509 signing certificate…"
            val resolvedCn = commonName.trim().ifBlank { current.appName }
            val injection = withContext(Dispatchers.IO) {
                OfflineApkBuildAndSignEngine.applyOfflineSigningConfigToProject(
                    project = current,
                    keyAlias = keyAlias,
                    storePassword = storePassword,
                    commonName = resolvedCn
                )
            }

            _offlineBuildProgress.value = "Signing APK with v1/v2/v3 signatures (apksig)…"
            val signedFile = File(exportsDir, "${safeAppSlug(current.appName)}-offline-signed.apk")
            val verifyResult = withContext(Dispatchers.IO) {
                try {
                    OfflineApkBuildAndSignEngine.signApkOffline(
                        inputApk = tempInputApk,
                        outputApk = signedFile,
                        keystore = injection.keystoreInfo
                    )
                } finally {
                    runCatching { tempInputApk.delete() }
                }
            }

            if (!verifyResult.isVerified) {
                val errSummary = verifyResult.errors.joinToString()
                appendConsoleLog(
                    level = ConsoleLogLevel.ERROR,
                    tag = "ApkSigner",
                    message = "Offline APK signing failed verification: $errSummary"
                )
                _snackbarMessage.value = "APK signing failed verification"
                FactoryBuildService.showErrorNotification(
                    getApplication(),
                    "APK Signing Failed",
                    "Verification errors: $errSummary"
                )
                return null
            }

            val saved = storageRepository.saveProject(
                injection.updatedProject.copy(
                    lastBuildStatus = "Offline Signed APK Ready (v1/v2/v3 Verified)"
                )
            )
            _activeProject.value = saved
            _projects.value = storageRepository.loadAllProjects()
            refreshGitStatus(saved.id)
            refreshOfflineSigningStatus(saved)

            appendConsoleLog(
                level = ConsoleLogLevel.SUCCESS,
                tag = "ApkSigner",
                message = "Signed ${signedFile.name} offline (${signedFile.length() / 1024} KB) • SHA-256=${verifyResult.sha256Fingerprint} • v1=${verifyResult.v1Verified}, v2=${verifyResult.v2Verified}, v3=${verifyResult.v3Verified}"
            )
            _snackbarMessage.value = "APK signed & verified offline — launching installer"
            FactoryBuildService.showCompletionNotification(
                app,
                "Offline APK Signed & Verified",
                "Signed ${signedFile.name} (${signedFile.length() / 1024} KB) verified v1+v2+v3"
            )

            androidx.core.content.FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                signedFile
            )
        } catch (ex: Exception) {
            appendConsoleLog(
                level = ConsoleLogLevel.ERROR,
                tag = "ApkSigner",
                message = "Offline APK signing failed: ${ex.localizedMessage ?: "Invalid APK or keystore"}"
            )
            _snackbarMessage.value = "Offline APK signing failed: ${ex.localizedMessage}"
            FactoryBuildService.showErrorNotification(
                app,
                "APK Signing Failed",
                ex.localizedMessage ?: "Invalid APK or keystore"
            )
            null
        } finally {
            _isOfflineBuildBusy.value = false
            _offlineBuildProgress.value = null
        }
    }

    fun getOfflineSignedApkInstallUri(): Uri? {
        val path = _offlineSigningStatus.value.signedApkPath ?: return null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val app = getApplication<Application>()
        return androidx.core.content.FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file
        )
    }

    override fun onCleared() {
        generatorRepository.close()
        super.onCleared()
    }
}

