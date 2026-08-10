package com.example.resqmesh.security

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoHelper {
    
    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12
    }

    /**
     * Generates a new EC (Elliptic Curve) Key Pair for Diffie-Hellman
     */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        return kpg.generateKeyPair()
    }

    /**
     * Converts a Public Key to a Base64 String to be put into a QR Code
     */
    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)
    }

    /**
     * Encrypts a message using a shared secret key (AES-256)
     */
    fun encrypt(plaintext: String, sharedSecret: ByteArray): String {
        val keySpec = SecretKeySpec(sharedSecret.copyOf(32), "AES") // Use first 256 bits
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTE).apply { SecureRandom().nextBytes(this) }
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Combine IV and Ciphertext for transport
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    /**
     * Decrypts a message using a shared secret key (AES-256)
     */
    fun decrypt(encryptedBase64: String, sharedSecret: ByteArray): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = combined.sliceArray(0 until IV_LENGTH_BYTE)
            val ciphertext = combined.sliceArray(IV_LENGTH_BYTE until combined.size)
            
            val keySpec = SecretKeySpec(sharedSecret.copyOf(32), "AES")
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null // Decryption failed (wrong key or corrupted data)
        }
    }
}
