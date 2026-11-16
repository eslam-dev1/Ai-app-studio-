package com.example.data.github

import com.example.model.GeneratedFile
import com.example.model.StudioProject

/**
 * Helper utility that injects a production-ready `.github/workflows/build-apk.yml`
 * GitHub Actions workflow into every generated Android project, configured with
 * the latest Gradle release and multi-layer dependency/build caching for fast APK builds.
 */
object GitHubWorkflowInjector {

    const val WORKFLOW_FILE_PATH = ".github/workflows/build-apk.yml"
    const val LATEST_GRADLE_VERSION = "8.13"

    /**
     * Injects (or updates if blank) `.github/workflows/build-apk.yml` into [files]
     * and returns the sorted project file list.
     */
    fun injectBuildApkWorkflow(
        appName: String,
        files: List<GeneratedFile>,
        overwriteExisting: Boolean = false
    ): List<GeneratedFile> {
        val byPath = files.associateBy { it.path.trim().removePrefix("/") }.toMutableMap()
        val existing = byPath[WORKFLOW_FILE_PATH]

        if (overwriteExisting || existing == null || existing.content.isBlank()) {
            byPath[WORKFLOW_FILE_PATH] = GeneratedFile(
                path = WORKFLOW_FILE_PATH,
                content = generateBuildApkWorkflowYaml(appName)
            )
        }

        return byPath.values.sortedBy { it.path }
    }

    /**
     * Injects `.github/workflows/build-apk.yml` directly into a [StudioProject].
     */
    fun injectBuildApkWorkflow(
        project: StudioProject,
        overwriteExisting: Boolean = false
    ): StudioProject {
        val updatedFiles = injectBuildApkWorkflow(
            appName = project.appName,
            files = project.files,
            overwriteExisting = overwriteExisting
        )
        return project.copy(files = updatedFiles)
    }

    /**
     * Generates the `.github/workflows/build-apk.yml` YAML content using the latest Gradle
     * version and dependency + Android build caching.
     */
    fun generateBuildApkWorkflowYaml(
        appName: String,
        gradleVersion: String = LATEST_GRADLE_VERSION
    ): String {
        val cleanTitle = appName.replace(Regex("[\\r\\n]+"), " ").trim().ifBlank { "Android App" }
        val artifactSlug = cleanTitle
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "app" }

        return """
            name: Build Debug APK - $cleanTitle

            on:
              push:
                branches: [ "main", "master" ]
              pull_request:
                branches: [ "main", "master" ]
              workflow_dispatch:

            concurrency:
              group: android-apk-${'$'}{{ github.ref }}
              cancel-in-progress: true

            env:
              JAVA_VERSION: '17'
              GRADLE_VERSION: '$gradleVersion'
              GRADLE_OPTS: '-Dorg.gradle.daemon=false -Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Xmx3072m'

            jobs:
              build-apk:
                name: Compile Debug APK
                runs-on: ubuntu-latest
                timeout-minutes: 25

                steps:
                  - name: Checkout repository
                    uses: actions/checkout@v4

                  - name: Set up JDK 17
                    uses: actions/setup-java@v4
                    with:
                      distribution: 'temurin'
                      java-version: ${'$'}{{ env.JAVA_VERSION }}

                  - name: Set up Android SDK
                    uses: android-actions/setup-android@v3

                  - name: Cache Android SDK & build-cache
                    uses: actions/cache@v4
                    with:
                      path: |
                        ~/.android/build-cache
                        ~/.android/cache
                      key: ${'$'}{{ runner.os }}-android-build-cache-${'$'}{{ hashFiles('**/*.gradle*', '**/gradle.properties', '**/libs.versions.toml') }}
                      restore-keys: |
                        ${'$'}{{ runner.os }}-android-build-cache-

                  - name: Setup Gradle with Dependency Caching
                    uses: gradle/actions/setup-gradle@v4
                    with:
                      gradle-version: ${'$'}{{ env.GRADLE_VERSION }}
                      cache-read-only: false

                  - name: Assemble Debug APK
                    run: gradle assembleDebug --build-cache --parallel --stacktrace

                  - name: Upload Compiled Debug APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: $artifactSlug-debug-apk
                      path: app/build/outputs/apk/debug/*.apk
                      retention-days: 14
                      if-no-files-found: error
        """.trimIndent() + "\n"
    }
}
