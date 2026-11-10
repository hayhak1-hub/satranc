package com.thunderbascraft.satranc.ui

import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.outlined.History
import com.thunderbascraft.satranc.data.history.GameRecord
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalMaterial3Api::class)
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
    /** The latest history games (up to three are shown). */
    recent: List<GameRecord> = emptyList(),
    onHistory: () -> Unit = {},
    onOpenRecord: (GameRecord) -> Unit = {},
) {
    var level by rememberSaveable { mutableStateOf(initialLevel) }
    var side by rememberSaveable { mutableStateOf(SideChoice.WHITE) }
    var pasteOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { readPgnText(context, uri) }.fold(onImportText, onImportError)
    }
    if (pasteOpen) {
        PastePgnDialog(onLoad = { pasteOpen = false; onImportText(it) }, onDismiss = { pasteOpen = false })
    }

    var importChooser by rememberSaveable { mutableStateOf(false) }
    if (importChooser) {
        ImportChooserDialog(
            onFile = { importChooser = false; openFile.launch(PGN_OPEN_TYPES) },
            onPaste = { importChooser = false; pasteOpen = true },
            onDismiss = { importChooser = false },
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    val entries = homeDrawerEntries(
        canContinue = canContinue,
        onHome = {},
        onContinue = onContinue,
        onHistory = onHistory,
        onImport = { importChooser = true },
        onSettings = onSettings,
        onAbout = onAbout,
    )
    val menuRotation by animateFloatAsState(
        targetValue = if (drawerState.targetValue == DrawerValue.Open) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "menuIconRotation",
    )

    AppDrawer(drawerState, selectedKey = "home", entries = entries) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { FitText(stringResource(R.string.app_name)) },
            navigationIcon = {
                IconButton(
                    onClick = { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } },
                    modifier = Modifier.testTag("nav_menu"),
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.open_menu),
                        modifier = Modifier.graphicsLayer { rotationZ = menuRotation },
                    )
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
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
                if (recent.isNotEmpty()) RecentGames(recent.take(3), onHistory, onOpenRecord)
                Spacer(Modifier.height(8.dp))
            }

    }
    }
}

@Composable
private fun Hero() {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.piece_wn), contentDescription = null, modifier = Modifier.size(68.dp))
            }
        }
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

@Composable
private fun RecentGames(records: List<GameRecord>, onHistory: () -> Unit, onOpen: (GameRecord) -> Unit) {
    ElevatedCard(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.recent_games), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onHistory) { Text(stringResource(R.string.see_all)) }
            }
            for (r in records) HistoryRow(record = r, selected = false, onClick = { onOpen(r) }, onLongClick = { onOpen(r) })
        }
    }
}
