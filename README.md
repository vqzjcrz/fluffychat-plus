# Sift

On-device link scanner for Android. Share a URL to Sift (or select one and pick **Scan link with Sift**) and it tells you whether the link itself looks like phishing, an exploit, or malware — **without ever opening, resolving, or uploading it**.

## Two builds

| APK | Network | Use it if |
|---|---|---|
| `Sift-offline.apk` | **None.** The `INTERNET` permission is stripped from the manifest; Android blocks every socket. CI fails the build if it ever sneaks back in. | You want zero network, period. You can still import a blocklist file by hand. |
| `Sift-selfhosted.apk` | One plain `GET` to **your** server for a blocklist text file. Nothing about scanned links is ever sent. | You run the Pi server in `server/` and want the list to stay fresh automatically. |

Both can be installed side by side (`app.sift` and `app.sift.selfhosted`).

## What it checks

- **Fake destinations** — `paypal.com@evil.net`, `paypal.com.secure-login.ru`, decimal/hex/octal IPs (`http://3232235777`), raw IPs, localhost / router / LAN targets, cloud-metadata addresses
- **Lookalikes** — punycode/IDN homographs (Cyrillic `аррӏе.com`), mixed alphabets, typosquats (`paypa1`, `steamcommunlty`), brand names on domains the brand doesn't own (~80 commonly phished brands incl. USAA, Navy Federal, DFAS/myPay, IPPS-A, TRICARE)
- **Hosting** — URL shorteners, IP loggers (Grabify etc.), ngrok/Cloudflare tunnels and dynamic DNS, free site builders, IPFS, high-abuse TLDs, `.zip`/`.mov` domains
- **Hidden characters** — right-to-left override, zero-width chars, tabs/newlines, double and pointless percent-encoding
- **Attack payloads** — XSS, SQLi, command injection, path traversal + sensitive files, SSRF, Log4Shell/JNDI, SSTI/OGNL, CRLF, PHP wrappers, NoSQL, prototype pollution, serialized objects, XXE
- **Downloads** — APK, executables/scripts, disk images, macro docs, config profiles, double extensions (`Invoice.pdf.exe`)
- **Redirects** — unwraps redirect params, base64-encoded targets, Google/Facebook/Outlook SafeLinks/Proofpoint wrappers, `intent://` fallback URLs, URLs glued into paths — and scans the destination too
- **Schemes** — `javascript:` (incl. `javascript://%0a…`), `data:text/html`, `intent://`, USSD codes in `tel:`, `ms-msdt:` and other abused handlers
- **Privacy** — tracking params (one-tap **Copy clean link**), email addresses embedded in the link (plain or base64)
- **Your blocklist** — hosts files, plain domain lists, AdBlock `||domain^`, full URLs

Uses the full Public Suffix List (bundled, refreshed on each CI build) to find the real owner domain, and highlights it in the UI.

Also handy for IOC work: it accepts defanged input (`hxxps[://]evil[.]com`) and has **Copy defanged** / **Share report** (report uses the defanged link).

**Limits:** it judges the link, not the page. Because it never visits anything, it can't see where a shortener goes or what a clean-looking page asks for.

## Build (GitHub Actions)

Same flow as Hush:

1. Create a **private** repo (e.g. `Sift`) and push this folder to `main`.
2. Actions → **Build APKs** runs tests, builds both APKs, verifies the offline one has no network permission, and publishes a Release `build-N` with `Sift-offline.apk` and `Sift-selfhosted.apk`.
3. Download on the Pixel → install (allow "Install unknown apps" for your browser/Files once).

No Gradle wrapper jar is committed; CI provides Gradle 8.11.1. Locally, open in Android Studio or run `gradle assembleOfflineRelease` with Gradle 8.11+ and JDK 17.

### Signing

`keystore/sift-release.jks` (alias `sift`, password `siftsift`) is a personal key so every CI build signs identically and updates install over the old version. It's fine in a private repo. To use your own key instead, add repo secrets `SIFT_KEYSTORE_BASE64` (`base64 -w0 your.jks`), `SIFT_KEYSTORE_PASSWORD`, `SIFT_KEY_ALIAS`, `SIFT_KEY_PASSWORD` — but switching keys means uninstalling the old build once.

## Self-hosted blocklist (Pi)

`server/` has everything; only Python 3 + curl needed. The **Pi** pulls public feeds (URLhaus, ThreatFox, Phishing Army, OpenPhish, Block List Project); the phone only talks to the Pi.

```bash
sudo mkdir -p /opt/sift && sudo cp server/* /opt/sift/ && sudo chown -R pi:pi /opt/sift
/opt/sift/build-blocklist.sh /opt/sift/www          # first build
sudo cp /opt/sift/*.service /opt/sift/*.timer /etc/systemd/system/
# optional: set Environment=SIFT_TOKEN=<random> in sift-blocklist.service
sudo systemctl daemon-reload
sudo systemctl enable --now sift-blocklist.service sift-blocklist-update.timer
```

Then in the self-hosted app: Settings → Blocklist URL `http://<pi-ip-or-tailscale-name>:8088/blocklist.txt` (+ token if set) → **Sync now**. Turn on **Sync on launch** to refresh when the list is >24h old.

Put your own IOCs in `/opt/sift/local-additions.txt`; they're merged on every rebuild.

Plain http is allowed in the self-hosted build so a LAN/Tailscale Pi works without certificates. Over the open internet, put it behind HTTPS.

## Layout

```
app/src/main/java/app/sift/engine/   pure-Kotlin analyzer (no Android deps, unit-tested)
app/src/main/java/app/sift/          activity, view model, blocklist storage/sync
app/src/main/java/app/sift/ui/       Compose UI
app/src/offline/AndroidManifest.xml  strips INTERNET
app/src/selfhosted/AndroidManifest.xml adds INTERNET (+cleartext for LAN)
server/                              Pi blocklist builder + server + systemd units
```
