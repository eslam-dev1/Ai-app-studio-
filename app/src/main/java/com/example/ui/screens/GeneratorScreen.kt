package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.generator.BundledModelState
import com.example.data.generator.DeviceHardwareProfile
import com.example.data.generator.DeviceHardwareProfiler
import com.example.data.generator.ModelLinkVerificationState
import com.example.model.AiEngineMode
import com.example.model.GenerationUiState
import com.example.model.LocalModelDownloadState
import com.example.model.OpenRouterModels
import com.example.model.StudioProject
import com.example.model.StudioSettings
import com.example.ui.components.CyberCodeFactoryCard
import com.example.ui.components.CyberGradientCard
import com.example.ui.components.LocalAiModelManagerPanel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.LocalStudioCyberPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val quickStartSuggestionPills = listOf(
    "🤖 Build an Android app" to "Build a modern Habit Tracker Android app in Material 3 with daily streaks, toggleable cards, progress bar, and an Add Habit dialog.",
    "📍 Map & Location app" to "Build a Location & Places Explorer app with a live coordinates card, saved favorite spots list, distance badge, and interactive category filters.",
    "🧮 Calculator & Tools" to "Build a sleek Scientific Calculator & Unit Converter app with dark cyber styling, calculation history list, and instant length/weight/currency tabs.",
    "📿 Arabic Azkar app" to "صمم تطبيق أذكار ومسبحة إلكترونية بتصميم Material 3 داكن مع عداد تسبيح تفاعلي وقائمة أذكار الصباح والمساء."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    settings: StudioSettings,
    projects: List<StudioProject>,
    generationState: GenerationUiState,
    isImportingModel: Boolean = false,
    deviceHardwareProfile: DeviceHardwareProfile = DeviceHardwareProfiler.buildProfile(8.0, 4.2),
    localModelDownloadState: LocalModelDownloadState = LocalModelDownloadState.Idle,
    bundledModelState: BundledModelState = BundledModelState.Idle,
    modelVerificationStates: Map<String, ModelLinkVerificationState> = emptyMap(),
    onEngineModeChange: (AiEngineMode) -> Unit,
    onCloudModelChange: (String) -> Unit = {},
    onImportLocalModelUri: (Uri) -> Unit = {},
    onRestoreBuiltInModel: () -> Unit = {},
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
    onOpenSettings: () -> Unit,
    onOpenStarterTemplates: () -> Unit = {},
    onGenerateApp: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalStudioCyberPalette.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var promptText by rememberSaveable { mutableStateOf("") }
    var plusMenuExpanded by remember { mutableStateOf(false) }
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var showLocalModelManagerSheet by rememberSaveable { mutableStateOf(false) }

    val localModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportLocalModelUri(uri)
        }
    }

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                promptText = if (promptText.isBlank()) spoken else "${promptText.trim()} $spoken"
            }
        }
    }

    if (drawerState.isOpen) {
        BackHandler {
            scope.launch { drawerState.close() }
        }
    }

    val isGenerating = generationState is GenerationUiState.Generating
    val hasLocalModelFile = remember(settings.localModelFilePath) {
        val path = settings.localModelFilePath.trim()
        path.isNotBlank() && File(path).exists()
    }
    val buildConfigGemini = com.example.BuildConfig.GEMINI_API_KEY.trim()
        .takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }.orEmpty()
    val isCloudConfigured = settings.openRouterApiKey.isNotBlank() ||
        settings.geminiApiKey.isNotBlank() ||
        buildConfigGemini.isNotBlank()

    val statusPillLabel = when (settings.aiEngineMode) {
        AiEngineMode.ON_DEVICE_MEDIAPIPE -> if (hasLocalModelFile) "🟢 Local AI Ready" else "🟡 Setup Local AI"
        AiEngineMode.CLOUD_OPENROUTER -> if (isCloudConfigured) "🟢 Cloud Ready" else "🟡 Cloud Key"
        AiEngineMode.LOCAL_SERVER -> "🟢 Termux Ready"
    }

    val engineDropdownLabel = when (settings.aiEngineMode) {
        AiEngineMode.ON_DEVICE_MEDIAPIPE -> "Local AI"
        AiEngineMode.CLOUD_OPENROUTER -> "Cloud AI"
        AiEngineMode.LOCAL_SERVER -> "Termux"
    }

    if (showLocalModelManagerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLocalModelManagerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.cardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Local AI Model Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                LocalAiModelManagerPanel(
                    settings = settings,
                    isImportingModel = isImportingModel,
                    hardwareProfile = deviceHardwareProfile,
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
                    onPickModelFile = { localModelPickerLauncher.launch(arrayOf("*/*")) },
                    onUnloadModel = onUnloadLocalModel
                )
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SavedProjectsDrawerContent(
                projects = projects,
                onOpenStarterTemplates = {
                    scope.launch { drawerState.close() }
                    onOpenStarterTemplates()
                },
                onOpenProject = { projectId ->
                    scope.launch { drawerState.close() }
                    onOpenProject(projectId)
                },
                onDeleteProject = onDeleteProject,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                HomeMinimalTopBar(
                    statusPillLabel = statusPillLabel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onTapStatusPill = {
                        if (settings.aiEngineMode == AiEngineMode.ON_DEVICE_MEDIAPIPE) {
                            showLocalModelManagerSheet = true
                        } else {
                            onOpenSettings()
                        }
                    },
                    onOpenSettings = onOpenSettings
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Center Hero Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.cardSurfaceHigh,
                            border = BorderStroke(1.dp, palette.accentCyan.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("home_hero_badge")
                        ) {
                            Text(
                                text = "Offline AI • Local & Cloud Ready",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accentCyan,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Text(
                            text = "Build your ideas with AI",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("home_hero_headline")
                        )
                    }

                    // Floating Prompt Input Card (24.dp radius + Cyber Gradient Border)
                    CyberGradientCard(
                        cornerRadius = 24.dp,
                        borderWidth = 1.8.dp,
                        containerColor = palette.cardSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("floating_prompt_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextField(
                                value = promptText,
                                onValueChange = { promptText = it },
                                enabled = !isGenerating,
                                placeholder = {
                                    Text(
                                        text = "Describe an app and let AI do the rest",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                minLines = 4,
                                maxLines = 8,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app_prompt_input")
                            )

                            // Bottom Card Action Row: "+" left | Engine Dropdown + Voice Mic + Sparkle Send right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Circular "+" Menu Button
                                Box {
                                    Surface(
                                        onClick = { plusMenuExpanded = true },
                                        enabled = !isGenerating,
                                        shape = CircleShape,
                                        color = palette.cardSurfaceHigh,
                                        border = BorderStroke(1.dp, palette.cardBorder),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("prompt_plus_menu_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Quick studio actions",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = plusMenuExpanded,
                                        onDismissRequest = { plusMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Browse Starter Templates") },
                                            leadingIcon = {
                                                Icon(Icons.Default.DashboardCustomize, contentDescription = null)
                                            },
                                            onClick = {
                                                plusMenuExpanded = false
                                                onOpenStarterTemplates()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Local AI Models & HF Token") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Memory, contentDescription = null)
                                            },
                                            onClick = {
                                                plusMenuExpanded = false
                                                showLocalModelManagerSheet = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Saved Projects (${projects.size})") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Menu, contentDescription = null)
                                            },
                                            onClick = {
                                                plusMenuExpanded = false
                                                scope.launch { drawerState.open() }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Studio Settings & Theme") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Settings, contentDescription = null)
                                            },
                                            onClick = {
                                                plusMenuExpanded = false
                                                onOpenSettings()
                                            }
                                        )
                                    }
                                }

                                // Right: Engine Dropdown + Voice Mic + Sparkle Send
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box {
                                        Surface(
                                            onClick = { engineMenuExpanded = true },
                                            enabled = !isGenerating,
                                            shape = RoundedCornerShape(50),
                                            color = palette.cardSurfaceHigh,
                                            border = BorderStroke(1.dp, palette.cardBorder),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("home_engine_dropdown")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = engineDropdownLabel,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ExpandMore,
                                                    contentDescription = "Select AI engine",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = engineMenuExpanded,
                                            onDismissRequest = { engineMenuExpanded = false }
                                        ) {
                                            AiEngineMode.entries.forEach { mode ->
                                                val icon = when (mode) {
                                                    AiEngineMode.ON_DEVICE_MEDIAPIPE -> Icons.Default.Memory
                                                    AiEngineMode.CLOUD_OPENROUTER -> Icons.Default.Cloud
                                                    AiEngineMode.LOCAL_SERVER -> Icons.Default.Terminal
                                                }
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = mode.label,
                                                            fontWeight = if (settings.aiEngineMode == mode) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                                    onClick = {
                                                        engineMenuExpanded = false
                                                        onEngineModeChange(mode)
                                                    }
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Manage Local Models…") },
                                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                                onClick = {
                                                    engineMenuExpanded = false
                                                    showLocalModelManagerSheet = true
                                                }
                                            )
                                            if (settings.aiEngineMode == AiEngineMode.CLOUD_OPENROUTER) {
                                                OpenRouterModels.allOptions.take(4).forEach { cloudModel ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = "Cloud: ${OpenRouterModels.shortDisplayName(cloudModel)}",
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        },
                                                        onClick = {
                                                            engineMenuExpanded = false
                                                            onCloudModelChange(cloudModel)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Voice Input Mic Button
                                    Surface(
                                        onClick = {
                                            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                )
                                                putExtra(
                                                    RecognizerIntent.EXTRA_PROMPT,
                                                    "Describe the Android app you want to build"
                                                )
                                            }
                                            try {
                                                voiceInputLauncher.launch(speechIntent)
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(
                                                    context,
                                                    "Voice input not available on this device",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        enabled = !isGenerating,
                                        shape = CircleShape,
                                        color = palette.cardSurfaceHigh,
                                        border = BorderStroke(1.dp, palette.cardBorder),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("voice_prompt_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Voice prompt input",
                                                tint = palette.accentCyan
                                            )
                                        }
                                    }

                                    // Glowing Sparkle Send Button
                                    val canSend = !isGenerating && promptText.isNotBlank()
                                    Surface(
                                        onClick = { onGenerateApp(promptText) },
                                        enabled = canSend,
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                brush = if (canSend) {
                                                    Brush.linearGradient(
                                                        colors = listOf(CyberPurple, CyberCyan, CyberEmerald)
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            palette.cardBorder,
                                                            palette.cardBorder
                                                        )
                                                    )
                                                }
                                            )
                                            .testTag("generate_app_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Generate Android App",
                                                tint = if (canSend) Color(0xFF041016) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Horizontally Scrollable Quick-Start Suggestion Pills
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_start_pills_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(quickStartSuggestionPills) { (pillLabel, fullPrompt) ->
                            Surface(
                                onClick = { if (!isGenerating) promptText = fullPrompt },
                                shape = RoundedCornerShape(50),
                                color = palette.cardSurface,
                                border = BorderStroke(1.dp, palette.cardBorder),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = pillLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Active Animated Code Factory & Server Card during generation
                    AnimatedVisibility(visible = generationState is GenerationUiState.Generating) {
                        val active = generationState as? GenerationUiState.Generating
                        if (active != null) {
                            val derivedProjectName = remember(promptText) {
                                promptText.trim()
                                    .split(Regex("\\s+"))
                                    .filter { it.isNotBlank() }
                                    .take(3)
                                    .joinToString(" ")
                                    .ifBlank { "Android App" }
                            }
                            CyberCodeFactoryCard(
                                projectName = derivedProjectName,
                                stepIndex = active.stepIndex,
                                totalSteps = 3,
                                stepMessage = active.stepMessage,
                                engineLabel = active.engineLabel,
                                streamingCode = active.streamingPreview,
                                onCancel = onCancelGeneration
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeMinimalTopBar(
    statusPillLabel: String,
    onOpenDrawer: () -> Unit,
    onTapStatusPill: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalStudioCyberPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                onClick = onOpenDrawer,
                shape = CircleShape,
                color = palette.cardSurface,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_projects_drawer_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Saved Projects Drawer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onTapStatusPill,
                shape = RoundedCornerShape(50),
                color = palette.cardSurfaceHigh,
                border = BorderStroke(1.dp, palette.accentEmerald.copy(alpha = 0.45f)),
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .testTag("home_live_status_pill")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusPillLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = palette.cardSurface,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Studio Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedProjectsDrawerContent(
    projects: List<StudioProject>,
    onOpenStarterTemplates: () -> Unit,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val palette = LocalStudioCyberPalette.current

    ModalDrawerSheet(
        drawerContainerColor = palette.cardSurface,
        modifier = Modifier
            .widthIn(max = 330.dp)
            .fillMaxHeight()
            .testTag("saved_projects_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = palette.accentCyan
                    )
                    Text(
                        text = "Saved Projects (${projects.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close drawer")
                }
            }

            Surface(
                onClick = onOpenStarterTemplates,
                shape = RoundedCornerShape(14.dp),
                color = palette.cardSurfaceHigh,
                border = BorderStroke(1.dp, palette.accentCyan.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("drawer_starter_templates_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = null,
                        tint = palette.accentCyan
                    )
                    Column {
                        Text(
                            text = "Starter Templates",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Bottom Nav, Master-Detail, Login Form",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.cardBorder)

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = palette.accentCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(R.string.empty_projects_title),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.empty_projects_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        DrawerSavedProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onDelete = { onDeleteProject(project.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSavedProjectCard(
    project: StudioProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalStudioCyberPalette.current
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val dateLabel = remember(project.updatedAtEpochMs) {
        formatter.format(Date(project.updatedAtEpochMs))
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .testTag("saved_project_card_${project.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onOpen) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open ${project.appName}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${project.appName}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text = project.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${project.files.size} files • ${project.engineMode.shortLabel} • $dateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
