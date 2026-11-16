package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.ApiUsageSummary
import com.example.data.preferences.SecureCredentialCipher
import com.example.data.preferences.SettingsDataStore
import com.example.model.AppThemeMode
import com.example.model.OpenRouterModels
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context and launch MainActivity`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AI App Studio", appName)
    assertEquals(AppThemeMode.SYSTEM, StudioSettings().themeMode)

    composeRule.onNodeWithText("AI App Studio").assertIsDisplayed()
    composeRule.onNodeWithText("Offline AI • Local & Cloud Ready").assertIsDisplayed()
    composeRule.onNodeWithText("Build your ideas with AI").assertIsDisplayed()
    composeRule.onNodeWithTag("app_prompt_input").assertIsDisplayed()
    composeRule.onNodeWithTag("open_projects_drawer_button").assertIsDisplayed()
    composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()
    composeRule.onNodeWithTag("voice_prompt_button").assertIsDisplayed()
    composeRule.onNodeWithTag("generate_app_button").assertIsDisplayed()

    // Open Settings Screen and verify Status Indicator + Appearance + Editor Theme + API Credentials Vault cards
    composeRule.onNodeWithTag("open_settings_button").performClick()
    composeRule.onNodeWithTag("full_screen_settings_modal").assertIsDisplayed()
    composeRule.onNodeWithTag("saved_api_key_status_indicator").assertIsDisplayed()
    composeRule.onNodeWithTag("saved_api_key_status_dot").assertIsDisplayed()
    composeRule.onNodeWithTag("saved_api_key_status_label").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_appearance_card").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_editor_theme_card").assertIsDisplayed()

    composeRule.onNodeWithTag("api_credentials_vault_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("gemini_key_input").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("openrouter_key_input").performScrollTo().assertIsDisplayed()

    // Immediate validation feedback when invalid format is entered before saving
    composeRule.onNodeWithTag("gemini_key_input")
      .performTextReplacement("AIzaShort")
    composeRule.onNodeWithTag("gemini_validation_feedback").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("vault_save_blocked_banner").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("save_cloud_credentials_button").performScrollTo().assertIsNotEnabled()
    composeRule.onNodeWithTag("save_settings_button").assertIsNotEnabled()

    // Enter valid formats and verify save triggers live status verification action visibility
    composeRule.onNodeWithTag("gemini_key_input").performScrollTo()
      .performTextReplacement("AIzaSyTestGeminiKey1234567890abcdef")
    composeRule.onNodeWithTag("openrouter_key_input").performScrollTo()
      .performTextReplacement("sk-or-v1-testopenrouterkey1234567890")
    composeRule.onNodeWithTag("save_cloud_credentials_button").performScrollTo()
      .assertIsEnabled()
      .performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("verify_saved_key_status_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("verify_saved_key_status_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("api_usage_summary_card").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `settingsDataStore encrypts and persists OpenRouter and Gemini keys across sessions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cipher = SecureCredentialCipher(context)
    val geminiPlain = "AIzaSyPersistSessionKey9876543210xyz"
    val openRouterPlain = "sk-or-v1-persistsessionkey9876543210"

    val encrypted = cipher.encrypt(openRouterPlain)
    assertNotEquals(openRouterPlain, encrypted)
    assertTrue(SecureCredentialCipher.isEncryptedPayload(encrypted))
    assertEquals(openRouterPlain, cipher.decrypt(encrypted))

    // Session 1: write credentials
    val sessionOneStore = SettingsDataStore(context)
    sessionOneStore.updateCloudSettings(
      openRouterKey = openRouterPlain,
      geminiKey = geminiPlain,
      model = OpenRouterModels.GEMINI_25_PRO_FREE
    )

    // Session 2: read from a fresh SettingsDataStore instance
    val sessionTwoStore = SettingsDataStore(context)
    val loaded = sessionTwoStore.settingsFlow.first()
    assertEquals(geminiPlain, loaded.geminiApiKey)
    assertEquals(openRouterPlain, loaded.openRouterApiKey)
    assertEquals(OpenRouterModels.GEMINI_25_PRO_FREE, loaded.openRouterModel)

    // Clear credentials and verify removal persists
    sessionTwoStore.clearAllCloudCredentials()
    val afterClear = SettingsDataStore(context).settingsFlow.first()
    assertEquals("", afterClear.geminiApiKey)
    assertEquals("", afterClear.openRouterApiKey)
  }

  @Test
  fun `settingsDataStore persists parsed API usage summary across sessions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store1 = SettingsDataStore(context)
    val sampleSummary = ApiUsageSummary(
      providerName = "OpenRouter",
      lastHttpStatusCode = 200,
      lastLatencyMs = 84L,
      validationRequestsMade = 3,
      successfulValidations = 3,
      failedValidations = 0,
      totalTokensUsed = 420L,
      lastCallTokensUsed = 140L,
      requestsLimit = 200L,
      requestsRemaining = 197L,
      requestsReset = "30s",
      parsedHeaders = mapOf(
        "x-ratelimit-limit-requests" to "200",
        "x-ratelimit-remaining-requests" to "197",
        "x-usage-total-tokens" to "140"
      )
    )
    store1.saveApiUsageSummary(sampleSummary)

    val loaded = SettingsDataStore(context).apiUsageSummaryFlow.first()
    assertEquals("OpenRouter", loaded.providerName)
    assertEquals(3, loaded.validationRequestsMade)
    assertEquals(420L, loaded.totalTokensUsed)
    assertEquals(197L, loaded.requestsRemaining)
    assertEquals("197", loaded.parsedHeaders["x-ratelimit-remaining-requests"])

    store1.clearApiUsageSummary()
    val cleared = SettingsDataStore(context).apiUsageSummaryFlow.first()
    assertEquals(0, cleared.validationRequestsMade)
    assertEquals(0L, cleared.totalTokensUsed)
  }

  @Test
  fun `settingsDataStore persists monthly usage threshold settings across sessions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store1 = SettingsDataStore(context)
    store1.updateMonthlyUsageThreshold(
      enabled = true,
      metric = com.example.model.ApiUsageLimitMetric.TOKENS,
      monthlyLimit = 25_000L,
      warningThresholdPercent = 75
    )

    val loaded = SettingsDataStore(context).settingsFlow.first()
    assertEquals(true, loaded.monthlyUsageAlertEnabled)
    assertEquals(com.example.model.ApiUsageLimitMetric.TOKENS, loaded.monthlyUsageLimitMetric)
    assertEquals(25_000L, loaded.monthlyUsageLimit)
    assertEquals(75, loaded.monthlyUsageWarningThresholdPercent)
  }

  @Test
  fun `monthly threshold setting triggers dashboard warning banner when usage reaches limit`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val currentMonth = com.example.data.preferences.ApiUsageThresholdEvaluator.yearMonthFromEpochMs()
    runBlocking {
      val store = SettingsDataStore(context)
      store.saveApiUsageSummary(
        ApiUsageSummary(
          providerName = "OpenRouter",
          lastHttpStatusCode = 200,
          validationRequestsMade = 5,
          successfulValidations = 5,
          monthlyRequestsMade = 5,
          trackingYearMonth = currentMonth
        )
      )
      store.updateMonthlyUsageThreshold(
        enabled = true,
        metric = com.example.model.ApiUsageLimitMetric.REQUESTS,
        monthlyLimit = 100L,
        warningThresholdPercent = 80
      )
    }
    composeRule.waitForIdle()

    // Open Settings, set monthly request limit to 5 (matching current 5 requests), and apply
    composeRule.onNodeWithTag("open_settings_button").performClick()
    composeRule.onNodeWithTag("full_screen_settings_modal").assertIsDisplayed()
    composeRule.onNodeWithTag("monthly_limit_input").performScrollTo().performTextReplacement("5")
    composeRule.onNodeWithTag("save_monthly_threshold_button").performScrollTo()
      .assertIsEnabled()
      .performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("settings_monthly_threshold_active_badge").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("settings_monthly_threshold_active_badge").performScrollTo().assertIsDisplayed()

    // Return to Dashboard and verify warning banner is rendered
    composeRule.onNodeWithTag("close_settings_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("dashboard_api_usage_warning_banner").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("dashboard_usage_warning_title").assertIsDisplayed()
    composeRule.onNodeWithTag("dashboard_usage_warning_percent").assertIsDisplayed()
    composeRule.onNodeWithTag("dashboard_usage_warning_settings_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `localAiModelManager displays expanded suggestions specialty filters and tuning tips`() {
    composeRule.onNodeWithTag("open_settings_button").performClick()
    composeRule.onNodeWithTag("full_screen_settings_modal").assertIsDisplayed()

    composeRule.onNodeWithTag("local_ai_model_manager_panel").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Local AI Model Catalog (18 Models)").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("local_ai_top_picks_row").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("local_ai_specialty_filters").performScrollTo().assertIsDisplayed()

    // Filter to JSON & Actions -> Hammer 2.1 1.5B should appear
    composeRule.onNodeWithTag("local_model_filter_tool_calling").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("local_model_catalog_scroll_list").performScrollTo()
    composeRule.onNodeWithTag("model_tier_card_hammer21_15b").performScrollTo().assertIsDisplayed()

    // Expand Local AI optimization tips card
    composeRule.onNodeWithTag("local_ai_tips_toggle").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("local_ai_tip_ram_headroom").performScrollTo().assertIsDisplayed()
  }
}
