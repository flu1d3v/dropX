\# dropX Threat Model (STRIDE)



> This document was written before and during development. Not everything planned here got implemented in v1.0 — reasons are noted honestly per row. The intent is to keep this as a living record of what was thought about, what shipped, and what didn't.



\---



\## 1. Threat Matrix



| Letter | Threat | What it means for dropX | Planned Mitigation | Status | Reason if not implemented |

| :--- | :--- | :--- | :--- | :--- | :--- |

| \*\*S\*\* | Spoofing | Can someone on the network pretend to be your phone and intercept file requests? | OOB QR Handshake — share a high-entropy session path via QR so the receiver knows they're talking to the right server | ❌ Not implemented | The session ID in the URL is only 6 characters, not high-entropy. QR is implemented but it just encodes the URL — it doesn't constitute an OOB secret handshake. Without HTTPS there is also no server identity verification. Accepted risk for local network use. |

| \*\*T\*\* | Tampering | Can someone modify the file in transit between the phone and browser? | SHA-256 hash of each file served alongside it so the receiver can verify integrity | ❌ Not implemented | No checksum is generated or served. Files are streamed directly from ContentResolver. On a trusted local network this was deprioritised. Can be added as a response header (`X-Content-SHA256`) in a future version. |

| \*\*R\*\* | Repudiation | Can someone claim they never downloaded a file? | Local read-only audit log with timestamps and requester IPs, rotated weekly | ❌ Not implemented | No logging is implemented on the server. Ktor routes have no request logging middleware. Out of scope for v1.0 — the use case doesn't require accountability tracking. |

| \*\*I\*\* | Info Disclosure | Can a random person on the same WiFi see your files? | 4-digit PIN challenge shown only on sender's screen + one-time-use ephemeral tokens | ⚠️ Partial | The session ID in the URL provides minimal obscurity. No PIN is implemented. No one-time tokens — the URL works for the entire duration of the session for anyone who has it. Accepted tradeoff: PIN adds friction that conflicts with the zero-friction receiver goal. |

| \*\*D\*\* | DoS | Can someone on the network saturate your phone's bandwidth by hammering simultaneous large file downloads, draining battery through sustained radio activity? | Hard limit on concurrent connections via Ktor engine config | ❌ Not implemented | No connection limit or rate limiting is configured. The primary strain is on bandwidth — sustained parallel downloads keep the WiFi/hotspot radio active at full power which directly hits battery. CPU impact is low since Ktor CIO is non-blocking and file serving is streaming from ContentResolver. The thumbnail semaphore (max 3 concurrent) limits generation load but does nothing for download concurrency. Local network threat, accepted for v1.0. |

| \*\*E\*\* | Elevation | Can a receiver exploit the server to access files beyond what was shared, or execute anything on the phone? | Read-only server scoped strictly to content:// URIs selected by the user | ✅ Implemented | The server only serves files registered in `FileRegistry`. File IDs are UUIDs — not guessable, not sequential. No path is exposed. No write endpoint exists. ContentResolver handles the actual file access — the server never touches the filesystem directly. |



\---



\## 2. Industry Standard Alignment (OWASP Mobile Top 10 — 2024)



| ID | Category | Planned Strategy | Status | Reason if not implemented |

| :--- | :--- | :--- | :--- | :--- |

| \*\*M1\*\* | Improper Credential Usage | PINs and tokens generated at runtime, never hardcoded or written to disk | ⚠️ Partial | No credentials are hardcoded. Session ID is generated fresh per session via UUID. However no PIN system exists — there are no credentials at all beyond the URL. NVD API key is correctly kept out of source in `local.properties`. |

| \*\*M2\*\* | Supply Chain Security | Automated dependency scanning via OWASP Dependency-Check Gradle plugin | ✅ Implemented | OWASP Dependency-Check is configured in `build.gradle.kts` with NVD API key, CVSS threshold of 7.0, HTML report output, and a suppression file. Runs as a Gradle task. |

