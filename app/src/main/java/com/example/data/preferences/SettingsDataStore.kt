package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AiEngineMode
import com.example.model.AppThemeMode
import com.example.model.EditorThemeMode
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_app_studio_prefs")

class SettingsDataStore(private val context: Context) {

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
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val modeName = prefs[Keys.AI_ENGINE_MODE]
        val mode = AiEngineMode.entries.find { it.name == modeName } ?: AiEngineMode.ON_DEVICE_MEDIAPIPE
        val themeName = prefs[Keys.THEME_MODE]
        val themeMode = AppThemeMode.entries.find { it.name == themeName } ?: AppThemeMode.SYSTEM
        val editorThemeName = prefs[Keys.EDITOR_THEME_MODE]
        val editorThemeMode = EditorThemeMode.entries.find { it.name == editorThemeName } ?: EditorThemeMode.CYBER_NIGHT
        StudioSettings(
            aiEngineMode = mode,
            openRouterApiKey = prefs[Keys.OPENROUTER_API_KEY] ?: "",
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            openRouterModel = prefs[Keys.OPENROUTER_MODEL] ?: OpenRouterModels.GEMINI_20_FLASH_FREE,
            localServerBaseUrl = prefs[Keys.LOCAL_SERVER_BASE_URL] ?: "http://127.0.0.1:11434/v1/",
            localServerModel = prefs[Keys.LOCAL_SERVER_MODEL] ?: "qwen2.5-coder:3b",
            localModelFilePath = prefs[Keys.LOCAL_MODEL_FILE_PATH] ?: "",
            localModelDisplayName = prefs[Keys.LOCAL_MODEL_DISPLAY_NAME] ?: "",
            localModelSizeBytes = prefs[Keys.LOCAL_MODEL_SIZE_BYTES] ?: 0L,
            huggingFaceToken = prefs[Keys.HUGGING_FACE_TOKEN] ?: StudioSettings.DEFAULT_HUGGING_FACE_TOKEN,
            gitHubPat = prefs[Keys.GITHUB_PAT] ?: "",
            gitHubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
            themeMode = themeMode,
            editorThemeMode = editorThemeMode
        )
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
            it[Keys.OPENROUTER_API_KEY] = openRouterKey.trim()
            it[Keys.GEMINI_API_KEY] = geminiKey.trim()
            it[Keys.OPENROUTER_MODEL] = model
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
            it[Keys.HUGGING_FACE_TOKEN] = token.trim()
        }
    }

    suspend fun updateGitHubSettings(pat: String, username: String) {
        context.studioDataStore.edit {
            it[Keys.GITHUB_PAT] = pat.trim()
            it[Keys.GITHUB_USERNAME] = username.trim()
        }
    }

    suspend fun updateEditorThemeMode(mode: EditorThemeMode) {
        context.studioDataStore.edit { it[Keys.EDITOR_THEME_MODE] = mode.name }
    }

    suspend fun saveAll(settings: StudioSettings) {
        context.studioDataStore.edit {
            it[Keys.AI_ENGINE_MODE] = settings.aiEngineMode.name
            it[Keys.OPENROUTER_API_KEY] = settings.openRouterApiKey.trim()
            it[Keys.GEMINI_API_KEY] = settings.geminiApiKey.trim()
            it[Keys.OPENROUTER_MODEL] = settings.openRouterModel
            it[Keys.LOCAL_SERVER_BASE_URL] = settings.localServerBaseUrl.trim()
            it[Keys.LOCAL_SERVER_MODEL] = settings.localServerModel.trim()
            it[Keys.LOCAL_MODEL_FILE_PATH] = settings.localModelFilePath
            it[Keys.LOCAL_MODEL_DISPLAY_NAME] = settings.localModelDisplayName
            it[Keys.LOCAL_MODEL_SIZE_BYTES] = settings.localModelSizeBytes
            it[Keys.HUGGING_FACE_TOKEN] = settings.huggingFaceToken.trim()
            it[Keys.GITHUB_PAT] = settings.gitHubPat.trim()
            it[Keys.GITHUB_USERNAME] = settings.gitHubUsername.trim()
            it[Keys.THEME_MODE] = settings.themeMode.name
            it[Keys.EDITOR_THEME_MODE] = settings.editorThemeMode.name
        }
    }
}
