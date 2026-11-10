package com.thunderbascraft.satranc.chess

/** Standard Algebraic Notation. */
object San {

    fun of(position: Position, move: Move, legal: List<Move> = position.legalMoves()): String {
        val piece = position.pieceAt(move.from) ?: throw IllegalArgumentException("No piece on ${Square.name(move.from)}")
        val sb = StringBuilder()
        if (position.isCastle(move)) {
            sb.append(if (move.to > move.from) "O-O" else "O-O-O")
        } else {
            val capture = position.isCapture(move)
            if (piece.type == PieceType.PAWN) {
                if (capture) sb.append('a' + Square.file(move.from)).append('x')
                sb.append(Square.name(move.to))
                move.promotion?.let { sb.append('=').append(it.letter) }
            } else {
                sb.append(piece.type.letter)
                val rivals = legal.filter {
                    it.to == move.to && it.from != move.from && position.pieceAt(it.from)?.type == piece.type
                }
                if (rivals.isNotEmpty()) {
                    val sameFile = rivals.any { Square.file(it.from) == Square.file(move.from) }
                    val sameRank = rivals.any { Square.rank(it.from) == Square.rank(move.from) }
                    when {
                        !sameFile -> sb.append('a' + Square.file(move.from))
                        !sameRank -> sb.append('1' + Square.rank(move.from))
                        else -> sb.append(Square.name(move.from))
                    }
                }
                if (capture) sb.append('x')
                sb.append(Square.name(move.to))
            }
        }
        val after = position.play(move)
        if (after.isInCheck()) {
            sb.append(if (after.legalMoves().isEmpty()) '#' else '+')
        }
        return sb.toString()
    }

    /** Parses a SAN move in [position]; returns null if it is not a legal move. */
    fun parse(position: Position, san: String): Move? {
        val clean = san.trim().trimEnd('+', '#', '!', '?')
        val legal = position.legalMoves()
        return legal.firstOrNull { of(position, it, legal).trimEnd('+', '#') == clean }
            ?: legal.firstOrNull { // accept "0-0" and missing "=" in promotions
                of(position, it, legal).trimEnd('+', '#').replace("O", "0").replace("=", "") ==
                    clean.replace("O", "0").replace("=", "")
            }
            ?: lenient(position, clean, legal)
    }

    private val LENIENT = Regex("""^([NBRQKP])?([a-h])?([1-8])?[x:-]?([a-h][1-8])=?([NBRQnbrq])?$""")

    /** Accepts over-disambiguated or long-ish SAN such as "Ngf3", "Pe4", "e2-e4", "exd6", "e8Q". */
    private fun lenient(position: Position, san: String, legal: List<Move>): Move? {
        val m = LENIENT.matchEntire(san) ?: return null
        val (pieceL, fileL, rankL, target, promoL) = m.destructured
        val type = if (pieceL.isEmpty()) PieceType.PAWN else PieceType.fromLetter(pieceL[0])!!
        val to = Square.parse(target)
        val promo = promoL.firstOrNull()?.let { PieceType.fromLetter(it) }
        val candidates = legal.filter {
            it.to == to && position.pieceAt(it.from)?.type == type && it.promotion == promo &&
                (fileL.isEmpty() || Square.file(it.from) == fileL[0] - 'a') &&
                (rankL.isEmpty() || Square.rank(it.from) == rankL[0] - '1')
        }
        return candidates.singleOrNull()
    }
}
