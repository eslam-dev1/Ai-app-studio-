package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DEFAULT_FACTORY_KOTLIN_SNIPPET = """
    @Composable
    fun AppStudioScreen(viewModel: AppViewModel = viewModel()) {
        val tasks = remember { mutableStateListOf("Design UI", "Wire State", "Build APK") }
        var isActive by remember { mutableStateOf(true) }

        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks) { item ->
                    ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                        Text(text = item, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
""".trimIndent()

private val MICRO_STATUS_TICKERS = listOf(
    "Forging UI elements...",
    "Weaving logic threads...",
    "Validating codebase..."
)

@Composable
fun CyberCodeFactoryCard(
    projectName: String,
    stepIndex: Int,
    totalSteps: Int = 3,
    stepMessage: String,
    engineLabel: String,
    streamingCode: String = "",
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current

    // 3 server nodes blinking sequentially every 0.5s (500ms)
    var activeNodeIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            activeNodeIndex = (activeNodeIndex + 1) % 3
        }
    }

    // Rotating micro-status ticker
    var tickerIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800L)
            tickerIndex = (tickerIndex + 1) % MICRO_STATUS_TICKERS.size
        }
    }

    // Character-by-character typewriter effect over live or simulated Kotlin Compose code
    val sourceCodeToType = remember(streamingCode) {
        if (streamingCode.isNotBlank()) streamingCode.takeLast(1400) else DEFAULT_FACTORY_KOTLIN_SNIPPET
    }
    var typedCharCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(sourceCodeToType) {
        if (streamingCode.isNotBlank()) {
            // Advance swiftly toward the latest streamed token count
            while (typedCharCount < sourceCodeToType.length) {
                val jump = ((sourceCodeToType.length - typedCharCount) / 8).coerceIn(1, 14)
                typedCharCount = (typedCharCount + jump).coerceAtMost(sourceCodeToType.length)
                delay(24L)
            }
        } else {
            typedCharCount = 0
            while (true) {
                if (typedCharCount < sourceCodeToType.length) {
                    typedCharCount += 2
                    delay(26L)
                } else {
                    delay(900L)
                    typedCharCount = 12
                }
            }
        }
    }

    // Glowing cyan block cursor blink
    var cursorVisible by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450L)
            cursorVisible = 1 - cursorVisible
        }
    }

    // Smooth animated progress readout (22% -> 74% -> 94% across steps)
    val baseProgress = when (stepIndex.coerceIn(0, totalSteps)) {
        0 -> 0.34f
        1 -> 0.74f
        2 -> 0.91f
        else -> 0.97f
    }
    val progressTransition = rememberInfiniteTransition(label = "factory_progress_wave")
    val wavePulse by progressTransition.animateFloat(
        initialValue = -0.03f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress_pulse"
    )
    val waveShift by progressTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_wave_shift"
    )
    val effectiveProgress = (baseProgress + wavePulse).coerceIn(0.12f, 0.99f)
    val percentInt = (effectiveProgress * 100f).toInt().coerceIn(1, 99)

    CyberGradientCard(
        cornerRadius = 22.dp,
        borderWidth = 1.8.dp,
        containerColor = palette.cardSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("cyber_code_factory_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Card Header: "BUILDING [ProjectName]..." + Engine chip + optional Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "BUILDING ${projectName.ifBlank { "APPSTUDIO" }.uppercase()}...",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$engineLabel • $stepMessage",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onCancel != null) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("cancel_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel build",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 3 Pulsing Green Server Nodes (blinking sequentially every 0.5s)
            val serverNodes = listOf("COMPILING", "GENERATING", "ASSEMBLING")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.cardSurfaceHigh.copy(alpha = 0.72f))
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                serverNodes.forEachIndexed { idx, label ->
                    val isLit = idx == activeNodeIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.testTag("factory_node_${label.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLit) CyberEmerald else CyberEmerald.copy(alpha = 0.25f)
                                )
                        )
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = if (isLit) FontWeight.Bold else FontWeight.Medium,
                            color = if (isLit) palette.accentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Compact Rotating Dual-Gear Assembly (Top-Center, ~64.dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                DualInterlockingGears(
                    purpleColor = CyberPurple,
                    cyanColor = CyberCyan,
                    modifier = Modifier
                        .width(128.dp)
                        .height(64.dp)
                        .testTag("factory_dual_gears")
                )
            }

            // 3. Live Streaming Code Typewriter Window with Matrix Data Particles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 136.dp, max = 186.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.terminalSurface)
                    .border(1.dp, palette.accentCyan.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .testTag("live_streaming_code_preview")
            ) {
                MatrixParticleBackdrop(
                    accentCyan = CyberCyan,
                    accentEmerald = CyberEmerald,
                    modifier = Modifier.fillMaxSize()
                )

                val visibleCode = sourceCodeToType.take(typedCharCount.coerceAtMost(sourceCodeToType.length))
                val scrollState = rememberScrollState()
                LaunchedEffect(visibleCode.length) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = palette.terminalText,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                )
                            ) {
                                append(visibleCode)
                            }
                            if (cursorVisible == 1) {
                                withStyle(
                                    SpanStyle(
                                        color = CyberCyan,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                ) {
                                    append("█")
                                }
                            }
                        },
                        lineHeight = 16.sp
                    )
                }
            }

            // 4. Digital Progress Bar + Percentage + Rotating Micro-Status Ticker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.cardBorder.copy(alpha = 0.65f))
                        .testTag("factory_gradient_progress_bar")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(effectiveProgress)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(CyberPurple, CyberCyan, CyberEmerald),
                                    startX = waveShift - 300f,
                                    endX = waveShift + 500f
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = MICRO_STATUS_TICKERS[tickerIndex],
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$percentInt% Completed",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun DualInterlockingGears(
    purpleColor: Color,
    cyanColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dual_gears_spin")
    val clockwiseAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "purple_gear_rotation"
    )
    // Meshed smaller cyan gear spins counter-clockwise (10 teeth vs 8 teeth = 1.25x ratio)
    val counterAngle = -(clockwiseAngle * 1.25f) + 18f

    Canvas(modifier = modifier) {
        val h = size.height
        val w = size.width
        val purpleCenter = Offset(x = w * 0.40f, y = h * 0.50f)
        val purpleOuterRadius = h * 0.42f
        val cyanOuterRadius = h * 0.30f
        val cyanCenter = Offset(
            x = purpleCenter.x + purpleOuterRadius + cyanOuterRadius * 0.68f,
            y = h * 0.56f
        )

        rotate(degrees = clockwiseAngle, pivot = purpleCenter) {
            drawGear(
                center = purpleCenter,
                outerRadius = purpleOuterRadius,
                innerRadius = purpleOuterRadius * 0.72f,
                holeRadius = purpleOuterRadius * 0.32f,
                teeth = 10,
                color = purpleColor
            )
        }

        rotate(degrees = counterAngle, pivot = cyanCenter) {
            drawGear(
                center = cyanCenter,
                outerRadius = cyanOuterRadius,
                innerRadius = cyanOuterRadius * 0.70f,
                holeRadius = cyanOuterRadius * 0.30f,
                teeth = 8,
                color = cyanColor
            )
        }
    }
}

