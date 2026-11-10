package com.thunderbascraft.satranc.online

import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Side

enum class OnlineStatus {
    WAITING, ACTIVE, FINISHED;

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: Any?): OnlineStatus? = entries.firstOrNull { it.key == key }
    }
}

/** Why a game ended, as stored in the database. */
object Reason {
    const val CHECKMATE = "checkmate"
    const val STALEMATE = "stalemate"
    const val INSUFFICIENT = "insufficient"
    const val REPETITION = "repetition"
    const val FIFTY = "fifty"
    const val RESIGN = "resign"
    const val AGREEMENT = "agreement"
    const val ABANDONED = "abandoned"
    const val CANCELLED = "cancelled"
}

data class PlayerPresence(val online: Boolean, val since: Long)

/**
 * A game as stored under /games/{code}. The moves are kept as one space-separated UCI string
 * in the database so that the security rules can check that moves are only ever appended.
 */
data class OnlineGame(
    val white: String? = null,
    val black: String? = null,
    val status: OnlineStatus = OnlineStatus.WAITING,
    val moves: List<String> = emptyList(),
    val result: String? = null,
    val reason: String? = null,
    val drawOffer: String? = null,
    val createdAt: Long = 0L,
    val lastMoveAt: Long = 0L,
    val presence: Map<String, PlayerPresence> = emptyMap(),
) {
    fun sideOf(uid: String?): Side? = when {
        uid == null -> null
        uid == white -> Side.WHITE
        uid == black -> Side.BLACK
        else -> null
    }

    fun uidOf(side: Side): String? = if (side == Side.WHITE) white else black

    fun opponentOf(uid: String): String? = when (uid) {
        white -> black
        black -> white
        else -> null
    }

    /** Online games always start from the initial position. */
    val sideToMove: Side get() = if (moves.size % 2 == 0) Side.WHITE else Side.BLACK

    fun isTurnOf(uid: String): Boolean = status == OnlineStatus.ACTIVE && uidOf(sideToMove) == uid

    /**
     * The database representation. [createdAt] and [lastMoveAt] may be passed as a server
     * timestamp placeholder.
     */
    fun toMap(createdAt: Any = this.createdAt, lastMoveAt: Any = this.lastMoveAt): Map<String, Any> = buildMap {
        white?.let { put("white", it) }
        black?.let { put("black", it) }
        put("status", status.key)
        if (moves.isNotEmpty()) put("moves", moves.joinToString(" "))
        put("moveCount", moves.size)
        result?.let { put("result", it) }
        reason?.let { put("reason", it) }
        drawOffer?.let { put("drawOffer", it) }
        put("createdAt", createdAt)
        put("lastMoveAt", lastMoveAt)
        if (presence.isNotEmpty()) {
            put("presence", presence.mapValues { (_, p) -> mapOf("online" to p.online, "since" to p.since) })
        }
    }

    companion object {
        /** Reads a game from the value the database returns; null if it is missing or malformed. */
        fun fromMap(value: Any?): OnlineGame? {
            val m = value as? Map<*, *> ?: return null
            val status = OnlineStatus.fromKey(m["status"]) ?: return null
            val moves = (m["moves"] as? String).orEmpty().split(' ').filter { it.isNotBlank() }
            val presence = (m["presence"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
                val p = v as? Map<*, *> ?: return@mapNotNull null
                (k as? String ?: return@mapNotNull null) to
                    PlayerPresence(p["online"] == true, (p["since"] as? Number)?.toLong() ?: 0L)
            }.toMap()
            return OnlineGame(
                white = m["white"] as? String,
                black = m["black"] as? String,
                status = status,
                moves = moves,
                result = m["result"] as? String,
                reason = m["reason"] as? String,
                drawOffer = m["drawOffer"] as? String,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                lastMoveAt = (m["lastMoveAt"] as? Number)?.toLong() ?: 0L,
                presence = presence,
            )
        }
    }
}

sealed interface JoinResult {
    data class Joined(val game: OnlineGame) : JoinResult
    /** The player is already in this game (e.g. joining their own game again). */
    data object AlreadyIn : JoinResult
    data object Full : JoinResult
    data object Finished : JoinResult
}

/** The game rules of online play, independent of Firebase. Every function returns the new state or null if not allowed. */
object OnlineLogic {
    /** Games that have not changed for this long are treated as gone. */
    const val EXPIRY_MS = 7L * 24 * 60 * 60 * 1000
    /** How long the opponent must be offline before the win can be claimed. */
    const val ABANDON_MS = 3L * 60 * 1000

    const val WHITE_WINS = "1-0"
    const val BLACK_WINS = "0-1"
    const val DRAW = "1/2-1/2"

    fun create(uid: String, side: Side): OnlineGame =
        if (side == Side.WHITE) OnlineGame(white = uid) else OnlineGame(black = uid)

    fun join(game: OnlineGame, uid: String): JoinResult = when {
        game.sideOf(uid) != null -> JoinResult.AlreadyIn
        game.status == OnlineStatus.FINISHED -> JoinResult.Finished
        game.status != OnlineStatus.WAITING -> JoinResult.Full
        game.white == null -> JoinResult.Joined(game.copy(white = uid, status = OnlineStatus.ACTIVE))
        game.black == null -> JoinResult.Joined(game.copy(black = uid, status = OnlineStatus.ACTIVE))
        else -> JoinResult.Full
    }

    fun isExpired(game: OnlineGame, now: Long): Boolean {
        val last = maxOf(game.createdAt, game.lastMoveAt)
        return last > 0 && now - last > EXPIRY_MS
    }

    /** Replays the moves from the start position; null if one of them is illegal. */
    fun replay(moves: List<String>): Game? {
        val g = Game()
        for (m in moves) if (!g.playUci(m)) return null
        return g
    }

    /**
     * Appends [uci] if it is [uid]'s turn, the game still has [expectedCount] moves (so two
     * clients cannot both add a move) and the move is legal. Ends the game on mate or a draw.
     */
    fun applyMove(game: OnlineGame, uid: String, uci: String, expectedCount: Int): OnlineGame? {
        if (game.moves.size != expectedCount || !game.isTurnOf(uid)) return null
        val g = replay(game.moves) ?: return null
        if (!g.playUci(uci)) return null
        val played = g.moves.last().move.uci
        val after = game.copy(moves = game.moves + played, drawOffer = null)
        val result = g.result ?: return after
        val (pgn, reason) = fields(result)
        return after.copy(status = OnlineStatus.FINISHED, result = pgn, reason = reason)
    }

    fun resign(game: OnlineGame, uid: String): OnlineGame? {
        val side = game.sideOf(uid) ?: return null
        if (game.status != OnlineStatus.ACTIVE) return null
        return game.copy(status = OnlineStatus.FINISHED, result = winFor(side.opposite), reason = Reason.RESIGN, drawOffer = null)
    }

    fun offerDraw(game: OnlineGame, uid: String): OnlineGame? {
        if (game.status != OnlineStatus.ACTIVE || game.sideOf(uid) == null || game.drawOffer != null) return null
        return game.copy(drawOffer = uid)
    }

    fun acceptDraw(game: OnlineGame, uid: String): OnlineGame? {
        if (game.status != OnlineStatus.ACTIVE || game.sideOf(uid) == null) return null
        if (game.drawOffer == null || game.drawOffer == uid) return null
        return game.copy(status = OnlineStatus.FINISHED, result = DRAW, reason = Reason.AGREEMENT, drawOffer = null)
    }

    /** Declines the opponent's offer or withdraws one's own. */
    fun clearDrawOffer(game: OnlineGame, uid: String): OnlineGame? {
        if (game.sideOf(uid) == null || game.drawOffer == null) return null
        return game.copy(drawOffer = null)
    }

    /** Ends the game in [uid]'s favour after the opponent left (a draw if they cannot mate). */
    fun claimAbandoned(game: OnlineGame, uid: String, now: Long): OnlineGame? {
        val side = game.sideOf(uid) ?: return null
        if (game.status != OnlineStatus.ACTIVE || !opponentGone(game, uid, now)) return null
        val g = replay(game.moves) ?: return null
        val result = if (Game.hasMatingMaterial(g.position, side)) winFor(side) else DRAW
        return game.copy(status = OnlineStatus.FINISHED, result = result, reason = Reason.ABANDONED, drawOffer = null)
    }

    /** Whether the opponent has been offline for at least [ABANDON_MS]. */
    fun opponentGone(game: OnlineGame, uid: String, now: Long): Boolean {
        val opp = game.opponentOf(uid) ?: return false
        val p = game.presence[opp] ?: return false
        return !p.online && p.since > 0 && now - p.since >= ABANDON_MS
    }

    /** The creator gives up on a game nobody has joined yet. */
    fun cancel(game: OnlineGame, uid: String): OnlineGame? {
        if (game.status != OnlineStatus.WAITING || game.sideOf(uid) == null) return null
        return game.copy(status = OnlineStatus.FINISHED, reason = Reason.CANCELLED)
    }

    fun winFor(side: Side): String = if (side == Side.WHITE) WHITE_WINS else BLACK_WINS

    /** The stored result and reason for a result detected by chess-core. */
    fun fields(result: GameResult): Pair<String, String> = when (result) {
        is GameResult.Checkmate -> winFor(result.winner) to Reason.CHECKMATE
        is GameResult.Resignation -> winFor(result.winner) to Reason.RESIGN
        is GameResult.Timeout -> winFor(result.winner) to Reason.ABANDONED
        GameResult.Stalemate -> DRAW to Reason.STALEMATE
        GameResult.FiftyMoves -> DRAW to Reason.FIFTY
        GameResult.Repetition -> DRAW to Reason.REPETITION
        GameResult.InsufficientMaterial -> DRAW to Reason.INSUFFICIENT
        GameResult.Agreement -> DRAW to Reason.AGREEMENT
    }

    /** The chess-core result of a finished game, for display; null if it is not over (or was cancelled). */
    fun gameResult(game: OnlineGame): GameResult? {
        if (game.status != OnlineStatus.FINISHED) return null
        val winner = when (game.result) {
            WHITE_WINS -> Side.WHITE
            BLACK_WINS -> Side.BLACK
            else -> null
        }
        return when (game.reason) {
            Reason.CHECKMATE -> winner?.let { GameResult.Checkmate(it) }
            Reason.RESIGN -> winner?.let { GameResult.Resignation(it) }
            Reason.ABANDONED -> winner?.let { GameResult.Timeout(it) } ?: GameResult.InsufficientMaterial
            Reason.STALEMATE -> GameResult.Stalemate
            Reason.FIFTY -> GameResult.FiftyMoves
            Reason.REPETITION -> GameResult.Repetition
            Reason.INSUFFICIENT -> GameResult.InsufficientMaterial
            Reason.AGREEMENT -> GameResult.Agreement
            else -> null
        }
    }
}
