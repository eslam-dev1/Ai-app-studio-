package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.generator.BundledModelManager
import com.example.data.generator.BundledModelState
import com.example.data.generator.DeviceHardwareProfile
import com.example.data.generator.DeviceHardwareProfiler
import com.example.data.generator.LocalModelCatalog
import com.example.data.generator.LocalModelSpecialty
import com.example.data.generator.ModelLinkVerificationState
import com.example.model.LocalModelDownloadState
import com.example.model.StudioSettings
import com.example.ui.theme.AmberWarning
import java.io.File
import java.util.Locale

@Composable
fun LocalAiModelManagerPanel(
    settings: StudioSettings,
    isImportingModel: Boolean,
    hardwareProfile: DeviceHardwareProfile,
    downloadState: LocalModelDownloadState,
    bundledModelState: BundledModelState,
    modelVerificationStates: Map<String, ModelLinkVerificationState>,
    onRefreshHardwareProfile: () -> Unit,
    onRestoreBuiltInModel: () -> Unit,
    onDownloadBuiltInFromMirrors: () -> Unit,
    onCancelBuiltInMirrorDownload: () -> Unit,
    onVerifyAllModelLinks: (Boolean) -> Unit,
    onVerifySingleModelLink: (String, Boolean) -> Unit,
    onUpdateHuggingFaceToken: (String) -> Unit,
    onDownloadSelectedModel: (
        url: String,
        displayName: String,
        fileNameHint: String,
        expectedSizeBytes: Long,
        presetId: String?
    ) -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownloadError: () -> Unit,
    onPickModelFile: () -> Unit,
    onUnloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasModelLoaded = remember(settings.localModelFilePath) {
        val path = settings.localModelFilePath.trim()
        path.isNotBlank() && File(path).exists()
    }

    val formattedSize = remember(settings.localModelSizeBytes) {
        formatLocalModelSize(settings.localModelSizeBytes)
    }

    val recommendedPreset = remember(hardwareProfile) {
        LocalModelCatalog.recommendPreset(hardwareProfile)
    }

    var selectedOptionId by rememberSaveable(recommendedPreset.id) {
        mutableStateOf(recommendedPreset.id)
    }
    var selectedSpecialty by rememberSaveable { mutableStateOf(LocalModelSpecialty.ALL) }
    val suggestionsReport = remember(hardwareProfile, selectedSpecialty) {
        LocalModelCatalog.buildSuggestionsForProfile(hardwareProfile, selectedSpecialty)
    }
    var customModelUrl by rememberSaveable { mutableStateOf("") }
    var hfTokenExpanded by rememberSaveable { mutableStateOf(false) }
    var hfTokenDraft by rememberSaveable { mutableStateOf(settings.huggingFaceToken) }
    var showHfToken by remember { mutableStateOf(false) }

    LaunchedEffect(settings.huggingFaceToken) {
        if (settings.huggingFaceToken != hfTokenDraft.trim()) {
            hfTokenDraft = settings.huggingFaceToken
        }
    }

    LaunchedEffect(Unit) {
        onVerifyAllModelLinks(false)
    }

    val selectedPreset = remember(selectedOptionId) {
        LocalModelCatalog.findById(selectedOptionId)
    }
    val selectedVerificationState = selectedPreset?.let { modelVerificationStates[it.id] }
    val oomWarning = remember(hardwareProfile, selectedPreset) {
        DeviceHardwareProfiler.getOomWarningForSelection(hardwareProfile, selectedPreset)
    }

    val context = LocalContext.current
    val isAssetBundled = remember(context) {
        BundledModelManager.hasBundledModelAsset(context)
    }

    val isDownloading = downloadState is LocalModelDownloadState.Downloading ||
        bundledModelState is BundledModelState.DownloadingFromMirror
    val isInitializing = downloadState is LocalModelDownloadState.Initializing ||
        bundledModelState is BundledModelState.Checking ||
        bundledModelState is BundledModelState.Extracting ||
        bundledModelState is BundledModelState.Initializing
    val isBusy = isImportingModel || isDownloading || isInitializing
    val isBuiltInLoaded = hasModelLoaded &&
        settings.localModelFilePath.trim().endsWith(BundledModelManager.BUNDLED_MODEL_FILE_NAME)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("local_ai_model_manager_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Local AI Model Catalog (${LocalModelCatalog.allPresets.size} Models)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (hasModelLoaded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.testTag("local_model_status_badge")
                ) {
                    Text(
                        text = if (hasModelLoaded) "Ready Offline" else "No Model",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasModelLoaded) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Phone RAM Hardware Capability Profiler Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy) { onRefreshHardwareProfile() }
                    .testTag("hardware_capability_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hardwareProfile.formatSummaryPill(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh device RAM info",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Currently Loaded Model Summary
            if (hasModelLoaded && !isBusy) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Loaded: ${settings.localModelDisplayName.ifBlank { File(settings.localModelFilePath).name }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Size: $formattedSize • MaxTokens: 2048 • Temp: 0.15 • TopK: 40",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onUnloadModel,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("unload_local_model_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Unload and delete local model",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unload", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else if (!isBuiltInLoaded && !isBusy && isAssetBundled) {
                FilledTonalButton(
                    onClick = onRestoreBuiltInModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("restore_builtin_model_button")
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Built-in Offline Model (built_in_coder.task)", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Smart Quick Picks & Specialty Filter Chips
            LocalAiSuggestionsHeader(
                suggestions = suggestionsReport,
                selectedOptionId = selectedOptionId,
                selectedSpecialty = selectedSpecialty,
                isBusy = isBusy,
                onSelectSpecialty = { selectedSpecialty = it },
                onQuickPickModel = { picked ->
                    onDismissDownloadError()
                    selectedOptionId = picked.id
                    onVerifySingleModelLink(picked.id, false)
                }
            )

            // Smart Tiered Model Selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select On-Device Model (${suggestionsReport.filteredModels.size} Shown):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { onVerifyAllModelLinks(true) },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("verify_all_model_links_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-verify Hugging Face model links",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                        .testTag("local_model_catalog_scroll_list"),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    suggestionsReport.filteredModels.forEach { preset ->
                        val isSelected = selectedOptionId == preset.id
                        val isRecommended = preset.id == recommendedPreset.id
                        val verificationState = modelVerificationStates[preset.id]
                        val unreachableState = verificationState as? ModelLinkVerificationState.Unreachable
                        val isAuthRequired = unreachableState?.requiresLicenseAcceptance == true
                        val isHardUnreachable = unreachableState != null && !isAuthRequired
                        val verifiedState = verificationState as? ModelLinkVerificationState.Verified
                        val isVerifying = verificationState is ModelLinkVerificationState.Verifying
                        val effectiveSizeLabel = verifiedState?.exactSizeLabel ?: preset.approxSizeLabel

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isHardUnreachable -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = when {
                                    isHardUnreachable -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !isBusy && !isHardUnreachable) {
                                    onDismissDownloadError()
                                    selectedOptionId = preset.id
                                    onVerifySingleModelLink(preset.id, false)
                                }
                                .testTag("model_tier_card_${preset.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    enabled = !isBusy && !isHardUnreachable,
                                    onClick = if (!isBusy && !isHardUnreachable) {
                                        {
                                            onDismissDownloadError()
                                            selectedOptionId = preset.id
                                            onVerifySingleModelLink(preset.id, false)
                                        }
                                    } else null
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "${preset.tierTitle} (${preset.targetRamHint})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isRecommended) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "Recommended",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = preset.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isHardUnreachable) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = effectiveSizeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (verifiedState != null) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                    if (preset.bestForSummary.isNotBlank()) {
                                        Text(
                                            text = preset.bestForSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                        ) {
                                            Text(
                                                text = preset.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        when {
                                            verifiedState != null -> {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Verified,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Text(
                                                            text = "Verified • ${verifiedState.exactSizeLabel}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            isVerifying -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                                    Text(
                                                        text = "Verifying…",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            isAuthRequired -> {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AmberWarning.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "Gemma License Needed",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            isHardUnreachable -> {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = "Unavailable: ${unreachableState?.reason.orEmpty()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Custom URL Option
                    val isCustomSelected = selectedOptionId == LocalModelCatalog.CUSTOM_URL_OPTION_ID
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustomSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = if (isCustomSelected) 1.5.dp else 1.dp,
                            color = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isBusy) {
                                onDismissDownloadError()
                                selectedOptionId = LocalModelCatalog.CUSTOM_URL_OPTION_ID
                            }
                            .testTag("model_tier_card_custom_url")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RadioButton(
                                    selected = isCustomSelected,
                                    onClick = if (!isBusy) {
                                        {
                                            onDismissDownloadError()
                                            selectedOptionId = LocalModelCatalog.CUSTOM_URL_OPTION_ID
                                        }
                                    } else null
                                )
                                Column {
                                    Text(
                                        text = "Custom .task / .bin URL",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Download any compatible LiteRT / MediaPipe .task or .bin URL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isCustomSelected) {
                                OutlinedTextField(
                                    value = customModelUrl,
                                    onValueChange = { customModelUrl = it },
                                    enabled = !isBusy,
                                    label = { Text("Direct .task / .bin HTTPS URL") },
                                    placeholder = { Text("https://huggingface.co/.../model.task") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_model_url_input")
                                )
                            }
                        }
                    }
                }
            }

            // Collapsible Optimization Guide & Power-User Local AI Suggestions
            LocalAiOptimizationTipsCard(suggestions = suggestionsReport)

            // Collapsible Hugging Face Token for Gated Google Gemma Models
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !isBusy) { hfTokenExpanded = !hfTokenExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("hf_token_collapsible_toggle"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Hugging Face Token (Gated Google Gemma Access)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hfTokenDraft.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Saved",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = if (hfTokenExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (hfTokenExpanded) "Collapse Hugging Face token" else "Expand Hugging Face token",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = hfTokenExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = hfTokenDraft,
                                onValueChange = { updated ->
                                    hfTokenDraft = updated
                                    onUpdateHuggingFaceToken(updated)
                                },
                                enabled = !isBusy,
                                label = { Text("Hugging Face Access Token") },
                                placeholder = { Text("hf_...") },
                                singleLine = true,
                                visualTransformation = if (showHfToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showHfToken = !showHfToken }) {
                                        Icon(
                                            imageVector = if (showHfToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showHfToken) "Hide Hugging Face token" else "Show Hugging Face token"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hf_token_input")
                            )
                            Text(
                                text = "Pre-filled with your Hugging Face token for official gated Gemma models. You can edit or clear it anytime.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (oomWarning != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AmberWarning.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_model_oom_warning_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Out of memory safety warning",
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = oomWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Download / Import / Initialization Progress or Error UI
            when {
                isImportingModel -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Copying model to app storage (context.filesDir/models/)…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_model_import_progress")
                        )
                    }
                }
                downloadState is LocalModelDownloadState.Downloading -> {
                    val downloadedMb = downloadState.downloadedBytes.toDouble() / (1024.0 * 1024.0)
                    val totalMb = downloadState.totalBytes.toDouble() / (1024.0 * 1024.0)
                    val progressText = if (totalMb > 0.0) {
                        String.format(
                            Locale.US,
                            "Downloading %s: %.1f MB / %.1f MB (%d%%)",
                            downloadState.modelName,
                            downloadedMb,
                            totalMb,
                            downloadState.percent
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "Downloading %s: %.1f MB…",
                            downloadState.modelName,
                            downloadedMb
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onCancelDownload) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Download", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (downloadState.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("local_model_download_progress")
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("local_model_download_progress")
                                )
                            }
                        }
                    }
                }
                downloadState is LocalModelDownloadState.Initializing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Initializing MediaPipe LlmInference for ${downloadState.modelName}…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                downloadState is LocalModelDownloadState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            val selectedUnreachable = selectedVerificationState as? ModelLinkVerificationState.Unreachable
            val selectedHardUnreachable = selectedUnreachable != null && !selectedUnreachable.requiresLicenseAcceptance
            val selectedVerified = selectedVerificationState as? ModelLinkVerificationState.Verified
            val downloadSizeLabel = selectedVerified?.exactSizeLabel ?: selectedPreset?.approxSizeLabel
            if (selectedUnreachable?.requiresLicenseAcceptance == true && downloadState !is LocalModelDownloadState.Error) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AmberWarning.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemma_license_notice_banner")
                ) {
                    Text(
                        text = selectedUnreachable.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (selectedPreset != null) {
                            onDownloadSelectedModel(
                                selectedVerified?.resolvedUrl ?: selectedPreset.downloadUrl,
                                selectedPreset.displayName,
                                selectedVerified?.resolvedFileName ?: selectedPreset.fileName,
                                selectedVerified?.exactSizeBytes ?: selectedPreset.approxSizeBytes,
                                selectedPreset.id
                            )
                        } else {
                            val cleanCustom = customModelUrl.trim()
                            val fileName = cleanCustom.substringBefore('?').substringAfterLast('/').ifBlank { "custom-model.task" }
                            onDownloadSelectedModel(cleanCustom, fileName, fileName, 0L, null)
                        }
                    },
                    enabled = !isBusy && !selectedHardUnreachable && (selectedPreset != null || customModelUrl.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("download_selected_model_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPreset != null && downloadSizeLabel != null) {
                            "Download Selected Model ($downloadSizeLabel)"
                        } else {
                            "Download Selected Model"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onPickModelFile,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("select_local_model_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Select Model (.task / .bin) from Storage",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun formatLocalModelSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "Unknown size"
    val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
