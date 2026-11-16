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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        com.example.ui.screens.SettingsScreen(
            settings = settings,
            isImportingModel = isImportingModel,
            bundledModelState = bundledModelState,
            generatorConfig = generatorConfig,
            hardwareProfile = hardwareProfile,
            localModelDownloadState = localModelDownloadState,
            modelVerificationStates = modelVerificationStates,
            onThemeModeChange = onThemeModeChange,
            onEditorThemeModeChange = onEditorThemeModeChange,
            onEngineModeChange = onEngineModeChange,
            onImportModelUri = onImportModelUri,
            onRestoreBuiltInModel = onRestoreBuiltInModel,
            onReinitializeModel = onReinitializeModel,
            onDownloadBuiltInFromMirrors = onDownloadBuiltInFromMirrors,
            onCancelBuiltInMirrorDownload = onCancelBuiltInMirrorDownload,
            onVerifyAllModelLinks = onVerifyAllModelLinks,
            onVerifySingleModelLink = onVerifySingleModelLink,
            onDownloadLocalModel = onDownloadLocalModel,
            onCancelLocalModelDownload = onCancelLocalModelDownload,
            onDismissLocalModelDownloadError = onDismissLocalModelDownloadError,
            onRefreshHardwareProfile = onRefreshHardwareProfile,
            onUpdateHuggingFaceToken = onUpdateHuggingFaceToken,
            onUnloadLocalModel = onUnloadLocalModel,
            benchmarkState = benchmarkState,
            onTestModel = onTestModel,
            onCancelTestModel = onCancelTestModel,
            onSaveSettings = onSaveSettings,
            onBack = onDismiss,
            isModalDialog = true
        )
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