private fun DrawScope.drawGear(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    holeRadius: Float,
    teeth: Int,
    color: Color
) {
    val path = Path()
    val step = (2.0 * PI / teeth).toFloat()
    for (i in 0 until teeth) {
        val a0 = i * step
        val a1 = a0 + step * 0.22f
        val a2 = a0 + step * 0.50f
        val a3 = a0 + step * 0.72f

        val p0 = Offset(center.x + innerRadius * cos(a0), center.y + innerRadius * sin(a0))
        val p1 = Offset(center.x + outerRadius * cos(a1), center.y + outerRadius * sin(a1))
        val p2 = Offset(center.x + outerRadius * cos(a2), center.y + outerRadius * sin(a2))
        val p3 = Offset(center.x + innerRadius * cos(a3), center.y + innerRadius * sin(a3))

        if (i == 0) path.moveTo(p0.x, p0.y) else path.lineTo(p0.x, p0.y)
        path.lineTo(p1.x, p1.y)
        path.lineTo(p2.x, p2.y)
        path.lineTo(p3.x, p3.y)
    }
    path.close()

    drawPath(path = path, color = color.copy(alpha = 0.24f))
    drawPath(path = path, color = color, style = Stroke(width = 2.6f))
    drawCircle(
        color = color.copy(alpha = 0.85f),
        radius = holeRadius,
        center = center,
        style = Stroke(width = 2.4f)
    )
    drawCircle(
        color = color,
        radius = holeRadius * 0.42f,
        center = center
    )
}

@Composable
private fun MatrixParticleBackdrop(
    accentCyan: Color,
    accentEmerald: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "matrix_particles")
    val fallOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "matrix_fall"
    )

    Canvas(modifier = modifier) {
        val columns = 14
        val colSpacing = size.width / columns
        for (col in 0 until columns) {
            val phase = ((col * 0.27f) + fallOffset) % 1f
            val x = colSpacing * col + colSpacing * 0.5f
            val y = phase * (size.height + 40f) - 20f
            val streamColor = if (col % 2 == 0) accentEmerald else accentCyan
            drawRoundRect(
                color = streamColor.copy(alpha = 0.08f),
                topLeft = Offset(x, y),
                size = Size(width = 3f, height = 22f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawCircle(
                color = streamColor.copy(alpha = 0.16f),
                radius = 2f,
                center = Offset(x + 1.5f, y + 24f)
            )
        }
    }
}
