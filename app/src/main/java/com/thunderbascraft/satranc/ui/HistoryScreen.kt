package com.thunderbascraft.satranc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.data.history.GameRecord
import com.thunderbascraft.satranc.data.history.RecordMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (GameRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saver = rememberPgnSaver()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { readPgnText(context, uri) }.fold(vm::importText) { vm.readFailed() }
    }

    // Snackbar texts (resolved here so they follow the app language).
    val undo = stringResource(R.string.undo)
    val readFailed = stringResource(R.string.pgn_error_read)
    val res = LocalResources.current
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.consumeMessage()
        when (m) {
            is HistoryMessage.Deleted -> {
                val r = snackbar.showSnackbar(
                    res.getQuantityString(R.plurals.games_deleted, m.records.size, m.records.size),
                    actionLabel = undo, duration = SnackbarDuration.Long,
                )
                if (r == SnackbarResult.ActionPerformed) vm.undoDelete(m.records)
            }
            is HistoryMessage.Imported -> snackbar.showSnackbar(
                res.getString(R.string.imported_summary, m.summary.imported, m.summary.skipped)
            )
            HistoryMessage.ReadFailed -> snackbar.showSnackbar(readFailed)
        }
    }

    fun exportThen(ids: Collection<Long>?, action: (String) -> Unit) {
        scope.launch { action(vm.export(ids)) }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.selecting) {
                TopAppBar(
                    title = { FitText(pluralStringResource(R.plurals.selected_count, state.selection.size, state.selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::selectAllShown) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
                        }
                        IconButton(onClick = { exportThen(state.selection) { sharePgn(context, it) } }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_pgn))
                        }
                        IconButton(onClick = { exportThen(state.selection, saver::save) }) {
                            Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save_pgn))
                        }
                        IconButton(onClick = { vm.delete(state.selection) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                TopAppBar(
                    title = { FitText(stringResource(R.string.history)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val hasGames = state.all.isNotEmpty()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_all)) },
                                    leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                                    enabled = hasGames,
                                    onClick = { menuOpen = false; exportThen(null, saver::save) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_all)) },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    enabled = hasGames,
                                    onClick = { menuOpen = false; exportThen(null) { sharePgn(context, it) } },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_into_history)) },
                                    leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                                    onClick = { menuOpen = false; openFile.launch(PGN_OPEN_TYPES) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_all)) },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                                    enabled = hasGames,
                                    onClick = { menuOpen = false; confirmDeleteAll = true },
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    StatsHeader(state.stats)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (f in HistoryFilter.entries) {
                            FilterChip(
                                selected = state.filter == f,
                                onClick = { vm.setFilter(f) },
                                label = {
                                    Text(stringResource(when (f) {
                                        HistoryFilter.ALL -> R.string.filter_all
                                        HistoryFilter.VS_COMPUTER -> R.string.filter_computer
                                        HistoryFilter.TWO_PLAYERS -> R.string.filter_two
                                        HistoryFilter.IMPORTED -> R.string.filter_imported
                                    }))
                                },
                            )
                        }
                    }
                    val shown = state.shown
                    if (state.loaded && shown.isEmpty()) {
                        Text(
                            stringResource(R.string.no_games),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    ) {
                        items(shown, key = { it.id }) { r ->
                            SwipeToDelete(onDelete = { vm.delete(listOf(r.id)) }, enabled = !state.selecting) {
                                HistoryRow(
                                    record = r,
                                    selected = r.id in state.selection,
                                    onClick = { if (state.selecting) vm.toggleSelect(r.id) else onOpen(r) },
                                    onLongClick = { vm.toggleSelect(r.id) },
                                    menu = if (state.selecting) null else { close ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.share_pgn)) },
                                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                            onClick = { close(); sharePgn(context, r.pgn) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.save_pgn)) },
                                            leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                                            onClick = { close(); saver.save(r.pgn) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete)) },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                            onClick = { close(); vm.delete(listOf(r.id)) },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            text = { Text(pluralStringResource(R.plurals.confirm_delete_all, state.all.size, state.all.size)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; vm.deleteAll() }) { Text(stringResource(R.string.delete_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) { content() }
}

@Composable
private fun StatsHeader(s: HistoryStats) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(pluralStringResource(R.plurals.games_count, s.games, s.games), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.stats_vs_computer, s.wins, s.draws, s.losses),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Opponent / players line for a record. */
@Composable
fun recordTitle(r: GameRecord): String = when (r.mode) {
    RecordMode.VS_COMPUTER -> stringResource(
        R.string.opponent_computer, r.level ?: 0, if (r.engine == "SF19") "SF 19" else "SF 11",
    )
    RecordMode.TWO_PLAYERS -> stringResource(R.string.two_players)
    RecordMode.IMPORTED -> "${r.white ?: stringResource(R.string.unknown_player)} – ${r.black ?: stringResource(R.string.unknown_player)}"
}

@Composable
private fun OutcomeChip(r: GameRecord) {
    val o = outcomeOf(r)
    val scheme = MaterialTheme.colorScheme
    val (text, bg, fg) = when (o) {
        Outcome.WIN -> Triple(stringResource(R.string.outcome_win), Color(0xFF3E7B2B), Color.White)
        Outcome.LOSS -> Triple(stringResource(R.string.outcome_loss), Color(0xFFB3261E), Color.White)
        Outcome.DRAW -> Triple(stringResource(R.string.outcome_draw), scheme.secondaryContainer, scheme.onSecondaryContainer)
        Outcome.UNFINISHED -> Triple(stringResource(R.string.outcome_unfinished), scheme.surfaceVariant, scheme.onSurfaceVariant)
        Outcome.WHITE_WON -> Triple("1-0", scheme.surfaceVariant, scheme.onSurfaceVariant)
        Outcome.BLACK_WON -> Triple("0-1", scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg, contentColor = fg, modifier = Modifier.width(92.dp)) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            FitText(if (o == Outcome.DRAW && r.mode != RecordMode.VS_COMPUTER) "½-½" else text)
        }
    }
}

/** One history entry; [menu] adds a ⋮ menu (items get a close callback). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    record: GameRecord,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val time = record.finishedAt ?: record.startedAt
    val date = remember(time) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time)) }
    val side = record.humanSide?.let { stringResource(if (it == "WHITE") R.string.white else R.string.black) }
    val fullMoves = (record.moveCount + 1) / 2
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            OutcomeChip(record)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FitText(recordTitle(record))
                Text(
                    listOfNotNull(date, side?.let { stringResource(R.string.you_played, it) },
                        pluralStringResource(R.plurals.moves_count, fullMoves, fullMoves)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.opening,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AnalysisBadges(record)
                }
            }
            if (menu != null) {
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** "?? 2  ? 1" for the player's own mistakes (or both sides' when there is no player side). */
@Composable
private fun AnalysisBadges(r: GameRecord) {
    val a = r.analysis ?: return
    val (blunders, mistakes) = when (r.humanSide) {
        "WHITE" -> a.whiteBlunders to a.whiteMistakes
        "BLACK" -> a.blackBlunders to a.blackMistakes
        else -> (a.whiteBlunders + a.blackBlunders) to (a.whiteMistakes + a.blackMistakes)
    }
    if (blunders > 0) Badge("?? $blunders", QualityColors.Blunder)
    if (mistakes > 0) Badge("? $mistakes", QualityColors.Mistake)
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 8.dp),
    )
}
