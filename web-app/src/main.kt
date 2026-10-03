package osign.web

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableSharedFlow
import osign.ui.OsignViewer

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val isDraggingFiles = mutableStateOf(false)
    val droppedFiles = MutableSharedFlow<List<PlatformFile>>(extraBufferCapacity = 1)
    installFileDropListener(
        onDragActiveChange = { isDraggingFiles.value = it },
        onDrop = { droppedFiles.tryEmit(it) },
    )

    ComposeViewport(document.body!!) {
        MaterialTheme {
            OsignViewer(
                remoteAssetLoader = ::fetchBytes,
                droppedFiles = droppedFiles,
                isDraggingFiles = isDraggingFiles.value,
            )
        }
    }
}
