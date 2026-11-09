package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thunderbascraft.satranc.BuildConfig
import com.thunderbascraft.satranc.R

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineSmall)
        Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_text))
        TextButton(onClick = { uri.openUri(BuildConfig.SOURCE_URL) }) {
            Text("${stringResource(R.string.source_code)}: ${BuildConfig.SOURCE_URL}")
        }
        Text(stringResource(R.string.credits), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.credits_stockfish))
        TextButton(onClick = { uri.openUri("https://stockfishchess.org") }) { Text("stockfishchess.org") }
        Text(stringResource(R.string.credits_pieces))
        Text(stringResource(R.string.license), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.license_text), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { uri.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }) { Text("GNU GPL v3") }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
