package osign.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import osign.shared.model.OsignDocument
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PanZoom(
    val pan: Offset = Offset(40f, 40f),
    val zoom: Float = 1f,
)

private const val TEXT_ZOOM_THRESHOLD = 0.12f

@Composable
internal fun CanvasPanel(
    doc: OsignDocument?,
    selectedNodeId: String?,
    panZoom: PanZoom,
    fitRequest: Int,
    assetImages: Map<String, ImageBitmap>,
    onPanZoomChange: (PanZoom) -> Unit,
    onRequestFit: () -> Unit,
    onSelectNode: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Canvas", style = MaterialTheme.typography.titleMedium)
            if (doc == null) {
                Text("Load a file to view the canvas.")
            } else {
                val typographyTokens = remember(doc) { extractTypographyTokens(doc) }
                val preparedNodes = remember(doc, assetImages, typographyTokens) {
                    prepareNodes(doc, assetImages, typographyTokens)
                }
                val preparedEdges = remember(doc) { prepareEdges(doc) }
                val textMeasurer = rememberTextMeasurer()
                val textLayoutCache = remember { LruCache<TextLayoutKey, TextLayoutResult?>(TEXT_LAYOUT_CACHE_CAPACITY) }
                LaunchedEffect(doc) { textLayoutCache.clear() }
                val gestureScope = rememberCoroutineScope()
                var isZooming by remember { mutableStateOf(false) }
                var zoomIdleJob by remember { mutableStateOf<Job?>(null) }

                val density = LocalDensity.current
                val paddingPx = with(density) { 32.dp.toPx() }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val canvasWidthPx = constraints.maxWidth.toFloat()
                    val canvasHeightPx = constraints.maxHeight.toFloat()

                    LaunchedEffect(doc, fitRequest, canvasWidthPx, canvasHeightPx) {
                        if (canvasWidthPx > 0 && canvasHeightPx > 0) {
                            val fit = computeFitPanZoom(doc, canvasWidthPx, canvasHeightPx, paddingPx)
                            onPanZoomChange(fit)
                        }
                    }

                    val currentPanZoom by rememberUpdatedState(panZoom)

                    fun markZooming() {
                        isZooming = true
                        zoomIdleJob?.cancel()
                        zoomIdleJob = gestureScope.launch {
                            delay(140)
                            isZooming = false
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .background(Color(0xFFF8FAFC))
                            .pointerInput(doc) {
                                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                    markZooming()
                                    val current = currentPanZoom
                                    val translatedPan = current.pan + pan
                                    val newZoom = (current.zoom * gestureZoom).coerceIn(0.25f, 4f)
                                    if (newZoom.isNaN() || current.zoom == 0f) {
                                        onPanZoomChange(current.copy(pan = translatedPan))
                                        return@detectTransformGestures
                                    }
                                    val focusWorld = (centroid - translatedPan) / current.zoom
                                    val adjustedPan = centroid - focusWorld * newZoom
                                    onPanZoomChange(PanZoom(pan = adjustedPan, zoom = newZoom))
                                }
                            }
                            .pointerInput(doc) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newPan = currentPanZoom.pan + dragAmount
                                    onPanZoomChange(currentPanZoom.copy(pan = newPan))
                                }
                            }
                            .pointerInput(doc) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        if (event.type != PointerEventType.Scroll) continue
                                        val change = event.changes.firstOrNull() ?: continue
                                        val scrollY = change.scrollDelta.y
                                        if (scrollY == 0f) continue
                                        markZooming()
                                        // Trackpads often send small deltas; map logarithmically for predictability.
                                        val zoomFactor = exp(-scrollY * 0.08f).coerceIn(0.75f, 1.25f)
                                        val current = currentPanZoom
                                        val newZoom = (current.zoom * zoomFactor).coerceIn(0.25f, 4f)
                                        if (newZoom == current.zoom) continue
                                        val focusWorld = (change.position - current.pan) / current.zoom
                                        val adjustedPan = change.position - focusWorld * newZoom
                                        onPanZoomChange(PanZoom(pan = adjustedPan, zoom = newZoom))
                                    }
                                }
                            }
                            .pointerInput(doc) {
                                detectTapGestures { offset ->
                                    val clicked = preparedNodes.lastOrNull { prepared ->
                                        val node = prepared.node
                                        val world = (offset - currentPanZoom.pan) / currentPanZoom.zoom
                                        val rectLeft = node.x.toFloat()
                                        val rectTop = node.y.toFloat()
                                        val rectRight = rectLeft + node.width.toFloat()
                                        val rectBottom = rectTop + node.height.toFloat()
                                        world.x in rectLeft..rectRight && world.y in rectTop..rectBottom
                                    }
                                    onSelectNode(clicked?.node?.id)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            withTransform({
                                translate(panZoom.pan.x, panZoom.pan.y)
                                scale(panZoom.zoom, panZoom.zoom, pivot = Offset.Zero)
                            }) {
                                val edgeStroke = (2f / panZoom.zoom).coerceAtLeast(0.5f)
                                preparedEdges.forEach { edge ->
                                    val x1 = (edge.from.x + edge.from.width / 2.0).toFloat()
                                    val y1 = (edge.from.y + edge.from.height / 2.0).toFloat()
                                    val x2 = (edge.to.x + edge.to.width / 2.0).toFloat()
                                    val y2 = (edge.to.y + edge.to.height / 2.0).toFloat()
                                    drawLine(
                                        color = Color(0xFFCBD5E1),
                                        start = Offset(x1, y1),
                                        end = Offset(x2, y2),
                                        strokeWidth = edgeStroke
                                    )
                                }
                                preparedNodes.forEach { prepared ->
                                    val node = prepared.node
                                    val left = node.x.toFloat()
                                    val top = node.y.toFloat()
                                    val width = node.width.toFloat()
                                    val height = node.height.toFloat()
                                    val fill = if (prepared.isText) Color.Transparent else prepared.fill
                                    val stroke = if (node.id == selectedNodeId) Color(0xFF2563EB) else prepared.stroke
                                    val strokeWidth = (if (node.id == selectedNodeId) 4f else 2f) / panZoom.zoom

                                    if (!prepared.hasImage && fill.alpha > 0f) {
                                        drawRect(
                                            color = fill,
                                            topLeft = Offset(left, top),
                                            size = Size(width, height)
                                        )
                                    }
                                    if (prepared.image != null) {
                                        drawImage(
                                            image = prepared.image,
                                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                            dstSize = IntSize(
                                                width.roundToInt().coerceAtLeast(1),
                                                height.roundToInt().coerceAtLeast(1)
                                            ),
                                            filterQuality = FilterQuality.High
                                        )
                                    } else if (prepared.hasImage) {
                                        drawRect(
                                            color = fill,
                                            topLeft = Offset(left, top),
                                            size = Size(width, height)
                                        )
                                    }
                                    drawRect(
                                        color = stroke,
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        style = Stroke(width = strokeWidth)
                                    )

                                    val textConfig = prepared.textConfig
                                    if (textConfig != null && textConfig.text.isNotBlank() && panZoom.zoom >= TEXT_ZOOM_THRESHOLD && !isZooming) {
                                        val key = TextLayoutKey(
                                            nodeId = node.id,
                                            widthPx = node.width.toInt(),
                                            heightPx = node.height.toInt()
                                        )
                                        val layout = textLayoutCache[key]
                                            ?: measureNodeText(textMeasurer, density, textConfig, width, height)
                                                .also { computed -> textLayoutCache[key] = computed }
                                        if (layout != null) {
                                            drawText(layout, topLeft = Offset(left, top))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val newZoom = (panZoom.zoom * 0.9f).coerceIn(0.25f, 4f)
                        onPanZoomChange(panZoom.copy(zoom = newZoom))
                    }) { Text("Zoom -") }
                    Button(onClick = onRequestFit) { Text("Fit view") }
                    Button(onClick = {
                        val newZoom = (panZoom.zoom * 1.1f).coerceIn(0.25f, 4f)
                        onPanZoomChange(panZoom.copy(zoom = newZoom))
                    }) { Text("Zoom +") }
                }
            }
        }
    }
}

private fun computeFitPanZoom(
    doc: OsignDocument,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    paddingPx: Float
): PanZoom {
    if (doc.nodes.isEmpty()) return PanZoom()
    val minX = doc.nodes.minOf { it.x }.toFloat()
    val minY = doc.nodes.minOf { it.y }.toFloat()
    val maxX = doc.nodes.maxOf { it.x + it.width }.toFloat()
    val maxY = doc.nodes.maxOf { it.y + it.height }.toFloat()
    val contentWidth = (maxX - minX).coerceAtLeast(1f)
    val contentHeight = (maxY - minY).coerceAtLeast(1f)
    val availableWidth = (canvasWidthPx - paddingPx * 2).coerceAtLeast(1f)
    val availableHeight = (canvasHeightPx - paddingPx * 2).coerceAtLeast(1f)
    val scale = min(availableWidth / contentWidth, availableHeight / contentHeight).coerceIn(0.1f, 8f)
    val offsetX = (canvasWidthPx - contentWidth * scale) / 2f - minX * scale
    val offsetY = (canvasHeightPx - contentHeight * scale) / 2f - minY * scale
    return PanZoom(pan = Offset(offsetX, offsetY), zoom = scale)
}
