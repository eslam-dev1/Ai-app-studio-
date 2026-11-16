package com.example.preview

import androidx.compose.ui.graphics.Color

data class PreviewComposableInfo(
    val name: String,
    val filePath: String,
    val bodyCode: String,
    val stateVars: List<PreviewStateVar>,
    val rootNodes: List<ComposeNode>
)

data class PreviewStateVar(
    val name: String,
    val initialValue: Any // Int, Boolean, Float, String
)

data class ParsedModifier(
    val fillMaxSize: Boolean = false,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val paddingAllDp: Float? = null,
    val paddingHorizontalDp: Float? = null,
    val paddingVerticalDp: Float? = null,
    val paddingStartDp: Float? = null,
    val paddingTopDp: Float? = null,
    val paddingEndDp: Float? = null,
    val paddingBottomDp: Float? = null,
    val widthDp: Float? = null,
    val heightDp: Float? = null,
    val sizeDp: Float? = null,
    val weight: Float? = null,
    val backgroundColorExpr: String? = null,
    val clipCornerDp: Float? = null,
    val isCircleClip: Boolean = false,
    val hasClickable: Boolean = false,
    val onClickCode: String? = null
)

data class ComposeNode(
    val name: String,
    val positionalArgs: List<String> = emptyList(),
    val namedArgs: Map<String, String> = emptyMap(),
    val modifier: ParsedModifier = ParsedModifier(),
    val lambdaBodyRaw: String = "",
    val children: List<ComposeNode> = emptyList(),
    val namedSlots: Map<String, List<ComposeNode>> = emptyMap(),
    val repeatCount: Int = 1,
    val listItems: List<String> = emptyList(),
    val itemVarName: String = "item",
    val conditionExpr: String? = null,
    val branches: List<Pair<String?, List<ComposeNode>>> = emptyList()
)

data class PreviewParseResult(
    val composables: List<PreviewComposableInfo>,
    val selectedComposable: PreviewComposableInfo?,
    val warningMessage: String? = null
)

object ComposePreviewEngine {

    fun parseProjectSources(
        files: List<Pair<String, String>>,
        preferredFilePath: String? = null,
        preferredComposableName: String? = null
    ): PreviewParseResult {
        val ktFiles = files.filter { it.first.endsWith(".kt", ignoreCase = true) }
        if (ktFiles.isEmpty()) {
            return PreviewParseResult(
                composables = emptyList(),
                selectedComposable = null,
                warningMessage = "No Kotlin (.kt) files found in project."
            )
        }

        val discovered = mutableListOf<PreviewComposableInfo>()
        // Prioritize preferred file first so helper composables resolve nicely
        val orderedFiles = ktFiles.sortedByDescending { it.first == preferredFilePath }

        for ((path, content) in orderedFiles) {
            discovered += extractComposablesFromSource(path, content)
        }

        // Also check setContent { ... } in MainActivity if no @Composable functions had direct UI
        if (discovered.isEmpty()) {
            for ((path, content) in orderedFiles) {
                val setContentBody = extractSetContentBody(content)
                if (setContentBody != null) {
                    val stateVars = extractStateVars(setContentBody)
                    val nodes = parseBlock(setContentBody)
                    discovered += PreviewComposableInfo(
                        name = "MainActivity.setContent",
                        filePath = path,
                        bodyCode = setContentBody,
                        stateVars = stateVars,
                        rootNodes = nodes
                    )
                }
            }
        }

        if (discovered.isEmpty()) {
            return PreviewParseResult(
                composables = emptyList(),
                selectedComposable = null,
                warningMessage = "Add a @Composable fun Screen() in a .kt file to see a live preview."
            )
        }

        val setContentRootName = orderedFiles.firstNotNullOfOrNull { (_, content) ->
            extractSetContentBody(content)?.let { findPrimaryInvokedComposable(it) }
        }

        val selected = discovered.firstOrNull {
            preferredComposableName != null && it.name == preferredComposableName
        } ?: discovered.firstOrNull {
            preferredFilePath != null && it.filePath == preferredFilePath &&
                !it.name.endsWith("Theme") && !it.name.endsWith("Preview")
        } ?: discovered.firstOrNull {
            setContentRootName != null && it.name == setContentRootName
        } ?: discovered.firstOrNull {
            containsScaffoldNode(it.rootNodes) && !it.name.endsWith("Theme") && !it.name.endsWith("Preview")
        } ?: discovered.firstOrNull {
            (it.name.endsWith("Screen") || it.name.endsWith("App") || it.name == "Main" || it.name == "Home") &&
                !it.name.endsWith("Theme") && !it.name.endsWith("Preview")
        } ?: discovered.firstOrNull {
            !it.name.endsWith("Theme") && !it.name.endsWith("Preview") && it.rootNodes.isNotEmpty()
        } ?: discovered.firstOrNull {
            !it.name.endsWith("Theme") && !it.name.endsWith("Preview")
        } ?: discovered.first()

        val hasUnbalancedBraces = selected.bodyCode.count { it == '{' } != selected.bodyCode.count { it == '}' }
        val warning = if (hasUnbalancedBraces) {
            "Unbalanced braces detected while editing — showing best-effort render"
        } else null

        return PreviewParseResult(
            composables = discovered,
            selectedComposable = selected,
            warningMessage = warning
        )
    }

