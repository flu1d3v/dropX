package com.fluid.dropx.core.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec

/*
this is a CryptoManager for secure communication between two devices

It does 2 main things:
1. Create its own identity
Generates an EC key pair (public + private)
2. Derive a shared secret with another device
Uses ECDH (Elliptic Curve Diffie-Hellman)
Turns that into an AES key
*/
class CryptoManager {
    private val keyPair: KeyPair

    init {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        keyPair = keyPairGenerator.generateKeyPair()
    }

    fun getPublicKey(): ByteArray = keyPair.public.encoded

    fun generateSharedSecret(remotePublicKeyBytes: ByteArray): SecretKeySpec {
        val kf = KeyFactory.getInstance("EC")
        val remotePublicKey = kf.generatePublic(X509EncodedKeySpec(remotePublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(remotePublicKey, true)

        val secret = keyAgreement.generateSecret()
        return SecretKeySpec(secret.take(32).toByteArray(), "AES")
    }
}