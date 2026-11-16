package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.generator.BundledModelState
import com.example.data.generator.DeviceHardwareProfile
import com.example.data.generator.DeviceHardwareProfiler
import com.example.data.generator.GenerationRepositoryConfig
import com.example.data.generator.ModelBenchmarkUiState
import com.example.data.generator.ModelLinkVerificationState
import com.example.data.preferences.ApiCredentialValidator
import com.example.data.preferences.ApiKeyVerificationStatus
import com.example.data.preferences.ApiUsageSummary
import com.example.data.preferences.ApiUsageThresholdEvaluator
import com.example.data.preferences.MonthlyUsageAlertState
import com.example.data.preferences.SavedApiKeyHealthState
import com.example.model.AiEngineMode
import com.example.model.ApiUsageLimitMetric
import com.example.model.AppThemeMode
import com.example.model.EditorThemeMode
import com.example.model.LocalModelDownloadState
import com.example.model.StudioSettings
import com.example.ui.components.ApiCredentialsVaultCard
import com.example.ui.components.ApiUsageSummaryCard
import com.example.ui.components.AppGenerationEngineSwitcherCard
import com.example.ui.components.BackgroundExecutionKeepAliveCard
import com.example.ui.components.BundledModelStatusCard
import com.example.ui.components.LocalAiModelManagerPanel
import com.example.ui.components.SavedApiKeyStatusIndicator
import com.example.ui.components.SystemDiagnosticsCard
import com.example.ui.theme.LocalStudioCyberPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: StudioSettings,
    isImportingModel: Boolean,
    bundledModelState: BundledModelState = BundledModelState.Idle,
    generatorConfig: GenerationRepositoryConfig = GenerationRepositoryConfig.fromSettings(settings),
    hardwareProfile: DeviceHardwareProfile = DeviceHardwareProfiler.buildProfile(8.0, 4.2),
    localModelDownloadState: LocalModelDownloadState = LocalModelDownloadState.Idle,
    modelVerificationStates: Map<String, ModelLinkVerificationState> = emptyMap(),
    isHardwareKeystoreBacked: Boolean = false,
    geminiVerification: ApiKeyVerificationStatus = ApiKeyVerificationStatus.Idle,
    openRouterVerification: ApiKeyVerificationStatus = ApiKeyVerificationStatus.Idle,
    savedKeyHealthState: SavedApiKeyHealthState = SavedApiKeyHealthState.NotConfigured,
    apiUsageSummary: ApiUsageSummary = ApiUsageSummary(),
    monthlyUsageAlertState: MonthlyUsageAlertState = MonthlyUsageAlertState(),
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onEditorThemeModeChange: (EditorThemeMode) -> Unit = {},
    onEngineModeChange: (AiEngineMode) -> Unit = {},
    onImportModelUri: (Uri) -> Unit,
    onRestoreBuiltInModel: () -> Unit = {},
    onReinitializeModel: () -> Unit = onRestoreBuiltInModel,
    onDownloadBuiltInFromMirrors: () -> Unit = {},
    onCancelBuiltInMirrorDownload: () -> Unit = {},
    onVerifyAllModelLinks: (Boolean) -> Unit = {},
    onVerifySingleModelLink: (String, Boolean) -> Unit = { _, _ -> },
    onDownloadLocalModel: (
        url: String,
        displayName: String,
        fileNameHint: String,
        expectedSizeBytes: Long,
        presetId: String?
    ) -> Unit = { _, _, _, _, _ -> },
    onCancelLocalModelDownload: () -> Unit = {},
    onDismissLocalModelDownloadError: () -> Unit = {},
    onRefreshHardwareProfile: () -> Unit = {},
    onUpdateHuggingFaceToken: (String) -> Unit = {},
    onUnloadLocalModel: () -> Unit = {},
    benchmarkState: ModelBenchmarkUiState = ModelBenchmarkUiState.Idle,
    onTestModel: () -> Unit = {},
    onCancelTestModel: () -> Unit = {},
    onVerifyGeminiKey: (String) -> Unit = {},
    onVerifyOpenRouterKey: (String) -> Unit = {},
    onVerifySavedKey: () -> Unit = {},
    onResetUsageStats: () -> Unit = {},
    onApplyMonthlyThreshold: (Boolean, ApiUsageLimitMetric, Long, Int) -> Unit = { _, _, _, _ -> },
    onSaveCloudCredentials: (openRouterKey: String, geminiKey: String, modelId: String) -> Unit = { _, _, _ -> },
    onClearGeminiKey: () -> Unit = {},
    onClearOpenRouterKey: () -> Unit = {},
    onClearAllCloudCredentials: () -> Unit = {},
    onSaveSettings: (StudioSettings) -> Unit,
    onBack: () -> Unit,
    isModalDialog: Boolean = false
) {
    val palette = LocalStudioCyberPalette.current

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        onVerifySavedKey()
    }

    var themeMode by remember(settings.themeMode) { mutableStateOf(settings.themeMode) }
    var editorThemeMode by remember(settings.editorThemeMode) { mutableStateOf(settings.editorThemeMode) }
    var engineMode by remember(settings.aiEngineMode) { mutableStateOf(settings.aiEngineMode) }
    var openRouterKey by remember(settings.openRouterApiKey) { mutableStateOf(settings.openRouterApiKey) }
    var geminiKey by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var openRouterModel by remember(settings.openRouterModel) { mutableStateOf(settings.openRouterModel) }

    var localBaseUrl by remember(settings.localServerBaseUrl) { mutableStateOf(settings.localServerBaseUrl) }
    var localModelName by remember(settings.localServerModel) { mutableStateOf(settings.localServerModel) }

    var gitHubPat by remember(settings.gitHubPat) { mutableStateOf(settings.gitHubPat) }
    var gitHubUsername by remember(settings.gitHubUsername) { mutableStateOf(settings.gitHubUsername) }
    var showGitHubPat by remember { mutableStateOf(false) }

    var monthlyAlertEnabled by remember(settings.monthlyUsageAlertEnabled) {
        mutableStateOf(settings.monthlyUsageAlertEnabled)
    }
    var monthlyLimitMetric by remember(settings.monthlyUsageLimitMetric) {
        mutableStateOf(settings.monthlyUsageLimitMetric)
    }
    var monthlyLimitInput by remember(settings.monthlyUsageLimit) {
        mutableStateOf(settings.monthlyUsageLimit.toString())
    }
    var monthlyWarningThresholdPercent by remember(settings.monthlyUsageWarningThresholdPercent) {
        mutableStateOf(settings.monthlyUsageWarningThresholdPercent)
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportModelUri(uri)
        }
    }

    val geminiValidation = remember(geminiKey) {
        ApiCredentialValidator.validateGeminiKeyFormat(geminiKey)
    }
    val openRouterValidation = remember(openRouterKey) {
        ApiCredentialValidator.validateOpenRouterKeyFormat(openRouterKey)
    }
    val areApiKeysValid = geminiValidation.isValidShape && openRouterValidation.isValidShape

    val saveAndClose = {
        if (areApiKeysValid) {
            val resolvedMonthlyLimit = monthlyLimitInput.trim().toLongOrNull()
                ?.takeIf { it > 0L }
                ?: settings.monthlyUsageLimit
            onSaveSettings(
                settings.copy(
                    themeMode = themeMode,
                    editorThemeMode = editorThemeMode,
                    aiEngineMode = engineMode,
                    openRouterApiKey = openRouterKey.trim(),
                    geminiApiKey = geminiKey.trim(),
                    openRouterModel = openRouterModel,
                    localServerBaseUrl = localBaseUrl.trim(),
                    localServerModel = localModelName.trim(),
                    gitHubPat = gitHubPat.trim(),
                    gitHubUsername = gitHubUsername.trim(),
                    monthlyUsageAlertEnabled = monthlyAlertEnabled,
                    monthlyUsageLimitMetric = monthlyLimitMetric,
                    monthlyUsageLimit = resolvedMonthlyLimit,
                    monthlyUsageWarningThresholdPercent = monthlyWarningThresholdPercent
                )
            )
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_screen_settings_modal"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_settings_button")
                    ) {
                        Icon(
                            imageVector = if (isModalDialog) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isModalDialog) "Close Settings" else "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Studio Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Credentials Vault • AI Engine • Theme • GitHub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = saveAndClose,
                        enabled = areApiKeysValid,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_save_settings_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.cardSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = palette.cardSurface,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = saveAndClose,
                        enabled = areApiKeysValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(50.dp)
                            .testTag("save_settings_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (areApiKeysValid) "Save All Settings"
                            else "Fix Invalid API Key"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live Saved API Key Health Status Indicator
                SavedApiKeyStatusIndicator(
                    healthState = savedKeyHealthState,
                    onVerifySavedKey = onVerifySavedKey
                )

                // 1. Appearance Selector (System / Light / Dark)
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_appearance_card"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Appearance",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "System-adaptive Cyber Obsidian (Dark) & Clean Glacier (Light)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_theme_mode_selector")
                        ) {
                            AppThemeMode.entries.forEachIndexed { index, mode ->
                                val modeIcon = when (mode) {
                                    AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                }
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = {
                                        themeMode = mode
                                        onThemeModeChange(mode)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AppThemeMode.entries.size
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                ) {
                                    Text(mode.label, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // 2. Code Editor Syntax Theme (Cyber Night / Glacier Day)
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_editor_theme_card"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column {
                                Text(
                                    text = "Code Editor Syntax Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Toggle between Cyber Night (Dark) & Glacier Day (Light) syntax coloring",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_editor_theme_selector")
                        ) {
                            EditorThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = editorThemeMode == mode,
                                    onClick = {
                                        editorThemeMode = mode
                                        onEditorThemeModeChange(mode)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = EditorThemeMode.entries.size
                                    ),
                                    modifier = Modifier.testTag("editor_theme_${mode.name.lowercase()}")
                                ) {
                                    Text(mode.label, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // 3. Dedicated Secure API Credentials Vault (Always visible!)
                ApiCredentialsVaultCard(
                    savedGeminiKey = settings.geminiApiKey,
                    savedOpenRouterKey = settings.openRouterApiKey,
                    geminiKeyInput = geminiKey,
                    openRouterKeyInput = openRouterKey,
                    selectedCloudModel = openRouterModel,
                    isHardwareKeystoreBacked = isHardwareKeystoreBacked,
                    geminiVerification = geminiVerification,
                    openRouterVerification = openRouterVerification,
                    onGeminiKeyChange = { geminiKey = it },
                    onOpenRouterKeyChange = { openRouterKey = it },
                    onCloudModelChange = { openRouterModel = it },
                    onVerifyGeminiKey = onVerifyGeminiKey,
                    onVerifyOpenRouterKey = onVerifyOpenRouterKey,
                    onSaveCredentials = {
                        onSaveCloudCredentials(openRouterKey, geminiKey, openRouterModel)
                    },
                    onClearGeminiKey = {
                        geminiKey = ""
                        onClearGeminiKey()
                    },
                    onClearOpenRouterKey = {
                        openRouterKey = ""
                        onClearOpenRouterKey()
                    },
                    onClearAllCredentials = {
                        geminiKey = ""
                        openRouterKey = ""
                        onClearAllCloudCredentials()
                    }
                )

                // 3b. API Usage Statistics Summary Card (Response Headers Telemetry + Monthly Limit Threshold)
                ApiUsageSummaryCard(
                    usageSummary = apiUsageSummary,
                    hasConfiguredKey = ApiCredentialValidator.resolveConfiguredProvider(settings) != null,
                    isChecking = savedKeyHealthState is SavedApiKeyHealthState.Checking,
                    onRefreshUsageCheck = onVerifySavedKey,
                    onResetUsageStats = onResetUsageStats,
                    alertEnabled = monthlyAlertEnabled,
                    limitMetric = monthlyLimitMetric,
                    limitInput = monthlyLimitInput,
                    warningThresholdPercent = monthlyWarningThresholdPercent,
                    savedAlertEnabled = settings.monthlyUsageAlertEnabled,
                    savedMetric = settings.monthlyUsageLimitMetric,
                    savedLimit = settings.monthlyUsageLimit,
                    savedWarningThresholdPercent = settings.monthlyUsageWarningThresholdPercent,
                    liveAlertState = monthlyUsageAlertState,
                    onAlertEnabledChange = { monthlyAlertEnabled = it },
                    onLimitMetricChange = { newMetric ->
                        monthlyLimitMetric = newMetric
                        if (newMetric == ApiUsageLimitMetric.TOKENS &&
                            monthlyLimitInput == StudioSettings.DEFAULT_MONTHLY_REQUEST_LIMIT.toString()
                        ) {
                            monthlyLimitInput = StudioSettings.DEFAULT_MONTHLY_TOKEN_LIMIT.toString()
                        } else if (newMetric == ApiUsageLimitMetric.REQUESTS &&
                            monthlyLimitInput == StudioSettings.DEFAULT_MONTHLY_TOKEN_LIMIT.toString()
                        ) {
                            monthlyLimitInput = StudioSettings.DEFAULT_MONTHLY_REQUEST_LIMIT.toString()
                        }
                    },
                    onLimitInputChange = { monthlyLimitInput = it },
                    onWarningThresholdPercentChange = { monthlyWarningThresholdPercent = it },
                    onApplyThreshold = onApplyMonthlyThreshold
                )

                // 3c. Background Execution Keep-Alive & Battery Optimization Status Card
                BackgroundExecutionKeepAliveCard()

                // 4. Primary App Generation Engine Switcher
                AppGenerationEngineSwitcherCard(
                    selectedMode = engineMode,
                    generatorConfig = generatorConfig,
                    onSelectEngineMode = { targetMode ->
                        engineMode = targetMode
                        onEngineModeChange(targetMode)
                    }
                )

                // 5. Local Server Controls (when active)
                if (engineMode == AiEngineMode.LOCAL_SERVER) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Local Server (Ollama / Termux / LAN)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = localBaseUrl,
                        onValueChange = { localBaseUrl = it },
                        label = { Text("OpenAI-Compatible Base URL") },
                        placeholder = { Text("http://127.0.0.1:11434/v1/") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_server_url_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = { localBaseUrl = "http://127.0.0.1:11434/v1/" },
                            label = { Text("Termux Localhost") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                        SuggestionChip(
                            onClick = { localBaseUrl = "http://192.168.1.100:11434/v1/" },
                            label = { Text("LAN Template") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }

                    OutlinedTextField(
                        value = localModelName,
                        onValueChange = { localModelName = it },
                        label = { Text("Local Model Name") },
                        placeholder = { Text("qwen2.5-coder:3b") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_server_model_input")
                    )

                    Text(
                        text = "No API key needed. Uses an extended 5-minute timeout for local CPU inference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 6. Local AI (Offline MediaPipe & Models)
                HorizontalDivider(color = palette.cardBorder)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Local AI (Offline MediaPipe & Models)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                BundledModelStatusCard(
                    bundledModelState = bundledModelState,
                    settings = settings,
                    onRetryOrRestore = onRestoreBuiltInModel,
                    onReinitializeModel = onReinitializeModel,
                    onDownloadBuiltInFromMirrors = onDownloadBuiltInFromMirrors,
                    onCancelBuiltInMirrorDownload = onCancelBuiltInMirrorDownload,
                    benchmarkState = benchmarkState,
                    onTestModel = onTestModel,
                    onCancelTestModel = onCancelTestModel
                )

                LocalAiModelManagerPanel(
                    settings = settings,
                    isImportingModel = isImportingModel,
                    hardwareProfile = hardwareProfile,
                    downloadState = localModelDownloadState,
                    bundledModelState = bundledModelState,
                    modelVerificationStates = modelVerificationStates,
                    onRefreshHardwareProfile = onRefreshHardwareProfile,
                    onRestoreBuiltInModel = onRestoreBuiltInModel,
                    onDownloadBuiltInFromMirrors = onDownloadBuiltInFromMirrors,
                    onCancelBuiltInMirrorDownload = onCancelBuiltInMirrorDownload,
                    onVerifyAllModelLinks = onVerifyAllModelLinks,
                    onVerifySingleModelLink = onVerifySingleModelLink,
                    onUpdateHuggingFaceToken = onUpdateHuggingFaceToken,
                    onDownloadSelectedModel = onDownloadLocalModel,
                    onCancelDownload = onCancelLocalModelDownload,
                    onDismissDownloadError = onDismissLocalModelDownloadError,
                    onPickModelFile = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    onUnloadModel = onUnloadLocalModel
                )

                // 7. GitHub PAT & Username
                HorizontalDivider(color = palette.cardBorder)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "GitHub (Git Push/Pull & Cloud APK Build)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = gitHubPat,
                    onValueChange = { gitHubPat = it },
                    label = { Text("GitHub Personal Access Token (PAT)") },
                    placeholder = { Text("ghp_... (repo + workflow scopes)") },
                    singleLine = true,
                    visualTransformation = if (showGitHubPat) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { showGitHubPat = !showGitHubPat },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (showGitHubPat) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle GitHub PAT visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_pat_input")
                )

                OutlinedTextField(
                    value = gitHubUsername,
                    onValueChange = { gitHubUsername = it },
                    label = { Text("GitHub Username (Optional)") },
                    placeholder = { Text("Auto-detected from PAT if blank") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_username_input")
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = palette.cardBorder)
                Spacer(modifier = Modifier.height(8.dp))

                SystemDiagnosticsCard(settings = settings)

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
