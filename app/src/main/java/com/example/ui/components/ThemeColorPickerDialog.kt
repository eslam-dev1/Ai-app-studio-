package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.preview.Material3SeedPaletteGenerator
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeColorPickerDialog(
    targetThemePath: String,
    initialSeedColor: Color = Color(0xFF0284C7),
    onApplySeedColor: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialSeedColor.red * 255f) }
    var green by remember { mutableFloatStateOf(initialSeedColor.green * 255f) }
    var blue by remember { mutableFloatStateOf(initialSeedColor.blue * 255f) }

    val activeSeedColor = Color(
        red = red.roundToInt().coerceIn(0, 255),
        green = green.roundToInt().coerceIn(0, 255),
        blue = blue.roundToInt().coerceIn(0, 255)
    )
    var hexInput by remember(activeSeedColor) {
        mutableStateOf(Material3SeedPaletteGenerator.formatHex6(activeSeedColor))
    }

    val generatedPalettes = remember(activeSeedColor) {
        Material3SeedPaletteGenerator.generateFromSeed(activeSeedColor)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = activeSeedColor
                )
                Column {
                    Text(
                        text = "Material 3 Theme Builder",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = targetThemePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Pick a seed color to generate harmonious Material 3 Light & Dark ColorSchemes and automatically update Theme.kt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Curated Seed Color Swatches
                Text(
                    text = "Seed Color Presets",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Material3SeedPaletteGenerator.presets.forEach { preset ->
                        val isSelected = Material3SeedPaletteGenerator.formatHex6(activeSeedColor)
                            .equals(preset.seedHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    red = preset.color.red * 255f
                                    green = preset.color.green * 255f
                                    blue = preset.color.blue * 255f
                                }
                                .testTag("seed_preset_${preset.seedHex.removePrefix("#")}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = preset.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Hex Input + Active Seed Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(activeSeedColor)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw
                            Material3SeedPaletteGenerator.parseHexColor(raw)?.let { parsed ->
                                red = parsed.red * 255f
                                green = parsed.green * 255f
                                blue = parsed.blue * 255f
                            }
                        },
                        label = { Text("Seed Hex (#RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_seed_hex_input")
                    )
                }

                // Fine-Tune RGB Sliders
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ColorChannelSlider(
                        label = "R (${red.roundToInt()})",
                        value = red,
                        activeColor = Color(0xFFEF4444),
                        onValueChange = { red = it }
                    )
                    ColorChannelSlider(
                        label = "G (${green.roundToInt()})",
                        value = green,
                        activeColor = Color(0xFF10B981),
                        onValueChange = { green = it }
                    )
                    ColorChannelSlider(
                        label = "B (${blue.roundToInt()})",
                        value = blue,
                        activeColor = Color(0xFF3B82F6),
                        onValueChange = { blue = it }
                    )
                }

                // Generated Light & Dark ColorScheme Previews
                SchemePreviewCard(
                    title = "LightColorScheme Preview",
                    scheme = generatedPalettes.lightScheme
                )
                SchemePreviewCard(
                    title = "DarkColorScheme Preview",
                    scheme = generatedPalettes.darkScheme
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplySeedColor(activeSeedColor) },
                modifier = Modifier.testTag("apply_theme_seed_button")
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Update Theme.kt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(52.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SchemePreviewCard(
    title: String,
    scheme: ColorScheme
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoleSwatch("Primary", scheme.primary, scheme.onPrimary, Modifier.weight(1f))
                RoleSwatch("Container", scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f))
                RoleSwatch("Secondary", scheme.secondary, scheme.onSecondary, Modifier.weight(1f))
                RoleSwatch("Tertiary", scheme.tertiary, scheme.onTertiary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoleSwatch(
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier.height(38.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1
            )
        }
    }
}
