package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
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
 * Single-line label: shrinks to fit narrow chips and buttons (e.g. "Vereinsspieler" at a
 * large font scale) and only ellipsizes as a last resort, instead of wrapping letter by letter.
 */
@Composable
fun FitText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val maxSize = LocalTextStyle.current.fontSize.takeIf { it.isSpecified } ?: 14.sp
    Text(
        text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = maxSize),
    )
}

private val MaxContentWidth = 520.dp

@Composable
fun HomeScreen(
    canContinue: Boolean,
    initialLevel: Level,
    onContinue: () -> Unit,
    onVsComputer: (Level, Side) -> Unit,
    onTwoPlayers: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
    /** Called with PGN text read from a file or pasted. */
    onImportText: (String) -> Unit = {},
    onImportError: (Throwable) -> Unit = {},
) {
    var level by rememberSaveable { mutableStateOf(initialLevel) }
    var side by rememberSaveable { mutableStateOf(SideChoice.WHITE) }
    var importMenu by remember { mutableStateOf(false) }
    var pasteOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { readPgnText(context, uri) }.fold(onImportText, onImportError)
    }
    if (pasteOpen) {
        PastePgnDialog(onLoad = { pasteOpen = false; onImportText(it) }, onDismiss = { pasteOpen = false })
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero()

            if (canContinue) ContinueCard(onContinue)

            ElevatedCard(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle(Icons.Filled.SmartToy, stringResource(R.string.vs_computer))
                    Text(
                        stringResource(R.string.level),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Level.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { l ->
                                FilterChip(
                                    selected = level == l,
                                    onClick = { level = l },
                                    label = { FitText("${l.number}. ${levelName(l)}") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.play_as),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SideChoice.entries.forEach { c ->
                            FilterChip(
                                selected = side == c,
                                onClick = { side = c },
                                label = {
                                    FitText(
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
                    Button(
                        onClick = {
                            val s = when (side) {
                                SideChoice.WHITE -> Side.WHITE
                                SideChoice.BLACK -> Side.BLACK
                                SideChoice.RANDOM -> if (Math.random() < 0.5) Side.WHITE else Side.BLACK
                            }
                            onVsComputer(level, s)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start))
                    }
                }
            }

            ElevatedCard(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle(Icons.Filled.People, stringResource(R.string.two_players))
                    Text(
                        stringResource(R.string.two_players_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onTwoPlayers, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Box {
                IconButton(onClick = { importMenu = true }) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = stringResource(R.string.import_pgn))
                }
                DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_pgn_file)) },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                        onClick = { importMenu = false; openFile.launch(PGN_OPEN_TYPES) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.paste_pgn)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                        onClick = { importMenu = false; pasteOpen = true },
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about))
            }
        }
    }
}

@Composable
private fun Hero() {
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.piece_wn), contentDescription = null, modifier = Modifier.size(68.dp))
            }
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ContinueCard(onContinue: () -> Unit) {
    Card(
        onClick = onContinue,
        modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.continue_game), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.continue_desc), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
