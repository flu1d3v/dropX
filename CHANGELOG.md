# Changelog

## 1.1.0 (November 2026)

### New
- Copy link on every file, in the app (⋮ menu) and in the web portal, to open it in VLC or another media player.
- Clear all button on the Files screen.
- Files keep the order you added them, and the same file can't be added twice.
- Light and dark theme cover every screen.

### Changed
- Seeking in large files no longer reads the file from the start.
- Downloads go to the browser's download manager, so large files don't need browser memory.
- Stop Server keeps your file list.
- Faster app start (no splash screen).
- Video thumbnails use less memory, and the app uses one shared thumbnail cache.
- The web portal no longer loads a font from Google.

### Fixed
- The share link now shows the address the server is really using.
- Sharing no longer stops by mistake when a second network comes up. If the address really disappears, sharing stops and a notification is shown.
- On Android 15 and newer, the 6-hour background limit no longer crashes the app. Sharing pauses and can be resumed with the same link.
- Files whose size can't be read are skipped, with a message.
- A browser that can't play a format now says so and suggests Copy link and a media player.

### Security
- Session IDs are 16 characters from a secure random source (were 6).
- Removed the unneeded cleartext-traffic and backup settings.
- File names in download headers are escaped.

## 1.0.1 (May 2026)

### Fixed
- Video thumbnails now generate correctly in the app and the web portal.

## 1.0.0 (May 2026)

- Embedded Ktor HTTP server with range request support
- Video and audio stream in the browser without downloading first
- Direct file URLs work in VLC, MX Player or any player that supports network streams
- Web portal with grid and list view, file type filters, thumbnails and inline preview
- QR code sharing
- Works over Wi-Fi or hotspot
- No ads, no account, no cloud
