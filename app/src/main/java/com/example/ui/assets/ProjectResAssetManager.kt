package com.example.ui.assets

import com.example.model.GeneratedFile
import java.io.ByteArrayOutputStream

enum class ResAssetKind(val label: String) {
    IMAGE("Images"),
    ICON("Icons"),
    FONT("Fonts")
}

enum class ResAssetFormat(val label: String) {
    PNG("PNG Bitmap"),
    JPEG("JPEG Image"),
    WEBP("WebP Image"),
    VECTOR_XML("Vector XML"),
    SHAPE_XML("Shape XML"),
    ADAPTIVE_ICON_XML("Adaptive Icon XML"),
    FONT_TTF("TrueType Font (.ttf)"),
    FONT_OTF("OpenType Font (.otf)"),
    FONT_XML("Font Family XML"),
    OTHER_XML("Drawable XML")
}

data class ResAssetItem(
    val path: String,
    val fileName: String,
    val baseName: String,
    val extension: String,
    val resFolder: String,
    val resourceType: String,
    val qualifier: String,
    val kind: ResAssetKind,
    val format: ResAssetFormat,
    val sizeBytes: Long,
    val isBinary: Boolean,
    val rawContent: String,
    val referenceKotlin: String,
    val referenceXml: String
)

data class ParsedVectorPath(
    val pathData: String,
    val fillColorArgb: Long?,
    val strokeColorArgb: Long?,
    val strokeWidth: Float,
    val fillAlpha: Float,
    val strokeAlpha: Float,
    val fillTypeEvenOdd: Boolean
)

data class ParsedVectorDrawable(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val widthDp: Float,
    val heightDp: Float,
    val tintColorArgb: Long?,
    val paths: List<ParsedVectorPath>
)

data class ParsedShapeDrawable(
    val shapeType: String,
    val solidColorArgb: Long?,
    val gradientStartArgb: Long?,
    val gradientEndArgb: Long?,
    val strokeColorArgb: Long?,
    val strokeWidthDp: Float,
    val cornerRadiusDp: Float
)

data class ParsedFontEntry(
    val fontRef: String,
    val weight: Int,
    val isItalic: Boolean
)

data class ParsedFontFamily(
    val entries: List<ParsedFontEntry>
)

data class AssetRenameResult(
    val oldPath: String,
    val newPath: String,
    val oldBaseName: String,
    val newBaseName: String,
    val updatedFiles: List<GeneratedFile>,
    val referencesUpdatedCount: Int
)

data class VectorIconPreset(
    val id: String,
    val label: String,
    val suggestedName: String,
    val xmlContent: String
)

object ProjectResAssetManager {

    const val BINARY_CONTENT_PREFIX = "__BASE64__:"

    private val BINARY_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val BINARY_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    private val ALL_BINARY_EXTENSIONS = BINARY_IMAGE_EXTENSIONS + BINARY_FONT_EXTENSIONS

    private val RESOURCE_NAME_REGEX = Regex("^[a-z][a-z0-9_]*$")

    private val BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
    private val BASE64_DECODE_TABLE = IntArray(128) { -1 }.apply {
        BASE64_ALPHABET.forEachIndexed { idx, ch -> this[ch.code] = idx }
    }

    fun encodeBinaryContent(bytes: ByteArray): String {
        return BINARY_CONTENT_PREFIX + encodeBase64(bytes)
    }

    fun isBinaryEncodedContent(content: String): Boolean {
        return content.startsWith(BINARY_CONTENT_PREFIX)
    }

    fun decodeBinaryContent(content: String): ByteArray? {
        if (!content.startsWith(BINARY_CONTENT_PREFIX)) return null
        val payload = content.removePrefix(BINARY_CONTENT_PREFIX).trim()
        return runCatching { decodeBase64(payload) }.getOrNull()
    }

