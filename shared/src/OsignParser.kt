package osign.shared.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val json = Json {
    ignoreUnknownKeys = true
}

/** The outcome of [parseOsign]. */
sealed interface OsignParseResult {
    data class Success(val document: OsignDocument) : OsignParseResult

    /** [message] explains what is wrong and, when known, where (as a JSON path such as `$.nodes[3]`). */
    data class Failure(val message: String) : OsignParseResult
}

/**
 * Parse an osign document from raw JSON text.
 */
fun parseOsign(text: String): OsignParseResult {
    return try {
        OsignParseResult.Success(json.decodeFromString<OsignDocument>(text))
    } catch (e: SerializationException) {
        OsignParseResult.Failure(describeParseError(e))
    } catch (e: IllegalArgumentException) {
        OsignParseResult.Failure(describeParseError(e))
    }
}

private val serialNameRegex = Regex("""for type with serial name '[\w.]*\.Osign(\w+)'""")

/**
 * Turns a kotlinx.serialization error into a message for people editing osign files: drops the echoed JSON input
 * and the configuration hints aimed at developers, and names model types the way the spec does.
 */
private fun describeParseError(e: Exception): String {
    val raw = e.message?.takeIf { it.isNotBlank() } ?: return "File is not valid JSON or does not match the osign shape."
    return raw
        .substringBefore("\nJSON input:")
        .substringBefore("\nUse '")
        .replace(serialNameRegex) { "for ${it.groupValues[1].lowercase()}" }
        .trim()
}

/**
 * Validate required fields and cross-references of an osign document and return warnings.
 */
fun validateOsign(doc: OsignDocument): ValidationResult {
    val warnings = buildList {
        if (doc.osignVersion.isBlank()) add("osign_version is missing or blank.")
        if (doc.canvasVersion.isBlank()) add("canvas_version is missing or blank.")
        if (doc.nodes.isEmpty()) add("No nodes present in document.")

        doc.nodes.forEachIndexed { index, node ->
            val nodeType = node.type.lowercase()
            if (node.id.isBlank()) add("Node[$index] is missing id.")
            if (node.type.isBlank()) add("Node[${node.id}] is missing type.")
            if (node.width <= 0.0) add("Node[${node.id}] width should be > 0.")
            if (node.height <= 0.0) add("Node[${node.id}] height should be > 0.")
            if (nodeType in setOf("image", "asset", "file") && node.file.isNullOrBlank()) {
                add("Node[${node.id}] requires a file path (e.g. \"file\": \"resources/images/example.png\").")
            }
        }

        duplicateIds(doc.nodes.map { it.id }).forEach { (id, count) ->
            add("Node id '$id' is used by $count nodes; ids must be unique.")
        }
        duplicateIds(doc.edges.map { it.id }).forEach { (id, count) ->
            add("Edge id '$id' is used by $count edges; ids must be unique.")
        }

        val nodeIds = doc.nodes.mapTo(HashSet()) { it.id }
        doc.nodes.forEach { node ->
            val parentId = node.parentId
            if (!parentId.isNullOrBlank() && parentId !in nodeIds) {
                add("Node[${node.id}] parentId '$parentId' does not match any node.")
            }
            node.children.orEmpty().filter { it !in nodeIds }.forEach { childId ->
                add("Node[${node.id}] lists child '$childId', which does not match any node.")
            }
        }

        val assetSources = collectAssetSources(doc)
        doc.nodes.filter { it.type.equals("image", ignoreCase = true) }.forEach { node ->
            val assetId = (node.data?.get("assetId") as? JsonPrimitive)?.contentOrNull
            if (!assetId.isNullOrBlank() && assetSources[assetId].isNullOrBlank()) {
                add("Node[${node.id}] references assetId '$assetId' but no asset with a file was found (node or resources.assets).")
            }
        }

        doc.edges.forEachIndexed { index, edge ->
            if (edge.id.isBlank()) add("Edge[$index] is missing id.")
            if (edge.fromNodeId.isBlank()) {
                add("Edge[${edge.id}] is missing fromNodeId.")
            } else if (edge.fromNodeId !in nodeIds) {
                add("Edge[${edge.id}] fromNodeId '${edge.fromNodeId}' does not match any node.")
            }
            if (edge.toNodeId.isBlank()) {
                add("Edge[${edge.id}] is missing toNodeId.")
            } else if (edge.toNodeId !in nodeIds) {
                add("Edge[${edge.id}] toNodeId '${edge.toNodeId}' does not match any node.")
            }
        }
    }
    return ValidationResult(warnings)
}

/** Non-blank ids that appear more than once, with their counts, in first-seen order. */
private fun duplicateIds(ids: List<String>): Map<String, Int> =
    ids.filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
