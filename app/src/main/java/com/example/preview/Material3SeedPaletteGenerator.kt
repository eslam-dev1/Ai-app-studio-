package com.example.preview

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.model.GeneratedFile
import com.example.model.StudioProject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SeedColorPreset(
    val name: String,
    val seedHex: String,
    val color: Color
)

data class GeneratedM3Palettes(
    val seedHex: String,
    val seedColor: Color,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme
)

object Material3SeedPaletteGenerator {

    val presets: List<SeedColorPreset> = listOf(
        SeedColorPreset("Electric Cyan", "#0284C7", Color(0xFF0284C7)),
        SeedColorPreset("Mint Emerald", "#059669", Color(0xFF059669)),
        SeedColorPreset("Royal Indigo", "#4F46E5", Color(0xFF4F46E5)),
        SeedColorPreset("Violet Pulse", "#7C3AED", Color(0xFF7C3AED)),
        SeedColorPreset("Crimson Rose", "#E11D48", Color(0xFFE11D48)),
        SeedColorPreset("Sunset Amber", "#D97706", Color(0xFFD97706)),
        SeedColorPreset("Ocean Teal", "#0D9488", Color(0xFF0D9488)),
        SeedColorPreset("Cobalt Blue", "#2563EB", Color(0xFF2563EB))
    )

    fun parseHexColor(hexInput: String): Color? {
        val cleaned = hexInput.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (cleaned.length != 6 && cleaned.length != 8) return null
        val parsed = cleaned.toLongOrNull(16) ?: return null
        val argb = if (cleaned.length == 6) (0xFF000000L or parsed) else parsed
        return Color(argb)
    }

    fun formatHex6(color: Color): String {
        val argb = color.toArgb()
        val rgb = argb and 0xFFFFFF
        return String.format("#%06X", rgb)
    }

    private fun formatKotlinColorLiteral(color: Color): String {
        val argb = color.toArgb()
        val rgb = argb and 0xFFFFFF
        return String.format("Color(0xFF%06X)", rgb)
    }

    fun generateFromSeed(seedColor: Color): GeneratedM3Palettes {
        val (h, s, _) = rgbToHsl(seedColor)
        val sat = s.coerceIn(0.45f, 0.88f)
        val secHue = (h + 18f) % 360f
        val secSat = (sat * 0.65f).coerceIn(0.28f, 0.65f)
        val tertHue = (h + 62f) % 360f
        val tertSat = (sat * 0.78f).coerceIn(0.35f, 0.78f)
        val neutralSat = (sat * 0.14f).coerceIn(0.04f, 0.14f)
        val neutralVarSat = (sat * 0.22f).coerceIn(0.08f, 0.24f)

        val light = lightColorScheme(
            primary = hslToColor(h, sat, 0.40f),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = hslToColor(h, sat, 0.90f),
            onPrimaryContainer = hslToColor(h, sat, 0.12f),
            secondary = hslToColor(secHue, secSat, 0.40f),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = hslToColor(secHue, secSat, 0.90f),
            onSecondaryContainer = hslToColor(secHue, secSat, 0.14f),
            tertiary = hslToColor(tertHue, tertSat, 0.40f),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = hslToColor(tertHue, tertSat, 0.90f),
            onTertiaryContainer = hslToColor(tertHue, tertSat, 0.14f),
            background = hslToColor(h, neutralSat, 0.98f),
            onBackground = hslToColor(h, neutralSat, 0.11f),
            surface = hslToColor(h, neutralSat, 0.99f),
            onSurface = hslToColor(h, neutralSat, 0.11f),
            surfaceVariant = hslToColor(h, neutralVarSat, 0.91f),
            onSurfaceVariant = hslToColor(h, neutralVarSat, 0.30f),
            outline = hslToColor(h, neutralVarSat, 0.50f)
        )

        val dark = darkColorScheme(
            primary = hslToColor(h, sat, 0.76f),
            onPrimary = hslToColor(h, sat, 0.16f),
            primaryContainer = hslToColor(h, sat, 0.28f),
            onPrimaryContainer = hslToColor(h, sat, 0.92f),
            secondary = hslToColor(secHue, secSat, 0.76f),
            onSecondary = hslToColor(secHue, secSat, 0.16f),
            secondaryContainer = hslToColor(secHue, secSat, 0.28f),
            onSecondaryContainer = hslToColor(secHue, secSat, 0.90f),
            tertiary = hslToColor(tertHue, tertSat, 0.78f),
            onTertiary = hslToColor(tertHue, tertSat, 0.16f),
            tertiaryContainer = hslToColor(tertHue, tertSat, 0.28f),
            onTertiaryContainer = hslToColor(tertHue, tertSat, 0.92f),
            background = hslToColor(h, neutralSat, 0.08f),
            onBackground = hslToColor(h, neutralSat, 0.92f),
            surface = hslToColor(h, neutralSat, 0.11f),
            onSurface = hslToColor(h, neutralSat, 0.92f),
            surfaceVariant = hslToColor(h, neutralVarSat, 0.20f),
            onSurfaceVariant = hslToColor(h, neutralVarSat, 0.80f),
            outline = hslToColor(h, neutralVarSat, 0.46f)
        )

        return GeneratedM3Palettes(
            seedHex = formatHex6(seedColor),
            seedColor = seedColor,
            lightScheme = light,
            darkScheme = dark
        )
    }

