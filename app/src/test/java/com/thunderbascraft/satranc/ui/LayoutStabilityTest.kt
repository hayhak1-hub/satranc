package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.thunderbascraft.satranc.chess.Side
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The board must not jump when the computer starts or stops thinking or the turn changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LayoutStabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playerBarKeepsItsHeight() {
        var thinking by mutableStateOf(false)
        var active by mutableStateOf(false)
        compose.setContent {
            SatrancTheme {
                Column {
                    PlayerBar("Bilgisayar · 4. Kulüp oyuncusu", Side.BLACK, active = active, thinking = thinking)
                    androidx.compose.foundation.layout.Box(Modifier.testTag("below"))
                }
            }
        }
        val top0 = compose.onNodeWithTag("below").getBoundsInRoot().top
        active = true; thinking = true
        compose.waitForIdle()
        assertEquals(top0, compose.onNodeWithTag("below").getBoundsInRoot().top)
        active = false; thinking = false
        compose.waitForIdle()
        assertEquals(top0, compose.onNodeWithTag("below").getBoundsInRoot().top)
    }
}
