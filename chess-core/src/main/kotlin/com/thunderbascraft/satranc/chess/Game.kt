package com.thunderbascraft.satranc.chess

sealed interface GameResult {
    /** "1-0", "0-1" or "1/2-1/2". */
    val pgn: String

    data class Checkmate(val winner: Side) : GameResult {
        override val pgn get() = if (winner == Side.WHITE) "1-0" else "0-1"
    }
    data class Resignation(val winner: Side) : GameResult {
        override val pgn get() = if (winner == Side.WHITE) "1-0" else "0-1"
    }
    data class Timeout(val winner: Side) : GameResult {
        override val pgn get() = if (winner == Side.WHITE) "1-0" else "0-1"
    }
    sealed interface Draw : GameResult {
        override val pgn get() = "1/2-1/2"
    }
    data object Stalemate : Draw
    data object FiftyMoves : Draw
    data object Repetition : Draw
    data object InsufficientMaterial : Draw
    data object Agreement : Draw
}

data class PlayedMove(val move: Move, val san: String)

/** A game: a start position plus the moves played, with draw/mate detection and undo. */
class Game(startFen: String = Position.START_FEN) {
    val startPosition: Position = Position.fromFen(startFen)

    private val positions = mutableListOf(startPosition)
    private val played = mutableListOf<PlayedMove>()
    private var externalResult: GameResult? = null

    val position: Position get() = positions.last()
    val moves: List<PlayedMove> get() = played.toList()
    val positionsHistory: List<Position> get() = positions.toList()
    val lastMove: Move? get() = played.lastOrNull()?.move

    val result: GameResult?
        get() = externalResult ?: computeResult()

    val isOver: Boolean get() = result != null

    fun legalMoves(): List<Move> = if (isOver) emptyList() else position.legalMoves()

    /** Plays [move] if it is legal. Returns false otherwise. */
    fun play(move: Move): Boolean {
        if (isOver) return false
        val pos = position
        val legal = pos.legalMoves()
        if (move !in legal) return false
        val san = San.of(pos, move, legal)
        positions.add(pos.play(move))
        played.add(PlayedMove(move, san))
        return true
    }

    fun playUci(uci: String): Boolean = runCatching { Move.fromUci(uci) }.getOrNull()?.let(::play) ?: false

    fun playSan(san: String): Boolean = San.parse(position, san)?.let(::play) ?: false

    fun canUndo(): Boolean = played.isNotEmpty() || externalResult != null

    /** Takes back a resignation/draw/timeout if there is one, otherwise the last move. */
    fun undo(): PlayedMove? {
        if (externalResult != null) {
            externalResult = null
            return null
        }
        if (played.isEmpty()) return null
        positions.removeAt(positions.lastIndex)
        return played.removeAt(played.lastIndex)
    }

    fun resign(loser: Side) { externalResult = GameResult.Resignation(loser.opposite) }
    fun timeout(loser: Side) {
        // A player who runs out of time loses only if the opponent could still mate.
        externalResult = if (hasMatingMaterial(position, loser.opposite)) GameResult.Timeout(loser.opposite)
        else GameResult.InsufficientMaterial
    }
    fun agreeDraw() { externalResult = GameResult.Agreement }

    private fun computeResult(): GameResult? {
        val pos = position
        if (pos.legalMoves().isEmpty()) {
            return if (pos.isInCheck()) GameResult.Checkmate(pos.sideToMove.opposite) else GameResult.Stalemate
        }
        if (isInsufficientMaterial(pos)) return GameResult.InsufficientMaterial
        if (pos.halfmoveClock >= 100) return GameResult.FiftyMoves
        val key = pos.repetitionKey
        if (positions.count { it.repetitionKey == key } >= 3) return GameResult.Repetition
        return null
    }

    fun toPgn(
        white: String = "?",
        black: String = "?",
        event: String = "Satranç",
        date: String = "????.??.??",
    ): String = Pgn.write(this, mapOf("Event" to event, "Date" to date, "White" to white, "Black" to black))

    companion object {
        fun isInsufficientMaterial(pos: Position): Boolean {
            val others = pos.pieces().filter { it.second.type != PieceType.KING }
            if (others.isEmpty()) return true
            if (others.any { it.second.type in setOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN) }) return false
            if (others.size == 1) return true // a single minor piece
            // Only bishops, all on the same square colour.
            if (others.all { it.second.type == PieceType.BISHOP }) {
                val colours = others.map { Square.isLight(it.first) }.toSet()
                return colours.size == 1
            }
            return false
        }

        /** Whether [side] has any material that could deliver mate in principle. */
        fun hasMatingMaterial(pos: Position, side: Side): Boolean {
            val own = pos.pieces().filter { it.second.side == side && it.second.type != PieceType.KING }
            if (own.isEmpty()) return false
            if (own.size == 1 && own[0].second.type in setOf(PieceType.BISHOP, PieceType.KNIGHT)) {
                // A lone minor piece can only mate with help from enemy pieces.
                return pos.pieces().any { it.second.side != side && it.second.type != PieceType.KING }
            }
            return true
        }

        /** Parses the first game of a PGN. Throws [PgnException] if it cannot be read. */
        fun fromPgn(pgn: String): Game = Pgn.parseAll(pgn).first().game
    }
}
