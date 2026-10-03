package osign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import osign.shared.model.OsignDocument
import osign.shared.model.OsignParseResult
import osign.shared.model.parseOsign
import osign.shared.model.validateOsign

private val PICKABLE_EXTENSIONS = listOf("json", "png", "jpg", "jpeg", "gif", "webp", "bmp")

/**
 * The osign viewer: open an osign document (plus any local images it references), then inspect it.
 *
 * @param remoteAssetLoader fetches assets referenced by absolute http(s) URLs.
 * @param droppedFiles files the host received via drag and drop; each emission is opened like a picker selection.
 * @param isDraggingFiles whether files are currently being dragged over the host, to show a drop hint.
 */
@Composable
fun OsignViewer(
    remoteAssetLoader: RemoteAssetLoader,
    droppedFiles: Flow<List<PlatformFile>> = emptyFlow(),
    isDraggingFiles: Boolean = false,
) {
    var doc by remember { mutableStateOf<OsignDocument?>(null) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var panZoom by remember { mutableStateOf(PanZoom()) }
    var filename by remember { mutableStateOf<String?>(null) }
    var fitRequest by remember { mutableStateOf(0) }
    var assetImages by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    suspend fun openFiles(files: List<PlatformFile>) {
        val docFile = files.firstOrNull { it.name.endsWith(".osign.json", ignoreCase = true) }
            ?: files.firstOrNull { it.name.endsWith(".json", ignoreCase = true) }
        if (docFile == null) {
            doc = null
            warnings = listOf("Open an .osign.json file (along with any local images it references).")
            selectedNodeId = null
            filename = null
            assetImages = emptyMap()
            return
        }
        when (val result = parseOsign(docFile.readString())) {
            is OsignParseResult.Failure -> {
                doc = null
                warnings = listOf("Could not read ${docFile.name}: ${result.message}")
                selectedNodeId = null
                filename = docFile.name
                assetImages = emptyMap()
            }
            is OsignParseResult.Success -> {
                val parsed = result.document
                val validationWarnings = validateOsign(parsed).warnings
                val companionFiles = files.filter { it !== docFile }.associateBy { it.name }
                val assetResult = loadAssetImages(parsed, companionFiles, remoteAssetLoader)
                doc = parsed
                warnings = validationWarnings + assetResult.warnings
                assetImages = assetResult.images
                selectedNodeId = null
                fitRequest++
                filename = docFile.name
            }
        }
    }

    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.File(PICKABLE_EXTENSIONS),
        mode = FileKitMode.Multiple(),
    ) { files: List<PlatformFile>? ->
        if (!files.isNullOrEmpty()) scope.launch { openFiles(files) }
    }

    LaunchedEffect(droppedFiles) {
        droppedFiles.collect { files -> if (files.isNotEmpty()) openFiles(files) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ViewerContent(
            doc = doc,
            warnings = warnings,
            selectedNodeId = selectedNodeId,
            panZoom = panZoom,
            fitRequest = fitRequest,
            filename = filename,
            assetImages = assetImages,
            onOpenFile = { filePicker.launch() },
            onClear = {
                doc = null
                warnings = emptyList()
                selectedNodeId = null
                panZoom = PanZoom()
                fitRequest++
                filename = null
                assetImages = emptyMap()
            },
            onPanZoomChange = { panZoom = it },
            onRequestFit = { fitRequest++ },
            onSelectNode = { selectedNodeId = it },
        )
        if (isDraggingFiles) DropHint()
    }
}

@Composable
private fun ViewerContent(
    doc: OsignDocument?,
    warnings: List<String>,
    selectedNodeId: String?,
    panZoom: PanZoom,
    fitRequest: Int,
    filename: String?,
    assetImages: Map<String, ImageBitmap>,
    onOpenFile: () -> Unit,
    onClear: () -> Unit,
    onPanZoomChange: (PanZoom) -> Unit,
    onRequestFit: () -> Unit,
    onSelectNode: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(
            filename = filename,
            warnings = warnings,
            onOpenFile = onOpenFile,
            onClear = onClear,
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoPanel(
                doc = doc,
                warnings = warnings,
                modifier = Modifier.weight(1f)
            )
            CanvasPanel(
                doc = doc,
                selectedNodeId = selectedNodeId,
                panZoom = panZoom,
                fitRequest = fitRequest,
                assetImages = assetImages,
                onPanZoomChange = onPanZoomChange,
                onRequestFit = onRequestFit,
                onSelectNode = onSelectNode,
                modifier = Modifier.weight(2f)
            )
            InspectorPanel(
                doc = doc,
                selectedNodeId = selectedNodeId,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DropHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1E3A8A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Drop to open", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("An .osign.json file, plus any local images it references", color = Color(0xFFDBEAFE))
        }
    }
}

@Composable
private fun Header(
    filename: String?,
    warnings: List<String>,
    onOpenFile: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("osign", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            Text("Viewer", style = MaterialTheme.typography.titleLarge)
            Text("Open or drop an .osign.json file (plus any local images it references), validate it, and inspect nodes/edges.")
            filename?.let { Text("Loaded: $it", color = Color(0xFF475569)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenFile) { Text("Open files") }
            Button(onClick = onClear) { Text("Clear") }
        }
    }
    if (warnings.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Warnings", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                warnings.forEach { Text(it, color = Color(0xFF7F1D1D)) }
            }
        }
    }
}

@Composable
private fun InfoPanel(doc: OsignDocument?, warnings: List<String>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("File info", style = MaterialTheme.typography.titleMedium)
            if (doc == null) {
                Text("No file loaded. Choose a .osign.json to view.")
                Text("Try the sample at examples/hello-world.osign.json.")
            } else {
                Text("osign_version: ${doc.osignVersion}")
                Text("canvas_version: ${doc.canvasVersion}")
                Text("nodes: ${doc.nodes.size}  edges: ${doc.edges.size}")
                doc.meta?.let { Text("meta keys: ${it.keys.joinToString()}") }
                if (warnings.isEmpty()) Text("Validation: OK", color = Color(0xFF16A34A))
            }
        }
    }
}
