package com.thunderbascraft.satranc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderbascraft.satranc.chess.Game
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
import com.thunderbascraft.satranc.engine.StockfishEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = GameStore(app)
    private val engine: ChessEngine = StockfishEngine(app)

    private var game = Game()
    private var engineJob: Job? = null
    /** Increases with every change of the game, so that stale engine replies are dropped. */
    private var generation = 0

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

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
        _state.update { it.copy(thinking = true) }
        engineJob = viewModelScope.launch {
            val best = runCatching { engine.bestMove(fen, moves, s.level) }
            if (gen != generation) return@launch
            best.onSuccess { uci ->
                _state.update { it.copy(thinking = false) }
                if (uci != null && game.playUci(uci)) {
                    generation++
                    refresh()
                }
            }.onFailure {
                _state.update { it.copy(thinking = false, engineError = true) }
            }
        }
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
}
