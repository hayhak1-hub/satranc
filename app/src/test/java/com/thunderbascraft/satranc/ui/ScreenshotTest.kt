package com.thunderbascraft.satranc.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.Square
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
