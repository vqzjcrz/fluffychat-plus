package dev.hush.client.discord

import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String = "",
    @SerialName("global_name") val globalName: String? = null,
    val avatar: String? = null,
    val discriminator: String? = null,
    val bot: Boolean = false,
) {
    val displayName: String
        get() = globalName?.takeIf { it.isNotBlank() } ?: username.ifBlank { "Unknown" }
}

@Serializable
data class Attachment(
    val id: String,
    val filename: String = "file",
    val url: String = "",
    @SerialName("proxy_url") val proxyUrl: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long = 0,
)

@Serializable
data class EmbedMedia(
    val url: String? = null,
    @SerialName("proxy_url") val proxyUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class EmbedNamed(val name: String? = null, val url: String? = null)

@Serializable
data class EmbedFooter(val text: String? = null)

@Serializable
data class EmbedField(val name: String = "", val value: String = "", val inline: Boolean = false)

@Serializable
data class Embed(
    val type: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val color: Int? = null,
    val image: EmbedMedia? = null,
    val thumbnail: EmbedMedia? = null,
    val video: EmbedMedia? = null,
    val author: EmbedNamed? = null,
    val provider: EmbedNamed? = null,
    val footer: EmbedFooter? = null,
    val fields: List<EmbedField> = emptyList(),
)

@Serializable
data class MessageReference(
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("guild_id") val guildId: String? = null,
)

@Serializable
data class Member(val nick: String? = null, val roles: List<String> = emptyList())

@Serializable
data class StickerItem(
    val id: String,
    val name: String = "",
    @SerialName("format_type") val formatType: Int = 1,
)

@Serializable
data class ReactionEmoji(val id: String? = null, val name: String? = null, val animated: Boolean = false) {
    fun sameAs(other: ReactionEmoji): Boolean =
        if (id != null || other.id != null) id == other.id else name == other.name
}

@Serializable
data class Reaction(val count: Int = 0, val me: Boolean = false, val emoji: ReactionEmoji = ReactionEmoji())

@Serializable
data class Message(
    val id: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("guild_id") val guildId: String? = null,
    val author: User,
    val content: String = "",
    val timestamp: String = "",
    @SerialName("edited_timestamp") val editedTimestamp: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val embeds: List<Embed> = emptyList(),
    val mentions: List<User> = emptyList(),
    @SerialName("mention_roles") val mentionRoles: List<String> = emptyList(),
    @SerialName("mention_everyone") val mentionEveryone: Boolean = false,
    @SerialName("referenced_message") val referencedMessage: Message? = null,
    @SerialName("message_reference") val messageReference: MessageReference? = null,
    val type: Int = 0,
    val member: Member? = null,
    @SerialName("sticker_items") val stickerItems: List<StickerItem> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
)

/** Message types rendered as ordinary chat messages; everything else is a system line. */
val NORMAL_MESSAGE_TYPES = setOf(0, 19, 20, 21, 23)

// ---- UI-facing state ----

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, AUTH_FAILED }

enum class ChannelKind { TEXT, ANNOUNCEMENT, CATEGORY, DM, GROUP_DM }

data class ChannelUi(
    val id: String,
    val guildId: String?,
    val name: String,
    val kind: ChannelKind,
    val topic: String? = null,
    val canSend: Boolean = true,
    val iconUrl: String? = null,
    val lastMessageId: String? = null,
    val muted: Boolean = false,
)

data class GuildUi(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val channels: List<ChannelUi>,
    val muted: Boolean,
)

data class Unread(val unread: Boolean = false, val mentions: Int = 0)

data class Typist(val userId: String, val name: String, val until: Long)

data class ChannelMessages(
    /** Newest first. */
    val list: List<Message> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
) {
    fun upsert(m: Message): ChannelMessages {
        val idx = list.indexOfFirst { it.id == m.id }
        val next = when {
            idx >= 0 -> list.toMutableList().also { it[idx] = m }
            list.isEmpty() || idCompare(m.id, list[0].id) > 0 -> listOf(m) + list
            else -> (list + m).sortedWith { a, b -> idCompare(b.id, a.id) }
        }
        return copy(list = next)
    }

    fun remove(id: String): ChannelMessages = copy(list = list.filterNot { it.id == id })
}

class UploadFile(val name: String, val mime: String, val bytes: ByteArray, val uri: Uri?)
