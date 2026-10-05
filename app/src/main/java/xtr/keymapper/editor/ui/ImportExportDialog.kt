package xtr.keymapper.editor

import android.content.Context
import android.content.Intent
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ImportExportDialog(
    code: String,
    onDismissRequest: () -> Unit,
    onImportClicked: (String) -> String?,
    onExportClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Helper function to launch the system Sharesheet
    fun launchSharesheet(textToShare: String, context: Context) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, textToShare)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Configuration")
        if (context !is Activity) shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(decorFitsSystemWindows = false)
    ) {
        ImportExportContent(
            code = code,
            onImportClicked = onImportClicked,
            onExportClicked = onExportClicked,
            onShareClicked = { launchSharesheet(it, context) },
            onDismissRequest = onDismissRequest,
            modifier = modifier
        )
    }
}

@Composable
fun ImportExportContent(
    code: String,
    onImportClicked: (String) -> String?,
    onExportClicked: (String) -> Unit,
    onShareClicked: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedCode by remember(code) { mutableStateOf(code) }
    var importError by remember { mutableStateOf<String?>(null) }
    Card(
        // Constrain the Card's scroll viewport above the IME and inside system bars.
        modifier = modifier.safeDrawingPadding().imePadding().fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Title & Sharesheet Quick Action ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configuration Code",
                    style = MaterialTheme.typography.titleLarge
                )

                IconButton(
                    onClick = { onShareClicked(editedCode) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share code via Sharesheet"
                    )
                }
            }

            OutlinedTextField(
                value = editedCode,
                onValueChange = { editedCode = it; importError = null },
                label = { Text("Profile configuration") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                isError = importError != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp)
            )
            importError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // --- Main Action Buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { importError = onImportClicked(editedCode) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Import")
                }

                Button(
                    onClick = { onExportClicked(editedCode) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Export")
                }
            }

            // --- Bottom Bar (Share & Dismiss) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onShareClicked(editedCode) }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Share")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = onDismissRequest) {
                    Text("Close")
                }
            }
        }
    }
}

// --- Preview Component ---
@Preview(showBackground = true)
@Composable
private fun ImportExportContentPreview() {
    val sampleCode = """
        DPAD_UDLR 1366.2356 496.11465 121.5 1487.7356 617.6146 243 243 KEY_UP KEY_DOWN KEY_LEFT KEY_RIGHT
        KEY_3 6.02262 2.0246727 35.0
        DPAD 140.42352 396.53687 100.0 240.42352 496.53687 200 200 KEY_W KEY_S KEY_A KEY_D
        MOUSE_AIM 960.0 326.57364 1 176.5 227.63704 300.0 181.42978 1.0 1.0 0
        KEY_5 6.02262 3.506849 35.0
        APPLICATION xtr.keymapper.debug
        KEY_6 10.478579 8.53712 35.0
        KEY_7 5.933529 4.989026 35.0
        KEY_1 6.0582576 0.6199953 35.0
        KEY_4 6.040438 2.7415423 35.0
        ENABLED
        KEY_2 5.880074 1.3853025 35.0
        SCREENSIZE 1920 1080
    """.trimIndent()

    MaterialTheme {
        ImportExportContent(
            code = sampleCode,
            onDismissRequest = {},
            onImportClicked = { null },
            onExportClicked = {},
            onShareClicked = {},
        )
    }
}

// --- Preview Component with Interactive Toggle State ---
@Preview(showBackground = true)
@Composable
private fun ImportExportDialogPreview() {
    var showDialog by remember { mutableStateOf(false) }

    val sampleCode = """
        DPAD_UDLR 1366.2356 496.11465 121.5 1487.7356 617.6146 243 243 KEY_UP KEY_DOWN KEY_LEFT KEY_RIGHT
        KEY_3 6.02262 2.0246727 35.0
        DPAD 140.42352 396.53687 100.0 240.42352 496.53687 200 200 KEY_W KEY_S KEY_A KEY_D
        MOUSE_AIM 960.0 326.57364 1 176.5 227.63704 300.0 181.42978 1.0 1.0 0
        KEY_5 6.02262 3.506849 35.0
        APPLICATION xtr.keymapper.debug
        KEY_6 10.478579 8.53712 35.0
        KEY_7 5.933529 4.989026 35.0
        KEY_1 6.0582576 0.6199953 35.0
        KEY_4 6.040438 2.7415423 35.0
        ENABLED
        KEY_2 5.880074 1.3853025 35.0
        SCREENSIZE 1920 1080
    """.trimIndent()

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { showDialog = true }) {
                Text("Open Dialog")
            }

            if (showDialog) {
                ImportExportDialog(
                    code = sampleCode,
                    onDismissRequest = { showDialog = false },
                    onImportClicked = { showDialog = false; null },
                    onExportClicked = { showDialog = false }
                )
            }
        }
    }
}
