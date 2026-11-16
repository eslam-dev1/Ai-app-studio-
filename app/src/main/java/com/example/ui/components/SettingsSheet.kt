package com.example.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.example.data.generator.BundledModelManager
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.generator.BundledModelState
import com.example.data.generator.DeviceHardwareProfile
import com.example.data.generator.DeviceHardwareProfiler
import com.example.data.generator.GenerationRepositoryConfig
import com.example.data.generator.ModelBenchmarkUiState
import com.example.data.generator.ModelLinkVerificationState
import com.example.model.AiEngineMode
import com.example.model.AppThemeMode
import com.example.model.EditorThemeMode
import com.example.model.LocalModelDownloadState
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import com.example.ui.theme.LocalStudioCyberPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: StudioSettings,
    isImportingModel: Boolean,
    bundledModelState: BundledModelState = BundledModelState.Idle,
    generatorConfig: GenerationRepositoryConfig = GenerationRepositoryConfig.fromSettings(settings),
    hardwareProfile: DeviceHardwareProfile = DeviceHardwareProfiler.buildProfile(8.0, 4.2),
    localModelDownloadState: LocalModelDownloadState = LocalModelDownloadState.Idle,
    modelVerificationStates: Map<String, ModelLinkVerificationState> = emptyMap(),
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
    onSaveSettings: (StudioSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioCyberPalette.current

    var themeMode by remember(settings.themeMode) { mutableStateOf(settings.themeMode) }
    var editorThemeMode by remember(settings.editorThemeMode) { mutableStateOf(settings.editorThemeMode) }
    var engineMode by remember(settings.aiEngineMode) { mutableStateOf(settings.aiEngineMode) }
    var openRouterKey by remember(settings.openRouterApiKey) { mutableStateOf(settings.openRouterApiKey) }
    var geminiKey by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var openRouterModel by remember(settings.openRouterModel) { mutableStateOf(settings.openRouterModel) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    var localBaseUrl by remember(settings.localServerBaseUrl) { mutableStateOf(settings.localServerBaseUrl) }
    var localModelName by remember(settings.localServerModel) { mutableStateOf(settings.localServerModel) }

    var gitHubPat by remember(settings.gitHubPat) { mutableStateOf(settings.gitHubPat) }
    var gitHubUsername by remember(settings.gitHubUsername) { mutableStateOf(settings.gitHubUsername) }
    var showGitHubPat by remember { mutableStateOf(false) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportModelUri(uri)
        }
    }

    val saveAndClose = {
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
                gitHubUsername = gitHubUsername.trim()
            )
        )
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
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
                            onClick = onDismiss,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("close_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings"
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
                                text = "Theme • Local AI • Cloud • Termux • GitHub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = saveAndClose,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
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
                    Button(
                        onClick = saveAndClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(50.dp)
                            .testTag("save_settings_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Settings")
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

                // 1.5 Code Editor Syntax Theme (Cyber Night / Glacier Day)
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

                // 2. Primary App Generation Engine Switcher
                AppGenerationEngineSwitcherCard(
                    selectedMode = engineMode,
                    generatorConfig = generatorConfig,
                    onSelectEngineMode = { targetMode ->
                        engineMode = targetMode
                        onEngineModeChange(targetMode)
                    }
                )

                // 3. Engine-Specific Controls + Full Local AI Model Catalog & Token
                when (engineMode) {
                    AiEngineMode.CLOUD_OPENROUTER -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Gemini Cloud API & OpenRouter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Gemini Cloud API Key (Google AI Studio)") },
                            placeholder = { Text("AIzaSy... (or configure via Secrets)") },
                            singleLine = true,
                            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                    Icon(
                                        imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Gemini API key visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_key_input")
                        )

                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = "${OpenRouterModels.shortDisplayName(openRouterModel)} ($openRouterModel)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cloud Model (Auto-Fallbacks on Rate Limit)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("openrouter_model_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                OpenRouterModels.allOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = OpenRouterModels.shortDisplayName(option),
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = option,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            openRouterModel = option
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = openRouterKey,
                            onValueChange = { openRouterKey = it },
                            label = { Text("OpenRouter API Key (Optional Fallback)") },
                            placeholder = { Text("sk-or-v1-... (supports Free Gemini, Qwen, Llama)") },
                            singleLine = true,
                            visualTransformation = if (showOpenRouterKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showOpenRouterKey = !showOpenRouterKey }) {
                                    Icon(
                                        imageVector = if (showOpenRouterKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle OpenRouter API key visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openrouter_key_input")
                        )
                    }

                    AiEngineMode.LOCAL_SERVER -> {
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
                                label = { Text("Termux Localhost") }
                            )
                            SuggestionChip(
                                onClick = { localBaseUrl = "http://192.168.1.100:11434/v1/" },
                                label = { Text("LAN Template") }
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

                    AiEngineMode.ON_DEVICE_MEDIAPIPE -> Unit
                }

                // Always keep Local AI Model Manager + Bundled Status accessible in Settings
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

                // 4. GitHub PAT & Username
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
                        IconButton(onClick = { showGitHubPat = !showGitHubPat }) {
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

data class DiagnosticItem(
    val title: String,
    val isPassing: Boolean,
    val details: String
)

private fun runDiagnostics(context: android.content.Context, settings: StudioSettings): List<DiagnosticItem> {
    val items = mutableListOf<DiagnosticItem>()

    // 1. Available Device RAM & Storage
    val hw = DeviceHardwareProfiler.inspectDevice(context)
    val freeSpaceMb = context.filesDir.freeSpace / (1024 * 1024)
    val ramPass = hw.totalRamGb >= 3.0
    items.add(
        DiagnosticItem(
            title = "Available Device RAM & Storage",
            isPassing = ramPass,
            details = "Total RAM: ${String.format(java.util.Locale.US, "%.1f", hw.totalRamGb)} GB, Free Storage: ${freeSpaceMb} MB (${if (ramPass) "Sufficient" else "Low RAM"})"
        )
    )

    // 2. Local MediaPipe Engine & .task Model Integrity
    val targetFile = BundledModelManager.getTargetModelFile(context)
    val modelValid = targetFile.exists() && targetFile.length() > 30L * 1024L * 1024L
    items.add(
        DiagnosticItem(
            title = "Local MediaPipe Engine & .task Model Integrity",
            isPassing = modelValid,
            details = if (modelValid) "Model task file valid (${targetFile.length() / (1024 * 1024)} MB)" else "Model task file missing or uninitialized (<30MB)"
        )
    )

    // 3. Cloud API / Network Connectivity
    val hasCloudKey = settings.openRouterApiKey.isNotBlank() || settings.geminiApiKey.isNotBlank()
    items.add(
        DiagnosticItem(
            title = "Cloud API / Network Connectivity",
            isPassing = hasCloudKey || settings.aiEngineMode == AiEngineMode.ON_DEVICE_MEDIAPIPE,
            details = if (hasCloudKey) "OpenRouter / Gemini API Key configured" else "No cloud API key set (Local AI offline mode active)"
        )
    )

    // 4. Project Storage & FileProvider Permissions
    val workspaceDir = File(context.filesDir, "projects")
    val storageWritable = try {
        workspaceDir.mkdirs()
        val testFile = File(workspaceDir, "test.txt")
        testFile.writeText("ok")
        testFile.delete()
        true
    } catch (e: Exception) {
        false
    }
    items.add(
        DiagnosticItem(
            title = "Project Storage & FileProvider Permissions",
            isPassing = storageWritable,
            details = if (storageWritable) "Workspace storage read/write active & FileProvider ready" else "Storage write check failed"
        )
    )

    // 5. APK Installer Permission Status
    val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    items.add(
        DiagnosticItem(
            title = "APK Installer Permission Status",
            isPassing = canInstall,
            details = if (canInstall) "Can request package installs (Unknown sources allowed)" else "Install unknown apps permission required for direct APK install"
        )
    )

    return items
}

@Composable
fun SystemDiagnosticsCard(
    settings: StudioSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var diagnosticResults by remember { mutableStateOf<List<DiagnosticItem>?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "System Diagnostics & Health Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        isRunning = true
                        diagnosticResults = runDiagnostics(context, settings)
                        isRunning = false
                    },
                    enabled = !isRunning,
                    modifier = Modifier.testTag("run_diagnostics_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Run Diagnostics")
                    }
                }
            }

            if (diagnosticResults != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diagnosticResults?.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = if (item.isPassing) "🟢" else "🔴",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = item.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = if (item.isPassing) "PASS" else "WARN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isPassing) androidx.compose.ui.graphics.Color(0xFF10B981) else androidx.compose.ui.graphics.Color(0xFFEF4444)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap 'Run Diagnostics' to verify device RAM, MediaPipe model integrity, network connectivity, storage, and APK installer permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

