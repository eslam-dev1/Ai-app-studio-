package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.StudioAppDatabase
import com.example.data.local.StudioSqlRepository
import com.example.model.GeneratedFile
import com.example.model.ProjectSnapshot
import com.example.model.StudioProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectStorageRepository(private val context: Context) {

    val sqlRepository: StudioSqlRepository by lazy {
        StudioSqlRepository(StudioAppDatabase.getDatabase(context).studioSqlDao())
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    private val modelsRoot: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    private val exportsRoot: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    fun getWorkspaceDir(projectId: String): File =
        File(File(projectsRoot, projectId), "workspace").apply { mkdirs() }

    private fun getMetadataFile(projectId: String): File =
        File(File(projectsRoot, projectId), "project.json")

    suspend fun loadAllProjects(): List<StudioProject> = withContext(Dispatchers.IO) {
        val diskProjects = projectsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val metaFile = File(dir, "project.json")
                if (!metaFile.exists()) return@mapNotNull null
                runCatching {
                    json.decodeFromString<StudioProject>(metaFile.readText())
                }.getOrNull()
            }
            ?: emptyList()

        // Ensure any existing disk projects are indexed in Room SQLite
        diskProjects.forEach { proj ->
            runCatching { sqlRepository.upsertProject(proj) }
        }

        val sqlProjects = runCatching { sqlRepository.getAllProjectsOnce() }.getOrDefault(emptyList())
        if (sqlProjects.isNotEmpty()) {
            sqlProjects.sortedByDescending { it.updatedAtEpochMs }
        } else {
            diskProjects.sortedByDescending { it.updatedAtEpochMs }
        }
    }

    suspend fun loadProject(projectId: String): StudioProject? = withContext(Dispatchers.IO) {
        val sqlMatch = runCatching { sqlRepository.getProjectById(projectId) }.getOrNull()
        if (sqlMatch != null) return@withContext sqlMatch

        val metaFile = getMetadataFile(projectId)
        if (!metaFile.exists()) return@withContext null
        runCatching {
            json.decodeFromString<StudioProject>(metaFile.readText())
        }.getOrNull()?.also { loaded ->
            runCatching { sqlRepository.upsertProject(loaded) }
        }
    }

    suspend fun saveProject(project: StudioProject): StudioProject = withContext(Dispatchers.IO) {
        val projectDir = File(projectsRoot, project.id).apply { mkdirs() }
        val workspaceDir = getWorkspaceDir(project.id)

        // Write metadata JSON & Room SQLite record
        val updated = project.copy(updatedAtEpochMs = System.currentTimeMillis())
        File(projectDir, "project.json").writeText(json.encodeToString(updated))
        runCatching { sqlRepository.upsertProject(updated) }

        // Sync project files to workspace directory (preserving .git folder!)
        syncFilesToWorkspace(workspaceDir, updated.files)
        updated
    }

    suspend fun syncWorkspaceBackToProject(project: StudioProject): StudioProject = withContext(Dispatchers.IO) {
        val workspaceDir = getWorkspaceDir(project.id)
        if (!workspaceDir.exists()) return@withContext project

        val collectedFiles = mutableListOf<GeneratedFile>()
        workspaceDir.walkTopDown()
            .onEnter { dir -> dir.name != ".git" && dir.name != "build" && dir.name != ".gradle" }
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.relativeTo(workspaceDir).invariantSeparatorsPath
                if (relativePath != ".gitignore") {
                    runCatching {
                        val content = if (com.example.ui.assets.ProjectResAssetManager.isBinaryAssetPath(relativePath)) {
                            com.example.ui.assets.ProjectResAssetManager.encodeBinaryContent(file.readBytes())
                        } else {
                            file.readText()
                        }
                        collectedFiles += GeneratedFile(
                            path = relativePath,
                            content = content
                        )
                    }
                }
            }

        val updated = project.copy(
            files = collectedFiles.sortedBy { it.path },
            updatedAtEpochMs = System.currentTimeMillis()
        )
        getMetadataFile(project.id).writeText(json.encodeToString(updated))
        runCatching { sqlRepository.upsertProject(updated) }
        updated
    }

    private fun syncFilesToWorkspace(workspaceDir: File, files: List<GeneratedFile>) {
        val expectedRelativePaths = mutableSetOf<String>()
        files.forEach { generatedFile ->
            val sanitized = generatedFile.path.trim().removePrefix("/")
            if (sanitized.isBlank() || sanitized.contains("..")) return@forEach
            expectedRelativePaths += sanitized
            val target = File(workspaceDir, sanitized)
            target.parentFile?.mkdirs()
            val decodedBinary = com.example.ui.assets.ProjectResAssetManager.decodeBinaryContent(generatedFile.content)
            if (decodedBinary != null) {
                target.writeBytes(decodedBinary)
            } else {
                target.writeText(generatedFile.content)
            }
        }

        // Remove stale files that were renamed or deleted in project state (preserving .git, build, .gradle, .gitignore)
        workspaceDir.walkTopDown()
            .onEnter { dir -> dir.name != ".git" && dir.name != "build" && dir.name != ".gradle" }
            .filter { it.isFile }
            .forEach { diskFile ->
                val rel = diskFile.relativeTo(workspaceDir).invariantSeparatorsPath
                if (rel != ".gitignore" && rel !in expectedRelativePaths) {
                    runCatching { diskFile.delete() }
                }
            }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        File(projectsRoot, projectId).deleteRecursively()
        runCatching { sqlRepository.deleteProject(projectId) }
    }

    suspend fun exportProjectToZip(project: StudioProject): Uri = withContext(Dispatchers.IO) {
        val safeName = project.appName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "android-app" }
        val zipFile = File(exportsRoot, "$safeName-${System.currentTimeMillis()}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            project.files.forEach { file ->
                val cleanPath = file.path.trim().removePrefix("/")
                if (cleanPath.isNotBlank()) {
                    zos.putNextEntry(ZipEntry("$safeName/$cleanPath"))
                    val binaryBytes = com.example.ui.assets.ProjectResAssetManager.decodeBinaryContent(file.content)
                    zos.write(binaryBytes ?: file.content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
    }

    suspend fun exportBuildLogsToTxt(project: StudioProject, logs: List<com.example.model.ConsoleLogEntry>): Uri = withContext(Dispatchers.IO) {
        val safeName = project.appName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "android-app" }
        val logFile = File(exportsRoot, "build-logs-$safeName-${System.currentTimeMillis()}.txt")

        val sb = StringBuilder()
        sb.append("==================================================\n")
        sb.append(" AI APP STUDIO - COMPILATION & BUILD LOGS REPORT\n")
        sb.append(" Project: ${project.appName} (${project.packageName})\n")
        sb.append(" Timestamp: ${java.util.Date()}\n")
        sb.append("==================================================\n\n")

        if (logs.isEmpty()) {
            sb.append("No compilation or build logs recorded yet for this session.\n")
        } else {
            logs.forEach { entry ->
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(entry.timestampMs))
                sb.append("[$timeStr] [${entry.level.name}] [${entry.tag}]: ${entry.message}\n")
                if (entry.filePath != null) {
                    sb.append("   At: ${entry.filePath}${if (entry.lineNumber != null) ":${entry.lineNumber}" else ""}\n")
                }
            }
        }

        logFile.writeText(sb.toString(), Charsets.UTF_8)

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
    }

    suspend fun importModelFromUri(uri: Uri): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "local-model.task"
        var reportedSize = 0L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) reportedSize = cursor.getLong(sizeIdx)
            }
        }

        val sanitizedFileName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destFile = File(modelsRoot, sanitizedFileName)

        // Clean older imported models to avoid exhausting storage
        modelsRoot.listFiles()?.forEach { existing ->
            if (existing.name != sanitizedFileName) existing.delete()
        }

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: throw IllegalStateException("Cannot open selected model file from storage.")

        val actualSize = if (destFile.length() > 0L) destFile.length() else reportedSize
        Triple(destFile.absolutePath, displayName, actualSize)
    }

    suspend fun downloadModelFromUrl(
        url: String,
        fileNameHint: String,
        expectedSizeBytes: Long = 0L,
        huggingFaceToken: String = "",
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Triple<String, String, Long> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            throw IllegalArgumentException("Enter a valid HTTP/HTTPS model URL ending in .task or .bin")
        }

        val inferredName = fileNameHint.ifBlank {
            cleanUrl.substringBefore('?').substringAfterLast('/').ifBlank { "local-model.task" }
        }
        val sanitizedFileName = inferredName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destFile = File(modelsRoot, sanitizedFileName)
        val partFile = File(modelsRoot, "$sanitizedFileName.part")

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()

        val candidateUrls = if (cleanUrl.contains("huggingface.co")) {
            listOf(cleanUrl, com.example.data.generator.SmartModelResolver.toMirrorUrl(cleanUrl))
        } else {
            listOf(cleanUrl)
        }

        var activeResponse: okhttp3.Response? = null
        var lastDownloadException: Throwable? = null

        for (candidateUrl in candidateUrls) {
            try {
                val request = buildModelDownloadRequest(
                    url = candidateUrl,
                    huggingFaceToken = huggingFaceToken
                )
                val resp = client.newCall(request).execute()
                if (resp.isSuccessful) {
                    activeResponse = resp
                    break
                } else {
                    resp.close()
                    lastDownloadException = IllegalStateException("HTTP ${resp.code} from $candidateUrl")
                }
            } catch (ioe: java.io.IOException) {
                lastDownloadException = ioe
            }
        }

        val response = activeResponse
            ?: throw (lastDownloadException ?: IllegalStateException("Failed to connect to model servers."))

        try {
            response.use {
                val body = response.body
                    ?: throw IllegalStateException("Empty response body from model server.")
                val contentLength = body.contentLength().takeIf { it > 0L } ?: expectedSizeBytes

                var totalRead = 0L
                var lastReportBytes = 0L
                val buffer = ByteArray(64 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (totalRead - lastReportBytes >= 262_144L || (contentLength > 0L && totalRead >= contentLength)) {
                                lastReportBytes = totalRead
                                onProgress(totalRead, contentLength)
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(totalRead, contentLength.coerceAtLeast(totalRead))

                if (totalRead <= 0L) {
                    throw IllegalStateException("Downloaded file was 0 bytes.")
                }

                if (destFile.exists()) destFile.delete()
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }

                // Clean older models to preserve internal storage
                modelsRoot.listFiles()?.forEach { existing ->
                    if (existing.name != sanitizedFileName && existing.name != "${sanitizedFileName}.part") {
                        existing.delete()
                    }
                }

                Triple(destFile.absolutePath, inferredName, destFile.length())
            }
        } catch (t: Throwable) {
            runCatching { partFile.delete() }
            throw t
        }
    }

    suspend fun deleteLocalModelFiles() = withContext(Dispatchers.IO) {
        modelsRoot.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    suspend fun readUriAsset(uri: Uri, maxBytes: Int = 8 * 1024 * 1024): Triple<String, String?, ByteArray> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var displayName = ""
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx).orEmpty()
                    }
                }
            }
            val mimeType = resolver.getType(uri)
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalArgumentException(
                            "Selected asset exceeds ${maxBytes / (1024 * 1024)} MB project limit."
                        )
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            } ?: throw IllegalStateException("Unable to read selected file from storage.")

            Triple(displayName.ifBlank { uri.lastPathSegment ?: "asset" }, mimeType, bytes)
        }

    private fun getSnapshotsDir(projectId: String): File {
        val dir = File(File(projectsRoot, projectId), "snapshots")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveSnapshot(
        projectId: String,
        versionLabel: String,
        summary: String,
        files: List<GeneratedFile>
    ): ProjectSnapshot = withContext(Dispatchers.IO) {
        val snapshot = ProjectSnapshot(
            projectId = projectId,
            versionLabel = versionLabel,
            summary = summary,
            createdAtEpochMs = System.currentTimeMillis(),
            files = files
        )
        val dir = getSnapshotsDir(projectId)
        val file = File(dir, "snapshot-${snapshot.createdAtEpochMs}-${snapshot.id.take(8)}.json")
        file.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        snapshot
    }

    suspend fun listSnapshots(projectId: String): List<ProjectSnapshot> = withContext(Dispatchers.IO) {
        val dir = getSnapshotsDir(projectId)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            try {
                json.decodeFromString<ProjectSnapshot>(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.createdAtEpochMs }
    }

    companion object {
        fun buildModelDownloadRequest(
            url: String,
            huggingFaceToken: String = ""
        ): Request {
            val builder = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "AIAppStudio-Android/1.0")
                .get()

            val cleanToken = huggingFaceToken.trim()
            if (cleanToken.isNotBlank()) {
                builder.header("Authorization", "Bearer $cleanToken")
            }
            return builder.build()
        }
    }
}
