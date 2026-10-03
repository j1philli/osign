package osign.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OsignParserTest {

    @Test
    fun `parses a minimal document`() {
        val result = parseOsign(
            """
            {
              "osign_version": "0.1.0",
              "canvas_version": "1.0",
              "nodes": [{ "id": "a", "type": "shape", "x": 0, "y": 0, "width": 10, "height": 10, "extra": true }]
            }
            """.trimIndent()
        )

        val document = assertIs<OsignParseResult.Success>(result).document
        assertEquals("0.1.0", document.osignVersion)
        assertEquals(listOf("a"), document.nodes.map { it.id })
    }

    @Test
    fun `reports a missing top-level field by name`() {
        val result = parseOsign("""{ "osign_version": "0.1.0", "nodes": [] }""")

        val message = assertIs<OsignParseResult.Failure>(result).message
        assertTrue("canvas_version" in message, message)
        assertTrue("osign.shared.model" !in message, "Internal type names should not leak: $message")
    }

    @Test
    fun `reports the path of a missing node field`() {
        val result = parseOsign(
            """
            {
              "osign_version": "0.1.0",
              "canvas_version": "1.0",
              "nodes": [
                { "id": "a", "type": "shape", "x": 0, "y": 0, "width": 10, "height": 10 },
                { "id": "b", "type": "shape", "y": 0, "width": 10, "height": 10 }
              ]
            }
            """.trimIndent()
        )

        val message = assertIs<OsignParseResult.Failure>(result).message
        assertTrue("'x'" in message, message)
        assertTrue("$.nodes[1]" in message, message)
    }

    @Test
    fun `reports malformed JSON without echoing the input`() {
        val result = parseOsign("""{ "osign_version": "0.1.0", "canvas_version": """)

        val message = assertIs<OsignParseResult.Failure>(result).message
        assertTrue(message.isNotBlank())
        assertTrue("JSON input" !in message, message)
    }

    @Test
    fun `reports a wrongly typed value`() {
        val result = parseOsign(
            """
            {
              "osign_version": "0.1.0",
              "canvas_version": "1.0",
              "nodes": [{ "id": "a", "type": "shape", "x": "left", "y": 0, "width": 10, "height": 10 }]
            }
            """.trimIndent()
        )

        val message = assertIs<OsignParseResult.Failure>(result).message
        assertTrue("$.nodes[0].x" in message, message)
    }
}
