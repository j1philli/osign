package osign.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OsignDocument(
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("osign_version")
    val osignVersion: String,
    @SerialName("canvas_version")
    val canvasVersion: String,
    val meta: JsonObject? = null,
    val nodes: List<OsignNode> = emptyList(),
    val edges: List<OsignEdge> = emptyList(),
    val resources: JsonObject? = null,
)

@Serializable
data class OsignNode(
    val id: String,
    val type: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val file: String? = null,
    @SerialName("z_index")
    val zIndex: Double? = null,
    val label: String? = null,
    val parentId: String? = null,
    val children: List<String>? = null,
    val style: JsonObject? = null,
    val layout: JsonObject? = null,
    val component: JsonObject? = null,
    val data: JsonObject? = null,
)

@Serializable
data class OsignEdge(
    val id: String,
    @SerialName("fromNodeId")
    val fromNodeId: String,
    @SerialName("toNodeId")
    val toNodeId: String,
    val label: String? = null,
    val style: JsonObject? = null,
    val data: JsonObject? = null,
)

data class ValidationResult(
    val warnings: List<String>,
    val isValid: Boolean = warnings.isEmpty(),
)

/**
 * Returns nodes sorted back-to-front by z-index (default 0) while preserving
 * original document order for ties.
 */
fun sortNodesByZIndex(nodes: List<OsignNode>): List<OsignNode> {
    return nodes.withIndex()
        .sortedWith(compareBy({ it.value.zIndex ?: 0.0 }, { it.index }))
        .map { it.value }
}
