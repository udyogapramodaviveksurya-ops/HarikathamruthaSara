package com.example.harikathamruthasara.account

import com.example.harikathamruthasara.HkApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface SyncStatus {
    /** not signed in, or no Firebase in this build */
    data object Off : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Failed(val message: String?) : SyncStatus
}

/**
 * Keeps the signed-in reader's progress and settings in Firestore at users/{uid}, so a second phone or tablet
 * picks up where the first left off.
 *
 *  - Progress is MERGED (never overwritten): the higher counts, every badge, every day — see ProgressStore.mergeFrom.
 *  - Settings: the side changed most recently wins.
 *  - Live: a change on one device reaches the others within seconds while they're open.
 *  - Offline: Firestore queues writes and sends them when the phone is back online.
 */
class CloudSync(private val app: HkApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: FirebaseFirestore? = if (app.account.available) FirebaseFirestore.getInstance() else null
    private var listener: ListenerRegistration? = null
    private var pushJob: Job? = null
    private var uid: String? = null
    private var lastProgress: String? = null
    private var lastSettingsAt: Long = -1

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status

    private fun doc(u: String) = db!!.collection("users").document(u)

    /** Follows sign-in / sign-out. */
    fun attach() {
        if (db == null) return
        scope.launch {
            app.account.user.collect { u ->
                if (u == null) stop() else if (u.uid != uid) start(u)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun start(u: UserInfo) {
        stop()
        uid = u.uid
        _status.value = SyncStatus.Syncing
        // 1) first: pull & merge, then push the merged result
        scope.launch {
            runCatching {
                val snap = doc(u.uid).get().await()
                applyRemote(snap.getString("progress"), snap.getString("settings"), snap.getLong("settingsAt") ?: 0)
                push(force = true, user = u)
            }.onFailure { _status.value = SyncStatus.Failed(it.message) }
        }
        // 2) live: changes from other devices
        listener = doc(u.uid).addSnapshotListener { snap, err ->
            if (err != null) { _status.value = SyncStatus.Failed(err.message); return@addSnapshotListener }
            if (snap == null || snap.metadata.hasPendingWrites()) return@addSnapshotListener
            applyRemote(snap.getString("progress"), snap.getString("settings"), snap.getLong("settingsAt") ?: 0)
        }
        // 3) local changes → cloud, a few seconds after the reader stops tapping
        pushJob = scope.launch {
            combine(app.progress.state, app.settings.flow) { _, _ -> Unit }.drop(1).debounce(4000).collect { push(force = false) }
        }
        // push notifications: this device's token, so the server can reach the reader directly
        runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> saveToken(t) } }
    }

    fun stop() {
        listener?.remove(); listener = null
        pushJob?.cancel(); pushJob = null
        uid = null; lastProgress = null; lastSettingsAt = -1
        _status.value = SyncStatus.Off
    }

    private fun applyRemote(progress: String?, settings: String?, settingsAt: Long) {
        if (progress != null) app.progress.mergeFrom(progress)
        // a device joining this account for the first time takes the account's settings, even if it has just
        // picked a language (which made its own settings "newer"); after that, the most recent change wins
        val firstTime = app.settings.syncedUid != uid
        if (settings != null && (firstTime || settingsAt > app.settings.changedAt)) app.settings.importJson(settings, maxOf(settingsAt, 1))
        uid?.let { app.settings.syncedUid = it }
    }

    private suspend fun push(force: Boolean, user: UserInfo? = null) {
        val u = uid ?: return
        val p = app.progress.exportJson()
        val sAt = app.settings.changedAt
        if (!force && p == lastProgress && sAt == lastSettingsAt) return
        _status.value = SyncStatus.Syncing
        runCatching {
            val data = mutableMapOf<String, Any>(
                "progress" to p, "settings" to app.settings.exportJson(), "settingsAt" to sAt,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            (user ?: app.account.user.value)?.let { info ->
                info.name?.let { data["name"] = it }; info.email?.let { data["email"] = it }; info.phone?.let { data["phone"] = it }
            }
            doc(u).set(data, SetOptions.merge()).await()
            lastProgress = p; lastSettingsAt = sAt
            _status.value = SyncStatus.Synced(System.currentTimeMillis())
        }.onFailure { _status.value = SyncStatus.Failed(it.message) }
    }

    /** "Sync now" in Settings. */
    fun syncNow() {
        val u = app.account.user.value ?: return
        scope.launch {
            runCatching {
                val snap = doc(u.uid).get().await()
                applyRemote(snap.getString("progress"), snap.getString("settings"), snap.getLong("settingsAt") ?: 0)
                push(force = true)
            }.onFailure { _status.value = SyncStatus.Failed(it.message) }
        }
    }

    /** Called by the messaging service when Firebase gives this install a new push token. */
    fun saveToken(token: String) {
        val u = uid ?: return
        scope.launch { runCatching { doc(u).set(mapOf("tokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).await() } }
    }

    suspend fun deleteCloudData(u: String) { runCatching { doc(u).delete().await() } }
}
