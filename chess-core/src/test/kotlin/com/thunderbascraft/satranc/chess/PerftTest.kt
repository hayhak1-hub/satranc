package com.thunderbascraft.satranc.chess

import org.junit.Assert.assertEquals
import org.junit.Test

/** Move-generator correctness against well-known perft node counts. */
class PerftTest {

    private fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = pos.legalMoves()
        if (depth == 1) return moves.size.toLong()
        var n = 0L
        for (m in moves) n += perft(pos.play(m), depth - 1)
        return n
    }

    private fun check(fen: String, vararg expected: Long) {
        val pos = Position.fromFen(fen)
        expected.forEachIndexed { i, e -> assertEquals("depth ${i + 1} of $fen", e, perft(pos, i + 1)) }
    }

    @Test fun startPosition() = check(Position.START_FEN, 20, 400, 8_902, 197_281, 4_865_609)

    @Test fun kiwipete() = check(
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        48, 2_039, 97_862, 4_085_603,
    )

    @Test fun position3() = check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2_812, 43_238, 674_624)

    @Test fun position4() = check(
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        6, 264, 9_467, 422_333,
    )

    @Test fun position5() = check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1_486, 62_379, 2_103_487)

    @Test fun position6() = check(
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        46, 2_079, 89_890,
    )
}
