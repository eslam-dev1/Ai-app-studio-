package com.example.diagnostics

import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.model.GeneratedFile
import com.example.model.StudioProject
import com.example.preview.ComposePreviewEngine
import java.util.UUID

object ProjectStaticAnalyzer {

    private data class ImportRule(
        val symbolPattern: Regex,
        val requiredImportPrefix: String,
        val suggestedImport: String
    )

    private val commonComposeImportRules = listOf(
        ImportRule(
            symbolPattern = Regex("\\bComposable\\b"),
            requiredImportPrefix = "androidx.compose.runtime",
            suggestedImport = "androidx.compose.runtime.Composable"
        ),
        ImportRule(
            symbolPattern = Regex("\\b(remember|mutableStateOf|LaunchedEffect)\\b"),
            requiredImportPrefix = "androidx.compose.runtime",
            suggestedImport = "androidx.compose.runtime.*"
        ),
        ImportRule(
            symbolPattern = Regex("\\b(Column|Row|Box|Spacer|Arrangement|PaddingValues)\\b"),
            requiredImportPrefix = "androidx.compose.foundation.layout",
            suggestedImport = "androidx.compose.foundation.layout.*"
        ),
        ImportRule(
            symbolPattern = Regex("\\b(Button|OutlinedButton|Text|Card|ElevatedCard|Scaffold|TopAppBar|OutlinedTextField|Icon|Surface|MaterialTheme)\\b"),
            requiredImportPrefix = "androidx.compose.material3",
            suggestedImport = "androidx.compose.material3.*"
        ),
        ImportRule(
            symbolPattern = Regex("\\bModifier\\b"),
            requiredImportPrefix = "androidx.compose.ui",
            suggestedImport = "androidx.compose.ui.Modifier"
        ),
        ImportRule(
            symbolPattern = Regex("\\b\\d+\\.(dp|sp)\\b"),
            requiredImportPrefix = "androidx.compose.ui.unit",
            suggestedImport = "androidx.compose.ui.unit.*"
        )
    )

    fun analyzeProject(project: StudioProject): List<ConsoleLogEntry> {
        val entries = mutableListOf<ConsoleLogEntry>()
        val now = System.currentTimeMillis()

        entries += ConsoleLogEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = now,
            level = ConsoleLogLevel.INFO,
            tag = "GradleDaemon",
            message = "Starting static pre-build analysis for '${project.appName}' (${project.packageName}) — ${project.files.size} source files."
        )

