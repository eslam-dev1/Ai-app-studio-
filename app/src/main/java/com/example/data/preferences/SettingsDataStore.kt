package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AiEngineMode
import com.example.model.ApiUsageLimitMetric
import com.example.model.AppThemeMode
import com.example.model.EditorThemeMode
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_app_studio_prefs")

class SettingsDataStore(private val context: Context) {

    private val cipher = SecureCredentialCipher(context.applicationContext)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private object Keys {
        val AI_ENGINE_MODE = stringPreferencesKey("ai_engine_mode")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENROUTER_MODEL = stringPreferencesKey("openrouter_model")
        val LOCAL_SERVER_BASE_URL = stringPreferencesKey("local_server_base_url")
        val LOCAL_SERVER_MODEL = stringPreferencesKey("local_server_model")
        val LOCAL_MODEL_FILE_PATH = stringPreferencesKey("local_model_file_path")
        val LOCAL_MODEL_DISPLAY_NAME = stringPreferencesKey("local_model_display_name")
        val LOCAL_MODEL_SIZE_BYTES = longPreferencesKey("local_model_size_bytes")
        val HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
        val GITHUB_PAT = stringPreferencesKey("github_pat")
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val EDITOR_THEME_MODE = stringPreferencesKey("editor_theme_mode")
        val API_USAGE_SUMMARY_JSON = stringPreferencesKey("api_usage_summary_json")
        val MONTHLY_USAGE_ALERT_ENABLED = booleanPreferencesKey("monthly_usage_alert_enabled")
        val MONTHLY_USAGE_LIMIT_METRIC = stringPreferencesKey("monthly_usage_limit_metric")
        val MONTHLY_USAGE_LIMIT = longPreferencesKey("monthly_usage_limit")
        val MONTHLY_USAGE_WARNING_PERCENT = intPreferencesKey("monthly_usage_warning_percent")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val modeName = prefs[Keys.AI_ENGINE_MODE]
        val mode = AiEngineMode.entries.find { it.name == modeName } ?: AiEngineMode.ON_DEVICE_MEDIAPIPE
        val themeName = prefs[Keys.THEME_MODE]
        val themeMode = AppThemeMode.entries.find { it.name == themeName } ?: AppThemeMode.SYSTEM
        val editorThemeName = prefs[Keys.EDITOR_THEME_MODE]
        val editorThemeMode = EditorThemeMode.entries.find { it.name == editorThemeName } ?: EditorThemeMode.CYBER_NIGHT
        val storedHfToken = prefs[Keys.HUGGING_FACE_TOKEN]
        val resolvedHfToken = if (storedHfToken == null) {
            StudioSettings.DEFAULT_HUGGING_FACE_TOKEN
        } else {
            cipher.decrypt(storedHfToken)
        }
        val limitMetricName = prefs[Keys.MONTHLY_USAGE_LIMIT_METRIC]
        val limitMetric = ApiUsageLimitMetric.entries.find { it.name == limitMetricName }
            ?: ApiUsageLimitMetric.REQUESTS
        val storedLimit = prefs[Keys.MONTHLY_USAGE_LIMIT] ?: StudioSettings.DEFAULT_MONTHLY_REQUEST_LIMIT
        val storedWarningPct = (prefs[Keys.MONTHLY_USAGE_WARNING_PERCENT]
            ?: StudioSettings.DEFAULT_WARNING_THRESHOLD_PERCENT).coerceIn(10, 100)
        StudioSettings(
            aiEngineMode = mode,
            openRouterApiKey = cipher.decrypt(prefs[Keys.OPENROUTER_API_KEY] ?: ""),
            geminiApiKey = cipher.decrypt(prefs[Keys.GEMINI_API_KEY] ?: ""),
            openRouterModel = prefs[Keys.OPENROUTER_MODEL] ?: OpenRouterModels.GEMINI_20_FLASH_FREE,
            localServerBaseUrl = prefs[Keys.LOCAL_SERVER_BASE_URL] ?: "http://127.0.0.1:11434/v1/",
            localServerModel = prefs[Keys.LOCAL_SERVER_MODEL] ?: "qwen2.5-coder:3b",
            localModelFilePath = prefs[Keys.LOCAL_MODEL_FILE_PATH] ?: "",
            localModelDisplayName = prefs[Keys.LOCAL_MODEL_DISPLAY_NAME] ?: "",
            localModelSizeBytes = prefs[Keys.LOCAL_MODEL_SIZE_BYTES] ?: 0L,
            huggingFaceToken = resolvedHfToken,
            gitHubPat = cipher.decrypt(prefs[Keys.GITHUB_PAT] ?: ""),
            gitHubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
            themeMode = themeMode,
            editorThemeMode = editorThemeMode,
            monthlyUsageAlertEnabled = prefs[Keys.MONTHLY_USAGE_ALERT_ENABLED] ?: true,
            monthlyUsageLimitMetric = limitMetric,
            monthlyUsageLimit = storedLimit.coerceAtLeast(1L),
            monthlyUsageWarningThresholdPercent = storedWarningPct
        )
    }

