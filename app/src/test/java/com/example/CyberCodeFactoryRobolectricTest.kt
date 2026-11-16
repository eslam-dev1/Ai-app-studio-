package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp")
    fun workspaceBuildApkTab_switchesBetweenOfflineAiAndGitHubCloudAndTriggersOfflineSign() {
        var offlineBuildTriggeredWith: Triple<String, String, String>? = null
        val sampleProject = com.example.model.StudioProject(
            id = "offline-ui-test",
            appName = "Offline Studio",
            packageName = "com.example.offlinestudio",
            prompt = "Test offline build & sign",
            files = listOf(
                com.example.model.GeneratedFile(
                    path = "app/src/main/java/com/example/offlinestudio/MainActivity.kt",
                    content = "package com.example.offlinestudio\nclass MainActivity"
                ),
                com.example.model.GeneratedFile(
                    path = "app/build.gradle.kts",
                    content = "android { namespace = \"com.example.offlinestudio\" }"
                )
            )
        )
        val signingStatus = com.example.model.OfflineSigningStatus(
            hasKeystore = true,
            keyAlias = "release",
            commonName = "Offline Studio",
            sha256Fingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
            signedApkPath = "/tmp/offline-studio-signed.apk",
            isSignatureVerified = true,
            v1Signed = true,
            v2Signed = true,
            v3Signed = true,
            signedApkSizeBytes = 256_000L,
            aiEngineStatus = "Offline AI Ready: Qwen 2.5 1.5B"
        )

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                com.example.ui.screens.WorkspaceBuildApkTab(
                    project = sampleProject,
                    settings = com.example.model.StudioSettings(),
                    gitState = com.example.model.GitRepoState(),
                    hasGitHubPat = true,
                    isGitBusy = false,
                    isCloudBuildBusy = false,
                    cloudBuildProgress = null,
                    isOfflineBuildBusy = false,
                    offlineBuildProgress = null,
                    offlineSigningStatus = signingStatus,
                    onTriggerCloudBuild = {},
                    onRefreshCloudBuildStatus = {},
                    onTriggerOfflineAiBuildAndSign = { alias, pass, cn ->
                        offlineBuildTriggeredWith = Triple(alias, pass, cn)
                    },
                    onPackageZip = { null },
                    onExportBuildLogs = { null },
                    onInitGit = {},
                    onRefreshGit = {},
                    onStageFile = {},
                    onUnstageFile = {},
                    onStageAll = {},
                    onCommitGit = {},
                    onConfigureRemote = {},
                    onPushGit = {},
                    onPullGit = {},
                    onOpenConsole = {},
                    onOpenSettings = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("workspace_build_apk_tab")
        list.performScrollToNode(hasTestTag("build_engine_selector"))
        composeRule.onNodeWithTag("build_engine_selector").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("offline_keystore_fingerprint_text"))
        composeRule.onNodeWithTag("offline_keystore_fingerprint_text").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("offline_ai_build_sign_button"))
        composeRule.onNodeWithTag("offline_ai_build_sign_button").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("pick_apk_sign_offline_button"))
        composeRule.onNodeWithTag("pick_apk_sign_offline_button").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("install_offline_signed_apk_button"))
        composeRule.onNodeWithTag("install_offline_signed_apk_button").assertIsDisplayed()

        list.performScrollToNode(hasTestTag("offline_ai_build_sign_button"))
        composeRule.onNodeWithTag("offline_ai_build_sign_button").performClick()
        assertEquals(
            Triple(
                com.example.data.build.OfflineApkBuildAndSignEngine.DEFAULT_KEY_ALIAS,
                com.example.data.build.OfflineApkBuildAndSignEngine.DEFAULT_KEY_PASSWORD,
                "Offline Studio"
            ),
            offlineBuildTriggeredWith
        )

        list.performScrollToNode(hasTestTag("build_mode_github_cloud"))
        composeRule.onNodeWithTag("build_mode_github_cloud").performClick()
        composeRule.waitForIdle()
        list.performScrollToNode(hasTestTag("cloud_apk_build_button"))
        composeRule.onNodeWithTag("cloud_apk_build_button").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp")
    fun livePreviewDeviceFrame_switchesNavigationTabsAndSupportsClickableActions() {
        val sampleSource = """
            package com.example.previewtest

            import androidx.compose.foundation.clickable
            import androidx.compose.foundation.layout.Box
            import androidx.compose.material3.NavigationBar
            import androidx.compose.material3.NavigationBarItem
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Modifier

            @Composable
            fun DemoRootScreen() {
                var selectedTab by remember { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("HomeTab") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("ExploreTab") },
                                icon = {}
                            )
                        }
                    }
                ) { _ ->
                    when (selectedTab) {
                        0 -> Box(modifier = Modifier.clickable { selectedTab = 1 }) {
                            Text("Welcome Home View")
                        }
                        else -> Text("Explore Catalog View")
                    }
                }
            }
        """.trimIndent()

        val parseResult = com.example.preview.ComposePreviewEngine.parseProjectSources(
            files = listOf("app/src/main/java/com/example/previewtest/MainActivity.kt" to sampleSource)
        )

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                com.example.preview.LivePreviewDeviceFrame(
                    composableInfo = parseResult.selectedComposable,
                    allComposables = parseResult.composables,
                    darkPreview = true,
                    appName = "Preview Test",
                    fontScale = 1.15f,
                    isLandscape = false
                )
            }
        }

        composeRule.onNodeWithText("Welcome Home View").assertIsDisplayed()
        composeRule.onNodeWithText("Explore Catalog View").assertDoesNotExist()

        // Tapping NavigationBarItem "ExploreTab" switches active when() branch inside preview device
        composeRule.onNodeWithText("ExploreTab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Explore Catalog View").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome Home View").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp")
    fun workspaceBuildApkTab_displaysMediaPipeGeneratedOfflineTasksAndExpandsScript() {
        val sampleProject = com.example.model.StudioProject(
            id = "mediapipe-ui-tasks-test",
            appName = "Offline Tasks App",
            packageName = "com.example.offlinetasks",
            prompt = "Test offline pipeline UI",
            files = listOf(
                com.example.model.GeneratedFile(
                    path = "app/src/main/java/com/example/offlinetasks/MainActivity.kt",
                    content = "package com.example.offlinetasks\nclass MainActivity"
                ),
                com.example.model.GeneratedFile(
                    path = "gradle/offline-build-tasks.gradle.kts",
                    content = "tasks.register(\"offlineBuildAndSignRelease\") { /* offline */ }"
                )
            )
        )
        val tasks = com.example.data.build.LocalMediaPipeBuildPipeline.defaultBuildTaskSpecs(
            generatedByMediaPipe = true
        )
        val signingStatus = com.example.model.OfflineSigningStatus(
            hasKeystore = true,
            keyAlias = "release",
            commonName = "Offline Tasks App",
            sha256Fingerprint = "11:22:33:44:55:66",
            aiEngineStatus = "Offline AI Ready (Qwen 2.5 0.5B)",
            pipelineTasks = tasks,
            pipelineTasksScript = "tasks.register(\"offlineBuildAndSignRelease\") {\n    group = \"offline-build\"\n}",
            pipelineOriginLabel = "MediaPipe LLM • Qwen 2.5 0.5B",
            pipelineLatencyMs = 420L
        )

        var openedFilePath = ""
        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                com.example.ui.screens.WorkspaceBuildApkTab(
                    project = sampleProject,
                    settings = com.example.model.StudioSettings(),
                    gitState = com.example.model.GitRepoState(),
                    hasGitHubPat = false,
                    isGitBusy = false,
                    isCloudBuildBusy = false,
                    cloudBuildProgress = null,
                    isOfflineBuildBusy = false,
                    offlineBuildProgress = null,
                    offlineBuildStreamPreview = "",
                    offlineSigningStatus = signingStatus,
                    onTriggerCloudBuild = {},
                    onRefreshCloudBuildStatus = {},
                    onNavigateToFile = { path, _ -> openedFilePath = path },
                    onPackageZip = { null },
                    onExportBuildLogs = { null },
                    onInitGit = {},
                    onRefreshGit = {},
                    onStageFile = {},
                    onUnstageFile = {},
                    onStageAll = {},
                    onCommitGit = {},
                    onConfigureRemote = {},
                    onPushGit = {},
                    onPullGit = {},
                    onOpenConsole = {},
                    onOpenSettings = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("workspace_build_apk_tab")
        list.performScrollToNode(hasTestTag("offline_pipeline_tasks_card"))
        composeRule.onNodeWithTag("offline_pipeline_tasks_card").assertIsDisplayed()
        composeRule.onNodeWithText("Local Compilation & Signing Tasks").assertIsDisplayed()
        composeRule.onNodeWithText("0 GitHub").assertIsDisplayed()

        list.performScrollToNode(hasTestTag("offline_task_offlineCompileReleaseApk"))
        composeRule.onNodeWithText(":offlineCompileReleaseApk").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("offline_task_offlineSignReleaseApk"))
        composeRule.onNodeWithText(":offlineSignReleaseApk").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("offline_task_offlineBuildAndSignRelease"))
        composeRule.onNodeWithText(":offlineBuildAndSignRelease").assertIsDisplayed()

        list.performScrollToNode(hasTestTag("toggle_offline_tasks_preview_button"))
        composeRule.onNodeWithTag("toggle_offline_tasks_preview_button").performClick()
        composeRule.waitForIdle()
        list.performScrollToNode(hasTestTag("offline_pipeline_script_preview"))
        composeRule.onNodeWithTag("offline_pipeline_script_preview").assertIsDisplayed()

        list.performScrollToNode(hasTestTag("open_offline_gradle_tasks_file_button"))
        composeRule.onNodeWithTag("open_offline_gradle_tasks_file_button").performClick()
        assertEquals(
            com.example.data.build.LocalMediaPipeBuildPipeline.GRADLE_PIPELINE_FILE_PATH,
            openedFilePath
        )
    }

    @Test
    fun cyberCodeFactoryCard_switchesToGlowingSuccessRingOnBuildCompletion() {
        var progress by mutableFloatStateOf(0.50f)
        var stepMessage by mutableStateOf("Compiling Kotlin sources…")
        var isSuccess by mutableStateOf(false)

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                CyberCodeFactoryCard(
                    projectName = "Task Tracker",
                    stepIndex = 1,
                    totalSteps = 3,
                    stepMessage = stepMessage,
                    engineLabel = "Offline MediaPipe LLM",
                    streamingCode = "package com.example.tasktracker\nclass MainActivity",
                    buildPhase = BuildPhase.GENERATING_CODE,
                    progress = progress,
                    isSuccess = isSuccess,
                    onCancel = {}
                )
            }
        }

        // Initially in building state: dual gears visible, success ring not shown, cancel button visible
        composeRule.onNodeWithTag("factory_dual_gears").assertIsDisplayed()
        composeRule.onNodeWithTag("factory_success_ring").assertDoesNotExist()
        composeRule.onNodeWithTag("factory_milestone_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("cancel_generation_button").assertIsDisplayed()
        composeRule.onNodeWithTag("factory_progress_percent_text").assertTextEquals("50% Completed")

        // Switch to successful build completion
        composeRule.runOnIdle {
            progress = 1.0f
            stepMessage = "Build Complete: task-tracker-release.apk (2.4 MB)"
            isSuccess = true
        }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()

        // Milestone reached: glowing green ring, success badge, 100% completed
        composeRule.onNodeWithTag("factory_success_ring").assertIsDisplayed()
        composeRule.onNodeWithTag("factory_milestone_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_generation_button").assertDoesNotExist()
        composeRule.onNodeWithText("BUILD SUCCEEDED").assertIsDisplayed()
        composeRule.onNodeWithTag("factory_progress_percent_text").assertTextEquals("100% Completed")
        composeRule.onNodeWithTag("factory_node_compiling").assertContentDescriptionEquals("COMPILING completed")
        composeRule.onNodeWithTag("factory_node_generating").assertContentDescriptionEquals("GENERATING completed")
        composeRule.onNodeWithTag("factory_node_assembling").assertContentDescriptionEquals("ASSEMBLING completed")
    }

    @Test
    fun generatorScreen_displaysDynamicSuggestionsAboveInputAndFallsBackWhenEmpty() {
        var dynamicSuggestions by mutableStateOf<List<String>>(emptyList())

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                com.example.ui.screens.GeneratorScreen(
                    settings = com.example.model.StudioSettings(),
                    projects = emptyList(),
                    generationState = com.example.model.GenerationUiState.Idle,
                    dynamicSuggestions = dynamicSuggestions,
                    onEngineModeChange = {},
                    onOpenSettings = {},
                    onGenerateApp = {},
                    onCancelGeneration = {},
                    onOpenProject = {},
                    onDeleteProject = {}
                )
            }
        }

        // Default fallback smart chips shown when dynamicSuggestions is empty
        composeRule.onNodeWithTag("quick_start_pills_row").assertIsDisplayed()
        composeRule.onNodeWithText("🤖 Build an Android app").assertIsDisplayed()

        // Update with dynamic AI-generated follow-up chips
        composeRule.runOnIdle {
            dynamicSuggestions = listOf("Add biometric lock", "Add CSV export", "Add statistics chart")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dynamic_suggestion_chip_0").assertIsDisplayed()
        composeRule.onNodeWithText("Add biometric lock").assertIsDisplayed()
        composeRule.onNodeWithText("Add CSV export").assertIsDisplayed()
        composeRule.onNodeWithText("🤖 Build an Android app").assertDoesNotExist()

        // Tapping a dynamic chip populates the prompt input
        composeRule.onNodeWithTag("dynamic_suggestion_chip_0").performClick()
        composeRule.onNodeWithTag("app_prompt_input").assertTextEquals("Add biometric lock")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp")
    fun workspaceBuildApkTab_displaysSaveToDownloadsAndShareButtonsForApkAndZip() {
        var saveOfflineApkCalled = false
        var saveZipCalled = false
        var getShareUriCalled = false

        val project = com.example.model.StudioProject(
            id = "p-download-share",
            appName = "Pocket Vault",
            packageName = "com.example.pocketvault",
            prompt = "Encrypted notes",
            files = listOf(
                com.example.model.GeneratedFile("app/build.gradle.kts", "android { compileSdk = 36 }")
            )
        )

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                com.example.ui.screens.WorkspaceBuildApkTab(
                    project = project,
                    settings = com.example.model.StudioSettings(),
                    gitState = com.example.model.GitRepoState(),
                    hasGitHubPat = false,
                    isGitBusy = false,
                    isCloudBuildBusy = false,
                    cloudBuildProgress = null,
                    offlineSigningStatus = com.example.model.OfflineSigningStatus(
                        hasKeystore = true,
                        signedApkPath = "/tmp/pocket-vault-offline-signed.apk",
                        signedApkSizeBytes = 256 * 1024L,
                        isSignatureVerified = true,
                        v1Signed = true,
                        v2Signed = true,
                        v3Signed = true
                    ),
                    onTriggerCloudBuild = {},
                    onRefreshCloudBuildStatus = {},
                    onGetOfflineSignedApkUri = {
                        getShareUriCalled = true
                        null
                    },
                    onSaveOfflineSignedApkToDownloads = {
                        saveOfflineApkCalled = true
                        com.example.data.storage.PublicDownloadResult.Saved(
                            "Downloads/pocket-vault-offline-signed.apk",
                            android.net.Uri.parse("content://downloads/1")
                        )
                    },
                    onPackageZip = { null },
                    onSaveProjectZipToDownloads = {
                        saveZipCalled = true
                        com.example.data.storage.PublicDownloadResult.Saved(
                            "Downloads/pocket-vault.zip",
                            android.net.Uri.parse("content://downloads/2")
                        )
                    },
                    onExportBuildLogs = { null },
                    onInitGit = {},
                    onRefreshGit = {},
                    onStageFile = {},
                    onUnstageFile = {},
                    onStageAll = {},
                    onCommitGit = {},
                    onConfigureRemote = {},
                    onPushGit = {},
                    onPullGit = {},
                    onOpenConsole = {},
                    onOpenSettings = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("workspace_build_apk_tab")
        list.performScrollToNode(hasTestTag("save_offline_apk_downloads_button"))
        composeRule.onNodeWithTag("save_offline_apk_downloads_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("share_offline_apk_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(saveOfflineApkCalled)
        assertTrue(getShareUriCalled)

        list.performScrollToNode(hasTestTag("save_zip_downloads_button"))
        composeRule.onNodeWithTag("save_zip_downloads_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("export_zip_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.waitForIdle()

        assertTrue(saveZipCalled)
    }

    @Test
    fun projectStorageRepository_exportsZipAndCopiesFileToDestinationUri() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = com.example.data.storage.ProjectStorageRepository(context)
        val project = com.example.model.StudioProject(
            id = "zip-test-real",
            appName = "My Signed App",
            packageName = "com.studio.signedapp",
            prompt = "Test zip",
            files = listOf(
                com.example.model.GeneratedFile(
                    path = "app/src/main/java/com/studio/signedapp/MainActivity.kt",
                    content = "package com.studio.signedapp\nclass MainActivity"
                ),
                com.example.model.GeneratedFile(
                    path = "keystore.properties",
                    content = "keyAlias=release"
                )
            )
        )

        val zipFile = repo.exportProjectToZipFile(project)
        assertTrue(zipFile.exists())
        assertTrue(zipFile.length() > 0L)
        assertTrue(zipFile.name.startsWith("my-signed-app-"))
        assertTrue(zipFile.name.endsWith(".zip"))

        val entryNames = mutableListOf<String>()
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryNames += entry.name
                zis.closeEntry()
            }
        }
        assertTrue(entryNames.any { it.endsWith("MainActivity.kt") })
        assertTrue(entryNames.any { it.endsWith("keystore.properties") })

        val destFile = java.io.File(context.cacheDir, "copied-signed-app.zip")
        val copyResult = repo.copyFileToUri(zipFile, android.net.Uri.fromFile(destFile))
        assertTrue(copyResult.isSuccess)
        assertTrue(destFile.exists())
        assertEquals(zipFile.length(), destFile.length())
    }

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h915dp")
    fun workspaceBuildApkTab_displaysSaveAndShareZipInsideBuilderCardWhenPkcs12ReadyWithoutApk() {
        var saveZipCalled = false
        var shareZipCalled = false

        val project = com.example.model.StudioProject(
            id = "p-pkcs12-only",
            appName = "HabitTracker",
            packageName = "com.studio.app",
            prompt = "Habit tracker",
            files = listOf(
                com.example.model.GeneratedFile("app/build.gradle.kts", "android { compileSdk = 36 }")
            )
        )

        composeRule.setContent {
            AiAppStudioTheme(darkTheme = true) {
                com.example.ui.screens.WorkspaceBuildApkTab(
                    project = project,
                    settings = com.example.model.StudioSettings(),
                    gitState = com.example.model.GitRepoState(),
                    hasGitHubPat = false,
                    isGitBusy = false,
                    isCloudBuildBusy = false,
                    cloudBuildProgress = null,
                    offlineSigningStatus = com.example.model.OfflineSigningStatus(
                        hasKeystore = true,
                        signedApkPath = null,
                        isSignatureVerified = false
                    ),
                    onTriggerCloudBuild = {},
                    onRefreshCloudBuildStatus = {},
                    onPackageZip = {
                        shareZipCalled = true
                        null
                    },
                    onSaveProjectZipToDownloads = {
                        saveZipCalled = true
                        com.example.data.storage.PublicDownloadResult.Saved(
                            "Downloads/habittracker.zip",
                            android.net.Uri.parse("content://downloads/3")
                        )
                    },
                    onExportBuildLogs = { null },
                    onInitGit = {},
                    onRefreshGit = {},
                    onStageFile = {},
                    onUnstageFile = {},
                    onStageAll = {},
                    onCommitGit = {},
                    onConfigureRemote = {},
                    onPushGit = {},
                    onPullGit = {},
                    onOpenConsole = {},
                    onOpenSettings = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("workspace_build_apk_tab")
        list.performScrollToNode(hasTestTag("offline_card_save_zip_button"))
        composeRule.onNodeWithTag("offline_card_save_zip_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("offline_card_share_zip_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertTrue(saveZipCalled)
        assertTrue(shareZipCalled)
    }
}
