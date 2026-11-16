package com.example.data.gradle

import com.example.model.StudioProject

data class ManagedDependencyGroup(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val matchTokens: List<String>,
    val mavenCoordinatesLines: List<String>,
    val versionCatalogLines: List<String>
)

data class DependencyGroupStatus(
    val group: ManagedDependencyGroup,
    val isEnabled: Boolean,
    val isPresentInFile: Boolean,
    val previewArtifacts: List<String>
)

data class CustomDependencyStatus(
    val rawDeclaration: String,
    val displayName: String,
    val isEnabled: Boolean
)

data class DependencyManagerState(
    val targetGradlePath: String,
    val groups: List<DependencyGroupStatus>,
    val customDependencies: List<CustomDependencyStatus>
)

object GradleDependencyManager {

    val curatedGroups: List<ManagedDependencyGroup> = listOf(
        ManagedDependencyGroup(
            id = "room",
            title = "Room Database (SQLite & KSP)",
            category = "Database & Storage",
            description = "Local SQLite object mapping with Kotlin Coroutines, Flow, and KSP compiler support.",
            matchTokens = listOf(
                "androidx.room.runtime",
                "androidx.room.ktx",
                "androidx.room.compiler",
                "androidx.room:room-runtime",
                "androidx.room:room-ktx",
                "androidx.room:room-compiler"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"androidx.room:room-runtime:2.6.1\")",
                "implementation(\"androidx.room:room-ktx:2.6.1\")",
                "\"ksp\"(\"androidx.room:room-compiler:2.6.1\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.androidx.room.runtime)",
                "implementation(libs.androidx.room.ktx)",
                "\"ksp\"(libs.androidx.room.compiler)"
            )
        ),
        ManagedDependencyGroup(
            id = "camerax",
            title = "CameraX (Core, Camera2, Lifecycle, View)",
            category = "Camera & Media",
            description = "Jetpack CameraX libraries for camera preview, image capture, and frame analysis.",
            matchTokens = listOf(
                "androidx.camera.core",
                "androidx.camera.camera2",
                "androidx.camera.lifecycle",
                "androidx.camera.view",
                "androidx.camera:camera-core",
                "androidx.camera:camera-camera2",
                "androidx.camera:camera-lifecycle",
                "androidx.camera:camera-view"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"androidx.camera:camera-core:1.4.0\")",
                "implementation(\"androidx.camera:camera-camera2:1.4.0\")",
                "implementation(\"androidx.camera:camera-lifecycle:1.4.0\")",
                "implementation(\"androidx.camera:camera-view:1.4.0\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.androidx.camera.core)",
                "implementation(libs.androidx.camera.camera2)",
                "implementation(libs.androidx.camera.lifecycle)",
                "implementation(libs.androidx.camera.view)"
            )
        ),
        ManagedDependencyGroup(
            id = "firebase_firestore",
            title = "Firebase BoM & Cloud Firestore",
            category = "Firebase & Cloud",
            description = "NoSQL cloud database with real-time snapshot listeners and offline caching.",
            matchTokens = listOf(
                "libs.firebase.bom",
                "libs.firebase.firestore",
                "com.google.firebase:firebase-bom",
                "com.google.firebase:firebase-firestore"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(platform(\"com.google.firebase:firebase-bom:33.6.0\"))",
                "implementation(\"com.google.firebase:firebase-firestore\")"
            ),
            versionCatalogLines = listOf(
                "implementation(platform(libs.firebase.bom))",
                "implementation(libs.firebase.firestore)"
            )
        ),
        ManagedDependencyGroup(
            id = "firebase_ai",
            title = "Firebase AI Logic (Gemini SDK)",
            category = "Firebase & Cloud",
            description = "Serverless Gemini multimodal AI and App Check integration via Firebase.",
            matchTokens = listOf(
                "libs.firebase.ai",
                "libs.firebase.appcheck",
                "com.google.firebase:firebase-ai",
                "com.google.firebase:firebase-appcheck"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"com.google.firebase:firebase-ai\")",
                "implementation(\"com.google.firebase:firebase-appcheck-debug\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.firebase.ai)",
                "implementation(libs.firebase.appcheck.debug)"
            )
        ),
        ManagedDependencyGroup(
            id = "firebase_auth_google",
            title = "Firebase Auth & Credential Manager (Google Sign-In)",
            category = "Firebase & Cloud",
            description = "All four dependencies required for Firebase Authentication with Google Identity Services.",
            matchTokens = listOf(
                "libs.firebase.auth",
                "libs.androidx.credentials",
                "libs.googleid",
                "com.google.firebase:firebase-auth",
                "androidx.credentials:credentials",
                "com.google.android.libraries.identity.googleid:googleid"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"com.google.firebase:firebase-auth\")",
                "implementation(\"androidx.credentials:credentials:1.3.0\")",
                "implementation(\"androidx.credentials:credentials-play-services-auth:1.3.0\")",
                "implementation(\"com.google.android.libraries.identity.googleid:googleid:1.1.1\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.firebase.auth)",
                "implementation(libs.androidx.credentials)",
                "implementation(libs.androidx.credentials.play.services)",
                "implementation(libs.googleid)"
            )
        ),
        ManagedDependencyGroup(
            id = "coil",
            title = "Coil Compose Image Loader",
            category = "Camera & Media",
            description = "Fast, coroutine-backed async image loading (`AsyncImage`) for Jetpack Compose.",
            matchTokens = listOf(
                "libs.coil.compose",
                "io.coil-kt:coil-compose",
                "io.coil-kt.coil3:coil-compose"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"io.coil-kt:coil-compose:2.7.0\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.coil.compose)"
            )
        ),
        ManagedDependencyGroup(
            id = "retrofit_networking",
            title = "Retrofit, OkHttp & Kotlinx Serialization",
            category = "Networking & Serialization",
            description = "Type-safe REST client with OkHttp logging interceptor and JSON serialization.",
            matchTokens = listOf(
                "libs.retrofit",
                "libs.okhttp",
                "libs.logging.interceptor",
                "libs.converter.kotlinx.serialization",
                "libs.kotlinx.serialization.json",
                "com.squareup.retrofit2:retrofit",
                "com.squareup.okhttp3:okhttp",
                "org.jetbrains.kotlinx:kotlinx-serialization-json"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"com.squareup.retrofit2:retrofit:2.11.0\")",
                "implementation(\"com.squareup.okhttp3:okhttp:4.12.0\")",
                "implementation(\"org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.retrofit)",
                "implementation(libs.okhttp)",
                "implementation(libs.logging.interceptor)",
                "implementation(libs.converter.kotlinx.serialization)",
                "implementation(libs.kotlinx.serialization.json)"
            )
        ),
        ManagedDependencyGroup(
            id = "datastore",
            title = "Jetpack DataStore Preferences",
            category = "Database & Storage",
            description = "Asynchronous, Flow-based key-value persistence replacing SharedPreferences.",
            matchTokens = listOf(
                "libs.androidx.datastore.preferences",
                "androidx.datastore:datastore-preferences"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"androidx.datastore:datastore-preferences:1.1.1\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.androidx.datastore.preferences)"
            )
        ),
        ManagedDependencyGroup(
            id = "navigation_compose",
            title = "Jetpack Navigation Compose",
            category = "UI & Navigation",
            description = "Type-safe composable navigation graph and backstack management.",
            matchTokens = listOf(
                "libs.androidx.navigation.compose",
                "androidx.navigation:navigation-compose"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"androidx.navigation:navigation-compose:2.8.4\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.androidx.navigation.compose)"
            )
        ),
        ManagedDependencyGroup(
            id = "location_permissions",
            title = "Play Services Location & Accompanist Permissions",
            category = "Hardware & Sensors",
            description = "FusedLocationProviderClient GPS telemetry and Compose runtime permission helpers.",
            matchTokens = listOf(
                "libs.play.services.location",
                "libs.accompanist.permissions",
                "com.google.android.gms:play-services-location",
                "com.google.accompanist:accompanist-permissions"
            ),
            mavenCoordinatesLines = listOf(
                "implementation(\"com.google.android.gms:play-services-location:21.3.0\")",
                "implementation(\"com.google.accompanist:accompanist-permissions:0.36.0\")"
            ),
            versionCatalogLines = listOf(
                "implementation(libs.play.services.location)",
                "implementation(libs.accompanist.permissions)"
            )
        )
    )

    private val depLineRegex = Regex(
        """^\s*(//\s*)?(implementation|api|compileOnly|runtimeOnly|debugImplementation|testImplementation|androidTestImplementation|"ksp"|ksp)\s*\((.+)\)\s*$"""
    )

    fun resolveTargetGradlePath(project: StudioProject): String {
        return project.files.firstOrNull {
            it.path.equals("app/build.gradle.kts", ignoreCase = true)
        }?.path
            ?: project.files.firstOrNull { it.path.endsWith("build.gradle.kts", ignoreCase = true) }?.path
            ?: "app/build.gradle.kts"
    }

    fun inspectProjectDependencies(project: StudioProject): DependencyManagerState {
        val targetPath = resolveTargetGradlePath(project)
        val gradleContent = project.files.firstOrNull { it.path == targetPath }?.content.orEmpty()
        return inspectGradleContent(targetPath, gradleContent)
    }

    fun inspectGradleContent(
        targetPath: String,
        gradleContent: String
    ): DependencyManagerState {
        val lines = gradleContent.lines()
        val usesVersionCatalog = gradleContent.contains("libs.androidx.") || gradleContent.contains("platform(libs.")

        val groupStatuses = curatedGroups.map { group ->
            val matchingLines = lines.filter { line ->
                val match = depLineRegex.find(line)
                match != null && group.matchTokens.any { token -> line.contains(token) }
            }
            val isPresent = matchingLines.isNotEmpty()
            val isEnabled = isPresent && matchingLines.any { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("//")
            }
            val artifacts = if (usesVersionCatalog) group.versionCatalogLines else group.mavenCoordinatesLines
            DependencyGroupStatus(
                group = group,
                isEnabled = isEnabled,
                isPresentInFile = isPresent,
                previewArtifacts = artifacts
            )
        }

        val allCuratedTokens = curatedGroups.flatMap { it.matchTokens }
        val customStatuses = mutableListOf<CustomDependencyStatus>()

        for (line in lines) {
            val match = depLineRegex.find(line) ?: continue
            val isCurated = allCuratedTokens.any { token -> line.contains(token) }
            if (isCurated) continue

            val isCommented = match.groupValues[1].isNotBlank()
            val configName = match.groupValues[2]
            val targetArg = match.groupValues[3].trim()
            val rawDecl = "$configName($targetArg)"
            if (customStatuses.none { it.rawDeclaration == rawDecl }) {
                customStatuses += CustomDependencyStatus(
                    rawDeclaration = rawDecl,
                    displayName = targetArg.removeSurrounding("\""),
                    isEnabled = !isCommented
                )
            }
        }

        return DependencyManagerState(
            targetGradlePath = targetPath,
            groups = groupStatuses,
            customDependencies = customStatuses
        )
    }

    fun applyDependencyChanges(
        gradleContent: String,
        enabledGroupIds: Set<String>,
        enabledCustomDeclarations: Set<String>
    ): String {
        val usesVersionCatalog = gradleContent.contains("libs.androidx.") || gradleContent.contains("platform(libs.")
        val lines = gradleContent.lines().toMutableList()
        val handledGroupIds = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i]
            val match = depLineRegex.find(line) ?: continue
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val configName = match.groupValues[2]
            val arg = match.groupValues[3].trim()
            val cleanDecl = "$configName($arg)"

            val matchedGroup = curatedGroups.firstOrNull { group ->
                group.matchTokens.any { token -> line.contains(token) }
            }

            if (matchedGroup != null) {
                handledGroupIds += matchedGroup.id
                val shouldEnable = matchedGroup.id in enabledGroupIds
                lines[i] = if (shouldEnable) {
                    "$indent$cleanDecl"
                } else {
                    "$indent// $cleanDecl"
                }
            } else {
                val shouldEnable = cleanDecl in enabledCustomDeclarations
                lines[i] = if (shouldEnable) {
                    "$indent$cleanDecl"
                } else {
                    "$indent// $cleanDecl"
                }
            }
        }

        // For any curated group that was enabled (or not yet present in build.gradle.kts),
        // insert its lines before the closing brace of the `dependencies { ... }` block.
        val missingEnabledGroups = curatedGroups.filter {
            it.id in enabledGroupIds && it.id !in handledGroupIds
        }

        if (missingEnabledGroups.isNotEmpty()) {
            val depCloseIndex = findDependenciesClosingBraceIndex(lines)
            if (depCloseIndex != -1) {
                val toInsert = mutableListOf<String>()
                for (group in missingEnabledGroups) {
                    toInsert += "    // ${group.title}"
                    val groupLines = if (usesVersionCatalog) group.versionCatalogLines else group.mavenCoordinatesLines
                    groupLines.forEach { decl ->
                        toInsert += "    $decl"
                    }
                }
                lines.addAll(depCloseIndex, toInsert)
            }
        }

        return lines.joinToString("\n")
    }

    private fun findDependenciesClosingBraceIndex(lines: List<String>): Int {
        var depStartLine = -1
        for (i in lines.indices) {
            if (Regex("""^\s*dependencies\s*\{""").containsMatchIn(lines[i])) {
                depStartLine = i
                break
            }
        }
        if (depStartLine == -1) return -1

        var depth = 0
        for (i in depStartLine until lines.size) {
            val line = lines[i].substringBefore("//")
            for (ch in line) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return lines.lastIndex
    }
}
