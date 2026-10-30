package dev.hush.client.ui

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hush.client.HushApp
import dev.hush.client.data.AppSettings
import dev.hush.client.discord.ApiException
import dev.hush.client.discord.ChannelMessages
import dev.hush.client.discord.ChannelUi
import dev.hush.client.discord.ConnectionState
import dev.hush.client.discord.GuildUi
import dev.hush.client.discord.Message
import dev.hush.client.discord.ReactionEmoji
import dev.hush.client.discord.Typist
import dev.hush.client.discord.Unread
import dev.hush.client.discord.UploadFile
import dev.hush.client.discord.User
import dev.hush.client.ui.chat.MdResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { MESSAGES, SERVERS, MENTIONS, YOU }

sealed interface Route {
    data object Home : Route
    data class ServerChannels(val guildId: String) : Route
    data class Chat(val channelId: String) : Route
    data object Settings : Route
}

class AppViewModel(private val app: HushApp) : ViewModel() {
    private val repo = app.repository

    /** null until DataStore has loaded, so the first frame uses the right theme. */
    val settings: StateFlow<AppSettings?> =
        app.settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val token: StateFlow<String?> = app.tokenStore.token
    val me: StateFlow<User?> = repo.me
    val guilds: StateFlow<List<GuildUi>> = repo.guilds
    val dms: StateFlow<List<ChannelUi>> = repo.dms
    val unread: StateFlow<Map<String, Unread>> = repo.unread
    val connection: StateFlow<ConnectionState> = repo.connection

    var route by mutableStateOf<Route>(Route.Home)
        private set
    var tab by mutableStateOf(Tab.MESSAGES)
    var selectedGuildId by mutableStateOf<String?>(null)
    private val backStack = ArrayDeque<Route>()

    var sessionExpired by mutableStateOf(false)
        private set
    var loggingIn by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // Composer state for the open channel.
    var replyTo by mutableStateOf<Message?>(null)
    var replyMention by mutableStateOf(true)
    var editing by mutableStateOf<Message?>(null)
    var sending by mutableStateOf(false)
        private set
    val attachments = mutableStateListOf<UploadFile>()
    private var lastTypingSent = 0L

