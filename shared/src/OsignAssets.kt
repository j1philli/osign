package osign.shared.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/**
 * Collects where each asset's bytes come from, keyed by asset id.
 *
 * Sources are, in order of precedence:
 * 1. `asset` nodes (`file`, or `data.uri`), keyed by node id;
 * 2. `resources.assets` entries (`file`, or `uri`), keyed by their `id`;
 * 3. `image` nodes (`file`, or `data.uri`), keyed by node id.
 *
 * A source is a relative path, a `file://` or `http(s)://` URL, or a `data:` URI (see [decodeDataUri]).
 */
fun collectAssetSources(doc: OsignDocument): Map<String, String> {
    val sources = LinkedHashMap<String, String>()
    val resourceAssets = doc.resources?.get("assets") as? JsonArray
    resourceAssets?.forEach { element ->
        val obj = element as? JsonObject ?: return@forEach
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val source = obj["file"]?.jsonPrimitive?.contentOrNull
            ?: obj["uri"]?.jsonPrimitive?.contentOrNull
            ?: return@forEach
        if (id !in sources) sources[id] = source
    }
    doc.nodes.filter { it.type.equals("asset", ignoreCase = true) }.forEach { node ->
        val source = node.file ?: node.dataUri() ?: return@forEach
        sources[node.id] = source
    }
    doc.nodes.filter { it.type.equals("image", ignoreCase = true) }.forEach { node ->
        val source = node.file ?: node.dataUri() ?: return@forEach
        if (node.id !in sources) sources[node.id] = source
    }
    return sources
}

private fun OsignNode.dataUri(): String? = (data?.get("uri") as? JsonPrimitive)?.contentOrNull

/**
 * Decodes the payload of a `data:` URI, either base64 (`data:image/png;base64,...`) or percent-encoded.
 *
 * @throws IllegalArgumentException if [uri] is not a well-formed data URI.
 */
fun decodeDataUri(uri: String): ByteArray {
    require(uri.startsWith("data:", ignoreCase = true)) { "Not a data URI." }
    val commaIndex = uri.indexOf(',')
    require(commaIndex != -1) { "Malformed data URI." }
    val metadata = uri.substring(5, commaIndex)
    val dataPart = uri.substring(commaIndex + 1)
    return if (metadata.endsWith(";base64", ignoreCase = true)) {
        Base64.decode(dataPart)
    } else {
        percentDecode(dataPart)
    }
}

private fun percentDecode(value: String): ByteArray {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 <= value.lastIndex) {
            val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes += decoded.toByte()
                index += 3
                continue
            }
        }
        val nextEscape = value.indexOf('%', startIndex = index + 1).let { if (it == -1) value.length else it }
        value.substring(index, nextEscape).encodeToByteArray().forEach { bytes += it }
        index = nextEscape
    }
    return bytes.toByteArray()
}
