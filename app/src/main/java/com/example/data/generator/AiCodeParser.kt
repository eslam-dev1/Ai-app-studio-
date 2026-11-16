package com.example.data.generator

import com.example.model.GeneratedFile
import com.example.model.GeneratedProjectPayload
import kotlinx.serialization.json.Json

object AiCodeParser {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        allowTrailingComma = true
    }

    fun parse(rawOutput: String, defaultAppName: String = "Studio App", defaultPackage: String = "com.studio.app"): GeneratedProjectPayload {
        if (rawOutput.isBlank()) {
            throw IllegalStateException("AI returned an empty response. Check your API key, model selection, or internet connection.")
        }
        val trimmed = rawOutput.trim()

        // Stage 1: JSON Extraction
        val jsonPayload = tryExtractJson(trimmed)
        if (jsonPayload != null && jsonPayload.files.isNotEmpty()) {
            return normalizePayload(jsonPayload, defaultAppName, defaultPackage)
        }

        // Stage 2: Markdown Code Fences (with support for multiple files via // FILE: markers or code blocks)
        val fencedPayload = tryExtractFencedCode(trimmed, defaultAppName, defaultPackage)
        if (fencedPayload != null && fencedPayload.files.isNotEmpty()) {
            return normalizePayload(fencedPayload, defaultAppName, defaultPackage)
        }

        // Stage 3: Raw Kotlin Heuristic Detection
        val heuristicPayload = tryExtractRawKotlinHeuristic(trimmed, defaultAppName, defaultPackage)
        if (heuristicPayload != null && heuristicPayload.files.isNotEmpty()) {
            return normalizePayload(heuristicPayload, defaultAppName, defaultPackage)
        }

        throw IllegalStateException("Failed to parse valid project files from AI response. Raw output preview: ${rawOutput.take(200)}")
    }

    private fun tryExtractJson(text: String): GeneratedProjectPayload? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        var endIdx = -1

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
                        endIdx = i
                        break
                    }
                }
            }
        }

        val jsonSlice = if (endIdx != -1) {
            text.substring(start, endIdx + 1)
        } else {
            val lastBrace = text.lastIndexOf('}')
            if (lastBrace > start) text.substring(start, lastBrace + 1) else null
        }

        if (jsonSlice == null) return null

        return runCatching {
            lenientJson.decodeFromString<GeneratedProjectPayload>(jsonSlice)
        }.getOrNull()
    }

    private fun tryExtractFencedCode(text: String, appName: String, packageName: String): GeneratedProjectPayload? {
        val fenceRegex = Regex("```(?:kotlin|kt|xml|json|java)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val matches = fenceRegex.findAll(text).toList()
        if (matches.isEmpty()) return null

        val files = mutableListOf<GeneratedFile>()
        var resolvedPackage = packageName

        for (match in matches) {
            val rawBlock = match.groupValues[1].trim()
            if (rawBlock.isBlank()) continue

            val splitSections = splitByFileMarkers(rawBlock)
            for (content in splitSections) {
                if (content.isBlank()) continue

                val pkgMatch = Regex("""^\s*package\s+([a-zA-Z0-9_.]+)""", RegexOption.MULTILINE).find(content)?.groupValues?.getOrNull(1)
                if (pkgMatch != null) {
                    resolvedPackage = JsonProjectExtractor.sanitizePackageName(pkgMatch)
                }
                val resolvedPkgDir = resolvedPackage.replace('.', '/')

                val fileMarkerMatch = Regex("""//\s*(?:FILE|Path):\s*([^\n]+)""", RegexOption.IGNORE_CASE).find(content)
                val path = if (fileMarkerMatch != null) {
                    fileMarkerMatch.groupValues[1].trim().removePrefix("/")
                } else if (content.contains("activity_main.xml") || content.contains("<LinearLayout") || content.contains("<androidx.")) {
                    "app/src/main/res/layout/activity_main.xml"
                } else if (content.contains("strings.xml") || content.contains("<resources>")) {
                    "app/src/main/res/values/strings.xml"
                } else if (content.contains("build.gradle")) {
                    "app/build.gradle.kts"
                } else {
                    "app/src/main/java/$resolvedPkgDir/MainActivity.kt"
                }

                val cleanContent = content.replace(Regex("""//\s*(?:FILE|Path):[^\n]+\n?""", RegexOption.IGNORE_CASE), "").trim()
                if (cleanContent.isNotBlank()) {
                    files.add(GeneratedFile(path = path, content = cleanContent))
                }
            }
        }

        if (files.isEmpty()) return null

        return GeneratedProjectPayload(
            appName = appName,
            packageName = resolvedPackage,
            files = files
        )
    }

    private fun tryExtractRawKotlinHeuristic(text: String, appName: String, packageName: String): GeneratedProjectPayload? {
        val hasKeywords = text.contains("package ") ||
            text.contains("import androidx.") ||
            text.contains("@Composable") ||
            text.contains("fun ") ||
            text.contains("class MainActivity") ||
            text.contains("setContent")

        if (!hasKeywords) return null

        val lines = text.lines()
        val codeLines = lines
            .dropWhile { line ->
                val l = line.trim()
                l.startsWith("Here") || l.startsWith("Sure") || l.startsWith("Here is") ||
                    l.startsWith("Output") || l.startsWith("```") || l.isBlank()
            }
            .dropLastWhile { line ->
                val l = line.trim()
                l.startsWith("```") || l.isBlank()
            }

        val codeContent = codeLines.joinToString("\n").trim()
        if (codeContent.isBlank()) return null

        val pkgMatch = Regex("""^\s*package\s+([a-zA-Z0-9_.]+)""", RegexOption.MULTILINE).find(codeContent)?.groupValues?.getOrNull(1)
        val resolvedPkg = pkgMatch?.let { JsonProjectExtractor.sanitizePackageName(it) } ?: packageName
        val pkgDir = resolvedPkg.replace('.', '/')

        val finalCode = if (codeContent.contains("package ")) {
            codeContent
        } else {
            "package $resolvedPkg\n\n$codeContent"
        }

        return GeneratedProjectPayload(
            appName = appName,
            packageName = resolvedPkg,
            files = listOf(
                GeneratedFile(
                    path = "app/src/main/java/$pkgDir/MainActivity.kt",
                    content = finalCode
                )
            )
        )
    }

    private fun splitByFileMarkers(block: String): List<String> {
        val markerRegex = Regex("""(?=^//\s*(?:FILE|Path):\s*[^\n]+)""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val parts = block.split(markerRegex).map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.isEmpty()) listOf(block) else parts
    }

    private fun normalizePayload(payload: GeneratedProjectPayload, defaultAppName: String, defaultPackage: String): GeneratedProjectPayload {
        val resolvedAppName = payload.appName.ifBlank { defaultAppName }
        val resolvedPkg = if (payload.packageName.isNotBlank()) {
            JsonProjectExtractor.sanitizePackageName(payload.packageName)
        } else {
            defaultPackage
        }
        val normalizedFiles = payload.files.map { file ->
            GeneratedFile(
                path = file.path.trim().removePrefix("/"),
                content = file.content
            )
        }.distinctBy { it.path }

        val completeFiles = JsonProjectExtractor.ensureEssentialGradleFiles(
            appName = resolvedAppName,
            packageName = resolvedPkg,
            files = normalizedFiles
        )

        val cleanSuggestions = payload.suggestions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)

        return payload.copy(
            appName = resolvedAppName,
            packageName = resolvedPkg,
            files = completeFiles,
            suggestions = cleanSuggestions
        )
    }
}
