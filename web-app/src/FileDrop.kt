package osign.web

import io.github.vinceglb.filekit.BrowserFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import kotlinx.browser.window
import org.w3c.dom.DragEvent
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.get
import kotlin.js.unsafeCast

/**
 * Listens for files dragged over the page. [onDragActiveChange] reports whether files are hovering over the
 * window, and [onDrop] receives the dropped files.
 */
internal fun installFileDropListener(
    onDragActiveChange: (Boolean) -> Unit,
    onDrop: (List<PlatformFile>) -> Unit,
) {
    // dragenter/dragleave fire for every child element crossed, so track nesting depth to know when the drag leaves.
    var depth = 0

    window.addEventListener("dragenter", { event ->
        if (!carriesFiles(event)) return@addEventListener
        event.preventDefault()
        depth++
        onDragActiveChange(true)
    }, true)
    window.addEventListener("dragover", { event ->
        // Cancelling dragover is what allows the drop.
        if (carriesFiles(event)) event.preventDefault()
    }, true)
    window.addEventListener("dragleave", { event ->
        if (!carriesFiles(event)) return@addEventListener
        depth = (depth - 1).coerceAtLeast(0)
        if (depth == 0) onDragActiveChange(false)
    }, true)
    window.addEventListener("drop", { event ->
        if (!carriesFiles(event)) return@addEventListener
        event.preventDefault()
        depth = 0
        onDragActiveChange(false)
        val fileList = (event as DragEvent).dataTransfer?.files ?: return@addEventListener
        val files = (0 until fileList.length).mapNotNull { index -> fileList[index] }
        onDrop(files.map { it.toPlatformFile() })
    }, true)
}

// FileKit declares its own external type for the same JS File object.
@OptIn(ExperimentalWasmJsInterop::class)
private fun File.toPlatformFile(): PlatformFile = PlatformFile(WebFile.FileWrapper(unsafeCast<BrowserFile>()))

@OptIn(ExperimentalWasmJsInterop::class)
private fun carriesFiles(event: Event): Boolean =
    js("event.dataTransfer != null && Array.prototype.indexOf.call(event.dataTransfer.types, 'Files') !== -1")
