package com.example.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.AiEngineMode
import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.model.GeneratedFile
import com.example.model.StudioProject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "studio_projects")
data class ProjectSqlEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "app_name")
    val appName: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "prompt")
    val prompt: String,
    @ColumnInfo(name = "engine_mode")
    val engineMode: String,
    @ColumnInfo(name = "model_used")
    val modelUsed: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "files_json")
    val filesJson: String,
    @ColumnInfo(name = "github_repo_owner")
    val gitHubRepoOwner: String = "",
    @ColumnInfo(name = "github_repo_name")
    val gitHubRepoName: String = "",
    @ColumnInfo(name = "github_repo_url")
    val gitHubRepoUrl: String = "",
    @ColumnInfo(name = "last_workflow_run_id")
    val lastWorkflowRunId: Long? = null,
    @ColumnInfo(name = "last_build_status")
    val lastBuildStatus: String = "Not Built",
    @ColumnInfo(name = "last_actions_url")
    val lastActionsUrl: String = "",
    @ColumnInfo(name = "last_artifact_url")
    val lastArtifactUrl: String = ""
)

@Entity(tableName = "studio_build_logs")
data class BuildLogSqlEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "project_id", index = true)
    val projectId: String,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "level")
    val level: String,
    @ColumnInfo(name = "tag")
    val tag: String,
    @ColumnInfo(name = "message")
    val message: String,
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,
    @ColumnInfo(name = "line_number")
    val lineNumber: Int? = null
)

@Entity(tableName = "project_sql_tables")
data class CustomSqlTableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "project_id", index = true)
    val projectId: String,
    @ColumnInfo(name = "entity_name")
    val entityName: String,
    @ColumnInfo(name = "table_name")
    val tableName: String,
    @ColumnInfo(name = "columns_json")
    val columnsJson: String,
    @ColumnInfo(name = "generated_file_path")
    val generatedFilePath: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class SqlColumnSpec(
    val columnName: String,
    val kotlinPropertyName: String,
    val sqlType: String, // TEXT, INTEGER, REAL, BOOLEAN
    val isNullable: Boolean = false
)

@Dao
interface StudioSqlDao {

