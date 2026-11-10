package com.thunderbascraft.satranc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.data.Mode
import com.thunderbascraft.satranc.ui.AboutScreen
import com.thunderbascraft.satranc.ui.AnalysisScreen
import com.thunderbascraft.satranc.ui.GameScreen
import com.thunderbascraft.satranc.ui.GameViewModel
import com.thunderbascraft.satranc.ui.HomeScreen
import com.thunderbascraft.satranc.ui.ImportDialogs
import com.thunderbascraft.satranc.ui.ImportState
import com.thunderbascraft.satranc.ui.ReviewScreen
import com.thunderbascraft.satranc.ui.SatrancTheme
import com.thunderbascraft.satranc.ui.SettingsScreen
import com.thunderbascraft.satranc.ui.readPgnText

private enum class Screen { HOME, GAME, ANALYSIS, REVIEW, SETTINGS, ABOUT }

class MainActivity : AppCompatActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            SatrancTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                var gameLoaded by rememberSaveable { mutableStateOf(false) }
                /** Where the analysis was opened from (GAME or REVIEW). */
                var analysisFrom by rememberSaveable { mutableStateOf(Screen.GAME) }
                val importState by vm.importState.collectAsStateWithLifecycle()

                LaunchedEffect(importState) {
                    if (importState == ImportState.Opened) {
                        screen = Screen.REVIEW
                        vm.dismissImport()
                    }
                }

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
                            onImportText = vm::importPgn,
                            onImportError = vm::importFailed,
                            modifier = mod,
                        )
                        Screen.GAME -> {
                            BackHandler { screen = Screen.HOME }
                            GameScreen(vm, onMenu = { screen = Screen.HOME }, modifier = mod, onAnalyse = {
                                vm.analyseReview(false)
                                analysisFrom = Screen.GAME
                                screen = Screen.ANALYSIS
                            })
                        }
                        Screen.ANALYSIS -> {
                            BackHandler { screen = analysisFrom }
                            AnalysisScreen(vm, onBack = { screen = analysisFrom }, modifier = mod)
                        }
                        Screen.REVIEW -> {
                            BackHandler { screen = Screen.HOME }
                            ReviewScreen(
                                vm,
                                onBack = { screen = Screen.HOME },
                                onAnalyse = {
                                    vm.analyseReview(true)
                                    analysisFrom = Screen.REVIEW
                                    screen = Screen.ANALYSIS
                                },
                                onContinue = {
                                    gameLoaded = true
                                    screen = Screen.GAME
                                },
                                modifier = mod,
                            )
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

                ImportDialogs(importState, onChoose = vm::chooseImported, onDismiss = vm::dismissImport)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** A .pgn file opened or text shared from another app. */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> return
        }
        if (uri != null) {
            runCatching { readPgnText(this, uri) }.fold(vm::importPgn, vm::importFailed)
        } else {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(vm::importPgn)
        }
    }
}
