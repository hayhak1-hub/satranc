package com.thunderbascraft.satranc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Piece
import com.thunderbascraft.satranc.chess.PieceType
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.data.Mode

@Composable
fun resultText(result: GameResult): String = stringResource(
    when (result) {
        is GameResult.Checkmate -> if (result.winner == Side.WHITE) R.string.result_checkmate_white else R.string.result_checkmate_black
        is GameResult.Resignation -> if (result.winner == Side.WHITE) R.string.result_resign_white else R.string.result_resign_black
        is GameResult.Timeout -> if (result.winner == Side.WHITE) R.string.result_timeout_white else R.string.result_timeout_black
        GameResult.Stalemate -> R.string.result_stalemate
        GameResult.FiftyMoves -> R.string.result_fifty
        GameResult.Repetition -> R.string.result_repetition
        GameResult.InsufficientMaterial -> R.string.result_material
        GameResult.Agreement -> R.string.result_agreement
    }
)

@Composable
fun GameScreen(vm: GameViewModel, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    var confirmDraw by remember { mutableStateOf(false) }

    val you = stringResource(R.string.you)
    val computer = stringResource(R.string.computer)
    val whiteName = stringResource(R.string.white)
    val blackName = stringResource(R.string.black)
    val copied = stringResource(R.string.copied)

    val topSide = if (state.flipped) Side.WHITE else Side.BLACK
    fun playerLabel(side: Side): String = when {
        state.mode == Mode.TWO_PLAYERS -> if (side == Side.WHITE) whiteName else blackName
        side == state.humanSide -> you
        else -> "$computer · ${state.level.number}"
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onMenu) { Text("‹ " + stringResource(R.string.menu)) }
            Box(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.flip)) }, onClick = { menuOpen = false; vm.flip() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share_pgn)) }, onClick = {
                        menuOpen = false
                        val pgn = vm.pgn(playerLabel(Side.WHITE), playerLabel(Side.BLACK))
                        val send = Intent(Intent.ACTION_SEND).setType("application/x-chess-pgn").putExtra(Intent.EXTRA_TEXT, pgn)
                        context.startActivity(Intent.createChooser(send, null))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy_fen)) }, onClick = {
                        menuOpen = false
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("FEN", vm.fen()))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    })
                    if (state.result == null) {
                        if (state.mode == Mode.TWO_PLAYERS) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.offer_draw)) }, onClick = { menuOpen = false; confirmDraw = true })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.resign)) }, onClick = { menuOpen = false; confirmResign = true })
                    }
                }
            }
        }

        Column(
            Modifier.widthIn(max = 560.dp).weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerBar(playerLabel(topSide), active = state.position.sideToMove == topSide && state.result == null)

            ChessBoard(
                position = state.position,
                lastMove = state.lastMove,
                selected = state.selected,
                targets = state.targets,
                flipped = state.flipped,
                onTap = vm::onSquareTapped,
                onDragStart = vm::onDragStart,
                onDrop = vm::onDrop,
            )

            PlayerBar(playerLabel(topSide.opposite), active = state.position.sideToMove == topSide.opposite && state.result == null)

            val status = when {
                state.result != null -> resultText(state.result!!)
                state.thinking -> stringResource(R.string.thinking)
                state.position.isInCheck() -> stringResource(R.string.check)
                state.position.sideToMove == Side.WHITE -> stringResource(R.string.white_to_move)
                else -> stringResource(R.string.black_to_move)
            }
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (state.result != null) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (state.thinking) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))

            MoveList(state.sanMoves)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::undo, enabled = state.canUndo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.undo))
                }
                OutlinedButton(onClick = vm::flip, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.flip))
                }
            }
        }
    }

    state.pendingPromotion?.let {
        PromotionDialog(side = state.position.sideToMove, onChoose = vm::onPromotionChosen)
    }

    if (confirmResign) ConfirmDialog(stringResource(R.string.confirm_resign), { confirmResign = false; vm.resign() }) { confirmResign = false }
    if (confirmDraw) ConfirmDialog(stringResource(R.string.confirm_draw), { confirmDraw = false; vm.agreeDraw() }) { confirmDraw = false }

    if (state.engineError) {
        AlertDialog(
            onDismissRequest = vm::dismissEngineError,
            confirmButton = { TextButton(onClick = vm::dismissEngineError) { Text("OK") } },
            text = { Text(stringResource(R.string.engine_error)) },
        )
    }
}

@Composable
private fun PlayerBar(name: String, active: Boolean) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MoveList(moves: List<String>) {
    val listState = rememberLazyListState()
    val pairs = moves.chunked(2)
    LaunchedEffect(pairs.size) { if (pairs.isNotEmpty()) listState.animateScrollToItem(pairs.lastIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(28.dp)) {
        itemsIndexed(pairs) { i, pair ->
            Text("${i + 1}. ${pair.joinToString(" ")}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PromotionDialog(side: Side, onChoose: (PieceType?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        confirmButton = {},
        title = { Text(stringResource(R.string.choose_promotion)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { t ->
                    IconButton(onClick = { onChoose(t) }, modifier = Modifier.size(56.dp)) {
                        Image(painterResource(pieceDrawable(Piece(t, side))), contentDescription = t.name, modifier = Modifier.size(48.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun ConfirmDialog(text: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.no)) } },
        text = { Text(text) },
    )
}
