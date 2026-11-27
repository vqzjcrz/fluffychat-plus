# Switchboard

A Matrix client for Android, built for people who bridge everything into one
place: Discord servers, Signal, Instagram, WhatsApp and the rest. Native
Kotlin and Jetpack Compose on the official
[matrix-rust-sdk](https://github.com/matrix-org/matrix-rust-sdk), with
end-to-end encryption, sliding sync and push notifications through
UnifiedPush.

## Download

Get `switchboard.apk` from [Releases](../../releases) (tags `switchboard-N`).
Builds are for arm64 phones.

With [Obtainium](https://github.com/ImranR98/Obtainium): add this repository's
URL and set **Filter APKs by regular expression** to `switchboard\.apk`.

Switchboard has its own app ID (`app.switchboard`), so it installs next to any
other Matrix app.

## First start

1. Sign in with your homeserver, username and password (or single sign-on).
2. Open **Settings → Security and encryption** and enter your recovery key, or
   verify from another logged-in device, to read older encrypted messages.
3. Your chats, spaces, space order, pins, categories, colors, personal chat
   names, read-only choices, snoozes and personal pins come back by themselves:
   they're stored in your Matrix account (see below).

Needs a homeserver with Simplified Sliding Sync (any recent Synapse).

## Notifications

Install a [UnifiedPush](https://unifiedpush.org) app such as
[ntfy](https://ntfy.sh) and pick it in **Settings → Notifications**. Switchboard
registers a pusher on your homeserver that goes through your ntfy server's
Matrix gateway (`/_matrix/push/v1/notify`), so notifications arrive while the
app is closed. Notification Reply and Mark as read work from the shade.

Snoozed chats don't notify until the snooze ends; the check happens on the
phone, so the snooze ends on its own.

## Features

### Spaces

- Spaces in a rail on the left; long-press and drag to reorder them. Your
  profile and settings button sits at the bottom of the rail.
- Per-space sort: **Recent activity first**, **Unread first** or **Custom
  order** (drag the ≡ handles in **Reorder chats**). **Reset chat order** clears it.
- **Pin** chats in a space (long-press, or swipe left). Pins are per space.
- **Categories**: your own headings in a space, like Discord categories.
  Create one from the space's ⋯ menu or while moving chats; collapse, rename,
  move up or down (around the chats without a category) or delete it.
- **Muted** section at the bottom of a space; **Mark all as read**, **Mute all
  chats / Unmute all chats**.
- **Space color**: the space and its chats take that accent color on top of
  your theme. Backgrounds and text keep their contrast.
- **Media** tab: every photo, video and reel shared in the space's chats.
  Tap to view and swipe through; long-press to jump to the message.
- **Members** tab, **Browse rooms in this space** to join rooms you aren't in.
- Opens on the space you had open last, or pick **Open this space on start**.

### Chat lists

- All / Unread / People / Groups tabs, search.
- Unread chats stand out, mentions get a stronger accent and a bar on the left,
  read chats fade back. Last message, time and who's typing under each name.
- Swipe right to mark read or unread, swipe left to pin or unpin.
- Discord channels show as `# channel`; chats you can't post in show a lock.
- **Select** several chats (long-press → Select, or Select all) and **Move to
  space**, **Leave** (declines invites), **Mark as read**, **Mute** or **Move to
  category** at once. When the server says "Too many requests", Switchboard
  waits and keeps going, with a progress bar.
- **Move to space**: from a space you can edit, chats move out of it; from one
  you can't (a bridged Discord server), they're added to the new space and stay.
- **Rename for me**: a name only you see, synced to your account.
- **Snooze**: 1 hour, 8 hours or until 8:00 tomorrow.

### Chats

- Text with Markdown, replies, edits, reactions (quick row plus a full emoji
  picker), deleting, forwarding, copying, links, mentions with `@` suggestions.
- Photos, videos, GIFs, files, voice messages (hold the mic), camera.
- **Reels** from the Instagram bridge and the reels hub show as 9:16 cards and
  play in the viewer. Drag a video or picture down to close the viewer.
- **Pin for me**: pin a message just for yourself, even in read-only chats.
  Pins (yours and the room's) show in a bar under the title.
- **Send later**: hold the send button for 1 hour, 3 hours, tonight, tomorrow
  morning or any date and time. Your homeserver sends it (MSC4140 delayed
  events; on Synapse set `max_event_delay_duration: 30d`). Scheduled messages
  show in a bar where you can send them now or cancel them.
- **Read-only**: where you can't post, the message box is replaced by a note.
  Discord chats start out read-only; switch any chat with **Make read-only /
  Allow posting** in its ⋯ menu.
- The message box says "Message #channel" or "Message Name".
- Swipe left-to-right anywhere in a chat to open a Discord-style panel with
  your spaces and their chats (or switch swipe-to-reply to that direction in
  Settings → Chats).
- Polls (vote), locations, stickers, read receipts, typing, "New" marker, jump
  to replies, date dividers.

### Everything else

- Themes: Mono (default), Midnight, AMOLED, Graphite, Nord, Mocha, Forest,
  Paper and Sand, plus Material You. Text uses Inter.
- Home screen **chat widget**: pick a chat; shows recent messages and the
  unread count (red with @ for mentions). Tap ⇄ to switch it to another chat.
- Share to Switchboard from other apps; open matrix.to links.
- New chats, groups and spaces; join by address or link; invites.
- Chat details: notifications per chat, members, invite, remove or ban,
  edit name, topic and picture.
- Settings: profile name and picture, recovery key and device verification,
  devices (rename, log out), ignored people, notification rules and defaults.

## Synced with your account

Switchboard's own settings live in your Matrix account data, so they're the
same on every device and survive reinstalls:

| Event type | What |
| --- | --- |
| `im.fluffychat.plus.space_prefs` | sort mode, pins, colors and categories per space |
| `im.fluffychat.plus.custom_order` | order of spaces and of chats in spaces |
| `im.fluffychat.plus.snooze` | snoozed chats |
| `im.fluffychat.plus.nicknames` | your own names for chats |
| `im.fluffychat.plus.personal_pins` | messages pinned for you |
| `im.fluffychat.plus.read_only` | read-only choices |

(The event type names are kept from the client this replaced, so nothing had
to be set up again.)

## Building

GitHub Actions builds the APK on every push to the `switchboard` branch
(`.github/workflows/switchboard.yml`) and publishes it as a release. It signs
with the key in the repository secrets `FLUFFYCHAT_KEYSTORE_BASE64` and
`FLUFFYCHAT_KEYSTORE_PASSWORD` (alias `plus`), so builds install over each
other.

Locally: JDK 21, Gradle 8.14 and the Android SDK (platform 35, build tools 35),
then `gradle :app:assembleRelease`. For a machine without network access, run
the workflow with **Also publish the offline dependency bundle**; it uploads
the Gradle cache and SDK parts as a `switchboard-deps-N` pre-release.

## License

Switchboard's code is original. Third-party parts and their licenses are
listed in [licenses/THIRD_PARTY.md](licenses/THIRD_PARTY.md) and in the app
under Settings → About.
