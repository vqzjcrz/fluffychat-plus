package app.switchboard.core

import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MembershipChange
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.RoomInfo
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.TimelineItemContent
import uniffi.matrix_sdk_base.EncryptionState
import uniffi.ruma_events.MessageLikeEventType

/** Everything the chat lists need to know about one room. */
data class RoomSummary(
    val id: String,
    /** The room's own name (not the user's nickname for it). */
    val realName: String,
    val avatarUrl: String?,
    val isSpace: Boolean,
    val isDirect: Boolean,
    val membership: Membership,
    val notificationCount: Long,
    val highlightCount: Long,
    val unreadMessages: Long,
    val unreadMentions: Long,
    val markedUnread: Boolean,
    val notificationMode: RoomNotificationMode?,
    val lastTs: Long,
    val preview: String?,
    val previewSender: String?,
    val previewIsOwn: Boolean,
    val isEncrypted: Boolean,
    val canSendMessages: Boolean,
    val canEditSpace: Boolean,
    val canPin: Boolean,
    val joinedCount: Long,
    val topic: String?,
    val alias: String?,
    val heroIds: List<String>,
    val inviterName: String?,
    val isFavourite: Boolean,
    val isLowPriority: Boolean,
    val directUserId: String?,
) {
    val isInvite get() = membership == Membership.INVITED
    val isMuted get() = notificationMode == RoomNotificationMode.MUTE
    val isUnread get() = markedUnread || unreadMessages > 0 || notificationCount > 0
    val hasMention get() = highlightCount > 0 || unreadMentions > 0
    /** Number for the badge: notifications if the server counts them, else unread messages. */
    val badgeCount get() = if (notificationCount > 0) notificationCount else unreadMessages

    companion object {
        fun from(info: RoomInfo, latest: LatestEventValue?, ownUserId: String): RoomSummary {
            var ts = 0L
            var preview: String? = null
            var sender: String? = null
            var own = false
            when (latest) {
                is LatestEventValue.Remote -> {
                    ts = latest.timestamp.toLong()
                    preview = Previews.content(latest.content)
                    sender = Previews.name(latest.profile, latest.sender)
                    own = latest.isOwn
                }
                is LatestEventValue.Local -> {
                    ts = latest.timestamp.toLong()
                    preview = Previews.content(latest.content)
                    sender = Previews.name(latest.profile, latest.sender)
                    own = true
                }
                is LatestEventValue.RemoteInvite -> {
                    ts = latest.timestamp.toLong()
                    preview = "Invited you"
                    sender = latest.inviter?.let { Previews.name(latest.inviterProfile, it) }
                }
                else -> {}
            }
            val pl = info.powerLevels
            val canSend = try {
                pl?.canOwnUserSendMessage(MessageLikeEventType.RoomMessage) ?: true
            } catch (_: Exception) { true }
            val canEditSpace = try {
                pl?.canOwnUserSendState(uniffi.ruma_events.StateEventType.SpaceChild) ?: false
            } catch (_: Exception) { false }
            val canPin = try { pl?.canOwnUserPinUnpin() ?: false } catch (_: Exception) { false }
            val heroes = info.heroes.map { it.userId }
            val direct = if (info.isDirect) heroes.firstOrNull { it != ownUserId } else null
            return RoomSummary(
                id = info.id,
                realName = info.displayName?.takeIf { it.isNotBlank() } ?: info.rawName ?: info.canonicalAlias ?: info.id,
                avatarUrl = info.avatarUrl ?: if (info.isDirect && info.heroes.size == 1) info.heroes[0].avatarUrl else null,
                isSpace = info.isSpace,
                isDirect = info.isDirect,
                membership = info.membership,
                notificationCount = info.notificationCount.toLong(),
                highlightCount = info.highlightCount.toLong(),
                unreadMessages = info.numUnreadMessages.toLong(),
                unreadMentions = info.numUnreadMentions.toLong(),
                markedUnread = info.isMarkedUnread,
                notificationMode = info.cachedUserDefinedNotificationMode,
                lastTs = ts,
                preview = preview,
                previewSender = sender,
                previewIsOwn = own,
                isEncrypted = info.encryptionState == EncryptionState.ENCRYPTED,
                canSendMessages = canSend,
                canEditSpace = canEditSpace,
                canPin = canPin,
                joinedCount = info.joinedMembersCount.toLong(),
                topic = info.topic,
                alias = info.canonicalAlias,
                heroIds = heroes,
                inviterName = info.inviter?.let { it.displayName ?: it.userId },
                isFavourite = info.isFavourite,
                isLowPriority = info.isLowPriority,
                directUserId = direct,
            )
        }
    }
}

