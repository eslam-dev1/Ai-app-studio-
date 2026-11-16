package com.example.ui.components

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.generator.GenerationRepositoryConfig
import com.example.model.AiEngineMode

@Composable
fun AppGenerationEngineSwitcherCard(
    selectedMode: AiEngineMode,
    generatorConfig: GenerationRepositoryConfig,
    onSelectEngineMode: (AiEngineMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGeminiCloudActive = selectedMode == AiEngineMode.CLOUD_OPENROUTER
    val isLocalOfflineActive = selectedMode == AiEngineMode.ON_DEVICE_MEDIAPIPE
    val isLocalServerActive = selectedMode == AiEngineMode.LOCAL_SERVER

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_engine_switcher_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "App Generation Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isGeminiCloudActive) {
                            "Active: Gemini Cloud API"
                        } else if (isLocalServerActive) {
                            "Active: Local Server (Ollama / Termux)"
                        } else {
                            "Active: Local AI (Offline)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isGeminiCloudActive,
                    onCheckedChange = { useCloud ->
                        onSelectEngineMode(
                            if (useCloud) AiEngineMode.CLOUD_OPENROUTER
                            else AiEngineMode.ON_DEVICE_MEDIAPIPE
                        )
                    },
                    thumbContent = {
                        Icon(
                            imageVector = if (isGeminiCloudActive) {
                                Icons.Default.AutoAwesome
                            } else {
                                Icons.Default.Memory
                            },
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics {
                            contentDescription = "Toggle between Local AI (Offline) and Gemini Cloud API"
                        }
                        .testTag("settings_engine_toggle_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EngineOptionTile(
                    title = "Local AI (Offline)",
                    subtitle = "MediaPipe on-device",
                    icon = Icons.Default.Memory,
                    selected = isLocalOfflineActive,
                    onClick = { onSelectEngineMode(AiEngineMode.ON_DEVICE_MEDIAPIPE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_switcher_local_ai_offline")
                )

                EngineOptionTile(
                    title = "Gemini Cloud API",
                    subtitle = "Google Gemini Cloud",
                    icon = Icons.Default.AutoAwesome,
                    selected = isGeminiCloudActive,
                    onClick = { onSelectEngineMode(AiEngineMode.CLOUD_OPENROUTER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_switcher_gemini_cloud_api")
                )
            }

            // Live Repository Configuration status indicator
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (generatorConfig.isConfiguredReady) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_repository_config_status")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (generatorConfig.isConfiguredReady) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = if (generatorConfig.isConfiguredReady) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "Repository: ${generatorConfig.target.displayTitle}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${generatorConfig.activeDataSourceName} • ${generatorConfig.statusDetail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Secondary advanced option for LAN / Termux Local Server
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Other backend:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = isLocalServerActive,
                    onClick = {
                        onSelectEngineMode(
                            if (isLocalServerActive) AiEngineMode.ON_DEVICE_MEDIAPIPE
                            else AiEngineMode.LOCAL_SERVER
                        )
                    },
                    label = {
                        Text(
                            text = "Local Server (Ollama / Termux)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_mode_local_server")
                )
            }
        }
    }
}

@Composable
private fun EngineOptionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 68.dp)
            .minimumInteractiveComponentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "$title selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
