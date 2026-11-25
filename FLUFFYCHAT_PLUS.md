# FluffyChat+

A personal fork of [FluffyChat](https://github.com/krille-chan/fluffychat) (AGPL-3.0)
that adds your own ordering for spaces and for the chats inside each space.

## What's different

- **Reorder spaces:** long-press (or click and hold) a space in the space bar and drag it.
- **Reorder chats inside a space:** open the space, tap its avatar menu → **Reorder chats**,
  drag the ≡ handles, then tap **Done**. **Reset chat order** in the same menu goes back
  to the server's order.
- The order is stored in your Matrix account (account data
  `im.fluffychat.plus.custom_order`), so it's the same on every device running
  FluffyChat+ and survives reinstalls. It doesn't affect anyone else, and you
  don't need admin rights in the space.
- New spaces and chats you haven't placed yet show up after the ones you've arranged.
- Separate app ID (`chat.fluffy.fluffychat.plus`) and name (FluffyChat+), so it
  installs next to the official FluffyChat.

Changed files: `lib/utils/custom_order_extension.dart` (new),
`lib/pages/chat_list/navigation_rail.dart`, `lib/pages/chat_list/navi_rail_item.dart`,
`lib/pages/chat_list/space_view.dart`, `lib/l10n/intl_en.arb`,
`android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`,
`.github/workflows/build-apk.yaml` (upstream workflows removed).

## Building with GitHub Actions

This lives on the `fluffychat-plus` branch of `vqzjcrz/Hush` (`main` there is untouched).
Every push to that branch builds the APK (arm64 only, which covers Pixels and
other modern phones, and keeps the build within the runner's memory) and attaches it to a release named
`FluffyChat+ build <n>` (tag `fluffychat-plus-<n>`).

### Signing key (do once, so updates install over the old version)

Until these secrets exist, each build is signed with a one-off key, and you have to
uninstall FluffyChat+ before installing a newer build.

```bash
keytool -genkeypair -keystore ~/fluffychat-plus.p12 -storetype PKCS12 \
  -alias plus -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=FluffyChat Plus"
gh secret set FLUFFYCHAT_KEYSTORE_BASE64 --repo vqzjcrz/Hush < <(base64 -w0 ~/fluffychat-plus.p12)
gh secret set FLUFFYCHAT_KEYSTORE_PASSWORD --repo vqzjcrz/Hush
```

Keep a backup of `~/fluffychat-plus.p12`. Then re-run the latest build (Actions tab) or push a commit.

## Updating from upstream

```bash
git remote add upstream https://github.com/krille-chan/fluffychat.git
git fetch upstream
git merge --allow-unrelated-histories upstream/main   # first time; resolve conflicts in the files listed above
git push origin fluffychat-plus
```