    fun isBinaryAssetPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in ALL_BINARY_EXTENSIONS
    }

    fun isResAssetPath(path: String): Boolean {
        val normalized = path.trim().replace('\\', '/').removePrefix("/").lowercase()
        if (!normalized.contains("res/")) return false
        val afterRes = normalized.substringAfter("res/")
        val folder = afterRes.substringBefore('/', "")
        val ext = afterRes.substringAfterLast('.', "")
        val isDrawableOrMipmapOrFont = folder.startsWith("drawable") ||
            folder.startsWith("mipmap") ||
            folder.startsWith("font")
        return isDrawableOrMipmapOrFont || ext in ALL_BINARY_EXTENSIONS
    }

    fun discoverResAssets(files: List<GeneratedFile>): List<ResAssetItem> {
        return files.mapNotNull { file -> toResAssetItem(file) }
            .sortedWith(
                compareBy<ResAssetItem> { it.kind.ordinal }
                    .thenBy { it.baseName.lowercase() }
                    .thenBy { it.qualifier.lowercase() }
                    .thenBy { it.path.lowercase() }
            )
    }

    fun toResAssetItem(file: GeneratedFile): ResAssetItem? {
        if (!isResAssetPath(file.path)) return null
        val normalized = file.path.trim().replace('\\', '/').removePrefix("/")
        val fileName = normalized.substringAfterLast('/')
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "").lowercase()

        val afterRes = normalized.lowercase().substringAfter("res/", "")
        val resFolder = afterRes.substringBefore('/', "drawable")
        val resourceType = when {
            resFolder.startsWith("font") || extension in BINARY_FONT_EXTENSIONS -> "font"
            resFolder.startsWith("mipmap") -> "mipmap"
            else -> "drawable"
        }
        val qualifier = resFolder.substringAfter('-', "").ifBlank { "default" }

        val contentTrimmed = file.content.trimStart()
        val isVectorXml = extension == "xml" && contentTrimmed.contains("<vector", ignoreCase = true)
        val isAdaptiveIconXml = extension == "xml" && contentTrimmed.contains("<adaptive-icon", ignoreCase = true)
        val isShapeXml = extension == "xml" && contentTrimmed.contains("<shape", ignoreCase = true)
        val isFontXml = extension == "xml" &&
            (resourceType == "font" || contentTrimmed.contains("<font-family", ignoreCase = true))

        val kind = when {
            resourceType == "font" || extension in BINARY_FONT_EXTENSIONS -> ResAssetKind.FONT
            isVectorXml || isAdaptiveIconXml || resourceType == "mipmap" ||
                baseName.startsWith("ic_", ignoreCase = true) -> ResAssetKind.ICON
            else -> ResAssetKind.IMAGE
        }

        val format = when (extension) {
            "png" -> ResAssetFormat.PNG
            "jpg", "jpeg" -> ResAssetFormat.JPEG
            "webp" -> ResAssetFormat.WEBP
            "ttf", "ttc" -> ResAssetFormat.FONT_TTF
            "otf" -> ResAssetFormat.FONT_OTF
            "xml" -> when {
                isVectorXml -> ResAssetFormat.VECTOR_XML
                isAdaptiveIconXml -> ResAssetFormat.ADAPTIVE_ICON_XML
                isShapeXml -> ResAssetFormat.SHAPE_XML
                isFontXml -> ResAssetFormat.FONT_XML
                else -> ResAssetFormat.OTHER_XML
            }
            else -> ResAssetFormat.OTHER_XML
        }

        val decodedBytes = decodeBinaryContent(file.content)
        val isBinary = decodedBytes != null || extension in ALL_BINARY_EXTENSIONS
        val sizeBytes = decodedBytes?.size?.toLong() ?: file.content.toByteArray(Charsets.UTF_8).size.toLong()

        return ResAssetItem(
            path = file.path,
            fileName = fileName,
            baseName = baseName,
            extension = extension,
            resFolder = resFolder,
            resourceType = resourceType,
            qualifier = qualifier,
            kind = kind,
            format = format,
            sizeBytes = sizeBytes,
            isBinary = isBinary,
            rawContent = file.content,
            referenceKotlin = "R.$resourceType.$baseName",
            referenceXml = "@$resourceType/$baseName"
        )
    }

    fun validateResourceBaseName(candidate: String): String? {
        val trimmed = candidate.trim()
        return when {
            trimmed.isBlank() -> "Resource name cannot be empty."
            trimmed.length > 64 -> "Resource name must be 64 characters or fewer."
            trimmed.first().isDigit() -> "Must start with a lowercase letter (a–z), not a digit."
            !RESOURCE_NAME_REGEX.matches(trimmed) ->
                "Use only lowercase letters (a–z), digits (0–9), and underscores (_)."
            else -> null
        }
    }

    fun sanitizeToResourceBaseName(raw: String, fallback: String = "asset_item"): String {
        val withoutExt = raw.substringBeforeLast('.', raw).trim().lowercase()
        val normalized = withoutExt
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        val validStart = when {
            normalized.isBlank() -> fallback
            normalized.first().isDigit() -> "res_$normalized"
            else -> normalized
        }
        return validStart.take(64)
    }

    fun renameAssetAndReferences(
        files: List<GeneratedFile>,
        oldPath: String,
        newBaseName: String,
        updateReferences: Boolean = true
    ): Result<AssetRenameResult> {
        val targetFile = files.firstOrNull { it.path == oldPath }
            ?: return Result.failure(IllegalArgumentException("Asset not found: $oldPath"))

        val cleanNewBase = newBaseName.trim()
        val validationError = validateResourceBaseName(cleanNewBase)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        val assetItem = toResAssetItem(targetFile)
            ?: return Result.failure(IllegalArgumentException("File is not inside a res/ asset directory: $oldPath"))

        val extSuffix = if (assetItem.extension.isNotBlank()) ".${assetItem.extension}" else ""
        val parentDir = oldPath.substringBeforeLast('/', "")
        val newFileName = "$cleanNewBase$extSuffix"
        val newPath = if (parentDir.isBlank()) newFileName else "$parentDir/$newFileName"

        if (newPath == oldPath) {
            return Result.failure(IllegalArgumentException("Enter a different resource name to rename."))
        }

        if (files.any { it.path.equals(newPath, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Another asset already exists at $newPath"))
        }

        val oldBase = assetItem.baseName
        val resourceType = assetItem.resourceType
        val kotlinRefRegex = Regex("\\bR\\.${Regex.escape(resourceType)}\\.${Regex.escape(oldBase)}\\b")
        val xmlRefRegex = Regex("@${Regex.escape(resourceType)}/${Regex.escape(oldBase)}\\b")

        var refCount = 0
        val updatedFiles = files.map { file ->
            if (file.path == oldPath) {
                file.copy(path = newPath)
            } else if (updateReferences && shouldScanFileForResourceRefs(file.path, file.content)) {
                var content = file.content
                val ktMatches = kotlinRefRegex.findAll(content).count()
                if (ktMatches > 0) {
                    content = kotlinRefRegex.replace(content, "R.$resourceType.$cleanNewBase")
                    refCount += ktMatches
                }
                val xmlMatches = xmlRefRegex.findAll(content).count()
                if (xmlMatches > 0) {
                    content = xmlRefRegex.replace(content, "@$resourceType/$cleanNewBase")
                    refCount += xmlMatches
                }
                if (content != file.content) file.copy(content = content) else file
            } else {
                file
            }
        }.sortedBy { it.path }

        return Result.success(
            AssetRenameResult(
                oldPath = oldPath,
                newPath = newPath,
                oldBaseName = oldBase,
                newBaseName = cleanNewBase,
                updatedFiles = updatedFiles,
                referencesUpdatedCount = refCount
            )
        )
    }

    private fun shouldScanFileForResourceRefs(path: String, content: String): Boolean {
        if (isBinaryEncodedContent(content) || isBinaryAssetPath(path)) return false
        val lower = path.lowercase()
        return lower.endsWith(".kt") ||
            lower.endsWith(".java") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".kts")
    }

    fun parseVectorDrawableXml(xml: String): ParsedVectorDrawable? {
        val vectorHeaderMatch = Regex("<vector\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL).find(xml)
            ?: return null
        val vectorAttrs = vectorHeaderMatch.groupValues[1]

        val viewportWidth = extractFloatAttr(vectorAttrs, "viewportWidth") ?: 24f
        val viewportHeight = extractFloatAttr(vectorAttrs, "viewportHeight") ?: 24f
        val widthDp = extractFloatAttr(vectorAttrs, "width") ?: viewportWidth
        val heightDp = extractFloatAttr(vectorAttrs, "height") ?: viewportHeight
        val tintColorArgb = extractAttr(vectorAttrs, "tint")?.let { resolveAndroidColorArgb(it) }

        val pathMatches = Regex("<path\\b([^>]*)/?>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        val parsedPaths = pathMatches.mapNotNull { match ->
            val attrs = match.groupValues[1]
            val pathData = extractAttr(attrs, "pathData")?.trim().orEmpty()
            if (pathData.isBlank()) return@mapNotNull null
            val fillRaw = extractAttr(attrs, "fillColor")
            val strokeRaw = extractAttr(attrs, "strokeColor")
            val fillColorArgb = fillRaw?.let { resolveAndroidColorArgb(it) }
                ?: if (strokeRaw == null) 0xFF1F2937L else null
            val strokeColorArgb = strokeRaw?.let { resolveAndroidColorArgb(it) }
            val strokeWidth = extractFloatAttr(attrs, "strokeWidth") ?: 0f
            val fillAlpha = extractFloatAttr(attrs, "fillAlpha")?.coerceIn(0f, 1f) ?: 1f
            val strokeAlpha = extractFloatAttr(attrs, "strokeAlpha")?.coerceIn(0f, 1f) ?: 1f
            val fillTypeEvenOdd = extractAttr(attrs, "fillType")?.equals("evenOdd", ignoreCase = true) == true

            ParsedVectorPath(
                pathData = pathData,
                fillColorArgb = fillColorArgb,
                strokeColorArgb = strokeColorArgb,
                strokeWidth = strokeWidth,
                fillAlpha = fillAlpha,
                strokeAlpha = strokeAlpha,
                fillTypeEvenOdd = fillTypeEvenOdd
            )
        }.toList()

        return ParsedVectorDrawable(
            viewportWidth = viewportWidth.coerceAtLeast(1f),
            viewportHeight = viewportHeight.coerceAtLeast(1f),
            widthDp = widthDp.coerceAtLeast(1f),
            heightDp = heightDp.coerceAtLeast(1f),
            tintColorArgb = tintColorArgb,
            paths = parsedPaths
        )
    }

    fun parseShapeDrawableXml(xml: String): ParsedShapeDrawable? {
        val shapeMatch = Regex("<shape\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL).find(xml)
            ?: return null
        val shapeType = extractAttr(shapeMatch.groupValues[1], "shape")?.lowercase() ?: "rectangle"

        val solidTag = Regex("<solid\\b([^>]*)/?>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
        val solidColorArgb = solidTag?.let { extractAttr(it, "color") }?.let { resolveAndroidColorArgb(it) }

        val gradTag = Regex("<gradient\\b([^>]*)/?>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
        val gradStart = gradTag?.let { extractAttr(it, "startColor") }?.let { resolveAndroidColorArgb(it) }
        val gradEnd = gradTag?.let { extractAttr(it, "endColor") }?.let { resolveAndroidColorArgb(it) }

        val strokeTag = Regex("<stroke\\b([^>]*)/?>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
        val strokeColor = strokeTag?.let { extractAttr(it, "color") }?.let { resolveAndroidColorArgb(it) }
        val strokeWidth = strokeTag?.let { extractFloatAttr(it, "width") } ?: 0f

        val cornersTag = Regex("<corners\\b([^>]*)/?>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
        val cornerRadius = cornersTag?.let { extractFloatAttr(it, "radius") } ?: 0f

        return ParsedShapeDrawable(
            shapeType = shapeType,
            solidColorArgb = solidColorArgb,
            gradientStartArgb = gradStart,
            gradientEndArgb = gradEnd,
            strokeColorArgb = strokeColor,
            strokeWidthDp = strokeWidth.coerceAtLeast(0f),
            cornerRadiusDp = cornerRadius.coerceAtLeast(0f)
        )
    }

    fun parseFontFamilyXml(xml: String): ParsedFontFamily? {
        if (!xml.contains("<font-family", ignoreCase = true)) return null
        val entries = Regex("<font\\b([^>]*)/?>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val fontRef = extractAttr(attrs, "font") ?: return@mapNotNull null
            val weight = extractAttr(attrs, "fontWeight")?.toIntOrNull() ?: 400
            val style = extractAttr(attrs, "fontStyle")?.lowercase() ?: "normal"
            ParsedFontEntry(
                fontRef = fontRef,
                weight = weight,
                isItalic = style == "italic"
            )
        }.toList()
        return ParsedFontFamily(entries = entries)
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.2f MB", mb)
    }

    fun resolveAndroidColorArgb(raw: String): Long? {
        val value = raw.trim()
        return when {
            value.startsWith("#") -> parseHexColorArgb(value)
            value.equals("@android:color/white", ignoreCase = true) -> 0xFFFFFFFFL
            value.equals("@android:color/black", ignoreCase = true) -> 0xFF000000L
            value.equals("@android:color/transparent", ignoreCase = true) -> 0x00000000L
            value.contains("colorPrimary", ignoreCase = true) -> 0xFF6750A4L
            value.contains("colorSecondary", ignoreCase = true) -> 0xFF006A6AL
            value.contains("colorOnSurface", ignoreCase = true) -> 0xFF1F2937L
            value.contains("colorSurface", ignoreCase = true) -> 0xFFF3F4F6L
            value.contains("white", ignoreCase = true) -> 0xFFFFFFFFL
            value.contains("black", ignoreCase = true) -> 0xFF000000L
            else -> null
        }
    }

    private fun parseHexColorArgb(hexRaw: String): Long? {
        val hex = hexRaw.removePrefix("#").trim()
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToIntOrNull(16) ?: return null
                val g = hex[1].digitToIntOrNull(16) ?: return null
                val b = hex[2].digitToIntOrNull(16) ?: return null
                (0xFFL shl 24) or ((r * 17).toLong() shl 16) or ((g * 17).toLong() shl 8) or (b * 17).toLong()
            }
            4 -> {
                val a = hex[0].digitToIntOrNull(16) ?: return null
                val r = hex[1].digitToIntOrNull(16) ?: return null
                val g = hex[2].digitToIntOrNull(16) ?: return null
                val b = hex[3].digitToIntOrNull(16) ?: return null
                ((a * 17).toLong() shl 24) or ((r * 17).toLong() shl 16) or ((g * 17).toLong() shl 8) or (b * 17).toLong()
            }
            6 -> {
                val rgb = hex.toLongOrNull(16) ?: return null
                0xFF000000L or rgb
            }
            8 -> hex.toLongOrNull(16)
            else -> null
        }
    }

    private fun extractAttr(attrs: String, localName: String): String? {
        val pattern = Regex("""(?:android:)?${Regex.escape(localName)}\s*=\s*["']([^"']*)["']""")
        return pattern.find(attrs)?.groupValues?.get(1)
    }

    private fun extractFloatAttr(attrs: String, localName: String): Float? {
        val raw = extractAttr(attrs, localName) ?: return null
        val cleaned = raw.trim()
            .removeSuffix("dp")
            .removeSuffix("dip")
            .removeSuffix("px")
            .removeSuffix("sp")
            .trim()
        return cleaned.toFloatOrNull()
    }

    val builtInVectorPresets: List<VectorIconPreset> = listOf(
        VectorIconPreset(
            id = "star",
            label = "Star Badge",
            suggestedName = "ic_studio_star",
            xmlContent = """
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="24"
                    android:viewportHeight="24">
                    <path
                        android:fillColor="#F59E0B"
                        android:pathData="M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21z" />
                </vector>
            """.trimIndent()
        ),
        VectorIconPreset(
            id = "bolt",
            label = "Flash Bolt",
            suggestedName = "ic_bolt_flash",
            xmlContent = """
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="24"
                    android:viewportHeight="24">
                    <path
                        android:fillColor="#2563EB"
                        android:pathData="M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66c0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z" />
                </vector>
            """.trimIndent()
        ),
        VectorIconPreset(
            id = "heart",
            label = "Favorite Heart",
            suggestedName = "ic_favorite_heart",
            xmlContent = """
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="24"
                    android:viewportHeight="24">
                    <path
                        android:fillColor="#EF4444"
                        android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5C2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3C19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z" />
                </vector>
            """.trimIndent()
        ),
        VectorIconPreset(
            id = "home",
            label = "Home Icon",
            suggestedName = "ic_home_badge",
            xmlContent = """
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="24dp"
                    android:height="24dp"
                    android:viewportWidth="24"
                    android:viewportHeight="24">
                    <path
                        android:fillColor="#10B981"
                        android:pathData="M10,20v-6h4v6h5v-8h3L12,3L2,12h3v8z" />
                </vector>
            """.trimIndent()
        ),
        VectorIconPreset(
            id = "gradient_card",
            label = "Gradient Card Shape",
            suggestedName = "bg_hero_card",
            xmlContent = """
                <shape xmlns:android="http://schemas.android.com/apk/res/android"
                    android:shape="rectangle">
                    <gradient
                        android:startColor="#6366F1"
                        android:endColor="#06B6D4"
                        android:angle="45" />
                    <corners android:radius="20dp" />
                    <stroke
                        android:width="2dp"
                        android:color="#E0E7FF" />
                </shape>
            """.trimIndent()
        )
    )

    private fun encodeBase64(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

            out.append(BASE64_ALPHABET[b0 ushr 2])
            out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(if (hasB1) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            out.append(if (hasB2) BASE64_ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    private fun decodeBase64(input: String): ByteArray {
        val cleaned = input.filter { !it.isWhitespace() }
        if (cleaned.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream((cleaned.length * 3) / 4)
        var i = 0
        while (i + 3 < cleaned.length) {
            val c0 = decodeChar(cleaned[i])
            val c1 = decodeChar(cleaned[i + 1])
            val ch2 = cleaned[i + 2]
            val ch3 = cleaned[i + 3]

            out.write((c0 shl 2) or (c1 ushr 4))
            if (ch2 != '=') {
                val c2 = decodeChar(ch2)
                out.write(((c1 and 0x0F) shl 4) or (c2 ushr 2))
                if (ch3 != '=') {
                    val c3 = decodeChar(ch3)
                    out.write(((c2 and 0x03) shl 6) or c3)
                }
            }
            i += 4
        }
        return out.toByteArray()
    }

    private fun decodeChar(ch: Char): Int {
        val code = ch.code
        val value = if (code < 128) BASE64_DECODE_TABLE[code] else -1
        if (value < 0) throw IllegalArgumentException("Invalid Base64 character: $ch")
        return value
    }
}