    fun findExistingThemeFile(files: List<GeneratedFile>): GeneratedFile? {
        return files.firstOrNull {
            val fileName = it.path.substringAfterLast('/')
            fileName.equals("Theme.kt", ignoreCase = true)
        }
    }

    fun resolveTargetThemePath(project: StudioProject): String {
        val existing = findExistingThemeFile(project.files)
        if (existing != null) return existing.path
        val pkgPath = project.packageName.replace('.', '/')
        return "app/src/main/java/$pkgPath/ui/theme/Theme.kt"
    }

    fun buildThemeKtContent(
        project: StudioProject,
        seedColor: Color
    ): Pair<String, String> {
        val palettes = generateFromSeed(seedColor)
        val existingFile = findExistingThemeFile(project.files)
        val targetPath = resolveTargetThemePath(project)

        val packageDecl = existingFile?.content?.let { content ->
            Regex("""^\s*package\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
                .find(content)?.groupValues?.get(1)
        } ?: "${project.packageName}.ui.theme"

        val themeFunName = existingFile?.content?.let { content ->
            Regex("""@Composable\s+fun\s+([A-Za-z0-9_]*Theme)\s*\(""")
                .find(content)?.groupValues?.get(1)
        } ?: run {
            val prefix = project.appName.filter { it.isLetterOrDigit() }.ifBlank { "App" }
            "${prefix}Theme"
        }

        val l = palettes.lightScheme
        val d = palettes.darkScheme

        val code = """
package $packageDecl

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Generated by AI App Studio Material 3 Seed Color Picker
// Seed Color: ${palettes.seedHex}
val SeedColor = ${formatKotlinColorLiteral(palettes.seedColor)}

private val LightColorScheme = lightColorScheme(
    primary = ${formatKotlinColorLiteral(l.primary)},
    onPrimary = ${formatKotlinColorLiteral(l.onPrimary)},
    primaryContainer = ${formatKotlinColorLiteral(l.primaryContainer)},
    onPrimaryContainer = ${formatKotlinColorLiteral(l.onPrimaryContainer)},
    secondary = ${formatKotlinColorLiteral(l.secondary)},
    onSecondary = ${formatKotlinColorLiteral(l.onSecondary)},
    secondaryContainer = ${formatKotlinColorLiteral(l.secondaryContainer)},
    onSecondaryContainer = ${formatKotlinColorLiteral(l.onSecondaryContainer)},
    tertiary = ${formatKotlinColorLiteral(l.tertiary)},
    onTertiary = ${formatKotlinColorLiteral(l.onTertiary)},
    tertiaryContainer = ${formatKotlinColorLiteral(l.tertiaryContainer)},
    onTertiaryContainer = ${formatKotlinColorLiteral(l.onTertiaryContainer)},
    background = ${formatKotlinColorLiteral(l.background)},
    onBackground = ${formatKotlinColorLiteral(l.onBackground)},
    surface = ${formatKotlinColorLiteral(l.surface)},
    onSurface = ${formatKotlinColorLiteral(l.onSurface)},
    surfaceVariant = ${formatKotlinColorLiteral(l.surfaceVariant)},
    onSurfaceVariant = ${formatKotlinColorLiteral(l.onSurfaceVariant)},
    outline = ${formatKotlinColorLiteral(l.outline)}
)

private val DarkColorScheme = darkColorScheme(
    primary = ${formatKotlinColorLiteral(d.primary)},
    onPrimary = ${formatKotlinColorLiteral(d.onPrimary)},
    primaryContainer = ${formatKotlinColorLiteral(d.primaryContainer)},
    onPrimaryContainer = ${formatKotlinColorLiteral(d.onPrimaryContainer)},
    secondary = ${formatKotlinColorLiteral(d.secondary)},
    onSecondary = ${formatKotlinColorLiteral(d.onSecondary)},
    secondaryContainer = ${formatKotlinColorLiteral(d.secondaryContainer)},
    onSecondaryContainer = ${formatKotlinColorLiteral(d.onSecondaryContainer)},
    tertiary = ${formatKotlinColorLiteral(d.tertiary)},
    onTertiary = ${formatKotlinColorLiteral(d.onTertiary)},
    tertiaryContainer = ${formatKotlinColorLiteral(d.tertiaryContainer)},
    onTertiaryContainer = ${formatKotlinColorLiteral(d.onTertiaryContainer)},
    background = ${formatKotlinColorLiteral(d.background)},
    onBackground = ${formatKotlinColorLiteral(d.onBackground)},
    surface = ${formatKotlinColorLiteral(d.surface)},
    onSurface = ${formatKotlinColorLiteral(d.onSurface)},
    surfaceVariant = ${formatKotlinColorLiteral(d.surfaceVariant)},
    onSurfaceVariant = ${formatKotlinColorLiteral(d.onSurfaceVariant)},
    outline = ${formatKotlinColorLiteral(d.outline)}
)

@Composable
fun $themeFunName(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
""".trimIndent() + "\n"

        return targetPath to code
    }

    /**
     * Parses `LightColorScheme` and `DarkColorScheme` (or `SeedColor`) from the project's `Theme.kt`
     * so that the Live Preview reflects any theme edits in real time.
     */
    fun extractCustomSchemesFromFiles(
        files: List<Pair<String, String>>
    ): Pair<ColorScheme, ColorScheme>? {
        val themeSource = files.firstOrNull { (path, _) ->
            path.substringAfterLast('/').equals("Theme.kt", ignoreCase = true)
        }?.second ?: return null

        // Check if there is an explicit SeedColor = Color(0xFF......)
        val seedMatch = Regex("""val\s+SeedColor\s*=\s*(Color\(\s*0x[0-9A-Fa-f]{6,8}\s*\))""").find(themeSource)
        val explicitLightBlock = Regex("""lightColorScheme\s*\(([\s\S]*?)\)""").find(themeSource)?.groupValues?.get(1)
        val explicitDarkBlock = Regex("""darkColorScheme\s*\(([\s\S]*?)\)""").find(themeSource)?.groupValues?.get(1)

        val baseSeed = seedMatch?.groupValues?.get(1)?.let { ComposePreviewEngine.resolveColorExpr(it) }
        val basePalettes = generateFromSeed(baseSeed ?: Color(0xFF0284C7))

        val light = if (explicitLightBlock != null) {
            applyParsedSchemeOverrides(basePalettes.lightScheme, explicitLightBlock)
        } else basePalettes.lightScheme

        val dark = if (explicitDarkBlock != null) {
            applyParsedSchemeOverrides(basePalettes.darkScheme, explicitDarkBlock)
        } else basePalettes.darkScheme

        if (explicitLightBlock == null && explicitDarkBlock == null && baseSeed == null) {
            return null
        }
        return light to dark
    }

    private fun applyParsedSchemeOverrides(base: ColorScheme, block: String): ColorScheme {
        fun colorFor(token: String): Color? {
            val m = Regex("""\b$token\s*=\s*(Color\(\s*0x[0-9A-Fa-f]{6,8}\s*\))""").find(block)
                ?: return null
            return ComposePreviewEngine.resolveColorExpr(m.groupValues[1])
        }
        return base.copy(
            primary = colorFor("primary") ?: base.primary,
            onPrimary = colorFor("onPrimary") ?: base.onPrimary,
            primaryContainer = colorFor("primaryContainer") ?: base.primaryContainer,
            onPrimaryContainer = colorFor("onPrimaryContainer") ?: base.onPrimaryContainer,
            secondary = colorFor("secondary") ?: base.secondary,
            onSecondary = colorFor("onSecondary") ?: base.onSecondary,
            secondaryContainer = colorFor("secondaryContainer") ?: base.secondaryContainer,
            onSecondaryContainer = colorFor("onSecondaryContainer") ?: base.onSecondaryContainer,
            tertiary = colorFor("tertiary") ?: base.tertiary,
            onTertiary = colorFor("onTertiary") ?: base.onTertiary,
            tertiaryContainer = colorFor("tertiaryContainer") ?: base.tertiaryContainer,
            onTertiaryContainer = colorFor("onTertiaryContainer") ?: base.onTertiaryContainer,
            background = colorFor("background") ?: base.background,
            onBackground = colorFor("onBackground") ?: base.onBackground,
            surface = colorFor("surface") ?: base.surface,
            onSurface = colorFor("onSurface") ?: base.onSurface,
            surfaceVariant = colorFor("surfaceVariant") ?: base.surfaceVariant,
            onSurfaceVariant = colorFor("onSurfaceVariant") ?: base.onSurfaceVariant,
            outline = colorFor("outline") ?: base.outline
        )
    }

    private fun rgbToHsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        var h = 0f
        val l = (maxC + minC) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

        if (delta != 0f) {
            h = when (maxC) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }
        return Triple(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m).coerceIn(0f, 1f) * 255f).roundToInt()
        val g = ((g1 + m).coerceIn(0f, 1f) * 255f).roundToInt()
        val b = ((b1 + m).coerceIn(0f, 1f) * 255f).roundToInt()
        return Color(r, g, b)
    }
}
