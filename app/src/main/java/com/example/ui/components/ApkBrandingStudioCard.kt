package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StudioProject
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette
import java.io.ByteArrayOutputStream

data class LauncherIconPreset(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val vectorXml: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkBrandingStudioCard(
    project: StudioProject,
    onSaveBranding: (
        appName: String,
        packageId: String,
        versionName: String,
        launcherIconBase64: String?,
        launcherIconVectorXml: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalStudioCyberPalette.current

    var isExpanded by rememberSaveable(project.id) { mutableStateOf(false) }

    var appName by remember(project.id, project.appName) { mutableStateOf(project.appName) }
    var packageId by remember(project.id, project.packageName) { mutableStateOf(project.packageName) }
    var versionName by remember(project.id) { mutableStateOf("1.0.0") }

    val iconPresets = remember {
        listOf(
            LauncherIconPreset("rocket", "Rocket", Icons.Default.RocketLaunch, "ic_preset_rocket"),
            LauncherIconPreset("sparkle", "AI Sparkle", Icons.Default.AutoAwesome, "ic_preset_sparkle"),
            LauncherIconPreset("terminal", "Terminal", Icons.Default.Terminal, "ic_preset_terminal"),
            LauncherIconPreset("code", "Code IDE", Icons.Default.Code, "ic_preset_code"),
            LauncherIconPreset("store", "Commerce", Icons.Default.ShoppingBag, "ic_preset_store"),
            LauncherIconPreset("game", "Game", Icons.Default.Gamepad, "ic_preset_game"),
            LauncherIconPreset("palette", "Creative", Icons.Default.Palette, "ic_preset_palette"),
            LauncherIconPreset("shield", "Security", Icons.Default.Security, "ic_preset_shield")
        )
    }

    var selectedPresetIndex by rememberSaveable(project.id) { mutableIntStateOf(0) }
    var customIconBase64 by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var customIconFileName by rememberSaveable(project.id) { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = encodeUriToBase64(context, uri)
            if (base64 != null) {
                customIconBase64 = base64
                customIconFileName = uri.lastPathSegment ?: "custom_icon.png"
            }
        }
    }

    CyberGradientCard(
        cornerRadius = 18.dp,
        borderWidth = 1.4.dp,
        containerColor = palette.cardSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("apk_branding_studio_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "App Identity & Branding",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$appName • $packageId (v$versionName)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("toggle_branding_studio_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse branding" else "Expand branding"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(color = palette.cardBorder)

                    // App Display Name Field
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("App Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("branding_app_name_input")
                    )

                    // Package ID Field
                    OutlinedTextField(
                        value = packageId,
                        onValueChange = { packageId = it },
                        label = { Text("Application Package ID (e.g. com.studio.myapp)") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("branding_package_id_input")
                    )

                    // Version Name Field
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text("Version Name (e.g. 1.0.0)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("branding_version_name_input")
                    )

                    // Launcher Icon Selection
                    Text(
                        text = "Launcher Icon Style",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        iconPresets.forEachIndexed { index, preset ->
                            val isSelected = selectedPresetIndex == index && customIconBase64 == null
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) palette.accentCyan.copy(alpha = 0.2f) else palette.cardSurfaceHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) palette.accentCyan else palette.cardBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedPresetIndex = index
                                        customIconBase64 = null
                                        customIconFileName = null
                                    }
                                    .testTag("preset_icon_${preset.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = preset.icon,
                                        contentDescription = preset.name,
                                        tint = if (isSelected) palette.accentCyan else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) palette.accentCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = palette.accentCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Gallery Custom Icon Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (customIconFileName != null) "Custom: $customIconFileName" else "Or import from Gallery (PNG)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customIconFileName != null) palette.accentCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("pick_custom_icon_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import PNG", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Save Branding CTA
                    Button(
                        onClick = {
                            val activePreset = iconPresets.getOrNull(selectedPresetIndex)
                            onSaveBranding(
                                appName.trim(),
                                packageId.trim(),
                                versionName.trim(),
                                customIconBase64,
                                activePreset?.vectorXml
                            )
                            isExpanded = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accentCyan,
                            contentColor = Color(0xFF03111C)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("save_branding_button")
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply App Identity & Branding", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun encodeUriToBase64(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    } catch (_: Exception) {
        null
    }
}
