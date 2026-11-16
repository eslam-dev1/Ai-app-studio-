package com.example.ui.components

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StudioProject
import com.example.ui.assets.ProjectResAssetManager
import com.example.ui.assets.ResAssetFormat
import com.example.ui.assets.ResAssetItem
import com.example.ui.assets.ResAssetKind
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

private enum class AssetFilterOption(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    ICONS("Icons"),
    FONTS("Fonts")
}

private enum class PreviewBackdropMode(val label: String) {
    CHECKER("Checker"),
    LIGHT("Light"),
    DARK("Dark")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagerTab(
    project: StudioProject,
    onRenameAsset: (oldPath: String, newBaseName: String, updateReferences: Boolean, onDone: (String) -> Unit) -> Unit,
    onDeleteAsset: (path: String) -> Unit,
    onImportImageUri: (uri: Uri, targetResFolder: String, onDone: (String) -> Unit) -> Unit,
    onImportFontUri: (uri: Uri, onDone: (String) -> Unit) -> Unit,
    onAddVectorAsset: (path: String, content: String, onDone: (String) -> Unit) -> Unit,
    onOpenXmlInEditor: (path: String) -> Unit,
    onShowToast: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    val allAssets = remember(project.files) {
        ProjectResAssetManager.discoverResAssets(project.files)
    }

    var searchQuery by rememberSaveable(project.id) { mutableStateOf("") }
    var selectedFilter by rememberSaveable(project.id) { mutableStateOf(AssetFilterOption.ALL) }
    var selectedAssetPath by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var showCompactPreviewSheet by rememberSaveable(project.id) { mutableStateOf(false) }

    var renameTargetAsset by remember { mutableStateOf<ResAssetItem?>(null) }
    var deleteTargetAsset by remember { mutableStateOf<ResAssetItem?>(null) }
    var showAddVectorDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportImageUri(uri, "drawable") { newPath ->
                selectedAssetPath = newPath
            }
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportFontUri(uri) { newPath ->
                selectedAssetPath = newPath
            }
        }
    }

    val filteredAssets = remember(allAssets, searchQuery, selectedFilter) {
        val cleanQuery = searchQuery.trim().lowercase()
        allAssets.filter { asset ->
            val matchesFilter = when (selectedFilter) {
                AssetFilterOption.ALL -> true
                AssetFilterOption.IMAGES -> asset.kind == ResAssetKind.IMAGE
                AssetFilterOption.ICONS -> asset.kind == ResAssetKind.ICON
                AssetFilterOption.FONTS -> asset.kind == ResAssetKind.FONT
            }
            val matchesQuery = cleanQuery.isBlank() ||
                asset.baseName.lowercase().contains(cleanQuery) ||
                asset.path.lowercase().contains(cleanQuery) ||
                asset.resFolder.lowercase().contains(cleanQuery)
            matchesFilter && matchesQuery
        }
    }

    val selectedAsset = remember(allAssets, selectedAssetPath, filteredAssets) {
        allAssets.firstOrNull { it.path == selectedAssetPath }
            ?: filteredAssets.firstOrNull()
    }

    if (renameTargetAsset != null) {
        val target = renameTargetAsset!!
        RenameAssetDialog(
            asset = target,
            onConfirm = { newBaseName, updateRefs ->
                onRenameAsset(target.path, newBaseName, updateRefs) { newPath ->
                    if (selectedAssetPath == target.path) {
                        selectedAssetPath = newPath
                    }
                }
                renameTargetAsset = null
            },
            onDismiss = { renameTargetAsset = null }
        )
    }

    if (deleteTargetAsset != null) {
        val target = deleteTargetAsset!!
        AlertDialog(
            onDismissRequest = { deleteTargetAsset = null },
            title = { Text("Delete Resource Asset?") },
            text = {
                Text(
                    "Remove ${target.fileName} from ${target.resFolder}/? References in Kotlin/XML files will remain until updated."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAsset(target.path)
                        if (selectedAssetPath == target.path) {
                            selectedAssetPath = null
                            showCompactPreviewSheet = false
                        }
                        deleteTargetAsset = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetAsset = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddVectorDialog) {
        AddVectorPresetDialog(
            existingPaths = project.files.map { it.path }.toSet(),
            onConfirm = { path, xmlContent ->
                onAddVectorAsset(path, xmlContent) { addedPath ->
                    selectedAssetPath = addedPath
                }
                showAddVectorDialog = false
            },
            onDismiss = { showAddVectorDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("asset_manager_tab")
    ) {
        // Action Toolbar (Import Image, Import Font, Add Vector Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                label = { Text("Import Image") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Import Image to res/drawable",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("asset_import_image_button")
            )
            AssistChip(
                onClick = {
                    fontPickerLauncher.launch(
                        arrayOf(
                            "font/*",
                            "application/x-font-ttf",
                            "application/x-font-opentype",
                            "application/font-sfnt",
                            "application/octet-stream"
                        )
                    )
                },
                label = { Text("Import Font (.ttf/.otf)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = "Import Font to res/font",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("asset_import_font_button")
            )
            AssistChip(
                onClick = { showAddVectorDialog = true },
                label = { Text("Add Vector / Shape") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Add Vector or Shape XML Drawable",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("asset_add_vector_button")
            )
        }

        // Search & Filter Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search res/ images, icons, and fonts…") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("asset_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageCount = allAssets.count { it.kind == ResAssetKind.IMAGE }
                val iconCount = allAssets.count { it.kind == ResAssetKind.ICON }
                val fontCount = allAssets.count { it.kind == ResAssetKind.FONT }

                AssetFilterOption.entries.forEach { option ->
                    val count = when (option) {
                        AssetFilterOption.ALL -> allAssets.size
                        AssetFilterOption.IMAGES -> imageCount
                        AssetFilterOption.ICONS -> iconCount
                        AssetFilterOption.FONTS -> fontCount
                    }
                    FilterChip(
                        selected = selectedFilter == option,
                        onClick = { selectedFilter = option },
                        label = { Text("${option.label} ($count)") },
                        modifier = Modifier.testTag("asset_filter_${option.name.lowercase()}")
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (allAssets.isEmpty()) {
            EmptyResAssetsState(
                onImportImage = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onImportFont = {
                    fontPickerLauncher.launch(arrayOf("font/*", "application/octet-stream"))
                },
                onAddVector = { showAddVectorDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWideLayout = maxWidth >= 640.dp

                if (isWideLayout) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AssetGridPane(
                            assets = filteredAssets,
                            selectedPath = selectedAsset?.path,
                            onSelectAsset = { asset ->
                                selectedAssetPath = asset.path
                            },
                            onRenameClick = { renameTargetAsset = it },
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (selectedAsset != null) {
                            AssetInspectorPane(
                                asset = selectedAsset,
                                allAssets = allAssets,
                                onRenameClick = { renameTargetAsset = selectedAsset },
                                onDeleteClick = { deleteTargetAsset = selectedAsset },
                                onCopyReference = { ref ->
                                    clipboardManager.setText(AnnotatedString(ref))
                                    onShowToast("Copied $ref")
                                },
                                onOpenXmlInEditor = { onOpenXmlInEditor(selectedAsset.path) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Select an asset to inspect & preview",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    AssetGridPane(
                        assets = filteredAssets,
                        selectedPath = selectedAssetPath,
                        onSelectAsset = { asset ->
                            selectedAssetPath = asset.path
                            showCompactPreviewSheet = true
                        },
                        onRenameClick = { renameTargetAsset = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showCompactPreviewSheet && selectedAsset != null) {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = { showCompactPreviewSheet = false },
                            sheetState = sheetState
                        ) {
                            AssetInspectorPane(
                                asset = selectedAsset,
                                allAssets = allAssets,
                                onRenameClick = { renameTargetAsset = selectedAsset },
                                onDeleteClick = { deleteTargetAsset = selectedAsset },
                                onCopyReference = { ref ->
                                    clipboardManager.setText(AnnotatedString(ref))
                                    onShowToast("Copied $ref")
                                },
                                onOpenXmlInEditor = {
                                    showCompactPreviewSheet = false
                                    onOpenXmlInEditor(selectedAsset.path)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(540.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyResAssetsState(
    onImportImage: () -> Unit,
    onImportFont: () -> Unit,
    onAddVector: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Assets in res/ Yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Import PNG/WebP/JPG images, TrueType/OpenType fonts, or add Material vector icons & shapes into app/src/main/res/.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onAddVector) {
                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Vector Icon")
            }
            OutlinedButton(onClick = onImportImage) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Image")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onImportFont) {
            Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Import Font (.ttf / .otf)")
        }
    }
}

@Composable
private fun AssetGridPane(
    assets: List<ResAssetItem>,
    selectedPath: String?,
    onSelectAsset: (ResAssetItem) -> Unit,
    onRenameClick: (ResAssetItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (assets.isEmpty()) {
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matching resources in res/.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        items(assets, key = { it.path }) { asset ->
            val isSelected = asset.path == selectedPath
            AssetGridCard(
                asset = asset,
                allAssets = assets,
                isSelected = isSelected,
                onClick = { onSelectAsset(asset) },
                onRenameClick = { onRenameClick(asset) }
            )
        }
    }
}

@Composable
private fun AssetGridCard(
    asset: ResAssetItem,
    allAssets: List<ResAssetItem>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("asset_card_${asset.baseName}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                CheckerboardBackground(
                    modifier = Modifier.fillMaxSize(),
                    cellSize = 10.dp
                )
                AssetThumbnailVisual(
                    asset = asset,
                    allAssets = allAssets,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = asset.extension.uppercase().ifBlank { "RES" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.baseName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${asset.resFolder} • ${ProjectResAssetManager.formatByteSize(asset.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier.testTag("asset_rename_icon_${asset.baseName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = "Rename ${asset.fileName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetInspectorPane(
    asset: ResAssetItem,
    allAssets: List<ResAssetItem>,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyReference: (String) -> Unit,
    onOpenXmlInEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var backdropMode by rememberSaveable(asset.path) { mutableStateOf(PreviewBackdropMode.CHECKER) }
    var fontSampleText by rememberSaveable(asset.path) {
        mutableStateOf("Pack my box with five dozen liquor jugs • 0123456789")
    }
    var fontSizeSp by rememberSaveable(asset.path) { mutableFloatStateOf(22f) }

    val decodedBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = asset.path, key2 = asset.rawContent) {
        value = if (asset.format == ResAssetFormat.PNG ||
            asset.format == ResAssetFormat.JPEG ||
            asset.format == ResAssetFormat.WEBP
        ) {
            withContext(Dispatchers.IO) {
                val bytes = ProjectResAssetManager.decodeBinaryContent(asset.rawContent) ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } else null
    }

    val context = LocalContext.current
    val loadedFontFamily by produceState<FontFamily?>(initialValue = null, key1 = asset.path, key2 = asset.rawContent) {
        value = if (asset.format == ResAssetFormat.FONT_TTF || asset.format == ResAssetFormat.FONT_OTF) {
            withContext(Dispatchers.IO) {
                val bytes = ProjectResAssetManager.decodeBinaryContent(asset.rawContent) ?: return@withContext null
                runCatching {
                    val cacheDir = File(context.cacheDir, "font_preview").apply { mkdirs() }
                    val safeName = "${asset.path.hashCode()}_${asset.fileName}"
                    val tempFile = File(cacheDir, safeName)
                    if (!tempFile.exists() || tempFile.length() != bytes.size.toLong()) {
                        tempFile.writeBytes(bytes)
                    }
                    FontFamily(Typeface.createFromFile(tempFile))
                }.getOrNull()
            }
        } else null
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = asset.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = asset.kind.label.dropLast(1),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Backdrop Toggle for visual assets
        if (asset.kind != ResAssetKind.FONT) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PreviewBackdropMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = backdropMode == mode,
                        onClick = { backdropMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PreviewBackdropMode.entries.size
                        )
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Large Live Preview Surface
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (asset.kind == ResAssetKind.FONT) {
                FontPreviewSpecimenBox(
                    asset = asset,
                    loadedFontFamily = loadedFontFamily,
                    allAssets = allAssets,
                    sampleText = fontSampleText,
                    onSampleTextChange = { fontSampleText = it },
                    fontSizeSp = fontSizeSp,
                    onFontSizeChange = { fontSizeSp = it }
                )
            } else {
                val canvasContainerColor = when (backdropMode) {
                    PreviewBackdropMode.CHECKER -> MaterialTheme.colorScheme.surfaceContainerHighest
                    PreviewBackdropMode.LIGHT -> MaterialTheme.colorScheme.surface
                    PreviewBackdropMode.DARK -> MaterialTheme.colorScheme.inverseSurface
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(canvasContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (backdropMode == PreviewBackdropMode.CHECKER) {
                        CheckerboardBackground(
                            modifier = Modifier.fillMaxSize(),
                            cellSize = 14.dp
                        )
                    }

                    when {
                        decodedBitmap != null -> {
                            Image(
                                bitmap = decodedBitmap!!,
                                contentDescription = "Preview of ${asset.fileName}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        }
                        else -> {
                            AssetThumbnailVisual(
                                asset = asset,
                                allAssets = allAssets,
                                modifier = Modifier
                                    .size(144.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Metadata Summary Row
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val bitmapResolution = decodedBitmap?.let { "${it.width} × ${it.height} px" }
                val vectorParsed = remember(asset.rawContent, asset.format) {
                    if (asset.format == ResAssetFormat.VECTOR_XML) {
                        ProjectResAssetManager.parseVectorDrawableXml(asset.rawContent)
                    } else null
                }
                Text(
                    text = "Format: ${asset.format.label} • Folder: res/${asset.resFolder}",
                    style = MaterialTheme.typography.bodySmall
                )
                val extraSpecs = buildList {
                    add("Size: ${ProjectResAssetManager.formatByteSize(asset.sizeBytes)}")
                    if (bitmapResolution != null) add("Resolution: $bitmapResolution")
                    if (vectorParsed != null) {
                        add(
                            "Viewport: ${vectorParsed.viewportWidth.toInt()}×${vectorParsed.viewportHeight.toInt()} " +
                                "(${vectorParsed.paths.size} path${if (vectorParsed.paths.size == 1) "" else "s"})"
                        )
                    }
                    if (asset.qualifier != "default") add("Qualifier: ${asset.qualifier}")
                }.joinToString(" • ")
                Text(
                    text = extraSpecs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action Buttons (Rename, Copy Refs, Edit XML, Delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRenameClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("inspector_rename_button")
            ) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rename")
            }
            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("inspector_delete_button")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onCopyReference(asset.referenceKotlin) },
                label = { Text(asset.referenceKotlin) },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("copy_kotlin_ref_chip")
            )
            AssistChip(
                onClick = { onCopyReference(asset.referenceXml) },
                label = { Text(asset.referenceXml) },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("copy_xml_ref_chip")
            )
            if (!asset.isBinary) {
                AssistChip(
                    onClick = onOpenXmlInEditor,
                    label = { Text("Edit XML") },
                    leadingIcon = {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("edit_asset_xml_chip")
                )
            }
        }
    }
}

@Composable
private fun FontPreviewSpecimenBox(
    asset: ResAssetItem,
    loadedFontFamily: FontFamily?,
    allAssets: List<ResAssetItem>,
    sampleText: String,
    onSampleTextChange: (String) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit
) {
    val parsedXml = remember(asset.rawContent, asset.format) {
        if (asset.format == ResAssetFormat.FONT_XML) {
            ProjectResAssetManager.parseFontFamilyXml(asset.rawContent)
        } else null
    }
    val activeFontFamily = loadedFontFamily ?: FontFamily.SansSerif

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (loadedFontFamily != null) "Live Font Specimen" else "Font Family Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${fontSizeSp.toInt()} sp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (parsedXml != null) {
            if (parsedXml.entries.isEmpty()) {
                Text(
                    text = "Empty <font-family> definition",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                parsedXml.entries.forEach { entry ->
                    val referencedName = entry.fontRef.substringAfterLast('/')
                    val hasBinaryMatch = allAssets.any { it.baseName == referencedName && it.isBinary }
                    Text(
                        text = "${entry.fontRef} • weight ${entry.weight}" +
                            (if (entry.isItalic) " italic" else "") +
                            if (hasBinaryMatch) " (linked)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFamily
                    )
                }
            }
        }

        Text(
            text = sampleText.ifBlank { "Aa Bb Cc 123" },
            style = TextStyle(
                fontFamily = activeFontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.3f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        )

        Text(
            text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz • 0123456789",
            style = TextStyle(
                fontFamily = activeFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Slider(
            value = fontSizeSp,
            onValueChange = onFontSizeChange,
            valueRange = 12f..36f,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = sampleText,
            onValueChange = onSampleTextChange,
            label = { Text("Preview sample text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AssetThumbnailVisual(
    asset: ResAssetItem,
    allAssets: List<ResAssetItem>,
    modifier: Modifier = Modifier
) {
    when (asset.format) {
        ResAssetFormat.PNG, ResAssetFormat.JPEG, ResAssetFormat.WEBP -> {
            val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = asset.path, key2 = asset.rawContent) {
                value = withContext(Dispatchers.IO) {
                    val bytes = ProjectResAssetManager.decodeBinaryContent(asset.rawContent) ?: return@withContext null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = asset.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = modifier
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = asset.fileName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = modifier
                )
            }
        }
        ResAssetFormat.VECTOR_XML -> {
            val parsed = remember(asset.rawContent) {
                ProjectResAssetManager.parseVectorDrawableXml(asset.rawContent)
            }
            if (parsed != null && parsed.paths.isNotEmpty()) {
                val fallbackTint = MaterialTheme.colorScheme.primary
                Canvas(modifier = modifier) {
                    val scale = min(
                        size.width / parsed.viewportWidth,
                        size.height / parsed.viewportHeight
                    )
                    val dx = (size.width - parsed.viewportWidth * scale) / 2f
                    val dy = (size.height - parsed.viewportHeight * scale) / 2f

                    withTransform({
                        translate(left = dx, top = dy)
                        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        parsed.paths.forEach { pathSpec ->
                            val composePath = runCatching {
                                PathParser().parsePathString(pathSpec.pathData).toPath()
                            }.getOrNull() ?: return@forEach

                            composePath.fillType = if (pathSpec.fillTypeEvenOdd) {
                                PathFillType.EvenOdd
                            } else {
                                PathFillType.NonZero
                            }

                            val tintArgb = parsed.tintColorArgb
                            val fillColor = when {
                                tintArgb != null && pathSpec.fillColorArgb != null ->
                                    Color(tintArgb.toInt()).copy(alpha = pathSpec.fillAlpha)
                                pathSpec.fillColorArgb != null ->
                                    Color(pathSpec.fillColorArgb.toInt()).copy(alpha = pathSpec.fillAlpha)
                                pathSpec.strokeColorArgb == null -> fallbackTint
                                else -> null
                            }
                            if (fillColor != null) {
                                drawPath(path = composePath, color = fillColor)
                            }
                            val strokeArgb = pathSpec.strokeColorArgb
                            if (strokeArgb != null && pathSpec.strokeWidth > 0f) {
                                drawPath(
                                    path = composePath,
                                    color = Color(strokeArgb.toInt()).copy(alpha = pathSpec.strokeAlpha),
                                    style = Stroke(
                                        width = pathSpec.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = asset.fileName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = modifier
                )
            }
        }
        ResAssetFormat.SHAPE_XML -> {
            val shapeSpec = remember(asset.rawContent) {
                ProjectResAssetManager.parseShapeDrawableXml(asset.rawContent)
            }
            val fallbackColor = MaterialTheme.colorScheme.primaryContainer
            val fallbackStroke = MaterialTheme.colorScheme.outline
            if (shapeSpec != null) {
                Canvas(modifier = modifier) {
                    val cornerPx = shapeSpec.cornerRadiusDp * density
                    val strokePx = shapeSpec.strokeWidthDp * density
                    val isOval = shapeSpec.shapeType == "oval"
                    val fillBrush = if (shapeSpec.gradientStartArgb != null && shapeSpec.gradientEndArgb != null) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(shapeSpec.gradientStartArgb.toInt()),
                                Color(shapeSpec.gradientEndArgb.toInt())
                            )
                        )
                    } else null
                    val solidColor = shapeSpec.solidColorArgb?.let { Color(it.toInt()) } ?: fallbackColor

                    if (isOval) {
                        if (fillBrush != null) {
                            drawOval(brush = fillBrush)
                        } else {
                            drawOval(color = solidColor)
                        }
                        if (strokePx > 0f) {
                            drawOval(
                                color = shapeSpec.strokeColorArgb?.let { Color(it.toInt()) } ?: fallbackStroke,
                                style = Stroke(width = strokePx)
                            )
                        }
                    } else {
                        val cr = CornerRadius(cornerPx, cornerPx)
                        if (fillBrush != null) {
                            drawRoundRect(brush = fillBrush, cornerRadius = cr)
                        } else {
                            drawRoundRect(color = solidColor, cornerRadius = cr)
                        }
                        if (strokePx > 0f) {
                            drawRoundRect(
                                color = shapeSpec.strokeColorArgb?.let { Color(it.toInt()) } ?: fallbackStroke,
                                cornerRadius = cr,
                                style = Stroke(width = strokePx)
                            )
                        }
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Brush,
                    contentDescription = asset.fileName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = modifier
                )
            }
        }
        ResAssetFormat.FONT_TTF, ResAssetFormat.FONT_OTF, ResAssetFormat.FONT_XML -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = asset.fileName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun CheckerboardBackground(
    modifier: Modifier = Modifier,
    cellSize: Dp = 12.dp
) {
    val lightCell = MaterialTheme.colorScheme.surfaceContainerLowest
    val darkCell = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier = modifier) {
        val px = cellSize.toPx().coerceAtLeast(4f)
        val cols = (size.width / px).toInt() + 1
        val rows = (size.height / px).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawRect(
                    color = if ((r + c) % 2 == 0) lightCell else darkCell,
                    topLeft = Offset(c * px, r * px),
                    size = Size(px, px)
                )
            }
        }
    }
}

@Composable
private fun RenameAssetDialog(
    asset: ResAssetItem,
    onConfirm: (newBaseName: String, updateReferences: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by rememberSaveable(asset.path) { mutableStateOf(asset.baseName) }
    var updateReferences by rememberSaveable(asset.path) { mutableStateOf(true) }
    val validationError = remember(nameInput) {
        ProjectResAssetManager.validateResourceBaseName(nameInput)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Resource Asset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Folder: res/${asset.resFolder}/ • Extension: .${asset.extension}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Resource name (without .${asset.extension})") },
                    isError = validationError != null,
                    supportingText = {
                        if (validationError != null) {
                            Text(validationError)
                        } else {
                            Text("Will save as $nameInput.${asset.extension}")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_asset_input")
                )
                if (validationError != null) {
                    AssistChip(
                        onClick = {
                            nameInput = ProjectResAssetManager.sanitizeToResourceBaseName(nameInput)
                        },
                        label = { Text("Auto-fix name") }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { updateReferences = !updateReferences },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = updateReferences,
                        onCheckedChange = { updateReferences = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Update R.${asset.resourceType}.${asset.baseName} & @${asset.resourceType}/${asset.baseName} references across project",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput.trim(), updateReferences) },
                enabled = validationError == null && nameInput.trim() != asset.baseName,
                modifier = Modifier.testTag("rename_asset_confirm_button")
            ) {
                Text("Rename")
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
private fun AddVectorPresetDialog(
    existingPaths: Set<String>,
    onConfirm: (path: String, xmlContent: String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = ProjectResAssetManager.builtInVectorPresets
    var selectedPreset by remember { mutableStateOf(presets.first()) }
    var resourceName by remember { mutableStateOf(presets.first().suggestedName) }
    val validationError = remember(resourceName) {
        ProjectResAssetManager.validateResourceBaseName(resourceName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vector / Shape Drawable") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pick a Material vector icon or shape drawable to add into app/src/main/res/drawable/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        val isChosen = preset.id == selectedPreset.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChosen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedPreset = preset
                                    resourceName = preset.suggestedName
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val tempAsset = remember(preset.id) {
                                    ProjectResAssetManager.toResAssetItem(
                                        com.example.model.GeneratedFile(
                                            path = "app/src/main/res/drawable/${preset.suggestedName}.xml",
                                            content = preset.xmlContent
                                        )
                                    )
                                }
                                if (tempAsset != null) {
                                    AssetThumbnailVisual(
                                        asset = tempAsset,
                                        allAssets = emptyList(),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "${preset.suggestedName}.xml",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = resourceName,
                    onValueChange = { resourceName = it },
                    label = { Text("Drawable resource name") },
                    isError = validationError != null,
                    supportingText = {
                        val candidatePath = "app/src/main/res/drawable/${resourceName.trim()}.xml"
                        if (validationError != null) {
                            Text(validationError)
                        } else if (existingPaths.any { it.equals(candidatePath, ignoreCase = true) }) {
                            Text("A file named ${resourceName.trim()}.xml already exists.")
                        } else {
                            Text("Saves to res/drawable/${resourceName.trim()}.xml")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val targetPath = "app/src/main/res/drawable/${resourceName.trim()}.xml"
            val alreadyExists = existingPaths.any { it.equals(targetPath, ignoreCase = true) }
            Button(
                onClick = { onConfirm(targetPath, selectedPreset.xmlContent) },
                enabled = validationError == null && !alreadyExists,
                modifier = Modifier.testTag("add_vector_confirm_button")
            ) {
                Text("Add Asset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
