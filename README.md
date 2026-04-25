\# dropX



A high-performance, cross-platform file transport utility for Android.



\## The Mission

To allow seamless file sharing from an Android device to any device with a web browser, using a localized Ktor server and QR-code handshakes.



\## Technical Goals

\- \*\*Min SDK:\*\* API 29 (Android 10)

\- \*\*Target SDK:\*\* API 36 (Android 16)

\- \*\*Architecture:\*\* Embedded Ktor-Netty Server within an Android Foreground Service.

\- \*\*Security:\*\* Implementing an SSDLC (Secure Software Development Life Cycle) with automated SCA scanning.



\## Current Status

\- \[x] Project environment initialized (API 29/36)

\- \[x] version control established with Git hardening

\- \[x] Security Architecture \& STRIDE Threat Framework defined

\- \[x] Security Implementation pipeline defined

\- \[x] A high-level project phases (outcomes) defined

\- \[x] Industry Standard Alignment (OWASP Mobile 2024) mapping done

\- \[x] Added OWASP Dependency-Check plugin

\- \[x] Created project-suppressions.xml to filter Android Studio UTP and Kotlin false positives. Added initial baseline reports and suppression ledger to docs/security

\- \[x] Added NetworkManager to scan physical interfaces (Wi-Fi, Hotspot)

\- \[x] Implemented multi-layer filtering (isUp, Loopback, Virtual, P2P)

\- \[x] Created NetworkResult data class for prioritized IP reporting

\- \[x] Added fallback for unknown multicast-capable adapters

\- \[x] Phase 0 \& 1 are over

\- \[x] implemented foreground service skeleton for Android 10-16"

\- \[x] add Ktor dependencies via Version Catalog and perform OWASP dependency scan



