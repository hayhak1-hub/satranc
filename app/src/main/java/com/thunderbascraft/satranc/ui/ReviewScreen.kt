package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.data.Mode

/** Shows an imported game: header, board with move navigation, analysis and "continue from here". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    vm: GameViewModel,
    onBack: () -> Unit,
    onAnalyse: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val review by vm.review.collectAsStateWithLifecycle()
    val r = review ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val context = LocalContext.current
    val saver = rememberPgnSaver()
    var menuOpen by remember { mutableStateOf(false) }
    var askContinue by remember { mutableStateOf(false) }

    val game = r.pgn.game
    val ply = r.selectedPly
    val positions = game.positionsHistory
    val moves = game.moves

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { FitText(stringResource(R.string.review_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_pgn)) },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = { menuOpen = false; vm.reviewPgn()?.let { sharePgn(context, it) } },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_pgn)) },
                            leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                            onClick = { menuOpen = false; vm.reviewPgn()?.let(saver::save) },
                        )
                    }
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        val header = @Composable {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitText(gameTitle(r.pgn), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(r.pgn.result, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val sub = listOfNotNull(r.pgn.event, r.pgn.date).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        val board = @Composable { mod: Modifier ->
            ChessBoard(
                position = positions[ply],
                lastMove = moves.getOrNull(ply - 1)?.move,
                selected = null,
                targets = emptySet(),
                flipped = false,
                onTap = {},
                onDragStart = { false },
                onDrop = {},
                modifier = mod,
            )
        }
        val nav = @Composable {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.reviewSelect(0) }, enabled = ply > 0) {
                    Icon(Icons.Filled.FirstPage, contentDescription = stringResource(R.string.start_position))
                }
                FilledTonalIconButton(onClick = { vm.reviewSelect(ply - 1) }, enabled = ply > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_move))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    FitText(
                        moves.getOrNull(ply - 1)?.let { "${movePrefixAt(game.startPosition, ply)} ${it.san}" }
                            ?: stringResource(R.string.start_position)
                    )
                }
                FilledTonalIconButton(onClick = { vm.reviewSelect(ply + 1) }, enabled = ply < moves.size) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_move))
                }
                IconButton(onClick = { vm.reviewSelect(moves.size) }, enabled = ply < moves.size) {
                    Icon(Icons.AutoMirrored.Filled.LastPage, contentDescription = null)
                }
            }
        }
        val unfinished = r.record?.let { !it.isFinished && it.mode != com.thunderbascraft.satranc.data.history.RecordMode.IMPORTED } == true
        val actions = @Composable {
            if (unfinished) {
                Button(
                    onClick = { if (vm.continueRecord(r.record!!)) onContinue() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    FitText(stringResource(R.string.continue_game))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAnalyse, enabled = moves.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    FitText(stringResource(R.string.analyse))
                }
                Button(onClick = { askContinue = true }, enabled = positions[ply].legalMoves().isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    FitText(stringResource(R.string.continue_from_here))
                }
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(ply) {
        val offset = if (game.startPosition.sideToMove == Side.BLACK) 1 else 0
        if (ply > 0) listState.animateScrollToItem(((ply - 1 + offset) / 2 - 1).coerceAtLeast(0))
    }
        val list = @Composable { mod: Modifier ->
            val firstBlack = game.startPosition.sideToMove == Side.BLACK
            // Rows of (white, black) plies; ply numbers are 1-based.
            val rows = buildList {
                var p = 1
                if (firstBlack && moves.isNotEmpty()) { add(null to 1); p = 2 }
                while (p <= moves.size) { add(p to (p + 1).takeIf { it <= moves.size }); p += 2 }
            }
            LazyColumn(mod, state = listState) {
                items(rows.size) { i ->
                    val (w, b) = rows[i]
                    val anyPly = w ?: b!!
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${game.startPosition.fullmoveNumber + (anyPly - 1 + if (firstBlack) 1 else 0) / 2}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(44.dp),
                        )
                        MoveCell(w?.let { moves[it - 1].san } ?: "…", w != null && ply == w, Modifier.weight(1f)) { w?.let(vm::reviewSelect) }
                        MoveCell(b?.let { moves[it - 1].san } ?: "", b != null && ply == b, Modifier.weight(1f)) { b?.let(vm::reviewSelect) }
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).padding(start = 12.dp, bottom = 8.dp)) {
                        board(Modifier)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        header(); nav(); actions()
                        list(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    header()
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        board(Modifier.widthIn(max = 360.dp).padding(horizontal = 12.dp))
                    }
                    nav(); actions()
                    list(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    if (askContinue) {
        val toMove = positions[ply].sideToMove
        AlertDialog(
            onDismissRequest = { askContinue = false },
            title = { Text(stringResource(R.string.continue_how)) },
            text = {
                Column {
                    ContinueOption(stringResource(R.string.two_players)) {
                        askContinue = false; vm.continueFromReview(Mode.TWO_PLAYERS, toMove); onContinue()
                    }
                    ContinueOption(stringResource(R.string.vs_computer_as_white)) {
                        askContinue = false; vm.continueFromReview(Mode.VS_COMPUTER, Side.WHITE); onContinue()
                    }
                    ContinueOption(stringResource(R.string.vs_computer_as_black)) {
                        askContinue = false; vm.continueFromReview(Mode.VS_COMPUTER, Side.BLACK); onContinue()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askContinue = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ContinueOption(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun MoveCell(san: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        san,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(2.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** "12." for a white move, "12…" for a black move at 1-based [ply] from [start]. */
fun movePrefixAt(start: com.thunderbascraft.satranc.chess.Position, ply: Int): String {
    val offset = if (start.sideToMove == Side.BLACK) 1 else 0
    val idx = ply - 1 + offset
    val no = start.fullmoveNumber + idx / 2
    return if (idx % 2 == 0) "$no." else "$no…"
}
