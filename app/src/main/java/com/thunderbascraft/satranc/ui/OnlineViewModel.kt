package com.thunderbascraft.satranc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderbascraft.satranc.chess.Game
import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Move
import com.thunderbascraft.satranc.chess.PieceType
import com.thunderbascraft.satranc.chess.Position
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.online.GameCode
import com.thunderbascraft.satranc.online.JoinResult
import com.thunderbascraft.satranc.online.OnlineGame
import com.thunderbascraft.satranc.online.OnlineLogic
import com.thunderbascraft.satranc.online.OnlineRepository
import com.thunderbascraft.satranc.online.OnlineStatus
import com.thunderbascraft.satranc.online.OnlineStore
import com.thunderbascraft.satranc.online.Reason
import com.thunderbascraft.satranc.online.SavedOnlineGame
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnlineError { SIGN_IN, INVALID_CODE, NOT_FOUND, FULL, FINISHED, EXPIRED, NETWORK }

data class OnlineUiState(
    /** False when the app was built without a Firebase configuration. */
    val configured: Boolean = false,
    val busy: Boolean = false,
    val error: OnlineError? = null,
    val errorDetail: String? = null,
    /** The game being shown; null in the lobby. */
    val code: String? = null,
    val mySide: Side = Side.WHITE,
    val game: OnlineGame? = null,
    val position: Position = Position.start(),
    val lastMove: Move? = null,
    val sanMoves: List<String> = emptyList(),
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val pendingPromotion: Pair<Int, Int>? = null,
    val sending: Boolean = false,
    val connected: Boolean = true,
    val opponentOnline: Boolean = false,
    val canClaimWin: Boolean = false,
    /** A game of this device that can be continued. */
    val resumable: SavedOnlineGame? = null,
) {
    val status: OnlineStatus? get() = game?.status
    val myTurn: Boolean get() = game?.status == OnlineStatus.ACTIVE && position.sideToMove == mySide
    val result: GameResult? get() = game?.let(OnlineLogic::gameResult)
    val cancelled: Boolean get() = game?.reason == Reason.CANCELLED
}

