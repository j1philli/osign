package osign.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OsignValidationTest {

    @Test
    fun `assetId is satisfied by resources assets`() {
        val doc = OsignDocument(
            osignVersion = "1.0",
            canvasVersion = "1.0",
            nodes = listOf(
                OsignNode(
                    id = "image-1",
                    type = "image",
                    x = 0.0,
                    y = 0.0,
                    width = 100.0,
                    height = 100.0,
                    file = "resources/images/avatar.png",
                    data = buildJsonObject { put("assetId", JsonPrimitive("asset-res")) }
                )
            ),
            resources = buildJsonObject {
                put(
                    "assets",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive("asset-res"))
                                put("file", JsonPrimitive("resources/images/avatar.png"))
                            }
                        )
                    }
                )
            }
        )

        val warnings = validateOsign(doc).warnings

        assertTrue(warnings.isEmpty(), "Expected no warnings, got: $warnings")
    }

    @Test
    fun `warn when assetId missing in nodes and resources`() {
        val doc = OsignDocument(
            osignVersion = "1.0",
            canvasVersion = "1.0",
            nodes = listOf(
                OsignNode(
                    id = "image-1",
                    type = "image",
                    x = 0.0,
                    y = 0.0,
                    width = 100.0,
                    height = 100.0,
                    file = "resources/images/avatar.png",
                    data = buildJsonObject { put("assetId", JsonPrimitive("missing-asset")) }
                )
            )
        )

        val warnings = validateOsign(doc).warnings

        assertTrue(
            warnings.any { it.contains("missing-asset") },
            "Expected warning about missing assetId, got: $warnings"
        )
    }

    private fun node(id: String, parentId: String? = null, children: List<String>? = null) = OsignNode(
        id = id,
        type = "shape",
        x = 0.0,
        y = 0.0,
        width = 10.0,
        height = 10.0,
        parentId = parentId,
        children = children,
    )

    private fun doc(nodes: List<OsignNode>, edges: List<OsignEdge> = emptyList()) =
        OsignDocument(osignVersion = "0.1.0", canvasVersion = "1.0", nodes = nodes, edges = edges)

    @Test
    fun `valid cross-references produce no warnings`() {
        val warnings = validateOsign(
            doc(
                nodes = listOf(node("frame", children = listOf("child")), node("child", parentId = "frame")),
                edges = listOf(OsignEdge(id = "e1", fromNodeId = "frame", toNodeId = "child")),
            )
        ).warnings

        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `warn on duplicate node and edge ids`() {
        val warnings = validateOsign(
            doc(
                nodes = listOf(node("a"), node("a"), node("b")),
                edges = listOf(
                    OsignEdge(id = "e", fromNodeId = "a", toNodeId = "b"),
                    OsignEdge(id = "e", fromNodeId = "b", toNodeId = "a"),
                ),
            )
        ).warnings

        assertEquals(
            listOf(
                "Node id 'a' is used by 2 nodes; ids must be unique.",
                "Edge id 'e' is used by 2 edges; ids must be unique.",
            ),
            warnings,
        )
    }

    @Test
    fun `warn when edges point at missing nodes`() {
        val warnings = validateOsign(
            doc(
                nodes = listOf(node("a")),
                edges = listOf(OsignEdge(id = "e1", fromNodeId = "ghost", toNodeId = "phantom")),
            )
        ).warnings

        assertEquals(
            listOf(
                "Edge[e1] fromNodeId 'ghost' does not match any node.",
                "Edge[e1] toNodeId 'phantom' does not match any node.",
            ),
            warnings,
        )
    }

    @Test
    fun `warn when parentId or children point at missing nodes`() {
        val warnings = validateOsign(
            doc(nodes = listOf(node("a", parentId = "ghost", children = listOf("b", "phantom")), node("b")))
        ).warnings

        assertEquals(
            listOf(
                "Node[a] parentId 'ghost' does not match any node.",
                "Node[a] lists child 'phantom', which does not match any node.",
            ),
            warnings,
        )
    }
}
