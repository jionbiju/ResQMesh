package com.example.resqmesh.security

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class CryptoHelper {
    
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
     * Converts a Base64 String (from a QR scan) back into a Public Key
     */
    fun stringToPublicKey(keyStr: String): PublicKey {
        val publicBytes = Base64.decode(keyStr, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(publicBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Generates the Shared Secret (the common Decryption Key)
     */
    fun generateSharedSecret(myPrivateKey: PrivateKey, partnerPublicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(partnerPublicKey, true)
        return keyAgreement.generateSecret()
    }
}