        val hasManifest = project.files.any { it.path.endsWith("AndroidManifest.xml", ignoreCase = true) }
        if (!hasManifest) {
            entries += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = now + 1,
                level = ConsoleLogLevel.ERROR,
                tag = "ManifestMerger",
                message = "Missing app/src/main/AndroidManifest.xml — APK packaging requires a valid manifest."
            )
        }

        val hasAppBuildGradle = project.files.any {
            it.path.endsWith("app/build.gradle.kts") || it.path.endsWith("app/build.gradle")
        }
        if (!hasAppBuildGradle) {
            entries += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = now + 2,
                level = ConsoleLogLevel.WARN,
                tag = "GradleConfig",
                message = "app/build.gradle.kts not found in project file list."
            )
        }

        var errorCount = entries.count { it.level == ConsoleLogLevel.ERROR }
        var warnCount = entries.count { it.level == ConsoleLogLevel.WARN }

        project.files.forEach { file ->
            val fileDiagnostics = analyzeSingleFile(file)
            entries.addAll(fileDiagnostics)
            errorCount += fileDiagnostics.count { it.level == ConsoleLogLevel.ERROR }
            warnCount += fileDiagnostics.count { it.level == ConsoleLogLevel.WARN }
        }

        // Also run ComposePreviewEngine to surface any Composable preview tree issues
        val previewResult = ComposePreviewEngine.parseProjectSources(
            files = project.files.map { it.path to it.content }
        )
        if (previewResult.composables.isEmpty()) {
            entries += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                level = ConsoleLogLevel.WARN,
                tag = "ComposeCompiler",
                message = "No @Composable functions detected across Kotlin sources."
            )
            warnCount++
        } else {
            entries += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                level = ConsoleLogLevel.DEBUG,
                tag = "ComposeCompiler",
                message = "Discovered ${previewResult.composables.size} @Composable screen(s): ${
                    previewResult.composables.joinToString { "@${it.name}" }
                }"
            )
        }

        if (errorCount == 0) {
            entries += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                level = ConsoleLogLevel.SUCCESS,
                tag = "KotlinCompiler",
                message = "BUILD CHECK PASSED — 0 compiler errors, $warnCount warning(s) across ${project.files.size} files."
            )
        } else {
            entries += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                level = ConsoleLogLevel.ERROR,
                tag = "KotlinCompiler",
                message = "BUILD CHECK FAILED — $errorCount compiler error(s), $warnCount warning(s). Tap an error below to jump to the file."
            )
        }

        return entries
    }

    fun analyzeSingleFile(file: GeneratedFile): List<ConsoleLogEntry> {
        val path = file.path
        val content = file.content
        if (com.example.ui.assets.ProjectResAssetManager.isBinaryAssetPath(path) ||
            com.example.ui.assets.ProjectResAssetManager.isBinaryEncodedContent(content)
        ) {
            return emptyList()
        }
        return when {
            path.endsWith(".kt", ignoreCase = true) -> analyzeKotlinFile(path, content)
            path.endsWith(".gradle.kts", ignoreCase = true) -> analyzeGradleKtsFile(path, content)
            path.endsWith("AndroidManifest.xml", ignoreCase = true) -> analyzeManifestXml(path, content)
            path.endsWith(".xml", ignoreCase = true) -> analyzeGenericXml(path, content)
            else -> emptyList()
        }
    }

    private fun analyzeKotlinFile(path: String, content: String): List<ConsoleLogEntry> {
        val logs = mutableListOf<ConsoleLogEntry>()
        val lines = content.lines()

        // 1. Check package declaration
        val hasPackage = lines.any { it.trim().startsWith("package ") }
        if (!hasPackage) {
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.WARN,
                tag = "KotlinCompiler",
                message = "$path:1: Missing 'package' directive at top of Kotlin source file.",
                filePath = path,
                lineNumber = 1
            )
        }

        // 2. Check balanced braces, parentheses, and unclosed string literals with line tracking
        val braceDiagnostics = checkBalancedDelimiters(path, lines, tag = "KotlinCompiler")
        logs.addAll(braceDiagnostics)

        // 3. Check common unresolved Compose imports
        val importLines = lines.map { it.trim() }.filter { it.startsWith("import ") }
        val codeLinesWithoutImports = lines.mapIndexedNotNull { idx, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ") || trimmed.startsWith("//")) {
                null
            } else {
                (idx + 1) to trimmed
            }
        }

        for (rule in commonComposeImportRules) {
            val firstMatch = codeLinesWithoutImports.firstOrNull { (_, text) ->
                rule.symbolPattern.containsMatchIn(text)
            }
            if (firstMatch != null) {
                val isImported = importLines.any { imp ->
                    imp.removePrefix("import ").trim().startsWith(rule.requiredImportPrefix)
                }
                if (!isImported) {
                    val (lineNum, matchedText) = firstMatch
                    val token = rule.symbolPattern.find(matchedText)?.value ?: "symbol"
                    logs += ConsoleLogEntry(
                        id = UUID.randomUUID().toString(),
                        level = ConsoleLogLevel.WARN,
                        tag = "KotlinCompiler",
                        message = "$path:$lineNum: Unresolved reference '$token' — add 'import ${rule.suggestedImport}'",
                        filePath = path,
                        lineNumber = lineNum
                    )
                }
            }
        }

        // 4. Detect stray markdown fences accidentally left in Kotlin file
        lines.forEachIndexed { idx, line ->
            if (line.trim().startsWith("```")) {
                logs += ConsoleLogEntry(
                    id = UUID.randomUUID().toString(),
                    level = ConsoleLogLevel.ERROR,
                    tag = "KotlinCompiler",
                    message = "$path:${idx + 1}: Unexpected markdown fence '```' in Kotlin code.",
                    filePath = path,
                    lineNumber = idx + 1
                )
            }
        }

        return logs
    }

    private fun analyzeGradleKtsFile(path: String, content: String): List<ConsoleLogEntry> {
        val logs = mutableListOf<ConsoleLogEntry>()
        val lines = content.lines()

        logs.addAll(checkBalancedDelimiters(path, lines, tag = "GradleCompiler"))

        if (path.endsWith("app/build.gradle.kts")) {
            if (!content.contains("namespace")) {
                logs += ConsoleLogEntry(
                    id = UUID.randomUUID().toString(),
                    level = ConsoleLogLevel.ERROR,
                    tag = "GradleCompiler",
                    message = "$path:1: Missing 'namespace' property inside android { } block.",
                    filePath = path,
                    lineNumber = 1
                )
            }
            if (!content.contains("compileSdk")) {
                logs += ConsoleLogEntry(
                    id = UUID.randomUUID().toString(),
                    level = ConsoleLogLevel.ERROR,
                    tag = "GradleCompiler",
                    message = "$path:1: Missing 'compileSdk' configuration in app/build.gradle.kts.",
                    filePath = path,
                    lineNumber = 1
                )
            }
        }
        return logs
    }

    private fun analyzeManifestXml(path: String, content: String): List<ConsoleLogEntry> {
        val logs = mutableListOf<ConsoleLogEntry>()
        if (!content.contains("<manifest")) {
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.ERROR,
                tag = "ManifestMerger",
                message = "$path:1: Root <manifest> tag is missing.",
                filePath = path,
                lineNumber = 1
            )
        }
        if (!content.contains("<application")) {
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.ERROR,
                tag = "ManifestMerger",
                message = "$path:1: <application> tag is missing in AndroidManifest.xml.",
                filePath = path,
                lineNumber = 1
            )
        }
        if (!content.contains("<activity")) {
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.WARN,
                tag = "ManifestMerger",
                message = "$path:1: No <activity> entry declared in AndroidManifest.xml.",
                filePath = path,
                lineNumber = 1
            )
        }
        return logs
    }

    private fun analyzeGenericXml(path: String, content: String): List<ConsoleLogEntry> {
        val logs = mutableListOf<ConsoleLogEntry>()
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.ERROR,
                tag = "Aapt2Resource",
                message = "$path:1: XML resource file is empty.",
                filePath = path,
                lineNumber = 1
            )
        } else if (!trimmed.startsWith("<")) {
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.ERROR,
                tag = "Aapt2Resource",
                message = "$path:1: Content is not allowed in prolog (XML must start with '<').",
                filePath = path,
                lineNumber = 1
            )
        }
        return logs
    }

    private fun checkBalancedDelimiters(
        path: String,
        lines: List<String>,
        tag: String
    ): List<ConsoleLogEntry> {
        val logs = mutableListOf<ConsoleLogEntry>()
        val braceStack = ArrayDeque<Int>() // stores 1-based line numbers
        val parenStack = ArrayDeque<Int>()
        var inBlockComment = false
        var inTripleQuote = false

        lines.forEachIndexed { lineIdx, rawLine ->
            val lineNum = lineIdx + 1
            var i = 0
            var inSingleLineString = false

            while (i < rawLine.length) {
                if (inBlockComment) {
                    if (i + 1 < rawLine.length && rawLine[i] == '*' && rawLine[i + 1] == '/') {
                        inBlockComment = false
                        i += 2
                        continue
                    }
                    i++
                    continue
                }

                if (!inSingleLineString && i + 2 < rawLine.length &&
                    rawLine[i] == '"' && rawLine[i + 1] == '"' && rawLine[i + 2] == '"'
                ) {
                    inTripleQuote = !inTripleQuote
                    i += 3
                    continue
                }

                if (inTripleQuote) {
                    i++
                    continue
                }

                if (!inSingleLineString && i + 1 < rawLine.length && rawLine[i] == '/' && rawLine[i + 1] == '/') {
                    break // rest of line is comment
                }

                if (!inSingleLineString && i + 1 < rawLine.length && rawLine[i] == '/' && rawLine[i + 1] == '*') {
                    inBlockComment = true
                    i += 2
                    continue
                }

                val c = rawLine[i]
                if (c == '"' && (i == 0 || rawLine[i - 1] != '\\')) {
                    inSingleLineString = !inSingleLineString
                    i++
                    continue
                }

                if (!inSingleLineString) {
                    when (c) {
                        '{' -> braceStack.addLast(lineNum)
                        '}' -> {
                            if (braceStack.isEmpty()) {
                                logs += ConsoleLogEntry(
                                    id = UUID.randomUUID().toString(),
                                    level = ConsoleLogLevel.ERROR,
                                    tag = tag,
                                    message = "$path:$lineNum: Syntax error: Unexpected closing brace '}' with no matching '{'.",
                                    filePath = path,
                                    lineNumber = lineNum
                                )
                            } else {
                                braceStack.removeLast()
                            }
                        }
                        '(' -> parenStack.addLast(lineNum)
                        ')' -> {
                            if (parenStack.isEmpty()) {
                                logs += ConsoleLogEntry(
                                    id = UUID.randomUUID().toString(),
                                    level = ConsoleLogLevel.ERROR,
                                    tag = tag,
                                    message = "$path:$lineNum: Syntax error: Unexpected closing parenthesis ')' with no matching '('.",
                                    filePath = path,
                                    lineNumber = lineNum
                                )
                            } else {
                                parenStack.removeLast()
                            }
                        }
                    }
                }
                i++
            }

            if (inSingleLineString) {
                logs += ConsoleLogEntry(
                    id = UUID.randomUUID().toString(),
                    level = ConsoleLogLevel.ERROR,
                    tag = tag,
                    message = "$path:$lineNum: Unclosed string literal on line $lineNum.",
                    filePath = path,
                    lineNumber = lineNum
                )
            }
        }

        if (braceStack.isNotEmpty()) {
            val openLine = braceStack.last()
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.ERROR,
                tag = tag,
                message = "$path:$openLine: Syntax error: Unclosed brace '{' opened at line $openLine (missing ${braceStack.size} closing '}').",
                filePath = path,
                lineNumber = openLine
            )
        }

        if (parenStack.isNotEmpty()) {
            val openLine = parenStack.last()
            logs += ConsoleLogEntry(
                id = UUID.randomUUID().toString(),
                level = ConsoleLogLevel.ERROR,
                tag = tag,
                message = "$path:$openLine: Syntax error: Unclosed parenthesis '(' opened at line $openLine.",
                filePath = path,
                lineNumber = openLine
            )
        }

        return logs
    }
}
