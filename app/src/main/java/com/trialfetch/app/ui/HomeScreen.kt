package com.trialfetch.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.Source
import com.trialfetch.app.data.HistoryEntry
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.BrutalTitle
import com.trialfetch.app.ui.theme.LocalExtraColors

/**
 * Beranda: cari + hasil + jalan pintas. Disengaja ringkas — tanpa
 * paragraf penjelasan. Input URL tinggal di dialog, bukan kartu
 * permanen yang memakan tempat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    sources: List<Source>,
    source: Source,
    onSourceChange: (Source) -> Unit,
    results: List<SearchResult>,
    error: String?,
    urlInput: String,
    urlLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onOpenUrl: () -> Unit,
    history: List<HistoryEntry>,
    onOpenHistory: (HistoryEntry) -> Unit,
    onPick: (SearchResult) -> Unit,
    storageGranted: Boolean,
    onReset: () -> Unit
) {
    var showUrlDialog by remember { mutableStateOf(false) }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Buka dari URL", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "Tempel link series atau chapter dari situs yang didukung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("manwang.net/book/…") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (!urlLoading && urlInput.isNotBlank()) {
                                onOpenUrl()
                                showUrlDialog = false
                            }
                        })
                    )
                    if (urlLoading) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOpenUrl()
                        showUrlDialog = false
                    },
                    enabled = !urlLoading && urlInput.isNotBlank()
                ) { Text("Buka") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Batal") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (!storageGranted) {
            BrutalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                background = LocalExtraColors.current.yellow
            ) {
                Text(
                    "Izin penyimpanan belum diberikan — unduhan tidak bisa " +
                        "menulis ke folder Download.",
                    color = LocalExtraColors.current.onAccent,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari judul komik") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = {
                IconButton(onClick = onSearch, enabled = !searching) {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    }
                }
            }
        )

        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources) { s ->
                FilterChip(
                    selected = source == s,
                    onClick = { onSourceChange(s) },
                    label = { Text(s.displayName) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Buka dari URL")
            }
            Spacer(Modifier.weight(1f))
            // Reset: hapus query + hasil cari + error + URL sekaligus.
            if (query.isNotBlank() || results.isNotEmpty() || error != null || urlInput.isNotBlank()) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        when {
            searching -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            results.isNotEmpty() -> {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.source.id + it.comicId }) { r ->
                        ResultRow(r) { onPick(r) }
                    }
                }
            }
            else -> {
                // Keadaan awal yang bersih: riwayat terakhir, tanpa ceramah.
                val recent = history.take(5)
                if (recent.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "Cari judul di atas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            BrutalTitle(
                                text = "Terakhir dibaca",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        items(recent, key = { it.key() }) { h ->
                            HistoryRow(entry = h, onClick = { onOpenHistory(h) })
                        }
                    }
                }
            }
        }
    }
}

/** Baris riwayat ringkas: sampul + judul + progres halaman. */
@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    BrutalCard(
        modifier = Modifier.fillMaxWidth(),
        background = extra.card,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.coverUrl.ifBlank { null },
                contentDescription = null,
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.chapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.totalPages > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Hal ${entry.page} dari ${entry.totalPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { entry.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    BrutalCard(
        modifier = Modifier.fillMaxWidth(),
        background = extra.card,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = r.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (r.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        r.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
