package com.thunderbascraft.satranc.engine

/**
 * The six difficulty levels. Stockfish is weakened through its "Skill Level"
 * option together with a search depth and time limit per move.
 */
enum class Level(val skill: Int, val depth: Int, val moveTimeMs: Long) {
    NOVICE(0, 1, 50),
    BEGINNER(3, 2, 100),
    CASUAL(6, 4, 200),
    CLUB(10, 8, 400),
    EXPERT(15, 14, 700),
    MASTER(20, 30, 1200);

    val number: Int get() = ordinal + 1
}

interface ChessEngine {
    /**
     * Returns the engine's chosen move in UCI notation for the position reached from
     * [startFen] after [moves] (UCI), or null if there is no legal move.
     */
    suspend fun bestMove(startFen: String, moves: List<String>, level: Level): String?

    fun close()
}
