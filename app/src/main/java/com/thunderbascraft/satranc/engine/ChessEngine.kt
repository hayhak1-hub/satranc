package com.thunderbascraft.satranc.engine

import com.thunderbascraft.satranc.chess.Evaluation

/**
 * The six difficulty levels. Stockfish is weakened through its "Skill Level"
 * option together with a search depth and time limit per move. Even at Skill
 * Level 0 Stockfish is far too strong for beginners, so the lowest levels also
 * play a random legal move with probability [randomMoveChance].
 */
enum class Level(val skill: Int, val depth: Int, val moveTimeMs: Long, val randomMoveChance: Double) {
    NOVICE(0, 1, 50, 0.55),
    BEGINNER(0, 1, 50, 0.30),
    CASUAL(2, 2, 100, 0.12),
    CLUB(8, 6, 300, 0.0),
    EXPERT(14, 12, 600, 0.0),
    MASTER(20, 30, 1200, 0.0);

    val number: Int get() = ordinal + 1
}

interface ChessEngine {
    /**
     * Returns the engine's chosen move in UCI notation for the position reached from
     * [startFen] after [moves] (UCI), or null if there is no legal move.
     */
    suspend fun bestMove(startFen: String, moves: List<String>, level: Level): String?

    /**
     * Evaluates the position reached from [startFen] after [moves] at full strength,
     * searching for [moveTimeMs]. The score is from the side to move's point of view.
     */
    suspend fun evaluate(startFen: String, moves: List<String>, moveTimeMs: Long): Evaluation

    fun close()
}