| \*\*M3\*\* | Insecure Auth/Authz | PIN-gated handshake + high-entropy ephemeral session tokens | ❌ Not implemented | No authentication exists. Any device that obtains the URL has full read access for the session lifetime. The session ID is 6 characters — not high entropy. Accepted tradeoff for frictionless UX. |

| \*\*M4\*\* | Input/Output Validation | Token-to-URI mapping to prevent path traversal and dirty stream attacks | ✅ Implemented | File access is through `/file/{fileId}` where `fileId` is a random UUID generated at registration time in `FileRegistry.addFile()`. The UUID has no filesystem path meaning — it is purely an opaque lookup key into `FileRegistry`. An attacker cannot construct a path to anything outside the registry. An unrecognised or unregistered ID returns 404. There is no path construction from user input anywhere in the routing layer. |

| \*\*M5\*\* | Insecure Communication | Local-only serving, session-bound URLs | ⚠️ Partial | Server binds to the local network interface only, not exposed to internet. HTTP is used — no TLS. This is a deliberate decision to avoid self-signed cert friction on the receiver side. The risk is accepted and documented. |

| \*\*M6\*\* | Privacy Controls | Android Scoped Storage and file picker — no broad storage permission | ✅ Implemented | The app uses `GetMultipleContents` activity result contract (system file picker). No `READ\_EXTERNAL\_STORAGE` or `MANAGE\_EXTERNAL\_STORAGE` permission is declared. Files are accessed only via granted content URIs. |

| \*\*M7\*\* | Binary Protections | R8/ProGuard obfuscation on release builds | ✅ Implemented | `isMinifyEnabled = true` and `isShrinkResources = true` on release. Custom ProGuard rules preserve Ktor reflection lookups and Kotlinx Serialization metadata while obfuscating everything else. |

| \*\*M8\*\* | Security Misconfiguration | Server tied to foreground service lifecycle, no directory indexing | ✅ Implemented | Server only runs while the foreground service is active. There is no directory listing endpoint — `/api/files` returns only explicitly registered file metadata. Routing is explicit, not filesystem-mapped. |

| \*\*M9\*\* | Insecure Data Storage | Files streamed directly, no temp copies written to disk | ✅ Implemented | Files are streamed from ContentResolver directly into the Ktor response channel. No temporary file is written. Thumbnails are cached to disk but contain only scaled-down JPEG previews, not the original file data. |

| \*\*M10\*\* | Insufficient Cryptography | SHA-256 checksums for file integrity verification | ❌ Not implemented | No hashing is performed on served files. This was planned but not built in v1.0. Would be straightforward to add as a response header. |



\---



\## 3. Security Implementation Pipeline



\### Stage 1 — Before development

\*\*Threat Modeling (STRIDE)\*\*

Status: `DONE`

This document. Written before v1.0 development to identify risks upfront rather than retroactively.



\---



\### Stage 2 — During development

\*\*Continuous SCA Scanning\*\*

Tool: OWASP Dependency-Check Gradle plugin

Status: `ACTIVE`

Configured with a CVSS 7.0 failure threshold. Runs via Gradle task during development. NVD API key stored in `local.properties`, not committed to source control.



\---



\### Stage 3 — After development, pre-deployment

\*\*SBOM Generation (Software Bill of Materials)\*\*

Format: CycloneDX

Status: `PLANNED`

Intended to generate a full dependency inventory before Play Store submission. Not yet configured in the build pipeline.



\---



\### Stage 4 — Post-deployment

\*\*Vulnerability Tracking\*\*

Method: External agent monitoring the SBOM for new CVEs after release

Status: `PLANNED`

Depends on Stage 3 SBOM being in place first.



\---



\## Notes



The biggest honest gap in v1.0 is \*\*I (Info Disclosure)\*\* and \*\*M3 (Auth)\*\*. The session ID is weak and there is no PIN. For the intended use case — sharing files with someone physically near you over your own hotspot — this is a conscious tradeoff, not an oversight. A PIN or stronger token system is the most valuable security addition for v2.0.



Everything under \*\*E (Elevation)\*\* and \*\*M4 (Input Validation)\*\* is solid. The URI mapping approach means there is genuinely no path traversal surface, which is the most critical attack class for a file server.

