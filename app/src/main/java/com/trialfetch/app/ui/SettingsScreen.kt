package com.trialfetch.app.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Pengaturan: satu sistem baris untuk semua opsi. Tiap baris memakai
 * ikon fungsi, label, nilai aktif, dan panah — tidak ada kartu bertumpuk
 * maupun kontrol yang tampil beda-beda antar seksi.
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
        Spacer(Modifier.height(Space.lg))

        SettingsGroup(stringResource(R.string.settings_section_display), Icons.Default.Palette) {
            ChoiceRow(
                icon = Icons.Default.Palette,
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode)
            ) { i -> onChange(settings.copy(themeMode = ThemeMode.entries[i])) }
            ChoiceRow(
                icon = Icons.Default.AutoStories,
                label = stringResource(R.string.settings_reader_mode),
                options = ReaderMode.entries.map { it.label },
                selectedIndex = ReaderMode.entries.indexOf(settings.readerMode)
            ) { i -> onChange(settings.copy(readerMode = ReaderMode.entries[i])) }
        }

        SettingsGroup(stringResource(R.string.settings_section_download), Icons.Default.Download) {
            ChoiceRow(
                icon = Icons.Default.Folder,
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
            ChoiceRow(
                icon = Icons.Default.Download,
                label = stringResource(R.string.settings_naming),
                options = NamingRule.PRESETS.map { it.second },
                selectedIndex = NamingRule.PRESETS.indexOfFirst { it.first == settings.naming }
                    .takeIf { it >= 0 } ?: 0
            ) { i -> onChange(settings.copy(naming = NamingRule.PRESETS[i].first)) }
            Text(
                stringResource(
                    R.string.settings_naming_example,
                    listOf(1, 12, 121).joinToString("  ") { settings.naming.fileName(it, "jpg") }
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.touch + Space.md, bottom = Space.md)
            )
            SwitchRow(
                icon = Icons.Default.Crop,
                label = stringResource(R.string.settings_crop_banner),
                subtitle = stringResource(R.string.settings_crop_banner_hint),
                checked = settings.cropBanner
            ) { v -> onChange(settings.copy(cropBanner = v)) }
        }

        SettingsGroup(stringResource(R.string.settings_section_network), Icons.Default.Dns) {
            SwitchRow(
                icon = Icons.Default.Dns,
                label = stringResource(R.string.settings_doh),
                subtitle = stringResource(R.string.settings_doh_hint),
                checked = settings.dohEnabled
            ) { v -> onChange(settings.copy(dohEnabled = v)) }
            if (settings.dohEnabled) {
                ChoiceRow(
                    icon = Icons.Default.Dns,
                    label = stringResource(R.string.settings_doh_provider),
                    options = DohProvider.entries.map { it.label },
                    selectedIndex = DohProvider.entries.indexOf(settings.dohProvider)
                ) { i -> onChange(settings.copy(dohProvider = DohProvider.entries[i])) }
            }
        }

        SettingsGroup(stringResource(R.string.settings_section_data), Icons.Default.Folder) {
            ActionRow(
                icon = Icons.Default.Folder,
                label = stringResource(R.string.settings_backup),
                onClick = onExportBackup
            )
            ActionRow(
                icon = Icons.Default.Folder,
                label = stringResource(R.string.settings_restore),
                onClick = onImportBackup
            )
            ActionRow(
                icon = Icons.Default.Folder,
                label = stringResource(
                    if (manageGranted) R.string.settings_manage_on else R.string.settings_manage_action
                ),
                onClick = onRequestManage,
                enabled = !manageGranted
            )
        }

        SettingsGroup(stringResource(R.string.settings_section_diagnostics), Icons.Default.BugReport) {
            ActionRow(
                icon = Icons.Default.BugReport,
                label = stringResource(R.string.settings_run),
                onClick = onRunDiagnostics
            )
            ActionRow(
                icon = Icons.Default.BugReport,
                label = stringResource(R.string.settings_copy),
                onClick = {
                    val clip = android.content.ClipData.newPlainText("diagnostik", diagResult)
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(clip)
                }
            )
            if (diagResult.isNotBlank()) {
                Text(
                    diagResult,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.touch + Space.md, bottom = Space.md)
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        TextButton(onClick = onReset) { Text(stringResource(R.string.settings_reset)) }
        Text(
            stringResource(R.string.settings_build, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
    }
    Spacer(Modifier.height(Space.sm))
    content()
    Spacer(Modifier.height(Space.md))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(Space.lg))
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch)
                .clickable { expanded = true }
                .padding(vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.md))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                options.getOrNull(selectedIndex).orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    trailingIcon = {
                        if (index == selectedIndex) {
                            Icon(Icons.Default.Check, contentDescription = null)
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (enabled) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
