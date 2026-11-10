package com.thunderbascraft.satranc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thunderbascraft.satranc.R
import com.thunderbascraft.satranc.chess.GameResult
import com.thunderbascraft.satranc.chess.Side
import com.thunderbascraft.satranc.online.GameCode
import com.thunderbascraft.satranc.online.OnlineStatus
import com.thunderbascraft.satranc.online.Reason

private val MaxWidth = 520.dp

/** Online play with a friend: lobby, waiting room and the game itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(vm: OnlineViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val inGame = state.code != null
    val back = { if (inGame) vm.leave() else onBack() }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        var menuOpen by remember { mutableStateOf(false) }
        var confirmResign by remember { mutableStateOf(false) }
        var confirmDraw by remember { mutableStateOf(false) }
        val playing = state.status == OnlineStatus.ACTIVE
        TopAppBar(
            title = { FitText(state.code?.let { stringResource(R.string.online_code_label, it) } ?: stringResource(R.string.online_title)) },
            navigationIcon = {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (playing) Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(Icons.Filled.Handshake, stringResource(R.string.online_offer_draw), enabled = state.game?.drawOffer == null) {
                            menuOpen = false; confirmDraw = true
                        }
                        MenuItem(Icons.Filled.Flag, stringResource(R.string.resign)) {
                            menuOpen = false; confirmResign = true
                        }
                    }
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (confirmResign) ConfirmDialog(stringResource(R.string.confirm_resign), { confirmResign = false; vm.resign() }) { confirmResign = false }
        if (confirmDraw) ConfirmDialog(stringResource(R.string.online_confirm_offer_draw), { confirmDraw = false; vm.offerDraw() }) { confirmDraw = false }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.configured -> NotConfigured()
                !inGame -> Lobby(state, vm)
                state.game == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.status == OnlineStatus.WAITING -> WaitingRoom(state, onCancel = vm::cancelWaiting)
                else -> OnlineGameView(state, vm)
            }
        }
    }

    state.error?.let { ErrorDialog(it, onDismiss = vm::clearError) }
}

@Composable
private fun NotConfigured() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.size(16.dp))
        Text(stringResource(R.string.online_not_configured_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.online_not_configured_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("online_not_configured"),
        )
    }
}

@Composable
private fun Lobby(state: OnlineUiState, vm: OnlineViewModel) {
    var side by rememberSaveable { mutableStateOf(SideChoice.WHITE) }
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.online_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MaxWidth),
        )
        state.resumable?.let { saved ->
            Card(
                onClick = vm::resume,
                enabled = !state.busy,
                modifier = Modifier.widthIn(max = MaxWidth).fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.online_resume), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.online_resume_desc, saved.code), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        ElevatedCard(Modifier.widthIn(max = MaxWidth).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardHeader(Icons.Filled.Add, stringResource(R.string.online_create), stringResource(R.string.online_create_desc))
                Text(stringResource(R.string.play_as), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SideChoice.entries.forEach { c ->
                        FilterChip(
                            selected = side == c,
                            onClick = { side = c },
                            label = {
                                FitText(stringResource(when (c) {
                                    SideChoice.WHITE -> R.string.white
                                    SideChoice.BLACK -> R.string.black
                                    SideChoice.RANDOM -> R.string.random
                                }))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Button(onClick = { vm.create(side) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("online_create")) {
                    Text(stringResource(R.string.online_create))
                }
            }
        }
        ElevatedCard(Modifier.widthIn(max = MaxWidth).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardHeader(Icons.AutoMirrored.Filled.Login, stringResource(R.string.online_join), stringResource(R.string.online_join_desc))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = GameCode.normalize(it).take(GameCode.LENGTH) },
                    label = { Text(stringResource(R.string.online_code_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.join(code) }),
                    modifier = Modifier.fillMaxWidth().testTag("online_code"),
                )
                OutlinedButton(
                    onClick = { vm.join(code) },
                    enabled = !state.busy && code.length == GameCode.LENGTH,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.online_join_button)) }
            }
        }
        if (state.busy) CircularProgressIndicator()
    }
}

@Composable
private fun CardHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WaitingRoom(state: OnlineUiState, onCancel: () -> Unit) {
    val code = state.code ?: return
    val context = LocalContext.current
    val shareText = stringResource(R.string.online_share_text, code)
    val copied = stringResource(R.string.copied)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.online_your_code), style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                code,
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("online_code_big"),
            )
        }
        Text(
            stringResource(R.string.online_you_play_as, stringResource(if (state.mySide == Side.WHITE) R.string.white else R.string.black)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
                context.startActivity(Intent.createChooser(send, null))
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.online_share))
            }
            OutlinedButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("code", code))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.online_copy))
            }
        }
        Spacer(Modifier.size(8.dp))
        CircularProgressIndicator()
        Text(stringResource(R.string.online_waiting), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        TextButton(onClick = onCancel) { Text(stringResource(R.string.online_cancel_game)) }
    }
}

@Composable
private fun OnlineGameView(state: OnlineUiState, vm: OnlineViewModel) {
    val you = stringResource(R.string.you)
    val opponentName = stringResource(R.string.online_opponent) + " · " +
        stringResource(if (state.opponentOnline) R.string.online_status_online else R.string.online_status_offline)
    val flipped = state.mySide == Side.BLACK
    val topSide = if (flipped) Side.WHITE else Side.BLACK
    val finished = state.status == OnlineStatus.FINISHED
    val toMove = state.position.sideToMove
    fun label(side: Side) = if (side == state.mySide) you else opponentName

    val topBar = @Composable { PlayerBar(label(topSide), topSide, active = !finished && toMove == topSide, thinking = false) }
    val bottomBar = @Composable { PlayerBar(label(topSide.opposite), topSide.opposite, active = !finished && toMove == topSide.opposite, thinking = state.sending) }
    val board = @Composable { mod: Modifier ->
        ChessBoard(
            position = state.position,
            lastMove = state.lastMove,
            selected = state.selected,
            targets = state.targets,
            flipped = flipped,
            onTap = vm::onSquareTapped,
            onDragStart = vm::onDragStart,
            onDrop = vm::onDrop,
            modifier = mod,
        )
    }
    val info = @Composable { mod: Modifier -> GameInfo(state, vm, mod) }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).padding(bottom = 8.dp)) { board(Modifier) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topBar(); bottomBar(); info(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxHeight().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topBar(); board(Modifier); bottomBar(); info(Modifier.weight(1f))
            }
        }
    }

    state.pendingPromotion?.let { PromotionDialog(side = state.mySide, onChoose = vm::onPromotionChosen) }

    var dismissedResult by rememberSaveable(state.code) { mutableStateOf(false) }
    if (finished && !dismissedResult) {
        AlertDialog(
            onDismissRequest = { dismissedResult = true },
            icon = { Icon(Icons.Filled.Flag, contentDescription = null) },
            title = { Text(stringResource(R.string.game_over), textAlign = TextAlign.Center) },
            text = {
                Text(
                    onlineResultText(state),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { Button(onClick = vm::leave) { Text(stringResource(R.string.online_back_to_lobby)) } },
            dismissButton = { TextButton(onClick = { dismissedResult = true }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun onlineResultText(state: OnlineUiState): String {
    if (state.cancelled) return stringResource(R.string.online_result_cancelled)
    val result = state.result ?: return stringResource(R.string.game_over)
    if (state.game?.reason == Reason.ABANDONED) {
        return stringResource(
            when ((result as? GameResult.Timeout)?.winner) {
                Side.WHITE -> R.string.online_result_abandoned_white
                Side.BLACK -> R.string.online_result_abandoned_black
                null -> R.string.online_result_abandoned_draw
            }
        )
    }
    return resultText(result)
}

@Composable
private fun GameInfo(state: OnlineUiState, vm: OnlineViewModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.connected) {
            Banner(stringResource(R.string.online_offline_banner), Icons.Filled.CloudOff)
        }
        when {
            state.status == OnlineStatus.FINISHED -> Text(
                onlineResultText(state),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            vm.drawOfferedByOpponent(state) -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.online_draw_offer_received), style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = vm::declineDraw) { Text(stringResource(R.string.online_decline)) }
                        Button(onClick = vm::acceptDraw) { Text(stringResource(R.string.online_accept)) }
                    }
                }
            }
            vm.drawOfferedByMe(state) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.online_draw_offered), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::declineDraw) { Text(stringResource(R.string.online_withdraw)) }
            }
            else -> Text(
                stringResource(if (state.myTurn) R.string.online_your_turn else R.string.online_their_turn),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.myTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        if (state.canClaimWin) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.online_claim_text), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = vm::claimWin) { Text(stringResource(R.string.online_claim_win)) }
                }
            }
        }
        if (state.sanMoves.isNotEmpty()) {
            Text(
                state.sanMoves.chunked(2).mapIndexed { i, p -> "${i + 1}. ${p.joinToString(" ")}" }.joinToString("  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Banner(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorDialog(error: OnlineError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
        text = {
            Text(stringResource(when (error) {
                OnlineError.SIGN_IN -> R.string.online_error_sign_in
                OnlineError.INVALID_CODE -> R.string.online_error_invalid_code
                OnlineError.NOT_FOUND -> R.string.online_error_not_found
                OnlineError.FULL -> R.string.online_error_full
                OnlineError.FINISHED -> R.string.online_error_finished
                OnlineError.EXPIRED -> R.string.online_error_expired
                OnlineError.NETWORK -> R.string.online_error_network
            }))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
