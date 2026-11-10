package com.thunderbascraft.satranc.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
    fun settingsScreen() {
        assumeTrue(dir != null)
        val vm = GameViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        compose.setContent { SatrancTheme { SettingsScreen(vm, onBack = {}) } }
        save("settings")
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
    fun reviewScreen() {
        assumeTrue(dir != null)
        val vm = GameViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val pgn = """
            [Event "Paris"]
            [Date "1858.??.??"]
            [White "Paul Morphy"]
            [Black "Duke Karl / Count Isouard"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7
            8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7
            14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0
        """.trimIndent()
        vm.openReview(com.thunderbascraft.satranc.chess.Pgn.parseAll(pgn).single())
        vm.reviewSelect(23)
        compose.setContent { SatrancTheme { ReviewScreen(vm, onBack = {}, onAnalyse = {}, onContinue = {}) } }
        save("review")
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
    fun historyScreen() {
        assumeTrue(dir != null)
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.thunderbascraft.satranc.data.history.AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = com.thunderbascraft.satranc.data.history.HistoryRepository(db.games())
        fun g(vararg san: String) = Game().also { x -> san.forEach { x.playSan(it) } }
        fun snap(game: Game, mode: com.thunderbascraft.satranc.data.history.RecordMode, side: com.thunderbascraft.satranc.chess.Side?, level: Int?, engine: String?) =
            com.thunderbascraft.satranc.data.history.GameSnapshot(null, System.currentTimeMillis(), game, mode, engine, level, side, "Sen", "Bilgisayar")
        val vs = com.thunderbascraft.satranc.data.history.RecordMode.VS_COMPUTER
        kotlinx.coroutines.runBlocking {
        val win = g("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7#")
        val winId = repo.save(snap(win, vs, com.thunderbascraft.satranc.chess.Side.WHITE, 2, "SF11"))!!
        val evals = List(win.positionsHistory.size) { i -> com.thunderbascraft.satranc.chess.Evaluation(if (i == 6) -900 else 30, null, null) }
        repo.setAnalysis(winId, com.thunderbascraft.satranc.chess.Analysis.analyse(win.positionsHistory, win.moves, evals))
        val loss = g("f3", "e5", "g4", "Qh4#")
        repo.save(snap(loss, vs, com.thunderbascraft.satranc.chess.Side.WHITE, 5, "SF19"))
        val draw = g("d4", "d5", "c4", "e6"); draw.agreeDraw()
        repo.save(snap(draw, com.thunderbascraft.satranc.data.history.RecordMode.TWO_PLAYERS, null, null, null))
        repo.save(snap(g("e4", "c5", "Nf3", "d6", "d4"), vs, com.thunderbascraft.satranc.chess.Side.BLACK, 3, "SF11"))
        repo.importPgn("[White \"Paul Morphy\"]\n[Black \"Duke Karl\"]\n[Event \"Paris\"]\n[Result \"1-0\"]\n\n1. e4 e5 2. Nf3 d6 3. d4 Bg4 1-0")
        }
        val hvm = HistoryViewModel(app)
        hvm.repo = repo
        compose.setContent { SatrancTheme { HistoryScreen(hvm, onBack = {}, onOpen = {}) } }
        compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            hvm.state.value.all.size == 5
        }
        compose.waitForIdle()
        save("history")
        db.close()
    }

    private fun openDrawerAndSave(name: String) {
        compose.setContent {
            SatrancTheme { HomeScreen(true, Level.CASUAL, {}, { _, _ -> }, {}, {}) }
        }
        compose.onNode(androidx.compose.ui.test.hasTestTag("nav_menu")).performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        save(name)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
    fun drawerOpen() {
        assumeTrue(dir != null)
        openDrawerAndSave("drawer")
    }

    @Test @Config(qualifiers = "tr-w891dp-h411dp-land-xxhdpi", fontScale = 1.3f)
    fun drawerOpenLandscapeLargeFont() {
        assumeTrue(dir != null)
        openDrawerAndSave("drawer_land_font130")
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
