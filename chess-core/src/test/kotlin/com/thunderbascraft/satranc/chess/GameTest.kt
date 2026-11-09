package com.thunderbascraft.satranc.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {

    private fun game(vararg san: String) = Game().apply { san.forEach { assertTrue("move $it", playSan(it)) } }

    @Test fun fenRoundTrip() {
        val fens = listOf(
            Position.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 12 40",
        )
        for (f in fens) assertEquals(f, Position.fromFen(f).toFen())
    }

    @Test fun foolsMate() {
        val g = game("f3", "e5", "g4", "Qh4#")
        assertEquals(GameResult.Checkmate(Side.BLACK), g.result)
        assertEquals("Qh4#", g.moves.last().san)
        assertTrue(g.legalMoves().isEmpty())
    }

    @Test fun stalemate() {
        val g = Game("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameResult.Stalemate, g.result)
    }

    @Test fun threefoldRepetition() {
        val g = game("Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(GameResult.Repetition, g.result)
    }

    @Test fun fiftyMoveRule() {
        val g = Game("8/8/8/4k3/8/8/4K3/7R w - - 99 80")
        assertNull(g.result)
        assertTrue(g.playSan("Rh2"))
        assertEquals(GameResult.FiftyMoves, g.result)
    }

    @Test fun insufficientMaterial() {
        assertEquals(GameResult.InsufficientMaterial, Game("8/8/8/4k3/8/8/4K3/7B w - - 0 1").result)
        assertEquals(GameResult.InsufficientMaterial, Game("8/8/8/4k3/8/8/4K3/8 w - - 0 1").result)
        assertEquals(GameResult.InsufficientMaterial, Game("8/8/2b5/4k3/8/8/4K3/7B w - - 0 1").result) // same colour
        assertNull(Game("8/8/3b4/4k3/8/8/4K3/7B w - - 0 1").result) // opposite colours
        assertNull(Game("8/8/8/4k3/8/8/4K3/6NN w - - 0 1").result)
    }

    @Test fun castlingBothSidesAndSan() {
        val g = Game("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertTrue(g.playSan("O-O"))
        assertTrue(g.playSan("O-O-O"))
        assertEquals("2kr3r/8/8/8/8/8/8/R4RK1 w - - 2 2", g.position.toFen())
    }

    @Test fun cannotCastleThroughCheck() {
        val g = Game("r3k2r/8/8/8/8/8/8/R3K1rR w KQkq - 0 1") // a piece on g1 blocks castling
        assertFalse(g.position.legalMoves().any { g.position.isCastle(it) && it.to == Square.parse("g1") })
        val g2 = Game("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1") // f2 rook attacks f1
        val castles = g2.position.legalMoves().filter { g2.position.isCastle(it) }.map { it.uci }
        assertEquals(listOf("e1c1"), castles)
    }

    @Test fun enPassant() {
        val g = game("e4", "a6", "e5", "d5")
        assertEquals("d6", Square.name(g.position.epSquare))
        assertTrue(g.playSan("exd6"))
        assertNull(g.position.pieceAt(Square.parse("d5")))
        assertEquals("exd6", g.moves.last().san)
    }

    @Test fun promotionAndUnderpromotion() {
        val g = Game("8/P7/8/8/8/8/8/k6K w - - 0 1")
        assertTrue(g.playUci("a7a8n"))
        assertEquals("a8=N", g.moves.last().san)
        assertEquals(Piece(PieceType.KNIGHT, Side.WHITE), g.position.pieceAt(Square.parse("a8")))
    }

    @Test fun sanDisambiguation() {
        val g = Game("4k3/8/8/8/8/8/4K3/R6R w - - 0 1")
        val pos = g.position
        assertEquals("Rad1", San.of(pos, Move.fromUci("a1d1")))
        val g2 = Game("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1")
        assertEquals("R1a3", San.of(g2.position, Move.fromUci("a1a3")))
    }

    @Test fun undoRestoresPosition() {
        val g = game("e4", "e5", "Nf3")
        g.undo()
        assertEquals(2, g.moves.size)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", g.position.toFen())
    }

    @Test fun pgnRoundTrip() {
        val g = game("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O")
        val pgn = g.toPgn("Ali", "Ayşe")
        val back = Game.fromPgn(pgn)
        assertEquals(g.position, back.position)
        assertTrue(pgn.contains("5. O-O *"))
    }

    @Test fun resignAndUndoClearsIt() {
        val g = game("e4")
        g.resign(Side.BLACK)
        assertEquals(GameResult.Resignation(Side.WHITE), g.result)
        assertFalse(g.play(Move.fromUci("e7e5")))
        g.undo()
        assertNull(g.result)
        assertEquals(1, g.moves.size)
    }
}