    private fun findPrimaryInvokedComposable(setContentBody: String): String? {
        val nodes = parseBlock(setContentBody)
        fun walk(list: List<ComposeNode>): String? {
            for (node in list) {
                if (!node.name.endsWith("Theme") && !node.name.startsWith("__") &&
                    node.name !in setOf("Surface", "MaterialTheme", "Box", "Column", "Row")
                ) {
                    return node.name
                }
                walk(node.children)?.let { return it }
            }
            return null
        }
        return walk(nodes)
    }

    private fun containsScaffoldNode(nodes: List<ComposeNode>): Boolean {
        return nodes.any { node ->
            node.name == "Scaffold" ||
                containsScaffoldNode(node.children) ||
                node.branches.any { (_, branchNodes) -> containsScaffoldNode(branchNodes) }
        }
    }

    /**
     * Resolves interactive navigation targets from generated Compose onClick lambdas such as
     * `navController.navigate("DetailScreen")`, `onNavigateToProfile()`, `navigateUp()`, or `DetailScreen()`.
     */
    fun extractNavigationTarget(
        actionSnippet: String,
        knownScreens: List<String>
    ): String? {
        val snippet = actionSnippet.trim()
        if (snippet.isBlank()) return null

        if (snippet.contains("popBackStack") ||
            snippet.contains("navigateUp") ||
            Regex("""\bonBack\s*\(""").containsMatchIn(snippet)
        ) {
            return "__BACK__"
        }

        if (knownScreens.isEmpty()) return null

        // 1. navController.navigate("route") or navigate("route")
        Regex("""navigate\s*\(\s*"([^"]+)"\s*\)""").find(snippet)?.let { match ->
            val rawRoute = match.groupValues[1].substringBefore('/').substringBefore('?').trim()
            matchScreenByRoute(rawRoute, knownScreens)?.let { return it }
        }

        // 2. navigate(Screen.Detail.route) or currentScreen = Screen.Detail
        Regex("""(?:navigate\s*\(|currentScreen\s*=)\s*[A-Za-z0-9_.]*?([A-Z][A-Za-z0-9_]+)(?:\.route)?""").find(snippet)?.let { match ->
            val token = match.groupValues[1]
            matchScreenByRoute(token, knownScreens)?.let { return it }
        }

        // 3. onNavigateToX() / goToX() / openX()
        Regex("""(?:onNavigateTo|navigateTo|goTo|open)([A-Z][A-Za-z0-9_]*)\s*\(""").find(snippet)?.let { match ->
            val targetHint = match.groupValues[1]
            matchScreenByRoute(targetHint, knownScreens)?.let { return it }
        }

        // 4. Direct screen name reference inside snippet (e.g. "DetailScreen()")
        for (screen in knownScreens) {
            if (Regex("""\b${Regex.escape(screen)}\s*\(""").containsMatchIn(snippet)) {
                return screen
            }
        }

        return null
    }

    private fun matchScreenByRoute(routeHint: String, knownScreens: List<String>): String? {
        val cleanHint = routeHint.trim().lowercase().removeSuffix("screen")
        if (cleanHint.isBlank()) return null
        return knownScreens.firstOrNull { it.equals(routeHint, ignoreCase = true) }
            ?: knownScreens.firstOrNull {
                it.lowercase().removeSuffix("screen") == cleanHint
            }
            ?: knownScreens.firstOrNull {
                it.lowercase().contains(cleanHint) || cleanHint.contains(it.lowercase().removeSuffix("screen"))
            }
    }

