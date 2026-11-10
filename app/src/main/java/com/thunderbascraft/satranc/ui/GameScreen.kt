package com.thunderbascraft.satranc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    vm: GameViewModel,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onAnalyse: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    var confirmDraw by remember { mutableStateOf(false) }
    /** The number of moves at which the result dialog was dismissed (-1 = not dismissed). */
    var resultDismissedAt by rememberSaveable { mutableStateOf(-1) }

    val you = stringResource(R.string.you)
    val computer = stringResource(R.string.computer)
    val whiteName = stringResource(R.string.white)
    val blackName = stringResource(R.string.black)
    val copied = stringResource(R.string.copied)
    val saver = rememberPgnSaver()

    val topSide = if (state.flipped) Side.WHITE else Side.BLACK
    val levelLabel = levelName(state.level)
    fun label(side: Side): String = when {
        state.mode == Mode.TWO_PLAYERS -> if (side == Side.WHITE) whiteName else blackName
        side == state.humanSide -> you
        else -> "$computer · ${state.level.number}. $levelLabel"
    }

    val title = if (state.mode == Mode.TWO_PLAYERS) stringResource(R.string.two_players)
    else stringResource(R.string.vs_computer)

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopAppBar(
            title = { FitText(title) },
            navigationIcon = {
                IconButton(onClick = onMenu) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.menu))
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(Icons.Filled.Share, stringResource(R.string.share_pgn)) {
                            menuOpen = false
                            sharePgn(context, vm.pgn(label(Side.WHITE), label(Side.BLACK)))
                        }
                        MenuItem(Icons.Filled.Save, stringResource(R.string.save_pgn)) {
                            menuOpen = false
                            saver.save(vm.pgn(label(Side.WHITE), label(Side.BLACK)))
                        }
                        MenuItem(Icons.Filled.ContentCopy, stringResource(R.string.copy_fen)) {
                            menuOpen = false
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("FEN", vm.fen()))
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                        }
                        if (state.result == null) {
                            if (state.mode == Mode.TWO_PLAYERS) {
                                MenuItem(Icons.Filled.Handshake, stringResource(R.string.offer_draw)) {
                                    menuOpen = false; confirmDraw = true
                                }
                            }
                            MenuItem(Icons.Filled.Flag, stringResource(R.string.resign)) {
                                menuOpen = false; confirmResign = true
                            }
                        }
                        MenuItem(Icons.Filled.Insights, stringResource(R.string.analyse), enabled = state.sanMoves.isNotEmpty()) {
                            menuOpen = false; onAnalyse()
                        }
                    }
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        val toMove = state.position.sideToMove
        val topBar = @Composable {
            PlayerBar(
                label(topSide), topSide,
                active = toMove == topSide && state.result == null,
                thinking = state.thinking && toMove == topSide,
            )
        }
        val bottomBar = @Composable {
            PlayerBar(
                label(topSide.opposite), topSide.opposite,
                active = toMove == topSide.opposite && state.result == null,
                thinking = state.thinking && toMove == topSide.opposite,
            )
        }
        val board = @Composable { mod: Modifier ->
            ChessBoard(
                position = state.position,
                lastMove = state.lastMove,
                selected = state.selected,
                targets = state.targets,
                flipped = state.flipped,
                onTap = vm::onSquareTapped,
                onDragStart = vm::onDragStart,
                onDrop = vm::onDrop,
                modifier = mod,
            )
        }
        val details = @Composable { mod: Modifier ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GameDetails(state, onAnalyse, vm)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (maxWidth > maxHeight) {
                // Landscape: board on the left, everything else on the right.
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).padding(bottom = 8.dp)) {
                        board(Modifier)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topBar()
                        bottomBar()
                        details(Modifier.weight(1f))
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxHeight().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topBar()
                    board(Modifier)
                    bottomBar()
                    details(Modifier.weight(1f))
                }
            }
        }
    }

    state.pendingPromotion?.let {
        PromotionDialog(side = state.position.sideToMove, onChoose = vm::onPromotionChosen)
    }

    if (confirmResign) ConfirmDialog(stringResource(R.string.confirm_resign), { confirmResign = false; vm.resign() }) { confirmResign = false }
    if (confirmDraw) ConfirmDialog(stringResource(R.string.confirm_draw), { confirmDraw = false; vm.agreeDraw() }) { confirmDraw = false }

    val result = state.result
    if (result != null && resultDismissedAt != state.sanMoves.size) {
        ResultDialog(
            result = result,
            onAnalyse = { resultDismissedAt = state.sanMoves.size; onAnalyse() },
            onNewGame = { resultDismissedAt = -1; vm.rematch() },
            onDismiss = { resultDismissedAt = state.sanMoves.size },
        )
    }
    // A new game (or an undone result) re-arms the dialog.
    LaunchedEffect(result == null) { if (result == null) resultDismissedAt = -1 }

    if (state.engineError) {
        AlertDialog(
            onDismissRequest = vm::dismissEngineError,
            confirmButton = { TextButton(onClick = vm::dismissEngineError) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.engine_error)) },
        )
    }
}

