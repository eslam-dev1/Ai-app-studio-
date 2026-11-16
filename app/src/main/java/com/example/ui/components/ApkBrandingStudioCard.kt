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

    val detectedVersionName = remember(project.files) {
        val gradle = project.files.firstOrNull {
            it.path.endsWith("app/build.gradle.kts") || it.path.endsWith("app/build.gradle")
        }?.content.orEmpty()
        Regex("""versionName\s*=?\s*["']([^"']+)["']""").find(gradle)?.groupValues?.getOrNull(1)
            ?.ifBlank { null } ?: "1.0.0"
    }

    var appName by remember(project.id, project.appName) { mutableStateOf(project.appName) }
    var packageId by remember(project.id, project.packageName) { mutableStateOf(project.packageName) }
    var versionName by remember(project.id, detectedVersionName) { mutableStateOf(detectedVersionName) }

    val iconPresets = remember {
        listOf(
            LauncherIconPreset(
                id = "rocket",
                name = "Rocket",
                icon = Icons.Default.RocketLaunch,
                vectorXml = buildAdaptiveVectorXml("#38BDF8", "M12,2.5C15.5,6 16.5,11 16.5,15L19,17.5L19,19.5L15,18.2L13.5,21H10.5L9,18.2L5,19.5V17.5L7.5,15C7.5,11 8.5,6 12,2.5ZM12,8.5A1.8,1.8 0 1,0 12,12.1A1.8,1.8 0 1,0 12,8.5Z")
            ),
            LauncherIconPreset(
                id = "sparkle",
                name = "AI Sparkle",
                icon = Icons.Default.AutoAwesome,
                vectorXml = buildAdaptiveVectorXml("#A855F7", "M12,2L14.4,8.6L21,11L14.4,13.4L12,20L9.6,13.4L3,11L9.6,8.6L12,2ZM19,3L19.9,5.1L22,6L19.9,6.9L19,9L18.1,6.9L16,6L18.1,5.1L19,3Z")
            ),
            LauncherIconPreset(
                id = "terminal",
                name = "Terminal",
                icon = Icons.Default.Terminal,
                vectorXml = buildAdaptiveVectorXml("#10B981", "M4,5H20A2,2 0 0,1 22,7V17A2,2 0 0,1 20,19H4A2,2 0 0,1 2,17V7A2,2 0 0,1 4,5ZM6.5,9L10.5,12L6.5,15L7.9,16.4L13.3,12L7.9,7.6L6.5,9ZM13,15V17H18V15H13Z")
            ),
            LauncherIconPreset(
                id = "code",
                name = "Code IDE",
                icon = Icons.Default.Code,
                vectorXml = buildAdaptiveVectorXml("#2563EB", "M9.4,16.6L4.8,12L9.4,7.4L8,6L2,12L8,18L9.4,16.6ZM14.6,16.6L19.2,12L14.6,7.4L16,6L22,12L16,18L14.6,16.6Z")
            ),
            LauncherIconPreset(
                id = "store",
                name = "Commerce",
                icon = Icons.Default.ShoppingBag,
                vectorXml = buildAdaptiveVectorXml("#F59E0B", "M18,6H16A4,4 0 0,0 8,6H6A2,2 0 0,0 4,8V20A2,2 0 0,0 6,22H18A2,2 0 0,0 20,20V8A2,2 0 0,0 18,6ZM12,4A2,2 0 0,1 14,6H10A2,2 0 0,1 12,4ZM12,13A3,3 0 0,1 9,10H11A1,1 0 0,0 13,10H15A3,3 0 0,1 12,13Z")
            ),
            LauncherIconPreset(
                id = "game",
                name = "Game",
                icon = Icons.Default.Gamepad,
                vectorXml = buildAdaptiveVectorXml("#EC4899", "M7,6H17A5,5 0 0,1 22,11V15A3,3 0 0,1 16.6,16.8L14.8,14.5H9.2L7.4,16.8A3,3 0 0,1 2,15V11A5,5 0 0,1 7,6ZM6,10V12H8V14H10V12H12V10H10V8H8V10H6ZM16.5,9.5A1,1 0 1,0 16.5,11.5A1,1 0 1,0 16.5,9.5ZM18.5,11.5A1,1 0 1,0 18.5,13.5A1,1 0 1,0 18.5,11.5Z")
            ),
            LauncherIconPreset(
                id = "palette",
                name = "Creative",
                icon = Icons.Default.Palette,
                vectorXml = buildAdaptiveVectorXml("#06B6D4", "M12,3A9,9 0 0,0 3,12A9,9 0 0,0 12,21A1.5,1.5 0 0,0 13.5,19.5C13.5,19.1 13.3,18.7 13.1,18.4C12.8,18.1 12.7,17.8 12.7,17.4A1.5,1.5 0 0,1 14.2,15.9H16A5,5 0 0,0 21,10.9C21,6.5 17,3 12,3ZM6.5,12A1.5,1.5 0 1,1 8,10.5A1.5,1.5 0 0,1 6.5,12ZM9.5,8A1.5,1.5 0 1,1 11,6.5A1.5,1.5 0 0,1 9.5,8ZM14.5,8A1.5,1.5 0 1,1 16,6.5A1.5,1.5 0 0,1 14.5,8ZM17.5,12A1.5,1.5 0 1,1 19,10.5A1.5,1.5 0 0,1 17.5,12Z")
            ),
            LauncherIconPreset(
                id = "shield",
                name = "Security",
                icon = Icons.Default.Security,
                vectorXml = buildAdaptiveVectorXml("#6366F1", "M12,2L4,5.5V11.5C4,16.8 7.4,21.7 12,23C16.6,21.7 20,16.8 20,11.5V5.5L12,2ZM10.8,16.2L7,12.4L8.4,11L10.8,13.4L15.6,8.6L17,10L10.8,16.2Z")
            )
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

private fun buildAdaptiveVectorXml(fillColorHex: String, pathData: String): String {
    return """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="24"
            android:viewportHeight="24">
            <group
                android:scaleX="0.58"
                android:scaleY="0.58"
                android:pivotX="12"
                android:pivotY="12">
                <path
                    android:fillColor="$fillColorHex"
                    android:pathData="$pathData" />
            </group>
        </vector>
    """.trimIndent()
}
