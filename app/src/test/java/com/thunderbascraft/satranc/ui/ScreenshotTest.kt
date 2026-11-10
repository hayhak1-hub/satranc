package com.thunderbascraft.satranc.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import com.thunderbascraft.satranc.chess.Evaluation
import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.Square
import com.thunderbascraft.satranc.engine.ChessEngine
import com.thunderbascraft.satranc.engine.Level
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens to PNG files. Runs only when SCREENSHOT_DIR is set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val dir = System.getenv("SCREENSHOT_DIR")?.let(::File)

    private fun save(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        dir!!.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() {
        assumeTrue(dir != null)
        compose.setContent {
            SatrancTheme { HomeScreen(true, Level.CASUAL, {}, { _, _ -> }, {}, {}) }
        }
        save("home")
    }

    @Test fun gameScreen() {
        assumeTrue(dir != null)
        val vm = GameViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        vm.newGame(com.thunderbascraft.satranc.data.Mode.TWO_PLAYERS)
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5").forEach { uci ->
            val m = com.thunderbascraft.satranc.chess.Move.fromUci(uci)
            vm.onSquareTapped(m.from); vm.onSquareTapped(m.to)
        }
        compose.setContent { SatrancTheme { GameScreen(vm, onMenu = {}) } }
        save("game")
    }

    private fun play(vm: GameViewModel, vararg uci: String) = uci.forEach {
        val m = com.thunderbascraft.satranc.chess.Move.fromUci(it)
        vm.onSquareTapped(m.from); vm.onSquareTapped(m.to)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun homeTurkishLargeFont() {
        assumeTrue(dir != null)
        compose.setContent {
            SatrancTheme { HomeScreen(true, Level.CLUB, {}, { _, _ -> }, {}, {}) }
        }
        save("home_tr_font130")
    }

    @Test @Config(qualifiers = "de-w411dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun homeGermanLargeFont() {
        assumeTrue(dir != null)
        compose.setContent {
            SatrancTheme { HomeScreen(false, Level.CLUB, {}, { _, _ -> }, {}, {}) }
        }
        save("home_de_font130")
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
    fun finishedGameWithResultDialog() {
        assumeTrue(dir != null)
        val vm = GameViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        vm.newGame(com.thunderbascraft.satranc.data.Mode.TWO_PLAYERS)
        play(vm, "f2f3", "e7e5", "g2g4", "d8h4")
        compose.setContent { SatrancTheme { GameScreen(vm, onMenu = {}) } }
        compose.waitForIdle()
        compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().let { saveBitmap("game_over", it) }
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { saveBitmap("game_over_dialog", it) }
    }

    @Test @Config(qualifiers = "tr-w891dp-h411dp-land-xxhdpi")
    fun gameLandscape() {
        assumeTrue(dir != null)
        val vm = GameViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        vm.newGame(com.thunderbascraft.satranc.data.Mode.VS_COMPUTER, Level.BEGINNER)
        compose.setContent { SatrancTheme { GameScreen(vm, onMenu = {}) } }
        save("game_landscape")
    }

    /** Fake engine with made-up evaluations: 1. e4 e5 2. Qh5 Ke7?? 3. Qxe5#. */
    private class FakeEngine : ChessEngine {
        val evals = listOf(
            Evaluation(25, null, "e2e4"),
            Evaluation(-30, null, "e7e5"),
            Evaluation(35, null, "g1f3"),
            Evaluation(-20, null, "b8c6"),
            Evaluation(null, 1, "h5e5"),
        )
        override suspend fun bestMove(startFen: String, moves: List<String>, level: Level): String? = null
        override suspend fun evaluate(startFen: String, moves: List<String>, moveTimeMs: Long) = evals[moves.size]
        override fun close() {}
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
    fun analysisScreen() {
        assumeTrue(dir != null)
        val vm = GameViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        vm.engine = FakeEngine()
        vm.newGame(com.thunderbascraft.satranc.data.Mode.TWO_PLAYERS)
        play(vm, "e2e4", "e7e5", "d1h5", "e8e7", "h5e5")
        compose.setContent { SatrancTheme { AnalysisScreen(vm, onBack = {}) } }
        compose.waitUntil(5_000) { vm.analysis.value.complete }
        vm.selectPly(4)
        compose.waitForIdle()
        save("analysis")
    }

    private fun saveBitmap(name: String, bmp: Bitmap) {
        dir!!.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun board() {
        assumeTrue(dir != null)
        val g = Game()
        listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6").forEach { g.playSan(it) }
        compose.setContent {
            SatrancTheme {
                ChessBoard(
                    position = g.position, lastMove = g.lastMove,
                    selected = Square.parse("f3"),
                    targets = g.position.legalMovesFrom(Square.parse("f3")).map { it.to }.toSet(),
                    flipped = false, onTap = {}, onDragStart = { false }, onDrop = {},
                )
            }
        }
        save("board")
    }
}
