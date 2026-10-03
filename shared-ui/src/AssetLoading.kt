package osign.ui

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import org.jetbrains.compose.resources.decodeToImageBitmap
import osign.shared.model.OsignDocument
import osign.shared.model.collectAssetSources
import osign.shared.model.decodeDataUri

/**
 * Loads the bytes of an asset referenced by an absolute http(s) URL. Provided by the host platform.
 */
fun interface RemoteAssetLoader {
    suspend fun load(url: String): ByteArray
}

internal data class AssetLoadResult(
    val images: Map<String, ImageBitmap>,
    val warnings: List<String>
)

/**
 * Loads every image asset referenced by [doc].
 *
 * Local (relative or `file://`) references are resolved by file name against [companionFiles], the files the
 * user picked together with the document, since a browser can't read arbitrary paths from disk.
 */
internal suspend fun loadAssetImages(
    doc: OsignDocument,
    companionFiles: Map<String, PlatformFile>,
    remoteAssetLoader: RemoteAssetLoader,
): AssetLoadResult {
    val uris = collectAssetSources(doc)
    if (uris.isEmpty()) return AssetLoadResult(emptyMap(), emptyList())
    val images = mutableMapOf<String, ImageBitmap>()
    val warnings = mutableListOf<String>()
    uris.forEach { (id, uri) ->
        val bytes = try {
            resolveAssetBytes(uri, companionFiles, remoteAssetLoader)
        } catch (e: Exception) {
            warnings += "Asset[$id]: ${e.message ?: "Unable to load image."}"
            null
        }
        if (bytes != null) {
            runCatching {
                bytes.decodeToImageBitmap()
            }.onSuccess { image ->
                images[id] = image
            }.onFailure { error ->
                warnings += "Asset[$id]: ${error.message ?: "Unable to decode image."}"
            }
        }
    }
    return AssetLoadResult(images, warnings)
}

private suspend fun resolveAssetBytes(
    uri: String,
    companionFiles: Map<String, PlatformFile>,
    remoteAssetLoader: RemoteAssetLoader,
): ByteArray {
    return when {
        uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
        uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
            remoteAssetLoader.load(uri)
        }
        else -> {
            val fileName = uri.substringAfterLast('/').substringAfterLast('\\')
            val file = requireNotNull(companionFiles[fileName]) {
                "Select '$fileName' together with the document to load it."
            }
            file.readBytes()
        }
    }
}