    @Query("SELECT * FROM studio_projects ORDER BY updated_at_ms DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectSqlEntity>>

    @Query("SELECT * FROM studio_projects ORDER BY updated_at_ms DESC")
    suspend fun getAllProjectsOnce(): List<ProjectSqlEntity>

    @Query("SELECT * FROM studio_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): ProjectSqlEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: ProjectSqlEntity)

    @Query("DELETE FROM studio_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("SELECT * FROM studio_build_logs WHERE project_id = :projectId ORDER BY timestamp_ms ASC LIMIT 300")
    fun getLogsForProjectFlow(projectId: String): Flow<List<BuildLogSqlEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildLog(log: BuildLogSqlEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildLogs(logs: List<BuildLogSqlEntity>)

    @Query("DELETE FROM studio_build_logs WHERE project_id = :projectId")
    suspend fun clearLogsForProject(projectId: String)

    @Query("SELECT * FROM project_sql_tables WHERE project_id = :projectId ORDER BY created_at_ms DESC")
    fun getCustomTablesForProjectFlow(projectId: String): Flow<List<CustomSqlTableEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomTable(table: CustomSqlTableEntity)

    @Query("DELETE FROM project_sql_tables WHERE id = :tableId")
    suspend fun deleteCustomTable(tableId: Int)

    @Query("DELETE FROM project_sql_tables WHERE project_id = :projectId")
    suspend fun deleteCustomTablesForProject(projectId: String)
}

@Database(
    entities = [
        ProjectSqlEntity::class,
        BuildLogSqlEntity::class,
        CustomSqlTableEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudioAppDatabase : RoomDatabase() {

    abstract fun studioSqlDao(): StudioSqlDao

    companion object {
        @Volatile
        private var Instance: StudioAppDatabase? = null

        fun getDatabase(context: Context): StudioAppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    StudioAppDatabase::class.java,
                    "studio_sql.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class StudioSqlRepository(private val dao: StudioSqlDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allProjectsFlow: Flow<List<ProjectSqlEntity>> = dao.getAllProjectsFlow()

    fun getLogsFlow(projectId: String): Flow<List<BuildLogSqlEntity>> =
        dao.getLogsForProjectFlow(projectId)

    fun getCustomTablesFlow(projectId: String): Flow<List<CustomSqlTableEntity>> =
        dao.getCustomTablesForProjectFlow(projectId)

    suspend fun upsertProject(project: StudioProject) {
        dao.upsertProject(project.toSqlEntity(json))
    }

    suspend fun getAllProjectsOnce(): List<StudioProject> {
        return dao.getAllProjectsOnce().mapNotNull { it.toDomainProject(json) }
    }

    suspend fun getProjectById(projectId: String): StudioProject? {
        return dao.getProjectById(projectId)?.toDomainProject(json)
    }

    suspend fun deleteProject(projectId: String) {
        dao.deleteProjectById(projectId)
        dao.clearLogsForProject(projectId)
        dao.deleteCustomTablesForProject(projectId)
    }

    suspend fun insertLog(projectId: String, entry: ConsoleLogEntry) {
        if (projectId.isBlank()) return
        dao.insertBuildLog(entry.toSqlEntity(projectId))
    }

    suspend fun insertLogs(projectId: String, entries: List<ConsoleLogEntry>) {
        if (projectId.isBlank() || entries.isEmpty()) return
        dao.insertBuildLogs(entries.map { it.toSqlEntity(projectId) })
    }

    suspend fun clearLogs(projectId: String) {
        if (projectId.isBlank()) return
        dao.clearLogsForProject(projectId)
    }

    suspend fun recordCustomTable(
        projectId: String,
        entityName: String,
        tableName: String,
        columns: List<SqlColumnSpec>,
        generatedFilePath: String
    ) {
        dao.insertCustomTable(
            CustomSqlTableEntity(
                projectId = projectId,
                entityName = entityName,
                tableName = tableName,
                columnsJson = json.encodeToString(columns),
                generatedFilePath = generatedFilePath
            )
        )
    }

    suspend fun deleteCustomTable(tableId: Int) {
        dao.deleteCustomTable(tableId)
    }

    fun decodeColumns(columnsJson: String): List<SqlColumnSpec> {
        return runCatching {
            json.decodeFromString<List<SqlColumnSpec>>(columnsJson)
        }.getOrDefault(emptyList())
    }
}

private fun StudioProject.toSqlEntity(json: Json): ProjectSqlEntity {
    return ProjectSqlEntity(
        id = id,
        appName = appName,
        packageName = packageName,
        prompt = prompt,
        engineMode = engineMode.name,
        modelUsed = modelUsed,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        filesJson = json.encodeToString(files),
        gitHubRepoOwner = gitHubRepoOwner.orEmpty(),
        gitHubRepoName = gitHubRepoName.orEmpty(),
        gitHubRepoUrl = gitHubRepoUrl.orEmpty(),
        lastWorkflowRunId = lastWorkflowRunId,
        lastBuildStatus = lastBuildStatus ?: "Not Built",
        lastActionsUrl = lastActionsUrl.orEmpty(),
        lastArtifactUrl = lastArtifactUrl.orEmpty()
    )
}

private fun ProjectSqlEntity.toDomainProject(json: Json): StudioProject? {
    val parsedFiles = runCatching {
        json.decodeFromString<List<GeneratedFile>>(filesJson)
    }.getOrNull() ?: return null

    val mode = runCatching { AiEngineMode.valueOf(engineMode) }
        .getOrDefault(AiEngineMode.CLOUD_OPENROUTER)

    return StudioProject(
        id = id,
        appName = appName,
        packageName = packageName,
        prompt = prompt,
        engineMode = mode,
        modelUsed = modelUsed,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        files = parsedFiles,
        gitHubRepoOwner = gitHubRepoOwner.ifBlank { null },
        gitHubRepoName = gitHubRepoName.ifBlank { null },
        gitHubRepoUrl = gitHubRepoUrl.ifBlank { null },
        lastWorkflowRunId = lastWorkflowRunId,
        lastBuildStatus = lastBuildStatus.ifBlank { null },
        lastActionsUrl = lastActionsUrl.ifBlank { null },
        lastArtifactUrl = lastArtifactUrl.ifBlank { null }
    )
}

private fun ConsoleLogEntry.toSqlEntity(projectId: String): BuildLogSqlEntity {
    return BuildLogSqlEntity(
        id = id,
        projectId = projectId,
        timestampMs = timestampMs,
        level = level.name,
        tag = tag,
        message = message,
        filePath = filePath,
        lineNumber = lineNumber
    )
}

fun BuildLogSqlEntity.toConsoleLogEntry(): ConsoleLogEntry {
    val parsedLevel = runCatching { ConsoleLogLevel.valueOf(level) }
        .getOrDefault(ConsoleLogLevel.INFO)
    return ConsoleLogEntry(
        id = id,
        timestampMs = timestampMs,
        level = parsedLevel,
        tag = tag,
        message = message,
        filePath = filePath,
        lineNumber = lineNumber
    )
}

object RoomCodeGenerator {

    data class PresetSqlSchema(
        val label: String,
        val entityName: String,
        val tableName: String,
        val columns: List<SqlColumnSpec>
    )

    val presets: List<PresetSqlSchema> = listOf(
        PresetSqlSchema(
            label = "Tasks / Todo SQL Table",
            entityName = "TaskItem",
            tableName = "tasks",
            columns = listOf(
                SqlColumnSpec("title", "title", "TEXT", isNullable = false),
                SqlColumnSpec("notes", "notes", "TEXT", isNullable = false),
                SqlColumnSpec("is_completed", "isCompleted", "BOOLEAN", isNullable = false),
                SqlColumnSpec("created_at", "createdAt", "INTEGER", isNullable = false)
            )
        ),
        PresetSqlSchema(
            label = "Notes / Journal SQL Table",
            entityName = "NoteEntry",
            tableName = "notes",
            columns = listOf(
                SqlColumnSpec("title", "title", "TEXT", isNullable = false),
                SqlColumnSpec("content", "content", "TEXT", isNullable = false),
                SqlColumnSpec("category", "category", "TEXT", isNullable = false),
                SqlColumnSpec("updated_at", "updatedAt", "INTEGER", isNullable = false)
            )
        ),
        PresetSqlSchema(
            label = "Expense / Budget SQL Table",
            entityName = "ExpenseRecord",
            tableName = "expenses",
            columns = listOf(
                SqlColumnSpec("merchant", "merchant", "TEXT", isNullable = false),
                SqlColumnSpec("amount", "amount", "REAL", isNullable = false),
                SqlColumnSpec("category", "category", "TEXT", isNullable = false),
                SqlColumnSpec("timestamp_ms", "timestampMs", "INTEGER", isNullable = false)
            )
        ),
        PresetSqlSchema(
            label = "Inventory / Catalog SQL Table",
            entityName = "CatalogItem",
            tableName = "catalog_items",
            columns = listOf(
                SqlColumnSpec("sku", "sku", "TEXT", isNullable = false),
                SqlColumnSpec("name", "name", "TEXT", isNullable = false),
                SqlColumnSpec("quantity", "quantity", "INTEGER", isNullable = false),
                SqlColumnSpec("unit_price", "unitPrice", "REAL", isNullable = false)
            )
        )
    )

    fun sanitizeEntityName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9]"), "")
        if (cleaned.isBlank()) return "ItemEntity"
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    fun sanitizeTableName(raw: String): String {
        val cleaned = raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        return cleaned.ifBlank { "app_items" }
    }

    /**
     * Generates a complete, self-contained Room SQLite database file (`@Entity`, `@Dao`,
     * `@Database`, and `Repository`) following the Android Room + KSP + Flow architecture.
     */
    fun generateRoomDatabaseFile(
        packageName: String,
        entityNameInput: String,
        tableNameInput: String,
        columns: List<SqlColumnSpec>
    ): Pair<String, String> {
        val entityName = sanitizeEntityName(entityNameInput)
        val tableName = sanitizeTableName(tableNameInput)
        val daoName = "${entityName}Dao"
        val dbName = "${entityName}Database"
        val repoName = "${entityName}Repository"
        val pkgPath = packageName.replace('.', '/')
        val filePath = "app/src/main/java/$pkgPath/data/local/${entityName}SqlDatabase.kt"

        val normalizedColumns = columns.ifEmpty {
            listOf(
                SqlColumnSpec("title", "title", "TEXT", false),
                SqlColumnSpec("created_at", "createdAt", "INTEGER", false)
            )
        }

        val fieldsCode = normalizedColumns.joinToString(",\n") { col ->
            val kotlinType = when (col.sqlType.uppercase()) {
                "INTEGER" -> "Long"
                "REAL" -> "Double"
                "BOOLEAN" -> "Boolean"
                else -> "String"
            } + if (col.isNullable) "?" else ""

            val defaultVal = when {
                col.isNullable -> " = null"
                col.sqlType.uppercase() == "INTEGER" && col.columnName.contains("created") -> " = System.currentTimeMillis()"
                col.sqlType.uppercase() == "INTEGER" -> " = 0L"
                col.sqlType.uppercase() == "REAL" -> " = 0.0"
                col.sqlType.uppercase() == "BOOLEAN" -> " = false"
                else -> " = \"\""
            }

            """    @ColumnInfo(name = "${col.columnName}")
    val ${col.kotlinPropertyName}: $kotlinType$defaultVal"""
        }

        val createTableSqlComment = buildString {
            append("CREATE TABLE IF NOT EXISTS `$tableName` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL")
            normalizedColumns.forEach { col ->
                val sqlType = when (col.sqlType.uppercase()) {
                    "INTEGER", "BOOLEAN" -> "INTEGER"
                    "REAL" -> "REAL"
                    else -> "TEXT"
                }
                val nullability = if (col.isNullable) "" else " NOT NULL"
                append(", `${col.columnName}` $sqlType$nullability")
            }
            append(");")
        }

        val orderByColumn = normalizedColumns.firstOrNull {
            it.columnName.contains("created") || it.columnName.contains("updated") || it.columnName.contains("timestamp")
        }?.columnName ?: "id"

        val content = """package $packageName.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Generated SQL Schema:
 * $createTableSqlComment
 */
@Entity(tableName = "$tableName")
data class $entityName(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
$fieldsCode
)

@Dao
interface $daoName {
    @Query("SELECT * FROM $tableName ORDER BY $orderByColumn DESC")
    fun getAllFlow(): Flow<List<$entityName>>

    @Query("SELECT * FROM $tableName WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): $entityName?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: $entityName): Long

    @Update
    suspend fun update(item: $entityName)

    @Delete
    suspend fun delete(item: $entityName)

    @Query("DELETE FROM $tableName")
    suspend fun deleteAll()
}

@Database(
    entities = [$entityName::class],
    version = 1,
    exportSchema = false
)
abstract class $dbName : RoomDatabase() {

    abstract fun dao(): $daoName

    companion object {
        @Volatile
        private var Instance: $dbName? = null

        fun getDatabase(context: Context): $dbName {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    $dbName::class.java,
                    "${tableName}_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class $repoName(private val dao: $daoName) {
    val allItemsFlow: Flow<List<$entityName>> = dao.getAllFlow()

    suspend fun getById(id: Int): $entityName? = dao.getById(id)

    suspend fun insert(item: $entityName): Long = dao.insert(item)

    suspend fun update(item: $entityName) = dao.update(item)

    suspend fun delete(item: $entityName) = dao.delete(item)

    suspend fun deleteAll() = dao.deleteAll()
}
"""
        return filePath to content
    }
}
