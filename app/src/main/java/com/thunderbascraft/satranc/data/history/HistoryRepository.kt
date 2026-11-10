package com.thunderbascraft.satranc.data.history

import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.GameAnalysis
import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Pgn
import com.thunderbascraft.satranc.chess.PgnGame
import com.thunderbascraft.satranc.chess.Side
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything needed to store the current game. */
data class GameSnapshot(
    val recordId: Long?,
    val startedAt: Long,
    val game: Game,
    val mode: RecordMode,
    val engine: String?,
    val level: Int?,
    val humanSide: Side?,
    /** Display names for the PGN, e.g. "You" / "Computer (Level 3, Stockfish 11)". */
    val whiteName: String,
    val blackName: String,
)

data class ImportSummary(val imported: Int, val skipped: Int)

/** Pure helpers that turn games into history records (unit-testable without a database). */
object HistoryRecords {
    /** Unfinished games need at least this many plies to be kept. */
    const val MIN_UNFINISHED_PLIES = 2

    fun shouldSave(game: Game): Boolean =
        if (game.isOver) game.moves.isNotEmpty() else game.moves.size >= MIN_UNFINISHED_PLIES

    fun reasonOf(result: GameResult?): EndReason = when (result) {
        null -> EndReason.UNFINISHED
        is GameResult.Checkmate -> EndReason.CHECKMATE
        is GameResult.Resignation -> EndReason.RESIGNATION
        is GameResult.Timeout -> EndReason.TIMEOUT
        GameResult.Stalemate -> EndReason.STALEMATE
        GameResult.FiftyMoves -> EndReason.FIFTY_MOVES
        GameResult.Repetition -> EndReason.REPETITION
        GameResult.InsufficientMaterial -> EndReason.INSUFFICIENT_MATERIAL
        GameResult.Agreement -> EndReason.AGREEMENT
    }

    private fun termination(reason: EndReason): String = when (reason) {
        EndReason.UNFINISHED -> "unterminated"
        EndReason.TIMEOUT -> "time forfeit"
        else -> "normal"
    }

    private val PGN_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    fun pgnDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(PGN_DATE)

    /** "1. e4 e5 2. Nf3 Nc6 3. Bb5" (first [fullMoves] moves). */
    fun opening(game: Game, fullMoves: Int = 3): String {
        val sb = StringBuilder()
        var no = game.startPosition.fullmoveNumber
        var side = game.startPosition.sideToMove
        for ((i, pm) in game.moves.withIndex()) {
            if (no >= game.startPosition.fullmoveNumber + fullMoves) break
            if (side == Side.WHITE) sb.append(if (sb.isEmpty()) "" else " ").append("$no. ")
            else if (i == 0) sb.append("$no... ") else sb.append(' ')
            sb.append(pm.san)
            if (side == Side.BLACK) no++
            side = side.opposite
        }
        return sb.toString()
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    fun fromSnapshot(s: GameSnapshot, now: Long): GameRecord {
        val reason = reasonOf(s.game.result)
        val tags = linkedMapOf(
            "Event" to "ThunderChess",
            "Date" to pgnDate(s.startedAt),
            "White" to s.whiteName,
            "Black" to s.blackName,
            "Termination" to termination(reason),
            "PlyCount" to s.game.moves.size.toString(),
        )
        val pgn = Pgn.write(s.game, tags)
        return GameRecord(
            id = s.recordId ?: 0,
            startedAt = s.startedAt,
            finishedAt = if (reason == EndReason.UNFINISHED) null else now,
            mode = s.mode,
            engine = s.engine,
            level = s.level,
            humanSide = s.humanSide?.name,
            result = s.game.result?.pgn ?: "*",
            reason = reason,
            moveCount = s.game.moves.size,
            pgn = pgn,
            pgnHash = sha256(pgn),
            opening = opening(s.game),
            white = s.whiteName,
            black = s.blackName,
        )
    }

    /** Normalised PGN of an imported game (so that the same game always has the same hash). */
    fun importedPgn(g: PgnGame): String = Pgn.write(g.game, g.tags)

    fun fromImported(g: PgnGame, now: Long): GameRecord {
        val pgn = importedPgn(g)
        val reason = g.game.result?.let(::reasonOf) ?: if (g.result == "*") EndReason.UNFINISHED else EndReason.UNKNOWN
        return GameRecord(
            startedAt = now,
            finishedAt = now,
            mode = RecordMode.IMPORTED,
            engine = null, level = null, humanSide = null,
            result = g.result,
            reason = if (reason == EndReason.UNFINISHED) EndReason.UNKNOWN else reason,
            moveCount = g.game.moves.size,
            pgn = pgn,
            pgnHash = sha256(pgn),
            opening = opening(g.game),
            white = g.white,
            black = g.black,
        )
    }

    fun summaryOf(a: GameAnalysis): AnalysisSummary {
        val w = a.summary(Side.WHITE)
        val b = a.summary(Side.BLACK)
        return AnalysisSummary(w.inaccuracies, w.mistakes, w.blunders, w.averageLoss, b.inaccuracies, b.mistakes, b.blunders, b.averageLoss)
    }

    /** One multi-game PGN file. */
    fun exportAll(records: List<GameRecord>): String =
        records.joinToString("\n") { it.pgn.trimEnd() + "\n" }
}

class HistoryRepository(private val dao: GameDao, private val clock: () -> Long = System::currentTimeMillis) {

