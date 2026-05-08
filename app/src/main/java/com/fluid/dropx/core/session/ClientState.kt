package com.fluid.dropx.core.session


enum class ClientState {
    /** * Initial landing. Client ID assigned, but no crypto started.
     */
    CREATED,

    /** * Client requested server's public key (GET /api/handshake).
     */
    HANDSHAKE_STARTED,

    /** * Client sent their public key and shared secret is derived (POST /api/handshake).
     */
    KEY_EXCHANGED,

    /** * Secure channel verified via first successful AES-GCM decryption.
     */
    READY
}