package com.example.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LivePreviewDeviceFrame(
    composableInfo: PreviewComposableInfo?,
    allComposables: List<PreviewComposableInfo>,
    darkPreview: Boolean,
    appName: String,
    customColorSchemes: Pair<ColorScheme, ColorScheme>? = null,
    syntaxErrorMessage: String? = null,
    onFixWithAi: (() -> Unit)? = null,
    onNavigateScreen: ((String) -> Unit)? = null,
    canNavigateBack: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    refreshNonce: Int = 0,
    sharedStateStore: SnapshotStateMap<String, Any>? = null,
    fontScale: Float = 1.0f,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val previewColorScheme = when {
        customColorSchemes != null -> if (darkPreview) customColorSchemes.second else customColorSchemes.first
        darkPreview -> darkColorScheme(
            primary = Color(0xFF38BDF8),
            secondary = Color(0xFF34D399),
            background = Color(0xFF0B1120),
            surface = Color(0xFF131C31),
            surfaceVariant = Color(0xFF1E293B)
        )
        else -> lightColorScheme(
            primary = Color(0xFF0284C7),
            secondary = Color(0xFF059669),
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            surfaceVariant = Color(0xFFE2E8F0)
        )
    }

    val localStateStore = remember(composableInfo?.name, refreshNonce) {
        mutableStateMapOf<String, Any>()
    }
    val stateStore = sharedStateStore ?: localStateStore

    LaunchedEffect(composableInfo?.stateVars, refreshNonce) {
        composableInfo?.stateVars?.forEach { v ->
            val key = v.name
            if (refreshNonce > 0 || !stateStore.containsKey(key)) {
                stateStore[key] = v.initialValue
            }
        }
    }

    val knownScreens = remember(allComposables) {
        allComposables.map { it.name }
    }

    val handleAction: (String) -> Unit = { snippet ->
        executeActionSnippet(snippet, stateStore)
        val navTarget = ComposePreviewEngine.extractNavigationTarget(snippet, knownScreens)
        when {
            navTarget == "__BACK__" -> onNavigateBack?.invoke()
            navTarget != null -> onNavigateScreen?.invoke(navTarget)
        }
    }

    val parentDensity = LocalDensity.current
    val effectiveDensity = remember(parentDensity.density, fontScale) {
        Density(density = parentDensity.density, fontScale = fontScale)
    }

    val bezelRadius = if (isLandscape) 22.dp else 32.dp
    val innerRadius = if (isLandscape) 18.dp else 27.dp
    val statusVerticalPadding = if (isLandscape) 4.dp else 7.dp

    CompositionLocalProvider(LocalDensity provides effectiveDensity) {
        // Outer realistic device bezel
        Surface(
            modifier = modifier
                .border(
                    width = 4.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(bezelRadius)
                ),
            shape = RoundedCornerShape(bezelRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                shape = RoundedCornerShape(innerRadius),
                color = previewColorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Simulated Phone Status Bar & Dynamic Camera Cutout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(previewColorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = statusVerticalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Text(
                        text = "9:41",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = previewColorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(previewColorScheme.onSurface.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(previewColorScheme.primary.copy(alpha = 0.55f))
                        )
                    }
                    Text(
                        text = "5G  100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = previewColorScheme.onSurfaceVariant
                    )
                }

                // In-frame simulated navigation breadcrumb bar when navigated inside preview
                if (canNavigateBack && composableInfo != null && syntaxErrorMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(previewColorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateBack?.invoke() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back in preview",
                                tint = previewColorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back",
                                style = MaterialTheme.typography.labelSmall,
                                color = previewColorScheme.primary
                            )
                        }
                        Text(
                            text = "@${composableInfo.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = previewColorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Live Rendered Generated App Content
                MaterialTheme(colorScheme = previewColorScheme) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(previewColorScheme.background)
                    ) {
                        when {
                            !syntaxErrorMessage.isNullOrBlank() -> {
                                PreviewErrorRecoveryCard(
                                    title = "Preview Paused — Syntax Issue",
                                    message = syntaxErrorMessage,
                                    onFixWithAi = onFixWithAi
                                )
                            }
                            composableInfo == null || composableInfo.rootNodes.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = previewColorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = previewColorScheme.onBackground,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Add or fix a @Composable screen function in Kotlin to render an interactive live preview.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = previewColorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    if (onFixWithAi != null) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = onFixWithAi,
                                            modifier = Modifier.testTag("preview_fix_with_ai_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoFixHigh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Fix with AI")
                                        }
                                    }
                                }
                            }
                            else -> {
                                val hasScaffold = composableInfo.rootNodes.any { it.name == "Scaffold" }
                                if (hasScaffold) {
                                    RenderNodes(
                                        nodes = composableInfo.rootNodes,
                                        allComposables = allComposables,
                                        stateStore = stateStore,
                                        loopVars = emptyMap(),
                                        onAction = handleAction,
                                        depth = 0
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(4.dp)
                                    ) {
                                        RenderNodes(
                                            nodes = composableInfo.rootNodes,
                                            allComposables = allComposables,
                                            stateStore = stateStore,
                                            loopVars = emptyMap(),
                                            onAction = handleAction,
                                            depth = 0
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Simulated System Gesture Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(previewColorScheme.surface)
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(previewColorScheme.onSurface.copy(alpha = 0.28f))
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun PreviewErrorRecoveryCard(
    title: String,
    message: String,
    onFixWithAi: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("preview_syntax_error_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Syntax error in preview",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(26.dp)
                    )
                }
                Text(
                    text = "Issue detected — Auto-fixing with AI...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                if (onFixWithAi != null) {
                    Button(
                        onClick = onFixWithAi,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_fix_with_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fix with AI")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderNodes(
    nodes: List<ComposeNode>,
    allComposables: List<PreviewComposableInfo>,
    stateStore: MutableMap<String, Any>,
    loopVars: Map<String, String>,
    onAction: (String) -> Unit,
    depth: Int,
    columnScope: ColumnScope? = null,
    rowScope: RowScope? = null
) {
    if (depth > 8) return
    nodes.forEach { node ->
        if (node.name == "__items__") {
            node.listItems.forEach { itemValue ->
                val childLoopVars = loopVars + (node.itemVarName to itemValue) + ("it" to itemValue)
                RenderNodes(
                    nodes = node.children,
                    allComposables = allComposables,
                    stateStore = stateStore,
                    loopVars = childLoopVars,
                    onAction = onAction,
                    depth = depth + 1,
                    columnScope = columnScope,
                    rowScope = rowScope
                )
            }
        } else {
            RenderNode(
                node = node,
                allComposables = allComposables,
                stateStore = stateStore,
                loopVars = loopVars,
                onAction = onAction,
                depth = depth,
                columnScope = columnScope,
                rowScope = rowScope
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderNode(
    node: ComposeNode,
    allComposables: List<PreviewComposableInfo>,
    stateStore: MutableMap<String, Any>,
    loopVars: Map<String, String>,
    onAction: (String) -> Unit,
    depth: Int,
    columnScope: ColumnScope? = null,
    rowScope: RowScope? = null
) {
    val modifier = buildComposeModifier(
        parsed = node.modifier,
        onAction = onAction,
        columnScope = columnScope,
        rowScope = rowScope
    )

    when (node.name) {
        "Scaffold" -> {
            val topBarNodes = node.namedSlots["topBar"].orEmpty()
            val bottomBarNodes = node.namedSlots["bottomBar"].orEmpty()
            val fabNodes = node.namedSlots["floatingActionButton"].orEmpty()
            val containerColor = resolveThemeOrLiteralColor(node.namedArgs["containerColor"])
                ?: MaterialTheme.colorScheme.background

            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = containerColor,
                topBar = {
                    if (topBarNodes.isNotEmpty()) {
                        RenderNodes(topBarNodes, allComposables, stateStore, loopVars, onAction, depth + 1)
                    }
                },
                bottomBar = {
                    if (bottomBarNodes.isNotEmpty()) {
                        RenderNodes(bottomBarNodes, allComposables, stateStore, loopVars, onAction, depth + 1)
                    }
                },
                floatingActionButton = {
                    if (fabNodes.isNotEmpty()) {
                        RenderNodes(fabNodes, allComposables, stateStore, loopVars, onAction, depth + 1)
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, columnScope = this)
                }
            }
        }

        "TopAppBar", "CenterAlignedTopAppBar" -> {
            val titleNodes = node.namedSlots["title"].orEmpty()
            val navNodes = node.namedSlots["navigationIcon"].orEmpty()
            val actionNodes = node.namedSlots["actions"].orEmpty()
            val colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
            if (node.name == "CenterAlignedTopAppBar") {
                CenterAlignedTopAppBar(
                    title = { RenderNodes(titleNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    navigationIcon = { RenderNodes(navNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    actions = { RenderNodes(actionNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    colors = colors
                )
            } else {
                TopAppBar(
                    title = { RenderNodes(titleNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    navigationIcon = { RenderNodes(navNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    actions = { RenderNodes(actionNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    colors = colors
                )
            }
        }

        "NavigationBar" -> {
            NavigationBar(modifier = modifier) {
                RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, rowScope = this)
            }
        }

        "NavigationBarItem" -> {
            val selected = evaluateBooleanExpr(node.namedArgs["selected"], stateStore) ?: false
            val iconNodes = node.namedSlots["icon"].orEmpty()
            val labelNodes = node.namedSlots["label"].orEmpty()
            val onClickBody = node.namedArgs["onClick"] ?: node.lambdaBodyRaw
            if (rowScope != null) {
                with(rowScope) {
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onAction(onClickBody) },
                        icon = { RenderNodes(iconNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                        label = if (labelNodes.isNotEmpty()) {
                            { RenderNodes(labelNodes, allComposables, stateStore, loopVars, onAction, depth + 1) }
                        } else null
                    )
                }
            }
        }

        "FloatingActionButton", "ExtendedFloatingActionButton" -> {
            val onClickBody = node.namedArgs["onClick"] ?: ""
            if (node.name == "ExtendedFloatingActionButton") {
                ExtendedFloatingActionButton(
                    onClick = { onAction(onClickBody) },
                    modifier = modifier
                ) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, rowScope = this)
                }
            } else {
                FloatingActionButton(
                    onClick = { onAction(onClickBody) },
                    modifier = modifier
                ) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1)
                }
            }
        }

        "Column" -> {
            val vArrangement = parseVerticalArrangement(node.namedArgs["verticalArrangement"])
            val hAlignment = parseHorizontalAlignment(node.namedArgs["horizontalAlignment"])
            Column(
                modifier = modifier,
                verticalArrangement = vArrangement,
                horizontalAlignment = hAlignment
            ) {
                RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, columnScope = this)
            }
        }

        "LazyColumn" -> {
            val vArrangement = parseVerticalArrangement(node.namedArgs["verticalArrangement"])
            val hAlignment = parseHorizontalAlignment(node.namedArgs["horizontalAlignment"])
            LazyColumn(
                modifier = modifier.height(340.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = vArrangement,
                horizontalAlignment = hAlignment
            ) {
                item {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1)
                }
            }
        }

        "Row" -> {
            val hArrangement = parseHorizontalArrangement(node.namedArgs["horizontalArrangement"])
            val vAlignment = parseVerticalAlignment(node.namedArgs["verticalAlignment"])
            Row(
                modifier = modifier,
                horizontalArrangement = hArrangement,
                verticalAlignment = vAlignment
            ) {
                RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, rowScope = this)
            }
        }

        "Box" -> {
            val alignment = parseBoxAlignment(node.namedArgs["contentAlignment"])
            Box(modifier = modifier, contentAlignment = alignment) {
                RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1)
            }
        }

        "Surface" -> {
            val color = resolveThemeOrLiteralColor(node.namedArgs["color"]) ?: MaterialTheme.colorScheme.surface
            Surface(modifier = modifier, color = color, shape = RoundedCornerShape(12.dp)) {
                RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1)
            }
        }

        "Card", "ElevatedCard", "OutlinedCard" -> {
            val shape = RoundedCornerShape(14.dp)
            when (node.name) {
                "ElevatedCard" -> ElevatedCard(modifier = modifier, shape = shape) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, columnScope = this)
                }
                "OutlinedCard" -> OutlinedCard(
                    modifier = modifier,
                    shape = shape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, columnScope = this)
                }
                else -> Card(
                    modifier = modifier,
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, columnScope = this)
                }
            }
        }

        "Text" -> {
            val rawText = node.namedArgs["text"] ?: node.positionalArgs.firstOrNull() ?: "Text"
            val resolvedText = evaluateTextExpr(rawText, stateStore, loopVars)
            val fontSize = Regex("""(\d+)\.sp""").find(node.namedArgs["fontSize"] ?: "")
                ?.groupValues?.get(1)?.toIntOrNull()?.sp
            val fontWeight = when {
                (node.namedArgs["fontWeight"] ?: "").contains("Bold") -> FontWeight.Bold
                (node.namedArgs["fontWeight"] ?: "").contains("SemiBold") -> FontWeight.SemiBold
                (node.namedArgs["fontWeight"] ?: "").contains("Medium") -> FontWeight.Medium
                else -> null
            }
            val color = resolveThemeOrLiteralColor(node.namedArgs["color"]) ?: Color.Unspecified
            val baseStyle = resolveTextStyle(node.namedArgs["style"])
            val finalStyle = if (fontSize != null) baseStyle.copy(fontSize = fontSize) else baseStyle

            Text(
                text = resolvedText,
                modifier = modifier,
                style = finalStyle,
                fontWeight = fontWeight,
                color = color,
                maxLines = node.namedArgs["maxLines"]?.toIntOrNull() ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        "Button", "OutlinedButton", "TextButton", "FilledTonalButton" -> {
            val onClickBody = node.namedArgs["onClick"] ?: node.positionalArgs.firstOrNull() ?: ""
            val content: @Composable RowScope.() -> Unit = {
                if (node.children.isNotEmpty()) {
                    RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1, rowScope = this)
                } else {
                    Text(node.name)
                }
            }
            when (node.name) {
                "OutlinedButton" -> OutlinedButton(
                    onClick = { onAction(onClickBody) },
                    modifier = modifier,
                    content = content
                )
                "TextButton" -> TextButton(
                    onClick = { onAction(onClickBody) },
                    modifier = modifier,
                    content = content
                )
                "FilledTonalButton" -> FilledTonalButton(
                    onClick = { onAction(onClickBody) },
                    modifier = modifier,
                    content = content
                )
                else -> Button(
                    onClick = { onAction(onClickBody) },
                    modifier = modifier,
                    content = content
                )
            }
        }

        "IconButton" -> {
            val onClickBody = node.namedArgs["onClick"] ?: ""
            IconButton(
                onClick = { onAction(onClickBody) },
                modifier = modifier
            ) {
                RenderNodes(node.children, allComposables, stateStore, loopVars, onAction, depth + 1)
            }
        }

        "Icon" -> {
            val iconExpr = node.namedArgs["imageVector"] ?: node.positionalArgs.firstOrNull() ?: "Star"
            val vector = resolveMaterialIcon(iconExpr)
            val tint = resolveThemeOrLiteralColor(node.namedArgs["tint"]) ?: MaterialTheme.colorScheme.onSurface
            Icon(
                imageVector = vector,
                contentDescription = node.namedArgs["contentDescription"]?.trim('"'),
                modifier = modifier,
                tint = tint
            )
        }

        "OutlinedTextField", "TextField" -> {
            val valueVar = node.namedArgs["value"]?.trim() ?: ""
            val currentVal = stateStore[valueVar]?.toString()
                ?: evaluateTextExpr(valueVar, stateStore, loopVars)
            val labelNodes = node.namedSlots["label"].orEmpty()
            val placeholderNodes = node.namedSlots["placeholder"].orEmpty()

            if (node.name == "OutlinedTextField") {
                OutlinedTextField(
                    value = currentVal,
                    onValueChange = { newVal ->
                        if (valueVar.isNotBlank()) {
                            stateStore[valueVar] = newVal
                        }
                    },
                    modifier = modifier,
                    label = if (labelNodes.isNotEmpty()) {
                        { RenderNodes(labelNodes, allComposables, stateStore, loopVars, onAction, depth + 1) }
                    } else null,
                    placeholder = if (placeholderNodes.isNotEmpty()) {
                        { RenderNodes(placeholderNodes, allComposables, stateStore, loopVars, onAction, depth + 1) }
                    } else null,
                    singleLine = true
                )
            } else {
                TextField(
                    value = currentVal,
                    onValueChange = { newVal ->
                        if (valueVar.isNotBlank()) {
                            stateStore[valueVar] = newVal
                        }
                    },
                    modifier = modifier,
                    label = if (labelNodes.isNotEmpty()) {
                        { RenderNodes(labelNodes, allComposables, stateStore, loopVars, onAction, depth + 1) }
                    } else null,
                    singleLine = true
                )
            }
        }

        "Switch" -> {
            val checkedVar = node.namedArgs["checked"]?.trim() ?: ""
            val isChecked = evaluateBooleanExpr(checkedVar, stateStore) ?: false
            Switch(
                checked = isChecked,
                onCheckedChange = { newVal ->
                    if (checkedVar.isNotBlank()) stateStore[checkedVar] = newVal
                },
                modifier = modifier
            )
        }

        "Checkbox" -> {
            val checkedVar = node.namedArgs["checked"]?.trim() ?: ""
            val isChecked = evaluateBooleanExpr(checkedVar, stateStore) ?: false
            Checkbox(
                checked = isChecked,
                onCheckedChange = { newVal ->
                    if (checkedVar.isNotBlank()) stateStore[checkedVar] = newVal
                },
                modifier = modifier
            )
        }

        "Slider" -> {
            val valueVar = node.namedArgs["value"]?.trim() ?: ""
            val current = (stateStore[valueVar] as? Number)?.toFloat()
                ?: valueVar.removeSuffix("f").toFloatOrNull()
                ?: 0.5f
            Slider(
                value = current.coerceIn(0f, 1f),
                onValueChange = { newVal ->
                    if (valueVar.isNotBlank()) stateStore[valueVar] = newVal
                },
                modifier = modifier
            )
        }

        "AssistChip", "FilterChip" -> {
            val labelNodes = node.namedSlots["label"].orEmpty()
            val selected = evaluateBooleanExpr(node.namedArgs["selected"], stateStore) ?: false
            val onClickBody = node.namedArgs["onClick"] ?: ""
            if (node.name == "FilterChip") {
                FilterChip(
                    selected = selected,
                    onClick = { onAction(onClickBody) },
                    label = { RenderNodes(labelNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    modifier = modifier
                )
            } else {
                AssistChip(
                    onClick = { onAction(onClickBody) },
                    label = { RenderNodes(labelNodes, allComposables, stateStore, loopVars, onAction, depth + 1) },
                    modifier = modifier
                )
            }
        }

        "LinearProgressIndicator" -> {
            LinearProgressIndicator(modifier = modifier)
        }

        "CircularProgressIndicator" -> {
            CircularProgressIndicator(modifier = modifier)
        }

        "HorizontalDivider", "Divider" -> {
            HorizontalDivider(modifier = modifier)
        }

        "Spacer" -> {
            Spacer(modifier = modifier)
        }

        else -> {
            // Look up custom @Composable function in project
            val customComposable = allComposables.firstOrNull { it.name == node.name }
            if (customComposable != null && !node.name.endsWith("Theme")) {
                RenderNodes(
                    nodes = customComposable.rootNodes,
                    allComposables = allComposables,
                    stateStore = stateStore,
                    loopVars = loopVars,
                    onAction = onAction,
                    depth = depth + 1,
                    columnScope = columnScope,
                    rowScope = rowScope
                )
            } else if (node.children.isNotEmpty()) {
                // Theme wrapper like MyAppTheme { ... }
                RenderNodes(
                    nodes = node.children,
                    allComposables = allComposables,
                    stateStore = stateStore,
                    loopVars = loopVars,
                    onAction = onAction,
                    depth = depth + 1,
                    columnScope = columnScope,
                    rowScope = rowScope
                )
            }
        }
    }
}

private fun buildComposeModifier(
    parsed: ParsedModifier,
    onAction: (String) -> Unit,
    columnScope: ColumnScope?,
    rowScope: RowScope?
): Modifier {
    var m: Modifier = Modifier
    if (parsed.fillMaxSize) m = m.fillMaxSize()
    if (parsed.fillMaxWidth) m = m.fillMaxWidth()
    if (parsed.fillMaxHeight) m = m.fillMaxHeight()
    parsed.sizeDp?.let { m = m.size(it.dp) }
    parsed.widthDp?.let { m = m.width(it.dp) }
    parsed.heightDp?.let { m = m.height(it.dp) }
    if (parsed.weight != null) {
        if (columnScope != null) {
            m = with(columnScope) { m.weight(parsed.weight) }
        } else if (rowScope != null) {
            m = with(rowScope) { m.weight(parsed.weight) }
        }
    }
    parsed.clipCornerDp?.let { m = m.clip(RoundedCornerShape(it.dp)) }
    ComposePreviewEngine.resolveColorExpr(parsed.backgroundColorExpr)?.let { color ->
        m = m.background(color)
    }
    parsed.paddingAllDp?.let { m = m.padding(it.dp) }
    if (parsed.paddingHorizontalDp != null || parsed.paddingVerticalDp != null) {
        m = m.padding(
            horizontal = (parsed.paddingHorizontalDp ?: 0f).dp,
            vertical = (parsed.paddingVerticalDp ?: 0f).dp
        )
    }
    if (parsed.hasClickable) {
        m = m.clickable {
            parsed.onClickCode?.let { onAction(it) }
        }
    }
    return m
}

private fun evaluateTextExpr(
    raw: String,
    stateStore: Map<String, Any>,
    loopVars: Map<String, String>
): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
        var text = trimmed.substring(1, trimmed.length - 1)
        // Substitute ${var} and $var templates
        Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""").findAll(text).forEach { m ->
            val key = m.groupValues[1]
            val value = stateStore[key]?.toString() ?: loopVars[key] ?: ""
            if (value.isNotEmpty() || stateStore.containsKey(key) || loopVars.containsKey(key)) {
                text = text.replace(m.value, value)
            }
        }
        Regex("""\$([A-Za-z_][A-Za-z0-9_]*)""").findAll(text).forEach { m ->
            val key = m.groupValues[1]
            val value = stateStore[key]?.toString() ?: loopVars[key]
            if (value != null) {
                text = text.replace(m.value, value)
            }
        }
        return text.replace("\\n", "\n")
    }
    stateStore[trimmed]?.let { return it.toString() }
    loopVars[trimmed]?.let { return it }
    return trimmed.trim('"')
}

private fun evaluateBooleanExpr(expr: String?, stateStore: Map<String, Any>): Boolean? {
    val clean = expr?.trim() ?: return null
    if (clean == "true") return true
    if (clean == "false") return false
    if (clean.startsWith("!")) {
        val v = stateStore[clean.drop(1).trim()] as? Boolean
        if (v != null) return !v
    }
    Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*(==|!=)\s*(.+)$""").find(clean)?.let { m ->
        val lhsKey = m.groupValues[1]
        val op = m.groupValues[2]
        val rhsRaw = m.groupValues[3].trim().trim('"')
        val curVal = stateStore[lhsKey]?.toString() ?: return null
        val isEq = curVal == rhsRaw
        return if (op == "==") isEq else !isEq
    }
    return stateStore[clean] as? Boolean
}

private fun executeActionSnippet(rawSnippet: String, stateStore: MutableMap<String, Any>) {
    val snippet = rawSnippet.trim().removePrefix("{").removeSuffix("}").trim()
    if (snippet.isBlank()) return

    // Handle var++ / var--
    Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*\+\+""").findAll(snippet).forEach { m ->
        val key = m.groupValues[1]
        val cur = (stateStore[key] as? Int) ?: 0
        stateStore[key] = cur + 1
    }
    Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*--""").findAll(snippet).forEach { m ->
        val key = m.groupValues[1]
        val cur = (stateStore[key] as? Int) ?: 0
        stateStore[key] = cur - 1
    }
    // Handle toggle: flag = !flag
    Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*!\s*\1""").findAll(snippet).forEach { m ->
        val key = m.groupValues[1]
        val cur = (stateStore[key] as? Boolean) ?: false
        stateStore[key] = !cur
    }
    // Handle direct assignment: count = 0 / text = "Hello" / flag = true
    Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^\n;]+)""").findAll(snippet).forEach { m ->
        val key = m.groupValues[1]
        val rhs = m.groupValues[2].trim()
        when {
            rhs == "true" -> stateStore[key] = true
            rhs == "false" -> stateStore[key] = false
            rhs.toIntOrNull() != null -> stateStore[key] = rhs.toInt()
            rhs.startsWith("\"") && rhs.endsWith("\"") ->
                stateStore[key] = rhs.substring(1, rhs.length - 1)
        }
    }
}

@Composable
private fun resolveThemeOrLiteralColor(expr: String?): Color? {
    if (expr.isNullOrBlank()) return null
    ComposePreviewEngine.resolveColorExpr(expr)?.let { return it }
    val trimmed = expr.trim()
    return when {
        trimmed.endsWith(".primary") -> MaterialTheme.colorScheme.primary
        trimmed.endsWith(".onPrimary") -> MaterialTheme.colorScheme.onPrimary
        trimmed.endsWith(".primaryContainer") -> MaterialTheme.colorScheme.primaryContainer
        trimmed.endsWith(".secondary") -> MaterialTheme.colorScheme.secondary
        trimmed.endsWith(".tertiary") -> MaterialTheme.colorScheme.tertiary
        trimmed.endsWith(".background") -> MaterialTheme.colorScheme.background
        trimmed.endsWith(".surface") -> MaterialTheme.colorScheme.surface
        trimmed.endsWith(".surfaceVariant") -> MaterialTheme.colorScheme.surfaceVariant
        trimmed.endsWith(".onSurface") -> MaterialTheme.colorScheme.onSurface
        trimmed.endsWith(".onSurfaceVariant") -> MaterialTheme.colorScheme.onSurfaceVariant
        trimmed.endsWith(".error") -> MaterialTheme.colorScheme.error
        else -> null
    }
}

@Composable
private fun resolveTextStyle(expr: String?): TextStyle {
    val clean = expr?.trim() ?: return MaterialTheme.typography.bodyMedium
    return when {
        clean.endsWith(".displayLarge") -> MaterialTheme.typography.displayLarge
        clean.endsWith(".displayMedium") -> MaterialTheme.typography.displayMedium
        clean.endsWith(".displaySmall") -> MaterialTheme.typography.displaySmall
        clean.endsWith(".headlineLarge") -> MaterialTheme.typography.headlineLarge
        clean.endsWith(".headlineMedium") -> MaterialTheme.typography.headlineMedium
        clean.endsWith(".headlineSmall") -> MaterialTheme.typography.headlineSmall
        clean.endsWith(".titleLarge") -> MaterialTheme.typography.titleLarge
        clean.endsWith(".titleMedium") -> MaterialTheme.typography.titleMedium
        clean.endsWith(".titleSmall") -> MaterialTheme.typography.titleSmall
        clean.endsWith(".bodyLarge") -> MaterialTheme.typography.bodyLarge
        clean.endsWith(".bodySmall") -> MaterialTheme.typography.bodySmall
        clean.endsWith(".labelLarge") -> MaterialTheme.typography.labelLarge
        clean.endsWith(".labelMedium") -> MaterialTheme.typography.labelMedium
        clean.endsWith(".labelSmall") -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.bodyMedium
    }
}

private fun parseVerticalArrangement(expr: String?): Arrangement.Vertical {
    val clean = expr?.trim() ?: return Arrangement.Top
    val spaced = Regex("""spacedBy\((\d+)\.dp\)""").find(clean)?.groupValues?.get(1)?.toIntOrNull()
    if (spaced != null) return Arrangement.spacedBy(spaced.dp)
    return when {
        clean.contains("Center") -> Arrangement.Center
        clean.contains("Bottom") -> Arrangement.Bottom
        clean.contains("SpaceBetween") -> Arrangement.SpaceBetween
        clean.contains("SpaceEvenly") -> Arrangement.SpaceEvenly
        clean.contains("SpaceAround") -> Arrangement.SpaceAround
        else -> Arrangement.Top
    }
}

private fun parseHorizontalArrangement(expr: String?): Arrangement.Horizontal {
    val clean = expr?.trim() ?: return Arrangement.Start
    val spaced = Regex("""spacedBy\((\d+)\.dp\)""").find(clean)?.groupValues?.get(1)?.toIntOrNull()
    if (spaced != null) return Arrangement.spacedBy(spaced.dp)
    return when {
        clean.contains("Center") -> Arrangement.Center
        clean.contains("End") -> Arrangement.End
        clean.contains("SpaceBetween") -> Arrangement.SpaceBetween
        clean.contains("SpaceEvenly") -> Arrangement.SpaceEvenly
        clean.contains("SpaceAround") -> Arrangement.SpaceAround
        else -> Arrangement.Start
    }
}

private fun parseHorizontalAlignment(expr: String?): Alignment.Horizontal {
    val clean = expr?.trim() ?: return Alignment.Start
    return when {
        clean.contains("CenterHorizontally") -> Alignment.CenterHorizontally
        clean.contains("End") -> Alignment.End
        else -> Alignment.Start
    }
}

private fun parseVerticalAlignment(expr: String?): Alignment.Vertical {
    val clean = expr?.trim() ?: return Alignment.Top
    return when {
        clean.contains("CenterVertically") -> Alignment.CenterVertically
        clean.contains("Bottom") -> Alignment.Bottom
        else -> Alignment.Top
    }
}

private fun parseBoxAlignment(expr: String?): Alignment {
    val clean = expr?.trim() ?: return Alignment.TopStart
    return when {
        clean.contains("Center") -> Alignment.Center
        clean.contains("BottomCenter") -> Alignment.BottomCenter
        clean.contains("TopCenter") -> Alignment.TopCenter
        clean.contains("BottomEnd") -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }
}

private fun resolveMaterialIcon(expr: String): ImageVector {
    val name = expr.substringAfterLast('.').trim()
    return when (name) {
        "Add" -> Icons.Default.Add
        "Home" -> Icons.Default.Home
        "Settings" -> Icons.Default.Settings
        "Search" -> Icons.Default.Search
        "Delete" -> Icons.Default.Delete
        "Edit" -> Icons.Default.Edit
        "Check" -> Icons.Default.Check
        "CheckCircle" -> Icons.Default.CheckCircle
        "Close" -> Icons.Default.Close
        "Favorite" -> Icons.Default.Favorite
        "Star" -> Icons.Default.Star
        "Person" -> Icons.Default.Person
        "AccountCircle" -> Icons.Default.AccountCircle
        "Email" -> Icons.Default.Email
        "Lock" -> Icons.Default.Lock
        "Refresh" -> Icons.Default.Refresh
        "Send" -> Icons.AutoMirrored.Filled.Send
        "Share" -> Icons.Default.Share
        "Menu" -> Icons.Default.Menu
        "ArrowBack" -> Icons.AutoMirrored.Filled.ArrowBack
        "PlayArrow" -> Icons.Default.PlayArrow
        "Pause" -> Icons.Default.Pause
        "ShoppingCart" -> Icons.Default.ShoppingCart
        "Notifications" -> Icons.Default.Notifications
        "Info" -> Icons.Default.Info
        "Warning" -> Icons.Default.Warning
        "Build" -> Icons.Default.Build
        "Code" -> Icons.Default.Code
        "RocketLaunch" -> Icons.Default.RocketLaunch
        "SmartToy" -> Icons.Default.SmartToy
        "Folder" -> Icons.Default.Folder
        "Download" -> Icons.Default.Download
        "Upload" -> Icons.Default.Upload
        else -> Icons.Default.Star
    }
}
