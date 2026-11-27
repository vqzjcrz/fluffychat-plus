package app.switchboard.core

import android.content.Context
import android.util.Log
import app.switchboard.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.NotificationSettings
import org.matrix.rustcomponents.sdk.ProfileListener
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListEntriesWithDynamicAdaptersResult
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.SqliteStoreBuilder
import org.matrix.rustcomponents.sdk.SsoHandler
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.SyncServiceStateObserver
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.UserProfile
import org.matrix.rustcomponents.sdk.VerificationState
import org.matrix.rustcomponents.sdk.VerificationStateListener
import uniffi.matrix_sdk.BackupDownloadStrategy
import uniffi.ruma_events.StateEventType
import java.io.File
import java.security.SecureRandom
import java.util.UUID

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val userId: String) : AuthState
}

/** A room listed in a space (from the space's m.space.child events). */
data class SpaceChildRef(val roomId: String, val order: String?, val suggested: Boolean)

/** Owns the Matrix client: login, sync and the live list of rooms. */
class MatrixManager(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth

    @Volatile var client: Client? = null
        private set
    @Volatile var stored: StoredSession? = null
        private set
    @Volatile var syncService: SyncService? = null
        private set
    @Volatile var roomListService: RoomListService? = null
        private set
    @Volatile private var notificationSettings: NotificationSettings? = null

    private val handles = mutableListOf<TaskHandle>()
    private var entries: RoomListEntriesWithDynamicAdaptersResult? = null

    /** Rooms in the SDK's order (most recent activity first). */
    private val roomObjs = ArrayList<Room>()
    private val roomById = HashMap<String, Room>()
    private val listMutex = Mutex()
    private val updates = Channel<List<RoomListEntriesUpdate>>(Channel.UNLIMITED)

    private val _summaries = MutableStateFlow<Map<String, RoomSummary>>(emptyMap())
    val summaries: StateFlow<Map<String, RoomSummary>> = _summaries
    private val _order = MutableStateFlow<List<String>>(emptyList())
    /** Room IDs, most recent activity first. */
    val order: StateFlow<List<String>> = _order
    private val _spaceChildren = MutableStateFlow<Map<String, List<SpaceChildRef>>>(emptyMap())
    val spaceChildren: StateFlow<Map<String, List<SpaceChildRef>>> = _spaceChildren
    private val _syncState = MutableStateFlow(SyncServiceState.IDLE)
    val syncState: StateFlow<SyncServiceState> = _syncState
    private val _loaded = MutableStateFlow(false)
    /** True once the first room list arrived. */
    val loaded: StateFlow<Boolean> = _loaded

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile
    private val _recovery = MutableStateFlow(RecoveryState.UNKNOWN)
    val recovery: StateFlow<RecoveryState> = _recovery
    private val _verification = MutableStateFlow(VerificationState.UNKNOWN)
    val verification: StateFlow<VerificationState> = _verification

    val accountData = AccountDataCache(this)

    val userId: String? get() = stored?.session?.userId

    // ---------------------------------------------------------------- login

    private fun newStoreDir(): String = "s_" + UUID.randomUUID().toString().replace("-", "").take(16)

    private fun newPassphrase(): String {
        val b = ByteArray(32); SecureRandom().nextBytes(b)
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    private fun builder(storeDir: String, passphrase: String): ClientBuilder {
        val data = File(context.filesDir, "matrix/$storeDir").apply { mkdirs() }
        val cache = File(context.cacheDir, "matrix/$storeDir").apply { mkdirs() }
        return ClientBuilder()
            .sqliteStore(SqliteStoreBuilder(data.absolutePath, cache.absolutePath).passphrase(passphrase))
            .userAgent("Switchboard/${BuildConfig.VERSION_NAME} (Android)")
            .autoEnableCrossSigning(true)
            .autoEnableBackups(true)
            .backupDownloadStrategy(BackupDownloadStrategy.AFTER_DECRYPTION_FAILURE)
    }

    /** Restores the saved session, if there is one. */
    fun restore() {
        scope.launch {
            val s = SessionStore.load()
            if (s == null) { _auth.value = AuthState.LoggedOut; return@launch }
            try {
                val c = builder(s.storeDir, s.passphrase)
                    .homeserverUrl(s.session.homeserverUrl)
                    .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.NATIVE)
                    .build()
                c.restoreSession(s.session)
                stored = s
                client = c
                _auth.value = AuthState.LoggedIn(s.session.userId)
                startSync()
            } catch (e: Exception) {
                Log.e(TAG, "restore failed", e)
                // Keep the session: a network or store hiccup shouldn't log the user out.
                stored = s
                _auth.value = AuthState.LoggedIn(s.session.userId)
                retryRestoreLater()
            }
        }
    }

    private fun retryRestoreLater() {
        scope.launch {
            kotlinx.coroutines.delay(5000)
            if (client == null) restore()
        }
    }

    /** What a server offers for logging in. */
    data class ServerInfo(val url: String, val password: Boolean, val sso: Boolean, val oauth: Boolean)

    private var pendingLogin: Pair<Client, String>? = null
    private var pendingPassphrase: String? = null
    private var ssoHandler: SsoHandler? = null

    suspend fun checkServer(server: String): ServerInfo {
        val dir = newStoreDir()
        val pass = newPassphrase()
        val c = builder(dir, pass)
            .serverNameOrHomeserverUrl(server.trim())
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
            .build()
        val d = c.homeserverLoginDetails()
        pendingLogin = c to dir
        pendingPassphrase = pass
        return ServerInfo(d.url(), d.supportsPasswordLogin(), d.supportsSsoLogin(), d.supportsOauthLogin())
    }

    suspend fun loginWithPassword(server: String, user: String, password: String) {
        if (pendingLogin == null) checkServer(server)
        val (c, dir) = pendingLogin!!
        c.login(user.trim(), password, "Switchboard (Android)", null)
        finishLogin(c, dir, pendingPassphrase!!)
    }

    /** Starts SSO in the browser; returns the URL to open. */
    suspend fun startSso(server: String): String {
        if (pendingLogin == null) checkServer(server)
        val (c, _) = pendingLogin!!
        val h = c.startSsoLogin(SSO_REDIRECT, null)
        ssoHandler = h
        return h.url()
    }

    suspend fun finishSso(callbackUrl: String) {
        val h = ssoHandler ?: throw IllegalStateException("No login in progress")
        val (c, dir) = pendingLogin!!
        h.finish(callbackUrl)
        ssoHandler = null
        finishLogin(c, dir, pendingPassphrase!!)
    }

    private suspend fun finishLogin(c: Client, dir: String, pass: String) {
        val s = StoredSession(c.session(), dir, pass)
        SessionStore.save(s)
        stored = s
        client = c
        pendingLogin = null
        pendingPassphrase = null
        _auth.value = AuthState.LoggedIn(s.session.userId)
        startSync()
    }

    suspend fun logout() {
        val c = client
        try { app.switchboard.push.PushSetup.unregister(context) } catch (_: Exception) {}
        try { syncService?.stop() } catch (_: Exception) {}
        try { c?.logout() } catch (e: Exception) { Log.w(TAG, "logout", e) }
        teardown()
        stored?.let {
            File(context.filesDir, "matrix/${it.storeDir}").deleteRecursively()
            File(context.cacheDir, "matrix/${it.storeDir}").deleteRecursively()
        }
        SessionStore.clear()
        Prefs.clearAll()
        stored = null
        client = null
        _auth.value = AuthState.LoggedOut
    }

    private fun teardown() {
        handles.forEach { runCatching { it.cancel() } }
        handles.clear()
        runCatching { entries?.entriesStream()?.cancel() }
        entries = null
        roomObjs.clear(); roomById.clear()
        _summaries.value = emptyMap(); _order.value = emptyList(); _spaceChildren.value = emptyMap()
        _loaded.value = false
        syncService = null; roomListService = null; notificationSettings = null
    }

    // ---------------------------------------------------------------- sync

    private fun startSync() {
        val c = client ?: return
        scope.launch {
            try {
                val sync = c.syncService().withOfflineMode().finish()
                syncService = sync
                handles += sync.state(object : SyncServiceStateObserver {
                    override fun onUpdate(state: SyncServiceState) {
                        _syncState.value = state
                        if (state == SyncServiceState.ERROR || state == SyncServiceState.TERMINATED) {
                            scope.launch { kotlinx.coroutines.delay(3000); if (foreground) runCatching { sync.start() } }
                        }
                    }
                })
                val rls = sync.roomListService()
                roomListService = rls
                startRoomList(rls)
                sync.start()
                observeAccount(c)
                accountData.loadAll()
                Bridges.init(this@MatrixManager)
                app.switchboard.push.PushSetup.ensureRegistered(context)
            } catch (e: Exception) {
                Log.e(TAG, "sync start failed", e)
            }
        }
        scope.launch {
            for (batch in updates) {
                try { applyBatch(batch) } catch (e: Exception) { Log.e(TAG, "room list update", e) }
            }
        }
    }

    @Volatile var foreground = true

    fun onForeground() {
        foreground = true
        scope.launch { runCatching { syncService?.start() }; accountData.loadAll() }
    }

    fun onBackground() {
        foreground = false
        // The SDK keeps syncing briefly; push notifications take over afterwards.
        scope.launch {
            kotlinx.coroutines.delay(30_000)
            if (!foreground) runCatching { syncService?.stop() }
        }
    }

    private suspend fun startRoomList(rls: RoomListService) {
        val list = rls.allRooms()
        val result = list.entriesWithDynamicAdapters(100_000u, object : RoomListEntriesListener {
            override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) {
                updates.trySend(roomEntriesUpdate)
            }
        })
        result.controller().setFilter(RoomListEntriesDynamicFilterKind.NonLeft)
        entries = result
    }

    private val infoSemaphore = Semaphore(8)

    private suspend fun applyBatch(batch: List<RoomListEntriesUpdate>) = listMutex.withLock {
        val changed = LinkedHashSet<Room>()
        for (u in batch) {
            when (u) {
                is RoomListEntriesUpdate.Append -> { roomObjs.addAll(u.values); changed.addAll(u.values) }
                is RoomListEntriesUpdate.Clear -> roomObjs.clear()
                is RoomListEntriesUpdate.PushFront -> { roomObjs.add(0, u.value); changed.add(u.value) }
                is RoomListEntriesUpdate.PushBack -> { roomObjs.add(u.value); changed.add(u.value) }
                is RoomListEntriesUpdate.PopFront -> if (roomObjs.isNotEmpty()) roomObjs.removeAt(0)
                is RoomListEntriesUpdate.PopBack -> if (roomObjs.isNotEmpty()) roomObjs.removeAt(roomObjs.size - 1)
                is RoomListEntriesUpdate.Insert -> {
                    val i = u.index.toInt().coerceIn(0, roomObjs.size)
                    roomObjs.add(i, u.value); changed.add(u.value)
                }
                is RoomListEntriesUpdate.Set -> {
                    val i = u.index.toInt()
                    if (i in roomObjs.indices) roomObjs[i] = u.value else roomObjs.add(u.value)
                    changed.add(u.value)
                }
                is RoomListEntriesUpdate.Remove -> {
                    val i = u.index.toInt(); if (i in roomObjs.indices) roomObjs.removeAt(i)
                }
                is RoomListEntriesUpdate.Truncate -> {
                    val n = u.length.toInt(); while (roomObjs.size > n) roomObjs.removeAt(roomObjs.size - 1)
                }
                is RoomListEntriesUpdate.Reset -> { roomObjs.clear(); roomObjs.addAll(u.values); changed.addAll(u.values) }
            }
        }
        val ids = roomObjs.map { it.id() }
        roomById.clear()
        roomObjs.forEach { roomById[it.id()] = it }
        val own = userId ?: ""
        val fresh = scope.run {
            changed.map { room ->
                async {
                    infoSemaphore.withPermit {
                        try {
                            val info = room.roomInfo()
                            val latest = try { room.latestEvent() } catch (_: Exception) { null }
                            RoomSummary.from(info, latest, own)
                        } catch (e: Exception) {
                            Log.w(TAG, "room info", e); null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val idSet = ids.toHashSet()
        val map = HashMap(_summaries.value)
        map.keys.retainAll(idSet)
        fresh.forEach { map[it.id] = it }
        _summaries.value = map
        _order.value = ids
        _loaded.value = true
        val spaces = fresh.filter { it.isSpace }
        if (spaces.isNotEmpty()) refreshSpaceChildren(spaces.map { it.id })
        if (_spaceChildren.value.keys.any { it !in idSet }) {
            _spaceChildren.value = _spaceChildren.value.filterKeys { it in idSet }
        }
    }

    private fun refreshSpaceChildren(spaceIds: List<String>) {
        scope.launch {
            val updated = HashMap(_spaceChildren.value)
            for (id in spaceIds) {
                val room = roomById[id] ?: continue
                try {
                    val events = room.stateEvents(StateEventType.SpaceChild)
                    updated[id] = events.mapNotNull { ev ->
                        val c = try { JSONObject(ev.contentJson) } catch (_: Exception) { return@mapNotNull null }
                        val content = c.optJSONObject("content") ?: c
                        val via = content.optJSONArray("via")
                        if (via == null || via.length() == 0) return@mapNotNull null
                        SpaceChildRef(ev.stateKey, content.optString("order").ifEmpty { null }, content.optBoolean("suggested"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "space children", e)
                }
            }
            _spaceChildren.value = updated
        }
    }

    /** Reloads the children of a space after changing them. */
    fun reloadSpace(spaceId: String) = refreshSpaceChildren(listOf(spaceId))

    private fun observeAccount(c: Client) {
        handles += c.subscribeToOwnProfile(object : ProfileListener {
            override fun onUpdate(profile: UserProfile) { _profile.value = profile }
        })
        scope.launch {
            runCatching {
                val id = c.userId()
                _profile.value = UserProfile(id, c.displayName(), c.avatarUrl(), null, null)
            }
        }
        val enc = c.encryption()
        _recovery.value = enc.recoveryState()
        _verification.value = enc.verificationState()
        handles += enc.recoveryStateListener(object : RecoveryStateListener {
            override fun onUpdate(status: RecoveryState) { _recovery.value = status }
        })
        handles += enc.verificationStateListener(object : VerificationStateListener {
            override fun onUpdate(status: VerificationState) { _verification.value = status }
        })
    }

    // ---------------------------------------------------------------- helpers

    fun room(roomId: String): Room? = roomById[roomId] ?: try {
        client?.getRoom(roomId)
    } catch (_: Exception) { null }

    fun summary(roomId: String): RoomSummary? = _summaries.value[roomId]

    suspend fun notificationSettings(): NotificationSettings {
        notificationSettings?.let { return it }
        val n = client!!.getNotificationSettings()
        notificationSettings = n
        return n
    }

    suspend fun setMuted(roomId: String, muted: Boolean) {
        val n = notificationSettings()
        if (muted) n.setRoomNotificationMode(roomId, RoomNotificationMode.MUTE)
        else {
            val s = summary(roomId)
            n.unmuteRoom(roomId, s?.isEncrypted ?: false, s?.isDirect ?: false)
        }
    }

    suspend fun setNotificationMode(roomId: String, mode: RoomNotificationMode?) {
        val n = notificationSettings()
        if (mode == null) n.restoreDefaultRoomNotificationMode(roomId) else n.setRoomNotificationMode(roomId, mode)
    }

    /** Asks sync to keep this room fully up to date while it's open. */
    fun subscribeToRoom(roomId: String) {
        scope.launch { runCatching { roomListService?.setRoomSubscriptions(listOf(roomId)) } }
    }

    /** Forces summaries of these rooms to be rebuilt (after local changes). */
    fun refreshRooms(ids: Collection<String>) {
        scope.launch {
            val own = userId ?: ""
            val map = HashMap(_summaries.value)
            for (id in ids) {
                val r = room(id) ?: continue
                runCatching { map[id] = RoomSummary.from(r.roomInfo(), runCatching { r.latestEvent() }.getOrNull(), own) }
            }
            _summaries.value = map
        }
    }

    companion object {
        const val TAG = "Switchboard"
        const val SSO_REDIRECT = "app.switchboard://sso"
    }
}