    val apiUsageSummaryFlow: Flow<ApiUsageSummary> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[Keys.API_USAGE_SUMMARY_JSON].orEmpty()
        if (raw.isBlank()) {
            ApiUsageSummary()
        } else {
            runCatching { json.decodeFromString<ApiUsageSummary>(raw) }
                .getOrElse { ApiUsageSummary() }
        }
    }

    fun isHardwareKeystoreBacked(): Boolean = cipher.isHardwareKeystoreAvailable()

    suspend fun saveApiUsageSummary(summary: ApiUsageSummary) {
        context.studioDataStore.edit {
            it[Keys.API_USAGE_SUMMARY_JSON] = json.encodeToString(summary)
        }
    }

    suspend fun clearApiUsageSummary() {
        context.studioDataStore.edit {
            it.remove(Keys.API_USAGE_SUMMARY_JSON)
        }
    }

    suspend fun updateThemeMode(themeMode: AppThemeMode) {
        context.studioDataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun updateEngineMode(mode: AiEngineMode) {
        context.studioDataStore.edit { it[Keys.AI_ENGINE_MODE] = mode.name }
    }

    suspend fun updateCloudModel(model: String) {
        context.studioDataStore.edit { it[Keys.OPENROUTER_MODEL] = model }
    }

    suspend fun updateCloudSettings(openRouterKey: String, geminiKey: String, model: String) {
        context.studioDataStore.edit {
            val cleanOr = openRouterKey.trim()
            val cleanGem = geminiKey.trim()
            if (cleanOr.isEmpty()) it.remove(Keys.OPENROUTER_API_KEY) else it[Keys.OPENROUTER_API_KEY] = cipher.encrypt(cleanOr)
            if (cleanGem.isEmpty()) it.remove(Keys.GEMINI_API_KEY) else it[Keys.GEMINI_API_KEY] = cipher.encrypt(cleanGem)
            it[Keys.OPENROUTER_MODEL] = model
        }
    }

    suspend fun clearGeminiApiKey() {
        context.studioDataStore.edit {
            it.remove(Keys.GEMINI_API_KEY)
        }
    }

    suspend fun clearOpenRouterApiKey() {
        context.studioDataStore.edit {
            it.remove(Keys.OPENROUTER_API_KEY)
        }
    }

    suspend fun clearAllCloudCredentials() {
        context.studioDataStore.edit {
            it.remove(Keys.OPENROUTER_API_KEY)
            it.remove(Keys.GEMINI_API_KEY)
        }
    }

    suspend fun updateLocalServerSettings(baseUrl: String, modelName: String) {
        context.studioDataStore.edit {
            it[Keys.LOCAL_SERVER_BASE_URL] = baseUrl.trim()
            it[Keys.LOCAL_SERVER_MODEL] = modelName.trim()
        }
    }

    suspend fun updateOnDeviceModel(filePath: String, displayName: String, sizeBytes: Long) {
        context.studioDataStore.edit {
            it[Keys.LOCAL_MODEL_FILE_PATH] = filePath
            it[Keys.LOCAL_MODEL_DISPLAY_NAME] = displayName
            it[Keys.LOCAL_MODEL_SIZE_BYTES] = sizeBytes
        }
    }

    suspend fun clearOnDeviceModel() {
        context.studioDataStore.edit {
            it[Keys.LOCAL_MODEL_FILE_PATH] = ""
            it[Keys.LOCAL_MODEL_DISPLAY_NAME] = ""
            it[Keys.LOCAL_MODEL_SIZE_BYTES] = 0L
        }
    }

    suspend fun updateHuggingFaceToken(token: String) {
        context.studioDataStore.edit {
            val clean = token.trim()
            if (clean.isEmpty()) {
                it[Keys.HUGGING_FACE_TOKEN] = ""
            } else {
                it[Keys.HUGGING_FACE_TOKEN] = cipher.encrypt(clean)
            }
        }
    }

    suspend fun updateGitHubSettings(pat: String, username: String) {
        context.studioDataStore.edit {
            val cleanPat = pat.trim()
            if (cleanPat.isEmpty()) it.remove(Keys.GITHUB_PAT) else it[Keys.GITHUB_PAT] = cipher.encrypt(cleanPat)
            it[Keys.GITHUB_USERNAME] = username.trim()
        }
    }

    suspend fun updateEditorThemeMode(mode: EditorThemeMode) {
        context.studioDataStore.edit { it[Keys.EDITOR_THEME_MODE] = mode.name }
    }

    suspend fun updateMonthlyUsageThreshold(
        enabled: Boolean,
        metric: ApiUsageLimitMetric,
        monthlyLimit: Long,
        warningThresholdPercent: Int
    ) {
        context.studioDataStore.edit {
            it[Keys.MONTHLY_USAGE_ALERT_ENABLED] = enabled
            it[Keys.MONTHLY_USAGE_LIMIT_METRIC] = metric.name
            it[Keys.MONTHLY_USAGE_LIMIT] = monthlyLimit.coerceAtLeast(1L)
            it[Keys.MONTHLY_USAGE_WARNING_PERCENT] = warningThresholdPercent.coerceIn(10, 100)
        }
    }

    suspend fun saveAll(settings: StudioSettings) {
        context.studioDataStore.edit {
            it[Keys.AI_ENGINE_MODE] = settings.aiEngineMode.name
            val cleanOr = settings.openRouterApiKey.trim()
            val cleanGem = settings.geminiApiKey.trim()
            val cleanHf = settings.huggingFaceToken.trim()
            val cleanPat = settings.gitHubPat.trim()

            if (cleanOr.isEmpty()) it.remove(Keys.OPENROUTER_API_KEY) else it[Keys.OPENROUTER_API_KEY] = cipher.encrypt(cleanOr)
            if (cleanGem.isEmpty()) it.remove(Keys.GEMINI_API_KEY) else it[Keys.GEMINI_API_KEY] = cipher.encrypt(cleanGem)
            it[Keys.OPENROUTER_MODEL] = settings.openRouterModel
            it[Keys.LOCAL_SERVER_BASE_URL] = settings.localServerBaseUrl.trim()
            it[Keys.LOCAL_SERVER_MODEL] = settings.localServerModel.trim()
            it[Keys.LOCAL_MODEL_FILE_PATH] = settings.localModelFilePath
            it[Keys.LOCAL_MODEL_DISPLAY_NAME] = settings.localModelDisplayName
            it[Keys.LOCAL_MODEL_SIZE_BYTES] = settings.localModelSizeBytes
            it[Keys.HUGGING_FACE_TOKEN] = if (cleanHf.isEmpty()) "" else cipher.encrypt(cleanHf)
            if (cleanPat.isEmpty()) it.remove(Keys.GITHUB_PAT) else it[Keys.GITHUB_PAT] = cipher.encrypt(cleanPat)
            it[Keys.GITHUB_USERNAME] = settings.gitHubUsername.trim()
            it[Keys.THEME_MODE] = settings.themeMode.name
            it[Keys.EDITOR_THEME_MODE] = settings.editorThemeMode.name
            it[Keys.MONTHLY_USAGE_ALERT_ENABLED] = settings.monthlyUsageAlertEnabled
            it[Keys.MONTHLY_USAGE_LIMIT_METRIC] = settings.monthlyUsageLimitMetric.name
            it[Keys.MONTHLY_USAGE_LIMIT] = settings.monthlyUsageLimit.coerceAtLeast(1L)
            it[Keys.MONTHLY_USAGE_WARNING_PERCENT] = settings.monthlyUsageWarningThresholdPercent.coerceIn(10, 100)
        }
    }
}
