package com.trialfetch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.NamingRule
import com.trialfetch.app.data.OutputMode

/** Dialog pengaturan unduhan: mode output, pola penamaan, crop banner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    initial: DownloadSettings,
    onDismiss: () -> Unit,
    onConfirm: (DownloadSettings) -> Unit
) {
    var mode by remember { mutableStateOf(initial.outputMode) }
    var naming by remember { mutableStateOf(initial.naming) }
    var crop by remember { mutableStateOf(initial.cropBanner) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan unduhan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Simpan sebagai", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row {
                    OutputMode.entries.forEach { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            RadioButton(
                                selected = mode == m,
                                onClick = { mode = m }
                            )
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Nama file", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NamingRule.PRESETS.forEach { (rule, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = naming == rule,
                                onClick = { naming = rule }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "{n} = nomor halaman, {i} = indeks mulai 0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = crop,
                        onClick = { crop = !crop }
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("Potong banner Baozimh", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Hanya memengaruhi sumber Baozimh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Disimpan ke Download/TrialFetch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(DownloadSettings(mode, naming, crop)) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
