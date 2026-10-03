package osign.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.ceil
import kotlin.math.max

internal data class TextConfig(
    val text: String,
    val baseFontSize: Float,
    val baseLineHeight: Float?,
    val textColor: Color
)

internal data class TextLayoutKey(
    val nodeId: String,
    val widthPx: Int,
    val heightPx: Int,
)

internal const val TEXT_LAYOUT_CACHE_CAPACITY = 200

/** A small least-recently-used cache (common Kotlin has no access-ordered LinkedHashMap). */
internal class LruCache<K, V>(private val capacity: Int) {
    private val entries = LinkedHashMap<K, V>()

    operator fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    operator fun set(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        if (entries.size > capacity) entries.remove(entries.keys.first())
    }

    fun clear() = entries.clear()
}

/**
 * Lays out [config]'s text to fit a node of [width] x [height] canvas units, wrapping lines and ellipsizing the
 * last visible line. Returns null when there is nothing to draw.
 */
internal fun measureNodeText(
    textMeasurer: TextMeasurer,
    density: Density,
    config: TextConfig,
    width: Float,
    height: Float,
): TextLayoutResult? {
    if (width <= 0f || height <= 0f || config.text.isBlank()) return null
    val lineHeight = config.baseLineHeight ?: (config.baseFontSize * 1.2f)
    val maxLines = max(1, (height / lineHeight).toInt())
    // Node geometry is in canvas units, which map 1:1 to pixels before the pan/zoom transform.
    val style = with(density) {
        TextStyle(
            color = config.textColor,
            fontSize = config.baseFontSize.toSp(),
            lineHeight = lineHeight.toSp(),
        )
    }
    return textMeasurer.measure(
        text = config.text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = ceil(width).toInt()),
        density = density,
    )
}
