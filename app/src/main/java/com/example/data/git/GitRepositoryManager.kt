package com.example.data.git

import com.example.model.GitChangeType
import com.example.model.GitCommitInfo
import com.example.model.GitFileChange
import com.example.model.GitRepoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitRepositoryManager {

    suspend fun getStatus(workspaceDir: File): GitRepoState = withContext(Dispatchers.IO) {
        val gitDir = File(workspaceDir, ".git")
        if (!gitDir.exists()) {
            return@withContext GitRepoState(isInitialized = false)
        }

        Git.open(workspaceDir).use { git ->
            val repo = git.repository
            val branch = runCatching { repo.branch }.getOrDefault("main")
            val remoteUrl = runCatching {
                repo.config.getString("remote", "origin", "url")
            }.getOrNull()

            val status = git.status().call()
            val changes = buildList {
                status.added.forEach { add(GitFileChange(it, GitChangeType.ADDED, isStaged = true)) }
                status.changed.forEach { add(GitFileChange(it, GitChangeType.STAGED_MODIFIED, isStaged = true)) }
                status.modified.forEach { add(GitFileChange(it, GitChangeType.MODIFIED, isStaged = false)) }
                status.untracked.forEach { add(GitFileChange(it, GitChangeType.UNTRACKED, isStaged = false)) }
                status.missing.forEach { add(GitFileChange(it, GitChangeType.DELETED, isStaged = false)) }
                status.removed.forEach { add(GitFileChange(it, GitChangeType.DELETED, isStaged = true)) }
            }.sortedBy { it.path }

            val commits = runCatching {
                git.log().setMaxCount(20).call().map { rev ->
                    GitCommitInfo(
                        shortSha = rev.name.take(7),
                        message = rev.shortMessage,
                        author = rev.authorIdent?.name ?: "Developer",
                        timestampMs = rev.commitTime.toLong() * 1000L
                    )
                }
            }.getOrDefault(emptyList())

            GitRepoState(
                isInitialized = true,
                branch = branch,
                remoteUrl = remoteUrl,
                changes = changes,
                commits = commits
            )
        }
    }

    suspend fun initRepo(workspaceDir: File): GitRepoState = withContext(Dispatchers.IO) {
        workspaceDir.mkdirs()
        val gitIgnore = File(workspaceDir, ".gitignore")
        if (!gitIgnore.exists()) {
            gitIgnore.writeText(
                """
                *.iml
                .gradle/
                local.properties
                .idea/
                build/
                app/build/
                .DS_Store
                """.trimIndent()
            )
        }

        val git = Git.init()
            .setDirectory(workspaceDir)
            .setInitialBranch("main")
            .call()
        git.close()
        getStatus(workspaceDir)
    }

    suspend fun stageFile(workspaceDir: File, path: String): GitRepoState = withContext(Dispatchers.IO) {
        Git.open(workspaceDir).use { git ->
            git.add().addFilepattern(path).call()
        }
        getStatus(workspaceDir)
    }

    suspend fun stageAll(workspaceDir: File): GitRepoState = withContext(Dispatchers.IO) {
        Git.open(workspaceDir).use { git ->
            git.add().addFilepattern(".").call()
        }
        getStatus(workspaceDir)
    }

    suspend fun unstageFile(workspaceDir: File, path: String): GitRepoState = withContext(Dispatchers.IO) {
        Git.open(workspaceDir).use { git ->
            val hasHead = git.repository.resolve("HEAD") != null
            if (hasHead) {
                git.reset().addPath(path).call()
            } else {
                git.rm().setCached(true).addFilepattern(path).call()
            }
        }
        getStatus(workspaceDir)
    }

    suspend fun commit(
        workspaceDir: File,
        message: String,
        authorName: String,
        authorEmail: String = "developer@aiappstudio.local"
    ): GitRepoState = withContext(Dispatchers.IO) {
        val cleanMsg = message.trim().ifBlank { "Update generated Android app" }
        val cleanAuthor = authorName.trim().ifBlank { "AI App Studio" }

        Git.open(workspaceDir).use { git ->
            val status = git.status().call()
            if (status.added.isEmpty() && status.changed.isEmpty() && status.removed.isEmpty()) {
                // Auto-stage if user didn't stage manually yet and there are unstaged changes
                if (!status.isClean) {
                    git.add().addFilepattern(".").call()
                } else {
                    throw IllegalStateException("Working tree is clean — nothing to commit.")
                }
            }
            val ident = PersonIdent(cleanAuthor, authorEmail)
            git.commit()
                .setMessage(cleanMsg)
                .setAuthor(ident)
                .setCommitter(ident)
                .call()
        }
        getStatus(workspaceDir)
    }

    suspend fun setRemoteOrigin(workspaceDir: File, remoteUrl: String): GitRepoState = withContext(Dispatchers.IO) {
        val normalized = remoteUrl.trim()
        Git.open(workspaceDir).use { git ->
            val existing = git.remoteList().call().any { it.name == "origin" }
            if (existing) {
                git.remoteSetUrl()
                    .setRemoteName("origin")
                    .setRemoteUri(URIish(normalized))
                    .call()
            } else {
                git.remoteAdd()
                    .setName("origin")
                    .setUri(URIish(normalized))
                    .call()
            }
        }
        getStatus(workspaceDir)
    }

    suspend fun push(
        workspaceDir: File,
        pat: String,
        username: String
    ): GitRepoState = withContext(Dispatchers.IO) {
        if (pat.isBlank()) {
            throw IllegalArgumentException("GitHub Personal Access Token (PAT) is required to push.")
        }
        Git.open(workspaceDir).use { git ->
            val branch = runCatching { git.repository.branch }.getOrDefault("main")
            val remoteUrl = git.repository.config.getString("remote", "origin", "url")
                ?: throw IllegalStateException("No Git remote 'origin' configured yet.")
            val creds = UsernamePasswordCredentialsProvider(
                username.ifBlank { "x-access-token" },
                pat.trim()
            )
            val results = git.push()
                .setRemote("origin")
                .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
                .setCredentialsProvider(creds)
                .setForce(false)
                .call()

            val rejected = results.flatMap { it.remoteUpdates }
                .firstOrNull { update ->
                    update.status.name.contains("REJECTED") || update.status.name.contains("ERROR")
                }
            if (rejected != null) {
                throw IllegalStateException(
                    "Push rejected (${rejected.status}): ${rejected.message ?: "Pull remote changes first or check PAT permissions."}"
                )
            }
            getStatus(workspaceDir).copy(
                statusMessage = "Pushed '$branch' to $remoteUrl"
            )
        }
    }

    suspend fun pull(
        workspaceDir: File,
        pat: String,
        username: String
    ): GitRepoState = withContext(Dispatchers.IO) {
        if (pat.isBlank()) {
            throw IllegalArgumentException("GitHub Personal Access Token (PAT) is required to pull.")
        }
        Git.open(workspaceDir).use { git ->
            val branch = runCatching { git.repository.branch }.getOrDefault("main")
            val creds = UsernamePasswordCredentialsProvider(
                username.ifBlank { "x-access-token" },
                pat.trim()
            )
            val pullResult = git.pull()
                .setRemote("origin")
                .setRemoteBranchName(branch)
                .setCredentialsProvider(creds)
                .call()

            if (!pullResult.isSuccessful) {
                throw IllegalStateException("Git pull did not complete cleanly. Check branch '$branch' or merge conflicts.")
            }
            getStatus(workspaceDir).copy(
                statusMessage = "Pulled latest updates from origin/$branch"
            )
        }
    }
}
