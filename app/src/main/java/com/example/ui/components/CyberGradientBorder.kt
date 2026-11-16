package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalStudioCyberPalette

@Composable
fun rememberAnimatedCyberGradientBrush(
    alpha: Float = 1f,
    durationMillis: Int = 3600
): Brush {
    val palette = LocalStudioCyberPalette.current
    val transition = rememberInfiniteTransition(label = "cyber_gradient_border")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cyber_gradient_shift"
    )
    val colors = palette.gradientStops.map { it.copy(alpha = alpha) }
    return Brush.linearGradient(
        colors = colors,
        start = Offset(shift - 400f, 0f),
        end = Offset(shift + 500f, 450f)
    )
}

@Composable
fun CyberGradientCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.6.dp,
    borderAlpha: Float = 0.88f,
    containerColor: Color = LocalStudioCyberPalette.current.cardSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val borderBrush = rememberAnimatedCyberGradientBrush(alpha = borderAlpha)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = BorderStroke(width = borderWidth, brush = borderBrush),
        content = content
    )
}