/** Status line, move list and the action buttons below the board. */
@Composable
private fun ColumnScope.GameDetails(state: GameUiState, onAnalyse: () -> Unit, vm: GameViewModel) {
    val toMove = state.position.sideToMove
            val status = when {
                state.result != null -> resultText(state.result)
                state.thinking -> stringResource(R.string.thinking)
                state.position.isInCheck() -> stringResource(R.string.check)
                toMove == Side.WHITE -> stringResource(R.string.white_to_move)
                else -> stringResource(R.string.black_to_move)
            }
            // Fixed single line, so that nothing moves when the status changes.
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (state.result != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (state.result != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            ) {
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                    FitText(status, textAlign = TextAlign.Center)
                }
            }

            MoveList(state.sanMoves)

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), state.canUndo, vm::undo, Modifier.weight(1f))
                ActionButton(Icons.Filled.SwapVert, stringResource(R.string.flip), true, vm::flip, Modifier.weight(1f))
                ActionButton(Icons.Filled.Insights, stringResource(R.string.analyse), state.result != null, onAnalyse, Modifier.weight(1f))
            }
}

@Composable
internal fun MenuItem(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            FitText(label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun PlayerBar(name: String, side: Side, active: Boolean, thinking: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Everything here has a fixed height, so the board never moves when the turn or
    // the "thinking" state changes.
    Column(Modifier.fillMaxWidth()) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        border = BorderStroke(1.5.dp, if (active) scheme.primary else Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (side == Side.WHITE) Color.White else Color(0xFF262421),
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier.size(16.dp),
            ) {}
            Spacer(Modifier.width(10.dp))
            FitText(name, Modifier.weight(1f))
            if (active) {
                Text(
                    stringResource(R.string.to_move),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 12.dp)) {
        if (thinking) LinearProgressIndicator(Modifier.fillMaxSize())
    }
    }
}

@Composable
private fun MoveList(moves: List<String>) {
    val listState = rememberLazyListState()
    val pairs = moves.chunked(2)
    LaunchedEffect(pairs.size) { if (pairs.isNotEmpty()) listState.animateScrollToItem(pairs.lastIndex) }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().height(40.dp)) {
      Box(contentAlignment = Alignment.CenterStart) {
        if (pairs.isEmpty()) {
            Text(
                stringResource(R.string.no_moves_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                itemsIndexed(pairs) { i, pair ->
                    Row {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(pair.joinToString(" "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun ResultDialog(result: GameResult, onAnalyse: () -> Unit, onNewGame: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Flag, contentDescription = null) },
        title = { Text(stringResource(R.string.game_over), textAlign = TextAlign.Center) },
        text = {
            Text(
                resultText(result),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = onAnalyse) {
                Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.analyse_game))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onNewGame) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_game))
            }
        },
    )
}

@Composable
internal fun PromotionDialog(side: Side, onChoose: (PieceType?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        confirmButton = {},
        title = { Text(stringResource(R.string.choose_promotion)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { t ->
                    IconButton(onClick = { onChoose(t) }, modifier = Modifier.size(56.dp)) {
                        val name = stringResource(
                            when (t) {
                                PieceType.QUEEN -> R.string.piece_queen
                                PieceType.ROOK -> R.string.piece_rook
                                PieceType.BISHOP -> R.string.piece_bishop
                                else -> R.string.piece_knight
                            }
                        )
                        Image(painterResource(pieceDrawable(Piece(t, side))), contentDescription = name, modifier = Modifier.size(48.dp))
                    }
                }
            }
        },
    )
}

@Composable
internal fun ConfirmDialog(text: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.no)) } },
        text = { Text(text) },
    )
}
