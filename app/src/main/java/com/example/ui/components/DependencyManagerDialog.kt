package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gradle.CustomDependencyStatus
import com.example.data.gradle.DependencyGroupStatus
import com.example.data.gradle.DependencyManagerState
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyManagerBottomSheet(
    state: DependencyManagerState,
    onApplyChanges: (enabledGroupIds: Set<String>, enabledCustomDeclarations: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var enabledGroupIds by remember(state) {
        mutableStateOf(state.groups.filter { it.isEnabled }.map { it.group.id }.toSet())
    }
    var enabledCustomDecls by remember(state) {
        mutableStateOf(
            state.customDependencies.filter { it.isEnabled }.map { it.rawDeclaration }.toSet()
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val categories = remember(state.groups) {
        state.groups.map { it.group.category }.distinct()
    }

    val filteredGroups = remember(state.groups, searchQuery, selectedCategory) {
        state.groups.filter { item ->
            val matchesCategory = selectedCategory == null || item.group.category == selectedCategory
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                item.group.title.contains(q, ignoreCase = true) ||
                item.group.description.contains(q, ignoreCase = true) ||
                item.previewArtifacts.any { it.contains(q, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    val filteredCustomDeps = remember(state.customDependencies, searchQuery, selectedCategory) {
        if (selectedCategory != null) emptyList()
        else {
            val q = searchQuery.trim()
            if (q.isEmpty()) state.customDependencies
            else state.customDependencies.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    it.rawDeclaration.contains(q, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("dependency_manager_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Gradle Dependency Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Toggle commented dependencies in ${state.targetGradlePath}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${enabledGroupIds.size}/${state.groups.size} Bundles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search Room, CameraX, Firebase, Coil, Retrofit…",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dependency_search_input")
            )

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = { Text(category, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Checkbox List of Curated Library Groups + Custom Dependencies
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("dependency_list"),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredGroups, key = { it.group.id }) { item ->
                    val isChecked = item.group.id in enabledGroupIds
                    DependencyGroupCard(
                        item = item,
                        isChecked = isChecked,
                        onToggle = {
                            enabledGroupIds = if (isChecked) {
                                enabledGroupIds - item.group.id
                            } else {
                                enabledGroupIds + item.group.id
                            }
                        }
                    )
                }

                if (filteredCustomDeps.isNotEmpty()) {
                    item {
                        Text(
                            text = "Core & Custom Project Dependencies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(filteredCustomDeps, key = { it.rawDeclaration }) { customDep ->
                        val isChecked = customDep.rawDeclaration in enabledCustomDecls
                        CustomDependencyRow(
                            item = customDep,
                            isChecked = isChecked,
                            onToggle = {
                                enabledCustomDecls = if (isChecked) {
                                    enabledCustomDecls - customDep.rawDeclaration
                                } else {
                                    enabledCustomDecls + customDep.rawDeclaration
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.36f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onApplyChanges(enabledGroupIds, enabledCustomDecls)
                    },
                    modifier = Modifier
                        .weight(0.64f)
                        .testTag("apply_dependencies_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply to build.gradle.kts")
                }
            }
        }
    }
}

@Composable
private fun DependencyGroupCard(
    item: DependencyGroupStatus,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("dependency_group_${item.group.id}"),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("dependency_checkbox_${item.group.id}")
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.group.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.group.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isChecked) "ACTIVE" else "COMMENTED",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = item.group.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )

            // Monospace preview of the Gradle lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item.previewArtifacts.forEach { line ->
                    val displayLine = if (isChecked) line else "// $line"
                    Text(
                        text = displayLine,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = if (isChecked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomDependencyRow(
    item: CustomDependencyStatus,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily
                )
                Text(
                    text = if (isChecked) item.rawDeclaration else "// ${item.rawDeclaration}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
