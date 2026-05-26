# dropX

Share files from your Android phone over your local network. The receiver opens a browser — nothing to install on their end, no account, no cloud, no middleman.

Lightweight. No ads. No tracking. Everything stays on your network.

---

## Screenshots

---

## The core idea

Most file sharing tools either require both people to have an app, upload your files to someone's server, or make you jump through pairing screens. dropX skips all of that.

You pick files, hit start, share a link or QR code. They open it in Chrome, Safari, Firefox — whatever. Done.

The files never leave your network. No upload step. The phone itself is the server.

---

## Streaming — not just downloading

This is the part that separates dropX from most tools in this category.

The server implements HTTP range requests (partial content). This means:

**In the browser** — videos and audio play directly without downloading the full file first. You can scrub to any point in a video instantly. A 2GB movie works the same as a 10MB clip.

**In external players over the network** — because dropX serves standard HTTP with range support, you can paste the direct file URL into any player that supports network streams. VLC, MX Player, IINA, mpv — open network stream, paste the URL, done. The file plays from your phone in real time with full seek support.

The direct file URL format is:

```
http://<your-ip>:<port>/share/<session-id>/file/<file-id>

```

You get this from the web portal or you can grab it from the share link shown in the app. Any player that can open an HTTP URL will work.

**Downloads** — if someone does want to save the file, the web portal handles it with a streaming download that shows real progress. Resumable if the connection drops mid-transfer.

---

## Web portal

The receiver gets a full file browser in their browser tab. Not a plain directory listing — an actual UI.

* Grid and list view toggle
* Filter by file type — images, video, audio, PDF, other
* Search across file names
* Thumbnail previews for images and video
* Click any file to preview it inline — images, video with controls, audio player, PDF viewer
* Navigate between files with arrow keys
* Select multiple files and batch download
* QR code on the sender side for quick sharing to phones

The whole portal is a single HTML file bundled inside the APK. No CDN calls for the UI itself, fonts aside.

---

## How the server works

The app runs an embedded Ktor CIO HTTP server inside a foreground service. CIO is non-blocking — multiple receivers downloading different files simultaneously don't block each other.

Wake lock and WiFi lock are held for the duration of the session so the server doesn't die when the screen turns off.

Files are served directly from their original content URIs through Android's ContentResolver. Nothing is copied or moved — the file stays exactly where it is on the device.

Each session gets a random ID that becomes part of every URL. Without that ID, you can't enumerate files or access anything on the server. It's not authentication but it's not nothing either.

Thumbnails are generated on demand and cached — LRU memory cache backed by a disk cache with a 50MB quota and automatic pruning. Concurrent thumbnail generation is limited to 3 at a time so it doesn't hammer the device.

---

## Hotspot support

dropX explicitly detects hotspot IP addresses separately from WiFi. This matters because the most common use case — "I want to share something with the person next to me right now" — usually means turning on your hotspot, not finding a shared router.

The network detection reads actual interface names (`ap*`, `swlan*`, `softap*` for hotspot, `wlan*` for WiFi) and surfaces whichever is available. If both are up, you can pick which one to bind to.

---

## Tech stack

* Kotlin + Jetpack Compose
* Ktor CIO — embedded HTTP server with partial content, conditional headers, auto head response
* Kotlinx Serialization — file metadata served as JSON to the web portal
* ZXing — QR code generation
* Android ContentResolver + DocumentFile for file access and metadata
* Custom thumbnail pipeline — no Glide, no Coil, hand-rolled LRU + disk cache with quota management
* Material3 with full dark mode support

---

## Security model

This is a local network tool and the security model reflects that. Honest about the tradeoffs:

**HTTP not HTTPS** — the receiver would get a self-signed cert warning in their browser that most people don't know how to bypass. That friction defeats the purpose of zero-install sharing. HTTP on a local network is an accepted tradeoff.

**Session ID is 6 characters** — this is not a strong secret. On a local network it's theoretically brute-forceable. For the use case (quick sharing to someone you're physically near) it's sufficient. It prevents casual stumbling, not a determined attacker on the same network.

**No authentication** — anyone with the URL can access the files for the duration of the session. Stopping the server ends the session and clears everything.

**Read-only** — receivers can only download. There is no upload endpoint. The server exposes no write surface.

**Nothing leaves the device** — no telemetry, no analytics, no crash reporting, no ads SDK, nothing phoning home.

---

## Building

```bash
git clone https://github.com/flu1d3v/dropX.git

```

Open in Android Studio. No API keys or external config needed to build and run.

```
Min SDK:    29  (Android 10)
Target SDK: 36
Language:   Kotlin

```

The OWASP dependency check is configured but requires an NVD API key in `local.properties` to run. The build works fine without it — it just skips the vulnerability scan.

```
# local.properties (not committed)
nvd.api.key=your_key_here

```

---

## What it doesn't do

* Upload from receiver to phone — one direction only
* Cross-internet sharing — local network only
* Persistent file registry — stopping the server clears everything, intentionally
* iOS sender — Android only on the sending side, any browser on the receiving side

---

## License

MIT