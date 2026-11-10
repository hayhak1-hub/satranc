package com.thunderbascraft.satranc.data.history

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** How a history game was played. */
enum class RecordMode { VS_COMPUTER, TWO_PLAYERS, IMPORTED }

/** Why a game ended; UNFINISHED while it is still in progress. */
enum class EndReason {
    CHECKMATE, RESIGNATION, TIMEOUT, STALEMATE, FIFTY_MOVES, REPETITION, INSUFFICIENT_MATERIAL, AGREEMENT,
    UNFINISHED,
    /** Imported games: the PGN gives a result but not always a reason. */
    UNKNOWN,
}

/** Counts from a post-game analysis, kept so the history list can show badges. */
data class AnalysisSummary(
    val whiteInaccuracies: Int,
    val whiteMistakes: Int,
    val whiteBlunders: Int,
    val whiteAverageLoss: Int,
    val blackInaccuracies: Int,
    val blackMistakes: Int,
    val blackBlunders: Int,
    val blackAverageLoss: Int,
)

/** One game in the history. [pgn] is the full PGN text including tags. */
@Entity(
    tableName = "games",
    indices = [Index("finishedAt"), Index("startedAt"), Index("pgnHash")],
)
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds. */
    val startedAt: Long,
    /** Epoch milliseconds; null while unfinished. */
    val finishedAt: Long?,
    val mode: RecordMode,
    /** "SF11" / "SF19" for games against the computer, else null. */
    val engine: String?,
    /** Level number 1..6 for games against the computer, else null. */
    val level: Int?,
    /** "WHITE" / "BLACK" for games against the computer, else null. */
    val humanSide: String?,
    /** PGN result: "1-0", "0-1", "1/2-1/2" or "*". */
    val result: String,
    val reason: EndReason,
    /** Number of plies (half moves). */
    val moveCount: Int,
    val pgn: String,
    /** SHA-256 of [pgn], used to skip duplicates on import. */
    val pgnHash: String,
    /** The first moves for display, e.g. "1. e4 e5 2. Nf3 Nc6". */
    val opening: String,
    /** Player names from the PGN (shown for imported games). */
    val white: String?,
    val black: String?,
    @Embedded(prefix = "analysis_") val analysis: AnalysisSummary? = null,
) {
    val isFinished: Boolean get() = reason != EndReason.UNFINISHED
}
