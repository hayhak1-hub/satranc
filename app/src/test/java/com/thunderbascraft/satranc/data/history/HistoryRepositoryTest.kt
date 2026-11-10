package com.thunderbascraft.satranc.data.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.thunderbascraft.satranc.chess.Analysis
import com.thunderbascraft.satranc.chess.Evaluation
import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.Pgn
import com.thunderbascraft.satranc.chess.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository
    private var now = 1_700_000_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HistoryRepository(db.games()) { now.also { now += 60_000 } }
    }

    @After fun tearDown() = db.close()

    private fun game(vararg san: String) = Game().also { g -> san.forEach { require(g.playSan(it)) } }

    private fun snapshot(g: Game, id: Long? = null, started: Long = now) = GameSnapshot(
        recordId = id, startedAt = started, game = g, mode = RecordMode.VS_COMPUTER, engine = "SF11",
        level = 3, humanSide = Side.WHITE, whiteName = "You", blackName = "Computer (Level 3, Stockfish 11)",
    )

    private suspend fun all() = db.games().observeAll().first()

    @Test fun unfinishedGameIsUpdatedWhenItEnds() = runBlocking {
        val g = game("e4", "e5")
        val id = repo.save(snapshot(g))!!
        var r = db.games().get(id)!!
        assertEquals(EndReason.UNFINISHED, r.reason)
        assertNull(r.finishedAt)
        assertEquals("*", r.result)

        g.playSan("Qh5"); g.playSan("Nc6"); g.playSan("Bc4"); g.playSan("Nf6"); g.playSan("Qxf7#")
        assertEquals(id, repo.save(snapshot(g, id)))
        val records = all()
        assertEquals(1, records.size)
        r = records.single()
        assertEquals(EndReason.CHECKMATE, r.reason)
        assertEquals("1-0", r.result)
        assertEquals(7, r.moveCount)
        assertNotNull(r.finishedAt)
        assertEquals("1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6", r.opening)
        assertTrue(r.pgn.contains("[Result \"1-0\"]"))
        assertTrue(r.pgn.contains("[Termination \"normal\"]"))
        assertTrue(r.pgn.contains("[PlyCount \"7\"]"))
        assertTrue(r.pgn.contains("[White \"You\"]"))
        // The stored PGN reads back to the same game.
        assertEquals(g.moves.map { it.san }, Pgn.parseAll(r.pgn).single().game.moves.map { it.san })
    }

    @Test fun resignationIsStoredWithItsResult() = runBlocking {
        val g = game("d4", "d5")
        g.resign(Side.WHITE)
        val id = repo.save(snapshot(g))!!
        val r = db.games().get(id)!!
        assertEquals(EndReason.RESIGNATION, r.reason)
        assertEquals("0-1", r.result)
        assertEquals("0-1", Pgn.parseAll(r.pgn).single().result)
    }

    @Test fun emptyOrTooShortGamesAreNotSaved() = runBlocking {
        assertNull(repo.save(snapshot(Game())))
        assertNull(repo.save(snapshot(game("e4"))))
        assertTrue(all().isEmpty())
        // ...but a game finished after one move is kept.
        val g = game("e4"); g.resign(Side.BLACK)
        assertNotNull(repo.save(snapshot(g)))
    }

    @Test fun newestFirst() = runBlocking {
        val a = repo.save(snapshot(game("e4", "e5"), started = 1000))!!
        val b = repo.save(snapshot(game("d4", "d5"), started = 5000))!!
        val c = game("c4", "c5"); c.agreeDraw()
        val cId = repo.save(snapshot(c, started = 100))!! // finished "now", i.e. latest
        assertEquals(listOf(cId, b, a), all().map { it.id })
    }

    @Test fun deleteAndUndo() = runBlocking {
        val a = repo.save(snapshot(game("e4", "e5")))!!
        val b = repo.save(snapshot(game("d4", "d5")))!!
        val removed = repo.records(listOf(a))
        repo.delete(listOf(a))
        assertEquals(listOf(b), all().map { it.id })
        repo.restore(removed)
        assertEquals(setOf(a, b), all().map { it.id }.toSet())
        repo.deleteAll()
        assertTrue(all().isEmpty())
    }

    private val file = """
        [Event "One"]
        [White "A"]
        [Black "B"]
        [Result "1-0"]

        1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0

        [Event "Two"]
        [White "C"]
        [Black "D"]
        [Result "1/2-1/2"]

        1. d4 d5 2. c4 e6 1/2-1/2

        [Event "Broken"]

        1. e4 e5 2. Ke3 *
    """.trimIndent()

    @Test fun importSkipsDuplicatesAndBrokenGames() = runBlocking {
        assertEquals(ImportSummary(imported = 2, skipped = 1), repo.importPgn(file))
        assertEquals(ImportSummary(imported = 0, skipped = 3), repo.importPgn(file))
        val records = all()
        assertEquals(2, records.size)
        assertTrue(records.all { it.mode == RecordMode.IMPORTED })
        assertEquals(setOf("A", "C"), records.map { it.white }.toSet())
        // The single-game import from the home screen finds the same record.
        val one = Pgn.parseAll(file.substringBefore("[Event \"Two\"]")).single()
        val id = repo.addImportedOrFind(one)
        assertEquals(2, all().size)
        assertEquals("A", db.games().get(id)!!.white)
    }

    @Test fun exportAllReadsBack() = runBlocking {
        repo.save(snapshot(game("e4", "e5", "Nf3")))
        val g = game("d4", "Nf6"); g.resign(Side.WHITE)
        repo.save(snapshot(g))
        repo.importPgn(file)
        val records = all()
        val text = repo.export()
        val back = Pgn.parseAll(text)
        assertEquals(records.size, back.size)
        assertEquals(records.map { it.moveCount }.sorted(), back.map { it.game.moves.size }.sorted())
        // Exporting a selection.
        assertEquals(1, Pgn.parseAll(repo.export(listOf(records.first().id))).size)
    }

    @Test fun analysisSummaryIsStored() = runBlocking {
        val g = game("e4", "e5", "Qh5", "Ke7")
        val id = repo.save(snapshot(g))!!
        val evals = listOf(
            Evaluation(20, null, "e2e4"), Evaluation(-25, null, "e7e5"), Evaluation(30, null, "g1f3"),
            Evaluation(-10, null, "b8c6"), Evaluation(600, null, "h5e5"),
        )
        repo.setAnalysis(id, Analysis.analyse(g.positionsHistory, g.moves, evals))
        val a = db.games().get(id)!!.analysis!!
        assertEquals(1, a.blackBlunders)
        assertEquals(0, a.whiteBlunders)
        // Saving the game again keeps the analysis.
        repo.save(snapshot(g, id))
        assertEquals(1, db.games().get(id)!!.analysis!!.blackBlunders)
    }
}
