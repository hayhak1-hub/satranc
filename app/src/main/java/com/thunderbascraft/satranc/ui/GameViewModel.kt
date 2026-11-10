package com.thunderbascraft.satranc.ui

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderbascraft.satranc.chess.Analysis
import com.thunderbascraft.satranc.chess.Evaluation
import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.GameAnalysis
import com.thunderbascraft.satranc.chess.PlayedMove
import java.io.IOException
import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Move
import com.thunderbascraft.satranc.chess.Position
import com.thunderbascraft.satranc.chess.PieceType
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.data.GameStore
import com.thunderbascraft.satranc.data.Mode
import com.thunderbascraft.satranc.data.SavedGame
import com.thunderbascraft.satranc.engine.ChessEngine
import com.thunderbascraft.satranc.engine.Level
import com.thunderbascraft.satranc.engine.DownloadResult
import com.thunderbascraft.satranc.engine.EngineKind
import com.thunderbascraft.satranc.engine.EngineManager
import com.thunderbascraft.satranc.engine.NetDownloader
import com.thunderbascraft.satranc.engine.NetFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class GameUiState(
    val position: Position = Position.start(),
    val lastMove: Move? = null,
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val sanMoves: List<String> = emptyList(),
    val result: GameResult? = null,
    val flipped: Boolean = false,
    val mode: Mode = Mode.TWO_PLAYERS,
    val level: Level = Level.CASUAL,
    val humanSide: Side = Side.WHITE,
    val thinking: Boolean = false,
    val pendingPromotion: Pair<Int, Int>? = null,
    val canUndo: Boolean = false,
    val engineError: Boolean = false,
)

data class AnalysisUiState(
    val positions: List<Position> = listOf(Position.start()),
    val moves: List<PlayedMove> = emptyList(),
    val evaluations: List<Evaluation> = emptyList(),
    val analysis: GameAnalysis? = null,
    val running: Boolean = false,
    val failed: Boolean = false,
    /** The shown position: 0 = start, n = after the n-th move. */
    val selectedPly: Int = 0,
    val engine: EngineKind = EngineKind.SF11,
) {
    val total: Int get() = positions.size
    val done: Int get() = evaluations.size
    val complete: Boolean get() = evaluations.size == positions.size
}

