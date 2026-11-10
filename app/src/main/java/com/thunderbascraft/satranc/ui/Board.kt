package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.Move
import com.thunderbascraft.satranc.chess.Piece
import com.thunderbascraft.satranc.chess.PieceType
import com.thunderbascraft.satranc.chess.Position
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.chess.Square

private val LightSquare = Color(0xFFEEEED2)
private val DarkSquare = Color(0xFF769656)
private val LastMoveTint = Color(0x80F6F669)
private val SelectedTint = Color(0x9946A0E0)
private val CheckTint = Color(0xB0E04646)
private val HintColor = Color(0x55000000)

fun pieceDrawable(piece: Piece): Int = when (piece.side) {
    Side.WHITE -> when (piece.type) {
        PieceType.PAWN -> R.drawable.piece_wp
        PieceType.KNIGHT -> R.drawable.piece_wn
        PieceType.BISHOP -> R.drawable.piece_wb
        PieceType.ROOK -> R.drawable.piece_wr
        PieceType.QUEEN -> R.drawable.piece_wq
        PieceType.KING -> R.drawable.piece_wk
    }
    Side.BLACK -> when (piece.type) {
        PieceType.PAWN -> R.drawable.piece_bp
        PieceType.KNIGHT -> R.drawable.piece_bn
        PieceType.BISHOP -> R.drawable.piece_bb
        PieceType.ROOK -> R.drawable.piece_br
        PieceType.QUEEN -> R.drawable.piece_bq
        PieceType.KING -> R.drawable.piece_bk
    }
}

@Composable
private fun rememberPiecePainters(): Map<Piece, Painter> {
    val map = HashMap<Piece, Painter>()
    for (side in Side.entries) for (type in PieceType.entries) {
        val piece = Piece(type, side)
        map[piece] = rememberVectorPainter(ImageVector.vectorResource(pieceDrawable(piece)))
    }
    return map
}

@Composable
fun ChessBoard(
    position: Position,
    lastMove: Move?,
    selected: Int?,
    targets: Set<Int>,
    flipped: Boolean,
    onTap: (Int) -> Unit,
    onDragStart: (Int) -> Boolean,
    onDrop: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** An optional arrow, e.g. the engine's best move in the analysis. */
    arrow: Move? = null,
) {
    val painters = rememberPiecePainters()
    val textMeasurer = rememberTextMeasurer()
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val currentFlipped by rememberUpdatedState(flipped)
    val tap by rememberUpdatedState(onTap)
    val dragStart by rememberUpdatedState(onDragStart)
    val drop by rememberUpdatedState(onDrop)

    fun squareAt(offset: Offset, boardSize: Float): Int? {
        val cell = boardSize / 8f
        val col = (offset.x / cell).toInt()
        val row = (offset.y / cell).toInt()
        if (col !in 0..7 || row !in 0..7 || offset.x < 0 || offset.y < 0) return null
        val file = if (currentFlipped) 7 - col else col
        val rank = if (currentFlipped) row else 7 - row
        return Square.of(file, rank)
    }

    val checkSquare = if (position.isInCheck()) position.kingSquare(position.sideToMove) else -1

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset -> squareAt(offset, size.width.toFloat())?.let(tap) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val sq = squareAt(offset, size.width.toFloat())
                        if (sq != null && dragStart(sq)) {
                            dragFrom = sq
                            dragPos = offset
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragFrom != null) {
                            change.consume()
                            dragPos += amount
                        }
                    },
                    onDragEnd = {
                        if (dragFrom != null) {
                            dragFrom = null
                            drop(squareAt(dragPos, size.width.toFloat()))
                        }
                    },
                    onDragCancel = { dragFrom = null },
                )
            }
    ) {
        val cell = size.width / 8f
        fun topLeft(sq: Int): Offset {
            val f = Square.file(sq)
            val r = Square.rank(sq)
            val col = if (flipped) 7 - f else f
            val row = if (flipped) r else 7 - r
            return Offset(col * cell, row * cell)
        }

        for (sq in 0 until 64) {
            val light = Square.isLight(sq)
            drawRect(if (light) LightSquare else DarkSquare, topLeft(sq), Size(cell, cell))
            if (lastMove != null && (sq == lastMove.from || sq == lastMove.to)) {
                drawRect(LastMoveTint, topLeft(sq), Size(cell, cell))
            }
            if (sq == selected) drawRect(SelectedTint, topLeft(sq), Size(cell, cell))
            if (sq == checkSquare) drawRect(CheckTint, topLeft(sq), Size(cell, cell))
        }

        // Coordinates along the left and bottom edges.
        val coordStyle = TextStyle(fontSize = (cell / 5.5f).toSp())
        for (i in 0..7) {
            val rankSq = Square.of(if (flipped) 7 else 0, i)
            val rankTl = topLeft(rankSq)
            if (rankTl.x == 0f) {
                drawText(
                    textMeasurer, "${'1' + i}", rankTl + Offset(cell * 0.04f, cell * 0.02f),
                    coordStyle.copy(color = if (Square.isLight(rankSq)) DarkSquare else LightSquare),
                )
            }
            val fileSq = Square.of(i, if (flipped) 7 else 0)
            val fileTl = topLeft(fileSq)
            val label = textMeasurer.measure("${'a' + i}", coordStyle)
            drawText(
                textMeasurer, "${'a' + i}",
                fileTl + Offset(cell - label.size.width - cell * 0.04f, cell - label.size.height),
                coordStyle.copy(color = if (Square.isLight(fileSq)) DarkSquare else LightSquare),
            )
        }

        for ((sq, piece) in position.pieces()) {
            if (sq == dragFrom) continue
            drawPiece(painters.getValue(piece), topLeft(sq), cell)
        }

        for (t in targets) {
            val tl = topLeft(t)
            val center = tl + Offset(cell / 2, cell / 2)
            if (position.pieceAt(t) != null) {
                drawCircle(HintColor, radius = cell * 0.46f, center = center, style = Stroke(width = cell * 0.08f))
            } else {
                drawCircle(HintColor, radius = cell * 0.16f, center = center)
            }
        }

        arrow?.let { m ->
            val half = Offset(cell / 2, cell / 2)
            drawArrow(topLeft(m.from) + half, topLeft(m.to) + half, cell)
        }

        dragFrom?.let { from ->
            position.pieceAt(from)?.let { piece ->
                val big = cell * 1.3f
                drawPiece(painters.getValue(piece), dragPos - Offset(big / 2, big * 0.8f), big)
            }
        }
    }
}

private val ArrowColor = Color(0xCC2E7D32)

private fun DrawScope.drawArrow(from: Offset, to: Offset, cell: Float) {
    val d = to - from
    val len = d.getDistance()
    if (len < 1f) return
    val dir = d / len
    val normal = Offset(-dir.y, dir.x)
    val head = cell * 0.42f
    val shaftEnd = to - dir * head
    drawLine(ArrowColor, from, shaftEnd, strokeWidth = cell * 0.17f, cap = StrokeCap.Round)
    val path = Path().apply {
        moveTo(to.x, to.y)
        val l = shaftEnd + normal * (head * 0.6f)
        val r = shaftEnd - normal * (head * 0.6f)
        lineTo(l.x, l.y)
        lineTo(r.x, r.y)
        close()
    }
    drawPath(path, ArrowColor)
}

private fun DrawScope.drawPiece(painter: Painter, topLeft: Offset, cell: Float) {
    translate(topLeft.x, topLeft.y) {
        with(painter) { draw(Size(cell, cell)) }
    }
}
