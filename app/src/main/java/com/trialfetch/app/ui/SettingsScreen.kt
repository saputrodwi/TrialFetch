package com.trialfetch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.trialfetch.app.BuildConfig
import com.trialfetch.app.R
import com.trialfetch.app.core.DohProvider
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.NamingRule
import com.trialfetch.app.data.OutputMode
import com.trialfetch.app.data.ReaderMode
import com.trialfetch.app.data.ThemeMode
import com.trialfetch.app.ui.theme.Space

/**
 * Pengaturan disusun sebagai daftar seksi datar: satu judul, baris-baris
 * dengan kontrol di kanan, dan pilihan ganda di bawah label. Tidak ada
 * kartu bertumpuk agar hierarki tetap terbaca di layar sempit.
 */
@Composable
fun SettingsScreen(
    settings: DownloadSettings,
    onChange: (DownloadSettings) -> Unit,
    onReset: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    diagResult: String = "",
    onRunDiagnostics: () -> Unit = {},
    manageGranted: Boolean = true,
    onRequestManage: () -> Unit = {}
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.md)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.xl))

        SectionBlock(stringResource(R.string.settings_section_display)) {
            ChoiceSetting(
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode)
            ) { i -> onChange(settings.copy(themeMode = ThemeMode.entries[i])) }
            ChoiceSetting(
                label = stringResource(R.string.settings_reader_mode),
                options = ReaderMode.entries.map { it.label },
                selectedIndex = ReaderMode.entries.indexOf(settings.readerMode)
            ) { i -> onChange(settings.copy(readerMode = ReaderMode.entries[i])) }
            Caption(stringResource(R.string.settings_reader_hint))
        }

        SectionBlock(stringResource(R.string.settings_section_download)) {
            ChoiceSetting(
                label = stringResource(R.string.settings_output_mode),
                options = listOf(
                    stringResource(R.string.settings_output_folder),
                    stringResource(R.string.settings_output_zip)
                ),
                selectedIndex = if (settings.outputMode == OutputMode.FOLDER) 0 else 1
            ) { i ->
                onChange(
                    settings.copy(
                        outputMode = if (i == 0) OutputMode.FOLDER else OutputMode.ZIP
                    )
                )
            }
            ChoiceSetting(
                label = stringResource(R.string.settings_naming),
                options = NamingRule.PRESETS.map { it.second },
                selectedIndex = NamingRule.PRESETS.indexOfFirst { it.first == settings.naming }
                    .takeIf { it >= 0 } ?: 0
            ) { i -> onChange(settings.copy(naming = NamingRule.PRESETS[i].first)) }
            PreviewRow(settings.naming)
            Caption(stringResource(R.string.settings_download_path))
            RowToggle(
                title = stringResource(R.string.settings_crop_banner),
                subtitle = stringResource(R.string.settings_crop_banner_hint),
                checked = settings.cropBanner
            ) { v -> onChange(settings.copy(cropBanner = v)) }
        }

        SectionBlock(stringResource(R.string.settings_section_network)) {
            RowToggle(
                title = stringResource(R.string.settings_doh),
                subtitle = stringResource(R.string.settings_doh_hint),
                checked = settings.dohEnabled
            ) { v -> onChange(settings.copy(dohEnabled = v)) }
            if (settings.dohEnabled) {
                ChoiceSetting(
                    label = stringResource(R.string.settings_doh_provider),
                    options = DohProvider.entries.map { it.label },
                    selectedIndex = DohProvider.entries.indexOf(settings.dohProvider)
                ) { i -> onChange(settings.copy(dohProvider = DohProvider.entries[i])) }
            }
        }

        SectionBlock(stringResource(R.string.settings_section_data)) {
            ActionRow(stringResource(R.string.settings_backup)) { onExportBackup() }
            ActionRow(stringResource(R.string.settings_restore)) { onImportBackup() }
            Text(
                stringResource(
                    if (manageGranted) R.string.settings_manage_on else R.string.settings_manage_off
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.sm)
            )
            if (!manageGranted) {
                ActionRow(stringResource(R.string.settings_manage_action)) { onRequestManage() }
            }
        }

        SectionBlock(stringResource(R.string.settings_section_diagnostics)) {
            Caption(stringResource(R.string.settings_diagnostics_hint))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRunDiagnostics) {
                    Text(stringResource(R.string.settings_run))
                }
                Spacer(Modifier.width(Space.sm))
                TextButton(onClick = {
                    val clip = android.content.ClipData.newPlainText("diagnostik", diagResult)
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(clip)
                    android.widget.Toast.makeText(
                        ctx, R.string.settings_copied, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) { Text(stringResource(R.string.settings_copy)) }
            }
            if (diagResult.isNotBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    diagResult,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(Space.xl))
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.settings_reset))
        }
        Text(
            stringResource(R.string.settings_build, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun SectionBlock(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(Space.md))
    content()
    Spacer(Modifier.height(Space.md))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(Space.lg))
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Space.sm)
    )
}

@Composable
private fun ChoiceSetting(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(
                    options.getOrNull(selectedIndex).orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.settings_choose, label)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        trailingIcon = {
                            if (index == selectedIndex) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
    ) { Text(text, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun PreviewRow(naming: NamingRule) {
    val examples = listOf(1, 2, 12, 121)
    Text(
        stringResource(
            R.string.settings_naming_example,
            examples.joinToString("  ") { naming.fileName(it, "jpg") }
        ),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(Space.md)
    )
}

@Composable
private fun RowToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