    private fun extractComposablesFromSource(
        filePath: String,
        source: String
    ): List<PreviewComposableInfo> {
        val result = mutableListOf<PreviewComposableInfo>()
        val headerRegex = Regex("""@Composable\s+(?:private\s+|internal\s+|public\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        var searchIndex = 0

        while (true) {
            val match = headerRegex.find(source, searchIndex) ?: break
            val name = match.groupValues[1]
            val parenStart = match.range.last
            val parenEnd = findMatchingDelimiter(source, parenStart, '(', ')')
            if (parenEnd == -1) {
                searchIndex = match.range.last + 1
                continue
            }

            // Find '{' after parameter list (skipping optional return type)
            val bodyStart = findNextBraceOutsideStrings(source, parenEnd + 1)
            if (bodyStart == -1) {
                searchIndex = parenEnd + 1
                continue
            }

            val bodyEnd = findMatchingDelimiter(source, bodyStart, '{', '}')
            val bodyCode = if (bodyEnd != -1) {
                source.substring(bodyStart + 1, bodyEnd)
            } else {
                source.substring(bodyStart + 1)
            }

            val stateVars = extractStateVars(bodyCode)
            val rootNodes = parseBlock(bodyCode)

            result += PreviewComposableInfo(
                name = name,
                filePath = filePath,
                bodyCode = bodyCode,
                stateVars = stateVars,
                rootNodes = rootNodes
            )

            searchIndex = if (bodyEnd != -1) bodyEnd + 1 else source.length
        }

        return result
    }

    private fun extractSetContentBody(source: String): String? {
        val regex = Regex("""setContent\s*\{""")
        val match = regex.find(source) ?: return null
        val start = match.range.last
        val end = findMatchingDelimiter(source, start, '{', '}')
        return if (end != -1) source.substring(start + 1, end) else source.substring(start + 1)
    }

    private fun extractStateVars(body: String): List<PreviewStateVar> {
        val vars = mutableListOf<PreviewStateVar>()
        val regex = Regex(
            """(?:var|val)\s+([A-Za-z_][A-Za-z0-9_]*)\s+(?:by|=)\s+remember(?:Saveable)?(?:\s*\([^)]*\))?\s*\{\s*mutable(?:(?:Int|Float|Double|Long)?State|Int|Float|Double|Long)?Of\s*\(([^)]*)\)\s*\}"""
        )
        regex.findAll(body).forEach { match ->
            val varName = match.groupValues[1]
            val rawInit = match.groupValues[2].trim()
            val value: Any = when {
                rawInit == "true" -> true
                rawInit == "false" -> false
                rawInit.toIntOrNull() != null -> rawInit.toInt()
                rawInit.removeSuffix("f").removeSuffix("F").toFloatOrNull() != null ->
                    rawInit.removeSuffix("f").removeSuffix("F").toFloat()
                rawInit.startsWith("\"") && rawInit.endsWith("\"") ->
                    rawInit.substring(1, rawInit.length - 1)
                else -> rawInit.trim('"')
            }
            vars += PreviewStateVar(varName, value)
        }
        return vars
    }

    fun parseBlock(code: String): List<ComposeNode> {
        val nodes = mutableListOf<ComposeNode>()
        var i = 0
        val n = code.length

        while (i < n) {
            // Skip whitespace & comments
            i = skipWhitespaceAndComments(code, i)
            if (i >= n) break

            // Handle when (expr) { ... }
            if (code.startsWith("when", i) && (i + 4 < n && (code[i + 4] == '(' || code[i + 4].isWhitespace()))) {
                var cursor = skipWhitespaceAndComments(code, i + 4)
                var subject = ""
                if (cursor < n && code[cursor] == '(') {
                    val pEnd = findMatchingDelimiter(code, cursor, '(', ')')
                    if (pEnd != -1) {
                        subject = code.substring(cursor + 1, pEnd).trim()
                        cursor = skipWhitespaceAndComments(code, pEnd + 1)
                    }
                }
                if (cursor < n && code[cursor] == '{') {
                    val bEnd = findMatchingDelimiter(code, cursor, '{', '}')
                    val blockBody = if (bEnd != -1) code.substring(cursor + 1, bEnd) else code.substring(cursor + 1)
                    val parsedBranches = parseWhenBranches(blockBody)
                    if (parsedBranches.isNotEmpty()) {
                        nodes += ComposeNode(
                            name = "__when__",
                            conditionExpr = subject,
                            branches = parsedBranches
                        )
                    }
                    i = if (bEnd != -1) bEnd + 1 else n
                    continue
                }
            }

            // Handle if (cond) { ... } [else { ... }]
            if (code.startsWith("if", i) && (i + 2 < n && (code[i + 2] == '(' || code[i + 2].isWhitespace()))) {
                val pStart = skipWhitespaceAndComments(code, i + 2)
                if (pStart < n && code[pStart] == '(') {
                    val pEnd = findMatchingDelimiter(code, pStart, '(', ')')
                    if (pEnd != -1) {
                        val condExpr = code.substring(pStart + 1, pEnd).trim()
                        var cursor = skipWhitespaceAndComments(code, pEnd + 1)
                        if (cursor < n && code[cursor] == '{') {
                            val bEnd = findMatchingDelimiter(code, cursor, '{', '}')
                            val thenBody = if (bEnd != -1) code.substring(cursor + 1, bEnd) else code.substring(cursor + 1)
                            val branches = mutableListOf<Pair<String?, List<ComposeNode>>>()
                            branches += condExpr to parseBlock(thenBody)
                            cursor = if (bEnd != -1) skipWhitespaceAndComments(code, bEnd + 1) else n
                            if (cursor < n && code.startsWith("else", cursor) &&
                                (cursor + 4 >= n || !code[cursor + 4].isLetterOrDigit())
                            ) {
                                val afterElse = skipWhitespaceAndComments(code, cursor + 4)
                                if (afterElse < n && code[afterElse] == '{') {
                                    val elseEnd = findMatchingDelimiter(code, afterElse, '{', '}')
                                    val elseBody = if (elseEnd != -1) code.substring(afterElse + 1, elseEnd) else code.substring(afterElse + 1)
                                    branches += null to parseBlock(elseBody)
                                    cursor = if (elseEnd != -1) elseEnd + 1 else n
                                }
                            }
                            nodes += ComposeNode(
                                name = "__if__",
                                conditionExpr = condExpr,
                                branches = branches
                            )
                            i = cursor
                            continue
                        }
                    }
                }
            }

            // Handle repeat(N) { ... }
            if (code.startsWith("repeat", i) && (i + 6 < n && (code[i + 6] == '(' || code[i + 6].isWhitespace()))) {
                val pStart = code.indexOf('(', i)
                if (pStart != -1) {
                    val pEnd = findMatchingDelimiter(code, pStart, '(', ')')
                    if (pEnd != -1) {
                        val count = code.substring(pStart + 1, pEnd).trim().toIntOrNull() ?: 1
                        val bStart = findNextBraceOutsideStrings(code, pEnd + 1)
                        if (bStart != -1) {
                            val bEnd = findMatchingDelimiter(code, bStart, '{', '}')
                            val inner = if (bEnd != -1) code.substring(bStart + 1, bEnd) else code.substring(bStart + 1)
                            val innerNodes = parseBlock(inner)
                            repeat(count.coerceIn(1, 12)) {
                                nodes += innerNodes
                            }
                            i = if (bEnd != -1) bEnd + 1 else n
                            continue
                        }
                    }
                }
            }

            // Handle items(listOf(...)) { item -> ... } or items(5) { ... } inside LazyColumn
            if (code.startsWith("items", i) && i + 5 < n && (code[i + 5] == '(' || code[i + 5].isWhitespace())) {
                val pStart = code.indexOf('(', i)
                if (pStart != -1) {
                    val pEnd = findMatchingDelimiter(code, pStart, '(', ')')
                    if (pEnd != -1) {
                        val itemsArg = code.substring(pStart + 1, pEnd).trim()
                        val bStart = findNextBraceOutsideStrings(code, pEnd + 1)
                        if (bStart != -1) {
                            val bEnd = findMatchingDelimiter(code, bStart, '{', '}')
                            val rawLambda = if (bEnd != -1) code.substring(bStart + 1, bEnd) else code.substring(bStart + 1)
                            val (itemVar, body) = splitLambdaParam(rawLambda)
                            val extractedItems = extractListItems(itemsArg)
                            nodes += ComposeNode(
                                name = "__items__",
                                children = parseBlock(body),
                                listItems = extractedItems,
                                itemVarName = itemVar,
                                repeatCount = extractedItems.size
                            )
                            i = if (bEnd != -1) bEnd + 1 else n
                            continue
                        }
                    }
                }
            }

            // Handle item { ... } inside LazyColumn
            if (code.startsWith("item", i) && i + 4 < n && (code[i + 4] == '{' || code[i + 4].isWhitespace())) {
                val bStart = findNextBraceOutsideStrings(code, i + 4)
                if (bStart != -1 && code.substring(i + 4, bStart).isBlank()) {
                    val bEnd = findMatchingDelimiter(code, bStart, '{', '}')
                    val inner = if (bEnd != -1) code.substring(bStart + 1, bEnd) else code.substring(bStart + 1)
                    nodes += parseBlock(inner)
                    i = if (bEnd != -1) bEnd + 1 else n
                    continue
                }
            }

            // Look for Capitalized Composable Call: Name(...) { ... } or Name { ... }
            val isStartOfIdentifier = i == 0 || (!code[i - 1].isLetterOrDigit() && code[i - 1] != '_' && code[i - 1] != '.')
            if (isStartOfIdentifier && code[i].isUpperCase()) {
                val nameEnd = readIdentifierEnd(code, i)
                val name = code.substring(i, nameEnd)
                var cursor = skipWhitespaceAndComments(code, nameEnd)

                var rawArgs = ""
                if (cursor < n && code[cursor] == '(') {
                    val pEnd = findMatchingDelimiter(code, cursor, '(', ')')
                    if (pEnd != -1) {
                        rawArgs = code.substring(cursor + 1, pEnd)
                        cursor = skipWhitespaceAndComments(code, pEnd + 1)
                    } else {
                        rawArgs = code.substring(cursor + 1)
                        cursor = n
                    }
                }

                var rawLambda = ""
                if (cursor < n && code[cursor] == '{') {
                    val bEnd = findMatchingDelimiter(code, cursor, '{', '}')
                    if (bEnd != -1) {
                        rawLambda = code.substring(cursor + 1, bEnd)
                        cursor = bEnd + 1
                    } else {
                        rawLambda = code.substring(cursor + 1)
                        cursor = n
                    }
                }

                if (rawArgs.isNotEmpty() || rawLambda.isNotEmpty()) {
                    val (positional, named) = parseArguments(rawArgs)
                    val modifierExpr = named["modifier"] ?: positional.firstOrNull { it.startsWith("Modifier") }
                    val parsedModifier = parseModifier(modifierExpr)

                    // Parse named composable slots like topBar = { ... }, floatingActionButton = { ... }, bottomBar = { ... }, icon = { ... }, label = { ... }, title = { ... }
                    val slots = mutableMapOf<String, List<ComposeNode>>()
                    named.forEach { (key, value) ->
                        val trimmedVal = value.trim()
                        if (trimmedVal.startsWith("{") && trimmedVal.endsWith("}")) {
                            val inner = trimmedVal.substring(1, trimmedVal.length - 1)
                            slots[key] = parseBlock(inner)
                        }
                    }

                    // For Scaffold/Surface/etc., strip lambda param like "innerPadding ->"
                    val (_, cleanLambdaBody) = splitLambdaParam(rawLambda)
                    val children = if (cleanLambdaBody.isNotBlank() && !isActionCallbackComposable(name)) {
                        parseBlock(cleanLambdaBody)
                    } else emptyList()

                    nodes += ComposeNode(
                        name = name,
                        positionalArgs = positional,
                        namedArgs = named,
                        modifier = parsedModifier,
                        lambdaBodyRaw = cleanLambdaBody,
                        children = children,
                        namedSlots = slots
                    )
                    i = cursor
                    continue
                }
            }

            // Skip until next line or statement boundary
            i++
        }

        return nodes
    }

    private fun parseWhenBranches(blockBody: String): List<Pair<String?, List<ComposeNode>>> {
        val branches = mutableListOf<Pair<String?, List<ComposeNode>>>()
        var i = 0
        val n = blockBody.length
        while (i < n) {
            i = skipWhitespaceAndComments(blockBody, i)
            if (i >= n) break
            val arrowIdx = findWhenBranchArrow(blockBody, i)
            if (arrowIdx == -1) break
            val condRaw = blockBody.substring(i, arrowIdx).trim()
            val condKey: String? = if (condRaw == "else") null else condRaw
            var cursor = skipWhitespaceAndComments(blockBody, arrowIdx + 2)
            if (cursor < n && blockBody[cursor] == '{') {
                val bEnd = findMatchingDelimiter(blockBody, cursor, '{', '}')
                val body = if (bEnd != -1) blockBody.substring(cursor + 1, bEnd) else blockBody.substring(cursor + 1)
                branches += condKey to parseBlock(body)
                i = if (bEnd != -1) bEnd + 1 else n
            } else {
                val stmtEnd = findStatementEnd(blockBody, cursor)
                val singleStmt = blockBody.substring(cursor, stmtEnd).trim()
                branches += condKey to parseBlock(singleStmt)
                i = stmtEnd + 1
            }
        }
        return branches
    }

    private fun findWhenBranchArrow(text: String, start: Int): Int {
        var depth = 0
        var inStr = false
        for (idx in start until text.length - 1) {
            val c = text[idx]
            if (c == '"' && (idx == 0 || text[idx - 1] != '\\')) inStr = !inStr
            if (!inStr) {
                when (c) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> if (depth > 0) depth--
                    '-' -> if (depth == 0 && text[idx + 1] == '>') return idx
                }
            }
        }
        return -1
    }

    private fun findStatementEnd(text: String, start: Int): Int {
        var depth = 0
        var inStr = false
        for (idx in start until text.length) {
            val c = text[idx]
            if (c == '"' && (idx == 0 || text[idx - 1] != '\\')) inStr = !inStr
            if (!inStr) {
                when (c) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> if (depth > 0) depth--
                    '\n', ';' -> if (depth == 0) return idx
                }
            }
        }
        return text.length
    }

    private fun isActionCallbackComposable(name: String): Boolean =
        name in setOf("IconButton", "Switch", "Checkbox", "Slider") && false

    private fun splitLambdaParam(raw: String): Pair<String, String> {
        val trimmed = raw.trimStart()
        val arrowIdx = findTopLevelArrow(trimmed)
        return if (arrowIdx != -1) {
            val param = trimmed.substring(0, arrowIdx).trim().split(Regex("\\s+")).lastOrNull() ?: "it"
            val body = trimmed.substring(arrowIdx + 2)
            param to body
        } else {
            "it" to raw
        }
    }

    private fun findTopLevelArrow(text: String): Int {
        var depth = 0
        var inStr = false
        for (i in 0 until text.length - 1) {
            val c = text[i]
            if (c == '"' && (i == 0 || text[i - 1] != '\\')) inStr = !inStr
            if (!inStr) {
                when (c) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> depth--
                    '\n' -> if (depth == 0) return -1
                    '-' -> if (depth == 0 && text[i + 1] == '>') return i
                }
            }
        }
        return -1
    }

    private fun extractListItems(arg: String): List<String> {
        val listOfMatch = Regex("""(?:listOf|mutableStateListOf)\s*\(([\s\S]*)\)""").find(arg)
        if (listOfMatch != null) {
            val inner = listOfMatch.groupValues[1]
            val items = splitTopLevelArgs(inner).map { it.trim().trim('"') }.filter { it.isNotBlank() }
            if (items.isNotEmpty()) return items
        }
        val count = arg.toIntOrNull()
        if (count != null && count > 0) {
            return (1..count.coerceAtMost(10)).map { "Item $it" }
        }
        return listOf("Sample Item 1", "Sample Item 2", "Sample Item 3")
    }

    fun parseArguments(rawArgs: String): Pair<List<String>, Map<String, String>> {
        if (rawArgs.isBlank()) return emptyList<String>() to emptyMap()
        val parts = splitTopLevelArgs(rawArgs)
        val positional = mutableListOf<String>()
        val named = mutableMapOf<String, String>()

        for (part in parts) {
            val eqIdx = findTopLevelEquals(part)
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                if (key.all { it.isLetterOrDigit() || it == '_' }) {
                    named[key] = value
                } else {
                    positional += part.trim()
                }
            } else {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) positional += trimmed
            }
        }
        return positional to named
    }

    private fun splitTopLevelArgs(text: String): List<String> {
        val result = mutableListOf<String>()
        var parenDepth = 0
        var braceDepth = 0
        var bracketDepth = 0
        var inString = false
        var escaped = false
        var start = 0

        for (i in text.indices) {
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
                when (ch) {
                    '(' -> parenDepth++
                    ')' -> parenDepth--
                    '{' -> braceDepth++
                    '}' -> braceDepth--
                    '[' -> bracketDepth++
                    ']' -> bracketDepth--
                    ',' -> if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                        result += text.substring(start, i)
                        start = i + 1
                    }
                }
            }
        }
        if (start < text.length) {
            result += text.substring(start)
        }
        return result
    }

    private fun findTopLevelEquals(text: String): Int {
        var parenDepth = 0
        var braceDepth = 0
        var inString = false
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '"' && (i == 0 || text[i - 1] != '\\')) inString = !inString
            if (!inString) {
                when (ch) {
                    '(' -> parenDepth++
                    ')' -> parenDepth--
                    '{' -> braceDepth++
                    '}' -> braceDepth--
                    '=' -> if (parenDepth == 0 && braceDepth == 0) {
                        val next = text.getOrNull(i + 1)
                        val prev = text.getOrNull(i - 1)
                        if (next != '=' && prev != '!' && prev != '<' && prev != '>') {
                            return i
                        }
                    }
                }
            }
        }
        return -1
    }

    fun parseModifier(expr: String?): ParsedModifier {
        if (expr.isNullOrBlank()) return ParsedModifier()
        var mod = ParsedModifier()

        if (expr.contains(".fillMaxSize")) mod = mod.copy(fillMaxSize = true)
        if (expr.contains(".fillMaxWidth")) mod = mod.copy(fillMaxWidth = true)
        if (expr.contains(".fillMaxHeight")) mod = mod.copy(fillMaxHeight = true)

        Regex("""\.padding\(([^)]+)\)""").findAll(expr).forEach { m ->
            val arg = m.groupValues[1].trim()
            val simpleDp = Regex("""^(\d+(?:\.\d+)?)\.dp$""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
            if (simpleDp != null) {
                mod = mod.copy(paddingAllDp = simpleDp)
            } else {
                val h = Regex("""horizontal\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                val v = Regex("""vertical\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                val all = Regex("""all\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                val startDp = Regex("""start\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                val topDp = Regex("""top\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                val endDp = Regex("""end\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                val bottomDp = Regex("""bottom\s*=\s*(\d+(?:\.\d+)?)\.dp""").find(arg)?.groupValues?.get(1)?.toFloatOrNull()
                mod = mod.copy(
                    paddingHorizontalDp = h ?: mod.paddingHorizontalDp,
                    paddingVerticalDp = v ?: mod.paddingVerticalDp,
                    paddingAllDp = all ?: mod.paddingAllDp,
                    paddingStartDp = startDp ?: mod.paddingStartDp,
                    paddingTopDp = topDp ?: mod.paddingTopDp,
                    paddingEndDp = endDp ?: mod.paddingEndDp,
                    paddingBottomDp = bottomDp ?: mod.paddingBottomDp
                )
            }
        }

        Regex("""\.size\((\d+(?:\.\d+)?)\.dp\)""").find(expr)?.let {
            mod = mod.copy(sizeDp = it.groupValues[1].toFloatOrNull())
        }
        Regex("""\.width\((\d+(?:\.\d+)?)\.dp\)""").find(expr)?.let {
            mod = mod.copy(widthDp = it.groupValues[1].toFloatOrNull())
        }
        Regex("""\.height\((\d+(?:\.\d+)?)\.dp\)""").find(expr)?.let {
            mod = mod.copy(heightDp = it.groupValues[1].toFloatOrNull())
        }
        Regex("""\.weight\((\d+(?:\.\d+)?)f?\)""").find(expr)?.let {
            mod = mod.copy(weight = it.groupValues[1].toFloatOrNull())
        }
        Regex("""\.background\(([^)]+)\)""").find(expr)?.let {
            mod = mod.copy(backgroundColorExpr = it.groupValues[1].trim())
        }
        Regex("""RoundedCornerShape\((\d+(?:\.\d+)?)\.dp\)""").find(expr)?.let {
            mod = mod.copy(clipCornerDp = it.groupValues[1].toFloatOrNull())
        }
        if (expr.contains("CircleShape")) {
            mod = mod.copy(isCircleClip = true)
        }
        if (expr.contains(".clickable")) {
            val clickCode = extractClickableLambda(expr)
            mod = mod.copy(hasClickable = true, onClickCode = clickCode)
        }
        return mod
    }

    private fun extractClickableLambda(expr: String): String? {
        val idx = expr.indexOf(".clickable")
        if (idx == -1) return null
        var cursor = skipWhitespaceAndComments(expr, idx + ".clickable".length)
        if (cursor < expr.length && expr[cursor] == '(') {
            val pEnd = findMatchingDelimiter(expr, cursor, '(', ')')
            if (pEnd != -1) {
                val argsText = expr.substring(cursor + 1, pEnd)
                val braceInArgs = argsText.indexOf('{')
                if (braceInArgs != -1) {
                    val bEnd = findMatchingDelimiter(argsText, braceInArgs, '{', '}')
                    if (bEnd != -1) {
                        return argsText.substring(braceInArgs + 1, bEnd).trim()
                    }
                }
                cursor = skipWhitespaceAndComments(expr, pEnd + 1)
            }
        }
        if (cursor < expr.length && expr[cursor] == '{') {
            val bEnd = findMatchingDelimiter(expr, cursor, '{', '}')
            if (bEnd != -1) {
                return expr.substring(cursor + 1, bEnd).trim()
            }
        }
        return null
    }

    fun resolveColorExpr(expr: String?): Color? {
        if (expr.isNullOrBlank()) return null
        val trimmed = expr.trim()

        Regex("""Color\(\s*0x([0-9A-Fa-f]{6,8})\s*\)""").find(trimmed)?.let { match ->
            val hex = match.groupValues[1]
            val value = hex.toLong(16)
            val argb = if (hex.length == 6) (0xFF000000L or value) else value
            return Color(argb)
        }

        return when {
            trimmed.endsWith("Color.Red") -> Color(0xFFEF4444)
            trimmed.endsWith("Color.Blue") -> Color(0xFF3B82F6)
            trimmed.endsWith("Color.Green") -> Color(0xFF22C55E)
            trimmed.endsWith("Color.Yellow") -> Color(0xFFEAB308)
            trimmed.endsWith("Color.Cyan") -> Color(0xFF06B6D4)
            trimmed.endsWith("Color.Magenta") -> Color(0xFFD946EF)
            trimmed.endsWith("Color.White") -> Color.White
            trimmed.endsWith("Color.Black") -> Color.Black
            trimmed.endsWith("Color.Gray") || trimmed.endsWith("Color.DarkGray") -> Color(0xFF64748B)
            trimmed.endsWith("Color.LightGray") -> Color(0xFFCBD5E1)
            trimmed.endsWith("Color.Transparent") -> Color.Transparent
            else -> null
        }
    }

    private fun skipWhitespaceAndComments(text: String, start: Int): Int {
        var i = start
        val n = text.length
        while (i < n) {
            when {
                text[i].isWhitespace() -> i++
                text.startsWith("//", i) -> {
                    val nl = text.indexOf('\n', i)
                    i = if (nl == -1) n else nl + 1
                }
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    i = if (end == -1) n else end + 2
                }
                else -> return i
            }
        }
        return i
    }

    private fun readIdentifierEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) {
            i++
        }
        return i
    }

    private fun findMatchingDelimiter(text: String, openIdx: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in openIdx until text.length) {
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
                if (ch == open) depth++
                else if (ch == close) {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun findNextBraceOutsideStrings(text: String, start: Int): Int {
        var inString = false
        for (i in start until text.length) {
            val ch = text[i]
            if (ch == '"' && (i == 0 || text[i - 1] != '\\')) inString = !inString
            if (!inString) {
                if (ch == '{') return i
                if (ch == '\n' && text.substring(start, i).trim().isNotEmpty() && !text.substring(start, i).contains(":")) {
                    return -1
                }
            }
        }
        return -1
    }
}
