package com.thunderbascraft.satranc.online

import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameCodeTest {
    @Test fun generatedCodesAreValidAndUnambiguous() {
        val random = Random(42)
        repeat(1000) {
            val code = GameCode.generate(random)
            assertEquals(6, code.length)
            assertTrue(code, GameCode.isValid(code))
            assertTrue(code, code.none { it in "0O1I" })
        }
    }

    @Test fun alphabetHasNoLookAlikes() {
        assertTrue(GameCode.ALPHABET.none { it in "0O1I" })
        assertEquals(GameCode.ALPHABET.length, GameCode.ALPHABET.toSet().size)
    }

    @Test fun normalizeAndValidate() {
        assertEquals("ABC234", GameCode.normalize(" abc-234 "))
        assertTrue(GameCode.isValid("ABC234"))
        assertFalse(GameCode.isValid("ABC23"))
        assertFalse(GameCode.isValid("ABC2340"))
        assertFalse(GameCode.isValid("ABCO23")) // O is not used
        assertFalse(GameCode.isValid("abc234"))
    }
}

class OnlineLogicTest {
    private val w = "uid-white"
    private val b = "uid-black"

    private fun active(vararg moves: String) =
        OnlineGame(white = w, black = b, status = OnlineStatus.ACTIVE, moves = moves.toList())

    @Test fun createAndJoin() {
        val g = OnlineLogic.create(b, Side.BLACK)
        assertEquals(b, g.black)
        assertNull(g.white)
        assertEquals(OnlineStatus.WAITING, g.status)

        val joined = OnlineLogic.join(g, w) as JoinResult.Joined
        assertEquals(w, joined.game.white)
        assertEquals(OnlineStatus.ACTIVE, joined.game.status)

        assertEquals(JoinResult.AlreadyIn, OnlineLogic.join(g, b))
        assertEquals(JoinResult.Full, OnlineLogic.join(joined.game, "someone"))
        assertEquals(JoinResult.Finished, OnlineLogic.join(g.copy(status = OnlineStatus.FINISHED), "someone"))
    }

    @Test fun onlyThePlayerToMoveMayMove() {
        val g = active()
        assertNull("black cannot start", OnlineLogic.applyMove(g, b, "e7e5", 0))
        val g1 = assertNotNullAnd(OnlineLogic.applyMove(g, w, "e2e4", 0))
        assertEquals(listOf("e2e4"), g1.moves)
        assertEquals(Side.BLACK, g1.sideToMove)
        assertNull("white cannot move twice", OnlineLogic.applyMove(g1, w, "d2d4", 1))
        assertNull("strangers cannot move", OnlineLogic.applyMove(g1, "x", "e7e5", 1))
        assertEquals(listOf("e2e4", "e7e5"), OnlineLogic.applyMove(g1, b, "e7e5", 1)?.moves)
    }

    @Test fun staleMoveCountIsRejected() {
        // Both clients saw 0 moves; after white's move the second write must fail.
        val g1 = active("e2e4")
        assertNull(OnlineLogic.applyMove(g1, w, "d2d4", 0))
    }

    @Test fun illegalMovesAreRejected() {
        assertNull(OnlineLogic.applyMove(active(), w, "e2e5", 0))
        assertNull(OnlineLogic.applyMove(active(), w, "zz", 0))
        assertNull(OnlineLogic.applyMove(active().copy(status = OnlineStatus.WAITING), w, "e2e4", 0))
    }

    @Test fun replayDetectsIllegalHistory() {
        assertNotNull(OnlineLogic.replay(listOf("e2e4", "e7e5", "g1f3")))
        assertNull(OnlineLogic.replay(listOf("e2e4", "e2e4")))
    }

