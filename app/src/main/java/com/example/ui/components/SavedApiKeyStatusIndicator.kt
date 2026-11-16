package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.SavedApiKeyHealthState
import com.example.ui.theme.LocalStudioCyberPalette

@Composable
fun SavedApiKeyStatusIndicator(
    healthState: SavedApiKeyHealthState,
    onVerifySavedKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current

    val pulseTransition = rememberInfiniteTransition(label = "api_status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val dotColor by animateColorAsState(
        targetValue = when (healthState) {
            is SavedApiKeyHealthState.Verified -> palette.accentEmerald
            is SavedApiKeyHealthState.Checking -> palette.accentCyan
            is SavedApiKeyHealthState.Error -> MaterialTheme.colorScheme.error
            SavedApiKeyHealthState.NotConfigured -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        label = "status_dot_color"
    )

    val borderColor by animateColorAsState(
        targetValue = when (healthState) {
            is SavedApiKeyHealthState.Verified -> palette.accentEmerald.copy(alpha = 0.6f)
            is SavedApiKeyHealthState.Checking -> palette.accentCyan.copy(alpha = 0.55f)
            is SavedApiKeyHealthState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            SavedApiKeyHealthState.NotConfigured -> palette.cardBorder
        },
        label = "status_border_color"
    )

    val containerColor = when (healthState) {
        is SavedApiKeyHealthState.Verified -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        is SavedApiKeyHealthState.Checking -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        is SavedApiKeyHealthState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        SavedApiKeyHealthState.NotConfigured -> palette.cardSurface
    }

    val dotDescription = when (healthState) {
        is SavedApiKeyHealthState.Verified -> "API key verified and connected"
        is SavedApiKeyHealthState.Checking -> "Verifying saved API key"
        is SavedApiKeyHealthState.Error -> "Saved API key verification failed"
        SavedApiKeyHealthState.NotConfigured -> "No saved API key configured"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("saved_api_key_status_indicator")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visual Status Dot / Spinner Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.16f))
                    .semantics { contentDescription = dotDescription }
                    .testTag("saved_api_key_status_dot")
            ) {
                when (healthState) {
                    is SavedApiKeyHealthState.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .alpha(pulseAlpha),
                            strokeWidth = 2.2.dp,
                            color = dotColor
                        )
                    }
                    is SavedApiKeyHealthState.Verified -> {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = dotColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    is SavedApiKeyHealthState.Error -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = dotColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    SavedApiKeyHealthState.NotConfigured -> {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Status Headline, Key & Provider Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .alpha(if (healthState is SavedApiKeyHealthState.Checking) pulseAlpha else 1f)
                    )
                    Text(
                        text = when (healthState) {
                            is SavedApiKeyHealthState.Verified -> "Connected • ${healthState.providerLabel}"
                            is SavedApiKeyHealthState.Checking -> "Verifying ${healthState.providerLabel}…"
                            is SavedApiKeyHealthState.Error -> "Verification Failed • ${healthState.providerLabel}"
                            SavedApiKeyHealthState.NotConfigured -> "No Saved Cloud API Key"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (healthState) {
                            is SavedApiKeyHealthState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("saved_api_key_status_label")
                    )
                }

                Text(
                    text = when (healthState) {
                        is SavedApiKeyHealthState.Verified ->
                            "${healthState.maskedKey} • ${healthState.detail} (${healthState.latencyMs} ms)"
                        is SavedApiKeyHealthState.Checking ->
                            "Performing lightweight test request for ${healthState.maskedKey}…"
                        is SavedApiKeyHealthState.Error ->
                            "${healthState.maskedKey} • ${healthState.message}"
                        SavedApiKeyHealthState.NotConfigured ->
                            "Save a Gemini or OpenRouter key below to run a live provider check."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Re-test / Retry Button (shown whenever a key is configured)
            if (healthState !is SavedApiKeyHealthState.NotConfigured) {
                FilledTonalButton(
                    onClick = onVerifySavedKey,
                    enabled = healthState !is SavedApiKeyHealthState.Checking,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("verify_saved_key_status_button")
                ) {
                    Icon(
                        imageVector = if (healthState is SavedApiKeyHealthState.Verified) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Refresh
                        },
                        contentDescription = "Verify saved API key",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (healthState) {
                            is SavedApiKeyHealthState.Checking -> "Testing"
                            is SavedApiKeyHealthState.Error -> "Retry"
                            is SavedApiKeyHealthState.Verified -> "Re-test"
                            else -> "Verify"
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}
