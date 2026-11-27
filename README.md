<!--
SPDX-FileCopyrightText: 2019-Present Christian Kußowski
SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# FluffyChat+

An unofficial fork of [FluffyChat](https://github.com/krille-chan/fluffychat), the open
source [Matrix](https://matrix.org) client by Christian Kußowski. FluffyChat+ adds
better space organization (sorting, pinning, reordering), snoozing, read-only chat
handling, a redesigned look with its own themes, Instagram-style reel cards and a home
screen chat widget.

Everything else (messaging, encryption, calls, notifications, settings) is FluffyChat,
unchanged. This fork is not affiliated with or endorsed by the FluffyChat project.
Please report problems with this fork here, not to FluffyChat.

## What it adds

### Spaces

- **Sort modes per space:** tap the space's avatar in the search bar and pick
  **Recent activity first** (the default), **Unread first** (mentions, then unread,
  then the rest) or **Custom order**.
- **Reorder chats:** choose **Reorder chats** in the same menu, drag the ≡ handles,
  then tap **Done**. This switches the space to Custom order. **Reset chat order**
  clears it.
- **Reorder spaces:** long-press a space in the space bar and drag it.
- **Pin chats in a space:** long-press a chat and choose **Pin**, or swipe it left.
  Pinned chats stay in a section at the top of that space. Pins are per space.
- **Muted section:** muted chats collapse into a **Muted** section at the bottom of
  the space. Tap the header to expand it. Searching shows them too.
- **Mark all as read** and **Mute all chats / Unmute all chats** in the space menu.
- **Space colors:** pick **Space color** in a space's menu. The space and the chats in
  it take that accent color, on top of whichever app theme you use.
- **Media gallery:** **Media** in a space's menu shows every photo, video and reel
  shared in its chats, newest first. Tap to view and swipe through them; long-press
  to jump to the message.
- **Opens where you left off:** the app starts on the space you had open last. Pick
  **Open this space on start** in a space's menu to always start on that one instead
  (choose it again to turn it off).

### Chat rows in a space

- **Unread highlight:** chats with unread messages get a brighter background. Chats
  where someone mentioned or replied to you get a stronger accent and a bar on the left.
- **Preview line:** the last message and how long ago it was sent (`5m ago`) under
  each chat name. Shows who's typing too.
- **Swipe actions:** swipe right to mark read or unread, swipe left to pin or unpin.
- **Lock icon** on chats you can't post in.

### Everywhere

- **Swipe right for the spaces panel:** in a chat, swipe from left to right anywhere
  to slide open a Discord-style panel with your spaces and their chats, and jump
  straight to another chat. (Off if you set swipe-to-reply to left-to-right.)
- **Discord-style message box:** the box at the bottom says "Message #channel" (or the
  person's name) instead of "Unencrypted message".
- **Select several chats at once:** long-press a chat and choose **Select**, then tap
  more chats (or **Select all**). The top bar then has **Move to space**, **Leave**
  (declines invites) and, under ⋮, **Mark as read** and **Mute** for all of them. Works
  in the chat list and in spaces. If the server says "Too many requests", it waits and
  keeps going instead of failing, with a progress bar.
- **Move chats to another space:** from the selection bar or a chat's long-press menu.
  Inside a space you can edit, the chats move out of it; from a space you can't edit
  (like a bridged Discord server), they're added to the new space and stay in the old one.
- **Categories in spaces:** your own headings to group a space's chats, like Discord
  categories. Create one from the space's ⋮ menu (**New category**) or while moving a
  chat. Put chats in with **Move to category** (long-press menu, or for many at once from
  the selection bar). Tap a heading to collapse it; long-press it or tap ⋯ to rename,
  reorder or delete it. They're saved to your account, need no permissions in the space
  and only you see them.
- **Rename chats for yourself:** long-press a chat and choose **Rename for me** to give
  it a name only you see, like for cryptic Discord channels. Leave it empty to go back.
  Synced to your account.
- **Pin for me:** select a message and choose **Pin for me** in the ⋮ menu to pin it
  just for you, even in read-only chats like Discord announcement channels. Your pins
  show in a bar under the chat title.
- **Send later:** hold the send button to schedule a message for 1 hour, 3 hours,
  tonight, tomorrow morning or any date and time. Your homeserver sends it, so your
  phone can be off. Scheduled messages show in a bar in the chat, where you can send
  them now or cancel them. Needs delayed events on Synapse: add
  `max_event_delay_duration: 30d` to homeserver.yaml.

- **Snooze a chat:** long-press it and choose **Snooze**, then 1 hour, 8 hours or
  until 8:00 tomorrow. Notifications from it stop until then and come back on their
  own. Snoozing doesn't change your push rules on the server: the app drops the
  notifications on the device, so the snooze ends even if you don't open the app.
- **Read-only chats:** where you can't post (for example a Signal group where only
  admins can send), the message box is replaced by a note. Discord chats start out
  read-only too. Switch any chat you're allowed to post in with **Make read-only** /
  **Allow posting** in its ⋮ menu or long-press menu, or **Allow posting** on the note.
  Your choice is saved to your account.
- **Redesign ("Mono"):** pure black by default, with big bold titles, thin dividers and
  color only where something needs attention: unread chats, mentions and your own
  messages. Chats you've read fade to gray. Spaces fade to gray in the rail except the
  open one, which gets an accent bar and a ring. Your profile and settings button sits at
  the bottom of the spaces rail. Text uses Inter (bundled, SIL Open Font License).
- **Themes:** Settings → Style → **App theme**: Mono (default), Midnight, AMOLED,
  Graphite, Nord, Mocha, Forest, Paper and Sand. Each theme sets its own bubble, link and
  badge colors, and every text color is checked against what it sits on (at least 4.5:1
  contrast). **Material You** uses your chosen chat color instead.

### Instagram reels

- **Reel cards in chats:** reels from the Instagram bridge (and the reels hub) show as
  tall 9:16 cards with who sent them, the caption and a play button, like a reel
  shared in an Instagram DM. Tap one to play it in the normal media viewer.
- **Swipe down to close:** drag a video down in the media viewer to close it. Let go
  early and it springs back.

### Home screen widget

- **Chat widget:** add **FluffyChat+ chat** from your launcher's widget list and pick a
  chat. The widget shows that chat's recent messages and unread count (red when you're
  mentioned). Tap it to open the chat.
- **One chat per widget, changeable any time:** add as many widgets as you like, each
  with its own chat. Tap the ⇄ button on a widget (or long-press it and choose
  **Reconfigure** on Android 12+) to switch it to another chat. No rebuild needed.
- Widgets update while the app is open, when a notification arrives, and when you
  leave the app. Messages are stored only in the app's private storage on the phone.
- Colors follow your wallpaper (Material You) on Android 12 and newer.

### Synced with your account

Sort modes, pins, custom order, space colors, personal chat names, personal pins and
snoozes are saved in your Matrix account data
(`im.fluffychat.plus.space_prefs`, `im.fluffychat.plus.custom_order`,
`im.fluffychat.plus.snooze`, `im.fluffychat.plus.nicknames`,
`im.fluffychat.plus.personal_pins`), so they're the same on every device running FluffyChat+
and survive reinstalls. Only you see them, and you don't need admin rights in the space.

The app has its own ID (`chat.fluffy.fluffychat.plus`), so it installs next to the
official FluffyChat instead of replacing it.

## Download

Get `fluffychat-plus.apk` from [Releases](../../releases). Builds are for arm64
Android phones (Pixels and most phones from the last several years).

To get updates automatically with [Obtainium](https://github.com/ImranR98/Obtainium),
add this repository's URL and set **Filter APKs by regular expression** to
`fluffychat-plus\.apk`.

For iOS, desktop and web, use [FluffyChat](https://fluffychat.im) itself.

## Building

GitHub Actions builds the APK on every push to the `fluffychat-plus` branch
(`.github/workflows/build-apk.yaml`) and publishes it as a release.

Builds are signed with the key stored in the repository secrets
`FLUFFYCHAT_KEYSTORE_BASE64` (base64 of a PKCS12 keystore with key alias `plus`) and
`FLUFFYCHAT_KEYSTORE_PASSWORD`. Without them, a one-off key is used, and that build
won't install over a previous one.

To build locally you need [Flutter](https://flutter.dev) (version in `.fvmrc`) and
[Rust](https://www.rust-lang.org/tools/install):

```bash
flutter pub get
flutter build apk --release --target-platform android-arm64
```

### Changes from FluffyChat

New files:

- `lib/utils/custom_order_extension.dart`: custom order of spaces and chats
- `lib/utils/space_prefs_extension.dart`: sort mode and pins per space
- `lib/utils/snooze_extension.dart`: snoozed chats, plus a local copy for the push handler
- `lib/utils/room_plus_extension.dart`: read-only and Discord detection, mark all read
- `lib/widgets/snooze_dialog.dart`: the snooze picker
- `lib/pages/chat/read_only_composer_note.dart`: the note that replaces the message box
- `lib/config/theme_presets.dart`, `lib/config/plus_colors.dart`: themes and design colors
- `lib/pages/chat_list/navigation_rail.dart`, `navi_rail_item.dart`: redesigned rail
- `lib/widgets/plus_circle_button.dart`, `plus_tab.dart`: redesign building blocks
- `assets/fonts/`: Inter
- `lib/utils/home_widget_sync.dart`: sends chats and messages to the home screen widgets
- `lib/utils/instagram_reel.dart`, `lib/pages/reels/reel_bubble.dart`: reel cards
- `lib/utils/chat_nickname_extension.dart`, `lib/widgets/rename_for_me_dialog.dart`: personal chat names
- `lib/widgets/space_accent_theme.dart`: space colors
- `lib/pages/chat_list/space_media_gallery.dart`: space media gallery
- `lib/utils/personal_pins_extension.dart`, `lib/pages/chat/personal_pinned_events.dart`: personal pins
- `lib/utils/scheduled_messages.dart`, `lib/widgets/send_later_dialog.dart`: send later
- `lib/pages/chat/spaces_panel.dart`: swipe-open spaces panel
- `lib/widgets/chat_selection_bar.dart`: multi-select bulk actions and move to space
- `lib/utils/rate_limit_retry.dart`: waits out "Too many requests" during bulk actions
- `lib/utils/read_only_override_extension.dart`: per-chat read-only switch
- `lib/pages/image_viewer/`: swipe down to close videos
- `android/app/src/main/kotlin/chat/fluffy/fluffychat/ChatWidget.kt`,
  `ChatWidgetConfigureActivity.kt` and `android/app/src/main/res/*/fcp_*`: the widget
  and its chat picker

Changed files:

- `lib/pages/chat_list/space_view.dart`: sort modes, sections, pins, swipe actions,
  highlights, previews, space menu actions
- `lib/pages/chat_list/navigation_rail.dart`, `navi_rail_item.dart`: drag-to-reorder space bar
- `lib/pages/chat_list/chat_list.dart`, `chat_list_item.dart`: snooze in the chat menu,
  lock and snooze icons
- `lib/pages/chat/chat.dart`, `chat_view.dart`: read-only note
- `lib/pages/chat/events/message.dart`, `message_content.dart`: reel cards
- `lib/utils/push_helper.dart`, `lib/widgets/local_notifications_extension.dart`,
  `lib/widgets/matrix.dart`: drop notifications from snoozed chats, update widgets
- `android/app/src/main/kotlin/chat/fluffy/fluffychat/MainActivity.kt`,
  `AndroidManifest.xml`: widget channel and components
- `lib/utils/client_manager.dart`: loads bridge info at startup
- `lib/config/themes.dart`, `setting_keys.dart`, `lib/widgets/theme_builder.dart`,
  `lib/pages/settings_style/`: preset themes
- `lib/l10n/intl_en.arb`: strings for the above
- `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`: app ID and name
- `.github/workflows/`: FluffyChat's workflows replaced by the APK build

### Pulling in FluffyChat updates

```bash
git remote add upstream https://github.com/krille-chan/fluffychat.git
git fetch upstream
git merge --allow-unrelated-histories upstream/main   # first time; then a plain merge
git push origin fluffychat-plus
```

## Credits

FluffyChat+ is built entirely on **[FluffyChat](https://github.com/krille-chan/fluffychat)**
by **[Christian Kußowski (krille-chan)](https://github.com/krille-chan)** and the
[FluffyChat contributors](https://github.com/krille-chan/fluffychat/graphs/contributors).
All of the app except the changes listed above is their work.

From the FluffyChat project's own thanks:

- [Fabiyamada](https://github.com/fabiyamada) made the FluffyChat logo and banner.
- The translators and testers who made FluffyChat available in many languages.
- The Matrix Foundation for the
  [emoji translations](https://github.com/matrix-org/matrix-spec/blob/main/data-definitions/sas-emoji.json)
  used for emoji verification (Apache 2.0).
- MTRNord, Sorunome and Advocatux.

If you like this app, support the original developer:
[Liberapay](https://de.liberapay.com/KrilleChritzelius) ·
[Ko-fi](https://ko-fi.com/krille) · [fluffychat.im](https://fluffychat.im)

## License

FluffyChat+, like FluffyChat, is licensed under the
[GNU Affero General Public License v3.0 or later](LICENSE). The source for every build
is this repository.
