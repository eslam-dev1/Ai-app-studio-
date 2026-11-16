package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomSqlTableEntity
import com.example.data.local.ProjectSqlEntity
import com.example.data.local.RoomCodeGenerator
import com.example.data.local.SqlColumnSpec
import com.example.model.StudioProject
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SqlDatabaseBottomSheet(
    project: StudioProject,
    customTables: List<CustomSqlTableEntity>,
    allSqlProjects: List<ProjectSqlEntity>,
    persistedLogCount: Int,
    onInjectRoomTable: (
        entityName: String,
        tableName: String,
        columns: List<SqlColumnSpec>,
        onInjected: (generatedFilePath: String, generatedCode: String) -> Unit
    ) -> Unit,
    onOpenGeneratedFile: (filePath: String) -> Unit,
    onDeleteCustomTable: (tableId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSection by remember { mutableIntStateOf(0) }

    val defaultPreset = RoomCodeGenerator.presets.first()
    var entityName by remember { mutableStateOf(defaultPreset.entityName) }
    var tableName by remember { mutableStateOf(defaultPreset.tableName) }
    val columns = remember {
        mutableStateListOf<SqlColumnSpec>().apply {
            addAll(defaultPreset.columns)
        }
    }

    var newColName by remember { mutableStateOf("") }
    var newColType by remember { mutableStateOf("TEXT") }
    var newColNullable by remember { mutableStateOf(false) }

    val sqlTypes = listOf("TEXT", "INTEGER", "REAL", "BOOLEAN")

    val (previewPath, previewCode) = remember(
        project.packageName,
        entityName,
        tableName,
        columns.toList()
    ) {
        RoomCodeGenerator.generateRoomDatabaseFile(
            packageName = project.packageName,
            entityNameInput = entityName,
            tableNameInput = tableName,
            columns = columns.toList()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("sql_database_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Room SQLite Database",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SQL Schema Builder & Room + KSP Code Generator",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("sql_tab_generator")
                ) {
                    Text("Add SQL Table (Room)")
                }
                SegmentedButton(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("sql_tab_inspector")
                ) {
                    Text("SQLite Inspector (${customTables.size})")
                }
            }

            if (selectedSection == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    item {
                        Text(
                            text = "Quick SQL Table Presets",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RoomCodeGenerator.presets.forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        entityName = preset.entityName
                                        tableName = preset.tableName
                                        columns.clear()
                                        columns.addAll(preset.columns)
                                    },
                                    label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.TableChart,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = entityName,
                                onValueChange = { entityName = it },
                                label = { Text("Room @Entity Class") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sql_entity_name_input")
                            )
                            OutlinedTextField(
                                value = tableName,
                                onValueChange = { tableName = it },
                                label = { Text("SQLite Table Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sql_table_name_input")
                            )
                        }
                    }

                    item {
                        Text(
                            text = "SQL Columns (`id INTEGER PRIMARY KEY AUTOINCREMENT` included)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    itemsIndexed(columns) { idx, col ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${col.columnName} (${col.sqlType}${if (col.isNullable) " NULL" else " NOT NULL"})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Kotlin property: val ${col.kotlinPropertyName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (columns.size > 1) {
                                    IconButton(onClick = { columns.removeAt(idx) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove column",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Add SQL Column",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newColName,
                                        onValueChange = { newColName = it },
                                        label = { Text("Column name (e.g. due_date)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = newColNullable,
                                            onCheckedChange = { newColNullable = it }
                                        )
                                        Text("Null", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        sqlTypes.forEach { type ->
                                            FilterChip(
                                                selected = newColType == type,
                                                onClick = { newColType = type },
                                                label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val cleanCol = newColName.trim()
                                                .lowercase()
                                                .replace(Regex("[^a-z0-9_]+"), "_")
                                                .trim('_')
                                            if (cleanCol.isNotBlank()) {
                                                val camelProp = cleanCol.split('_')
                                                    .mapIndexed { i, part ->
                                                        if (i == 0) part else part.replaceFirstChar { it.uppercase() }
                                                    }
                                                    .joinToString("")
                                                columns.add(
                                                    SqlColumnSpec(
                                                        columnName = cleanCol,
                                                        kotlinPropertyName = camelProp,
                                                        sqlType = newColType,
                                                        isNullable = newColNullable
                                                    )
                                                )
                                                newColName = ""
                                                newColNullable = false
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Generated Target: $previewPath",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                text = previewCode.lines().take(28).joinToString("\n") + "\n// ... (@Dao, @Database & Repository included)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                onInjectRoomTable(
                                    entityName,
                                    tableName,
                                    columns.toList()
                                ) { _, _ ->
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inject_room_sql_button")
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Inject Room SQL + Enable KSP")
                        }
                    }
                }
            } else {
                // Live SQLite Inspector Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Active Engine: Room SQLite (studio_sql.db)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Tables: studio_projects (${allSqlProjects.size} rows) • project_sql_tables (${customTables.size} rows) • studio_build_logs ($persistedLogCount rows)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Injected Room SQL Tables in '${project.appName}'",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (customTables.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No custom Room SQL tables injected into this project yet. Use the 'Add SQL Table (Room)' tab to generate an @Entity, @Dao, @Database, and Repository.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        items(customTables, key = { it.id }) { table ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@Entity ${table.entityName} (table: `${table.tableName}`)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = table.generatedFilePath,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                onOpenGeneratedFile(table.generatedFilePath)
                                                onDismiss()
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Code,
                                                contentDescription = "Open generated Room file",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { onDeleteCustomTable(table.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete table record",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "SQLite Table: `studio_projects` (${allSqlProjects.size} rows)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(allSqlProjects, key = { it.id }) { row ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${row.appName} (${row.packageName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "PK id=${row.id.take(8)}… • engine=${row.engineMode} • status=${row.lastBuildStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
