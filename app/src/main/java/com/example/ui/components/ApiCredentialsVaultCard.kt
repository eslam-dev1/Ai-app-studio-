package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.preferences.ApiCredentialValidator
import com.example.data.preferences.ApiKeyVerificationStatus
import com.example.data.preferences.SecureCredentialCipher
import com.example.model.OpenRouterModels
import com.example.ui.theme.LocalStudioCyberPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiCredentialsVaultCard(
    savedGeminiKey: String,
    savedOpenRouterKey: String,
    geminiKeyInput: String,
    openRouterKeyInput: String,
    selectedCloudModel: String,
    isHardwareKeystoreBacked: Boolean,
    geminiVerification: ApiKeyVerificationStatus,
    openRouterVerification: ApiKeyVerificationStatus,
    onGeminiKeyChange: (String) -> Unit,
    onOpenRouterKeyChange: (String) -> Unit,
    onCloudModelChange: (String) -> Unit,
    onVerifyGeminiKey: (String) -> Unit,
    onVerifyOpenRouterKey: (String) -> Unit,
    onSaveCredentials: () -> Unit,
    onClearGeminiKey: () -> Unit,
    onClearOpenRouterKey: () -> Unit,
    onClearAllCredentials: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var showGeminiKey by remember { mutableStateOf(false) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val buildConfigGemini = remember {
        BuildConfig.GEMINI_API_KEY.trim()
            .takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            .orEmpty()
    }

    val geminiValidation = remember(geminiKeyInput) {
        ApiCredentialValidator.validateGeminiKeyFormat(geminiKeyInput)
    }
    val openRouterValidation = remember(openRouterKeyInput) {
        ApiCredentialValidator.validateOpenRouterKeyFormat(openRouterKeyInput)
    }
    val canSaveCredentials = geminiValidation.isValidShape && openRouterValidation.isValidShape

    val hasUnsavedChanges = remember(
        geminiKeyInput,
        openRouterKeyInput,
        savedGeminiKey,
        savedOpenRouterKey
    ) {
        geminiKeyInput.trim() != savedGeminiKey.trim() ||
            openRouterKeyInput.trim() != savedOpenRouterKey.trim()
    }

    val savedKeysCount = listOf(savedGeminiKey, savedOpenRouterKey).count { it.isNotBlank() }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear Saved API Keys?") },
            text = {
                Text(
                    "This removes your stored Gemini and OpenRouter API keys from encrypted DataStore persistence."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAllCredentials()
                    },
                    modifier = Modifier.testTag("confirm_clear_credentials_button")
                ) {
                    Text("Clear Keys", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("api_credentials_vault_card"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, palette.accentCyan.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Encryption Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.settings_vault_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_vault_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (savedKeysCount > 0) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        if (savedKeysCount > 0) {
                            palette.accentEmerald.copy(alpha = 0.5f)
                        } else {
                            palette.cardBorder
                        }
                    ),
                    modifier = Modifier.testTag("vault_persistence_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (savedKeysCount > 0) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (savedKeysCount > 0) {
                                "$savedKeysCount Saved"
                            } else {
                                "No Keys"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (savedKeysCount > 0) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Security / Persistence Info Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.cardSurfaceHigh,
                border = BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = palette.accentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isHardwareKeystoreBacked) {
                            "Persisted across sessions in Jetpack DataStore with Android Keystore AES-256-GCM encryption."
                        } else {
                            "Persisted across sessions in Jetpack DataStore with AES-256-GCM credential encryption."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Google Gemini API Key Section
            CredentialKeySection(
                providerTitle = "Gemini API Key (Google AI Studio)",
                savedKey = savedGeminiKey,
                envFallbackActive = savedGeminiKey.isBlank() && buildConfigGemini.isNotBlank(),
                inputValue = geminiKeyInput,
                onInputChange = onGeminiKeyChange,
                placeholder = "AIzaSy... (Google AI Studio API key)",
                showPlaintext = showGeminiKey,
                onToggleVisibility = { showGeminiKey = !showGeminiKey },
                onPasteFromClipboard = {
                    val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                    if (clip.isNotEmpty()) onGeminiKeyChange(clip)
                },
                onClearKey = onClearGeminiKey,
                formatWarning = geminiValidation.warningMessage,
                helperHint = geminiValidation.helperHint,
                verificationStatus = geminiVerification,
                onVerifyClick = { onVerifyGeminiKey(geminiKeyInput) },
                inputTestTag = "gemini_key_input",
                visibilityTestTag = "toggle_gemini_visibility_button",
                pasteTestTag = "paste_gemini_key_button",
                clearTestTag = "clear_gemini_key_button",
                verifyTestTag = "verify_gemini_key_button",
                validationFeedbackTestTag = "gemini_validation_feedback"
            )

            HorizontalDivider(color = palette.cardBorder)

            // 2. OpenRouter API Key Section
            CredentialKeySection(
                providerTitle = "OpenRouter API Key",
                savedKey = savedOpenRouterKey,
                envFallbackActive = false,
                inputValue = openRouterKeyInput,
                onInputChange = onOpenRouterKeyChange,
                placeholder = "sk-or-v1-... (Gemini, Qwen, Llama & auto-fallback)",
                showPlaintext = showOpenRouterKey,
                onToggleVisibility = { showOpenRouterKey = !showOpenRouterKey },
                onPasteFromClipboard = {
                    val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                    if (clip.isNotEmpty()) onOpenRouterKeyChange(clip)
                },
                onClearKey = onClearOpenRouterKey,
                formatWarning = openRouterValidation.warningMessage,
                helperHint = openRouterValidation.helperHint,
                verificationStatus = openRouterVerification,
                onVerifyClick = { onVerifyOpenRouterKey(openRouterKeyInput) },
                inputTestTag = "openrouter_key_input",
                visibilityTestTag = "toggle_openrouter_visibility_button",
                pasteTestTag = "paste_openrouter_key_button",
                clearTestTag = "clear_openrouter_key_button",
                verifyTestTag = "verify_openrouter_key_button",
                validationFeedbackTestTag = "openrouter_validation_feedback"
            )

            HorizontalDivider(color = palette.cardBorder)

            // 3. Preferred Cloud Model Selector
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "${OpenRouterModels.shortDisplayName(selectedCloudModel)} ($selectedCloudModel)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preferred Cloud Model (Auto-Fallback on Rate Limit)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                    },
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
                                onCloudModelChange(option)
                                modelDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !canSaveCredentials) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_save_blocked_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Fix invalid API key format above before saving to DataStore.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Save & Clear Vault Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSaveCredentials,
                    enabled = canSaveCredentials,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_cloud_credentials_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !canSaveCredentials -> "Fix Invalid Key"
                            hasUnsavedChanges -> "Save Credentials"
                            else -> "Saved to DataStore"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (savedKeysCount > 0 || geminiKeyInput.isNotBlank() || openRouterKeyInput.isNotBlank()) {
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("clear_cloud_credentials_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear saved API credentials",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialKeySection(
    providerTitle: String,
    savedKey: String,
    envFallbackActive: Boolean,
    inputValue: String,
    onInputChange: (String) -> Unit,
    placeholder: String,
    showPlaintext: Boolean,
    onToggleVisibility: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onClearKey: () -> Unit,
    formatWarning: String?,
    helperHint: String?,
    verificationStatus: ApiKeyVerificationStatus,
    onVerifyClick: () -> Unit,
    inputTestTag: String,
    visibilityTestTag: String,
    pasteTestTag: String,
    clearTestTag: String,
    verifyTestTag: String,
    validationFeedbackTestTag: String
) {
    val palette = LocalStudioCyberPalette.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = providerTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val badgeText = when {
                savedKey.isNotBlank() -> SecureCredentialCipher.maskSecret(savedKey)
                envFallbackActive -> "BuildConfig Secret"
                else -> "Not Set"
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (savedKey.isNotBlank() || envFallbackActive) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (savedKey.isNotBlank() || envFallbackActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        OutlinedTextField(
            value = inputValue,
            onValueChange = onInputChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = if (showPlaintext) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPasteFromClipboard,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(pasteTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste $providerTitle from clipboard",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(visibilityTestTag)
                    ) {
                        Icon(
                            imageVector = if (showPlaintext) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = "Toggle $providerTitle visibility"
                        )
                    }
                }
            },
            isError = formatWarning != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag)
        )

        if (formatWarning != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag(validationFeedbackTestTag)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Invalid key format",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = formatWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else if (helperHint != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag(validationFeedbackTestTag)
            ) {
                if (inputValue.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Valid key format",
                        tint = palette.accentEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = helperHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inputValue.isNotEmpty()) {
                        palette.accentEmerald
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onVerifyClick,
                enabled = inputValue.isNotBlank() &&
                    formatWarning == null &&
                    verificationStatus !is ApiKeyVerificationStatus.Verifying,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag(verifyTestTag)
            ) {
                if (verificationStatus is ApiKeyVerificationStatus.Verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying…")
                } else {
                    Text("Test Key")
                }
            }

            if (savedKey.isNotBlank() || inputValue.isNotBlank()) {
                TextButton(
                    onClick = onClearKey,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag(clearTestTag)
                ) {
                    Text("Remove")
                }
            }
        }

        AnimatedVisibility(
            visible = verificationStatus is ApiKeyVerificationStatus.Valid ||
                verificationStatus is ApiKeyVerificationStatus.Invalid
        ) {
            when (verificationStatus) {
                is ApiKeyVerificationStatus.Valid -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, palette.accentEmerald.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = palette.accentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${verificationStatus.provider}: ${verificationStatus.detail}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                is ApiKeyVerificationStatus.Invalid -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = verificationStatus.errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
