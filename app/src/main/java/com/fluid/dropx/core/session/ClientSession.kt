package com.fluid.dropx.core.session

import javax.crypto.spec.SecretKeySpec

class ClientSession (val clientId: String, var state: ClientState = ClientState.CREATED) {
    var clientPublicKey: ByteArray? = null
    var sharedSecret: ByteArray? = null
    var aesKey: SecretKeySpec? = null

    var isVerified: Boolean = false

    val createdAt: Long = System.currentTimeMillis()
    var lastSeen: Long = System.currentTimeMillis()

    fun updateActivity() {
        lastSeen = System.currentTimeMillis()
    }
}