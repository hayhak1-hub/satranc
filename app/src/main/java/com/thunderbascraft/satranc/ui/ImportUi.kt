package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.PgnGame

/** Dialog with a multi-line field to paste PGN text, prefilled from the clipboard. */
@Composable
fun PastePgnDialog(onLoad: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(clipboardPgn(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_pgn)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.paste_pgn_hint)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            )
        },
        confirmButton = {
            Button(onClick = { onLoad(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.load)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Shows the import progress, the game chooser for multi-game files and errors. */
@Composable
fun ImportDialogs(state: ImportState, onChoose: (PgnGame) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        ImportState.Busy -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Column(Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 12.dp))
                }
            },
        )
        is ImportState.Choose -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.choose_game)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(state.games) { i, g ->
                        if (i > 0) HorizontalDivider()
                        Column(Modifier.fillMaxWidth().clickable { onChoose(g) }.padding(vertical = 10.dp)) {
                            Text(gameTitle(g), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(g.event, g.date, g.result).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.pgn_error_title)) },
            text = { Text(pgnErrorText(context, state.error)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
        ImportState.Idle, ImportState.Opened -> {}
    }
}

@Composable
fun gameTitle(g: PgnGame): String {
    val unknown = stringResource(R.string.unknown_player)
    return "${g.white ?: unknown} – ${g.black ?: unknown}"
}