    val games: Flow<List<GameRecord>> = dao.observeAll()

    /**
     * Inserts or updates the record of the current game. Returns the record id, or the given
     * id unchanged when the game is too short to keep.
     */
    suspend fun save(s: GameSnapshot): Long? {
        if (!HistoryRecords.shouldSave(s.game)) return s.recordId
        val old = s.recordId?.let { dao.get(it) }
        val record = HistoryRecords.fromSnapshot(s.copy(recordId = old?.id), clock())
        return if (old == null) {
            dao.insert(record.copy(id = 0))
        } else {
            // Keep the finish time of a game that was already finished, and any analysis.
            dao.update(record.copy(finishedAt = record.finishedAt?.let { old.finishedAt ?: it }, analysis = old.analysis))
            old.id
        }
    }

    suspend fun get(id: Long): GameRecord? = dao.get(id)

    /** Adds one imported game unless the very same game is already stored. Returns its id or null. */
    suspend fun addImported(g: PgnGame): Long? {
        val record = HistoryRecords.fromImported(g, clock())
        if (dao.countByHash(record.pgnHash) > 0) return null
        return dao.insert(record)
    }

    /** Adds an imported game, or returns the id of the identical game already stored. */
    suspend fun addImportedOrFind(g: PgnGame): Long {
        val record = HistoryRecords.fromImported(g, clock())
        return dao.idByHash(record.pgnHash) ?: dao.insert(record)
    }

    /** Imports every readable game of a PGN file, skipping unreadable games and duplicates. */
    suspend fun importPgn(text: String): ImportSummary {
        var imported = 0
        var skipped = 0
        for (raw in Pgn.splitGames(text)) {
            val g = runCatching { Pgn.parseGame(raw) }.getOrNull()
            if (g == null || g.game.moves.isEmpty() || addImported(g) == null) skipped++ else imported++
        }
        return ImportSummary(imported, skipped)
    }

    suspend fun export(ids: List<Long>? = null): String =
        HistoryRecords.exportAll(if (ids == null) dao.all() else dao.getAll(ids))

    suspend fun records(ids: List<Long>): List<GameRecord> = dao.getAll(ids)

    suspend fun delete(ids: List<Long>) = dao.delete(ids)

    /** Puts deleted records back (undo). */
    suspend fun restore(records: List<GameRecord>) { dao.insertAll(records) }

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun setAnalysis(id: Long, a: GameAnalysis) {
        val s = HistoryRecords.summaryOf(a)
        dao.setAnalysis(id, s.whiteInaccuracies, s.whiteMistakes, s.whiteBlunders, s.whiteAverageLoss,
            s.blackInaccuracies, s.blackMistakes, s.blackBlunders, s.blackAverageLoss)
    }
}
