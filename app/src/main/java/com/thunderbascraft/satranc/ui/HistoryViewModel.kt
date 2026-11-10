package com.thunderbascraft.satranc.ui

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderbascraft.satranc.data.history.AppDatabase
import com.thunderbascraft.satranc.data.history.GameRecord
import com.thunderbascraft.satranc.data.history.HistoryRepository
import com.thunderbascraft.satranc.data.history.ImportSummary
import com.thunderbascraft.satranc.data.history.RecordMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryFilter { ALL, VS_COMPUTER, TWO_PLAYERS, IMPORTED }

/** The player's view of a game result. */
enum class Outcome { WIN, LOSS, DRAW, UNFINISHED, WHITE_WON, BLACK_WON }

fun outcomeOf(r: GameRecord): Outcome {
    if (r.result == "*") return Outcome.UNFINISHED
    if (r.result == "1/2-1/2") return Outcome.DRAW
    val whiteWon = r.result == "1-0"
    return if (r.mode == RecordMode.VS_COMPUTER && r.humanSide != null) {
        if ((r.humanSide == "WHITE") == whiteWon) Outcome.WIN else Outcome.LOSS
    } else if (whiteWon) Outcome.WHITE_WON else Outcome.BLACK_WON
}

data class HistoryStats(val games: Int, val wins: Int, val draws: Int, val losses: Int)

fun statsOf(records: List<GameRecord>): HistoryStats {
    val vs = records.filter { it.mode == RecordMode.VS_COMPUTER }.map(::outcomeOf)
    return HistoryStats(records.size, vs.count { it == Outcome.WIN }, vs.count { it == Outcome.DRAW }, vs.count { it == Outcome.LOSS })
}

data class HistoryUiState(
    val all: List<GameRecord> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val selection: Set<Long> = emptySet(),
    val loaded: Boolean = false,
) {
    val shown: List<GameRecord>
        get() = when (filter) {
            HistoryFilter.ALL -> all
            HistoryFilter.VS_COMPUTER -> all.filter { it.mode == RecordMode.VS_COMPUTER }
            HistoryFilter.TWO_PLAYERS -> all.filter { it.mode == RecordMode.TWO_PLAYERS }
            HistoryFilter.IMPORTED -> all.filter { it.mode == RecordMode.IMPORTED }
        }
    val stats: HistoryStats get() = statsOf(all)
    val selecting: Boolean get() = selection.isNotEmpty()
}

/** One-off messages for a snackbar. */
sealed interface HistoryMessage {
    data class Deleted(val records: List<GameRecord>) : HistoryMessage
    data class Imported(val summary: ImportSummary) : HistoryMessage
    data object ReadFailed : HistoryMessage
}

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    @VisibleForTesting
    internal var repo = HistoryRepository(AppDatabase.get(app).games())

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    /** Created on first use, so tests can replace [repo] first. */
    val state: StateFlow<HistoryUiState> by lazy {
        combine(repo.games, filter, selection) { all, f, sel ->
            HistoryUiState(all, f, sel.filterTo(HashSet()) { id -> all.any { it.id == id } }, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
    }

    private val _message = MutableStateFlow<HistoryMessage?>(null)
    val message: StateFlow<HistoryMessage?> = _message.asStateFlow()

    fun setFilter(f: HistoryFilter) { filter.value = f }

    fun toggleSelect(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAllShown() { selection.value = state.value.shown.mapTo(HashSet()) { it.id } }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) { repo.records(ids.toList()).also { repo.delete(ids.toList()) } }
            selection.value = emptySet()
            _message.value = HistoryMessage.Deleted(records)
        }
    }

    fun undoDelete(records: List<GameRecord>) {
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.restore(records) } }
    }

    fun deleteAll() {
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.deleteAll() } }
        selection.value = emptySet()
    }

    /** PGN of the given games (all games when [ids] is null), as one multi-game file. */
    suspend fun export(ids: Collection<Long>?): String = withContext(Dispatchers.IO) { repo.export(ids?.toList()) }

    fun importText(text: String) {
        viewModelScope.launch {
            val summary = withContext(Dispatchers.Default) { repo.importPgn(text) }
            _message.value = HistoryMessage.Imported(summary)
        }
    }

    fun readFailed() { _message.value = HistoryMessage.ReadFailed }

    fun consumeMessage() { _message.value = null }
}
