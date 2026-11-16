package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.service.BackgroundTaskManager
import com.example.model.BuildPhase
import com.example.ui.components.BackgroundExecutionKeepAliveCard
import com.example.ui.components.CyberCodeFactoryCard
import com.example.ui.theme.AiAppStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CyberCodeFactoryRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cyberCodeFactoryCard_rendersRealPhaseTelemetryWrappedLiveCodeAndProgress() {
        var buildPhase by mutableStateOf(BuildPhase.GENERATING_CODE)
        var progress by mutableFloatStateOf(0.42f)
        var stepMessage by mutableStateOf("Streaming MainActivity.kt (3,200 chars)…")
        var streamingCode by mutableStateOf(
            """
            package com.example.tracker
            // compose-compose-compose-compose-compose
            @Composable
            fun TrackerScreen() { Text("Live Stream") }
            """.trimIndent()
        )

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                CyberCodeFactoryCard(
                    projectName = "Habit Tracker",
                    stepIndex = buildPhase.nodeIndex,
                    totalSteps = 3,
                    stepMessage = stepMessage,
                    engineLabel = "Qwen 2.5 Coder 1.5B",
                    streamingCode = streamingCode,
                    buildPhase = buildPhase,
                    progress = progress,
                    onCancel = {}
                )
            }
        }

        // Real stage nodes reflect GENERATING_CODE (COMPILING completed, GENERATING active, ASSEMBLING pending)
        composeRule.onNodeWithTag("factory_node_compiling")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("COMPILING completed")
        composeRule.onNodeWithTag("factory_node_generating")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("GENERATING active")
        composeRule.onNodeWithTag("factory_node_assembling")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("ASSEMBLING pending")

        // Real progress & bottom telemetry (no hardcoded 75% Completed or dummy ticker)
        composeRule.onNodeWithTag("factory_status_ticker")
            .assertIsDisplayed()
            .assertTextEquals("Streaming MainActivity.kt (3,200 chars)…")
        composeRule.onNodeWithTag("factory_progress_percent_text")
            .assertIsDisplayed()
            .assertTextEquals("42% Completed")
        composeRule.onNodeWithText("75% Completed").assertDoesNotExist()
        composeRule.onNodeWithText("Forging UI elements...").assertDoesNotExist()

        // Transparent code viewport shows live streamed code and collapses repeated tokens
        composeRule.onNodeWithTag("live_streaming_code_preview").assertIsDisplayed()
        composeRule.onNodeWithText("compose-compose-compose-compose", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("TrackerScreen", substring = true).assertIsDisplayed()

        // Advance telemetry to PARSING_FILES at 92%
        composeRule.runOnIdle {
            buildPhase = BuildPhase.PARSING_FILES
            progress = 0.92f
            stepMessage = "Writing Theme.kt (4/5 files)…"
            streamingCode += "\n@Composable fun ThemePreview() { Surface {} }"
        }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("factory_node_generating")
            .assertContentDescriptionEquals("GENERATING completed")
        composeRule.onNodeWithTag("factory_node_assembling")
            .assertContentDescriptionEquals("ASSEMBLING active")
        composeRule.onNodeWithTag("factory_status_ticker")
            .assertTextEquals("Writing Theme.kt (4/5 files)…")
        composeRule.onNodeWithTag("factory_progress_percent_text")
            .assertTextEquals("92% Completed")
    }

    @Test
    fun backgroundTaskManager_handlesFullLifecycleWithNotificationsAndService() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Start Task
        BackgroundTaskManager.startTask(context, "Downloading Qwen 1.5B", "Initializing mirrors…")
        var state = BackgroundTaskManager.taskState.value
        assertTrue(state.isBusy)
        assertEquals("Downloading Qwen 1.5B", state.title)
        assertEquals("Initializing mirrors…", state.message)
        assertEquals(0.05f, state.progress, 0.001f)

        // 2. Update Progress
        BackgroundTaskManager.updateProgress(context, "Downloading Qwen 1.5B", "Downloaded 420 MB / 950 MB", 0.44f)
        state = BackgroundTaskManager.taskState.value
        assertTrue(state.isBusy)
        assertEquals("Downloaded 420 MB / 950 MB", state.message)
        assertEquals(0.44f, state.progress, 0.001f)

        // 3. Finish Task
        BackgroundTaskManager.finishTask(context, "Download Ready", "Model ready for offline inference")
        state = BackgroundTaskManager.taskState.value
        assertFalse(state.isBusy)
        assertEquals("Model ready for offline inference", state.message)
        assertEquals(1f, state.progress, 0.001f)

        // 4. Fail Task
        BackgroundTaskManager.startTask(context, "Cloud Build", "Starting…")
        BackgroundTaskManager.failTask(context, "Cloud Build", "Network error")
        state = BackgroundTaskManager.taskState.value
        assertFalse(state.isBusy)
        assertEquals("Network error", state.message)

        // 5. Cancel Task
        BackgroundTaskManager.startTask(context, "Compiling", "Starting…")
        BackgroundTaskManager.cancelTask(context)
        state = BackgroundTaskManager.taskState.value
        assertFalse(state.isBusy)
        assertEquals("Cancelled", state.message)
    }

    @Test
    fun backgroundExecutionKeepAliveCard_rendersStatusAndInformation() {
        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                BackgroundExecutionKeepAliveCard()
            }
        }

        composeRule.onNodeWithTag("background_execution_keep_alive_card").assertIsDisplayed()
        composeRule.onNodeWithText("Background Keep-Alive Architecture").assertIsDisplayed()
        composeRule.onNodeWithText("Anti-Kill Foreground Service • WakeLock • Wi-Fi Lock").assertIsDisplayed()
        composeRule.onNodeWithText("Ensures model downloads, AI code generation, and APK compilation continue uninterrupted when screen locks or backgrounded.")
            .assertIsDisplayed()
    }
}
