package com.thunderbascraft.satranc.chess

/**
 * An immutable chess position. Pieces are stored as ints: positive for white,
 * negative for black, absolute value = [PieceType.code].
 */
class Position internal constructor(
    private val board: IntArray,
    val sideToMove: Side,
    /** Bit flags: 1 = white O-O, 2 = white O-O-O, 4 = black O-O, 8 = black O-O-O. */
    val castling: Int,
    /** En passant target square, or -1. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(sq: Int): Piece? = Piece.fromCode(board[sq])

    internal fun codeAt(sq: Int): Int = board[sq]

    // ---------------------------------------------------------------- attacks

    fun kingSquare(side: Side): Int {
        val k = PieceType.KING.code * side.sign
        for (i in 0 until 64) if (board[i] == k) return i
        return -1
    }

    fun isInCheck(side: Side = sideToMove): Boolean {
        val k = kingSquare(side)
        return k >= 0 && isAttacked(k, side.opposite)
    }

    /** True if [sq] is attacked by any piece of [by]. */
    fun isAttacked(sq: Int, by: Side): Boolean {
        val s = by.sign
        val f = Square.file(sq)
        val r = Square.rank(sq)
        // Pawns: a white pawn attacks upwards, so it sits one rank below.
        val pr = r - s
        if (pr in 0..7) {
            if (f > 0 && board[Square.of(f - 1, pr)] == PAWN * s) return true
            if (f < 7 && board[Square.of(f + 1, pr)] == PAWN * s) return true
        }
        for ((df, dr) in KNIGHT_STEPS) {
            val nf = f + df; val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[Square.of(nf, nr)] == KNIGHT * s) return true
        }
        for ((df, dr) in KING_STEPS) {
            val nf = f + df; val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[Square.of(nf, nr)] == KING * s) return true
        }
        for ((df, dr) in ROOK_DIRS) {
            if (slideHits(f, r, df, dr, s, ROOK)) return true
        }
        for ((df, dr) in BISHOP_DIRS) {
            if (slideHits(f, r, df, dr, s, BISHOP)) return true
        }
        return false
    }

    private fun slideHits(f: Int, r: Int, df: Int, dr: Int, s: Int, slider: Int): Boolean {
        var nf = f + df; var nr = r + dr
        while (nf in 0..7 && nr in 0..7) {
            val p = board[Square.of(nf, nr)]
            if (p != 0) return p == slider * s || p == QUEEN * s
            nf += df; nr += dr
        }
        return false
    }

    // -------------------------------------------------------- move generation

    fun legalMoves(): List<Move> {
        val result = ArrayList<Move>(48)
        for (m in pseudoLegalMoves()) {
            if (!play(m).isInCheck(sideToMove)) result.add(m)
        }
        return result
    }

    fun legalMovesFrom(sq: Int): List<Move> = legalMoves().filter { it.from == sq }

    fun isLegal(move: Move): Boolean = move in legalMoves()

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(64)
        val s = sideToMove.sign
        for (from in 0 until 64) {
            val p = board[from]
            if (p == 0 || (p > 0) != (s > 0)) continue
            val f = Square.file(from)
            val r = Square.rank(from)
            when (kotlin.math.abs(p)) {
                PAWN -> pawnMoves(from, f, r, s, moves)
                KNIGHT -> stepMoves(from, f, r, s, KNIGHT_STEPS, moves)
                BISHOP -> slideMoves(from, f, r, s, BISHOP_DIRS, moves)
                ROOK -> slideMoves(from, f, r, s, ROOK_DIRS, moves)
                QUEEN -> { slideMoves(from, f, r, s, ROOK_DIRS, moves); slideMoves(from, f, r, s, BISHOP_DIRS, moves) }
                KING -> { stepMoves(from, f, r, s, KING_STEPS, moves); castleMoves(from, moves) }
            }
        }
        return moves
    }

    private fun addPawnMove(from: Int, to: Int, moves: MutableList<Move>) {
        val tr = Square.rank(to)
        if (tr == 0 || tr == 7) {
            for (t in PROMOTIONS) moves.add(Move(from, to, t))
        } else {
            moves.add(Move(from, to))
        }
    }

    private fun pawnMoves(from: Int, f: Int, r: Int, s: Int, moves: MutableList<Move>) {
        val nr = r + s
        if (nr !in 0..7) return
        val one = Square.of(f, nr)
        if (board[one] == 0) {
            addPawnMove(from, one, moves)
            val startRank = if (s > 0) 1 else 6
            if (r == startRank) {
                val two = Square.of(f, r + 2 * s)
                if (board[two] == 0) moves.add(Move(from, two))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val to = Square.of(nf, nr)
            val t = board[to]
            if ((t != 0 && (t > 0) != (s > 0)) || to == epSquare) addPawnMove(from, to, moves)
        }
    }

    private fun stepMoves(from: Int, f: Int, r: Int, s: Int, steps: Array<IntArray>, moves: MutableList<Move>) {
        for ((df, dr) in steps) {
            val nf = f + df; val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val to = Square.of(nf, nr)
            val t = board[to]
            if (t == 0 || (t > 0) != (s > 0)) moves.add(Move(from, to))
        }
    }

    private fun slideMoves(from: Int, f: Int, r: Int, s: Int, dirs: Array<IntArray>, moves: MutableList<Move>) {
        for ((df, dr) in dirs) {
            var nf = f + df; var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val to = Square.of(nf, nr)
                val t = board[to]
                if (t == 0) {
                    moves.add(Move(from, to))
                } else {
                    if ((t > 0) != (s > 0)) moves.add(Move(from, to))
                    break
                }
                nf += df; nr += dr
            }
        }
    }

    private fun castleMoves(from: Int, moves: MutableList<Move>) {
        val white = sideToMove == Side.WHITE
        val home = if (white) 4 else 60
        if (from != home) return
        val opp = sideToMove.opposite
        val king = KING * sideToMove.sign
        val rook = ROOK * sideToMove.sign
        if (board[home] != king) return
        val kFlag = if (white) 1 else 4
        val qFlag = if (white) 2 else 8
        if (castling and kFlag != 0 && board[home + 3] == rook &&
            board[home + 1] == 0 && board[home + 2] == 0 &&
            !isAttacked(home, opp) && !isAttacked(home + 1, opp) && !isAttacked(home + 2, opp)
        ) moves.add(Move(home, home + 2))
        if (castling and qFlag != 0 && board[home - 4] == rook &&
            board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0 &&
            !isAttacked(home, opp) && !isAttacked(home - 1, opp) && !isAttacked(home - 2, opp)
        ) moves.add(Move(home, home - 2))
    }

    // ------------------------------------------------------------ make move

    fun isCapture(move: Move): Boolean =
        board[move.to] != 0 || isEnPassant(move)

    fun isEnPassant(move: Move): Boolean =
        kotlin.math.abs(board[move.from]) == PAWN && move.to == epSquare &&
            Square.file(move.from) != Square.file(move.to) && board[move.to] == 0

    fun isCastle(move: Move): Boolean =
        kotlin.math.abs(board[move.from]) == KING && kotlin.math.abs(move.to - move.from) == 2

    /** Applies a (pseudo-)legal move without checking legality. */
    fun play(move: Move): Position {
        val b = board.copyOf()
        val piece = b[move.from]
        val s = if (piece > 0) 1 else -1
        val type = kotlin.math.abs(piece)
        val captured = b[move.to]
        var halfmove = halfmoveClock + 1

        if (type == PAWN && move.to == epSquare && Square.file(move.from) != Square.file(move.to) && captured == 0) {
            b[move.to - 8 * s] = 0
        }
        b[move.to] = if (move.promotion != null) move.promotion.code * s else piece
        b[move.from] = 0

        if (type == KING && kotlin.math.abs(move.to - move.from) == 2) {
            if (move.to > move.from) { b[move.from + 1] = b[move.from + 3]; b[move.from + 3] = 0 }
            else { b[move.from - 1] = b[move.from - 4]; b[move.from - 4] = 0 }
        }
        if (type == PAWN || captured != 0) halfmove = 0

        var rights = castling
        rights = rights and CASTLE_MASK[move.from] and CASTLE_MASK[move.to]

        var ep = -1
        if (type == PAWN && kotlin.math.abs(move.to - move.from) == 16) {
            val target = (move.from + move.to) / 2
            // Only record the square if an enemy pawn could actually capture there,
            // so that repetition detection compares positions correctly.
            val f = Square.file(move.to)
            val enemyPawn = -PAWN * s
            if ((f > 0 && b[move.to - 1] == enemyPawn) || (f < 7 && b[move.to + 1] == enemyPawn)) ep = target
        }

        return Position(
            b,
            sideToMove.opposite,
            rights,
            ep,
            halfmove,
            if (sideToMove == Side.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    // ------------------------------------------------------------------- FEN

    fun toFen(): String = "${placementFen()} ${if (sideToMove == Side.WHITE) 'w' else 'b'} ${castlingFen()} " +
        "${if (epSquare >= 0) Square.name(epSquare) else "-"} $halfmoveClock $fullmoveNumber"

    /** Key that identifies the position for repetition purposes. */
    val repetitionKey: String
        get() = "${placementFen()} $sideToMove ${castlingFen()} $epSquare"

    private fun placementFen(): String = buildString {
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = Piece.fromCode(board[Square.of(f, r)])
                if (p == null) empty++ else {
                    if (empty > 0) { append(empty); empty = 0 }
                    append(p.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (r > 0) append('/')
        }
    }

    private fun castlingFen(): String {
        if (castling == 0) return "-"
        return buildString {
            if (castling and 1 != 0) append('K')
            if (castling and 2 != 0) append('Q')
            if (castling and 4 != 0) append('k')
            if (castling and 8 != 0) append('q')
        }
    }

    /** Pieces on the board with their squares. */
    fun pieces(): List<Pair<Int, Piece>> =
        (0 until 64).mapNotNull { sq -> Piece.fromCode(board[sq])?.let { sq to it } }

    override fun equals(other: Any?): Boolean = other is Position && toFen() == other.toFen()
    override fun hashCode(): Int = toFen().hashCode()
    override fun toString(): String = toFen()

    companion object {
        internal const val PAWN = 1
        internal const val KNIGHT = 2
        internal const val BISHOP = 3
        internal const val ROOK = 4
        internal const val QUEEN = 5
        internal const val KING = 6

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val KNIGHT_STEPS = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
        )
        private val KING_STEPS = arrayOf(
            intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        )
        private val ROOK_DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        private val BISHOP_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))
        private val PROMOTIONS = arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        private val CASTLE_MASK = IntArray(64) { 15 }.also {
            it[0] = 15 and 2.inv()       // a1
            it[7] = 15 and 1.inv()       // h1
            it[4] = 15 and 3.inv()       // e1
            it[56] = 15 and 8.inv()      // a8
            it[63] = 15 and 4.inv()      // h8
            it[60] = 15 and 12.inv()     // e8
        }

        fun start(): Position = fromFen(START_FEN)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Bad FEN: $fen" }
            val board = IntArray(64)
            val rows = parts[0].split('/')
            require(rows.size == 8) { "Bad FEN placement: $fen" }
            for ((i, row) in rows.withIndex()) {
                val r = 7 - i
                var f = 0
                for (c in row) {
                    if (c.isDigit()) f += c - '0' else {
                        val p = Piece.fromFenChar(c) ?: throw IllegalArgumentException("Bad FEN piece: $c")
                        require(f < 8) { "Bad FEN row: $row" }
                        board[Square.of(f, r)] = p.code
                        f++
                    }
                }
                require(f == 8) { "Bad FEN row: $row" }
            }
            val side = when (parts[1]) {
                "w" -> Side.WHITE
                "b" -> Side.BLACK
                else -> throw IllegalArgumentException("Bad FEN side: ${parts[1]}")
            }
            var castling = 0
            if (parts[2] != "-") for (c in parts[2]) castling = castling or when (c) {
                'K' -> 1; 'Q' -> 2; 'k' -> 4; 'q' -> 8
                else -> throw IllegalArgumentException("Bad FEN castling: ${parts[2]}")
            }
            val ep = if (parts[3] == "-") -1 else Square.parse(parts[3])
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
            return Position(board, side, castling, ep, half, full)
        }
    }
}