/** Short text describing an event, for chat list previews and notifications. */
object Previews {
    fun name(profile: ProfileDetails, userId: String): String =
        (profile as? ProfileDetails.Ready)?.displayName?.takeIf { it.isNotBlank() } ?: localpart(userId)

    fun localpart(userId: String) = userId.removePrefix("@").substringBefore(':')

    fun content(content: TimelineItemContent): String = when (content) {
        is TimelineItemContent.MsgLike -> when (val k = content.content.kind) {
            is MsgLikeKind.Message -> message(k.content)
            is MsgLikeKind.Sticker -> "Sticker"
            is MsgLikeKind.Poll -> "Poll: ${k.question}"
            is MsgLikeKind.Redacted -> "Message deleted"
            is MsgLikeKind.UnableToDecrypt -> "Encrypted message"
            else -> "Message"
        }
        is TimelineItemContent.RoomMembership -> membership(content.userDisplayName ?: localpart(content.userId), content.change)
        is TimelineItemContent.ProfileChange -> when {
            content.displayName != content.prevDisplayName -> "Changed their name to ${content.displayName ?: "nothing"}"
            else -> "Changed their profile picture"
        }
        is TimelineItemContent.CallInvite -> "Call"
        is TimelineItemContent.RtcNotification -> "Call"
        else -> ""
    }

    fun message(m: MessageContent): String = when (val t = m.msgType) {
        is MessageType.Image -> t.content.caption?.takeIf { it.isNotBlank() } ?: "Photo"
        is MessageType.Video -> t.content.caption?.takeIf { it.isNotBlank() } ?: "Video"
        is MessageType.Audio -> if (t.content.voice != null) "Voice message" else "Audio"
        is MessageType.File -> "File: ${t.content.filename}"
        is MessageType.Location -> "Location"
        is MessageType.Emote -> "* ${t.content.body}"
        is MessageType.Gallery -> "Album"
        is MessageType.Text -> t.content.body
        is MessageType.Notice -> t.content.body
        else -> m.body
    }

    fun membership(who: String, change: MembershipChange?): String = when (change) {
        MembershipChange.JOINED -> "$who joined"
        MembershipChange.LEFT -> "$who left"
        MembershipChange.BANNED, MembershipChange.KICKED_AND_BANNED -> "$who was banned"
        MembershipChange.UNBANNED -> "$who was unbanned"
        MembershipChange.KICKED -> "$who was removed"
        MembershipChange.INVITED -> "$who was invited"
        MembershipChange.INVITATION_ACCEPTED -> "$who accepted the invite"
        MembershipChange.INVITATION_REJECTED -> "$who declined the invite"
        MembershipChange.INVITATION_REVOKED -> "$who's invite was withdrawn"
        MembershipChange.KNOCKED -> "$who asked to join"
        MembershipChange.KNOCK_ACCEPTED -> "$who was let in"
        MembershipChange.KNOCK_RETRACTED -> "$who stopped asking to join"
        MembershipChange.KNOCK_DENIED -> "$who wasn't let in"
        else -> "$who changed membership"
    }

    fun notificationBody(c: MessageLikeEventContent): String? = when (c) {
        is MessageLikeEventContent.RoomMessage -> when (val t = c.messageType) {
            is MessageType.Image -> t.content.caption?.takeIf { it.isNotBlank() } ?: "Photo"
            is MessageType.Video -> t.content.caption?.takeIf { it.isNotBlank() } ?: "Video"
            is MessageType.Audio -> if (t.content.voice != null) "Voice message" else "Audio"
            is MessageType.File -> "File: ${t.content.filename}"
            is MessageType.Location -> "Location"
            is MessageType.Emote -> "* ${t.content.body}"
            is MessageType.Text -> t.content.body
            is MessageType.Notice -> t.content.body
            is MessageType.Gallery -> "Album"
            is MessageType.Other -> t.body
        }
        is MessageLikeEventContent.Sticker -> "Sticker"
        is MessageLikeEventContent.Poll -> "Poll: ${c.question}"
        is MessageLikeEventContent.RoomEncrypted -> "Encrypted message"
        is MessageLikeEventContent.CallInvite, is MessageLikeEventContent.RtcNotification -> "Call"
        else -> null
    }
}
