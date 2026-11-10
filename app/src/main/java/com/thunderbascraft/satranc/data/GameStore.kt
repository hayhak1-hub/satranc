package com.thunderbascraft.satranc.data

import android.content.Context
import androidx.core.content.edit
import com.thunderbascraft.satranc.chess.Position
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.engine.Level

enum class Mode { VS_COMPUTER, TWO_PLAYERS }

data class SavedGame(
    val mode: Mode,
    val level: Level,
    val humanSide: Side,
    val startFen: String,
    val moves: List<String>,
    val flipped: Boolean,
    val finished: Boolean,
    /** The history record of this game, once it has been stored. */
    val recordId: Long? = null,
    /** When the game started (epoch ms). */
    val startedAt: Long = System.currentTimeMillis(),
)

/** Keeps the current game so that it can be continued after the app is closed. */
class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)

    fun save(g: SavedGame) {
        prefs.edit {
            putString("mode", g.mode.name)
            putString("level", g.level.name)
            putString("side", g.humanSide.name)
            putString("fen", g.startFen)
            putString("moves", g.moves.joinToString(" "))
            putBoolean("flipped", g.flipped)
            putBoolean("finished", g.finished)
            putLong("recordId", g.recordId ?: -1L)
            putLong("startedAt", g.startedAt)
        }
    }

    fun load(): SavedGame? {
        val mode = prefs.getString("mode", null) ?: return null
        return runCatching {
            SavedGame(
                mode = Mode.valueOf(mode),
                level = Level.valueOf(prefs.getString("level", Level.CASUAL.name)!!),
                humanSide = Side.valueOf(prefs.getString("side", Side.WHITE.name)!!),
                startFen = prefs.getString("fen", Position.START_FEN)!!,
                moves = prefs.getString("moves", "")!!.split(" ").filter { it.isNotBlank() },
                flipped = prefs.getBoolean("flipped", false),
                finished = prefs.getBoolean("finished", false),
                recordId = prefs.getLong("recordId", -1L).takeIf { it > 0 },
                startedAt = prefs.getLong("startedAt", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    var lastLevel: Level
        get() = runCatching { Level.valueOf(prefs.getString("lastLevel", Level.CASUAL.name)!!) }.getOrDefault(Level.CASUAL)
        set(v) { prefs.edit { putString("lastLevel", v.name) } }
}
