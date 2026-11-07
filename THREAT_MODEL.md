# dropX Threat Model (STRIDE)

> This document was written before and during development and has been updated for v1.1.0. Not everything planned here got implemented — reasons are noted honestly per row. The intent is to keep this as a living record of what was thought about, what shipped, and what didn't.

---

## 1. Threat Matrix

| Letter | Threat | What it means for dropX | Planned Mitigation | Status | Reason if not implemented |
| --- | --- | --- | --- | --- | --- |
| **S** | Spoofing | Can someone on the network pretend to be your phone and intercept file requests? | OOB QR Handshake — share a high-entropy session path via QR so the receiver knows they're talking to the right server | ⚠️ Partial | Since v1.1.0 the session path is a 16-character random ID (64 random bits from `SecureRandom`), shared through the link and the QR code, so it can't be guessed or found by scanning. That covers the high-entropy part of the plan. What is still missing is a way for the receiver to verify the server's identity: the QR code only encodes the URL, and without HTTPS an active attacker on the network can sit in the middle. Accepted risk for local network use. See the open problem in CONTRIBUTING.md. |
| **T** | Tampering | Can someone modify the file in transit between the phone and browser? | SHA-256 hash of each file served alongside it so the receiver can verify integrity | ❌ Not implemented | No checksum is generated or served. Files are streamed directly from ContentResolver. On a trusted local network this was deprioritised. Can be added as a response header (`X-Content-SHA256`) in a future version. |
| **R** | Repudiation | Can someone claim they never downloaded a file? | Local read-only audit log with timestamps and requester IPs, rotated weekly | ❌ Not implemented | No logging is implemented on the server. Ktor routes have no request logging middleware. Out of scope — the use case doesn't require accountability tracking. |
| **I** | Info Disclosure | Can a random person on the same WiFi see your files? | 4-digit PIN challenge shown only on sender's screen + one-time-use ephemeral tokens | ⚠️ Partial | No PIN and no one-time tokens. The 16-character session ID means someone on the same Wi-Fi can't find a share by guessing or scanning. But anyone who has the link (including someone watching unencrypted HTTP traffic) can read the shared files until the server is stopped. Stopping the server ends the session and a new start creates a new ID. Resuming after Android's background time limit keeps the same ID on purpose. Accepted tradeoff: a PIN adds friction that conflicts with the zero-friction receiver goal. |
| **D** | DoS | Can someone on the network saturate your phone's bandwidth by hammering simultaneous large file downloads, draining battery through sustained radio activity? | Hard limit on concurrent connections via Ktor engine config | ❌ Not implemented | No connection limit or rate limiting is configured. The primary strain is on bandwidth — sustained parallel downloads keep the WiFi/hotspot radio active at full power which directly hits battery. CPU impact is low since Ktor CIO is non-blocking and file serving is streaming from ContentResolver. The thumbnail semaphore (max 3 concurrent) limits generation load, and since v1.1.0 thumbnail work runs on the IO pool so it can't block streaming threads, but neither limits download concurrency. The service shows a notification with a Stop button, and sharing stops by itself if the network address disappears. Local network threat, accepted. |
| **E** | Elevation | Can a receiver exploit the server to access files beyond what was shared, or execute anything on the phone? | Read-only server scoped strictly to content:// URIs selected by the user | ✅ Implemented | The server only serves files registered in `FileRegistry`. File IDs are UUIDs — not guessable, not sequential — and every URL needs both the session ID and the file ID. No path is exposed. No write endpoint exists. ContentResolver handles the actual file access — the server never touches the filesystem directly. |

---

## 2. Industry Standard Alignment (OWASP Mobile Top 10 — 2024)