class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    private val configured = OnlineRepository.isConfigured(app)
    private val repo by lazy { OnlineRepository() }
    private val store = OnlineStore(app)

    private val _state = MutableStateFlow(OnlineUiState(configured = configured, resumable = store.load()))
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    private var uid: String? = null
    private var chess = Game()
    private var serverOffset = 0L
    private var gameJobs = mutableListOf<Job>()

    private fun now() = System.currentTimeMillis() + serverOffset

    /** The saved game code, for the home screen. */
    fun refreshResumable() = _state.update { it.copy(resumable = store.load()) }

    fun clearError() = _state.update { it.copy(error = null, errorDetail = null) }

    private fun fail(error: OnlineError, t: Throwable? = null) =
        _state.update { it.copy(busy = false, error = error, errorDetail = t?.message) }

    private suspend fun signedIn(): String? = uid ?: try {
        repo.signIn().also { uid = it }
    } catch (t: Throwable) {
        fail(OnlineError.SIGN_IN, t); null
    }

    private fun busy(block: suspend () -> Unit) {
        if (!configured || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                fail(OnlineError.NETWORK, t)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    // ------------------------------------------------------------ lobby

    fun create(choice: SideChoice) = busy {
        val me = signedIn() ?: return@busy
        val side = when (choice) {
            SideChoice.WHITE -> Side.WHITE
            SideChoice.BLACK -> Side.BLACK
            SideChoice.RANDOM -> if (Math.random() < 0.5) Side.WHITE else Side.BLACK
        }
        val code = repo.create(me, side)
        open(code, side)
    }

    fun join(input: String) {
        val code = GameCode.normalize(input)
        if (!GameCode.isValid(code)) {
            fail(OnlineError.INVALID_CODE); return
        }
        busy {
            val me = signedIn() ?: return@busy
            val existing = repo.load(code)
            when {
                existing == null -> { fail(OnlineError.NOT_FOUND); return@busy }
                OnlineLogic.isExpired(existing, now()) && existing.sideOf(me) == null -> {
                    fail(OnlineError.EXPIRED); return@busy
                }
            }
            when (val r = repo.join(code, me)) {
                is JoinResult.Joined -> open(code, checkNotNull(r.game.sideOf(me)))
                JoinResult.AlreadyIn -> open(code, checkNotNull(repo.load(code)?.sideOf(me)))
                JoinResult.Full -> fail(OnlineError.FULL)
                JoinResult.Finished -> fail(OnlineError.FINISHED)
                null -> fail(OnlineError.NOT_FOUND)
            }
        }
    }

    /** Continues the saved game after a restart. */
    fun resume() {
        val saved = store.load() ?: return
        busy {
            val me = signedIn() ?: return@busy
            val g = repo.load(saved.code)
            if (g == null || g.sideOf(me) == null || g.status == OnlineStatus.FINISHED || OnlineLogic.isExpired(g, now())) {
                store.clear()
                _state.update { it.copy(resumable = null) }
                fail(if (g == null || g.sideOf(me) == null) OnlineError.NOT_FOUND
                     else if (g.status == OnlineStatus.FINISHED) OnlineError.FINISHED else OnlineError.EXPIRED)
                return@busy
            }
            open(saved.code, checkNotNull(g.sideOf(me)))
        }
    }

    private fun open(code: String, side: Side) {
        val me = checkNotNull(uid)
        store.save(SavedOnlineGame(code, side))
        chess = Game()
        _state.update {
            OnlineUiState(configured = true, code = code, mySide = side, resumable = store.load())
        }
        stopGameJobs()
        gameJobs += viewModelScope.launch {
            repo.observe(code)
                .catch { fail(OnlineError.NETWORK, it) }
                .collect { g -> if (g == null) fail(OnlineError.NOT_FOUND) else onGame(g) }
        }
        gameJobs += viewModelScope.launch {
            repo.connected().catch { }.collectLatest { connected ->
                _state.update { it.copy(connected = connected) }
                if (connected) runCatching { repo.goOnline(code, me) }
            }
        }
        gameJobs += viewModelScope.launch {
            repo.serverTimeOffset().catch { }.collect { serverOffset = it }
        }
        gameJobs += viewModelScope.launch {
            // Re-evaluates whether the opponent has been gone long enough.
            while (true) {
                delay(5_000)
                _state.value.game?.let(::updateFromGame)
            }
        }
    }

    private fun stopGameJobs() {
        gameJobs.forEach { it.cancel() }
        gameJobs.clear()
    }

    /** Back to the lobby. An unfinished game stays saved and can be resumed. */
    fun leave() {
        val code = _state.value.code
        val me = uid
        stopGameJobs()
        if (code != null && me != null) viewModelScope.launch { runCatching { repo.goOffline(code, me) } }
        if (_state.value.game?.status == OnlineStatus.FINISHED) store.clear()
        _state.value = OnlineUiState(configured = configured, resumable = store.load())
    }

    /** Gives up on a game nobody has joined yet. */
    fun cancelWaiting() {
        val code = _state.value.code ?: return
        val me = uid ?: return
        viewModelScope.launch {
            runCatching { repo.update(code) { OnlineLogic.cancel(it, me) } }
            store.clear()
            leave()
        }
    }

    // ------------------------------------------------------------ game updates

    private fun onGame(g: OnlineGame) {
        val replayed = OnlineLogic.replay(g.moves) ?: return
        chess = replayed
        if (g.status == OnlineStatus.FINISHED) store.clear()
        updateFromGame(g)
    }

    private fun updateFromGame(g: OnlineGame) {
        val me = uid ?: return
        val opp = g.opponentOf(me)
        val sameMoves = _state.value.game?.moves == g.moves
        _state.update { s ->
            s.copy(
                game = g,
                position = chess.position,
                lastMove = chess.lastMove,
                sanMoves = chess.moves.map { it.san },
                selected = if (sameMoves) s.selected else null,
                targets = if (sameMoves) s.targets else emptySet(),
                pendingPromotion = if (sameMoves) s.pendingPromotion else null,
                opponentOnline = opp != null && g.presence[opp]?.online == true,
                canClaimWin = OnlineLogic.opponentGone(g, me, now()) && g.status == OnlineStatus.ACTIVE,
            )
        }
    }

    // ------------------------------------------------------------ board input

    private fun canMove(): Boolean {
        val s = _state.value
        return s.myTurn && !s.sending && s.pendingPromotion == null && !chess.isOver
    }

    fun onSquareTapped(sq: Int) {
        if (!canMove()) return
        val s = _state.value
        val sel = s.selected
        if (sel != null && sq in s.targets) {
            tryMove(sel, sq); return
        }
        select(sq)
    }

    fun onDragStart(sq: Int): Boolean {
        if (!canMove()) return false
        select(sq)
        return _state.value.selected == sq
    }

    fun onDrop(sq: Int?) {
        val sel = _state.value.selected ?: return
        if (sq != null && sq in _state.value.targets) tryMove(sel, sq)
        else if (sq != sel) _state.update { it.copy(selected = null, targets = emptySet()) }
    }

    private fun select(sq: Int) {
        val piece = chess.position.pieceAt(sq)
        if (piece == null || piece.side != _state.value.mySide) {
            _state.update { it.copy(selected = null, targets = emptySet()) }
            return
        }
        _state.update { it.copy(selected = sq, targets = chess.position.legalMovesFrom(sq).map { m -> m.to }.toSet()) }
    }

    private fun tryMove(from: Int, to: Int) {
        val candidates = chess.position.legalMovesFrom(from).filter { it.to == to }
        when {
            candidates.isEmpty() -> _state.update { it.copy(selected = null, targets = emptySet()) }
            candidates.any { it.promotion != null } -> _state.update { it.copy(pendingPromotion = from to to) }
            else -> send(candidates.first())
        }
    }

    fun onPromotionChosen(type: PieceType?) {
        val pending = _state.value.pendingPromotion ?: return
        _state.update { it.copy(pendingPromotion = null, selected = null, targets = emptySet()) }
        if (type != null) send(Move(pending.first, pending.second, type))
    }

    private fun send(move: Move) {
        val s = _state.value
        val code = s.code ?: return
        val me = uid ?: return
        val g = s.game ?: return
        val expected = g.moves.size
        // Show the move at once; the listener confirms it (or puts the board back).
        val local = chess.copy()
        if (!local.play(move)) return
        _state.update {
            it.copy(
                position = local.position, lastMove = move, sanMoves = local.moves.map { m -> m.san },
                selected = null, targets = emptySet(), sending = true,
            )
        }
        viewModelScope.launch {
            val ok = try {
                repo.update(code, touch = true) { OnlineLogic.applyMove(it, me, move.uci, expected) }
            } catch (t: Throwable) {
                fail(OnlineError.NETWORK, t); false
            }
            _state.update { it.copy(sending = false) }
            if (!ok) _state.value.game?.let(::updateFromGame)
        }
    }

    // ------------------------------------------------------------ actions

    private fun act(change: (OnlineGame, String) -> OnlineGame?) {
        val code = _state.value.code ?: return
        val me = uid ?: return
        viewModelScope.launch {
            try {
                repo.update(code) { change(it, me) }
            } catch (t: Throwable) {
                fail(OnlineError.NETWORK, t)
            }
        }
    }

    fun resign() = act(OnlineLogic::resign)
    fun offerDraw() = act(OnlineLogic::offerDraw)
    fun acceptDraw() = act(OnlineLogic::acceptDraw)
    fun declineDraw() = act(OnlineLogic::clearDrawOffer)
    fun claimWin() = act { g, me -> OnlineLogic.claimAbandoned(g, me, now()) }

    /** Whether there is a draw offer from the opponent / from this player. */
    fun drawOfferedByOpponent(s: OnlineUiState): Boolean = s.game?.drawOffer?.let { it != uid } ?: false
    fun drawOfferedByMe(s: OnlineUiState): Boolean = s.game?.drawOffer?.let { it == uid } ?: false

    override fun onCleared() {
        stopGameJobs()
        super.onCleared()
    }
}
