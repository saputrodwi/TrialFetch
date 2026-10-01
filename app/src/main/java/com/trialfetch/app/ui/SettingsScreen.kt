package com.trialfetch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.trialfetch.app.core.DohProvider
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.NamingRule
import com.trialfetch.app.data.OutputMode
import com.trialfetch.app.data.ThemeMode
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.BrutalChoiceRow
import com.trialfetch.app.ui.theme.BrutalTitle
import com.trialfetch.app.ui.theme.LocalExtraColors

/**
 * Layar pengaturan: tiap seksi satu kartu, satu jenis kontrol per jenis
 * pilihan (pil brutal untuk opsi sedikit, chip untuk opsi banyak),
 * tanpa paragraf penjelasan.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: DownloadSettings,
    onChange: (DownloadSettings) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard("Tampilan") {
            BrutalChoiceRow(
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                onSelect = { i ->
                    onChange(settings.copy(themeMode = ThemeMode.entries[i]))
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Unduhan") {
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
            Spacer(Modifier.height(8.dp))
            Text(
                "Tersimpan di Download/TrialFetch/.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("Banner Baozimh") {
            RowToggle(
                title = "Potong banner otomatis",
                subtitle = "Hapus pita iklan 200px di gambar Baozimh.",
                checked = settings.cropBanner
            ) { v -> onChange(settings.copy(cropBanner = v)) }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard("DNS over HTTPS") {
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val extra = LocalExtraColors.current
    BrutalCard(modifier = Modifier.fillMaxWidth(), background = extra.card) {
        BrutalTitle(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
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
        border = androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.outline
        ),
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
        }
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
