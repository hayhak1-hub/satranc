package com.thunderbascraft.satranc.chess

enum class Side {
    WHITE, BLACK;

    val opposite: Side get() = if (this == WHITE) BLACK else WHITE
    internal val sign: Int get() = if (this == WHITE) 1 else -1
}

enum class PieceType(val letter: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K');

    /** Internal piece code (1..6). */
    internal val code: Int get() = ordinal + 1

    companion object {
        internal fun fromCode(code: Int): PieceType = entries[kotlin.math.abs(code) - 1]
        fun fromLetter(c: Char): PieceType? = entries.firstOrNull { it.letter == c.uppercaseChar() }
    }
}

data class Piece(val type: PieceType, val side: Side) {
    /** FEN character: uppercase for white, lowercase for black. */
    val fenChar: Char get() = if (side == Side.WHITE) type.letter else type.letter.lowercaseChar()

    internal val code: Int get() = type.code * side.sign

    companion object {
        internal fun fromCode(code: Int): Piece? =
            if (code == 0) null else Piece(PieceType.fromCode(code), if (code > 0) Side.WHITE else Side.BLACK)

        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromLetter(c) ?: return null
            return Piece(type, if (c.isUpperCase()) Side.WHITE else Side.BLACK)
        }
    }
}

/** Squares are 0..63, a1 = 0, b1 = 1, ..., h8 = 63. */
object Square {
    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3
    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"
    fun parse(name: String): Int {
        require(name.length == 2) { "Bad square: $name" }
        val f = name[0] - 'a'
        val r = name[1] - '1'
        require(f in 0..7 && r in 0..7) { "Bad square: $name" }
        return of(f, r)
    }
    fun isLight(sq: Int): Boolean = (file(sq) + rank(sq)) % 2 == 1
}

data class Move(val from: Int, val to: Int, val promotion: PieceType? = null) {
    /** UCI notation, e.g. e2e4 or e7e8q. */
    val uci: String
        get() = Square.name(from) + Square.name(to) + (promotion?.letter?.lowercaseChar()?.toString() ?: "")

    override fun toString(): String = uci

    companion object {
        fun fromUci(uci: String): Move {
            require(uci.length == 4 || uci.length == 5) { "Bad UCI move: $uci" }
            val promo = if (uci.length == 5) PieceType.fromLetter(uci[4]) else null
            return Move(Square.parse(uci.substring(0, 2)), Square.parse(uci.substring(2, 4)), promo)
        }
    }
}
