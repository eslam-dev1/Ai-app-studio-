package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectSnapshot
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryBottomSheet(
    snapshots: List<ProjectSnapshot>,
    onCreateCheckpoint: (summary: String, label: String?) -> Unit,
    onRestoreSnapshot: (ProjectSnapshot) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = LocalStudioCyberPalette.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var checkpointSummary by remember { mutableStateOf("") }
    var checkpointLabel by remember { mutableStateOf("") }
    var snapshotToRestore by remember { mutableStateOf<ProjectSnapshot?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        modifier = Modifier.testTag("version_history_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = palette.accentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Time-Travel Snapshots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${snapshots.size} version snapshot(s) available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_version_history_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close history")
                }
            }

            // Create Checkpoint CTA Row
            if (!showCreateDialog) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save instant rollback point",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("create_checkpoint_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Checkpoint")
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.cardSurfaceHigh,
                    border = BorderStroke(1.dp, palette.accentCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Create Version Checkpoint",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentCyan
                        )
                        OutlinedTextField(
                            value = checkpointSummary,
                            onValueChange = { checkpointSummary = it },
                            label = { Text("Checkpoint Note (e.g. 'Before theme redesign')") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkpoint_summary_input")
                        )
                        OutlinedTextField(
                            value = checkpointLabel,
                            onValueChange = { checkpointLabel = it },
                            label = { Text("Custom Version Label (optional, e.g. 'v1.5-beta')") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checkpoint_label_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val summary = checkpointSummary.ifBlank { "Manual checkpoint" }
                                    val label = checkpointLabel.trim().ifBlank { null }
                                    onCreateCheckpoint(summary, label)
                                    checkpointSummary = ""
                                    checkpointLabel = ""
                                    showCreateDialog = false
                                },
                                modifier = Modifier.testTag("save_checkpoint_button")
                            ) {
                                Text("Save Snapshot")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.cardBorder)

            // Confirmation Banner for Restore
            if (snapshotToRestore != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.accentPurple.copy(alpha = 0.15f),
                    border = BorderStroke(1.2.dp, palette.accentPurple),
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
                                text = "Restore to ${snapshotToRestore?.versionLabel}?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentCyan
                            )
                            Text(
                                text = "This will roll back code to '${snapshotToRestore?.summary}'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { snapshotToRestore = null }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    snapshotToRestore?.let { onRestoreSnapshot(it) }
                                    snapshotToRestore = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accentCyan,
                                    contentColor = Color(0xFF02131D)
                                ),
                                modifier = Modifier.testTag("confirm_restore_button")
                            ) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }

            // Snapshots List
            if (snapshots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No snapshots recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Snapshots are automatically created during AI generation or edits.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(snapshots, key = { it.id }) { snapshot ->
                        SnapshotCardItem(
                            snapshot = snapshot,
                            isLatest = snapshots.firstOrNull()?.id == snapshot.id,
                            onRestore = { snapshotToRestore = snapshot }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotCardItem(
    snapshot: ProjectSnapshot,
    isLatest: Boolean,
    onRestore: () -> Unit
) {
    val palette = LocalStudioCyberPalette.current
    val formattedDate = remember(snapshot.createdAtEpochMs) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(snapshot.createdAtEpochMs))
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("snapshot_item_${snapshot.versionLabel}"),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLatest) palette.accentCyan.copy(alpha = 0.2f) else palette.cardSurfaceHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isLatest) palette.accentCyan else palette.cardBorder
                        )
                    ) {
                        Text(
                            text = snapshot.versionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFamily,
                            color = if (isLatest) palette.accentCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (isLatest) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.accentEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = snapshot.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )

                Text(
                    text = "${snapshot.files.size} file(s) bundled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            if (!isLatest) {
                OutlinedButton(
                    onClick = onRestore,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("restore_button_${snapshot.versionLabel}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore this snapshot",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
