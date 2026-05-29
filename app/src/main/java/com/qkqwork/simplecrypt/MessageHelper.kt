package com.qkqwork.simplecrypt

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

object MessageHelper {
    private val gson = Gson()

    data class PublicKeyMessage(
        @SerializedName("u") val userId: String,
        @SerializedName("v") val version: Int,
        @SerializedName("k") val publicKey: String
    )

    data class EncryptedMessage(
        @SerializedName("s") val senderId: String,
        @SerializedName("r") val recipientId: String, // Added recipient
        @SerializedName("v") val receiverVersion: Int,
        @SerializedName("d") val data: String
    )

    fun createPublicKeyMessage(userId: String, version: Int, publicKeyString: String): String {
        return gson.toJson(PublicKeyMessage(userId, version, publicKeyString))
    }

    fun parsePublicKeyMessage(message: String): Triple<String, Int, String>? {
        return try {
            val obj = gson.fromJson(message, PublicKeyMessage::class.java)
            if (obj.userId.isNotEmpty() && obj.publicKey.isNotEmpty()) {
                Triple(obj.userId, obj.version, obj.publicKey)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun createEncryptedMessage(senderId: String, recipientId: String, receiverVersion: Int, encryptedData: String): String {
        return gson.toJson(EncryptedMessage(senderId, recipientId, receiverVersion, encryptedData))
    }

    fun parseEncryptedMessage(message: String): EncryptedMessage? {
        return try {
            gson.fromJson(message, EncryptedMessage::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
