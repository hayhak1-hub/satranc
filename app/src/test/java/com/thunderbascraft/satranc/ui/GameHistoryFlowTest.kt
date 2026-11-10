package com.thunderbascraft.satranc.ui

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.thunderbascraft.satranc.chess.Move
import com.thunderbascraft.satranc.chess.Pgn
import com.thunderbascraft.satranc.data.Mode
import com.thunderbascraft.satranc.data.history.AppDatabase
import com.thunderbascraft.satranc.data.history.EndReason
import com.thunderbascraft.satranc.data.history.GameRecord
import com.thunderbascraft.satranc.data.history.HistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Games played through the view model end up in the history exactly once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameHistoryFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var vm: GameViewModel

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.history = HistoryRepository(db.games())
    }

    @After fun tearDown() = db.close()

    private fun play(vararg uci: String) = uci.forEach {
        val m = Move.fromUci(it)
        vm.onSquareTapped(m.from); vm.onSquareTapped(m.to)
    }

    /** Lets the view model's coroutines (main looper + IO) finish. */
    private fun records(expected: Int, until: (List<GameRecord>) -> Boolean = { true }): List<GameRecord> {
        repeat(150) {
            shadowOf(Looper.getMainLooper()).idle()
            val r = runBlocking { db.games().all() }
            if (r.size == expected && until(r)) return r
            Thread.sleep(20)
        }
        return runBlocking { db.games().all() }
    }

    @Test fun finishedGameIsSavedOnce() {
        vm.newGame(Mode.TWO_PLAYERS)
        play("f2f3", "e7e5", "g2g4")
        vm.leaveGame()                    // unfinished, 3 plies
        val first = records(1).single()
        assertEquals(EndReason.UNFINISHED, first.reason)

        play("d8h4")                       // mate
        val r = records(1) { it.single().reason != EndReason.UNFINISHED }
        assertEquals(1, r.size)
        val rec = r.single()
        assertEquals(first.id, rec.id)
        assertEquals(EndReason.CHECKMATE, rec.reason)
        assertEquals("0-1", rec.result)
        assertEquals(4, rec.moveCount)
        assertEquals(4, Pgn.parseAll(rec.pgn).single().game.moves.size)

        vm.leaveGame()                     // leaving again does not duplicate it
        vm.newGame(Mode.TWO_PLAYERS)       // nor does starting a new (empty) game
        Thread.sleep(50)
        assertEquals(1, records(1).size)
    }

    @Test fun resignedGameAndNextGameAreSeparateRecords() {
        vm.newGame(Mode.TWO_PLAYERS)
        play("e2e4", "e7e5")
        vm.resign()
        assertEquals(EndReason.RESIGNATION, records(1) { it.single().reason == EndReason.RESIGNATION }.single().reason)
        vm.newGame(Mode.TWO_PLAYERS)
        play("d2d4", "d7d5")
        vm.newGame(Mode.TWO_PLAYERS)       // the unfinished one is kept
        val all = records(2)
        assertEquals(2, all.size)
        assertTrue(all.any { it.reason == EndReason.UNFINISHED && it.moveCount == 2 })
    }
}
