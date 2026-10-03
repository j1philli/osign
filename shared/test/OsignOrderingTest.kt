package osign.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OsignOrderingTest {

    @Test
    fun `sortNodesByZIndex orders by z then document order`() {
        val nodes = listOf(
            OsignNode(id = "a", type = "shape", x = 0.0, y = 0.0, width = 10.0, height = 10.0, zIndex = null),
            OsignNode(id = "b", type = "shape", x = 0.0, y = 0.0, width = 10.0, height = 10.0, zIndex = 2.0),
            OsignNode(id = "c", type = "shape", x = 0.0, y = 0.0, width = 10.0, height = 10.0, zIndex = -1.0),
            OsignNode(id = "d", type = "shape", x = 0.0, y = 0.0, width = 10.0, height = 10.0, zIndex = 2.0)
        )

        val sorted = sortNodesByZIndex(nodes)

        assertEquals(listOf("c", "a", "b", "d"), sorted.map { it.id })
    }
}

