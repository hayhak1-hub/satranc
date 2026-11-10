package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.filled.Translate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.engine.EngineKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val active by vm.engineKind.collectAsStateWithLifecycle()
    val net by vm.netState.collectAsStateWithLifecycle()
    var askDownload by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { FitText(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.engine), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        stringResource(R.string.engine_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EngineOption(
                        title = EngineKind.SF11.displayName,
                        subtitle = stringResource(R.string.engine_sf11_desc),
                        selected = active == EngineKind.SF11,
                        onClick = { vm.selectEngine(EngineKind.SF11) },
                    )
                    EngineOption(
                        title = EngineKind.SF19.displayName,
                        subtitle = stringResource(
                            if (net == NetState.Ready) R.string.engine_sf19_ready else R.string.engine_sf19_desc
                        ),
                        selected = active == EngineKind.SF19,
                        onClick = {
                            when (net) {
                                NetState.Ready -> vm.selectEngine(EngineKind.SF19)
                                is NetState.Downloading -> {}
                                else -> askDownload = true
                            }
                        },
                    )

                    when (val s = net) {
                        is NetState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { if (s.total == 0L) 0f else s.bytes / s.total.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.downloading, (s.bytes / 1_000_000).toInt(), (s.total / 1_000_000).toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = vm::cancelDownload) { Text(stringResource(R.string.cancel)) }
                            }
                        }
                        is NetState.Failed -> {
                            Text(
                                stringResource(R.string.download_failed, s.reason),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = vm::downloadNet) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.retry))
                            }
                        }
                        NetState.Ready -> {
                            OutlinedButton(onClick = vm::deleteNet) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.delete_download))
                            }
                        }
                        NetState.Missing -> {}
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LanguageCard()
        }
    }

    if (askDownload) {
        AlertDialog(
            onDismissRequest = { askDownload = false },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            title = { Text(stringResource(R.string.download_title)) },
            text = { Text(stringResource(R.string.download_text)) },
            confirmButton = {
                Button(onClick = { askDownload = false; vm.downloadNet() }) { Text(stringResource(R.string.download)) }
            },
            dismissButton = {
                TextButton(onClick = { askDownload = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** App languages: tag ("" = follow the system) and the label resource. */
private val Languages = listOf(
    "" to R.string.language_system,
    "tr" to R.string.language_tr,
    "de" to R.string.language_de,
    "en" to R.string.language_en,
)

/** The app language chosen with the per-app language API, "" when following the system. */
private fun currentLanguage(): String =
    AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()

@Composable
private fun LanguageCard() {
    val current = remember { currentLanguage() }
    ElevatedCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge)
            }
            for ((tag, label) in Languages) {
                val selected = current == tag || (tag.isNotEmpty() && current.startsWith(tag))
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    // Recreates the activity; the screen and game survive (saveable state, ViewModel).
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                }
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun EngineOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
