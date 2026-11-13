package org.liaocl.careeyes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.liaocl.careeyes.R
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.MAX_BREAK_SECONDS
import org.liaocl.careeyes.data.MAX_WORK_MINUTES
import org.liaocl.careeyes.data.MIN_BREAK_SECONDS
import org.liaocl.careeyes.data.MIN_WORK_MINUTES
import org.liaocl.careeyes.data.Preset
import org.liaocl.careeyes.data.PresetValidationError
import org.liaocl.careeyes.data.validationError
import org.liaocl.careeyes.ui.components.ScreenScaffold
import org.liaocl.careeyes.util.durationText
import java.util.UUID

@Composable
fun PresetsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onSavePreset: (Preset) -> Unit,
    onDeletePreset: (Preset) -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<Preset?>(null) }
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }
    var showLastOneError by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.presets_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                editingPreset = null
                editorOpen = true
            }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.presets_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(settings.presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset.id == settings.selectedPresetId,
                    onClick = { onSelectPreset(preset.id) },
                    onEdit = {
                        editingPreset = preset
                        editorOpen = true
                    },
                    onDelete = {
                        if (settings.presets.size <= 1) {
                            showLastOneError = true
                        } else {
                            pendingDelete = preset
                        }
                    },
                )
            }
        }
    }

    if (editorOpen) {
        PresetEditorDialog(
            initial = editingPreset,
            onDismiss = { editorOpen = false },
            onSave = { preset ->
                onSavePreset(preset)
                editorOpen = false
            },
        )
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.presets_delete_title)) },
            text = { Text(stringResource(R.string.presets_delete_message, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(preset)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showLastOneError) {
        AlertDialog(
            onDismissRequest = { showLastOneError = false },
            confirmButton = {
                TextButton(onClick = { showLastOneError = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            text = { Text(stringResource(R.string.presets_last_one)) },
        )
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.presets_selected),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(
                        R.string.presets_summary,
                        durationText(androidx.compose.ui.platform.LocalContext.current, preset.workMinutes * 60L),
                        durationText(androidx.compose.ui.platform.LocalContext.current, preset.breakSeconds.toLong()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun PresetEditorDialog(
    initial: Preset?,
    onDismiss: () -> Unit,
    onSave: (Preset) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var work by remember { mutableStateOf((initial?.workMinutes ?: 20).toString()) }
    var rest by remember { mutableStateOf((initial?.breakSeconds ?: 20).toString()) }
    var error by remember { mutableStateOf<PresetValidationError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.presets_add else R.string.presets_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.presets_name)) },
                    placeholder = { Text(stringResource(R.string.presets_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = work,
                    onValueChange = {
                        work = it.filter(Char::isDigit).take(3)
                        error = null
                    },
                    label = { Text(stringResource(R.string.presets_work)) },
                    supportingText = {
                        Text("$MIN_WORK_MINUTES–$MAX_WORK_MINUTES")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rest,
                    onValueChange = {
                        rest = it.filter(Char::isDigit).take(4)
                        error = null
                    },
                    label = { Text(stringResource(R.string.presets_break)) },
                    supportingText = {
                        Text("$MIN_BREAK_SECONDS–$MAX_BREAK_SECONDS")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = presetErrorText(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                val candidate = Preset(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    workMinutes = work.toIntOrNull() ?: 0,
                    breakSeconds = rest.toIntOrNull() ?: 0,
                )
                val validation = candidate.validationError()
                if (validation == null) {
                    onSave(candidate)
                } else {
                    error = validation
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun presetErrorText(error: PresetValidationError): String = when (error) {
    PresetValidationError.NAME_BLANK -> stringResource(R.string.presets_error_name_blank)
    PresetValidationError.NAME_TOO_LONG -> stringResource(R.string.presets_error_name_long)
    PresetValidationError.WORK_OUT_OF_RANGE -> stringResource(R.string.presets_error_work)
    PresetValidationError.BREAK_OUT_OF_RANGE -> stringResource(R.string.presets_error_break)
}
