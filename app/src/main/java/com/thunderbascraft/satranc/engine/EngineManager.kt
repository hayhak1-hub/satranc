package com.thunderbascraft.satranc.engine

import android.content.Context
import androidx.core.content.edit
import java.io.File

enum class EngineKind(val displayName: String) {
    SF11("Stockfish 11"),
    SF19("Stockfish 19"),
}

/**
 * Knows which engine the player chose and whether the Stockfish 19 network is available.
 * Both engine executables are shipped in the APK (nativeLibraryDir); only the network
 * file for Stockfish 19 is downloaded.
 */
class EngineManager(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val libDir: String? = context.applicationInfo.nativeLibraryDir
    val netFile = File(File(context.filesDir, "nets"), NetFile.NAME)

    var selected: EngineKind
        get() = runCatching { EngineKind.valueOf(prefs.getString("engine", EngineKind.SF11.name)!!) }
            .getOrDefault(EngineKind.SF11)
        set(v) { prefs.edit { putString("engine", v.name) } }

    private fun stamp(): String = "${netFile.length()}:${netFile.lastModified()}"

    /** True when the network file exists and was verified (SHA-256) after its download. */
    val isNetReady: Boolean
        get() = netFile.isFile && netFile.length() == NetFile.SIZE && prefs.getString("netStamp", null) == stamp()

    /** Records that [netFile] passed the SHA-256 check, so it need not be hashed at every start. */
    fun markNetVerified() { prefs.edit { putString("netStamp", stamp()) } }

    fun deleteNet() {
        netFile.delete()
        prefs.edit { remove("netStamp") }
    }

    /** The engine actually used: Stockfish 19 only when chosen and its network is present. */
    val active: EngineKind
        get() = if (selected == EngineKind.SF19 && isNetReady && executable(EngineKind.SF19).isFile) EngineKind.SF19
        else EngineKind.SF11

    fun executable(kind: EngineKind): File = File(
        libDir,
        when (kind) {
            EngineKind.SF11 -> "libstockfish.so"
            EngineKind.SF19 -> "libstockfish19.so"
        },
    )

    fun create(kind: EngineKind): ChessEngine = when (kind) {
        EngineKind.SF11 -> StockfishEngine(executable(kind))
        EngineKind.SF19 -> StockfishEngine(executable(kind), evalFile = netFile)
    }
}
