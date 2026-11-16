package com.example.data.generator

import com.example.data.github.GitHubWorkflowInjector
import com.example.model.GeneratedFile
import com.example.model.GeneratedProjectPayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object JsonProjectExtractor {

    @OptIn(ExperimentalSerializationApi::class)
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        allowTrailingComma = true
    }

    fun extract(rawOutput: String): GeneratedProjectPayload {
        val trimmed = rawOutput.trim()
        val cleaned = stripMarkdownFences(trimmed)
        val jsonSlice = extractOutermostJsonObject(cleaned)

        val parsed: GeneratedProjectPayload = if (jsonSlice != null) {
            runCatching {
                lenientJson.decodeFromString<GeneratedProjectPayload>(jsonSlice)
            }.getOrElse { ex ->
                extractKotlinCodeFallback(trimmed) ?: throw IllegalStateException(
                    "Could not parse generated project JSON: ${ex.localizedMessage ?: "Malformed JSON"}"
                )
            }
        } else {
            extractKotlinCodeFallback(trimmed) ?: throw IllegalStateException(
                "The model did not return a valid JSON object or Kotlin source block. Please try again or switch models."
            )
        }

        if (parsed.files.isEmpty()) {
            throw IllegalStateException("Generated JSON contained zero project files.")
        }

        val normalizedPackage = sanitizePackageName(parsed.packageName)
        val normalizedFiles = parsed.files.map { file ->
            GeneratedFile(
                path = file.path.trim().removePrefix("/"),
                content = file.content
            )
        }.distinctBy { it.path }

        val resolvedAppName = parsed.appName.ifBlank { "Generated App" }
        val completeFiles = ensureEssentialGradleFiles(
            appName = resolvedAppName,
            packageName = normalizedPackage,
            files = normalizedFiles
        )

        return parsed.copy(
            appName = resolvedAppName,
            packageName = normalizedPackage,
            files = completeFiles
        )
    }

    private fun extractKotlinCodeFallback(rawText: String): GeneratedProjectPayload? {
        val kotlinFenceRegex = Regex("```(?:kotlin|kt|KOTLIN)?\\s*([\\s\\S]*?)```")
        val fencedBlocks = kotlinFenceRegex.findAll(rawText)
            .map { it.groupValues[1].trim() }
            .filter {
                it.contains("package ") ||
                    it.contains("import androidx.") ||
                    it.contains("@Composable") ||
                    it.contains("class MainActivity")
            }
            .toList()

        val candidateCode = when {
            fencedBlocks.isNotEmpty() -> fencedBlocks.joinToString("\n\n")
            rawText.contains("@Composable") || rawText.contains("class MainActivity") ->
                stripMarkdownFences(rawText)
            else -> return null
        }

        val pkgMatch = Regex("""^\s*package\s+([a-zA-Z0-9_.]+)""", RegexOption.MULTILINE)
            .find(candidateCode)
            ?.groupValues
            ?.getOrNull(1)
        val packageName = sanitizePackageName(pkgMatch ?: "com.generated.localapp")
        val pkgDir = packageName.replace('.', '/')

        val finalKotlinSource = if (candidateCode.contains("package ")) {
            candidateCode
        } else {
            "package $packageName\n\n$candidateCode"
        }

        return GeneratedProjectPayload(
            appName = "Local AI App",
            packageName = packageName,
            files = listOf(
                GeneratedFile(
                    path = "app/src/main/java/$pkgDir/MainActivity.kt",
                    content = finalKotlinSource
                )
            )
        )
    }

    private fun stripMarkdownFences(text: String): String {
        val fenceRegex = Regex("```(?:json|JSON|kotlin|kt)?\\s*([\\s\\S]*?)```")
        val match = fenceRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        return text
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```kotlin")
            .removePrefix("```kt")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractOutermostJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        // Fallback if truncated slightly: return from start to last '}'
        val lastBrace = text.lastIndexOf('}')
        return if (lastBrace > start) text.substring(start, lastBrace + 1) else null
    }

    fun sanitizePackageName(pkg: String): String {
        val cleaned = pkg.lowercase()
            .replace(Regex("[^a-z0-9.]"), "")
            .split('.')
            .filter { it.isNotBlank() && it.first().isLetter() }
            .joinToString(".")
        return if (cleaned.contains('.')) cleaned else "com.generated.app"
    }

    fun ensureEssentialGradleFiles(
        appName: String,
        packageName: String,
        files: List<GeneratedFile>
    ): List<GeneratedFile> {
        val byPath = files.associateBy { it.path }.toMutableMap()

        if (!byPath.containsKey("settings.gradle.kts")) {
            byPath["settings.gradle.kts"] = GeneratedFile(
                path = "settings.gradle.kts",
                content = """
                    pluginManagement {
                        repositories {
                            google()
                            mavenCentral()
                            gradlePluginPortal()
                        }
                    }
                    dependencyResolutionManagement {
                        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                        repositories {
                            google()
                            mavenCentral()
                        }
                    }
                    rootProject.name = "$appName"
                    include(":app")
                """.trimIndent()
            )
        }

        if (!byPath.containsKey("gradle.properties")) {
            byPath["gradle.properties"] = GeneratedFile(
                path = "gradle.properties",
                content = """
                    org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                    org.gradle.caching=true
                    org.gradle.parallel=true
                    org.gradle.configuration-cache=true
                    android.useAndroidX=true
                    kotlin.code.style=official
                    android.nonTransitiveRClass=true
                """.trimIndent()
            )
        }

        if (!byPath.containsKey("build.gradle.kts")) {
            byPath["build.gradle.kts"] = GeneratedFile(
                path = "build.gradle.kts",
                content = """
                    plugins {
                        id("com.android.application") version "8.7.3" apply false
                        id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
                    }
                """.trimIndent()
            )
        }

        if (!byPath.containsKey("app/build.gradle.kts")) {
            byPath["app/build.gradle.kts"] = GeneratedFile(
                path = "app/build.gradle.kts",
                content = """
                    plugins {
                        id("com.android.application")
                        id("org.jetbrains.kotlin.android")
                        id("org.jetbrains.kotlin.plugin.compose")
                    }

                    android {
                        namespace = "$packageName"
                        compileSdk = 35

                        defaultConfig {
                            applicationId = "$packageName"
                            minSdk = 24
                            targetSdk = 35
                            versionCode = 1
                            versionName = "1.0"
                        }

                        compileOptions {
                            sourceCompatibility = JavaVersion.VERSION_17
                            targetCompatibility = JavaVersion.VERSION_17
                        }
                        kotlinOptions {
                            jvmTarget = "17"
                        }
                        buildFeatures {
                            compose = true
                        }
                    }

                    dependencies {
                        implementation(platform("androidx.compose:compose-bom:2024.10.01"))
                        implementation("androidx.core:core-ktx:1.15.0")
                        implementation("androidx.activity:activity-compose:1.9.3")
                        implementation("androidx.compose.ui:ui")
                        implementation("androidx.compose.ui:ui-graphics")
                        implementation("androidx.compose.ui:ui-tooling-preview")
                        implementation("androidx.compose.material3:material3")
                        implementation("androidx.compose.material:material-icons-extended")

                        // Optional dependencies (toggleable via Dependency Manager):
                        // implementation("androidx.navigation:navigation-compose:2.8.4")
                        // implementation("androidx.datastore:datastore-preferences:1.1.1")
                        // implementation("androidx.room:room-runtime:2.6.1")
                        // implementation("androidx.room:room-ktx:2.6.1")
                        // "ksp"("androidx.room:room-compiler:2.6.1")
                        // implementation("androidx.camera:camera-core:1.4.0")
                        // implementation("androidx.camera:camera-camera2:1.4.0")
                        // implementation("androidx.camera:camera-lifecycle:1.4.0")
                        // implementation("androidx.camera:camera-view:1.4.0")
                        // implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
                        // implementation("com.google.firebase:firebase-firestore")
                        // implementation("com.google.firebase:firebase-ai")
                        // implementation("com.google.firebase:firebase-auth")
                        // implementation("androidx.credentials:credentials:1.3.0")
                        // implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
                        // implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
                        // implementation("io.coil-kt:coil-compose:2.7.0")
                        // implementation("com.squareup.retrofit2:retrofit:2.11.0")
                        // implementation("com.squareup.okhttp3:okhttp:4.12.0")
                        // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                        // implementation("com.google.android.gms:play-services-location:21.3.0")
                        // implementation("com.google.accompanist:accompanist-permissions:0.36.0")
                    }
                """.trimIndent()
            )
        }


        if (!byPath.containsKey("app/src/main/AndroidManifest.xml")) {
            byPath["app/src/main/AndroidManifest.xml"] = GeneratedFile(
                path = "app/src/main/AndroidManifest.xml",
                content = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application
                            android:allowBackup="true"
                            android:label="$appName"
                            android:supportsRtl="true"
                            android:theme="@android:style/Theme.Material.Light.NoActionBar">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                """.trimIndent()
            )
        }

        return GitHubWorkflowInjector.injectBuildApkWorkflow(
            appName = appName,
            files = byPath.values.toList()
        )
    }
}
