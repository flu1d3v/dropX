package com.fluid.dropx.core.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
//DXSP-dropX Secure Protocol
this is a CryptoManager for secure communication between two devices
*/
class CryptoManager {
    private val keyPair: KeyPair

    init {
        // algorithm family = EC (Elliptic Curve Cryptography) and Curve: secp256r1 (NIST P-256)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(ecSpec)
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

    fun encrypt(data: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) } // Generate new Nonce
        val spec = GCMParameterSpec(128, nonce) // 128-bit authentication tag

        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(data)

        // Return: [12 bytes Nonce] + [Encrypted Data] + [16 bytes Tag]
        return nonce + ciphertext
    }

    fun decrypt(encryptedData: ByteArray, key: SecretKeySpec): ByteArray {
        val nonce = encryptedData.sliceArray(0 until 12) // Extract Nonce from the front
        val ciphertext = encryptedData.sliceArray(12 until encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)

        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }
}