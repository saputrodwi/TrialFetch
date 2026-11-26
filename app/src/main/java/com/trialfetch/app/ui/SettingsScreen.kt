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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * Pengaturan dikelompokkan dalam permukaan tonal. Tiap baris punya label,
 * penjelasan, dan kontrol yang jelas; tidak ada warna aksen berulang.
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
            .padding(horizontal = Space.lg, vertical = Space.lg)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.lg))

        SettingsGroup(stringResource(R.string.settings_section_display)) {
            SettingChoice(
                title = stringResource(R.string.settings_theme),
                description = stringResource(R.string.settings_theme_desc),
                options = ThemeMode.entries.map { it.label },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode)
            ) { i -> onChange(settings.copy(themeMode = ThemeMode.entries[i])) }
            SettingChoice(
                title = stringResource(R.string.settings_reader_mode),
                description = stringResource(R.string.settings_reader_mode_desc),
                options = ReaderMode.entries.map { it.label },
                selectedIndex = ReaderMode.entries.indexOf(settings.readerMode)
            ) { i -> onChange(settings.copy(readerMode = ReaderMode.entries[i])) }
        }

        SettingsGroup(stringResource(R.string.settings_section_download)) {
            SettingChoice(
                title = stringResource(R.string.settings_output_mode),
                description = stringResource(R.string.settings_output_mode_desc),
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
            SettingChoice(
                title = stringResource(R.string.settings_naming),
                description = stringResource(R.string.settings_naming_desc),
                options = NamingRule.PRESETS.map { it.second },
                selectedIndex = NamingRule.PRESETS.indexOfFirst { it.first == settings.naming }
                    .takeIf { it >= 0 } ?: 0
            ) { i -> onChange(settings.copy(naming = NamingRule.PRESETS[i].first)) }
            SettingNote(
                stringResource(
                    R.string.settings_naming_example,
                    listOf(1, 12, 121).joinToString("  ") { settings.naming.fileName(it, "jpg") }
                )
            )
            SettingSwitch(
                title = stringResource(R.string.settings_crop_banner),
                description = stringResource(R.string.settings_crop_banner_hint),
                checked = settings.cropBanner
            ) { v -> onChange(settings.copy(cropBanner = v)) }
        }

        SettingsGroup(stringResource(R.string.settings_section_network)) {
            SettingSwitch(
                title = stringResource(R.string.settings_doh),
                description = stringResource(R.string.settings_doh_hint),
                checked = settings.dohEnabled
            ) { v -> onChange(settings.copy(dohEnabled = v)) }
            if (settings.dohEnabled) {
                SettingChoice(
                    title = stringResource(R.string.settings_doh_provider),
                    description = stringResource(R.string.settings_doh_provider_desc),
                    options = DohProvider.entries.map { it.label },
                    selectedIndex = DohProvider.entries.indexOf(settings.dohProvider)
                ) { i -> onChange(settings.copy(dohProvider = DohProvider.entries[i])) }
            }
        }

        SettingsGroup(stringResource(R.string.settings_section_data)) {
            SettingAction(
                title = stringResource(R.string.settings_backup),
                description = stringResource(R.string.settings_backup_desc),
                onClick = onExportBackup
            )
            SettingAction(
                title = stringResource(R.string.settings_restore),
                description = stringResource(R.string.settings_restore_desc),
                onClick = onImportBackup
            )
            SettingAction(
                title = stringResource(R.string.settings_manage_title),
                description = stringResource(
                    if (manageGranted) R.string.settings_manage_on else R.string.settings_manage_off_desc
                ),
                onClick = onRequestManage,
                enabled = !manageGranted
            )
        }

        SettingsGroup(stringResource(R.string.settings_section_diagnostics)) {
            SettingAction(
                title = stringResource(R.string.settings_run),
                description = stringResource(R.string.settings_diagnostics_hint),
                onClick = onRunDiagnostics
            )
            if (diagResult.isNotBlank()) {
                SettingNote(diagResult)
                SettingAction(
                    title = stringResource(R.string.settings_copy),
                    description = stringResource(R.string.settings_copy_desc),
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("diagnostik", diagResult)
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(clip)
                    }
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
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.sm))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Space.md)) { content() }
        }
        Spacer(Modifier.height(Space.lg))
    }
}

@Composable
private fun SettingChoice(
    title: String,
    description: String,
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
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(Space.xs))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Space.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    options.getOrNull(selectedIndex).orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.settings_choose, title),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
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
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.xs))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingAction(
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .clickable(enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (enabled) {
            Spacer(Modifier.width(Space.sm))
            Text(
                stringResource(R.string.settings_open),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Space.sm)
    )
}
