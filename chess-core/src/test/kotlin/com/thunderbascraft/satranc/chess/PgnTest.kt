package com.thunderbascraft.satranc.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PgnTest {

    /** Morphy – Duke Karl / Count Isouard, Paris 1858 ("Opera Game"), heavily annotated. */
    private val opera = """
        [Event "Paris"]
        [Site "Paris FRA"]
        [Date "1858.??.??"]
        [Round "?"]
        [White "Paul Morphy"]
        [Black "Duke Karl / Count Isouard"]
        [Result "1-0"]

        1. e4 e5 2. Nf3 d6 {Philidor Defence. A comment
        that spans [several] lines.} 3. d4 Bg4?! $6 (3... exd4 4. Nxd4 (4. Qxd4 Nc6
        (4... Nf6)) 4... Nf6) 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3! Qe7 8. Nc3 c6
        9. Bg5 b5?! 10. Nxb5! cxb5 11. Bxb5+ Nbd7 12. 0-0-0 Rd8 13. Rxd7! Rxd7
        14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+!! Nxb8 17. Rd8# ; mate
        1-0
    """.trimIndent()

    @Test fun readsAnnotatedOperaGame() {
        val g = Pgn.parseAll(opera).single()
        assertEquals("Paul Morphy", g.white)
        assertEquals("Duke Karl / Count Isouard", g.black)
        assertEquals("Paris", g.event)
        assertEquals("1-0", g.result)
        assertEquals(33, g.game.moves.size)
        assertEquals(GameResult.Checkmate(Side.WHITE), g.game.result)
        assertEquals("O-O-O", g.game.moves[22].san)
        assertEquals("Rd8#", g.game.moves.last().san)
    }

    @Test fun multiGameFileWithBomAndCrlf() {
        val file = "﻿" + listOf(
            opera,
            """
            [Event "Blitz"]
            [White "A"]
            [Black "B"]
            [Result "1-0"]

            1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6?? 4.Qxf7# 1-0
            """.trimIndent(),
            """
            [Event "Study"]
            [White "?"]
            [Black "?"]
            [Result "*"]
            [SetUp "1"]
            [FEN "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1"]

            1. Rd8# *
            """.trimIndent(),
        ).joinToString("\n\n").replace("\n", "\r\n")

        val headers = Pgn.headers(file)
        assertEquals(listOf("Paris", "Blitz", "Study"), headers.map { it["Event"] })
        val games = Pgn.parseAll(file)
        assertEquals(3, games.size)
        assertEquals(7, games[1].game.moves.size)
        assertEquals("A", games[1].white)
        assertEquals(null, games[2].white)
        assertEquals("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", games[2].game.startPosition.toFen())
        assertEquals(GameResult.Checkmate(Side.WHITE), games[2].game.result)
    }

    @Test fun blackToMoveFromFen() {
        val pgn = """
            [FEN "4k3/8/8/8/8/8/4P3/4K3 b - - 0 12"]

            12... Kd7 13. e4 Ke6 *
        """.trimIndent()
        val g = Pgn.parseAll(pgn).single().game
        assertEquals(3, g.moves.size)
        val back = Pgn.write(g)
        assertTrue(back.contains("12... Kd7 13. e4 Ke6 *"))
    }

    @Test fun clearErrorForIllegalMove() {
        try {
            Pgn.parseAll("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ke3 *")
            fail("expected an exception")
        } catch (e: PgnException) {
            assertEquals(PgnException.Kind.ILLEGAL_MOVE, e.kind)
            assertEquals("4. Ke3", e.moveLabel)
        }
        try {
            Pgn.parseAll("1. e4 e5 2. Nf3 Qxf3?? *")
            fail("expected an exception")
        } catch (e: PgnException) {
            assertEquals("2... Qxf3", e.moveLabel)
        }
    }

    @Test fun badFenAndEmptyInput() {
        try { Pgn.parseAll("[FEN \"not a fen\"]\n\n1. e4 *"); fail() } catch (e: PgnException) {
            assertEquals(PgnException.Kind.BAD_FEN, e.kind)
        }
        try { Pgn.parseAll("   \n\n "); fail() } catch (e: PgnException) {
            assertEquals(PgnException.Kind.NO_GAME, e.kind)
        }
    }

    @Test fun enPassantAndPromotionVariants() {
        val ep = Pgn.parseAll("1. e4 a6 2. e5 d5 3. exd6 e.p. Ra7 4. dxc7 Ra8 5. cxb8Q Rxb8 *").single().game
        assertEquals("exd6", ep.moves[4].san)
        assertEquals("cxb8=Q", ep.moves[8].san)

        val fen = "[FEN \"8/P6k/8/8/8/8/8/K7 w - - 0 1\"]\n\n"
        assertEquals("a8=Q", Pgn.parseAll(fen + "1. a8=Q *").single().game.moves.single().san)
        assertEquals("a8=N", Pgn.parseAll(fen + "1. a8N *").single().game.moves.single().san)
    }

    @Test fun lenientNotation() {
        // Over-disambiguated knight move, long algebraic pawn move, attached move numbers.
        val g = Pgn.parseAll("1.e2-e4 e5 2.Ngf3 Nc6 3.Bb5 a6 *").single().game
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), g.moves.map { it.san })
    }

    @Test fun writeAndReadBack() {
        val g = Pgn.parseAll(opera).single()
        val text = Pgn.write(g.game, g.tags + ("Event" to "Opera \"Game\""))
        val back = Pgn.parseAll(text).single()
        assertEquals(g.game.moves.map { it.san }, back.game.moves.map { it.san })
        assertEquals("Opera \"Game\"", back.event)
        assertEquals("Paul Morphy", back.white)
        assertEquals("1-0", back.result)
    }
}
