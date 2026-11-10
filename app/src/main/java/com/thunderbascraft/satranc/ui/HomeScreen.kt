package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.engine.Level

enum class SideChoice { WHITE, BLACK, RANDOM }

@Composable
fun levelName(level: Level): String = stringResource(
    when (level) {
        Level.NOVICE -> R.string.level_1
        Level.BEGINNER -> R.string.level_2
        Level.CASUAL -> R.string.level_3
        Level.CLUB -> R.string.level_4
        Level.EXPERT -> R.string.level_5
        Level.MASTER -> R.string.level_6
    }
)

/**
 * Single-line chip label: shrinks to fit narrow chips (e.g. "Vereinsspieler" at a large
 * font scale) and only ellipsizes as a last resort, instead of wrapping letter by letter.
 */
@Composable
private fun ChipText(text: String) {
    val maxSize = LocalTextStyle.current.fontSize.takeIf { it.isSpecified } ?: 14.sp
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = maxSize),
    )
}

@Composable
fun HomeScreen(
    canContinue: Boolean,
    initialLevel: Level,
    onContinue: () -> Unit,
    onVsComputer: (Level, Side) -> Unit,
    onTwoPlayers: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var level by rememberSaveable { mutableStateOf(initialLevel) }
    var side by rememberSaveable { mutableStateOf(SideChoice.WHITE) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Image(painterResource(R.drawable.piece_wn), contentDescription = null, modifier = Modifier.size(72.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        if (canContinue) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                Text(stringResource(R.string.continue_game))
            }
        }

        Card(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.vs_computer), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.level), style = MaterialTheme.typography.labelLarge)
                Level.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { l ->
                            FilterChip(
                                selected = level == l,
                                onClick = { level = l },
                                label = { ChipText("${l.number}. ${levelName(l)}") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Text(stringResource(R.string.play_as), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideChoice.entries.forEach { c ->
                        FilterChip(
                            selected = side == c,
                            onClick = { side = c },
                            label = {
                                ChipText(
                                    stringResource(
                                        when (c) {
                                            SideChoice.WHITE -> R.string.white
                                            SideChoice.BLACK -> R.string.black
                                            SideChoice.RANDOM -> R.string.random
                                        }
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        val s = when (side) {
                            SideChoice.WHITE -> Side.WHITE
                            SideChoice.BLACK -> Side.BLACK
                            SideChoice.RANDOM -> if (Math.random() < 0.5) Side.WHITE else Side.BLACK
                        }
                        onVsComputer(level, s)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.start)) }
            }
        }

        Card(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.two_players), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.two_players_desc), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onTwoPlayers, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.start))
                }
            }
        }

        TextButton(onClick = onAbout) { Text(stringResource(R.string.about)) }
    }
}