    init {
        viewModelScope.launch {
            settings.collect { s -> if (s != null) repo.readSyncEnabled = s.syncReadState }
        }
        viewModelScope.launch {
            repo.authFailed.collect {
                sessionExpired = true
                logout()
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { app.settingsStore.update(transform) }
    }

    // ---------------------------------------------------------------- account

    fun login(raw: String) {
        val token = raw.trim().removeSurrounding("\"")
        if (token.isEmpty() || loggingIn) return
        viewModelScope.launch {
            loggingIn = true
            loginError = null
            try {
                repo.rest.me(token)
                app.tokenStore.save(token)
                sessionExpired = false
                repo.start()
            } catch (e: ApiException) {
                loginError = if (e.code == 401) "Discord rejected that token." else (e.message ?: "Sign-in failed.")
            } catch (e: Exception) {
                loginError = "Couldn't reach Discord. Check your connection."
            } finally {
                loggingIn = false
            }
        }
    }

    fun logout() {
        repo.logout()
        app.tokenStore.clear()
        backStack.clear()
        route = Route.Home
        tab = Tab.MESSAGES
        selectedGuildId = null
        replyTo = null
        editing = null
        attachments.clear()
    }

    // ---------------------------------------------------------------- navigation

    private fun navigate(r: Route) {
        if (route != r) backStack.addLast(route)
        route = r
    }

    fun openChannel(id: String) {
        replyTo = null
        editing = null
        attachments.clear()
        navigate(Route.Chat(id))
    }

    fun openServer(guildId: String) {
        selectedGuildId = guildId
        navigate(Route.ServerChannels(guildId))
    }

    fun openSettings() = navigate(Route.Settings)

    fun back() {
        route = backStack.removeLastOrNull() ?: Route.Home
    }

    fun findChannel(id: String): ChannelUi? =
        dms.value.firstOrNull { it.id == id }
            ?: guilds.value.firstNotNullOfOrNull { g -> g.channels.firstOrNull { it.id == id } }

    // ---------------------------------------------------------------- channel content

    fun messages(channelId: String): StateFlow<ChannelMessages> = repo.messages(channelId)
    fun typing(channelId: String): Flow<List<Typist>> = repo.typingIn(channelId)
    fun enterChannel(channelId: String) = repo.enterChannel(channelId)
    fun leaveChannel(channelId: String) = repo.leaveChannel(channelId)

    fun loadOlder(channelId: String) {
        viewModelScope.launch { repo.loadOlder(channelId) }
    }

    fun retry(channelId: String) {
        viewModelScope.launch { repo.loadInitial(channelId) }
    }

    fun displayName(m: Message, guildId: String?): String =
        guildId?.let { repo.nickFor(it, m.author.id) } ?: m.member?.nick ?: m.author.displayName

    fun resolver(guildId: String?, h24: Boolean): MdResolver = object : MdResolver {
        override fun user(id: String): String? =
            guildId?.let { repo.nickFor(it, id) } ?: repo.users[id]?.displayName
        override fun channel(id: String): String? = repo.channelNames[id]
        override fun role(id: String): String? = repo.roleNames[id]
        override val use24h: Boolean = h24
    }

    // ---------------------------------------------------------------- composing

    fun onTyping(channelId: String) {
        if (settings.value?.sendTyping == false) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTypingSent > 8_000) {
            lastTypingSent = now
            repo.sendTyping(channelId)
        }
    }

    fun startReply(m: Message) {
        editing = null
        replyTo = m
        replyMention = true
    }

    fun startEdit(m: Message) {
        replyTo = null
        editing = m
    }

    fun delete(m: Message) {
        viewModelScope.launch {
            try {
                repo.delete(m)
            } catch (e: Exception) {
                toast("Couldn't delete: ${e.message}")
            }
        }
    }

    fun toggleReaction(m: Message, emoji: ReactionEmoji) {
        viewModelScope.launch {
            try {
                repo.toggleReaction(m, emoji)
            } catch (e: Exception) {
                toast("Couldn't react: ${e.message}")
            }
        }
    }

    /** Sends (or saves an edit). [onFailed] hands the text back to the composer. */
    fun submit(channelId: String, text: String, onFailed: (String) -> Unit) {
        val content = text.trim()
        val edit = editing
        if (edit != null) {
            editing = null
            if (content.isEmpty() || content == edit.content) return
            viewModelScope.launch {
                try {
                    repo.edit(edit, content)
                } catch (e: Exception) {
                    toast("Couldn't edit: ${e.message}")
                    onFailed(text)
                }
            }
            return
        }
        if (content.isEmpty() && attachments.isEmpty()) return
        if (content.length > 2000) {
            toast("Messages can be up to 2000 characters.")
            onFailed(text)
            return
        }
        val files = attachments.toList()
        val reply = replyTo
        val mention = replyMention
        attachments.clear()
        replyTo = null
        sending = true
        lastTypingSent = 0
        viewModelScope.launch {
            try {
                repo.send(channelId, content, reply, mention, files)
            } catch (e: Exception) {
                toast("Couldn't send: ${e.message}")
                onFailed(text)
                attachments.addAll(files)
                replyTo = reply
            } finally {
                sending = false
            }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            val resolver = app.contentResolver
            for (uri in uris) {
                if (attachments.size >= 10) break
                val file = withContext(Dispatchers.IO) { runCatching { readUpload(resolver, uri) }.getOrNull() }
                when {
                    file == null -> toast("Couldn't read that file.")
                    file.bytes.size > MAX_UPLOAD -> toast("${file.name} is over 10 MB.")
                    else -> attachments += file
                }
            }
        }
    }

    private fun readUpload(resolver: ContentResolver, uri: Uri): UploadFile {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "file"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unreadable")
        return UploadFile(name, mime, bytes, uri)
    }

    private fun toast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MAX_UPLOAD = 10 * 1024 * 1024

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HushApp)
            }
        }
    }
}