| ID | Category | Planned Strategy | Status | Reason if not implemented |
| --- | --- | --- | --- | --- |
| **M1** | Improper Credential Usage | PINs and tokens generated at runtime, never hardcoded or written to disk | ⚠️ Partial | No credentials are hardcoded. The session ID is generated fresh per session from `SecureRandom` (16 hex characters). It is kept in memory, with one exception: if Android's background time limit pauses sharing, the session ID and port are saved to the app's private storage so Resume can bring back the same link. They are deleted when the user stops the server or starts a new session, and app backup is turned off. There is no PIN system — no credentials beyond the URL. The NVD API key is kept out of source in `local.properties`. |
| **M2** | Supply Chain Security | Automated dependency scanning via OWASP Dependency-Check Gradle plugin | ✅ Implemented | OWASP Dependency-Check is configured in `build.gradle.kts` with NVD API key, CVSS threshold of 7.0, HTML report output, and a suppression file. Runs as a Gradle task. Dependabot opens weekly update pull requests for Gradle dependencies (see Stage 4). |
| **M3** | Insecure Auth/Authz | PIN-gated handshake + high-entropy ephemeral session tokens | ❌ Not implemented | No authentication exists. Any device that obtains the URL has full read access for the session lifetime. The session ID is now high-entropy (16 random characters), which stops guessing, but it is a secret in the URL, not authentication. Accepted tradeoff for frictionless UX. |
| **M4** | Input/Output Validation | Token-to-URI mapping to prevent path traversal and dirty stream attacks | ✅ Implemented | File access is through `/file/{fileId}` where `fileId` is a random UUID generated at registration time in `FileRegistry.addFile()`. The UUID has no filesystem path meaning — it is purely an opaque lookup key into `FileRegistry`. An attacker cannot construct a path to anything outside the registry. An unrecognised or unregistered ID returns 404. There is no path construction from user input anywhere in the routing layer. File names in the `Content-Disposition` header are escaped. |
| **M5** | Insecure Communication | Local-only serving, session-bound URLs | ⚠️ Partial | The server binds to the one network address the user selected (Wi-Fi or hotspot), not to all interfaces, and is not exposed to the internet. HTTP is used — no TLS. This is a deliberate decision to avoid self-signed cert friction on the receiver side. The app makes no outgoing connections, so the cleartext-traffic setting and network security config were removed in v1.1.0. The risk is accepted and documented. |
| **M6** | Privacy Controls | Android Scoped Storage and file picker — no broad storage permission | ✅ Implemented | The app uses `GetMultipleContents` activity result contract (system file picker). No `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` permission is declared. Files are accessed only via granted content URIs. Files whose size can't be read are not shared. |
| **M7** | Binary Protections | R8/ProGuard obfuscation on release builds | ✅ Implemented | `isMinifyEnabled = true` and `isShrinkResources = true` on release. Custom ProGuard rules preserve Ktor reflection lookups and Kotlinx Serialization metadata while obfuscating everything else. |
| **M8** | Security Misconfiguration | Server tied to foreground service lifecycle, no directory indexing | ✅ Implemented | Server only runs while the foreground service is active. The service is not exported. App backup is turned off. There is no directory listing endpoint — `/api/files` returns only explicitly registered file metadata. Routing is explicit, not filesystem-mapped. |
| **M9** | Insecure Data Storage | Files streamed directly, no temp copies written to disk | ✅ Implemented | Files are streamed from ContentResolver directly into the Ktor response channel. No temporary file is written. Thumbnails are cached to disk (limit 50 MB, oldest-unused removed first) but contain only scaled-down JPEG previews, not the original file data. The only other stored data is the paused-session note described under M1. |
| **M10** | Insufficient Cryptography | SHA-256 checksums for file integrity verification | ❌ Not implemented | No hashing is performed on served files. This was planned but not built. Would be straightforward to add as a response header. |

---

## 3. Security Implementation Pipeline

### Stage 1 — Before development

**Threat Modeling (STRIDE)**
Status: `DONE`

This document. Written before development to identify risks upfront rather than retroactively, and updated for each release.

---

### Stage 2 — During development

**Continuous SCA Scanning**
Tool: OWASP Dependency-Check Gradle plugin
Status: `ACTIVE`

Configured with a CVSS 7.0 failure threshold. Runs via Gradle task during development. NVD API key stored in `local.properties`, not committed to source control.

---

### Stage 3 — After development, pre-deployment

**SBOM Generation (Software Bill of Materials)**
Format: CycloneDX
Status: `DONE`

Dependency inventory generated with the CycloneDX Gradle plugin and exported to `docs/security/dropx-sbom.json`. Regenerated for each release.

---

### Stage 4 — Post-deployment

**Vulnerability Tracking**
Method: External agent monitoring the SBOM for new CVEs after release
Status: `ACTIVE`

Dependabot alerts are enabled on the repository. It monitors all dependencies declared in `libs.versions.toml` for newly discovered CVEs. Dependabot security updates are enabled and automatically open a pull request when a patched version of an affected dependency is available. Dependabot version updates (`.github/dependabot.yml`) also open weekly pull requests for newer Gradle dependency versions. Each pull request is built and tested before it is merged.

---

## 4. Changes in v1.1.0

Security-relevant changes since v1.0.1:

- Session ID is 16 random characters from `SecureRandom` (was 6 characters from a UUID).
- Sharing stops automatically, with a notification, if the network address the server is bound to disappears.
- The share link always shows the address the server is really bound to.
- Android 15 background time limit handled: sharing pauses cleanly and can be resumed with the same link.
- Removed the cleartext-traffic setting, the network security config and the unused backup rules; app backup is turned off.
- Files with an unreadable size are skipped instead of being served with a wrong length.
- `Content-Disposition` file names are escaped.
- The web portal no longer loads a font from an external server, so it makes no outside requests.

---

## Notes

The biggest honest gaps are **I (Info Disclosure)** and **M3 (Auth)**: there is no PIN and no login, so anyone who has the link can read the shared files while the server runs. The 16-character session ID stops guessing, but it does not stop someone who can see the traffic on the network (HTTP is unencrypted) or an active attacker who sits in the middle. For the intended use case — sharing files with someone physically near you over your own hotspot or home Wi-Fi — this is a conscious tradeoff, not an oversight. A PIN or a stronger trust mechanism is the most valuable security addition for a future version.

Everything under **E (Elevation)** and **M4 (Input Validation)** is solid. The URI mapping approach means there is genuinely no path traversal surface, which is the most critical attack class for a file server.