package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.FactoryTelemetryCalculator
import com.example.model.BuildPhase
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioCyberPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun CyberCodeFactoryCard(
    projectName: String,
    stepIndex: Int,
    totalSteps: Int = 3,
    stepMessage: String,
    engineLabel: String,
    streamingCode: String = "",
    buildPhase: BuildPhase = when (stepIndex.coerceIn(0, totalSteps)) {
        0 -> BuildPhase.PROMPTING
        1 -> BuildPhase.GENERATING_CODE
        else -> BuildPhase.PARSING_FILES
    },
    progress: Float = when (stepIndex.coerceIn(0, totalSteps)) {
        0 -> 0.12f
        1 -> 0.45f
        2 -> 0.85f
        else -> 0.96f
    },
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioCyberPalette.current
    val activeNodeIndex = remember(buildPhase) {
        FactoryTelemetryCalculator.activeNodeForPhase(buildPhase)
    }

    // Glowing cyan cursor blink
    var cursorVisible by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450L)
            cursorVisible = 1 - cursorVisible
        }
    }

    // Smooth gradient sweep along the real progress bar
    val shimmerTransition = rememberInfiniteTransition(label = "factory_progress_shimmer")
    val waveShift by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_wave_shift"
    )

    // Real execution progress (0.0f..1.0f) interpolated cleanly from ViewModel/service telemetry
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "factory_real_progress"
    )
    val percentInt = (animatedProgress * 100f).toInt().coerceIn(0, 100)

    val sanitizedCode = remember(streamingCode) {
        FactoryTelemetryCalculator.sanitizeStreamingCode(streamingCode)
    }
    val liveStatusOperation = stepMessage.ifBlank { "Executing build pipeline…" }
    val displayCode = remember(sanitizedCode, engineLabel, liveStatusOperation) {
        if (sanitizedCode.isNotBlank()) {
            sanitizedCode
        } else {
            "// [$engineLabel] $liveStatusOperation"
        }
    }
    val codeLines = remember(displayCode) {
        displayCode.split('\n').ifEmpty { listOf("") }
    }

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
            // 1. Card Header: "BUILDING [ProjectName]..." + Live Engine & Phase + Cancel
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
                        text = "$engineLabel • $liveStatusOperation",
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

            // 2. Real Pipeline Stage Nodes (COMPILING • GENERATING • ASSEMBLING) driven by BuildPhase
            val serverNodes = listOf("COMPILING", "GENERATING", "ASSEMBLING")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.cardSurfaceHigh.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                serverNodes.forEachIndexed { idx, label ->
                    val isActive = idx == activeNodeIndex
                    val isCompleted = idx < activeNodeIndex
                    val stateDescription = when {
                        isActive -> "$label active"
                        isCompleted -> "$label completed"
                        else -> "$label pending"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .testTag("factory_node_${label.lowercase()}")
                            .semantics { contentDescription = stateDescription }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isActive -> CyberEmerald
                                        isCompleted -> CyberCyan.copy(alpha = 0.75f)
                                        else -> CyberEmerald.copy(alpha = 0.22f)
                                    }
                                )
                        )
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isActive -> palette.accentEmerald
                                isCompleted -> palette.accentCyan
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // 3. Compact Rotating Dual-Gear Assembly above the transparent code viewport
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

            // 4. Borderless & Transparent Code Stream Container (floats seamlessly on translucent card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Transparent)
                    .testTag("live_streaming_code_preview")
            ) {
                MatrixParticleBackdrop(
                    accentCyan = CyberCyan,
                    accentEmerald = CyberEmerald,
                    modifier = Modifier.fillMaxSize()
                )

                val listState = rememberLazyListState()
                LaunchedEffect(codeLines.size, displayCode.length) {
                    if (codeLines.isNotEmpty()) {
                        listState.scrollToItem(codeLines.lastIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(codeLines) { index, lineText ->
                        val isLastLine = index == codeLines.lastIndex
                        val codeInk = if (palette.isDark) {
                            palette.terminalText
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = codeInk,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp
                                    )
                                ) {
                                    append(lineText)
                                }
                                if (isLastLine && cursorVisible == 1) {
                                    withStyle(
                                        SpanStyle(
                                            color = palette.accentCyan,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    ) {
                                        append(" █")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            softWrap = true,
                            overflow = TextOverflow.Clip,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 5. Real Live Progress Bar + Exact Percentage + Real Bottom Status Ticker
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
                            .fillMaxWidth(animatedProgress.coerceAtLeast(0.02f))
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
                        text = liveStatusOperation,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("factory_status_ticker")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$percentInt% Completed",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentEmerald,
                        modifier = Modifier.testTag("factory_progress_percent_text")
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
                color = streamColor.copy(alpha = 0.06f),
                topLeft = Offset(x, y),
                size = Size(width = 3f, height = 22f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawCircle(
                color = streamColor.copy(alpha = 0.12f),
                radius = 2f,
                center = Offset(x + 1.5f, y + 24f)
            )
        }
    }
}
