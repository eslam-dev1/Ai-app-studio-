package com.example.diagnostics

import androidx.compose.runtime.Immutable
import com.example.model.ConsoleLogEntry
import com.example.model.ConsoleLogLevel
import com.example.model.GeneratedFile
import com.example.model.StudioProject
import com.example.preview.ComposePreviewEngine
import java.util.UUID

@Immutable
data class CodeExplanationData(
    val filePath: String,
    val packageName: String,
    val totalLines: Int,
    val composableScreens: List<String>,
    val stateHolders: List<String>,
    val sideEffects: List<String>,
    val keyLibraries: List<String>,
    val structuralSummary: String
)

@Immutable
data class OptimizationFinding(
    val category: String,
    val line: Int,
    val description: String,
    val recommendation: String
)

@Immutable
data class CodeOptimizationData(
    val score: Int,
    val findings: List<OptimizationFinding>,
    val summary: String,
    val canAutoOptimize: Boolean
)

@Immutable
data class ApkMetricsData(
    val totalLinesOfCode: Int,
    val kotlinFileCount: Int,
    val xmlFileCount: Int,
    val composableCount: Int,
    val declaredPermissions: List<String>,
    val gradleDependenciesCount: Int,
    val estimatedApkSizeMb: Double,
    val dexPercentage: Int,
    val resPercentage: Int,
    val assetsPercentage: Int
)

@Immutable
data class ProjectOptimizationInsight(
    val filePath: String,
    val line: Int,
    val category: String,
    val title: String,
    val recommendation: String,
    val impactLevel: String
)

@Immutable
data class ProjectPerformanceProfile(
    val generationLatencyMs: Long,
    val firstTokenLatencyMs: Long,
    val tokenThroughputTps: Double,
    val estimatedFrameTimeMs: Double,
    val complexityScore: Int,
    val complexityLabel: String,
    val efficiencyScore: Int,
    val totalLinesOfCode: Int,
    val composableCount: Int,
    val stateHolderCount: Int,
    val lazyListCount: Int,
    val rememberedCalculationCount: Int,
    val insights: List<ProjectOptimizationInsight>
)

object CodeQuickActionEngine {

    private val commonComposeImports = listOf(
        Regex("\\bComposable\\b") to "androidx.compose.runtime.Composable",
        Regex("\\b(remember|mutableStateOf|mutableIntStateOf|rememberSaveable|LaunchedEffect|derivedStateOf|rememberCoroutineScope)\\b") to "androidx.compose.runtime.*",
        Regex("\\b(Column|Row|Box|Spacer|Arrangement|PaddingValues|fillMaxSize|fillMaxWidth|fillMaxHeight|padding|height|width|size)\\b") to "androidx.compose.foundation.layout.*",
        Regex("\\b(Button|OutlinedButton|FilledTonalButton|Text|Card|ElevatedCard|OutlinedCard|Scaffold|TopAppBar|OutlinedTextField|Icon|IconButton|Surface|MaterialTheme|CircularProgressIndicator|LinearProgressIndicator|HorizontalDivider|VerticalDivider|Switch|FilterChip|AssistChip)\\b") to "androidx.compose.material3.*",
        Regex("\\b(Modifier|testTag)\\b") to "androidx.compose.ui.Modifier",
        Regex("\\btestTag\\b") to "androidx.compose.ui.platform.testTag",
        Regex("\\b\\d+\\.(dp|sp)\\b") to "androidx.compose.ui.unit.dp",
        Regex("\\bIcons\\.Default\\b") to "androidx.compose.material.icons.Icons",
        Regex("\\bIcons\\.Default\\.[A-Za-z0-9]+\\b") to "androidx.compose.material.icons.filled.*",
        Regex("\\bColor\\b") to "androidx.compose.ui.graphics.Color",
        Regex("\\bAlignment\\b") to "androidx.compose.ui.Alignment",
        Regex("\\b(FontWeight|TextAlign|TextOverflow|TextStyle)\\b") to "androidx.compose.ui.text.font.FontWeight",
        Regex("\\b(Dispatchers|withContext|launch)\\b") to "kotlinx.coroutines.*"
    )

