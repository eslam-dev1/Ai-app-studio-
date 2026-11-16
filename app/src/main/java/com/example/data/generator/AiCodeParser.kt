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
        val trimmed = rawOutput.trim()
        if (trimmed.isBlank()) {
            return buildGracefulFallbackPayload(defaultAppName, defaultPackage, "// Empty model response fallback")
        }

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

        // Stage 4: Graceful Fallback Wrapper (never throws; wraps partial snippets or prompts into a valid MainActivity)
        return buildGracefulFallbackPayload(trimmed, defaultAppName, defaultPackage)
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
            val content = match.groupValues[1].trim()
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

            val cleanContent = content.replace(Regex("""//\s*(?:FILE|Path):[^\n]+\n?""""), "").trim()
            files.add(GeneratedFile(path = path, content = cleanContent))
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
        val codeLines = lines.dropWhile { line ->
            val l = line.trim()
            l.startsWith("Here") || l.startsWith("Sure") || l.startsWith("Here is") ||
                l.startsWith("Output") || l.startsWith("```") || l.isBlank()
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

    private fun buildGracefulFallbackPayload(rawText: String, appName: String, packageName: String): GeneratedProjectPayload {
        val pkgDir = packageName.replace('.', '/')
        val sanitizedSnippet = rawText.take(3000).replace("*/", "* /")
        val wrappedCode = """
package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$appName",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Generated via Local AI", style = MaterialTheme.typography.titleMedium)
                                }
                                Text("Model output snippet:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "$sanitizedSnippet",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
        """.trimIndent()

        return GeneratedProjectPayload(
            appName = appName,
            packageName = packageName,
            files = listOf(
                GeneratedFile(
                    path = "app/src/main/java/$pkgDir/MainActivity.kt",
                    content = wrappedCode
                )
            )
        )
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

        return payload.copy(
            appName = resolvedAppName,
            packageName = resolvedPkg,
            files = completeFiles
        )
    }
}
