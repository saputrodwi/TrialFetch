package com.trialfetch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trialfetch.app.core.DohProvider
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.NamingRule
import com.trialfetch.app.data.OutputMode
import com.trialfetch.app.data.ReaderMode
import com.trialfetch.app.data.ThemeMode
import com.trialfetch.app.ui.theme.BrutalButton
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.BrutalChoiceRow
import com.trialfetch.app.ui.theme.BrutalTitle
import com.trialfetch.app.ui.theme.LocalExtraColors

/**
 * Layar pengaturan: tiap seksi satu kartu, satu jenis kontrol per jenis
 * pilihan (pil brutal untuk opsi sedikit, chip untuk opsi banyak),
 * tanpa paragraf penjelasan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: DownloadSettings,
    onChange: (DownloadSettings) -> Unit,
    onReset: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard("Tampilan", Icons.Default.Palette) {
            BrutalChoiceRow(
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                onSelect = { i ->
                    onChange(settings.copy(themeMode = ThemeMode.entries[i]))
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Mode baca", Icons.Default.AutoStories) {
            BrutalChoiceRow(
                options = ReaderMode.entries.map { it.label },
                selectedIndex = ReaderMode.entries.indexOf(settings.readerMode),
                onSelect = { i ->
                    onChange(settings.copy(readerMode = ReaderMode.entries[i]))
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Berlaku untuk semua chapter; tetap bisa diganti sementara dari bilah bawah reader.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Unduhan", Icons.Default.Download) {
            BrutalChoiceRow(
                options = listOf("Folder", "ZIP"),
                selectedIndex = if (settings.outputMode == OutputMode.FOLDER) 0 else 1,
                onSelect = { i ->
                    onChange(
                        settings.copy(
                            outputMode = if (i == 0) OutputMode.FOLDER else OutputMode.ZIP
                        )
                    )
                }
            )
            Spacer(Modifier.height(12.dp))
            BrutalChoiceRow(
                options = NamingRule.PRESETS.map { it.second },
                selectedIndex = NamingRule.PRESETS.indexOfFirst { it.first == settings.naming }
                    .takeIf { it >= 0 } ?: 0,
                onSelect = { i ->
                    onChange(settings.copy(naming = NamingRule.PRESETS[i].first))
                },
                accent = LocalExtraColors.current.green
            )
            Spacer(Modifier.height(10.dp))
            PreviewRow(settings.naming)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tersimpan di Download/TrialFetch/.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Banner Baozimh", Icons.Default.Crop) {
            RowToggle(
                title = "Potong banner otomatis",
                subtitle = "Hapus pita iklan 200px di gambar Baozimh.",
                checked = settings.cropBanner
            ) { v -> onChange(settings.copy(cropBanner = v)) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("DNS over HTTPS", Icons.Default.Dns) {
            RowToggle(
                title = "Pakai DoH",
                subtitle = "Aktifkan bila situs tidak bisa dibuka padahal internet lancar.",
                checked = settings.dohEnabled
            ) { v -> onChange(settings.copy(dohEnabled = v)) }
            if (settings.dohEnabled) {
                Spacer(Modifier.height(10.dp))
                BrutalChoiceRow(
                    options = DohProvider.entries.map { it.label },
                    selectedIndex = DohProvider.entries.indexOf(settings.dohProvider),
                    onSelect = { i ->
                        onChange(settings.copy(dohProvider = DohProvider.entries[i]))
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Data", Icons.Default.Folder) {
            BrutalButton(
                text = "Cadangkan bookmark & riwayat",
                onClick = onExportBackup,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            BrutalButton(
                text = "Pulihkan dari cadangan",
                onClick = onImportBackup,
                modifier = Modifier.fillMaxWidth(),
                background = LocalExtraColors.current.blue
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onReset) {
            Text("Kembalikan bawaan")
        }

        Spacer(Modifier.height(16.dp))
        // Info build: supaya jelas APK mana yang terpasang saat lapor bug
        // (versionName statis "1.0" tidak bisa dipakai pembeda).
        Text(
            "TrialFetch 1.0 (build ${com.trialfetch.app.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    val extra = LocalExtraColors.current
    BrutalCard(modifier = Modifier.fillMaxWidth(), background = extra.card) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            BrutalTitle(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Pratinjau nama file supaya efeknya kelihatan sebelum disimpan. */
@Composable
private fun PreviewRow(naming: NamingRule) {
    val examples = listOf(1, 2, 12, 121)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                2.dp,
                MaterialTheme.colorScheme.onBackground,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .background(
                MaterialTheme.colorScheme.surface,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "Contoh: " + examples.joinToString("  ") { naming.fileName(it, "jpg") },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RowToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
