package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GitChangeType
import com.example.model.GitCommitInfo
import com.example.model.GitFileChange
import com.example.model.GitRepoState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceGitTab(
    gitState: GitRepoState,
    isBusy: Boolean,
    hasGitHubPat: Boolean,
    onInitRepo: () -> Unit,
    onRefreshStatus: () -> Unit,
    onStageFile: (String) -> Unit,
    onUnstageFile: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommit: (String) -> Unit,
    onConfigureRemote: (String) -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var remoteInput by remember(gitState.remoteUrl) {
        mutableStateOf(gitState.remoteUrl ?: "")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isBusy) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (!gitState.isInitialized) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Git Version Control",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "Initialize a real local Git repository inside this project's workspace to stage files, commit snapshots, push to GitHub, and pull remote changes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onInitRepo,
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("git_init_button")
                        ) {
                            Icon(Icons.Default.Source, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Initialize Git Repository")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Branch & Sync Controls Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "branch: ${gitState.branch}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Text(
                                text = "${gitState.changes.size} changes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRefreshStatus, enabled = !isBusy) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh git status")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPush,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("git_push_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push")
                        }
                        OutlinedButton(
                            onClick = onPull,
                            enabled = !isBusy && !gitState.remoteUrl.isNullOrBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("git_pull_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull")
                        }
                    }

                    if (!hasGitHubPat) {
                        TextButton(onClick = onOpenSettings) {
                            Text("Configure GitHub PAT in Settings to Push/Pull")
                        }
                    }

                    // Remote Origin Configuration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = remoteInput,
                            onValueChange = { remoteInput = it },
                            label = { Text("Remote origin URL") },
                            placeholder = { Text("https://github.com/user/repo.git (auto-created on Push)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { onConfigureRemote(remoteInput) },
                            enabled = !isBusy && remoteInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Save remote URL")
                        }
                    }
                }
            }
        }

        // Commit Box
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Commit Changes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit message") },
                        placeholder = { Text("feat: implement generated Compose UI") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("git_commit_message_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStageAll,
                            enabled = !isBusy && gitState.changes.any { !it.isStaged },
                            modifier = Modifier.testTag("git_stage_all_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stage All")
                        }
                        Button(
                            onClick = {
                                onCommit(commitMessage)
                                commitMessage = ""
                            },
                            enabled = !isBusy && gitState.changes.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("git_commit_button")
                        ) {
                            Icon(Icons.Default.Commit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Commit")
                        }
                    }
                }
            }
        }

        // Working Tree Changes
        item {
            Text(
                text = "Working Tree (${gitState.changes.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (gitState.changes.isEmpty()) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Working tree clean — all edits committed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(gitState.changes, key = { "${it.path}_${it.isStaged}" }) { change ->
                GitChangeRow(
                    change = change,
                    isBusy = isBusy,
                    onStage = { onStageFile(change.path) },
                    onUnstage = { onUnstageFile(change.path) }
                )
            }
        }

        // Commit Log
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Text(
                    text = "Commit History (${gitState.commits.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (gitState.commits.isEmpty()) {
            item {
                Text(
                    text = "No commits yet. Stage files and create your first commit above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(gitState.commits, key = { it.shortSha + it.timestampMs }) { commit ->
                CommitHistoryCard(commit)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun GitChangeRow(
    change: GitFileChange,
    isBusy: Boolean,
    onStage: () -> Unit,
    onUnstage: () -> Unit
) {
    val badgeColor = when {
        change.isStaged -> MaterialTheme.colorScheme.secondaryContainer
        change.status == GitChangeType.MODIFIED -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = change.path,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                    Text(
                        text = change.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (change.isStaged) {
                IconButton(onClick = onUnstage, enabled = !isBusy) {
                    Icon(Icons.Default.Remove, contentDescription = "Unstage ${change.path}")
                }
            } else {
                IconButton(onClick = onStage, enabled = !isBusy) {
                    Icon(Icons.Default.Add, contentDescription = "Stage ${change.path}")
                }
            }
        }
    }
}

@Composable
private fun CommitHistoryCard(commit: GitCommitInfo) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = commit.shortSha,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = fmt.format(Date(commit.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = commit.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
