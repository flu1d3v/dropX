\# dropX Threat Model (STRIDE)



\## 1. Threat Matrix

| Letter | Threat | What it means for dropX | Mitigation Strategy | Status |

| :--- | :--- | :--- | :--- | :--- |

| \*\*S\*\* | Spoofing | Can someone pretend to be your phone to steal files? | \*\*OOB QR Handshake:\*\* Uses a physical "Out-of-Band" channel to share a high-entropy secret path. An attacker cannot spoof the server identity without physical access to the generated QR. |  |

| \*\*T\*\* | Tampering | Can someone modify the file while it's being "dropped"? | \*\*Integrity Verification:\*\* The server generates a SHA−256 hash of the file. The receiver can verify the integrity of the downloaded data against this checksum to ensure no bit-level modifications occurred. | |

| \*\*R\*\* | Repudiation | Can a user claim they never downloaded a file? | \*\*Transaction Logs:\*\* Implementation of a local, read-only audit log capturing timestamps and requestor IPs. Logs are rotated (cleared) weekly to maintain privacy and storage efficiency |  |

| \*\*I\*\* | Info Disclosure | Can a random person on Wi-Fi see your file? | \*\*PIN-Gate \& Ephemeral Tokens:\*\* Shared Wi-Fi traffic is protected by a 4-digit PIN challenge shown only on the sender's screen. Tokens are "one-time use," preventing secondary access via sniffed URLs. |  |

| \*\*D\*\* | DoS | Can someone flood your phone to crash it? | \*\*Connection Throttling:\*\* The Ktor-Netty engine is configured with a hard limit on concurrent connections (Max: 3). This protects the Android system resources from battery exhaustion and CPU flooding. |  |

| \*\*E\*\* | Elevation | Can a receiver hack into your phone's system? | \*\*Atomic Scoping:\*\* The server is restricted to Read-Only access. It only has permission to serve specific content:// Uris selected by the user, preventing any system-level command execution. |  |



\---



\## 2. Industry Standard Alignment (OWASP Mobile 2024)

\*Use this section to map project defenses to the current mobile security standards.\*



| OWASP ID | Category | dropX Mitigation | Status |

| :--- | :--- | :--- | :--- |

| M2 | Inadequate Supply Chain Security | \[Your Detail Here] |  |

| M3 | Insecure Authentication/Authorization | \[Your Detail Here] |  |

| M5 | Insecure Communication | \[Your Detail Here] |  |

| M6 | Inadequate Privacy Controls | \[Your Detail Here] |  |

| M8 | Security Misconfiguration | \[Your Detail Here] |  |

| M9 | Insecure Data Storage | \[Your Detail Here] |  |



\---



\## 3. Security Implementation Pipeline



\### Stage 1: Before development

Threat Modeling: Using the STRIDE framework to identify risks before writing code (Status: DONE)



\### Stage 2: During development

Continuous SCA Scanning: Automated Gradle tasks that run during development to ensure no vulnerable libraries are introduced (Using OWASP Dependency-Check)



\### Stage 3: After development (Pre-deployment)

SBOM (Software Bill of Materials): Generating a CycloneDX manifest to inventory all project dependencies



\### Stage 4: Post-deployment

Vulnerability Tracking: Utilizing an external agent to monitor the SBOM for new CVEs (vulnerabilities) discovered after the app is released

