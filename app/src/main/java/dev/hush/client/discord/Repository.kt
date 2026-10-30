package dev.hush.client.discord

import android.util.Log
import dev.hush.client.data.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private class RoleData(val id: String, val name: String, val position: Int, val permissions: Long)

private class Overwrite(val id: String, val type: Int, val allow: Long, val deny: Long)

private class ChannelData(
    val id: String,
    val guildId: String?,
    val type: Int,
    val name: String?,
    val position: Int,
    val parentId: String?,
    val topic: String?,
    var lastMessageId: String?,
    val overwrites: List<Overwrite>,
    var recipients: List<User>,
    val icon: String?,
)

private class GuildData(
    val id: String,
    var name: String,
    var icon: String? = null,
    var ownerId: String? = null,
    val roles: HashMap<String, RoleData> = HashMap(),
    val channels: LinkedHashMap<String, ChannelData> = LinkedHashMap(),
    /** null = unknown (permissions can't be computed, so everything is shown). */
    var selfRoles: List<String>? = null,
)

private object Perm {
    const val ADMINISTRATOR = 8L
    const val VIEW_CHANNEL = 1024L
    const val SEND_MESSAGES = 2048L
    const val ALL = -1L
}

private const val TYPE_TEXT = 0
private const val TYPE_DM = 1
private const val TYPE_GROUP_DM = 3
private const val TYPE_CATEGORY = 4
private const val TYPE_ANNOUNCEMENT = 5

/**
 * Single source of truth for Discord state. Gateway events and all mutations of the
 * structural maps run on one confined dispatcher, so no locking is needed there.
 */
