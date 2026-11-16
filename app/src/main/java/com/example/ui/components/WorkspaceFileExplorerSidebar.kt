package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GeneratedFile

enum class WorkspaceFileCategory(val title: String) {
    KOTLIN("Kotlin Files"),
    XML_LAYOUTS("XML Layouts & Resources"),
    ASSETS("Assets & Media"),
    CONFIG("Gradle & Config")
}

data class FileExplorerGroup(
    val category: WorkspaceFileCategory,
    val files: List<GeneratedFile>
)

object WorkspaceFileCategorizer {

    fun classifyPath(path: String): WorkspaceFileCategory {
        val lower = path.trim().lowercase()
        val fileName = lower.substringAfterLast('/')
        return when {
            lower.endsWith(".kt") -> WorkspaceFileCategory.KOTLIN
            lower.contains("/assets/") ||
                lower.startsWith("assets/") ||
                lower.contains("/res/drawable") ||
                lower.contains("/res/mipmap") ||
                lower.contains("/res/font") ||
                fileName.endsWith(".png") ||
                fileName.endsWith(".jpg") ||
                fileName.endsWith(".jpeg") ||
                fileName.endsWith(".webp") ||
                fileName.endsWith(".svg") ||
                fileName.endsWith(".ttf") ||
                fileName.endsWith(".otf") ||
                fileName.endsWith(".wav") ||
                fileName.endsWith(".mp3") -> WorkspaceFileCategory.ASSETS
            lower.endsWith(".xml") ||
                lower.contains("/res/layout") ||
                lower.contains("/res/values") -> WorkspaceFileCategory.XML_LAYOUTS
            else -> WorkspaceFileCategory.CONFIG
        }
    }

    fun groupFiles(
        files: List<GeneratedFile>,
        searchQuery: String = ""
    ): List<FileExplorerGroup> {
        val cleanQuery = searchQuery.trim().lowercase()
        val filtered = if (cleanQuery.isBlank()) {
            files
        } else {
            files.filter {
                it.path.lowercase().contains(cleanQuery)
            }
        }

        val grouped = filtered.groupBy { classifyPath(it.path) }
        return WorkspaceFileCategory.entries.mapNotNull { category ->
            val items = grouped[category]?.sortedWith(
                compareBy<GeneratedFile> { !it.path.endsWith("MainActivity.kt") }
                    .thenBy { it.path.substringAfterLast('/').lowercase() }
                    .thenBy { it.path.lowercase() }
            ).orEmpty()
            if (items.isEmpty()) null else FileExplorerGroup(category = category, files = items)
        }
    }

    fun formatRelativeDirectory(path: String): String {
        val dir = path.substringBeforeLast('/', "")
        if (dir.isBlank()) return "root"
        return dir
            .removePrefix("app/src/main/java/")
            .removePrefix("app/src/main/")
            .ifBlank { "app" }
    }
}

@Composable
fun WorkspaceFileExplorerSidebar(
    files: List<GeneratedFile>,
    selectedPath: String,
    onSelectFile: (GeneratedFile) -> Unit,
    onNewFileClick: () -> Unit,
    onCollapseSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val expandedFolders = remember {
        mutableStateMapOf(
            WorkspaceFileCategory.KOTLIN to true,
            WorkspaceFileCategory.XML_LAYOUTS to true,
            WorkspaceFileCategory.ASSETS to true,
            WorkspaceFileCategory.CONFIG to true
        )
    }

    val groups = remember(files, searchQuery) {
        WorkspaceFileCategorizer.groupFiles(files, searchQuery)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxHeight()
            .testTag("workspace_file_explorer_sidebar")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sidebar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${files.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNewFileClick,
                        modifier = Modifier.testTag("sidebar_new_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New File",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCollapseSidebar,
                        modifier = Modifier.testTag("collapse_file_explorer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Collapse File Explorer",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Filter Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Filter files…",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("explorer_search_input")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Grouped File Tree
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No files in project" else "No matching files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    groups.forEach { group ->
                        val isExpanded = searchQuery.isNotBlank() || (expandedFolders[group.category] ?: true)

                        item(key = "header_${group.category.name}") {
                            ExplorerFolderHeader(
                                category = group.category,
                                count = group.files.size,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedFolders[group.category] = !(expandedFolders[group.category] ?: true)
                                }
                            )
                        }

                        if (isExpanded) {
                            items(
                                items = group.files,
                                key = { "file_${it.path}" }
                            ) { file ->
                                val isSelected = file.path == selectedPath
                                ExplorerFileItemRow(
                                    file = file,
                                    category = group.category,
                                    isSelected = isSelected,
                                    onClick = { onSelectFile(file) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerFolderHeader(
    category: WorkspaceFileCategory,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("explorer_folder_${category.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (isExpanded) "Collapse ${category.title}" else "Expand ${category.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExplorerFileItemRow(
    file: GeneratedFile,
    category: WorkspaceFileCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val fileName = file.path.substringAfterLast('/')
    val dirLabel = remember(file.path) {
        WorkspaceFileCategorizer.formatRelativeDirectory(file.path)
    }
    val icon: ImageVector = when (category) {
        WorkspaceFileCategory.KOTLIN -> Icons.Default.Code
        WorkspaceFileCategory.XML_LAYOUTS -> Icons.Default.Description
        WorkspaceFileCategory.ASSETS -> Icons.Default.Image
        WorkspaceFileCategory.CONFIG -> Icons.Default.Tune
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .testTag("explorer_file_$fileName"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dirLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
