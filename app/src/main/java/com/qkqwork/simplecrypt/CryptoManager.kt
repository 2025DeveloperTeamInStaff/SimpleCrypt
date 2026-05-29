package com.qkqwork.simplecrypt

import android.security.keystore.KeyInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Checks if the key is actually stored inside secure hardware (TEE/SE).
     */
    fun isKeyHardwareBacked(alias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey ?: return false
            val factory = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE_PROVIDER)
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            keyInfo.isInsideSecureHardware
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a key pair.
     * If useTee is true, it's stored in AndroidKeyStore. Returns null.
     * If useTee is false, it returns a Pair of (PrivateKeyBase64, PublicKeyBase64).
     */
    fun generateRsaKeyPairBetter(alias: String, useTee: Boolean): Pair<String, String>? {
        if (useTee) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                KEYSTORE_PROVIDER
            )
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setKeySize(2048)
            keyPairGenerator.initialize(builder.build())
            keyPairGenerator.generateKeyPair()
            return null
        } else {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            val priv = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
            val pub = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            return Pair(priv, pub)
        }
    }

    fun getPublicKey(alias: String, softwarePrivKeyBase64: String? = null): PublicKey? {
        if (softwarePrivKeyBase64 == null) {
            // Check AndroidKeyStore
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            return keyStore.getCertificate(alias)?.publicKey
        } else {
            // Derive public key from software private key if we didn't store it? 
            // Better to store it, but for RSA we can't easily derive without N and E.
            // Actually, standard KeyPairGenerator gives both. 
            // For MVP simplicity, let's assume we store the public key or re-generate.
            // Wait, PKCS8 private key usually doesn't contain the public key.
            // Let's modify generateRsaKeyPair to return a Pair or just save both.
            return null // We'll fix this by storing public key too or using a different approach.
        }
    }

    // Refactored to handle both types
    fun getPublicKeyFromAny(alias: String, prefs: PreferenceManager): PublicKey? {
        val swKey = prefs.getSoftwarePrivateKey(alias)
        return if (swKey != null) {
            // For software keys, we should have stored the public key too.
            // Let's store public keys for ALL aliases in a common way.
            val pubStr = prefs.getFriendKey("LOCAL_PUB_$alias")
            pubStr?.let { stringToPublicKey(it) }
        } else {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.getCertificate(alias)?.publicKey
        }
    }

    private fun getPrivateKey(alias: String, prefs: PreferenceManager): PrivateKey? {
        val swKeyBase64 = prefs.getSoftwarePrivateKey(alias)
        return if (swKeyBase64 != null) {
            val keyBytes = Base64.decode(swKeyBase64, Base64.NO_WRAP)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(spec)
        } else {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.getKey(alias, null) as? PrivateKey
        }
    }

    fun encrypt(plainText: String, receiverPublicKey: PublicKey): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val aesKey = keyGen.generateKey()

        val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
        rsaCipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        val aesCipher = Cipher.getInstance(AES_ALGORITHM)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = aesCipher.iv
        val encryptedPayload = aesCipher.doFinal(plainText.toByteArray())

        return Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encryptedPayload, Base64.NO_WRAP)
    }

    fun decrypt(encryptedData: String, alias: String, prefs: PreferenceManager): String {
        val parts = encryptedData.split(":")
        if (parts.size != 3) throw IllegalArgumentException("Invalid encrypted data format")

        val encryptedAesKey = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val encryptedPayload = Base64.decode(parts[2], Base64.NO_WRAP)

        val privateKey = getPrivateKey(alias, prefs) ?: throw IllegalStateException("Private key not found")
        val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        val aesCipher = Cipher.getInstance(AES_ALGORITHM)
        val spec = GCMParameterSpec(128, iv)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
        val decryptedPayload = aesCipher.doFinal(encryptedPayload)

        return String(decryptedPayload)
    }

    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun stringToPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }
    
    // For Export/Import
    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }
}
