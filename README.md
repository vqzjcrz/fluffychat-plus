<!--
SPDX-FileCopyrightText: 2019-Present Christian Kußowski
SPDX-FileCopyrightText: 2019-Present Contributors to FluffyChat

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# FluffyChat+

An unofficial fork of [FluffyChat](https://github.com/krille-chan/fluffychat), the open
source [Matrix](https://matrix.org) client by Christian Kußowski. FluffyChat+ is
FluffyChat with one addition: you can put your **spaces** and the **chats inside each
space** in your own order instead of the default order.

Everything else (messaging, encryption, calls, notifications, settings) is FluffyChat,
unchanged. This fork is not affiliated with or endorsed by the FluffyChat project.
Please report problems with this fork here, not to FluffyChat.

## What it adds

- **Reorder spaces:** long-press (or click and hold) a space in the space bar and drag it.
- **Reorder chats inside a space:** open the space, tap its avatar in the search bar,
  choose **Reorder chats**, drag the ≡ handles, then tap **Done**.
  **Reset chat order** in the same menu returns to the default order.
- **Synced with your account:** the order is saved in your Matrix account data
  (`im.fluffychat.plus.custom_order`), so it's the same on every device running
  FluffyChat+ and survives reinstalls. Only you see it, and you don't need admin
  rights in the space.
- Spaces and chats you haven't placed yet appear after the ones you've arranged.
- Separate app ID (`chat.fluffy.fluffychat.plus`), so it installs next to the official
  FluffyChat instead of replacing it.

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

- `lib/utils/custom_order_extension.dart` (new): stores and applies the custom order
- `lib/pages/chat_list/navigation_rail.dart`: drag-to-reorder space bar
- `lib/pages/chat_list/navi_rail_item.dart`: tooltip no longer steals the long press
- `lib/pages/chat_list/space_view.dart`: reorder mode for chats in a space
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