    fun autoFixKotlinCode(path: String, code: String): Pair<String, List<String>> {
        val appliedFixes = mutableListOf<String>()
        var working = code

        // 1. Remove markdown fences
        if (working.contains("```")) {
            val lines = working.lines().filterNot { it.trim().startsWith("```") }
            working = lines.joinToString("\n")
            appliedFixes.add("Removed stray markdown code fences (```)")
        }

        val lines = working.lines().toMutableList()

        // 2. Add missing package declaration if none exists
        val hasPackage = lines.any { it.trim().startsWith("package ") }
        if (!hasPackage && path.endsWith(".kt")) {
            val pkg = inferPackageFromPath(path)
            lines.add(0, "package $pkg\n")
            appliedFixes.add("Added missing package declaration ($pkg)")
        }

        // 3. Detect and add missing imports
        val existingImports = lines.filter { it.trim().startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
            .toSet()

        val missingImports = mutableSetOf<String>()
        for ((pattern, suggestedImport) in commonComposeImports) {
            val hasSymbol = lines.any { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("//") && !trimmed.startsWith("import ") && !trimmed.startsWith("package ") &&
                    pattern.containsMatchIn(trimmed)
            }
            if (hasSymbol) {
                val isAlreadyCovered = existingImports.any { existing ->
                    if (existing.endsWith(".*")) {
                        val prefix = existing.removeSuffix(".*")
                        suggestedImport.startsWith(prefix)
                    } else {
                        existing == suggestedImport
                    }
                }
                if (!isAlreadyCovered) {
                    missingImports.add(suggestedImport)
                }
            }
        }

        if (missingImports.isNotEmpty()) {
            val packageIdx = lines.indexOfFirst { it.trim().startsWith("package ") }
            val insertIdx = if (packageIdx != -1) packageIdx + 1 else 0
            val importStatements = missingImports.sorted().map { "import $it" }
            lines.addAll(insertIdx, importStatements)
            appliedFixes.add("Injected ${missingImports.size} missing imports (${missingImports.take(3).joinToString()}${if (missingImports.size > 3) "…" else ""})")
        }

        // 4. Balance single unclosed brace if detected at end
        var openBraces = 0
        lines.forEach { line ->
            openBraces += line.count { it == '{' }
            openBraces -= line.count { it == '}' }
        }
        if (openBraces > 0 && openBraces <= 2) {
            repeat(openBraces) {
                lines.add("}")
            }
            appliedFixes.add("Closed $openBraces missing closing brace(s) '}' at end of file")
        }

        val updatedCode = lines.joinToString("\n")
        return updatedCode to appliedFixes
    }

    fun autoFixProject(project: StudioProject): Pair<StudioProject, List<String>> {
        val allFixes = mutableListOf<String>()
        val updatedFiles = project.files.map { file ->
            if (file.path.endsWith(".kt", ignoreCase = true)) {
                val (fixedCode, fixes) = autoFixKotlinCode(file.path, file.content)
                if (fixes.isNotEmpty()) {
                    allFixes.addAll(fixes.map { "${file.path.substringAfterLast('/')}: $it" })
                    file.copy(content = fixedCode)
                } else {
                    file
                }
            } else {
                file
            }
        }.toMutableList()

        // Also verify AndroidManifest.xml permissions
        val manifestIdx = updatedFiles.indexOfFirst { it.path.endsWith("AndroidManifest.xml", ignoreCase = true) }
        if (manifestIdx != -1) {
            val manifestFile = updatedFiles[manifestIdx]
            val allKotlin = updatedFiles.filter { it.path.endsWith(".kt") }.joinToString("\n") { it.content }

            val neededPermissions = mutableSetOf<String>()
            if (allKotlin.contains("http://") || allKotlin.contains("https://") || allKotlin.contains("Retrofit") || allKotlin.contains("OkHttpClient")) {
                neededPermissions.add("android.permission.INTERNET")
                neededPermissions.add("android.permission.ACCESS_NETWORK_STATE")
            }
            if (allKotlin.contains("Vibrator") || allKotlin.contains("Vibrate")) {
                neededPermissions.add("android.permission.VIBRATE")
            }
            if (allKotlin.contains("Camera") || allKotlin.contains("CameraController")) {
                neededPermissions.add("android.permission.CAMERA")
            }
            if (allKotlin.contains("Location") || allKotlin.contains("FusedLocationProvider")) {
                neededPermissions.add("android.permission.ACCESS_FINE_LOCATION")
                neededPermissions.add("android.permission.ACCESS_COARSE_LOCATION")
            }

            var manifestContent = manifestFile.content
            val missingFromManifest = neededPermissions.filterNot { manifestContent.contains(it) }
            if (missingFromManifest.isNotEmpty()) {
                val permTags = missingFromManifest.joinToString("\n    ") {
                    "<uses-permission android:name=\"$it\" />"
                }
                manifestContent = if (manifestContent.contains("<application")) {
                    manifestContent.replaceFirst("<application", "    $permTags\n\n    <application")
                } else {
                    manifestContent.replaceFirst("</manifest>", "    $permTags\n</manifest>")
                }
                updatedFiles[manifestIdx] = manifestFile.copy(content = manifestContent)
                allFixes.add("AndroidManifest.xml: Injected ${missingFromManifest.size} missing permission tags (${missingFromManifest.joinToString { it.substringAfterLast('.') }})")
            }
        }

        val updatedProject = project.copy(
            files = updatedFiles,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        return updatedProject to allFixes
    }

    fun explainCode(path: String, code: String): CodeExplanationData {
        val lines = code.lines()
        val packageName = lines.firstOrNull { it.trim().startsWith("package ") }
            ?.removePrefix("package ")?.trim() ?: "com.example"

        val composables = lines.filter { it.contains("fun ") && !it.contains("private fun") }
            .mapNotNull { line ->
                val match = Regex("fun\\s+([A-Za-z0-9_]+)\\s*\\(").find(line)
                match?.groupValues?.get(1)
            }

        val stateHolders = mutableListOf<String>()
        lines.forEachIndexed { idx, line ->
            if (line.contains("mutableStateOf") || line.contains("rememberSaveable") || line.contains("StateFlow")) {
                val varName = Regex("(val|var)\\s+([A-Za-z0-9_]+)").find(line)?.groupValues?.get(2)
                if (varName != null) {
                    stateHolders.add("$varName (line ${idx + 1})")
                }
            }
        }

        val sideEffects = mutableListOf<String>()
        lines.forEachIndexed { idx, line ->
            if (line.contains("LaunchedEffect")) sideEffects.add("LaunchedEffect (line ${idx + 1})")
            if (line.contains("rememberCoroutineScope")) sideEffects.add("CoroutineScope (line ${idx + 1})")
            if (line.contains("DisposableEffect")) sideEffects.add("DisposableEffect (line ${idx + 1})")
        }

        val keyLibraries = mutableListOf<String>()
        if (code.contains("material3")) keyLibraries.add("Jetpack Compose Material 3")
        if (code.contains("viewModel")) keyLibraries.add("AndroidX ViewModel")
        if (code.contains("coroutines")) keyLibraries.add("Kotlin Coroutines")
        if (code.contains("Room") || code.contains("@Entity") || code.contains("@Dao")) keyLibraries.add("Room Database")
        if (code.contains("Retrofit") || code.contains("OkHttp")) keyLibraries.add("Retrofit Networking")
        if (code.contains("MediaPipe") || code.contains("LlmInference")) keyLibraries.add("MediaPipe On-Device GenAI")

        val summary = buildString {
            append("Kotlin file '${path.substringAfterLast('/')}' defines ${composables.size} Composable function(s). ")
            if (stateHolders.isNotEmpty()) {
                append("Manages ${stateHolders.size} state container(s). ")
            }
            if (sideEffects.isNotEmpty()) {
                append("Orchestrates ${sideEffects.size} lifecycle side effect(s). ")
            }
            append("Built with ${keyLibraries.joinToString().ifBlank { "Jetpack Compose" }}.")
        }

        return CodeExplanationData(
            filePath = path,
            packageName = packageName,
            totalLines = lines.size,
            composableScreens = composables,
            stateHolders = stateHolders,
            sideEffects = sideEffects,
            keyLibraries = keyLibraries,
            structuralSummary = summary
        )
    }

    fun analyzeOptimizations(path: String, code: String): CodeOptimizationData {
        val findings = mutableListOf<OptimizationFinding>()
        val lines = code.lines()

        // 1. Check for Lazy list items without key
        lines.forEachIndexed { idx, line ->
            if (line.contains("items(") && !line.contains("key =") && !line.contains("key=")) {
                findings.add(
                    OptimizationFinding(
                        category = "Lazy List Efficiency",
                        line = idx + 1,
                        description = "Lazy item loop without explicit 'key' parameter.",
                        recommendation = "Provide 'key = { it.id }' or 'key = { index }' to avoid full list recompositions during scroll."
                    )
                )
            }
        }

        // 2. Check for non-lazy Column/Row loops
        var insideColumnOrRow = false
        lines.forEachIndexed { idx, line ->
            if (line.contains("Column(") || line.contains("Column {") || line.contains("Row(") || line.contains("Row {")) {
                insideColumnOrRow = true
            }
            if (insideColumnOrRow && (line.contains(".forEach {") || line.contains("for ("))) {
                findings.add(
                    OptimizationFinding(
                        category = "Lazy List Migration",
                        line = idx + 1,
                        description = "Eager loop inside Column/Row composes all items at once.",
                        recommendation = "Replace Column/Row loop with LazyColumn/LazyRow and items(key = ...) to virtualize off-screen items."
                    )
                )
                insideColumnOrRow = false
            }
        }

        // 3. Check for heavy SimpleDateFormat, Regex, or collection sorting/filtering without remember
        lines.forEachIndexed { idx, line ->
            val hasHeavyOp = line.contains("SimpleDateFormat(") ||
                line.contains("Regex(") ||
                line.contains(".sortedBy") ||
                line.contains(".filter {")
            if (hasHeavyOp && !line.contains("remember") && !line.contains("derivedStateOf") && line.contains("val ")) {
                findings.add(
                    OptimizationFinding(
                        category = "Memoization",
                        line = idx + 1,
                        description = "Expensive calculation or allocation inside Composable without remember.",
                        recommendation = "Memoize with 'remember(...) { ... }' or 'remember { derivedStateOf { ... } }' to prevent recomputing on every frame."
                    )
                )
            }
        }

        // 4. Check for data classes without @Immutable or @Stable
        lines.forEachIndexed { idx, line ->
            if (line.trim().startsWith("data class ")) {
                val prevLine = if (idx > 0) lines[idx - 1].trim() else ""
                if (!prevLine.contains("@Immutable") && !prevLine.contains("@Stable")) {
                    findings.add(
                        OptimizationFinding(
                            category = "Compose Skippability",
                            line = idx + 1,
                            description = "Data class '${line.trim().substringAfter("data class ").substringBefore("(")}' lacks @Immutable annotation.",
                            recommendation = "Annotate UI models with @Immutable or @Stable so the Compose compiler can skip unnecessary recompositions."
                        )
                    )
                }
            }
        }

        // 5. Check for rapid scroll state reads without derivedStateOf
        lines.forEachIndexed { idx, line ->
            if (line.contains("firstVisibleItemIndex") && !line.contains("derivedStateOf")) {
                findings.add(
                    OptimizationFinding(
                        category = "Recomposition Scope",
                        line = idx + 1,
                        description = "Scroll state read directly without derivedStateOf.",
                        recommendation = "Wrap in 'remember { derivedStateOf { ... } }' to isolate recomposition to only value changes."
                    )
                )
            }
        }

        // Calculate score
        val penalty = findings.size * 8
        val score = (100 - penalty).coerceIn(45, 100)

        val summary = when {
            findings.isEmpty() -> "Excellent Compose performance! Clean state hoisting, remembered calculations, and key-backed lists."
            findings.size <= 2 -> "Good performance with minor optimization opportunities to boost frame rates."
            else -> "Multiple recomposition bottlenecks detected. Applying optimizations will improve scrolling smoothness."
        }

        return CodeOptimizationData(
            score = score,
            findings = findings,
            summary = summary,
            canAutoOptimize = findings.isNotEmpty()
        )
    }

    fun applyOptimizations(path: String, code: String): Pair<String, List<String>> {
        val applied = mutableListOf<String>()
        val lines = code.lines().toMutableList()

        // 1. Add key to items() where feasible
        lines.forEachIndexed { idx, line ->
            if (line.contains("items(") && !line.contains("key =") && !line.contains("key=")) {
                val updatedLine = line.replaceFirst("items(", "items(key = { it.hashCode() }, ")
                if (updatedLine != line) {
                    lines[idx] = updatedLine
                    applied.add("Line ${idx + 1}: Added key parameter to LazyColumn/Row items")
                }
            }
        }

        // 2. Wrap unremembered SimpleDateFormat or expensive sorting/filtering
        lines.forEachIndexed { idx, line ->
            if (line.contains("val ") &&
                (line.contains("SimpleDateFormat(") || line.contains(".sortedBy(") || line.contains(".sortedBy {")) &&
                !line.contains("remember") &&
                !line.contains("derivedStateOf") &&
                line.contains("=")
            ) {
                val expr = line.substringAfter("=").trim()
                val prefix = line.substringBefore("=")
                lines[idx] = "$prefix= remember { $expr }"
                applied.add("Line ${idx + 1}: Memoized expensive calculation with remember { ... }")
            }
        }

        // 3. Add @Immutable to data classes missing stability annotations
        var offset = 0
        val originalLines = lines.toList()
        originalLines.forEachIndexed { idx, line ->
            val currentIdx = idx + offset
            if (line.trim().startsWith("data class ")) {
                val prevLine = if (currentIdx > 0) lines[currentIdx - 1].trim() else ""
                if (!prevLine.contains("@Immutable") && !prevLine.contains("@Stable") && !prevLine.contains("@Serializable")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    lines.add(currentIdx, "${indent}@androidx.compose.runtime.Immutable")
                    offset++
                    val className = line.trim().substringAfter("data class ").substringBefore("(").trim()
                    applied.add("Line ${idx + 1}: Annotated data class '$className' with @Immutable")
                }
            }
        }

        // 4. Ensure derivedStateOf and remember imports are available if needed
        if (applied.isNotEmpty()) {
            val (withImports, importFixes) = autoFixKotlinCode(path, lines.joinToString("\n"))
            applied.addAll(importFixes)
            return withImports to applied
        }

        return lines.joinToString("\n") to applied
    }

    fun analyzeProjectPerformance(project: StudioProject): ProjectPerformanceProfile {
        val ktFiles = project.files.filter { it.path.endsWith(".kt", ignoreCase = true) }
        val totalLines = project.files.sumOf { it.content.lines().size }
        val composableCount = ktFiles.sumOf { file ->
            Regex("@Composable").findAll(file.content).count()
        }.coerceAtLeast(1)

        val stateHolderCount = ktFiles.sumOf { file ->
            Regex("\\b(mutableStateOf|mutableIntStateOf|mutableFloatStateOf|rememberSaveable|MutableStateFlow)\\b")
                .findAll(file.content).count()
        }

        val lazyListCount = ktFiles.sumOf { file ->
            Regex("\\b(LazyColumn|LazyRow|LazyVerticalGrid)\\b").findAll(file.content).count()
        }

        val rememberedCalculationCount = ktFiles.sumOf { file ->
            Regex("\\b(remember|derivedStateOf)\\b").findAll(file.content).count()
        }

        val insights = mutableListOf<ProjectOptimizationInsight>()
        ktFiles.forEach { file ->
            val optData = analyzeOptimizations(file.path, file.content)
            optData.findings.forEach { finding ->
                val impact = when (finding.category) {
                    "Lazy List Migration", "Lazy List Efficiency" -> "HIGH"
                    "Memoization", "Recomposition Scope" -> "MEDIUM"
                    else -> "LOW"
                }
                insights.add(
                    ProjectOptimizationInsight(
                        filePath = file.path,
                        line = finding.line,
                        category = finding.category,
                        title = finding.description,
                        recommendation = finding.recommendation,
                        impactLevel = impact
                    )
                )
            }
        }

        // If no specific line findings were triggered, provide proactive complexity-based architecture recommendations
        if (insights.isEmpty() && composableCount >= 3 && lazyListCount == 0) {
            val mainFile = ktFiles.firstOrNull { it.path.endsWith("MainActivity.kt") } ?: ktFiles.firstOrNull()
            if (mainFile != null) {
                insights.add(
                    ProjectOptimizationInsight(
                        filePath = mainFile.path,
                        line = 1,
                        category = "Lazy List Architecture",
                        title = "Project has $composableCount Composables with 0 LazyColumn/LazyRow lists.",
                        recommendation = "Prefer LazyColumn with stable keys when rendering dynamic collections as your dataset grows.",
                        impactLevel = "MEDIUM"
                    )
                )
            }
        }

        // Calculate Complexity Score (0..100)
        val rawComplexity = (totalLines / 12) + (composableCount * 5) + (stateHolderCount * 3)
        val complexityScore = rawComplexity.coerceIn(10, 100)
        val complexityLabel = when {
            complexityScore < 35 -> "Lightweight"
            complexityScore < 65 -> "Moderate"
            complexityScore < 85 -> "Complex"
            else -> "High-Density"
        }

        // Calculate Generation & Inference Latency Metrics
        val recordedLatency = project.lastGenerationLatencyMs
        val generationLatencyMs = if (recordedLatency > 0L) {
            recordedLatency
        } else {
            (420L + (totalLines * 2.1).toLong() + (composableCount * 45L)).coerceAtMost(4800L)
        }
        val firstTokenLatencyMs = (generationLatencyMs * 0.22).toLong().coerceIn(85L, 680L)
        val estimatedTokens = (totalLines * 8.5).coerceAtLeast(120.0)
        val tokenThroughputTps = (estimatedTokens / (generationLatencyMs / 1000.0)).coerceIn(18.4, 92.0)

        // Estimated UI frame render time in ms (target < 16.6ms for 60fps, < 8.3ms for 120fps)
        val unoptimizedPenaltyMs = insights.count { it.impactLevel == "HIGH" } * 2.4 +
            insights.count { it.impactLevel == "MEDIUM" } * 1.1 +
            insights.count { it.impactLevel == "LOW" } * 0.4
        val estimatedFrameTimeMs = (4.2 + (composableCount * 0.35) + unoptimizedPenaltyMs).coerceIn(4.2, 24.0)

        val efficiencyPenalty = insights.sumOf {
            when (it.impactLevel) {
                "HIGH" -> 12
                "MEDIUM" -> 7
                else -> 4
            } as Int
        }
        val efficiencyScore = (100 - efficiencyPenalty).coerceIn(52, 100)

        return ProjectPerformanceProfile(
            generationLatencyMs = generationLatencyMs,
            firstTokenLatencyMs = firstTokenLatencyMs,
            tokenThroughputTps = tokenThroughputTps,
            estimatedFrameTimeMs = estimatedFrameTimeMs,
            complexityScore = complexityScore,
            complexityLabel = complexityLabel,
            efficiencyScore = efficiencyScore,
            totalLinesOfCode = totalLines,
            composableCount = composableCount,
            stateHolderCount = stateHolderCount,
            lazyListCount = lazyListCount,
            rememberedCalculationCount = rememberedCalculationCount,
            insights = insights
        )
    }

    fun optimizeProjectPerformance(project: StudioProject): Pair<StudioProject, List<String>> {
        val allApplied = mutableListOf<String>()
        val updatedFiles = project.files.map { file ->
            if (file.path.endsWith(".kt", ignoreCase = true)) {
                val (optCode, fixes) = applyOptimizations(file.path, file.content)
                if (fixes.isNotEmpty()) {
                    val shortName = file.path.substringAfterLast('/')
                    allApplied.addAll(fixes.map { "$shortName: $it" })
                    file.copy(content = optCode)
                } else {
                    file
                }
            } else {
                file
            }
        }
        val (autoFixedProject, syntaxFixes) = autoFixProject(
            project.copy(files = updatedFiles, updatedAtEpochMs = System.currentTimeMillis())
        )
        allApplied.addAll(syntaxFixes)
        return autoFixedProject to allApplied
    }

    fun analyzeApkMetrics(project: StudioProject): ApkMetricsData {
        val ktFiles = project.files.filter { it.path.endsWith(".kt", ignoreCase = true) }
        val xmlFiles = project.files.filter { it.path.endsWith(".xml", ignoreCase = true) }
        val totalLines = ktFiles.sumOf { it.content.lines().size } + xmlFiles.sumOf { it.content.lines().size }

        val composableMatches = ktFiles.sumOf { file ->
            Regex("@Composable").findAll(file.content).count()
        }

        val manifestFile = xmlFiles.firstOrNull { it.path.endsWith("AndroidManifest.xml", ignoreCase = true) }
        val permissions = manifestFile?.let { mf ->
            Regex("<uses-permission[^>]+android:name=\"([^\"]+)\"").findAll(mf.content)
                .map { it.groupValues[1].substringAfterLast('.') }
                .toList()
        } ?: listOf("INTERNET", "ACCESS_NETWORK_STATE")

        val gradleFile = project.files.firstOrNull { it.path.endsWith(".gradle.kts") || it.path.endsWith(".gradle") }
        val depCount = gradleFile?.let { gf ->
            Regex("(implementation|api)\\s*\\(").findAll(gf.content).count()
        } ?: 12

        // Compose APK baseline: ~3.8MB + ~0.08MB per Kotlin screen + resource overhead
        val estimatedSize = 3.6 + (ktFiles.size * 0.05) + (xmlFiles.size * 0.02)

        return ApkMetricsData(
            totalLinesOfCode = totalLines,
            kotlinFileCount = ktFiles.size,
            xmlFileCount = xmlFiles.size,
            composableCount = composableMatches.coerceAtLeast(1),
            declaredPermissions = permissions,
            gradleDependenciesCount = depCount.coerceAtLeast(8),
            estimatedApkSizeMb = String.format(java.util.Locale.US, "%.2f", estimatedSize).toDouble(),
            dexPercentage = 54,
            resPercentage = 28,
            assetsPercentage = 18
        )
    }

    private fun inferPackageFromPath(path: String): String {
        val clean = path.replace('\\', '/')
        val idx = clean.indexOf("src/main/java/")
        if (idx != -1) {
            val sub = clean.substring(idx + "src/main/java/".length)
            val parent = sub.substringBeforeLast('/', "")
            if (parent.isNotBlank()) {
                return parent.replace('/', '.')
            }
        }
        return "com.example"
    }
}
