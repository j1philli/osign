package osign.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import osign.shared.model.OsignDocument
import osign.shared.model.OsignNode
import osign.shared.model.sortNodesByZIndex

internal data class PreparedNode(
    val node: OsignNode,
    val isText: Boolean,
    val hasImage: Boolean,
    val fill: Color,
    val stroke: Color,
    val image: ImageBitmap?,
    val textConfig: TextConfig?
)

internal data class PreparedEdge(
    val from: OsignNode,
    val to: OsignNode
)

internal data class TypographyToken(
    val fontSize: Float?,
    val lineHeight: Float?,
    val fontWeight: Int?
)

internal fun OsignNode.dataString(key: String): String? {
    val primitive = data?.get(key) as? JsonPrimitive ?: return null
    return primitive.contentOrNull
}

private fun OsignNode.styleColor(key: String): String? {
    val primitive = style?.get(key) as? JsonPrimitive ?: return null
    return primitive.contentOrNull
}

private fun OsignNode.styleNumber(key: String): Double? {
    val primitive = style?.get(key) as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.toDoubleOrNull()
}

private fun hexToColor(hex: String): Color? {
    val clean = hex.removePrefix("#")
    val parsed = clean.toLongOrNull(16) ?: return null
    return when (clean.length) {
        6 -> Color((0xFF shl 24) or parsed.toInt())
        8 -> Color(parsed.toInt())
        else -> null
    }
}

internal fun extractTypographyTokens(doc: OsignDocument?): Map<String, TypographyToken> {
    if (doc == null) return emptyMap()
    val tokensArray = doc.resources?.get("tokens") as? JsonArray ?: return emptyMap()
    return tokensArray.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val fontSize = obj["fontSize"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
        val lineHeight = obj["lineHeight"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
        val fontWeight = obj["fontWeight"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        id to TypographyToken(fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight)
    }.toMap()
}

internal fun prepareNodes(
    doc: OsignDocument?,
    assetImages: Map<String, ImageBitmap>,
    typographyTokens: Map<String, TypographyToken>
): List<PreparedNode> {
    if (doc == null) return emptyList()
    val orderedNodes = sortNodesByZIndex(doc.nodes)
    return orderedNodes.map { node ->
        val nodeTypeLower = node.type.lowercase()
        val isText = nodeTypeLower == "text"
        val isImageType = nodeTypeLower == "image" || nodeTypeLower == "asset"
        val baseFill = node.styleColor("fill")?.let { hexToColor(it) } ?: Color(0xFFF1F5F9)
        val stroke = node.styleColor("stroke")?.let { hexToColor(it) } ?: Color(0xFFCBD5E1)
        val textColor = node.styleColor("fill")?.let { hexToColor(it) } ?: Color(0xFF111827)
        val tokenId = (node.style?.get("typographyTokenId") as? JsonPrimitive)?.contentOrNull
        val token = tokenId?.let { typographyTokens[it] }
        val baseFontSize = (token?.fontSize ?: node.styleNumber("fontSize") ?: 16.0).toFloat()
        val baseLineHeight = token?.lineHeight
        val image = if (isImageType) {
            val direct = assetImages[node.id]
            val assetId = node.dataString("assetId")
            when {
                assetId != null -> assetImages[assetId] ?: direct
                else -> direct
            }
        } else null
        val textConfig = if (isText) TextConfig(node.label.orEmpty(), baseFontSize, baseLineHeight, textColor) else null
        PreparedNode(
            node = node,
            isText = isText,
            hasImage = isImageType,
            fill = baseFill,
            stroke = stroke,
            image = image,
            textConfig = textConfig
        )
    }
}

internal fun prepareEdges(doc: OsignDocument?): List<PreparedEdge> {
    if (doc == null) return emptyList()
    val nodeById = doc.nodes.associateBy { it.id }
    return doc.edges.mapNotNull { edge ->
        val from = nodeById[edge.fromNodeId]
        val to = nodeById[edge.toNodeId]
        if (from != null && to != null) PreparedEdge(from, to) else null
    }
}
