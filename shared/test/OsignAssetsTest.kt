package osign.shared.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OsignAssetsTest {

    private fun node(id: String, type: String, file: String? = null, uri: String? = null) = OsignNode(
        id = id,
        type = type,
        x = 0.0,
        y = 0.0,
        width = 10.0,
        height = 10.0,
        file = file,
        data = uri?.let { buildJsonObject { put("uri", JsonPrimitive(it)) } },
    )

    @Test
    fun `collects sources with asset nodes taking precedence over resources and images`() {
        val doc = OsignDocument(
            osignVersion = "0.1.0",
            canvasVersion = "1.0",
            nodes = listOf(
                node("shared", "asset", file = "from-asset-node.png"),
                node("img", "image", uri = "https://example.com/img.png"),
                node("res-only", "image", file = "image-fallback.png"),
                node("no-source", "image"),
                node("frame", "frame", file = "ignored.png"),
            ),
            resources = buildJsonObject {
                put("assets", buildJsonArray {
                    add(buildJsonObject { put("id", "shared"); put("file", "from-resources.png") })
                    add(buildJsonObject { put("id", "res-only"); put("uri", "data:image/png;base64,AA==") })
                    add(buildJsonObject { put("id", "no-file") })
                })
            },
        )

        assertEquals(
            mapOf(
                "shared" to "from-asset-node.png",
                "res-only" to "data:image/png;base64,AA==",
                "img" to "https://example.com/img.png",
            ),
            collectAssetSources(doc),
        )
    }

    @Test
    fun `decodes base64 data URIs`() {
        assertContentEquals("hello".encodeToByteArray(), decodeDataUri("data:text/plain;base64,aGVsbG8="))
    }

    @Test
    fun `decodes percent-encoded data URIs`() {
        assertContentEquals(
            "<svg a=\"1\">é</svg>".encodeToByteArray(),
            decodeDataUri("data:image/svg+xml,%3Csvg%20a=%221%22%3E%C3%A9%3C/svg%3E"),
        )
        assertContentEquals("é".encodeToByteArray(), decodeDataUri("data:,é"))
    }

    @Test
    fun `rejects malformed data URIs`() {
        assertFailsWith<IllegalArgumentException> { decodeDataUri("data:text/plain;base64") }
        assertFailsWith<IllegalArgumentException> { decodeDataUri("https://example.com/a.png") }
    }
}
