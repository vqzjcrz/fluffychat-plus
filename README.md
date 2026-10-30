# Hush

A quiet, minimal Discord client for Android, built with Kotlin and Jetpack Compose.

## Features

- Servers, channels, DMs and group DMs, with permission-aware channel lists
- Real-time messages over the gateway (zlib-compressed, auto-resume, backoff)
- Discord markdown: bold, italic, underline, strikethrough, spoilers (tap to reveal),
  inline code, code blocks, quotes, headings, masked links, mentions, timestamps
- Custom emoji (animated too), stickers, image attachments, link embeds, reactions
- Replies (with @ ping toggle), edit, delete, copy text/link, quick reactions
- Image/video uploads (up to 10 per message), typing indicators, unread dots and mention badges
- Read-state sync with your other devices (can be turned off)
- Image viewer with pinch-to-zoom

### Customization
- 12 themes: Material You (Android 12+), Midnight, AMOLED, Classic, Nord, Dracula,
  Mocha, Gruvbox, Rosé Pine, Tokyo Night, Paper, Solarized
- Accent color override (9 swatches or the theme's own)
- Cozy or compact message layout
- Circle, rounded or square avatars
- Text size 85–130%
- Toggles for avatars, 24-hour time, image previews, embeds, GIF/emoji animation,
  typing indicator and read sync
- Live preview at the top of Settings

## Build

1. Install Android Studio (Ladybug or newer).
2. **File → Open** and choose this `Hush` folder. Let Gradle sync (it downloads Gradle 8.11.1,
   AGP 8.7.3 and the libraries on first run).
3. Plug in your phone with USB debugging on and press **Run**, or use
   **Build → Build APK(s)** and sideload `app/build/outputs/apk/debug/app-debug.apk`.

Command line: `./gradlew assembleDebug` (needs a JDK 17+ and the Android SDK; Android Studio's
bundled JDK works).

The release build (`./gradlew assembleRelease`) is minified and signed with the debug key so it
installs directly.

## Signing in

Tap **Log in with Discord**. Discord's own login page opens inside Hush; sign in as usual
(email/phone, password, 2FA and captcha all work). Hush picks up your session token
automatically, stores it encrypted with an Android Keystore key, and wipes the login page's
cookies and storage afterward. Your password is never stored.

Prefer pasting a token? Tap **Use a token instead**. On a computer, open discord.com, press
F12 → **Network**, open a channel, filter for `messages`, select a request, and copy the
`authorization` request header.

Anyone with your token has full access to your account, so never share it. Logging out of
Discord elsewhere doesn't sign Hush out; changing your password does.

## Limitations

- No voice/video calls, threads, or forum channels (those are hidden).
- No push notifications. Discord's push service only talks to the official app, so Hush is
  connected while it's open and for about 90 seconds after you leave it.
- Server folders and ordering from your Discord settings aren't applied.

## Disclaimer

Third-party clients are against Discord's Terms of Service. Hush only does what a normal user
does (no automation), but use it at your own risk. Hush is not affiliated with Discord.

## Project layout

```
app/src/main/java/dev/hush/client/
  HushApp.kt, MainActivity.kt
  data/        TokenStore (encrypted token), Settings (DataStore)
  discord/     Models, Rest, Gateway, Repository, Cdn
  ui/          AppViewModel, Root, Format
    theme/     Palettes and HushTheme
    components/ Avatars, icons, badges
    login/ home/ chat/ settings/
```