class DiscordRepository(
    http: OkHttpClient,
    private val json: Json,
    private val tokens: TokenStore,
) : Gateway.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val state = Dispatchers.Default.limitedParallelism(1)

    val rest = DiscordRest(http, json) { tokens.token.value }
    private val gateway = Gateway(
        http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
        json,
        scope,
        this,
    )

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me.asStateFlow()
    private val _guilds = MutableStateFlow<List<GuildUi>>(emptyList())
    val guilds: StateFlow<List<GuildUi>> = _guilds.asStateFlow()
    private val _dms = MutableStateFlow<List<ChannelUi>>(emptyList())
    val dms: StateFlow<List<ChannelUi>> = _dms.asStateFlow()
    private val _unread = MutableStateFlow<Map<String, Unread>>(emptyMap())
    val unread: StateFlow<Map<String, Unread>> = _unread.asStateFlow()
    private val _typing = MutableStateFlow<Map<String, List<Typist>>>(emptyMap())
    private val _authFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authFailed: SharedFlow<Unit> = _authFailed.asSharedFlow()

    // Confined to [state].
    private val guildData = LinkedHashMap<String, GuildData>()
    private val dmData = HashMap<String, ChannelData>()
    private val readState = HashMap<String, String>()
    private val mentionCounts = HashMap<String, Int>()
    private val mutedGuilds = HashSet<String>()
    private val mutedChannels = HashSet<String>()
    private val ackJobs = HashMap<String, Job>()

    // Lookup caches read from the UI thread.
    val users = ConcurrentHashMap<String, User>()
    val channelNames = ConcurrentHashMap<String, String>()
    val roleNames = ConcurrentHashMap<String, String>()
    private val nicks = ConcurrentHashMap<String, String>()
    private val messageStore = ConcurrentHashMap<String, MutableStateFlow<ChannelMessages>>()

    @Volatile var activeChannelId: String? = null
    @Volatile var readSyncEnabled = true
    @Volatile private var inForeground = false
    private var backgroundJob: Job? = null

    init {
        // Expire typing indicators.
        scope.launch {
            while (true) {
                delay(1_500)
                val now = System.currentTimeMillis()
                val current = _typing.value
                if (current.values.any { list -> list.any { it.until <= now } }) {
                    _typing.update { map ->
                        map.mapValues { (_, list) -> list.filter { it.until > now } }.filterValues { it.isNotEmpty() }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        val token = tokens.token.value ?: return
        if (gateway.running) return
        gateway.start(token)
    }

    fun onForeground() {
        inForeground = true
        backgroundJob?.cancel()
        start()
        activeChannelId?.let { cid -> scope.launch { markLatestRead(cid) } }
    }

    fun onBackground() {
        inForeground = false
        backgroundJob?.cancel()
        // Keep the socket for a bit so quick app switches don't reconnect.
        backgroundJob = scope.launch {
            delay(90_000)
            gateway.stop(keepSession = true)
        }
    }

    fun logout() {
        backgroundJob?.cancel()
        gateway.stop(keepSession = false)
        activeChannelId = null
        messageStore.clear()
        users.clear()
        nicks.clear()
        channelNames.clear()
        roleNames.clear()
        scope.launch(state) {
            guildData.clear()
            dmData.clear()
            readState.clear()
            mentionCounts.clear()
            mutedGuilds.clear()
            mutedChannels.clear()
            ackJobs.values.forEach { it.cancel() }
            ackJobs.clear()
            _me.value = null
            _guilds.value = emptyList()
            _dms.value = emptyList()
            _unread.value = emptyMap()
            _typing.value = emptyMap()
        }
    }

    override fun onState(state: ConnectionState) {
        _connection.value = state
        if (state == ConnectionState.AUTH_FAILED) _authFailed.tryEmit(Unit)
    }

    // ---------------------------------------------------------------- gateway events

    override suspend fun onDispatch(type: String, data: JsonObject) {
        withContext(state) {
            try {
                when (type) {
                    "READY" -> onReady(data)
                    "READY_SUPPLEMENTAL" -> onReadySupplemental(data)
                    "GUILD_CREATE", "GUILD_UPDATE" -> {
                        upsertGuild(data, null)
                        publishGuilds()
                    }
                    "GUILD_DELETE" -> {
                        val id = data.str("id")
                        if (id != null && data.bool("unavailable") != true) {
                            guildData.remove(id)
                            publishGuilds()
                        }
                    }
                    "CHANNEL_CREATE", "CHANNEL_UPDATE" -> upsertChannel(data)
                    "CHANNEL_DELETE" -> deleteChannel(data)
                    "GUILD_ROLE_CREATE", "GUILD_ROLE_UPDATE" -> {
                        val guild = data.str("guild_id")?.let { guildData[it] }
                        val role = data.obj("role")?.let { parseRole(it) }
                        if (guild != null && role != null) {
                            guild.roles[role.id] = role
                            roleNames[role.id] = role.name
                            publishGuilds()
                        }
                    }
                    "GUILD_ROLE_DELETE" -> {
                        val guild = data.str("guild_id")?.let { guildData[it] }
                        val roleId = data.str("role_id")
                        if (guild != null && roleId != null) {
                            guild.roles.remove(roleId)
                            publishGuilds()
                        }
                    }
                    "GUILD_MEMBER_UPDATE" -> onMemberUpdate(data)
                    "MESSAGE_CREATE" -> onMessageCreate(data)
                    "MESSAGE_UPDATE" -> onMessageUpdate(data)
                    "MESSAGE_DELETE" -> {
                        val cid = data.str("channel_id")
                        val id = data.str("id")
                        if (cid != null && id != null) messageStore[cid]?.update { it.remove(id) }
                    }
                    "MESSAGE_DELETE_BULK" -> {
                        val cid = data.str("channel_id")
                        val ids = data.arr("ids")?.map { it.jsonPrimitive.content }?.toSet()
                        if (cid != null && ids != null) {
                            messageStore[cid]?.update { cm -> cm.copy(list = cm.list.filterNot { it.id in ids }) }
                        }
                    }
                    "MESSAGE_ACK" -> {
                        val cid = data.str("channel_id")
                        val mid = data.str("message_id")
                        if (cid != null && mid != null) {
                            readState[cid] = mid
                            mentionCounts.remove(cid)
                            _unread.update { it - cid }
                        }
                    }
                    "MESSAGE_REACTION_ADD", "MESSAGE_REACTION_REMOVE",
                    "MESSAGE_REACTION_REMOVE_ALL", "MESSAGE_REACTION_REMOVE_EMOJI" -> onReaction(type, data)
                    "TYPING_START" -> onTyping(data)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply $type", e)
            }
        }
    }

    private fun onReady(d: JsonObject) {
        guildData.clear()
        dmData.clear()
        readState.clear()
        mentionCounts.clear()
        mutedGuilds.clear()
        mutedChannels.clear()

        val me = d.obj("user")?.let { decodeUser(it) }
        _me.value = me
        me?.let { users[it.id] = it }
        d.arr("users")?.forEach { el -> el.asObj()?.let { decodeUser(it) }?.let { users[it.id] = it } }

        val merged = d.arr("merged_members")
        d.arr("guilds")?.forEachIndexed { i, el ->
            val g = el.asObj() ?: return@forEachIndexed
            upsertGuild(g, merged?.getOrNull(i) as? JsonArray)
        }
        d.arr("private_channels")?.forEach { el ->
            el.asObj()?.let { parseChannel(it, null) }?.let { dmData[it.id] = it }
        }
        entriesOf(d["read_state"]).forEach { e ->
            val id = e.str("id") ?: return@forEach
            e.str("last_message_id")?.let { readState[id] = it }
            e.int("mention_count")?.takeIf { it > 0 }?.let { mentionCounts[id] = it }
        }
        entriesOf(d["user_guild_settings"]).forEach { s ->
            val gid = s.str("guild_id")
            if (gid != null && s.bool("muted") == true) mutedGuilds += gid
            s.arr("channel_overrides")?.forEach { o ->
                val ov = o.asObj() ?: return@forEach
                val cid = ov.str("channel_id")
                if (cid != null && ov.bool("muted") == true) mutedChannels += cid
            }
        }

        publishGuilds()
        publishDms()
        recomputeUnread()

        // A fresh session may have missed messages: drop cached history and reload what's open.
        val active = activeChannelId
        messageStore.keys.toList().forEach { if (it != active) messageStore.remove(it) }
        if (active != null) {
            scope.launch {
                loadInitial(active, replace = true)
                markLatestRead(active)
            }
        }
    }

    private fun onReadySupplemental(d: JsonObject) {
        val guilds = d.arr("guilds") ?: return
        val merged = d.arr("merged_members") ?: return
        val meId = _me.value?.id
        guilds.forEachIndexed { i, el ->
            val gid = el.asObj()?.str("id") ?: return@forEachIndexed
            val roles = selfRolesFrom(merged.getOrNull(i) as? JsonArray, meId) ?: return@forEachIndexed
            guildData[gid]?.selfRoles = roles
        }
        publishGuilds()
    }

    private fun entriesOf(el: JsonElement?): List<JsonObject> = when (el) {
        is JsonArray -> el.mapNotNull { it.asObj() }
        is JsonObject -> el.arr("entries")?.mapNotNull { it.asObj() } ?: emptyList()
        else -> emptyList()
    }

    private fun decodeUser(o: JsonObject): User? =
        runCatching { json.decodeFromJsonElement(User.serializer(), o) }.getOrNull()

    private fun parseRole(o: JsonObject): RoleData? {
        val id = o.str("id") ?: return null
        return RoleData(
            id = id,
            name = o.str("name") ?: "role",
            position = o.int("position") ?: 0,
            permissions = parsePermissions(o.str("permissions")),
        )
    }

    private fun parseOverwrites(arr: JsonArray?): List<Overwrite> = arr?.mapNotNull { el ->
        val o = el.asObj() ?: return@mapNotNull null
        val id = o.str("id") ?: return@mapNotNull null
        val t = o["type"]
        val type = when {
            t !is JsonPrimitive -> 0
            t.intOrNull != null -> t.intOrNull ?: 0
            t.content == "member" -> 1
            else -> 0
        }
        Overwrite(id, type, parsePermissions(o.str("allow")), parsePermissions(o.str("deny")))
    } ?: emptyList()

    private fun parseChannel(o: JsonObject, guildId: String?): ChannelData? {
        val id = o.str("id") ?: return null
        val recipients = o.arr("recipients")?.mapNotNull { el -> el.asObj()?.let { decodeUser(it) } }
            ?: o.arr("recipient_ids")?.mapNotNull { users[it.jsonPrimitive.content] }
            ?: emptyList()
        recipients.forEach { users[it.id] = it }
        val channel = ChannelData(
            id = id,
            guildId = guildId ?: o.str("guild_id"),
            type = o.int("type") ?: TYPE_TEXT,
            name = o.str("name"),
            position = o.int("position") ?: 0,
            parentId = o.str("parent_id"),
            topic = o.str("topic"),
            lastMessageId = o.str("last_message_id"),
            overwrites = parseOverwrites(o.arr("permission_overwrites")),
            recipients = recipients,
            icon = o.str("icon"),
        )
        channel.name?.let { channelNames[id] = it }
        return channel
    }

    private fun selfRolesFrom(arr: JsonArray?, meId: String?): List<String>? {
        if (arr == null || meId == null) return null
        val members = arr.mapNotNull { it.asObj() }
        val mine = members.firstOrNull { it.str("user_id") == meId || it.obj("user")?.str("id") == meId }
            ?: return null
        return mine.arr("roles")?.map { it.jsonPrimitive.content } ?: emptyList()
    }

    private fun upsertGuild(g: JsonObject, mergedSelf: JsonArray?) {
        val id = g.str("id") ?: return
        val props = g.obj("properties")
        val existing = guildData[id]
        val name = props?.str("name") ?: g.str("name") ?: existing?.name ?: return
        val data = existing ?: GuildData(id = id, name = name).also { guildData[id] = it }
        data.name = name
        when {
            props != null && "icon" in props -> data.icon = props.str("icon")
            "icon" in g -> data.icon = g.str("icon")
        }
        (props?.str("owner_id") ?: g.str("owner_id"))?.let { data.ownerId = it }
        g.arr("roles")?.let { roles ->
            data.roles.clear()
            roles.forEach { r ->
                r.asObj()?.let { parseRole(it) }?.let {
                    data.roles[it.id] = it
                    roleNames[it.id] = it.name
                }
            }
        }
        g.arr("channels")?.let { chans ->
            data.channels.clear()
            chans.forEach { c -> c.asObj()?.let { parseChannel(it, id) }?.let { data.channels[it.id] = it } }
        }
        val meId = _me.value?.id
        selfRolesFrom(mergedSelf, meId)?.let { data.selfRoles = it }
        if (data.selfRoles == null) selfRolesFrom(g.arr("members"), meId)?.let { data.selfRoles = it }
    }

    private fun upsertChannel(d: JsonObject) {
        val channel = parseChannel(d, null) ?: return
        if (channel.type == TYPE_DM || channel.type == TYPE_GROUP_DM) {
            val prev = dmData[channel.id]
            if (prev != null && channel.recipients.isEmpty()) channel.recipients = prev.recipients
            dmData[channel.id] = channel
            publishDms()
        } else {
            val guild = channel.guildId?.let { guildData[it] } ?: return
            guild.channels[channel.id] = channel
            publishGuilds()
        }
    }

    private fun deleteChannel(d: JsonObject) {
        val id = d.str("id") ?: return
        if (dmData.remove(id) != null) publishDms()
        val guild = d.str("guild_id")?.let { guildData[it] }
        if (guild?.channels?.remove(id) != null) publishGuilds()
    }

    private fun onMemberUpdate(d: JsonObject) {
        val gid = d.str("guild_id") ?: return
        val uid = d.obj("user")?.str("id") ?: return
        d.str("nick")?.let { nicks["$gid:$uid"] = it } ?: nicks.remove("$gid:$uid")
        if (uid == _me.value?.id) {
            guildData[gid]?.selfRoles = d.arr("roles")?.map { it.jsonPrimitive.content } ?: emptyList()
            publishGuilds()
        }
    }

    private fun findChannel(id: String): ChannelData? =
        dmData[id] ?: guildData.values.firstNotNullOfOrNull { it.channels[id] }

    private fun rememberUsers(m: Message, guildId: String?) {
        users[m.author.id] = m.author
        m.mentions.forEach { users[it.id] = it }
        m.referencedMessage?.author?.let { users[it.id] = it }
        val gid = guildId ?: m.guildId ?: return
        m.member?.nick?.let { nicks["$gid:${m.author.id}"] = it }
    }

    private fun onMessageCreate(d: JsonObject) {
        val msg = runCatching { json.decodeFromJsonElement(Message.serializer(), d) }.getOrNull() ?: return
        rememberUsers(msg, null)
        val gid = msg.guildId
        if (gid != null) {
            d.arr("mentions")?.forEach { el ->
                val o = el.asObj() ?: return@forEach
                val uid = o.str("id") ?: return@forEach
                o.obj("member")?.str("nick")?.let { nicks["$gid:$uid"] = it }
            }
        }

        val channel = findChannel(msg.channelId)
        channel?.lastMessageId = msg.id
        messageStore[msg.channelId]?.let { flow -> if (flow.value.loaded) flow.update { it.upsert(msg) } }
        removeTypist(msg.channelId, msg.author.id)

        val meId = _me.value?.id
        when {
            msg.author.id == meId -> {
                readState[msg.channelId] = msg.id
                mentionCounts.remove(msg.channelId)
                if (_unread.value.containsKey(msg.channelId)) _unread.update { it - msg.channelId }
            }
            activeChannelId == msg.channelId && inForeground -> {
                readState[msg.channelId] = msg.id
                ack(msg.channelId, msg.id)
            }
            channel != null -> {
                val selfRoles = gid?.let { guildData[it]?.selfRoles }.orEmpty()
                val mentioned = gid == null ||
                    msg.mentionEveryone ||
                    msg.mentions.any { it.id == meId } ||
                    msg.mentionRoles.any { it in selfRoles }
                _unread.update { map ->
                    val current = map[msg.channelId]
                    map + (msg.channelId to Unread(true, (current?.mentions ?: 0) + if (mentioned) 1 else 0))
                }
            }
        }
        if (channel != null && channel.guildId == null) publishDms()
    }

    private fun onMessageUpdate(d: JsonObject) {
        val id = d.str("id") ?: return
        val cid = d.str("channel_id") ?: return
        val flow = messageStore[cid] ?: return
        val full = if (d.obj("author") != null) {
            runCatching { json.decodeFromJsonElement(Message.serializer(), d) }.getOrNull()
        } else {
            null
        }
        full?.let { rememberUsers(it, null) }
        flow.update { cm ->
            cm.copy(list = cm.list.map { m ->
                if (m.id != id) {
                    m
                } else if (full != null) {
                    full.copy(referencedMessage = full.referencedMessage ?: m.referencedMessage)
                } else {
                    var r = m
                    d.str("content")?.let { r = r.copy(content = it) }
                    if ("edited_timestamp" in d) r = r.copy(editedTimestamp = d.str("edited_timestamp"))
                    d.arr("embeds")?.let {
                        r = r.copy(embeds = json.decodeFromJsonElement(ListSerializer(Embed.serializer()), it))
                    }
                    d.arr("attachments")?.let {
                        r = r.copy(attachments = json.decodeFromJsonElement(ListSerializer(Attachment.serializer()), it))
                    }
                    r
                }
            })
        }
    }

    private fun onReaction(type: String, d: JsonObject) {
        val cid = d.str("channel_id") ?: return
        val mid = d.str("message_id") ?: return
        val flow = messageStore[cid] ?: return
        val isMe = d.str("user_id") == _me.value?.id
        val emoji = d.obj("emoji")?.let {
            runCatching { json.decodeFromJsonElement(ReactionEmoji.serializer(), it) }.getOrNull()
        }
        flow.update { cm ->
            cm.copy(list = cm.list.map { m ->
                if (m.id != mid) return@map m
                val next = when (type) {
                    "MESSAGE_REACTION_ADD" -> if (emoji == null) m.reactions else addReaction(m.reactions, emoji, isMe)
                    "MESSAGE_REACTION_REMOVE" -> if (emoji == null) m.reactions else removeReaction(m.reactions, emoji, isMe)
                    "MESSAGE_REACTION_REMOVE_ALL" -> emptyList()
                    else -> if (emoji == null) m.reactions else m.reactions.filterNot { it.emoji.sameAs(emoji) }
                }
                m.copy(reactions = next)
            })
        }
    }

    private fun addReaction(list: List<Reaction>, emoji: ReactionEmoji, isMe: Boolean): List<Reaction> {
        val idx = list.indexOfFirst { it.emoji.sameAs(emoji) }
        if (idx < 0) return list + Reaction(count = 1, me = isMe, emoji = emoji)
        val r = list[idx]
        if (isMe && r.me) return list // already counted (we applied it optimistically)
        return list.toMutableList().also { it[idx] = r.copy(count = r.count + 1, me = r.me || isMe) }
    }

    private fun removeReaction(list: List<Reaction>, emoji: ReactionEmoji, isMe: Boolean): List<Reaction> {
        val idx = list.indexOfFirst { it.emoji.sameAs(emoji) }
        if (idx < 0) return list
        val r = list[idx]
        if (isMe && !r.me) return list // already removed optimistically
        val count = r.count - 1
        return if (count <= 0) {
            list.filterIndexed { i, _ -> i != idx }
        } else {
            list.toMutableList().also { it[idx] = r.copy(count = count, me = if (isMe) false else r.me) }
        }
    }

    private fun onTyping(d: JsonObject) {
        val cid = d.str("channel_id") ?: return
        val uid = d.str("user_id") ?: return
        if (uid == _me.value?.id) return
        val gid = d.str("guild_id")
        val member = d.obj("member")
        val memberUser = member?.obj("user")?.let { decodeUser(it) }
        memberUser?.let { users[it.id] = it }
        val nick = member?.str("nick")
        if (gid != null && nick != null) nicks["$gid:$uid"] = nick
        val name = nick ?: (memberUser ?: users[uid])?.displayName ?: "Someone"
        val until = System.currentTimeMillis() + 10_000
        _typing.update { map ->
            map + (cid to (map[cid].orEmpty().filter { it.userId != uid } + Typist(uid, name, until)))
        }
    }

    private fun removeTypist(cid: String, uid: String) {
        _typing.update { map ->
            val list = map[cid] ?: return@update map
            val next = list.filter { it.userId != uid }
            if (next.isEmpty()) map - cid else map + (cid to next)
        }
    }

    // ---------------------------------------------------------------- derived UI state

    private fun computePermissions(g: GuildData, ch: ChannelData, meId: String, selfRoles: List<String>): Long {
        if (g.ownerId == meId) return Perm.ALL
        var base = g.roles[g.id]?.permissions ?: 0L
        for (r in selfRoles) base = base or (g.roles[r]?.permissions ?: 0L)
        if (base and Perm.ADMINISTRATOR != 0L) return Perm.ALL

        var perms = base
        ch.overwrites.firstOrNull { it.id == g.id }?.let { perms = (perms and it.deny.inv()) or it.allow }
        var allow = 0L
        var deny = 0L
        for (o in ch.overwrites) {
            if (o.type == 0 && o.id in selfRoles) {
                allow = allow or o.allow
                deny = deny or o.deny
            }
        }
        perms = (perms and deny.inv()) or allow
        ch.overwrites.firstOrNull { it.type == 1 && it.id == meId }?.let { perms = (perms and it.deny.inv()) or it.allow }
        return perms
    }

    private fun isMuted(c: ChannelData): Boolean =
        (c.guildId != null && c.guildId in mutedGuilds) || c.id in mutedChannels ||
            (c.parentId != null && c.parentId in mutedChannels)

    private fun ChannelData.toUi(canSend: Boolean): ChannelUi = ChannelUi(
        id = id,
        guildId = guildId,
        name = name ?: "unknown",
        kind = when (type) {
            TYPE_ANNOUNCEMENT -> ChannelKind.ANNOUNCEMENT
            TYPE_CATEGORY -> ChannelKind.CATEGORY
            else -> ChannelKind.TEXT
        },
        topic = topic,
        canSend = canSend,
        lastMessageId = lastMessageId,
        muted = isMuted(this),
    )

    private fun ChannelData.toDmUi(): ChannelUi {
        val label = name?.takeIf { it.isNotBlank() }
            ?: recipients.joinToString(", ") { it.displayName }.ifBlank { "Unnamed" }
        val iconUrl = if (type == TYPE_DM) {
            recipients.firstOrNull()?.let { Cdn.avatar(it, 64, false) }
        } else {
            icon?.let { Cdn.channelIcon(id, it) }
        }
        return ChannelUi(
            id = id,
            guildId = null,
            name = label,
            kind = if (type == TYPE_DM) ChannelKind.DM else ChannelKind.GROUP_DM,
            iconUrl = iconUrl,
            lastMessageId = lastMessageId,
            muted = id in mutedChannels,
        )
    }

    private fun buildChannelList(g: GuildData, meId: String?): List<ChannelUi> {
        val selfRoles = g.selfRoles
        val textChannels = g.channels.values
            .filter { it.type == TYPE_TEXT || it.type == TYPE_ANNOUNCEMENT }
            .map { ch ->
                val perms = if (meId == null || selfRoles == null) Perm.ALL else computePermissions(g, ch, meId, selfRoles)
                ch to perms
            }
            .filter { (_, perms) -> perms and Perm.VIEW_CHANNEL != 0L }
        val categories = g.channels.values
            .filter { it.type == TYPE_CATEGORY }
            .sortedWith(compareBy<ChannelData>({ it.position }, { it.id.toLongOrNull() ?: 0L }))
        val byParent = textChannels.groupBy { it.first.parentId }

        val out = ArrayList<ChannelUi>()
        fun addChildren(parent: String?) {
            byParent[parent].orEmpty()
                .sortedWith(compareBy<Pair<ChannelData, Long>>({ it.first.position }, { it.first.id.toLongOrNull() ?: 0L }))
                .forEach { (ch, perms) -> out += ch.toUi(canSend = perms and Perm.SEND_MESSAGES != 0L) }
        }
        addChildren(null)
        val known = HashSet<String>()
        for (cat in categories) {
            known += cat.id
            if (byParent[cat.id].isNullOrEmpty()) continue
            out += cat.toUi(canSend = false)
            addChildren(cat.id)
        }
        byParent.keys.filter { it != null && it !in known }.forEach { addChildren(it) }
        return out
    }

    private fun publishGuilds() {
        val meId = _me.value?.id
        _guilds.value = guildData.values.map { g ->
            GuildUi(
                id = g.id,
                name = g.name,
                iconUrl = Cdn.guildIcon(g.id, g.icon),
                channels = buildChannelList(g, meId),
                muted = g.id in mutedGuilds,
            )
        }
    }

    private fun publishDms() {
        _dms.value = dmData.values
            .filter { it.type == TYPE_DM || it.type == TYPE_GROUP_DM }
            .sortedByDescending { (it.lastMessageId ?: it.id).toLongOrNull() ?: 0L }
            .map { it.toDmUi() }
    }

    private fun recomputeUnread() {
        val map = HashMap<String, Unread>()
        fun check(c: ChannelData) {
            val read = readState[c.id]
            val last = c.lastMessageId
            val mentions = mentionCounts[c.id] ?: 0
            val isUnread = read != null && last != null && idCompare(last, read) > 0
            if (isUnread || mentions > 0) map[c.id] = Unread(isUnread, mentions)
        }
        guildData.values.forEach { g ->
            g.channels.values.filter { it.type == TYPE_TEXT || it.type == TYPE_ANNOUNCEMENT }.forEach { check(it) }
        }
        dmData.values.forEach { check(it) }
        _unread.value = map
    }

    // ---------------------------------------------------------------- public API for screens

    fun nickFor(guildId: String, userId: String): String? = nicks["$guildId:$userId"]

    private fun flowFor(cid: String): MutableStateFlow<ChannelMessages> =
        messageStore.getOrPut(cid) { MutableStateFlow(ChannelMessages()) }

    fun messages(cid: String): StateFlow<ChannelMessages> = flowFor(cid).asStateFlow()

    fun typingIn(cid: String): Flow<List<Typist>> =
        _typing.map { it[cid].orEmpty() }.distinctUntilChanged()

    fun enterChannel(cid: String) {
        activeChannelId = cid
        scope.launch {
            loadInitial(cid)
            markLatestRead(cid)
        }
    }

    fun leaveChannel(cid: String) {
        if (activeChannelId == cid) activeChannelId = null
    }

    suspend fun loadInitial(cid: String, replace: Boolean = false) {
        val flow = flowFor(cid)
        var started = false
        flow.update {
            if (!replace && (it.loaded || it.loading)) {
                started = false
                it
            } else {
                started = true
                it.copy(loading = true, error = null)
            }
        }
        if (!started) return
        try {
            val list = rest.messages(cid)
            withContext(state) {
                val gid = findChannel(cid)?.guildId
                list.forEach { rememberUsers(it, gid) }
            }
            flow.update { cur ->
                val merged = if (replace) {
                    list
                } else {
                    (list + cur.list).distinctBy { it.id }.sortedWith { a, b -> idCompare(b.id, a.id) }
                }
                cur.copy(list = merged, loaded = true, loading = false, hasMore = list.size >= 50)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            flow.update { it.copy(loading = false, error = e.message ?: "Couldn't load messages") }
        }
    }

    suspend fun loadOlder(cid: String) {
        val flow = messageStore[cid] ?: return
        var before: String? = null
        flow.update {
            if (!it.loaded || it.loading || !it.hasMore || it.list.isEmpty()) {
                before = null
                it
            } else {
                before = it.list.last().id
                it.copy(loading = true)
            }
        }
        val oldest = before ?: return
        try {
            val list = rest.messages(cid, before = oldest)
            withContext(state) {
                val gid = findChannel(cid)?.guildId
                list.forEach { rememberUsers(it, gid) }
            }
            flow.update { cur ->
                val merged = (cur.list + list).distinctBy { it.id }.sortedWith { a, b -> idCompare(b.id, a.id) }
                cur.copy(list = merged, loading = false, hasMore = list.size >= 50)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            flow.update { it.copy(loading = false) }
        }
    }

    suspend fun markLatestRead(cid: String) {
        withContext(state) {
            val latest = messageStore[cid]?.value?.list?.firstOrNull()?.id
                ?: findChannel(cid)?.lastMessageId
                ?: return@withContext
            val previous = readState[cid]
            readState[cid] = latest
            mentionCounts.remove(cid)
            val wasUnread = _unread.value.containsKey(cid)
            if (wasUnread) _unread.update { it - cid }
            if (wasUnread || (previous != null && idCompare(latest, previous) > 0)) ack(cid, latest)
        }
    }

    /** Must run on [state]. Debounced so busy channels don't spam the endpoint. */
    private fun ack(cid: String, mid: String) {
        if (!readSyncEnabled) return
        ackJobs[cid]?.cancel()
        ackJobs[cid] = scope.launch {
            delay(1_500)
            runCatching { rest.ack(cid, mid) }
        }
    }

    suspend fun send(cid: String, content: String, replyTo: Message?, mentionReply: Boolean, files: List<UploadFile>) {
        val sent = rest.send(cid, content, replyTo, mentionReply, files)
        withContext(state) {
            rememberUsers(sent, findChannel(cid)?.guildId)
            findChannel(cid)?.lastMessageId = sent.id
            readState[cid] = sent.id
        }
        messageStore[cid]?.update { it.upsert(sent) }
    }

    suspend fun edit(message: Message, content: String) {
        val updated = rest.edit(message.channelId, message.id, content)
        messageStore[message.channelId]?.update {
            it.upsert(updated.copy(referencedMessage = updated.referencedMessage ?: message.referencedMessage))
        }
    }

    suspend fun delete(message: Message) {
        rest.delete(message.channelId, message.id)
        messageStore[message.channelId]?.update { it.remove(message.id) }
    }

    suspend fun toggleReaction(message: Message, emoji: ReactionEmoji) {
        val existing = message.reactions.firstOrNull { it.emoji.sameAs(emoji) }
        val add = existing?.me != true
        // Optimistic update; the gateway echo is de-duplicated in add/removeReaction.
        messageStore[message.channelId]?.update { cm ->
            cm.copy(list = cm.list.map { m ->
                if (m.id != message.id) m
                else m.copy(reactions = if (add) addReaction(m.reactions, emoji, true) else removeReaction(m.reactions, emoji, true))
            })
        }
        try {
            rest.react(message.channelId, message.id, emoji, add)
        } catch (e: Exception) {
            // Roll back.
            messageStore[message.channelId]?.update { cm ->
                cm.copy(list = cm.list.map { m -> if (m.id == message.id) m.copy(reactions = message.reactions) else m })
            }
            throw e
        }
    }

    fun sendTyping(cid: String) {
        scope.launch { runCatching { rest.typing(cid) } }
    }

    companion object {
        private const val TAG = "HushRepo"
    }
}
