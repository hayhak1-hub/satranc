package com.thunderbascraft.satranc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thunderbascraft.satranc.data.Mode
import com.thunderbascraft.satranc.ui.AboutScreen
import com.thunderbascraft.satranc.ui.AnalysisScreen
import com.thunderbascraft.satranc.ui.GameScreen
import com.thunderbascraft.satranc.ui.GameViewModel
import com.thunderbascraft.satranc.ui.HomeScreen
import com.thunderbascraft.satranc.ui.SatrancTheme
import com.thunderbascraft.satranc.ui.SettingsScreen

private enum class Screen { HOME, GAME, ANALYSIS, SETTINGS, ABOUT }

class MainActivity : AppCompatActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SatrancTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                var gameLoaded by rememberSaveable { mutableStateOf(false) }
                Scaffold(Modifier.fillMaxSize()) { padding ->
                    val mod = Modifier.padding(padding)
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            canContinue = gameLoaded || vm.savedGame != null,
                            initialLevel = vm.lastLevel,
                            onContinue = {
                                if (!gameLoaded) vm.continueSaved()
                                gameLoaded = true
                                screen = Screen.GAME
                            },
                            onVsComputer = { level, side ->
                                vm.newGame(Mode.VS_COMPUTER, level, side)
                                gameLoaded = true
                                screen = Screen.GAME
                            },
                            onTwoPlayers = {
                                vm.newGame(Mode.TWO_PLAYERS)
                                gameLoaded = true
                                screen = Screen.GAME
                            },
                            onAbout = { screen = Screen.ABOUT },
                            onSettings = { screen = Screen.SETTINGS },
                            modifier = mod,
                        )
                        Screen.GAME -> {
                            BackHandler { screen = Screen.HOME }
                            GameScreen(vm, onMenu = { screen = Screen.HOME }, modifier = mod, onAnalyse = { screen = Screen.ANALYSIS })
                        }
                        Screen.ANALYSIS -> {
                            BackHandler { screen = Screen.GAME }
                            AnalysisScreen(vm, onBack = { screen = Screen.GAME }, modifier = mod)
                        }
                        Screen.SETTINGS -> {
                            BackHandler { screen = Screen.HOME }
                            SettingsScreen(vm, onBack = { screen = Screen.HOME }, modifier = mod)
                        }
                        Screen.ABOUT -> {
                            BackHandler { screen = Screen.HOME }
                            AboutScreen(onBack = { screen = Screen.HOME }, modifier = mod)
                        }
                    }
                }
            }
        }
    }
}
