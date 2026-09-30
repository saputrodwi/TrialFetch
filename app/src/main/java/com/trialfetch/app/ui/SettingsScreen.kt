package com.trialfetch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.NamingRule
import com.trialfetch.app.data.OutputMode
import com.trialfetch.app.data.ThemeMode
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.BrutalChoiceRow
import com.trialfetch.app.ui.theme.BrutalTitle
import com.trialfetch.app.ui.theme.LocalExtraColors

/**
 * Layar pengaturan penuh.
 *
 * Sebelumnya pengaturan berupa dialog yang mengambang. Sekarang jadi
 * halaman tersendiri supaya tidak tertutup content dan lebih legibil,
 * terutama di layar kecil.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: DownloadSettings,
    onChange: (DownloadSettings) -> Unit
) {
    val extra = LocalExtraColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard("Tampilan", extra.purple) {
            BrutalChoiceRow(
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                onSelect = { i ->
                    onChange(settings.copy(themeMode = ThemeMode.entries[i]))
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM ->
                        "Ikuti setelan HP, jadi tema ikut berubah saat mode terang atau gelap dinyalakan."
                    ThemeMode.LIGHT -> "Selalu terang, apa pun setelan HP."
                    ThemeMode.DARK -> "Selalu gelap, apa pun setelan HP."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Simpan sebagai", extra.blue) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.outputMode == mode,
                        onClick = { onChange(settings.copy(outputMode = mode)) },
                        label = {
                            Text(
                                when (mode) {
                                    OutputMode.FOLDER -> "Folder"
                                    OutputMode.ZIP -> "ZIP"
                                }
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (settings.outputMode) {
                    OutputMode.FOLDER ->
                        "Setiap halaman jadi file terpisah di dalam folder chapter."
                    OutputMode.ZIP ->
                        "Semua halaman dibungkus jadi satu arsip .zip per chapter."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Nama file", extra.pink) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NamingRule.PRESETS.forEach { (rule, label) ->
                    FilterChip(
                        selected = settings.naming == rule,
                        onClick = { onChange(settings.copy(naming = rule)) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PreviewRow(settings.naming)
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Pemotongan banner", extra.yellow) {
            RowToggle(
                title = "Potong banner Baozimh",
                subtitle = "Khusus Baozimh. Menghapus pita banner 200px di atas atau bawah gambar (logo + alamat situs).",
                checked = settings.cropBanner,
                accent = extra.yellowDeep
            ) { v -> onChange(settings.copy(cropBanner = v)) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Penyimpanan", extra.green) {
            Text(
                "Semua hasil unduhan disimpan ke folder publik:\n" +
                    "Download/TrialFetch/<judul>/<chapter>/",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Contoh nama file: 0001.jpg, 0002.jpg, … (bisa diubah di atas).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    val extra = LocalExtraColors.current
    BrutalCard(modifier = Modifier.fillMaxWidth(), background = extra.card) {
        BrutalTitle(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accent
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Pratinjau nama file supaya efeknya kelihatan sebelum disimpan. */
@Composable
private fun PreviewRow(naming: NamingRule) {
    val examples = listOf(1, 2, 12, 121)
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Contoh hasil:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            examples.forEach { n ->
                Text(
                    naming.fileName(n, "jpg"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Placeholder: {n} = nomor halaman, {i} = indeks mulai 0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
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
