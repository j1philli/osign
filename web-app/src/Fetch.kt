package osign.web

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

/** Downloads [url] with the browser's `fetch` API. */
@OptIn(ExperimentalWasmJsInterop::class)
internal suspend fun fetchBytes(url: String): ByteArray {
    val response = window.fetch(url).await()
    check(response.ok) { "HTTP ${response.status} while fetching $url" }
    val bytes = Int8Array(response.arrayBuffer().await())
    return ByteArray(bytes.length) { bytes[it] }
}
