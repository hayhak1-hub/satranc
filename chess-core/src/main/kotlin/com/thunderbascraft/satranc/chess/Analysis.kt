package com.thunderbascraft.satranc.chess

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * An engine evaluation of one position, from the point of view of the side to move.
 * [mateIn] > 0: the side to move mates in that many moves; [mateIn] <= 0: it gets mated
 * (0 = it is already checkmated).
 */
data class Evaluation(val scoreCp: Int?, val mateIn: Int?, val bestMoveUci: String?)

enum class MoveQuality(val symbol: String) {
    BEST(""), GOOD(""), INACCURACY("?!"), MISTAKE("?"), BLUNDER("??");

    val isFlagged: Boolean get() = this == INACCURACY || this == MISTAKE || this == BLUNDER
}

data class MoveAnalysis(
    /** 1-based ply number of this move. */
    val ply: Int,
    val mover: Side,
    val san: String,
    val uci: String,
    /** Evaluations before and after the move, from White's point of view. */
    val evalBefore: Int,
    val evalAfter: Int,
    /** How much the move lost for the mover, in centipawns (>= 0). */
    val loss: Int,
    val quality: MoveQuality,
    val bestMoveUci: String?,
    val bestMoveSan: String?,
)

data class SideSummary(val inaccuracies: Int, val mistakes: Int, val blunders: Int, val averageLoss: Int)

data class GameAnalysis(
    /** Evaluation of every analysed position (index = ply), from White's point of view. */
    val evals: List<Int>,
    val moves: List<MoveAnalysis>,
) {
    fun summary(side: Side): SideSummary {
        val own = moves.filter { it.mover == side }
        val avg = if (own.isEmpty()) 0 else own.map { Analysis.acplLoss(it) }.average().roundToInt()
        return SideSummary(
            inaccuracies = own.count { it.quality == MoveQuality.INACCURACY },
            mistakes = own.count { it.quality == MoveQuality.MISTAKE },
            blunders = own.count { it.quality == MoveQuality.BLUNDER },
            averageLoss = avg,
        )
    }
}

/** Turns per-position engine evaluations into move classifications. Pure logic, no engine. */
object Analysis {
    const val MATE_SCORE = 10_000
    const val CP_CLAMP = 1_500

    /**
     * Above this advantage (for the mover, before and after the move) the game is considered
     * decided, so a slower but still winning move, or a move in an already lost position,
     * is not flagged.
     */
    const val DECIDED = 1_000

    const val INACCURACY = 50
    const val MISTAKE = 100
    const val BLUNDER = 300

    /** Converts [eval] (side to move's view) to White's point of view, in centipawns. */
    fun whitePov(eval: Evaluation, sideToMove: Side): Int {
        val mate = eval.mateIn
        val forMover = when {
            mate != null && mate > 0 -> MATE_SCORE - mate
            mate != null -> -(MATE_SCORE - abs(mate))
            else -> (eval.scoreCp ?: 0).coerceIn(-CP_CLAMP, CP_CLAMP)
        }
        return if (sideToMove == Side.WHITE) forMover else -forMover
    }

    fun classify(loss: Int): MoveQuality = when {
        loss >= BLUNDER -> MoveQuality.BLUNDER
        loss >= MISTAKE -> MoveQuality.MISTAKE
        loss >= INACCURACY -> MoveQuality.INACCURACY
        else -> MoveQuality.GOOD
    }

    /** Loss used for the average: both evaluations clamped to ±[CP_CLAMP], mates included. */
    internal fun acplLoss(m: MoveAnalysis): Int {
        val sign = if (m.mover == Side.WHITE) 1 else -1
        val before = (m.evalBefore * sign).coerceIn(-CP_CLAMP, CP_CLAMP)
        val after = (m.evalAfter * sign).coerceIn(-CP_CLAMP, CP_CLAMP)
        return (before - after).coerceAtLeast(0)
    }

    /**
     * Classifies every move whose before- and after-positions have an evaluation.
     * [positions] has one more entry than [moves]; [evals] may be shorter while the
     * analysis is still running.
     */
    fun analyse(positions: List<Position>, moves: List<PlayedMove>, evals: List<Evaluation>): GameAnalysis {
        require(positions.size == moves.size + 1) { "positions must be moves + 1" }
        val white = evals.mapIndexed { i, e -> whitePov(e, positions[i].sideToMove) }
        val analysed = ArrayList<MoveAnalysis>()
        for (i in moves.indices) {
            if (i + 1 >= white.size) break
            val pos = positions[i]
            val mover = pos.sideToMove
            val sign = if (mover == Side.WHITE) 1 else -1
            val before = white[i] * sign
            val after = white[i + 1] * sign
            val best = evals[i].bestMoveUci
            val played = moves[i]
            val isBest = best != null && best == played.move.uci
            val decided = (before >= DECIDED && after >= DECIDED) || (before <= -DECIDED && after <= -DECIDED)
            val loss = (before - after).coerceAtLeast(0)
            val quality = when {
                isBest -> MoveQuality.BEST
                decided -> MoveQuality.GOOD
                else -> classify(loss)
            }
            analysed.add(
                MoveAnalysis(
                    ply = i + 1,
                    mover = mover,
                    san = played.san,
                    uci = played.move.uci,
                    evalBefore = white[i],
                    evalAfter = white[i + 1],
                    loss = loss,
                    quality = quality,
                    bestMoveUci = best,
                    bestMoveSan = best?.let { sanOrNull(pos, it) },
                )
            )
        }
        return GameAnalysis(white, analysed)
    }

    private fun sanOrNull(pos: Position, uci: String): String? {
        val move = runCatching { Move.fromUci(uci) }.getOrNull() ?: return null
        val legal = pos.legalMoves()
        return if (move in legal) San.of(pos, move, legal) else null
    }

    /** Evaluation of a position without legal moves: checkmate or stalemate. */
    fun terminalEvaluation(pos: Position): Evaluation? = when {
        pos.legalMoves().isNotEmpty() -> null
        pos.isInCheck() -> Evaluation(null, 0, null)
        else -> Evaluation(0, null, null)
    }
}

/** Parses the output of a UCI engine search. */
object UciParser {

    /** Returns (cp, mate) from an `info ... score ...` line, or null if it has no exact score. */
    fun parseScore(line: String): Pair<Int?, Int?>? {
        val t = line.trim().split(Regex("\\s+"))
        if (t.firstOrNull() != "info") return null
        val i = t.indexOf("score")
        if (i < 0 || i + 2 >= t.size) return null
        if (t.getOrNull(i + 3) == "lowerbound" || t.getOrNull(i + 3) == "upperbound") return null
        val value = t[i + 2].toIntOrNull() ?: return null
        return when (t[i + 1]) {
            "cp" -> value to null
            "mate" -> null to value
            else -> null
        }
    }

    /** The move after `bestmove`, or null for `bestmove (none)` / anything else. */
    fun parseBestMove(line: String): String? {
        val t = line.trim().split(Regex("\\s+"))
        if (t.firstOrNull() != "bestmove") return null
        return t.getOrNull(1)?.takeIf { it != "(none)" }
    }

    /** Builds an [Evaluation] from all output lines of one search, up to and including `bestmove`. */
    fun parseSearch(lines: List<String>): Evaluation {
        val score = lines.mapNotNull(::parseScore).lastOrNull()
        val best = lines.lastOrNull { it.startsWith("bestmove") }?.let(::parseBestMove)
        return Evaluation(score?.first, score?.second, best)
    }
}