/** State of the Stockfish 19 network download shown in the settings. */
sealed interface NetState {
    data object Missing : NetState
    data class Downloading(val bytes: Long, val total: Long) : NetState
    data class Failed(val reason: String) : NetState
    data object Ready : NetState
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app)
    private val engines = EngineManager(app)

    private val _engineKind = MutableStateFlow(engines.active)
    /** The engine used for play and analysis. */
    val engineKind: StateFlow<EngineKind> = _engineKind.asStateFlow()

    private val _netState = MutableStateFlow<NetState>(if (engines.isNetReady) NetState.Ready else NetState.Missing)
    val netState: StateFlow<NetState> = _netState.asStateFlow()
    private var downloadJob: Job? = null
    @Volatile private var downloadCancelled = false

    /** Replaceable in tests. */
    @VisibleForTesting
    internal var engine: ChessEngine = engines.create(_engineKind.value)

    private var game = Game()
    private var engineJob: Job? = null
    /** Increases with every change of the game, so that stale engine replies are dropped. */
    private var generation = 0

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _analysis = MutableStateFlow(AnalysisUiState())
    val analysis: StateFlow<AnalysisUiState> = _analysis.asStateFlow()
    private var analysisJob: Job? = null

    val savedGame: SavedGame? get() = store.load()?.takeIf { !it.finished && it.moves.isNotEmpty() }
    val lastLevel: Level get() = store.lastLevel

    fun newGame(mode: Mode, level: Level = Level.CASUAL, humanSide: Side = Side.WHITE) {
        cancelEngine()
        game = Game()
        if (mode == Mode.VS_COMPUTER) store.lastLevel = level
        _state.value = GameUiState(
            mode = mode,
            level = level,
            humanSide = humanSide,
            flipped = mode == Mode.VS_COMPUTER && humanSide == Side.BLACK,
        )
        refresh()
        maybeEngineMove()
    }

    // ------------------------------------------------------------ engine choice

    /**
     * Switches to [kind]. Stockfish 19 needs its network; when it is missing this does
     * nothing (the settings screen asks the user to download it first).
     */
    fun selectEngine(kind: EngineKind) {
        if (kind == EngineKind.SF19 && !engines.isNetReady) return
        engines.selected = kind
        applyEngine()
    }

    /** Restarts the engine process if the active engine changed. */
    private fun applyEngine() {
        val kind = engines.active
        if (kind == _engineKind.value) return
        val wasThinking = _state.value.thinking
        cancelEngine()
        analysisJob?.cancel()
        _analysis.update { it.copy(running = false) }
        engine.close()
        engine = engines.create(kind)
        _engineKind.value = kind
        if (wasThinking) _state.update { it.copy(thinking = false) }
        maybeEngineMove()
    }

    /** Downloads and verifies the Stockfish 19 network, then switches to Stockfish 19. */
    fun downloadNet() {
        if (downloadJob?.isActive == true) return
        downloadCancelled = false
        _netState.value = NetState.Downloading(0, NetFile.SIZE)
        downloadJob = viewModelScope.launch {
            var lastReport = 0L
            val result = withContext(Dispatchers.IO) {
                NetDownloader(NetFile.SIZE, NetFile.SHA256).download(
                    NetFile.URLS, engines.netFile,
                    onProgress = { done, total ->
                        if (done - lastReport >= 512 * 1024 || done == total) {
                            lastReport = done
                            _netState.value = NetState.Downloading(done, total)
                        }
                    },
                    isCancelled = { downloadCancelled },
                )
            }
            when (result) {
                DownloadResult.Ok -> {
                    engines.markNetVerified()
                    _netState.value = NetState.Ready
                    selectEngine(EngineKind.SF19)
                }
                DownloadResult.Cancelled -> _netState.value = NetState.Missing
                is DownloadResult.Failed -> _netState.value = NetState.Failed(result.reason)
            }
        }
    }

    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Deletes the downloaded network and falls back to Stockfish 11. */
    fun deleteNet() {
        cancelDownload()
        engines.selected = EngineKind.SF11
        applyEngine()
        engines.deleteNet()
        _netState.value = NetState.Missing
    }

    /** Starts a new game with the same mode, level and colour. */
    fun rematch() {
        val s = _state.value
        newGame(s.mode, s.level, s.humanSide)
    }

    // ------------------------------------------------------------ analysis

    /**
     * Starts (or resumes) the engine analysis of the current game. Evaluations that are
     * already done for the same moves are kept, so leaving and reopening the screen resumes.
     */
    fun startAnalysis() {
        cancelEngine()
        _state.update { it.copy(thinking = false) }
        val positions = game.positionsHistory
        val moves = game.moves
        val current = _analysis.value
        val sameGame = current.engine == _engineKind.value &&
            current.positions.firstOrNull() == positions.first() &&
            current.moves.map { it.move } == moves.map { it.move }
        val kept = if (sameGame) current.evaluations else emptyList()
        _analysis.value = AnalysisUiState(
            positions = positions,
            moves = moves,
            evaluations = kept,
            analysis = Analysis.analyse(positions, moves, kept),
            running = kept.size < positions.size,
            selectedPly = if (sameGame) current.selectedPly.coerceIn(0, moves.size) else moves.size,
            engine = _engineKind.value,
        )
        if (kept.size == positions.size) return
        val fen = game.startPosition.toFen()
        val uci = moves.map { it.move.uci }
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val evals = kept.toMutableList()
            try {
                for (i in kept.size until positions.size) {
                    val eval = Analysis.terminalEvaluation(positions[i])
                        ?: engine.evaluate(fen, uci.subList(0, i), ANALYSIS_TIME_MS)
                    evals.add(eval)
                    val snapshot = evals.toList()
                    _analysis.update {
                        it.copy(evaluations = snapshot, analysis = Analysis.analyse(positions, moves, snapshot))
                    }
                }
                _analysis.update { it.copy(running = false) }
            } catch (e: IOException) {
                _analysis.update { it.copy(running = false, failed = true) }
            }
        }
    }

    /** Stops the analysis (e.g. when the screen is left); finished evaluations are kept. */
    fun stopAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _analysis.update { it.copy(running = false) }
        // startAnalysis() stopped any pending computer move; let the computer continue.
        if (!_state.value.thinking) maybeEngineMove()
    }

    fun selectPly(ply: Int) = _analysis.update { it.copy(selectedPly = ply.coerceIn(0, it.moves.size)) }

    fun continueSaved() {
        val saved = store.load() ?: return newGame(Mode.TWO_PLAYERS)
        cancelEngine()
        game = Game(saved.startFen)
        for (m in saved.moves) if (!game.playUci(m)) break
        _state.value = GameUiState(
            mode = saved.mode, level = saved.level, humanSide = saved.humanSide, flipped = saved.flipped,
        )
        refresh()
        maybeEngineMove()
    }

    private fun isHumanTurn(): Boolean {
        val s = _state.value
        return s.mode == Mode.TWO_PLAYERS || game.position.sideToMove == s.humanSide
    }

    fun onSquareTapped(sq: Int) {
        val s = _state.value
        if (game.isOver || s.thinking || !isHumanTurn() || s.pendingPromotion != null) return
        val sel = s.selected
        if (sel != null && sq in s.targets) {
            tryMove(sel, sq)
            return
        }
        select(sq)
    }

    /** Called when a drag starts on [sq]. Returns true if the piece may be dragged. */
    fun onDragStart(sq: Int): Boolean {
        val s = _state.value
        if (game.isOver || s.thinking || !isHumanTurn() || s.pendingPromotion != null) return false
        select(sq)
        return _state.value.selected == sq
    }

    fun onDrop(sq: Int?) {
        val sel = _state.value.selected ?: return
        if (sq != null && sq in _state.value.targets) tryMove(sel, sq)
        else if (sq != sel) _state.update { it.copy(selected = null, targets = emptySet()) }
    }

    private fun select(sq: Int) {
        val piece = game.position.pieceAt(sq)
        if (piece == null || piece.side != game.position.sideToMove) {
            _state.update { it.copy(selected = null, targets = emptySet()) }
            return
        }
        val targets = game.position.legalMovesFrom(sq).map { it.to }.toSet()
        _state.update { it.copy(selected = sq, targets = targets) }
    }

    private fun tryMove(from: Int, to: Int) {
        val candidates = game.position.legalMovesFrom(from).filter { it.to == to }
        when {
            candidates.isEmpty() -> _state.update { it.copy(selected = null, targets = emptySet()) }
            candidates.any { it.promotion != null } ->
                _state.update { it.copy(pendingPromotion = from to to) }
            else -> playMove(candidates.first())
        }
    }

    fun onPromotionChosen(type: PieceType?) {
        val pending = _state.value.pendingPromotion ?: return
        _state.update { it.copy(pendingPromotion = null) }
        if (type == null) {
            _state.update { it.copy(selected = null, targets = emptySet()) }
            return
        }
        playMove(Move(pending.first, pending.second, type))
    }

    private fun playMove(move: Move) {
        if (!game.play(move)) return
        generation++
        refresh()
        maybeEngineMove()
    }

    private fun maybeEngineMove() {
        val s = _state.value
        if (s.mode != Mode.VS_COMPUTER || game.isOver || game.position.sideToMove == s.humanSide) return
        val gen = generation
        val fen = game.startPosition.toFen()
        val moves = game.moves.map { it.move.uci }
        val randomMove = pickRandomMove(s.level)
        _state.update { it.copy(thinking = true) }
        engineJob = viewModelScope.launch {
            val best = if (randomMove != null) {
                delay(RANDOM_MOVE_DELAY_MS)
                Result.success(randomMove.uci)
            } else {
                runCatching { engine.bestMove(fen, moves, s.level) }
            }
            if (gen != generation) return@launch
            best.onSuccess { uci ->
                _state.update { it.copy(thinking = false) }
                if (uci != null && game.playUci(uci)) {
                    generation++
                    refresh()
                }
            }.onFailure {
                _state.update { it.copy(thinking = false) }
                if (_engineKind.value == EngineKind.SF19) {
                    // Stockfish 19 could not start (e.g. a broken network file): use the built-in engine.
                    engines.selected = EngineKind.SF11
                    applyEngine()
                } else {
                    _state.update { it.copy(engineError = true) }
                }
            }
        }
    }

    /**
     * With probability [Level.randomMoveChance] returns a random legal move, so that the
     * lowest levels make real mistakes. From level 3 up a mate in one is never thrown away.
     */
    private fun pickRandomMove(level: Level): Move? {
        if (level.randomMoveChance <= 0.0 || Random.nextDouble() >= level.randomMoveChance) return null
        val legal = game.position.legalMoves()
        if (legal.isEmpty()) return null
        if (level.number >= 3 && legal.any { givesMate(it) }) return null
        return legal.random()
    }

    private fun givesMate(move: Move): Boolean {
        val next = game.position.play(move)
        return next.isInCheck() && next.legalMoves().isEmpty()
    }

    private fun cancelEngine() {
        generation++
        engineJob?.cancel()
        engineJob = null
    }

    fun undo() {
        val s = _state.value
        if (!game.canUndo()) return
        cancelEngine()
        if (s.mode == Mode.VS_COMPUTER) {
            // Take back the computer's reply and the player's own move.
            game.undo()
            while (game.canUndo() && game.position.sideToMove != s.humanSide) game.undo()
        } else {
            game.undo()
        }
        _state.update { it.copy(thinking = false) }
        refresh()
        maybeEngineMove()
    }

    fun flip() {
        _state.update { it.copy(flipped = !it.flipped) }
        save()
    }

    fun resign() {
        if (game.isOver) return
        cancelEngine()
        val s = _state.value
        val loser = if (s.mode == Mode.VS_COMPUTER) s.humanSide else game.position.sideToMove
        game.resign(loser)
        _state.update { it.copy(thinking = false) }
        refresh()
    }

    fun agreeDraw() {
        if (game.isOver) return
        cancelEngine()
        game.agreeDraw()
        _state.update { it.copy(thinking = false) }
        refresh()
    }

    fun dismissEngineError() = _state.update { it.copy(engineError = false) }

    fun pgn(white: String, black: String): String = game.toPgn(white, black)

    fun fen(): String = game.position.toFen()

    private fun refresh() {
        _state.update {
            it.copy(
                position = game.position,
                lastMove = game.lastMove,
                selected = null,
                targets = emptySet(),
                sanMoves = game.moves.map { m -> m.san },
                result = game.result,
                canUndo = game.canUndo(),
            )
        }
        save()
    }

    private fun save() {
        val s = _state.value
        store.save(
            SavedGame(
                mode = s.mode,
                level = s.level,
                humanSide = s.humanSide,
                startFen = game.startPosition.toFen(),
                moves = game.moves.map { it.move.uci },
                flipped = s.flipped,
                finished = game.isOver,
            )
        )
    }

    override fun onCleared() {
        engine.close()
    }

    private companion object {
        const val RANDOM_MOVE_DELAY_MS = 400L
        const val ANALYSIS_TIME_MS = 300L
    }
}