    @Test fun checkmateFinishesTheGame() {
        // Fool's mate.
        val g = active("f2f3", "e7e5", "g2g4")
        val done = assertNotNullAnd(OnlineLogic.applyMove(g, b, "d8h4", 3))
        assertEquals(OnlineStatus.FINISHED, done.status)
        assertEquals("0-1", done.result)
        assertEquals(Reason.CHECKMATE, done.reason)
        assertEquals(GameResult.Checkmate(Side.BLACK), OnlineLogic.gameResult(done))
        assertNull("no moves after the end", OnlineLogic.applyMove(done, w, "a2a3", 4))
    }

    @Test fun promotionIsKeptInUci() {
        val moves = listOf("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6a7", "e7e6")
        val g = active(*moves.toTypedArray())
        val after = assertNotNullAnd(OnlineLogic.applyMove(g, w, "a7b8q", moves.size))
        assertEquals("a7b8q", after.moves.last())
    }

    @Test fun resignAndDraws() {
        val g = active("e2e4")
        val resigned = assertNotNullAnd(OnlineLogic.resign(g, w))
        assertEquals("0-1", resigned.result)
        assertEquals(GameResult.Resignation(Side.BLACK), OnlineLogic.gameResult(resigned))
        assertNull(OnlineLogic.resign(g, "x"))

        val offered = assertNotNullAnd(OnlineLogic.offerDraw(g, w))
        assertEquals(w, offered.drawOffer)
        assertNull("cannot accept own offer", OnlineLogic.acceptDraw(offered, w))
        val drawn = assertNotNullAnd(OnlineLogic.acceptDraw(offered, b))
        assertEquals("1/2-1/2", drawn.result)
        assertEquals(GameResult.Agreement, OnlineLogic.gameResult(drawn))
        assertNull(assertNotNullAnd(OnlineLogic.clearDrawOffer(offered, b)).drawOffer)
        // A move answers an open offer.
        assertNull(OnlineLogic.applyMove(offered, b, "e7e5", 1)?.drawOffer)
    }

    @Test fun abandonedNeedsTheOpponentOfflineLongEnough() {
        val now = 10_000_000L
        val g = active("e2e4").copy(
            presence = mapOf(w to PlayerPresence(true, now), b to PlayerPresence(false, now - 60_000)),
        )
        assertNull(OnlineLogic.claimAbandoned(g, w, now))
        val later = now + OnlineLogic.ABANDON_MS
        val claimed = assertNotNullAnd(OnlineLogic.claimAbandoned(g, w, later))
        assertEquals("1-0", claimed.result)
        assertEquals(Reason.ABANDONED, claimed.reason)
        assertNull("the offline player cannot claim", OnlineLogic.claimAbandoned(g, b, later))
    }

    @Test fun expiry() {
        val g = active().copy(createdAt = 1_000L, lastMoveAt = 1_000L)
        assertFalse(OnlineLogic.isExpired(g, 1_000L + OnlineLogic.EXPIRY_MS))
        assertTrue(OnlineLogic.isExpired(g, 2_000L + OnlineLogic.EXPIRY_MS))
    }

    @Test fun mapRoundTrip() {
        val g = active("e2e4", "e7e5").copy(
            drawOffer = w, createdAt = 5L, lastMoveAt = 7L,
            presence = mapOf(w to PlayerPresence(true, 3L)),
        )
        val map = g.toMap()
        assertEquals("e2e4 e7e5", map["moves"])
        assertEquals(2, map["moveCount"])
        assertEquals("active", map["status"])
        // The database returns numbers as Long.
        val fromDb = map.mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        assertEquals(g, OnlineGame.fromMap(fromDb))
        assertNull(OnlineGame.fromMap(null))
        assertNull(OnlineGame.fromMap(mapOf("status" to "bogus")))
        assertEquals(emptyList<String>(), OnlineGame.fromMap(OnlineLogic.create(w, Side.WHITE).toMap())?.moves)
    }

    private fun assertNotNullAnd(g: OnlineGame?): OnlineGame {
        assertNotNull(g)
        return g!!
    }
}
