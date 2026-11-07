# dropX Privacy Policy

Last updated: 6 November 2026

dropX is a free, open-source Android app that shares files from your phone to other devices on your local network. This
policy explains what the app does with your information. In short: **dropX does not collect, store on a server, sell or
share your personal data.**

## What dropX does

When you tap **Start Server**, dropX runs a small web server on your phone. Devices on the same Wi-Fi network or hotspot that
have the share link can open a web page in their browser and view, stream or download the files you selected. Files are sent
directly from your phone to those devices. Nothing is uploaded to the internet or to any server operated by the developer.

## Data we collect

None. dropX has no accounts, no analytics, no advertising and no tracking, and it sends no data to the developer or to any
third party.

## Data stored on your device

dropX stores a small amount of data only on your own phone, in the app's private storage:

- **Thumbnail cache:** small preview images of the files you chose to share (limited to about 50 MB, and removed when you
  clear the app's storage or uninstall).
- **Paused-session information:** if Android pauses sharing because of its background time limit, dropX temporarily saves the
  network port and session code so you can resume with the same link. It is deleted when you stop the server or start a new
  session.

The list of files you selected is kept in memory only. dropX does not copy your files; it reads them from their original
location when another device requests them.

## Who can access your files

While the server is running, **anyone on the same network who has your share link can view and download the files you
selected.** The link contains a random 16-character session code. Files are served over unencrypted HTTP, so use trusted
networks only (for example, your home Wi-Fi or your own hotspot) and stop the server when you are finished.

## Permissions

| Permission | Why dropX needs it |
|---|---|
| Internet (network access) | To run the local web server and accept connections from devices on your network. dropX does not connect to the internet by itself. |
| Foreground service (data sync) | To keep the server running with a visible notification while you share files. |
| Notifications | To show the "server is active" notification with a Stop button, and alerts when sharing pauses or stops. |
| Wake lock | To keep the phone's processor and Wi-Fi active while files are being transferred. |

dropX does not request access to your contacts, location, camera, microphone or storage permissions. Files are selected
through Android's system file picker, which grants access only to the files you choose.

## Third parties

dropX contains no third-party analytics, advertising or crash-reporting code. If you have chosen to share usage and
diagnostics data with Google, Google Play may provide the developer with anonymous, aggregated crash and performance
statistics; dropX itself does not collect or send this information.

## Children

dropX is not directed at children and collects no personal information from anyone.

## Open source

The source code is available at https://github.com/flu1d3v/dropX under the MIT license, so you can verify how the app works.

## Changes to this policy

If this policy changes, the new version will be published at the same location with an updated date.

## Contact

Questions or concerns: please open an issue at https://github.com/flu1d3v/dropX/issues or email **flu1d3v@yahoo.com**.
