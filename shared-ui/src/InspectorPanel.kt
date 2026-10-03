package osign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import osign.shared.model.OsignDocument

@Composable
internal fun InspectorPanel(doc: OsignDocument?, selectedNodeId: String?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Inspector", style = MaterialTheme.typography.titleMedium)
            if (doc == null) {
                Text("Load a file to inspect nodes.")
                return@Column
            }
            val node = doc.nodes.find { it.id == selectedNodeId }
            if (node == null) {
                Text("Select a node to view its details.")
                return@Column
            }
            Text("id: ${node.id}")
            Text("type: ${node.type}")
            Text("position: (${node.x}, ${node.y})")
            Text("size: ${node.width} x ${node.height}")
            node.file?.let { Text("file: $it") }
            node.label?.let { Text("label: $it") }
            node.style?.let { Text("style: $it") }
            node.component?.let { Text("component: $it") }
            node.data?.let { Text("data: $it") }
        }
    }
}
