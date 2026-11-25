<!--
SPDX-FileCopyrightText: 2019-Present Christian Kußowski
SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# FluffyChat+

An unofficial fork of [FluffyChat](https://github.com/krille-chan/fluffychat), the open
source [Matrix](https://matrix.org) client by Christian Kußowski. FluffyChat+ adds
better space organization (sorting, pinning, reordering), snoozing, read-only chat
handling, preset themes and a home screen chat widget.

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

### Chat rows in a space

- **Unread highlight:** chats with unread messages get a brighter background. Chats
  where someone mentioned or replied to you get a stronger accent and a bar on the left.
- **Preview line:** the last message and how long ago it was sent (`5m ago`) under
  each chat name. Shows who's typing too.
- **Swipe actions:** swipe right to mark read or unread, swipe left to pin or unpin.
- **Lock icon** on chats you can't post in.

### Everywhere

- **Snooze a chat:** long-press it and choose **Snooze**, then 1 hour, 8 hours or
  until 8:00 tomorrow. Notifications from it stop until then and come back on their
  own. Snoozing doesn't change your push rules on the server: the app drops the
  notifications on the device, so the snooze ends even if you don't open the app.
- **Read-only chats:** where you can't post (for example a Signal group where only
  admins can send, or a read-only bridged Discord channel), the message box is
  replaced by a note. In Discord chats, **Send a command** brings the box back so
  you can still send bridge commands.
- **Preset themes:** Settings → Style → **App theme**: AMOLED Black, Nord, Dracula,
  Catppuccin, Gruvbox, Solarized, Tokyo Night, Rosé Pine, Everforest and One Dark.
  Each has a light and a dark variant except AMOLED Black, which is dark only and
  keeps your accent color. Picking an accent color switches back to Default.

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

Sort modes, pins, custom order and snoozes are saved in your Matrix account data
(`im.fluffychat.plus.space_prefs`, `im.fluffychat.plus.custom_order`,
`im.fluffychat.plus.snooze`), so they're the same on every device running FluffyChat+
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
- `lib/config/theme_presets.dart`: the preset themes
- `lib/utils/home_widget_sync.dart`: sends chats and messages to the home screen widgets
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
