package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.Analysis
import com.thunderbascraft.satranc.chess.Move
import com.thunderbascraft.satranc.chess.MoveAnalysis
import com.thunderbascraft.satranc.chess.MoveQuality
import com.thunderbascraft.satranc.chess.Side
import kotlin.math.abs

fun qualityColor(q: MoveQuality): Color? = when (q) {
    MoveQuality.INACCURACY -> QualityColors.Inaccuracy
    MoveQuality.MISTAKE -> QualityColors.Mistake
    MoveQuality.BLUNDER -> QualityColors.Blunder
    else -> null
}

/** Human-readable evaluation, White's point of view: "+1.3", "-0.4", "#3", "#-2". */
fun formatEval(white: Int): String {
    val mateDistance = Analysis.MATE_SCORE - abs(white)
    return if (abs(white) > Analysis.CP_CLAMP) {
        if (mateDistance == 0) "#" else if (white > 0) "#$mateDistance" else "#-$mateDistance"
    } else {
        val pawns = white / 100.0
        (if (pawns > 0) "+" else "") + String.format(java.util.Locale.ROOT, "%.1f", pawns)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: GameViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.analysis.collectAsStateWithLifecycle()
    val game by vm.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.startAnalysis()
        onDispose { vm.stopAnalysis() }
    }

    val analysis = state.analysis
    val ply = state.selectedPly
    val moves = analysis?.moves.orEmpty()
    val shownMove: MoveAnalysis? = moves.getOrNull(ply - 1)
    val lastMove = state.moves.getOrNull(ply - 1)?.move
    val bestHere = state.evaluations.getOrNull(ply)?.bestMoveUci?.let { runCatching { Move.fromUci(it) }.getOrNull() }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    FitText(stringResource(R.string.analysis_title))
                    Text(
                        stringResource(R.string.analysis_with, state.engine.displayName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (state.running) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.analysing, state.done, state.total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.done / state.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }
        if (state.failed) {
            Text(
                stringResource(R.string.analysis_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        val graph = @Composable {
            EvalGraph(
                evals = analysis?.evals.orEmpty(),
                moves = moves,
                plies = state.positions.size - 1,
                selected = ply,
                onSelect = vm::selectPly,
                modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val board = @Composable { boardModifier: Modifier ->
            ChessBoard(
                position = state.positions[ply.coerceIn(0, state.positions.lastIndex)],
                lastMove = lastMove,
                selected = null,
                targets = emptySet(),
                flipped = game.flipped,
                onTap = {},
                onDragStart = { false },
                onDrop = {},
                arrow = bestHere,
                modifier = boardModifier,
            )
        }

        val nav = @Composable {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.selectPly(0) }, enabled = ply > 0) {
                Icon(Icons.Filled.FirstPage, contentDescription = stringResource(R.string.start_position))
            }
            FilledTonalIconButton(onClick = { vm.selectPly(ply - 1) }, enabled = ply > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_move))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val label = shownMove?.let { moveLabel(it) } ?: stringResource(R.string.start_position)
                FitText(label)
                val evalHere = analysis?.evals?.getOrNull(ply)
                val best = bestHere?.let { m -> state.positions.getOrNull(ply)?.let { sanOf(it, m) } }
                val sub = listOfNotNull(evalHere?.let(::formatEval), best?.let { stringResource(R.string.best_move, it) })
                if (sub.isNotEmpty()) {
                    Text(
                        sub.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalIconButton(onClick = { vm.selectPly(ply + 1) }, enabled = ply < state.moves.size) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_move))
            }
            IconButton(onClick = { vm.selectPly(state.moves.size) }, enabled = ply < state.moves.size) {
                Icon(Icons.AutoMirrored.Filled.LastPage, contentDescription = null)
            }
        }
        }

        val listState = rememberLazyListState()
        val scrollTarget by rememberUpdatedState(ply)
        LaunchedEffect(ply) {
            // Item 0 is the summary; move n is item n.
            if (scrollTarget > 0) listState.animateScrollToItem((scrollTarget - 1).coerceAtLeast(0))
        }
        val list = @Composable { listModifier: Modifier ->
        LazyColumn(
            state = listState,
            modifier = listModifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                if (analysis != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryCard(Side.WHITE, analysis.summary(Side.WHITE), Modifier.weight(1f))
                        SummaryCard(Side.BLACK, analysis.summary(Side.BLACK), Modifier.weight(1f))
                    }
                }
            }
            items(state.moves.size) { i ->
                val m = moves.getOrNull(i)
                MoveRow(
                    ply = i + 1,
                    san = state.moves[i].san,
                    analysis = m,
                    selected = ply == i + 1,
                    onClick = { vm.selectPly(i + 1) },
                )
            }
        }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (maxWidth > maxHeight) {
                // Landscape: board on the left, graph, navigation and moves on the right.
                Row(Modifier.fillMaxSize()) {
                    board(
                        Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .padding(start = 12.dp, bottom = 8.dp),
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        graph()
                        nav()
                        list(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    graph()
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        board(Modifier.widthIn(max = 340.dp).padding(horizontal = 12.dp))
                    }
                    nav()
                    list(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

private fun sanOf(pos: com.thunderbascraft.satranc.chess.Position, move: Move): String? {
    val legal = pos.legalMoves()
    return if (move in legal) com.thunderbascraft.satranc.chess.San.of(pos, move, legal) else null
}

private fun movePrefix(ply: Int): String {
    val no = (ply + 1) / 2
    return if (ply % 2 == 1) "$no." else "$no…"
}

private fun moveLabel(m: MoveAnalysis): String = "${movePrefix(m.ply)} ${m.san}${m.quality.symbol}"

@Composable
private fun MoveRow(ply: Int, san: String, analysis: MoveAnalysis?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color = analysis?.let { qualityColor(it.quality) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(if (selected) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            movePrefix(ply),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(san, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (color != null) {
            Spacer(Modifier.width(4.dp))
            Text(analysis.quality.symbol, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(12.dp))
        if (analysis != null && analysis.quality.isFlagged && analysis.bestMoveSan != null) {
            Text(
                stringResource(R.string.best_was, analysis.bestMoveSan!!),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (analysis != null) {
            Text(
                formatEval(analysis.evalAfter),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(side: Side, s: com.thunderbascraft.satranc.chess.SideSummary, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).background(
                        if (side == Side.WHITE) Color.White else Color(0xFF262421),
                        RoundedCornerShape(6.dp),
                    )
                )
                Spacer(Modifier.width(6.dp))
                FitText(stringResource(if (side == Side.WHITE) R.string.white else R.string.black))
            }
            StatLine(stringResource(R.string.inaccuracies), "?!", s.inaccuracies, QualityColors.Inaccuracy)
            StatLine(stringResource(R.string.mistakes), "?", s.mistakes, QualityColors.Mistake)
            StatLine(stringResource(R.string.blunders), "??", s.blunders, QualityColors.Blunder)
            Row {
                Text(
                    stringResource(R.string.average_loss),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text("${s.averageLoss}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, symbol: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(22.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

private val GraphDark = Color(0xFF3B3A36)
private val GraphLight = Color(0xFFF2F0EA)
private const val GRAPH_RANGE = 800f

/** Evaluation chart: White's advantage up, White's area filled light over a dark background. */
@Composable
private fun EvalGraph(
    evals: List<Int>,
    moves: List<MoveAnalysis>,
    plies: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.secondary
    val select by rememberUpdatedState(onSelect)
    val n = plies.coerceAtLeast(1)
    Canvas(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GraphDark)
            .pointerInput(n) {
                detectTapGestures { o -> select(((o.x / size.width) * n).let { kotlin.math.round(it).toInt() }) }
            }
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        fun x(i: Int) = w * i / n
        fun y(v: Int) = mid - (v.coerceIn(-GRAPH_RANGE.toInt(), GRAPH_RANGE.toInt()) / GRAPH_RANGE) * mid
        if (evals.isNotEmpty()) {
            val area = Path().apply {
                moveTo(0f, h)
                evals.forEachIndexed { i, v -> lineTo(x(i), y(v)) }
                lineTo(x(evals.lastIndex), h)
                close()
            }
            drawPath(area, GraphLight)
        }
        drawLine(Color(0x80888888), Offset(0f, mid), Offset(w, mid), strokeWidth = 1.dp.toPx())
        for (m in moves) {
            val c = qualityColor(m.quality) ?: continue
            if (m.quality == MoveQuality.INACCURACY) continue
            drawCircle(c, radius = 3.dp.toPx(), center = Offset(x(m.ply), y(m.evalAfter)))
        }
        val sx = x(selected)
        drawLine(accent, Offset(sx, 0f), Offset(sx, h), strokeWidth = 2.dp.toPx())
    }
}
