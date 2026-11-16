package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppThemeMode
import com.example.model.StudioSettings
import org.junit.Assert.assertEquals
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

    // Open Full-Screen Settings Modal and verify Appearance + Editor Theme cards
    composeRule.onNodeWithTag("open_settings_button").performClick()
    composeRule.onNodeWithTag("full_screen_settings_modal").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_appearance_card").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_editor_theme_card").assertIsDisplayed()
  }
}
