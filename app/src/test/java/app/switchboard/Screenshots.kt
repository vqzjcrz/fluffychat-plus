package app.switchboard

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.switchboard.core.Bridges
import app.switchboard.core.PlusTypes
import app.switchboard.core.Prefs
import app.switchboard.core.RoomSummary
import app.switchboard.core.Sb
import app.switchboard.core.SpaceChildRef
import app.switchboard.ui.LocalNav
import app.switchboard.ui.LoginScreen
import app.switchboard.ui.Nav
import app.switchboard.ui.chat.Body
import app.switchboard.ui.chat.MessageCallbacks
import app.switchboard.ui.chat.MessageItem
import app.switchboard.ui.chat.ReactionGroup
import app.switchboard.ui.chat.ReplyPreview
import app.switchboard.ui.chat.TEvent
import app.switchboard.ui.home.HomeScreen
import app.switchboard.ui.home.LocalRoomUi
import app.switchboard.ui.home.RoomUi
import app.switchboard.ui.settings.SettingsStyleScreen
import app.switchboard.ui.theme.Sbt
import app.switchboard.ui.theme.SwitchboardTheme
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.LazyTimelineItemProvider
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeContent
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.TextMessageContent
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.UserProfile
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens with sample data to PNGs in build/screens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class Screenshots {
    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun room(
        id: String, name: String, preview: String? = null, sender: String? = null, minsAgo: Long = 5,
        unread: Long = 0, mention: Boolean = false, direct: Boolean = false, space: Boolean = false,
        muted: Boolean = false, heroes: List<String> = emptyList(), members: Long = 6, canSend: Boolean = true,
    ) = RoomSummary(
        id = id, realName = name, avatarUrl = null, isSpace = space, isDirect = direct, membership = Membership.JOINED,
        notificationCount = unread, highlightCount = if (mention) 1 else 0, unreadMessages = unread, unreadMentions = if (mention) 1 else 0,
        markedUnread = false, notificationMode = if (muted) RoomNotificationMode.MUTE else null, lastTs = now - minsAgo * 60_000,
        preview = preview, previewSender = sender, previewIsOwn = sender == "You", isEncrypted = false, canSendMessages = canSend,
        canEditSpace = true, canPin = true, joinedCount = members, topic = null, alias = null, heroIds = heroes,
        serviceMembers = emptyList(), inviterName = null, isFavourite = false, isLowPriority = false, directUserId = null,
    )

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Sb.context = app
        Prefs.init(app)
        val rooms = listOf(
            room("!reels", "Reels", "Maria · DM — no way 😂", "Reels hub", 2, unread = 3),
            room("!maria", "Maria Lopez", "did you see the reel I sent?", "Maria", 5, unread = 1, direct = true, members = 2, heroes = listOf("@instagram_1:hs")),
            room("!squad", "Squad Chat", "@you range day moved to Friday", "Jay", 18, unread = 2, mention = true, heroes = listOf("@signal_a:hs")),
            room("!ann", "announcements", "Patch 4.2 notes are up", "Mod", 60, heroes = listOf("@discord_1:hs"), members = 340),
            room("!alex", "Alex", "sounds good, see you there", "You", 180, direct = true, members = 2),
            room("!lab", "Home Lab Alerts", "ntfy: Paperless upload finished", "ntfy", 300, muted = true),
            room("!dad", "Dad", "Call me when you get a chance", "Dad", 60 * 30, direct = true, members = 2),
            room("!general", "general", "anyone on tonight?", "Kev", 3, unread = 12, heroes = listOf("@discord_2:hs"), members = 340),
            room("!lfg", "lfg", "@you need one more", "Sam", 9, unread = 1, mention = true, heroes = listOf("@discord_3:hs")),
            room("!clips", "clips", "clip from last night", "Ana", 120, heroes = listOf("@discord_4:hs")),
            room("!rules", "rules", "Read before posting", "Mod", 60 * 24 * 3, heroes = listOf("@discord_5:hs")),
            room("!gaming", "Gaming Hub", space = true, members = 340),
            room("!work", "Work", space = true, members = 12),
            room("!fam", "Family", space = true, members = 5),
        )
        val summaries = rooms.associateBy { it.id }
        val order = rooms.map { it.id }
        val children = mapOf(
            "!gaming" to listOf("!ann", "!general", "!lfg", "!clips", "!rules").map { SpaceChildRef(it, null, false) },
            "!fam" to listOf("!dad").map { SpaceChildRef(it, null, false) },
        )
        Sb.matrix.setSampleState(summaries, order, children, UserProfile("@you:hs", "You", null, null, null))
        val prefs = JSONObject()
            .put("pins", JSONObject().put("!gaming", org.json.JSONArray().put("!ann")))
            .put("categories", JSONObject().put("!gaming", org.json.JSONArray()
                .put(JSONObject().put("id", "c1").put("name", "Info").put("collapsed", false))
                .put(JSONObject().put("id", "c2").put("name", "Squad").put("collapsed", false))))
            .put("category_of", JSONObject().put("!gaming", JSONObject().put("!rules", "c1").put("!general", "c2").put("!lfg", "c2").put("!clips", "c2")))
            .put("chats_pos", JSONObject().put("!gaming", 2))
        Sb.matrix.accountData.setSample(mapOf(PlusTypes.SPACE_PREFS to prefs))
        listOf("!ann", "!general", "!lfg", "!clips", "!rules").forEach { Bridges.setSample(it, "discord", "Discord") }
        Bridges.setSample("!gaming", "discord", "Discord")
        Bridges.setSample("!squad", "signal", "Signal")
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { content() }
        rule.waitForIdle()
        val view = (rule.onRoot().fetchSemanticsNode().root as androidx.compose.ui.platform.ViewRootForTest).view.rootView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Providers(nav: Nav = Nav(), content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalNav provides nav, LocalRoomUi provides RoomUi()) {
            SwitchboardTheme { Box(Modifier.fillMaxSize().background(Sbt.c.background)) { content() } }
        }
    }

    @Test fun login() = shoot("1_login") { SwitchboardTheme { LoginScreen() } }

    @Test fun chats() = shoot("2_chats") { Providers { HomeScreen() } }

    @Test fun space() {
        val nav = Nav().apply { selectedSpace.value = "!gaming" }
        shoot("3_space") { Providers(nav) { HomeScreen() } }
    }

    @Test fun styles() = shoot("5_style") { Providers { SettingsStyleScreen() } }

    private fun ev(key: String, sender: String, name: String, text: String, own: Boolean, minsAgo: Long, reactions: List<ReactionGroup> = emptyList(), reply: ReplyPreview? = null): TEvent {
        val content = TimelineItemContent.MsgLike(
            MsgLikeContent(MsgLikeKind.Message(MessageContent(MessageType.Text(TextMessageContent(text, null)), text, false, null)), null, null, null),
        )
        val item = EventTimelineItem(
            isRemote = true, eventOrTransactionId = EventOrTransactionId.EventId("\$$key"), sender = sender,
            senderProfile = ProfileDetails.Ready(name, false, null, null, null), forwarder = null, forwarderProfile = null,
            isOwn = own, isEditable = own, content = content, reactions = emptyList(), eventTypeRaw = null,
            timestamp = (now - minsAgo * 60_000).toULong(), localSendState = null, editSendState = null, redactionSendState = null,
            redactionReason = null, localCreatedAt = null, readReceipts = emptyMap(), origin = null, canBeRepliedTo = true,
            lazyProvider = LazyTimelineItemProvider(NoHandle),
        )
        return TEvent(key, item, "\$$key", item.eventOrTransactionId, sender, name, null, own, now - minsAgo * 60_000,
            Body.Text(text, null), reactions, reply, false, false, false, if (own) listOf("@jay:hs") else emptyList(), true, own, 0)
    }

    @Test fun chat() {
        val events = listOf(
            ev("1", "@jay:hs", "Jay", "Range day got moved 👀 @you it's Friday now, 0700", false, 30),
            ev("2", "@jay:hs", "Jay", "bring eye pro", false, 29),
            ev("3", "@you:hs", "You", "copy, I'll bring the extra mags", true, 25, listOf(ReactionGroup("👍", 3, false, emptyList()))),
            ev("4", "@sam:hs", "Sam", "this is literally us", false, 12, reply = ReplyPreview("\$3", "You", "copy, I'll bring the extra mags")),
            ev("5", "@you:hs", "You", "😂", true, 10),
        )
        val cb = MessageCallbacks({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> })
        shoot("4_chat") {
            Providers {
                Column(Modifier.fillMaxSize()) {
                    events.forEachIndexed { i, e -> MessageItem(e, events.getOrNull(i - 1), events.getOrNull(i + 1), false, false, cb) }
                }
            }
        }
    }
}
