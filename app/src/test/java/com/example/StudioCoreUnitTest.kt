package com.example

import com.example.data.generator.JsonProjectExtractor
import com.example.data.git.GitRepositoryManager
import com.example.preview.ComposePreviewEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StudioCoreUnitTest {

    @Test
    fun jsonExtractor_stripsMarkdownFencesAndGeneratesMissingGradleFiles() {
        val raw = """
            Here is the generated app:
            ```json
            {
              "appName": "Habit Pulse",
              "packageName": "com.generated.habitpulse",
              "files": [
                {
                  "path": "app/src/main/java/com/generated/habitpulse/MainActivity.kt",
                  "content": "package com.generated.habitpulse\nclass MainActivity"
                }
              ]
            }
            ```
        """.trimIndent()

        val payload = JsonProjectExtractor.extract(raw)
        assertEquals("Habit Pulse", payload.appName)
        assertEquals("com.generated.habitpulse", payload.packageName)
        assertTrue(payload.files.any { it.path == "app/build.gradle.kts" })
        assertTrue(payload.files.any { it.path == "app/src/main/AndroidManifest.xml" })
        val workflowFile = payload.files.firstOrNull {
            it.path == com.example.data.github.GitHubWorkflowInjector.WORKFLOW_FILE_PATH
        }
        assertNotNull(workflowFile)
        assertTrue(workflowFile!!.content.contains("gradle/actions/setup-gradle@v4"))
        assertTrue(workflowFile.content.contains("actions/cache@v4"))
        assertTrue(workflowFile.content.contains("--build-cache --parallel"))
        assertTrue(workflowFile.content.contains(com.example.data.github.GitHubWorkflowInjector.LATEST_GRADLE_VERSION))
    }

    @Test
    fun composePreviewEngine_parsesComposablesAndStateVars() {
        val sampleKt = """
            package com.generated.counter

            import androidx.compose.material3.*
            import androidx.compose.runtime.*

            @Composable
            fun CounterScreen() {
                var count by remember { mutableStateOf(3) }
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Counter") }) }
                ) { padding ->
                    Column {
                        Text(text = "Count: ${'$'}count")
                        Button(onClick = { count++ }) {
                            Text("Increment")
                        }
                    }
                }
            }
        """.trimIndent()

        val result = ComposePreviewEngine.parseProjectSources(
            files = listOf("app/src/main/java/com/generated/counter/MainActivity.kt" to sampleKt)
        )
        assertNotNull(result.selectedComposable)
        assertEquals("CounterScreen", result.selectedComposable?.name)
        assertEquals(1, result.selectedComposable?.stateVars?.size)
        assertEquals("count", result.selectedComposable?.stateVars?.first()?.name)
        assertEquals(3, result.selectedComposable?.stateVars?.first()?.initialValue)
        assertEquals("Scaffold", result.selectedComposable?.rootNodes?.firstOrNull()?.name)
    }

    @Test
    fun gitRepositoryManager_initStageCommitWorksOnWorkspace() = runBlocking {
        val tempDir = Files.createTempDirectory("studio_git_test").toFile()
        try {
            File(tempDir, "MainActivity.kt").writeText("class MainActivity")
            val gitManager = GitRepositoryManager()

            val initialized = gitManager.initRepo(tempDir)
            assertTrue(initialized.isInitialized)
            assertTrue(initialized.changes.any { it.path == "MainActivity.kt" && !it.isStaged })

            val staged = gitManager.stageAll(tempDir)
            assertTrue(staged.changes.all { it.isStaged })

            val committed = gitManager.commit(tempDir, "Initial commit", "Tester")
            assertTrue(committed.changes.isEmpty())
            assertEquals(1, committed.commits.size)
            assertEquals("Initial commit", committed.commits.first().message)
            assertFalse(committed.commits.first().shortSha.isBlank())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun themeSeedGenerator_generatesAndParsesThemeKtColorSchemes() {
        val project = com.example.model.StudioProject(
            id = "p1",
            appName = "Fitness Buddy",
            packageName = "com.generated.fitness",
            prompt = "Fitness tracker",
            files = listOf(
                com.example.model.GeneratedFile(
                    path = "app/src/main/java/com/generated/fitness/ui/theme/Theme.kt",
                    content = "package com.generated.fitness.ui.theme\n@Composable fun FitnessTheme(content: @Composable () -> Unit) {}"
                )
            )
        )
        val seed = androidx.compose.ui.graphics.Color(0xFF059669)
        val (path, code) = com.example.preview.Material3SeedPaletteGenerator.buildThemeKtContent(project, seed)
        assertEquals("app/src/main/java/com/generated/fitness/ui/theme/Theme.kt", path)
        assertTrue(code.contains("private val LightColorScheme = lightColorScheme("))
        assertTrue(code.contains("private val DarkColorScheme = darkColorScheme("))
        assertTrue(code.contains("fun FitnessTheme("))

        val parsed = com.example.preview.Material3SeedPaletteGenerator.extractCustomSchemesFromFiles(
            listOf(path to code)
        )
        assertNotNull(parsed)
    }

    @Test
    fun debouncedAutoSaver_coalescesRapidEditsAndPersistsLatestState() = runBlocking {
        var saveCallCount = 0
        var lastPersistedContent = ""
        val saver = com.example.data.storage.DebouncedProjectAutoSaver(
            scope = this,
            debounceMs = 80L,
            persistProject = { proj ->
                saveCallCount++
                lastPersistedContent = proj.files.first().content
                proj
            }
        )

        val baseProject = com.example.model.StudioProject(
            id = "auto-save-test",
            appName = "Notes",
            packageName = "com.generated.notes",
            prompt = "Notes app",
            files = listOf(com.example.model.GeneratedFile("MainActivity.kt", "v1"))
        )

        saver.scheduleSave(baseProject.copy(files = listOf(com.example.model.GeneratedFile("MainActivity.kt", "v1"))))
        saver.scheduleSave(baseProject.copy(files = listOf(com.example.model.GeneratedFile("MainActivity.kt", "v2"))))
        saver.scheduleSave(baseProject.copy(files = listOf(com.example.model.GeneratedFile("MainActivity.kt", "v3-final"))))
        assertEquals(com.example.model.AutoSaveStatus.PENDING, saver.status.value)

        kotlinx.coroutines.delay(180L)
        assertEquals(1, saveCallCount)
        assertEquals("v3-final", lastPersistedContent)
        assertEquals(com.example.model.AutoSaveStatus.SAVED, saver.status.value)
    }

    @Test
    fun projectStaticAnalyzer_detectsUnclosedBracesAndMissingImports() {
        val brokenKt = com.example.model.GeneratedFile(
            path = "app/src/main/java/com/generated/notes/MainActivity.kt",
            content = """
                package com.generated.notes

                @Composable
                fun BrokenScreen() {
                    Column {
                        Text("Hello")
                    // missing closing brace for BrokenScreen
                }
            """.trimIndent()
        )
        val diagnostics = com.example.diagnostics.ProjectStaticAnalyzer.analyzeSingleFile(brokenKt)
        assertTrue(diagnostics.any { it.level == com.example.model.ConsoleLogLevel.ERROR && it.message.contains("Unclosed brace") })
        assertTrue(diagnostics.any { it.level == com.example.model.ConsoleLogLevel.WARN && it.message.contains("Unresolved reference") })
    }

    @Test
    fun aiCodeRefactorExtractor_parsesSuggestionsAndRefactoredCode() {
        val rawJson = """
            ```json
            {
              "summary": "Hoisted state and added rememberSaveable for configuration survival.",
              "suggestions": [
                {
                  "title": "Use rememberSaveable for counter state",
                  "category": "PERFORMANCE",
                  "impact": "HIGH",
                  "explanation": "Preserves state across configuration changes and avoids recomposition overhead.",
                  "beforeSnippet": "var count = 0",
                  "afterSnippet": "var count by rememberSaveable { mutableIntStateOf(0) }"
                }
              ],
              "refactoredCode": "package com.generated.app\n@Composable fun CleanScreen() {}"
            }
            ```
        """.trimIndent()

        val payload = com.example.data.refactor.AiCodeRefactorRepository.extractRefactorPayload(
            rawResponse = rawJson,
            fallbackOriginalCode = "fallback"
        )
        assertEquals(1, payload.suggestions.size)
        assertEquals("Use rememberSaveable for counter state", payload.suggestions.first().title)
        assertEquals("HIGH", payload.suggestions.first().impact)
        assertTrue(payload.refactoredCode.contains("fun CleanScreen()"))
    }

    @Test
    fun gradleDependencyManager_togglesCommentedDependenciesInBuildGradle() {
        val sampleGradle = """
            dependencies {
                implementation(libs.androidx.core.ktx)
                // implementation(libs.androidx.room.runtime)
                // implementation(libs.androidx.room.ktx)
                // "ksp"(libs.androidx.room.compiler)
                // implementation(libs.androidx.camera.core)
                // implementation(libs.androidx.camera.camera2)
            }
        """.trimIndent()

        val initialState = com.example.data.gradle.GradleDependencyManager.inspectGradleContent(
            targetPath = "app/build.gradle.kts",
            gradleContent = sampleGradle
        )
        val roomGroup = initialState.groups.first { it.group.id == "room" }
        val cameraGroup = initialState.groups.first { it.group.id == "camerax" }
        assertFalse(roomGroup.isEnabled)
        assertFalse(cameraGroup.isEnabled)

        val enabledCustom = initialState.customDependencies.filter { it.isEnabled }.map { it.rawDeclaration }.toSet()
        val updatedGradle = com.example.data.gradle.GradleDependencyManager.applyDependencyChanges(
            gradleContent = sampleGradle,
            enabledGroupIds = setOf("room", "firebase_firestore"),
            enabledCustomDeclarations = enabledCustom
        )

        assertTrue(updatedGradle.contains("\n    implementation(libs.androidx.room.runtime)"))
        assertTrue(updatedGradle.contains("\n    \"ksp\"(libs.androidx.room.compiler)"))
        assertTrue(updatedGradle.contains("// implementation(libs.androidx.camera.core)"))
        assertTrue(updatedGradle.contains("implementation(platform(libs.firebase.bom))"))
        assertTrue(updatedGradle.contains("implementation(libs.firebase.firestore)"))
    }

    @Test
    fun roomCodeGenerator_buildsEntityDaoDatabaseAndRepository() {
        val columns = listOf(
            com.example.data.local.SqlColumnSpec("title", "title", "TEXT", false),
            com.example.data.local.SqlColumnSpec("amount", "amount", "REAL", false),
            com.example.data.local.SqlColumnSpec("is_paid", "isPaid", "BOOLEAN", false)
        )
        val (filePath, content) = com.example.data.local.RoomCodeGenerator.generateRoomDatabaseFile(
            packageName = "com.example.finapp",
            entityNameInput = "InvoiceRecord",
            tableNameInput = "invoices",
            columns = columns
        )

        assertEquals(
            "app/src/main/java/com/example/finapp/data/local/InvoiceRecordSqlDatabase.kt",
            filePath
        )
        assertTrue(content.contains("@Entity(tableName = \"invoices\")"))
        assertTrue(content.contains("data class InvoiceRecord("))
        assertTrue(content.contains("interface InvoiceRecordDao"))
        assertTrue(content.contains("abstract class InvoiceRecordDatabase : RoomDatabase()"))
        assertTrue(content.contains("class InvoiceRecordRepository(private val dao: InvoiceRecordDao)"))
    }

    @Test
    fun jsonProjectExtractor_extractsKotlinMarkdownFallback() {
        val rawMarkdown = """
            Here is your offline generated Compose app:
            ```kotlin
            package com.local.counter

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun CounterScreen() {
                Text("Hello Offline Local AI")
            }
            ```
        """.trimIndent()

        val payload = JsonProjectExtractor.extract(rawMarkdown)
        assertEquals("com.local.counter", payload.packageName)
        assertTrue(payload.files.any { it.path == "app/src/main/java/com/local/counter/MainActivity.kt" })
        assertTrue(payload.files.any { it.path == "app/build.gradle.kts" })
    }

    @Test
    fun composePreviewEngine_extractsNavigationTargets() {
        val knownScreens = listOf("HomeScreen", "DetailScreen", "SettingsScreen")
        assertEquals(
            "DetailScreen",
            ComposePreviewEngine.extractNavigationTarget("navController.navigate(\"detail\")", knownScreens)
        )
        assertEquals(
            "SettingsScreen",
            ComposePreviewEngine.extractNavigationTarget("onNavigateToSettings()", knownScreens)
        )
        assertEquals(
            "__BACK__",
            ComposePreviewEngine.extractNavigationTarget("navController.popBackStack()", knownScreens)
        )
    }

    @Test
    fun deviceHardwareProfiler_recommendsMatchingTierAndWarnsOnHeavyModels() {
        val lowRamProfile = com.example.data.generator.DeviceHardwareProfiler.buildProfile(
            totalRamGb = 4.0,
            availableRamGb = 1.9,
            cpuAbi = "arm64-v8a"
        )
        assertEquals(
            com.example.data.generator.LocalModelTier.TIER_1_LITE,
            lowRamProfile.recommendedTier
        )

        val midRamProfile = com.example.data.generator.DeviceHardwareProfiler.buildProfile(
            totalRamGb = 8.0,
            availableRamGb = 4.2,
            cpuAbi = "arm64-v8a"
        )
        assertEquals(
            com.example.data.generator.LocalModelTier.TIER_2_BALANCED,
            midRamProfile.recommendedTier
        )
        assertTrue(midRamProfile.formatSummaryPill().contains("8.0 GB RAM (4.2 GB Free)"))
        assertTrue(midRamProfile.formatSummaryPill().contains("Medium Tier"))

        val highRamProfile = com.example.data.generator.DeviceHardwareProfiler.buildProfile(
            totalRamGb = 12.0,
            availableRamGb = 6.5,
            cpuAbi = "arm64-v8a"
        )
        assertEquals(
            com.example.data.generator.LocalModelTier.TIER_3_PRO,
            highRamProfile.recommendedTier
        )

        val tier3Preset = com.example.data.generator.LocalModelCatalog.presetForTier(
            com.example.data.generator.LocalModelTier.TIER_3_PRO
        )
        val tier1Preset = com.example.data.generator.LocalModelCatalog.presetForTier(
            com.example.data.generator.LocalModelTier.TIER_1_LITE
        )

        val oomWarning = com.example.data.generator.DeviceHardwareProfiler.getOomWarningForSelection(
            profile = lowRamProfile,
            preset = tier3Preset
        )
        assertNotNull(oomWarning)
        assertTrue(oomWarning!!.contains("Out-Of-Memory (OOM)"))
        assertEquals(
            null,
            com.example.data.generator.DeviceHardwareProfiler.getOomWarningForSelection(
                profile = lowRamProfile,
                preset = tier1Preset
            )
        )
    }

    @Test
    fun workspaceFileCategorizer_groupsKotlinXmlAssetsAndFilters() {
        val sampleFiles = listOf(
            com.example.model.GeneratedFile("app/src/main/java/com/app/MainActivity.kt", ""),
            com.example.model.GeneratedFile("app/src/main/res/layout/activity_main.xml", ""),
            com.example.model.GeneratedFile("app/src/main/assets/sample_data.json", ""),
            com.example.model.GeneratedFile("app/src/main/res/drawable/hero_banner.png", ""),
            com.example.model.GeneratedFile("app/build.gradle.kts", "")
        )

        val groups = com.example.ui.components.WorkspaceFileCategorizer.groupFiles(sampleFiles)
        assertEquals(4, groups.size)
        assertTrue(
            groups.first { it.category == com.example.ui.components.WorkspaceFileCategory.KOTLIN }
                .files.any { it.path.endsWith("MainActivity.kt") }
        )
        assertTrue(
            groups.first { it.category == com.example.ui.components.WorkspaceFileCategory.XML_LAYOUTS }
                .files.any { it.path.endsWith("activity_main.xml") }
        )
        assertEquals(
            2,
            groups.first { it.category == com.example.ui.components.WorkspaceFileCategory.ASSETS }.files.size
        )
        assertTrue(
            groups.first { it.category == com.example.ui.components.WorkspaceFileCategory.CONFIG }
                .files.any { it.path.endsWith("build.gradle.kts") }
        )

        val xmlFiltered = com.example.ui.components.WorkspaceFileCategorizer.groupFiles(
            files = sampleFiles,
            searchQuery = "activity_main"
        )
        assertEquals(1, xmlFiltered.size)
        assertEquals(com.example.ui.components.WorkspaceFileCategory.XML_LAYOUTS, xmlFiltered.first().category)
    }

    @Test
    fun localModelCatalog_uses7PublicUngatedReposAndVerifierSelectsBestTaskVariant() {
        val fullCatalog = com.example.data.generator.LocalModelCatalog.presets
        assertEquals(10, fullCatalog.size)
        val catalog = fullCatalog.filter { !it.isGated }
        assertEquals(7, catalog.size)

        val expectedPairs = listOf(
            "SmolLM2 360M Instruct (Ultra-Lite)" to "litert-community/SmolLM2-360M-Instruct",
            "SmolLM2 1.7B Instruct (Lite)" to "litert-community/SmolLM2-1.7B-Instruct",
            "Qwen 2.5 0.5B Instruct (Micro)" to "litert-community/Qwen2.5-0.5B-Instruct",
            "Qwen 2.5 1.5B Instruct (Q8)" to "litert-community/Qwen2.5-1.5B-Instruct",
            "DeepSeek R1 Distill Qwen 1.5B" to "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            "Qwen 2.5 3B Instruct (Q8)" to "litert-community/Qwen2.5-3B-Instruct",
            "Phi-4 Mini Instruct (Q8)" to "litert-community/Phi-4-mini-instruct"
        )
        expectedPairs.forEachIndexed { idx, (expectedName, expectedRepo) ->
            assertEquals(expectedName, catalog[idx].displayName)
            assertEquals(expectedRepo, catalog[idx].repoId)
            assertTrue(catalog[idx].downloadUrl.startsWith("https://huggingface.co/$expectedRepo/resolve/main/"))
        }

        val resolvedTask = com.example.data.generator.HuggingFaceModelVerifier.selectBestModelFilename(
            listOf(
                "README.md",
                "tokenizer.model",
                "model_fp16.bin",
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
            )
        )
        assertEquals("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task", resolvedTask)

        val resolvedInt4 = com.example.data.generator.HuggingFaceModelVerifier.selectBestModelFilename(
            listOf("model_q8.bin", "model_int4.task")
        )
        assertEquals("model_int4.task", resolvedInt4)

        assertEquals(
            null,
            com.example.data.generator.HuggingFaceModelVerifier.selectBestModelFilename(
                listOf("README.md", "config.json")
            )
        )
        assertEquals(
            1623456789L,
            com.example.data.generator.HuggingFaceModelVerifier.parseContentRangeTotalBytes("bytes 0-0/1623456789")
        )
        assertEquals(
            "380 MB",
            com.example.data.generator.HuggingFaceModelVerifier.formatExactSize(380L * 1024L * 1024L)
        )

        val publicRequest = com.example.data.storage.ProjectStorageRepository.buildModelDownloadRequest(
            url = catalog.first().downloadUrl,
            huggingFaceToken = ""
        )
        assertEquals(null, publicRequest.header("Authorization"))

        val authedRequest = com.example.data.storage.ProjectStorageRepository.buildModelDownloadRequest(
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            huggingFaceToken = "  hf_test_secret_token  "
        )
        assertEquals("Bearer hf_test_secret_token", authedRequest.header("Authorization"))
    }

    @Test
    fun starterTemplateCatalog_buildsValidProjectsWithZeroCompilerErrors() {
        val templates = com.example.data.templates.StarterTemplateCatalog.templates
        assertTrue(templates.any { it.title == "Bottom Navigation App" })
        assertTrue(templates.any { it.title == "Master-Detail List" })
        assertTrue(templates.any { it.title == "Login Form" })

        templates.forEach { template ->
            val payload = com.example.data.templates.StarterTemplateCatalog.buildProjectPayload(
                template = template,
                customAppName = "Demo ${template.defaultAppName}",
                customPackageName = "com.demo.${template.id.replace("_", "")}"
            )
            assertEquals("Demo ${template.defaultAppName}", payload.appName)
            assertTrue(payload.files.any { it.path.endsWith("MainActivity.kt") })
            assertTrue(payload.files.any { it.path.endsWith("Theme.kt") })
            assertTrue(payload.files.any { it.path == "app/build.gradle.kts" })
            assertTrue(payload.files.any { it.path == "app/src/main/AndroidManifest.xml" })

            val studioProject = com.example.model.StudioProject(
                id = "test-${template.id}",
                appName = payload.appName,
                packageName = payload.packageName,
                prompt = template.subtitle,
                files = payload.files
            )
            val diagnostics = com.example.diagnostics.ProjectStaticAnalyzer.analyzeProject(studioProject)
            val errors = diagnostics.filter { it.level == com.example.model.ConsoleLogLevel.ERROR }
            val warnings = diagnostics.filter { it.level == com.example.model.ConsoleLogLevel.WARN }
            assertEquals(
                "Unexpected compiler errors in ${template.id}: ${errors.joinToString { it.message }}",
                0,
                errors.size
            )
            assertEquals(
                "Unexpected compiler warnings in ${template.id}: ${warnings.joinToString { it.message }}",
                0,
                warnings.size
            )

            val preview = ComposePreviewEngine.parseProjectSources(
                files = payload.files.map { it.path to it.content }
            )
            assertNotNull("Expected previewable composable in ${template.id}", preview.selectedComposable)
        }
    }

    @Test
    fun projectResAssetManager_discoversParsesValidatesAndRenamesResAssets() {
        val samplePngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val encodedPng = com.example.ui.assets.ProjectResAssetManager.encodeBinaryContent(samplePngBytes)
        val decodedPng = com.example.ui.assets.ProjectResAssetManager.decodeBinaryContent(encodedPng)
        assertNotNull(decodedPng)
        assertEquals(samplePngBytes.toList(), decodedPng!!.toList())

        val starVectorXml = com.example.ui.assets.ProjectResAssetManager.builtInVectorPresets
            .first { it.id == "star" }.xmlContent
        val shapeXml = com.example.ui.assets.ProjectResAssetManager.builtInVectorPresets
            .first { it.id == "gradient_card" }.xmlContent

        val sampleFiles = listOf(
            com.example.model.GeneratedFile("app/src/main/res/drawable/hero_banner.png", encodedPng),
            com.example.model.GeneratedFile("app/src/main/res/drawable/ic_star.xml", starVectorXml),
            com.example.model.GeneratedFile("app/src/main/res/drawable/bg_hero_card.xml", shapeXml),
            com.example.model.GeneratedFile("app/src/main/res/font/inter_regular.ttf", encodedPng),
            com.example.model.GeneratedFile("app/src/main/res/values/strings.xml", "<resources><string name=\"app_name\">Demo</string></resources>"),
            com.example.model.GeneratedFile(
                "app/src/main/java/com/demo/MainActivity.kt",
                "package com.demo\nval x = R.drawable.ic_star\nval y = R.font.inter_regular"
            ),
            com.example.model.GeneratedFile(
                "app/src/main/res/layout/activity_main.xml",
                "<ImageView android:src=\"@drawable/ic_star\" />"
            )
        )

        val discovered = com.example.ui.assets.ProjectResAssetManager.discoverResAssets(sampleFiles)
        assertEquals(4, discovered.size) // strings.xml & activity_main.xml excluded
        assertEquals(1, discovered.count { it.kind == com.example.ui.assets.ResAssetKind.ICON })
        assertEquals(2, discovered.count { it.kind == com.example.ui.assets.ResAssetKind.IMAGE })
        assertEquals(1, discovered.count { it.kind == com.example.ui.assets.ResAssetKind.FONT })

        val parsedVector = com.example.ui.assets.ProjectResAssetManager.parseVectorDrawableXml(starVectorXml)
        assertNotNull(parsedVector)
        assertEquals(24f, parsedVector!!.viewportWidth)
        assertEquals(1, parsedVector.paths.size)

        val parsedShape = com.example.ui.assets.ProjectResAssetManager.parseShapeDrawableXml(shapeXml)
        assertNotNull(parsedShape)
        assertEquals("rectangle", parsedShape!!.shapeType)
        assertEquals(20f, parsedShape.cornerRadiusDp)

        assertNotNull(com.example.ui.assets.ProjectResAssetManager.validateResourceBaseName("Bad-Name"))
        assertNotNull(com.example.ui.assets.ProjectResAssetManager.validateResourceBaseName("1icon"))
        assertEquals(null, com.example.ui.assets.ProjectResAssetManager.validateResourceBaseName("ic_badge_2"))
        assertEquals("hero_banner_01", com.example.ui.assets.ProjectResAssetManager.sanitizeToResourceBaseName("Hero Banner 01.png"))

        val renameResult = com.example.ui.assets.ProjectResAssetManager.renameAssetAndReferences(
            files = sampleFiles,
            oldPath = "app/src/main/res/drawable/ic_star.xml",
            newBaseName = "ic_featured_star",
            updateReferences = true
        ).getOrThrow()

        assertEquals("app/src/main/res/drawable/ic_featured_star.xml", renameResult.newPath)
        assertEquals(2, renameResult.referencesUpdatedCount)
        assertTrue(renameResult.updatedFiles.any { it.path == "app/src/main/res/drawable/ic_featured_star.xml" })
        assertFalse(renameResult.updatedFiles.any { it.path == "app/src/main/res/drawable/ic_star.xml" })
        val updatedKt = renameResult.updatedFiles.first { it.path.endsWith("MainActivity.kt") }.content
        assertTrue(updatedKt.contains("R.drawable.ic_featured_star"))
        val updatedLayout = renameResult.updatedFiles.first { it.path.endsWith("activity_main.xml") }.content
        assertTrue(updatedLayout.contains("@drawable/ic_featured_star"))
    }

    @Test
    fun bundledModelManager_defaultsAndConstantsVerifyOfflineReadiness() {
        val defaultSettings = com.example.model.StudioSettings()
        assertEquals(com.example.model.AiEngineMode.ON_DEVICE_MEDIAPIPE, defaultSettings.aiEngineMode)
        assertEquals("Local AI (Built-in Offline)", com.example.model.AiEngineMode.ON_DEVICE_MEDIAPIPE.label)
        assertEquals("models/built_in_coder.task", com.example.data.generator.BundledModelManager.BUNDLED_ASSET_PATH)
        assertEquals("built_in_coder.task", com.example.data.generator.BundledModelManager.BUNDLED_MODEL_FILE_NAME)
        assertEquals(2048, com.example.data.generator.BundledModelManager.DEFAULT_MAX_TOKENS)
        assertEquals(0.15f, com.example.data.generator.BundledModelManager.DEFAULT_TEMPERATURE)

        // If a physical bundled asset is present, verify > 100 MB; otherwise multi-mirror fallback handles download cleanly
        val candidatePaths = listOf(
            File("src/main/assets/models/built_in_coder.task"),
            File("app/src/main/assets/models/built_in_coder.task")
        )
        val bundledFile = candidatePaths.firstOrNull { it.exists() }
        if (bundledFile != null) {
            assertTrue(
                "Bundled model must be > 100 MB (actual: ${bundledFile.length()} bytes)",
                bundledFile.length() > 100L * 1024L * 1024L
            )
        } else {
            assertEquals(3, com.example.data.generator.BundledModelManager.BUILT_IN_FALLBACK_MIRRORS.size)
            assertTrue(
                com.example.data.generator.BundledModelManager.MIN_FALLBACK_DOWNLOAD_BYTES >= 50L * 1024L * 1024L
            )
        }
    }

    @Test
    fun generationRepositoryConfig_togglesBetweenLocalOfflineAndGeminiCloudApi() {
        val offlineSettings = com.example.model.StudioSettings(
            aiEngineMode = com.example.model.AiEngineMode.ON_DEVICE_MEDIAPIPE,
            localModelFilePath = "/data/user/0/com.example/files/models/built_in_coder.task",
            localModelDisplayName = "Qwen 2.5 0.5B Instruct (Built-in Offline)"
        )
        val offlineConfig = com.example.data.generator.GenerationRepositoryConfig.fromSettings(offlineSettings)
        assertEquals(
            com.example.data.generator.GenerationEngineTarget.LOCAL_AI_OFFLINE,
            offlineConfig.target
        )
        assertEquals("Local AI (Offline)", offlineConfig.target.displayTitle)
        assertEquals("OnDeviceMediaPipeGenerator", offlineConfig.activeDataSourceName)
        assertTrue(offlineConfig.isConfiguredReady)

        val geminiMissingKeyConfig = com.example.data.generator.GenerationRepositoryConfig.fromSettings(
            offlineSettings.copy(aiEngineMode = com.example.model.AiEngineMode.CLOUD_OPENROUTER)
        )
        assertEquals(
            com.example.data.generator.GenerationEngineTarget.GEMINI_CLOUD_API,
            geminiMissingKeyConfig.target
        )
        assertEquals("Gemini Cloud API", geminiMissingKeyConfig.target.displayTitle)
        assertFalse(geminiMissingKeyConfig.isConfiguredReady)

        val geminiReadyConfig = com.example.data.generator.GenerationRepositoryConfig.fromSettings(
            offlineSettings.copy(
                aiEngineMode = com.example.model.AiEngineMode.CLOUD_OPENROUTER,
                geminiApiKey = "AIzaTestKey123456"
            )
        )
        assertEquals(
            com.example.data.generator.GenerationEngineTarget.GEMINI_CLOUD_API,
            geminiReadyConfig.target
        )
        assertTrue(geminiReadyConfig.isConfiguredReady)
        assertTrue(geminiReadyConfig.statusDetail.contains("Gemini Cloud API"))
    }

    @Test
    fun bundledModelManager_verifyModelFileIntegrity_detectsCorruptionAndValidFiles() {
        val tempDir = Files.createTempDirectory("bundled_integrity_test").toFile()
        try {
            val missingFile = File(tempDir, "missing.task")
            val missingResult = com.example.data.generator.BundledModelManager.verifyModelFileIntegrity(
                file = missingFile,
                expectedSizeBytes = 2048L,
                minValidBytes = 1024L
            )
            assertFalse(missingResult.isValid)

            val truncatedFile = File(tempDir, "truncated.task").apply {
                writeBytes(ByteArray(128) { 0x42 })
            }
            val truncatedResult = com.example.data.generator.BundledModelManager.verifyModelFileIntegrity(
                file = truncatedFile,
                expectedSizeBytes = 2048L,
                minValidBytes = 1024L
            )
            assertFalse(truncatedResult.isValid)

            val mismatchFile = File(tempDir, "mismatch.task").apply {
                writeBytes(ByteArray(1500) { 0x42 })
            }
            val mismatchResult = com.example.data.generator.BundledModelManager.verifyModelFileIntegrity(
                file = mismatchFile,
                expectedSizeBytes = 2048L,
                minValidBytes = 1024L
            )
            assertFalse(mismatchResult.isValid)

            val zeroCorruptedFile = File(tempDir, "zero_corrupted.task").apply {
                writeBytes(ByteArray(2048) { 0x00 })
            }
            val zeroResult = com.example.data.generator.BundledModelManager.verifyModelFileIntegrity(
                file = zeroCorruptedFile,
                expectedSizeBytes = 2048L,
                minValidBytes = 1024L
            )
            assertFalse(zeroResult.isValid)

            val validFile = File(tempDir, "valid.task").apply {
                writeBytes(ByteArray(2048) { 0x2A })
            }
            val validResult = com.example.data.generator.BundledModelManager.verifyModelFileIntegrity(
                file = validFile,
                expectedSizeBytes = 2048L,
                minValidBytes = 1024L
            )
            assertTrue(validResult.isValid)
            assertEquals(2048L, validResult.actualSizeBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun modelBenchmarkCalculator_summarizesPredefinedPromptTimings() {
        val prompts = com.example.data.generator.ModelBenchmarkDefaults.prompts
        assertEquals(3, prompts.size)
        assertTrue(prompts.all { it.prompt.isNotBlank() && it.label.isNotBlank() })

        val sampleResults = listOf(
            com.example.data.generator.BenchmarkPromptResult(
                id = prompts[0].id,
                label = prompts[0].label,
                prompt = prompts[0].prompt,
                durationMs = 300L,
                responsePreview = "Button(...)",
                charCount = 11,
                succeeded = true
            ),
            com.example.data.generator.BenchmarkPromptResult(
                id = prompts[1].id,
                label = prompts[1].label,
                prompt = prompts[1].prompt,
                durationMs = 600L,
                responsePreview = "var c by remember { mutableIntStateOf(0) }",
                charCount = 42,
                succeeded = true
            ),
            com.example.data.generator.BenchmarkPromptResult(
                id = prompts[2].id,
                label = prompts[2].label,
                prompt = prompts[2].prompt,
                durationMs = 450L,
                responsePreview = "Dispatchers.IO",
                charCount = 14,
                succeeded = true
            )
        )

        val summary = com.example.data.generator.ModelBenchmarkCalculator.summarize(
            modelDisplayName = com.example.data.generator.BundledModelManager.BUNDLED_DISPLAY_NAME,
            modelPath = "/files/models/built_in_coder.task",
            results = sampleResults
        )
        assertEquals(450L, summary.averageMs)
        assertEquals(300L, summary.minMs)
        assertEquals(600L, summary.maxMs)
        assertEquals(1350L, summary.totalMs)
        assertEquals("450 ms", com.example.data.generator.ModelBenchmarkCalculator.formatDurationLabel(450L))
        assertTrue(
            com.example.data.generator.ModelBenchmarkCalculator.formatDurationLabel(1350L)
                .contains("1.35 s")
        )
    }

    @Test
    fun gatedGemmaCatalog_andDefaultHuggingFaceToken_areConfiguredCorrectly() {
        val defaults = com.example.model.StudioSettings()
        assertEquals(
            "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",
            defaults.huggingFaceToken
        )

        val presets = com.example.data.generator.LocalModelCatalog.presets
        assertEquals(10, presets.size)
        val gemma3 = presets.firstOrNull { it.id == "gemma3_1b_it_int4" }
        val gemma2Int4 = presets.firstOrNull { it.id == "gemma2_2b_it_gpu_int4" }
        val gemma2Int8 = presets.firstOrNull { it.id == "gemma2_2b_it_gpu_int8" }

        assertNotNull(gemma3)
        assertNotNull(gemma2Int4)
        assertNotNull(gemma2Int8)
        assertEquals("litert-community/Gemma3-1B-IT", gemma3!!.repoId)
        assertEquals("Official Google • Fast Lite", gemma3.badge)
        assertEquals("litert-community/Gemma2-2B-IT", gemma2Int4!!.repoId)
        assertEquals("GPU Accelerated • Balanced", gemma2Int4.badge)
        assertEquals("litert-community/Gemma2-2B-IT", gemma2Int8!!.repoId)
        assertEquals("High Accuracy • 8GB+ RAM", gemma2Int8.badge)

        val pickedGemma3 = com.example.data.generator.HuggingFaceModelVerifier.selectBestModelFilename(
            siblings = listOf(
                "README.md",
                "gemma3-1b-it-int4-web.task",
                "gemma3-1b-it-int4.task",
                "Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task"
            ),
            preferredVariantHint = "int4"
        )
        assertEquals("gemma3-1b-it-int4.task", pickedGemma3)

        val requestWithToken = com.example.data.storage.ProjectStorageRepository.buildModelDownloadRequest(
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            huggingFaceToken = defaults.huggingFaceToken
        )
        assertEquals(
            "Bearer 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",
            requestWithToken.header("Authorization")
        )
        assertEquals(
            "Please ensure you have accepted the Gemma license agreement on Hugging Face for your account.",
            com.example.data.generator.HuggingFaceModelVerifier.GATED_LICENSE_ERROR_MESSAGE
        )
    }

    @Test
    fun bundledModelManager_multiMirrorFallback_retriesUntil200AndRejectsHtml() {
        val mirrors = com.example.data.generator.BundledModelManager.BUILT_IN_FALLBACK_MIRRORS
        assertEquals(3, mirrors.size)
        assertTrue(mirrors[0].url.startsWith("https://huggingface.co/"))
        assertTrue(mirrors[1].url.startsWith("https://hf-mirror.com/"))
        assertTrue(mirrors[2].url.startsWith("https://modelscope.cn/"))
        assertEquals(
            "Download Built-in AI (~380 MB)",
            com.example.data.generator.BundledModelManager.DOWNLOAD_BUTTON_LABEL
        )

        // Token is only sent to Hugging Face / HF-Mirror hosts, not ModelScope
        val hfReq = com.example.data.generator.BundledModelManager.buildMirrorDownloadRequest(
            mirror = mirrors[0],
            huggingFaceToken = "hf_test_token"
        )
        val modelScopeReq = com.example.data.generator.BundledModelManager.buildMirrorDownloadRequest(
            mirror = mirrors[2],
            huggingFaceToken = "hf_test_token"
        )
        assertEquals("Bearer hf_test_token", hfReq.header("Authorization"))
        assertEquals(null, modelScopeReq.header("Authorization"))

        val tempDir = Files.createTempDirectory("mirror_fallback_test").toFile()
        try {
            val destFile = File(tempDir, "models/built_in_coder.task")
            val payloadBytes = ByteArray(1024) { idx -> ((idx % 250) + 1).toByte() }
            val visitedUrls = mutableListOf<String>()

            val fakeClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val url = req.url.toString()
                    visitedUrls += url
                    when {
                        url.startsWith("https://huggingface.co/") -> {
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(404)
                                .message("Not Found")
                                .body("missing".toByteArray().let {
                                    okhttp3.ResponseBody.create(null, it)
                                })
                                .build()
                        }
                        url.startsWith("https://hf-mirror.com/") -> {
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "text/html; charset=utf-8")
                                .body("<html><body>Blocked</body></html>".toByteArray().let {
                                    okhttp3.ResponseBody.create(null, it)
                                })
                                .build()
                        }
                        else -> {
                            okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "application/octet-stream")
                                .body(payloadBytes.let {
                                    okhttp3.ResponseBody.create(null, it)
                                })
                                .build()
                        }
                    }
                }
                .build()

            val attempts = mutableListOf<Int>()
            val result = runBlocking {
                com.example.data.generator.BundledModelManager.downloadBuiltInModelToFileWithMirrorFallback(
                    targetFile = destFile,
                    huggingFaceToken = "hf_test_token",
                    mirrors = mirrors,
                    minValidBytes = 512L,
                    okHttpClient = fakeClient,
                    onMirrorAttempt = { mirrorIdx, _, _ -> attempts += mirrorIdx }
                )
            }

            assertTrue("Expected mirror fallback to succeed on Mirror 3", result.isSuccess)
            val success = result.getOrThrow()
            assertEquals(3, success.attemptedMirrors)
            assertEquals("modelscope", success.mirrorUsed.id)
            assertEquals(listOf(1, 2, 3), attempts)
            assertEquals(3, visitedUrls.size)
            assertTrue(destFile.exists())
            assertEquals(1024L, destFile.length())

            // Verify failure cleanup when all mirrors fail
            val failingDest = File(tempDir, "models/failed_coder.task")
            val failingClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    okhttp3.Response.Builder()
                        .request(req)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("unavailable".toByteArray().let {
                            okhttp3.ResponseBody.create(null, it)
                        })
                        .build()
                }
                .build()

            val failResult = runBlocking {
                com.example.data.generator.BundledModelManager.downloadBuiltInModelToFileWithMirrorFallback(
                    targetFile = failingDest,
                    mirrors = mirrors,
                    minValidBytes = 512L,
                    okHttpClient = failingClient
                )
            }
            assertTrue(failResult.isFailure)
            assertFalse(failingDest.exists())
            assertFalse(File(failingDest.parentFile, "${failingDest.name}.mirror.part").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCodeQuickActionEngineAutoFixAndMarkdownRemoval() {
        val brokenSnippet = """
            ```kotlin
            fun BrokenScreen() {
                val state = remember { mutableStateOf("hello") }
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(text = state.value)
                    Button(onClick = {}) {
                        Text("Tap")
                    }
            ```
        """.trimIndent()

        val (fixedCode, fixes) = com.example.diagnostics.CodeQuickActionEngine.autoFixKotlinCode(
            path = "app/src/main/java/com/example/ui/BrokenScreen.kt",
            code = brokenSnippet
        )

        assertFalse(fixedCode.contains("```"))
        assertTrue(fixedCode.contains("package com.example.ui"))
        assertTrue(fixedCode.contains("import androidx.compose.runtime.*"))
        assertTrue(fixedCode.contains("import androidx.compose.foundation.layout.*"))
        assertTrue(fixedCode.contains("import androidx.compose.material3.*"))
        assertTrue(fixedCode.contains("import androidx.compose.ui.Modifier"))
        assertTrue(fixes.isNotEmpty())
    }

    @Test
    fun testCodeQuickActionEngineExplainAndOptimize() {
        val snippet = """
            package com.example.ui

            import androidx.compose.foundation.layout.*
            import androidx.compose.foundation.lazy.LazyColumn
            import androidx.compose.foundation.lazy.items
            import androidx.compose.material3.*
            import androidx.compose.runtime.*

            @Composable
            fun MyFeedScreen() {
                val feedItems = remember { mutableStateOf(listOf("Item 1", "Item 2")) }
                LazyColumn {
                    items(feedItems.value) { item ->
                        Text(item)
                    }
                }
            }
        """.trimIndent()

        val explanation = com.example.diagnostics.CodeQuickActionEngine.explainCode(
            path = "app/src/main/java/com/example/ui/MyFeedScreen.kt",
            code = snippet
        )
        assertEquals("com.example.ui", explanation.packageName)
        assertTrue(explanation.composableScreens.contains("MyFeedScreen"))
        assertEquals(1, explanation.stateHolders.size)

        val optimization = com.example.diagnostics.CodeQuickActionEngine.analyzeOptimizations(
            path = "app/src/main/java/com/example/ui/MyFeedScreen.kt",
            code = snippet
        )
        assertTrue(optimization.findings.any { it.category == "Lazy List Efficiency" })
        assertTrue(optimization.canAutoOptimize)

        val (optimizedCode, appliedFixes) = com.example.diagnostics.CodeQuickActionEngine.applyOptimizations(
            path = "app/src/main/java/com/example/ui/MyFeedScreen.kt",
            code = snippet
        )
        assertTrue(optimizedCode.contains("key ="))
        assertTrue(appliedFixes.isNotEmpty())
    }

    @Test
    fun testCodeQuickActionEngineApkMetrics() {
        val project = com.example.model.StudioProject(
            id = "proj-metrics",
            appName = "MetricsApp",
            packageName = "com.example.metrics",
            prompt = "Create a metrics app",
            files = listOf(
                com.example.model.GeneratedFile(
                    path = "app/src/main/java/com/example/metrics/MainActivity.kt",
                    content = "@Composable fun MainScreen() { Text(\"Hi\") }"
                ),
                com.example.model.GeneratedFile(
                    path = "app/src/main/AndroidManifest.xml",
                    content = "<manifest><uses-permission android:name=\"android.permission.INTERNET\"/><application/></manifest>"
                )
            )
        )

        val metrics = com.example.diagnostics.CodeQuickActionEngine.analyzeApkMetrics(project)
        assertTrue(metrics.totalLinesOfCode > 0)
        assertEquals(1, metrics.kotlinFileCount)
        assertEquals(1, metrics.xmlFileCount)
        assertTrue(metrics.declaredPermissions.contains("INTERNET"))
        assertTrue(metrics.estimatedApkSizeMb > 3.0)
    }

    @Test
    fun apiCredentialValidator_detectsKeyFormatsAndCrossProviderMistakes() {
        val validGemini = com.example.data.preferences.ApiCredentialValidator.validateGeminiKeyFormat(
            "AIzaSyDemoKeyValue1234567890abcdefg"
        )
        assertTrue(validGemini.isValidShape)
        assertEquals(null, validGemini.warningMessage)
        assertTrue(validGemini.helperHint!!.contains("Valid Gemini key format"))

        val shortGemini = com.example.data.preferences.ApiCredentialValidator.validateGeminiKeyFormat("AIzaShort123")
        assertFalse(shortGemini.isValidShape)
        assertTrue(shortGemini.warningMessage!!.contains("Too short"))

        val spaceGemini = com.example.data.preferences.ApiCredentialValidator.validateGeminiKeyFormat(
            "AIzaSyDemoKeyValue 1234567890abcdefg"
        )
        assertFalse(spaceGemini.isValidShape)
        assertTrue(spaceGemini.warningMessage!!.contains("spaces"))

        val badCharGemini = com.example.data.preferences.ApiCredentialValidator.validateGeminiKeyFormat(
            "AIzaSyDemoKeyValue\$1234567890abcdefg"
        )
        assertFalse(badCharGemini.isValidShape)
        assertTrue(badCharGemini.warningMessage!!.contains("Invalid character"))

        val misplacedOpenRouter = com.example.data.preferences.ApiCredentialValidator.validateGeminiKeyFormat(
            "sk-or-v1-1234567890abcdef1234567890"
        )
        assertFalse(misplacedOpenRouter.isValidShape)
        assertTrue(misplacedOpenRouter.warningMessage!!.contains("OpenRouter"))

        val validOpenRouter = com.example.data.preferences.ApiCredentialValidator.validateOpenRouterKeyFormat(
            "sk-or-v1-1234567890abcdef1234567890"
        )
        assertTrue(validOpenRouter.isValidShape)
        assertEquals(null, validOpenRouter.warningMessage)
        assertTrue(validOpenRouter.helperHint!!.contains("Valid OpenRouter key format"))

        val shortOpenRouter = com.example.data.preferences.ApiCredentialValidator.validateOpenRouterKeyFormat(
            "sk-or-v1-short"
        )
        assertFalse(shortOpenRouter.isValidShape)
        assertTrue(shortOpenRouter.warningMessage!!.contains("Too short"))

        val badCharOpenRouter = com.example.data.preferences.ApiCredentialValidator.validateOpenRouterKeyFormat(
            "sk-or-v1-1234567890!abcdef12345678"
        )
        assertFalse(badCharOpenRouter.isValidShape)
        assertTrue(badCharOpenRouter.warningMessage!!.contains("Invalid character"))

        val misplacedGemini = com.example.data.preferences.ApiCredentialValidator.validateOpenRouterKeyFormat(
            "AIzaSyDemoKeyValue1234567890abcdefg"
        )
        assertFalse(misplacedGemini.isValidShape)
        assertTrue(misplacedGemini.warningMessage!!.contains("Gemini"))

        assertEquals(
            "AIzaSy••••••defg",
            com.example.data.preferences.SecureCredentialCipher.maskSecret("AIzaSyDemoKey123456defg")
        )
        assertEquals(
            "sk-or-v1-••••••7890",
            com.example.data.preferences.SecureCredentialCipher.maskSecret("sk-or-v1-abcdef1234567890")
        )
    }

    @Test
    fun apiCredentialValidator_verifySavedProviderHealth_handlesConfiguredAndErrorStates() = runBlocking {
        val emptySettings = com.example.model.StudioSettings(
            geminiApiKey = "",
            openRouterApiKey = ""
        )
        val (noneResolved, noneState) = com.example.data.preferences.ApiCredentialValidator.verifySavedProviderHealth(
            settings = emptySettings,
            buildConfigGeminiKey = ""
        )
        assertEquals(null, noneResolved)
        assertTrue(noneState is com.example.data.preferences.SavedApiKeyHealthState.NotConfigured)

        // Gemini lightweight health check (200 OK with models payload)
        val mockGeminiClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                assertTrue(req.url.toString().contains("generativelanguage.googleapis.com/v1beta/models"))
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        okhttp3.ResponseBody.create(
                            null,
                            """{"models":[{"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash"}]}"""
                        )
                    )
                    .build()
            }
            .build()

        val geminiSettings = com.example.model.StudioSettings(
            geminiApiKey = "AIzaSyTestSavedGeminiKey1234567890ab",
            openRouterModel = com.example.model.OpenRouterModels.GEMINI_25_PRO_FREE
        )
        val (gemResolved, gemHealth) = com.example.data.preferences.ApiCredentialValidator.verifySavedProviderHealth(
            settings = geminiSettings,
            buildConfigGeminiKey = "",
            httpClient = mockGeminiClient
        )
        assertEquals(com.example.data.preferences.ConfiguredCloudProvider.GEMINI, gemResolved?.provider)
        assertTrue(gemHealth is com.example.data.preferences.SavedApiKeyHealthState.Verified)
        val verifiedGem = gemHealth as com.example.data.preferences.SavedApiKeyHealthState.Verified
        assertEquals("Google Gemini", verifiedGem.providerLabel)
        assertTrue(verifiedGem.detail.contains("Gemini 2.5 Flash"))
        assertTrue(verifiedGem.latencyMs >= 1L)

        // OpenRouter lightweight health check (200 OK with auth/key payload)
        val mockOpenRouterClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                assertEquals("https://openrouter.ai/api/v1/auth/key", req.url.toString())
                assertTrue(req.header("Authorization")!!.startsWith("Bearer sk-or-v1-"))
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        okhttp3.ResponseBody.create(
                            null,
                            """{"data":{"label":"Studio Key","is_free_tier":true}}"""
                        )
                    )
                    .build()
            }
            .build()

        val openRouterSettings = com.example.model.StudioSettings(
            openRouterApiKey = "sk-or-v1-testsavedopenrouterkey1234567890",
            openRouterModel = com.example.model.OpenRouterModels.QWEN_CODER_FREE
        )
        val (orResolved, orHealth) = com.example.data.preferences.ApiCredentialValidator.verifySavedProviderHealth(
            settings = openRouterSettings,
            buildConfigGeminiKey = "",
            httpClient = mockOpenRouterClient
        )
        assertEquals(com.example.data.preferences.ConfiguredCloudProvider.OPENROUTER, orResolved?.provider)
        assertTrue(orHealth is com.example.data.preferences.SavedApiKeyHealthState.Verified)
        val verifiedOr = orHealth as com.example.data.preferences.SavedApiKeyHealthState.Verified
        assertEquals("OpenRouter", verifiedOr.providerLabel)
        assertTrue(verifiedOr.detail.contains("Studio Key (Free tier)"))

        // Unauthorized 401 -> SavedApiKeyHealthState.Error
        val failingClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(okhttp3.ResponseBody.create(null, """{"error":"invalid_key"}"""))
                    .build()
            }
            .build()
        val (_, failedHealth) = com.example.data.preferences.ApiCredentialValidator.verifySavedProviderHealth(
            settings = openRouterSettings,
            buildConfigGeminiKey = "",
            httpClient = failingClient
        )
        assertTrue(failedHealth is com.example.data.preferences.SavedApiKeyHealthState.Error)
        assertTrue((failedHealth as com.example.data.preferences.SavedApiKeyHealthState.Error).message.contains("401"))
    }

    @Test
    fun apiUsageHeaderParser_parsesAndAccumulatesValidationResponseHeaders() = runBlocking {
        // 1. Mock validation response with both request quota headers and token usage headers
        val telemetryClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("X-RateLimit-Limit-Requests", "200")
                    .header("X-RateLimit-Remaining-Requests", "194")
                    .header("X-RateLimit-Reset-Requests", "45s")
                    .header("X-Usage-Total-Tokens", "125")
                    .header("X-RateLimit-Limit-Tokens", "10000")
                    .header("X-RateLimit-Remaining-Tokens", "9875")
                    .header("X-Request-Id", "req_abc123")
                    .body(
                        okhttp3.ResponseBody.create(
                            null,
                            """{"data":{"label":"Pro Key","is_free_tier":false}}"""
                        )
                    )
                    .build()
            }
            .build()

        val settings = com.example.model.StudioSettings(
            openRouterApiKey = "sk-or-v1-headertelemetrykey1234567890",
            openRouterModel = com.example.model.OpenRouterModels.QWEN_CODER_FREE
        )
        val (_, health) = com.example.data.preferences.ApiCredentialValidator.verifySavedProviderHealth(
            settings = settings,
            buildConfigGeminiKey = "",
            httpClient = telemetryClient
        )

        assertTrue(health is com.example.data.preferences.SavedApiKeyHealthState.Verified)
        val verified = health as com.example.data.preferences.SavedApiKeyHealthState.Verified
        assertEquals(200, verified.httpStatusCode)
        val headers = verified.usageHeaders
        assertNotNull(headers)
        assertEquals(200L, headers!!.requestsLimit)
        assertEquals(194L, headers.requestsRemaining)
        assertEquals(6L, headers.computedHeaderRequestsUsed)
        assertEquals("45s", headers.requestsReset)
        assertEquals(125L, headers.tokensUsedFromHeader)
        assertEquals(10000L, headers.tokensLimit)
        assertEquals(9875L, headers.tokensRemaining)
        assertEquals("req_abc123", headers.rawDetectedHeaders["x-request-id"])

        // 2. Accumulate across multiple validation calls into ApiUsageSummary
        val initialSummary = com.example.data.preferences.ApiUsageSummary()
        val afterFirst = com.example.data.preferences.ApiUsageHeaderParser.applyValidationResponse(
            current = initialSummary,
            providerName = "OpenRouter",
            httpStatusCode = verified.httpStatusCode,
            latencyMs = verified.latencyMs,
            parsed = headers
        )
        assertEquals(1, afterFirst.validationRequestsMade)
        assertEquals(1, afterFirst.successfulValidations)
        assertEquals(0, afterFirst.failedValidations)
        assertEquals(125L, afterFirst.totalTokensUsed)
        assertEquals(125L, afterFirst.lastCallTokensUsed)
        assertEquals(200L, afterFirst.requestsLimit)
        assertEquals(194L, afterFirst.requestsRemaining)
        assertEquals(6L, afterFirst.headerReportedRequestsUsed)

        // Second validation call with prompt + completion token headers and 401 failure
        val secondRawHeaders = okhttp3.Headers.headersOf(
            "x-ratelimit-limit", "200",
            "x-ratelimit-remaining", "193",
            "x-usage-prompt-tokens", "30",
            "x-usage-completion-tokens", "20"
        )
        val parsedSecond = com.example.data.preferences.ApiUsageHeaderParser.parse(secondRawHeaders)
        val afterSecond = com.example.data.preferences.ApiUsageHeaderParser.applyValidationResponse(
            current = afterFirst,
            providerName = "OpenRouter",
            httpStatusCode = 401,
            latencyMs = 42L,
            parsed = parsedSecond
        )
        assertEquals(2, afterSecond.validationRequestsMade)
        assertEquals(1, afterSecond.successfulValidations)
        assertEquals(1, afterSecond.failedValidations)
        assertEquals(175L, afterSecond.totalTokensUsed) // 125 + (30 + 20)
        assertEquals(50L, afterSecond.lastCallTokensUsed)
        assertEquals(193L, afterSecond.requestsRemaining)
    }

    @Test
    fun apiUsageThresholdEvaluator_detectsApproachingReachedDisabledAndMonthRollover() {
        val currentMonth = "2026-11"
        val baseSettings = com.example.model.StudioSettings(
            monthlyUsageAlertEnabled = true,
            monthlyUsageLimitMetric = com.example.model.ApiUsageLimitMetric.REQUESTS,
            monthlyUsageLimit = 10L,
            monthlyUsageWarningThresholdPercent = 80
        )

        // Below 80% threshold (7 / 10 = 70%) -> NONE
        val belowSummary = com.example.data.preferences.ApiUsageSummary(
            trackingYearMonth = currentMonth,
            monthlyRequestsMade = 7,
            monthlyTokensUsed = 7000L
        )
        val belowState = com.example.data.preferences.ApiUsageThresholdEvaluator.evaluate(
            summary = belowSummary,
            settings = baseSettings,
            currentYearMonth = currentMonth
        )
        assertEquals(com.example.data.preferences.UsageThresholdSeverity.NONE, belowState.severity)
        assertFalse(belowState.shouldShowWarning)
        assertEquals(70, belowState.usagePercent)

        // At 80% threshold (8 / 10 = 80%) -> APPROACHING
        val approachingSummary = belowSummary.copy(monthlyRequestsMade = 8)
        val approachingState = com.example.data.preferences.ApiUsageThresholdEvaluator.evaluate(
            summary = approachingSummary,
            settings = baseSettings,
            currentYearMonth = currentMonth
        )
        assertEquals(com.example.data.preferences.UsageThresholdSeverity.APPROACHING, approachingState.severity)
        assertTrue(approachingState.shouldShowWarning)
        assertEquals(80, approachingState.usagePercent)

        // At or above 100% limit (10 / 10) -> LIMIT_REACHED
        val reachedSummary = belowSummary.copy(monthlyRequestsMade = 10)
        val reachedState = com.example.data.preferences.ApiUsageThresholdEvaluator.evaluate(
            summary = reachedSummary,
            settings = baseSettings,
            currentYearMonth = currentMonth
        )
        assertEquals(com.example.data.preferences.UsageThresholdSeverity.LIMIT_REACHED, reachedState.severity)
        assertTrue(reachedState.shouldShowWarning)
        assertEquals(100, reachedState.usagePercent)

        // Token metric evaluation (9,000 / 10,000 = 90% >= 80%) -> APPROACHING
        val tokenSettings = baseSettings.copy(
            monthlyUsageLimitMetric = com.example.model.ApiUsageLimitMetric.TOKENS,
            monthlyUsageLimit = 10_000L
        )
        val tokenState = com.example.data.preferences.ApiUsageThresholdEvaluator.evaluate(
            summary = belowSummary.copy(monthlyTokensUsed = 9_000L),
            settings = tokenSettings,
            currentYearMonth = currentMonth
        )
        assertEquals(com.example.data.preferences.UsageThresholdSeverity.APPROACHING, tokenState.severity)
        assertEquals(90, tokenState.usagePercent)

        // Alerts disabled -> NONE even when at limit
        val disabledState = com.example.data.preferences.ApiUsageThresholdEvaluator.evaluate(
            summary = reachedSummary,
            settings = baseSettings.copy(monthlyUsageAlertEnabled = false),
            currentYearMonth = currentMonth
        )
        assertEquals(com.example.data.preferences.UsageThresholdSeverity.NONE, disabledState.severity)
        assertFalse(disabledState.shouldShowWarning)

        // Calendar month rollover: usage tracked in 2026-10 evaluates as 0 in 2026-11
        val rolloverEval = com.example.data.preferences.ApiUsageThresholdEvaluator.evaluate(
            summary = reachedSummary.copy(trackingYearMonth = "2026-10"),
            settings = baseSettings,
            currentYearMonth = "2026-11"
        )
        assertEquals(0L, rolloverEval.currentUsage)
        assertEquals(com.example.data.preferences.UsageThresholdSeverity.NONE, rolloverEval.severity)

        // Applying generation usage in a new calendar month resets monthly counters while keeping lifetime totals
        val novEpochMs = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .parse("2026-11-02")!!.time
        val rolledSummary = com.example.data.preferences.ApiUsageHeaderParser.applyGenerationUsage(
            current = reachedSummary.copy(
                trackingYearMonth = "2026-10",
                validationRequestsMade = 10,
                totalTokensUsed = 25_000L,
                monthlyRequestsMade = 10,
                monthlyTokensUsed = 25_000L
            ),
            providerName = "OpenRouter",
            tokensUsed = 350L,
            latencyMs = 120L,
            nowEpochMs = novEpochMs
        )
        assertEquals("2026-11", rolledSummary.trackingYearMonth)
        assertEquals(1, rolledSummary.monthlyRequestsMade)
        assertEquals(350L, rolledSummary.monthlyTokensUsed)
        assertEquals(11, rolledSummary.validationRequestsMade)
        assertEquals(25_350L, rolledSummary.totalTokensUsed)
    }

    @Test
    fun localModelCatalog_extendedSuggestionsAndSpecialtyFilteringWorkAcrossRamTiers() {
        assertEquals(10, com.example.data.generator.LocalModelCatalog.presets.size)
        assertEquals(8, com.example.data.generator.LocalModelCatalog.extendedSuggestions.size)
        val all = com.example.data.generator.LocalModelCatalog.allPresets
        assertEquals(18, all.size)
        assertEquals(18, all.map { it.id }.distinct().size)
        assertNotNull(com.example.data.generator.LocalModelCatalog.findById("qwen25_coder_15b"))
        assertNotNull(com.example.data.generator.LocalModelCatalog.findById("hammer21_15b"))

        val lowRamProfile = com.example.data.generator.DeviceHardwareProfiler.buildProfile(
            totalRamGb = 4.0,
            availableRamGb = 1.6
        )
        val lowRamBestFit = com.example.data.generator.LocalModelCatalog.buildSuggestionsForProfile(
            profile = lowRamProfile,
            specialty = com.example.data.generator.LocalModelSpecialty.BEST_FIT
        )
        assertTrue(lowRamBestFit.filteredModels.isNotEmpty())
        assertTrue(
            lowRamBestFit.filteredModels.all {
                it.tier == com.example.data.generator.LocalModelTier.TIER_1_LITE
            }
        )
        assertEquals("model3_micro_coder", lowRamBestFit.codingPick.id)
        assertEquals(4, lowRamBestFit.tips.size)

        val flagshipProfile = com.example.data.generator.DeviceHardwareProfiler.buildProfile(
            totalRamGb = 12.0,
            availableRamGb = 6.5
        )
        val flagshipCoding = com.example.data.generator.LocalModelCatalog.buildSuggestionsForProfile(
            profile = flagshipProfile,
            specialty = com.example.data.generator.LocalModelSpecialty.CODING
        )
        assertEquals("qwen25_coder_3b", flagshipCoding.codingPick.id)
        assertTrue(flagshipCoding.filteredModels.any { it.id == "qwen25_coder_15b" })
        assertTrue(flagshipCoding.filteredModels.any { it.id == "qwen25_coder_3b" })

        val toolCalling = com.example.data.generator.LocalModelCatalog.buildSuggestionsForProfile(
            profile = flagshipProfile,
            specialty = com.example.data.generator.LocalModelSpecialty.TOOL_CALLING
        )
        assertEquals(listOf("hammer21_15b"), toolCalling.filteredModels.map { it.id })
    }

    @Test
    fun factoryTelemetryCalculator_computesRealProgressExtractsFilenamesAndSanitizesLoops() {
        assertEquals(
            0,
            com.example.diagnostics.FactoryTelemetryCalculator.activeNodeForPhase(com.example.model.BuildPhase.PROMPTING)
        )
        assertEquals(
            1,
            com.example.diagnostics.FactoryTelemetryCalculator.activeNodeForPhase(com.example.model.BuildPhase.GENERATING_CODE)
        )
        assertEquals(
            2,
            com.example.diagnostics.FactoryTelemetryCalculator.activeNodeForPhase(com.example.model.BuildPhase.PARSING_FILES)
        )
        assertEquals(
            2,
            com.example.diagnostics.FactoryTelemetryCalculator.activeNodeForPhase(com.example.model.BuildPhase.PACKAGING_APK)
        )

        val streamStart = com.example.diagnostics.FactoryTelemetryCalculator.calculateStreamProgress(charsReceived = 0)
        val streamMid = com.example.diagnostics.FactoryTelemetryCalculator.calculateStreamProgress(charsReceived = 3600)
        val streamHigh = com.example.diagnostics.FactoryTelemetryCalculator.calculateStreamProgress(charsReceived = 7200)
        assertEquals(0.16f, streamStart, 0.001f)
        assertEquals(0.50f, streamMid, 0.01f)
        assertEquals(0.84f, streamHigh, 0.001f)

        val fileHalf = com.example.diagnostics.FactoryTelemetryCalculator.calculateFileWriteProgress(
            writtenFiles = 3,
            totalFiles = 6
        )
        assertEquals(0.915f, fileHalf, 0.01f)

        val downloadQuarter = com.example.diagnostics.FactoryTelemetryCalculator.calculateDownloadProgress(
            bytesRead = 250L * 1024L * 1024L,
            totalBytes = 1000L * 1024L * 1024L
        )
        assertEquals(0.25f, downloadQuarter, 0.001f)

        val extractedJsonFile = com.example.diagnostics.FactoryTelemetryCalculator.extractActiveFileLabel(
            """{"appName":"Demo","files":[{"path":"app/src/main/java/com/example/ui/Theme.kt","content":"..."}]}"""
        )
        assertEquals("Theme.kt", extractedJsonFile)

        val extractedCommentFile = com.example.diagnostics.FactoryTelemetryCalculator.extractActiveFileLabel(
            "// MainActivity.kt\n@Composable fun Home() {}"
        )
        assertEquals("MainActivity.kt", extractedCommentFile)

        val loopText = "implementation(compose-compose-compose-compose-compose-compose)"
        val sanitized = com.example.diagnostics.FactoryTelemetryCalculator.sanitizeStreamingCode(loopText)
        assertFalse(sanitized.contains("compose-compose-compose-compose"))
        assertTrue(sanitized.contains("compose…"))

        val cloudUploading = com.example.diagnostics.FactoryTelemetryCalculator.resolveCloudApkBuildTelemetry(
            "Uploading (3/6): app/src/main/java/com/example/MainActivity.kt"
        )
        assertEquals(com.example.model.BuildPhase.GENERATING_CODE, cloudUploading.buildPhase)
        assertEquals(0.525f, cloudUploading.progress, 0.01f)
        assertTrue(cloudUploading.statusTicker.contains("Uploading (3/6)"))
    }

    @Test
    fun openAiSseClient_streamsIncrementalDeltaTokensAndUsage() = runBlocking {
        val ssePayload = listOf(
            """data: {"model":"google/gemini-2.5-pro-exp-03-25","choices":[{"delta":{"content":"package com.demo\n"}}]}""",
            """data: {"choices":[{"delta":{"content":"@Composable fun LiveApp() { Text(\"Real SSE\") }"}}]}""",
            """data: {"usage":{"prompt_tokens":12,"completion_tokens":28,"total_tokens":40},"choices":[{"delta":{}}]}""",
            """data: [DONE]"""
        ).joinToString("\n\n")

        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/event-stream")
                    .body(okhttp3.ResponseBody.create(null, ssePayload.toByteArray()))
                    .build()
            }
            .build()

        val emittedChunks = mutableListOf<String>()
        val result = com.example.data.remote.OpenAiSseClient.streamChatCompletion(
            client = mockClient,
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            model = "google/gemini-2.5-pro-exp-03-25",
            messages = listOf(com.example.data.remote.ChatMessage(role = "user", content = "Build counter")),
            temperature = 0.2,
            maxTokens = 2048,
            authorization = "Bearer test-key"
        ) { chunk ->
            emittedChunks += chunk
        }

        assertTrue(emittedChunks.isNotEmpty())
        assertTrue(result.content.contains("@Composable fun LiveApp()"))
        assertEquals("google/gemini-2.5-pro-exp-03-25", result.modelUsed)
        assertEquals(40L, result.tokensUsed)
    }

    @Test
    fun aiCodeParser_parsesJsonFencedCodeHeuristicsAndGracefulFallback() {
        // Stage 1: JSON Object parsing
        val rawJson = """
            {
              "appName": "Test App",
              "packageName": "com.test.app",
              "files": [
                { "path": "MainActivity.kt", "content": "package com.test.app\nclass MainActivity" }
              ]
            }
        """.trimIndent()
        val parsedJson = com.example.data.generator.AiCodeParser.parse(rawJson)
        assertEquals("Test App", parsedJson.appName)
        assertEquals("com.test.app", parsedJson.packageName)
        assertTrue(parsedJson.files.any { it.path.endsWith("MainActivity.kt") })

        // Stage 2: Markdown Code Fences
        val rawFenced = """
            Here is the implementation:
            ```kotlin
            package com.fenced.app
            @Composable fun FencedScreen() { Text("Fenced") }
            ```
        """.trimIndent()
        val parsedFenced = com.example.data.generator.AiCodeParser.parse(rawFenced)
        assertTrue(parsedFenced.files.any { it.content.contains("FencedScreen") })

        // Stage 3: Raw Kotlin Heuristic
        val rawHeuristic = """
            @Composable
            fun RawScreen() {
                Button(onClick = {}) { Text("Raw") }
            }
        """.trimIndent()
        val parsedHeuristic = com.example.data.generator.AiCodeParser.parse(rawHeuristic)
        assertTrue(parsedHeuristic.files.any { it.content.contains("RawScreen") })

        // Stage 4: Graceful Fallback Wrapper for arbitrary text or partial snippet
        val rawArbitrary = "Build a todo app with list and add button"
        val parsedFallback = com.example.data.generator.AiCodeParser.parse(rawArbitrary)
        assertTrue(parsedFallback.files.any { it.content.contains("class MainActivity") })
    }

    @Test
    fun backgroundFactoryState_updatesStateAndClampsProgress() {
        val manager = com.example.data.service.BackgroundTaskManager
        manager.updateState(
            isBusy = true,
            title = "Compiling APK",
            message = "Running dx...",
            progress = 1.45f // progress over 1.0f should clamp to 1.0f
        )
        val state = manager.taskState.value
        assertTrue(state.isBusy)
        assertEquals("Compiling APK", state.title)
        assertEquals("Running dx...", state.message)
        assertEquals(1.0f, state.progress, 0.001f)

        manager.updateState(
            isBusy = false,
            title = "Build Complete",
            message = "Success",
            progress = -0.2f // progress under 0f should clamp to 0f
        )
        val clampedState = manager.taskState.value
        assertFalse(clampedState.isBusy)
        assertEquals(0.0f, clampedState.progress, 0.001f)

        manager.cancelTask(null)
        val cancelledState = manager.taskState.value
        assertFalse(cancelledState.isBusy)
        assertEquals("Cancelled", cancelledState.message)
    }
}



