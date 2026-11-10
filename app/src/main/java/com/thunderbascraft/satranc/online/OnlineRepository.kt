package com.thunderbascraft.satranc.online

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.thunderbascraft.satranc.chess.Side
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Firebase access for online games. Only use it when [isConfigured] is true. */
class OnlineRepository {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseDatabase by lazy {
        val app = FirebaseApp.getInstance()
        // google-services.json only contains the database URL if the database existed when the
        // file was downloaded; otherwise use the default database in europe-west1.
        if (app.options.databaseUrl != null) FirebaseDatabase.getInstance(app)
        else FirebaseDatabase.getInstance(app, defaultDatabaseUrl(checkNotNull(app.options.projectId)))
    }

    private fun game(code: String): DatabaseReference = db.getReference("games").child(code)

    /** The anonymous user's id; signs in on first use (the id stays the same after restarts). */
    suspend fun signIn(): String =
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid

    /** Creates a waiting game with a fresh code and returns the code. */
    suspend fun create(uid: String, side: Side): String {
        val data = OnlineLogic.create(uid, side).toMap(ServerValue.TIMESTAMP, ServerValue.TIMESTAMP)
        repeat(8) {
            val code = GameCode.generate()
            // Aborts if the code is taken (the transaction is rerun with the server's data).
            if (transact(game(code)) { current -> if (current == null) data else null }) return code
        }
        error("No free game code found")
    }

    suspend fun load(code: String): OnlineGame? = OnlineGame.fromMap(game(code).get().await().value)

    /**
     * Changes the game atomically: [change] gets the current state and returns the new one, or
     * null to leave it unchanged. Returns whether the change was written.
     */
    suspend fun update(code: String, touch: Boolean = false, change: (OnlineGame) -> OnlineGame?): Boolean =
        transact(game(code)) { current ->
            val g = OnlineGame.fromMap(current) ?: return@transact null
            val next = change(g) ?: return@transact null
            if (touch) next.toMap(lastMoveAt = ServerValue.TIMESTAMP) else next.toMap()
        }

    suspend fun join(code: String, uid: String): JoinResult? {
        var outcome: JoinResult? = null
        update(code) { g ->
            val r = OnlineLogic.join(g, uid)
            outcome = r
            (r as? JoinResult.Joined)?.game
        }
        return outcome
    }

    /** Updates of the game; null when it does not exist (or cannot be read). */
    fun observe(code: String): Flow<OnlineGame?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(OnlineGame.fromMap(snapshot.value)) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        val ref = game(code)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Whether the device is connected to the database. */
    fun connected(): Flow<Boolean> = infoFlow(".info/connected") { it as? Boolean ?: false }

    /** Estimated difference between server and local clock (ms). */
    fun serverTimeOffset(): Flow<Long> = infoFlow(".info/serverTimeOffset") { (it as? Number)?.toLong() ?: 0L }

    private fun <T> infoFlow(path: String, map: (Any?) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(map(snapshot.value)) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        val ref = db.getReference(path)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Marks the player online; the server marks them offline when the connection drops. */
    suspend fun goOnline(code: String, uid: String) {
        val ref = game(code).child("presence").child(uid)
        ref.onDisconnect().setValue(presence(false)).await()
        ref.setValue(presence(true)).await()
    }

    suspend fun goOffline(code: String, uid: String) {
        val ref = game(code).child("presence").child(uid)
        ref.onDisconnect().cancel().await()
        ref.setValue(presence(false)).await()
    }

    private fun presence(online: Boolean) = mapOf("online" to online, "since" to ServerValue.TIMESTAMP)

    private suspend fun transact(ref: DatabaseReference, change: (Any?) -> Any?): Boolean =
        suspendCancellableCoroutine { cont ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = currentData.value
                    val next = change(current)
                    return when {
                        next != null -> { currentData.value = next; Transaction.success(currentData) }
                        // Nothing cached yet: let the server answer with the real data and rerun.
                        current == null -> Transaction.success(currentData)
                        else -> Transaction.abort()
                    }
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (!cont.isActive) return
                    if (error != null) cont.resumeWithException(error.toException())
                    else cont.resume(committed && currentData?.value != null)
                }
            })
        }

    companion object {
        fun defaultDatabaseUrl(projectId: String) = "https://$projectId-default-rtdb.europe-west1.firebasedatabase.app"

        /** False when the app was built without google-services.json. */
        fun isConfigured(context: Context): Boolean =
            runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
}

/** The online game this device is playing, so that it can be resumed after a restart. */
data class SavedOnlineGame(val code: String, val side: Side)

class OnlineStore(context: Context) {
    private val prefs = context.getSharedPreferences("online", Context.MODE_PRIVATE)

    fun load(): SavedOnlineGame? {
        val code = prefs.getString("code", null) ?: return null
        val side = runCatching { Side.valueOf(prefs.getString("side", null)!!) }.getOrNull() ?: return null
        return SavedOnlineGame(code, side)
    }

    fun save(g: SavedOnlineGame) = prefs.edit { putString("code", g.code); putString("side", g.side.name) }

    fun clear() = prefs.edit { remove("code"); remove("side") }
}
