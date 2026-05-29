package com.qkqwork.simplecrypt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val prefs: PreferenceManager) : ViewModel() {
    var accounts by mutableStateOf(prefs.accounts)
        private set

    var activeUserId by mutableStateOf(prefs.activeUserId)
        private set

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    var lastEncryptedResult by mutableStateOf("")
    var lastDecryptedResult by mutableStateOf("")
    var lastDecryptionInfo by mutableStateOf("")

    val activeAccount: Account?
        get() = accounts.find { it.userId == activeUserId }

    fun switchAccount(userId: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                prefs.activeUserId = userId
                delay(300)
            }
            activeUserId = userId
            _isProcessing.value = false
        }
    }

    fun addAccount(userId: String, useTee: Boolean) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                val newAcc = Account(userId, useTee, 0, false)
                prefs.addAccount(newAcc)
                delay(300)
            }
            accounts = prefs.accounts
            _isProcessing.value = false
        }
    }

    fun generateNewKey(acc: Account) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val nextV = acc.currentVersion + 1
                val alias = "user_${acc.userId}_v$nextV"

                val pair = withContext(Dispatchers.Default) {
                    CryptoManager.generateRsaKeyPairBetter(alias, acc.useTee)
                }

                val isHw = withContext(Dispatchers.Default) {
                    if (acc.useTee) CryptoManager.isKeyHardwareBacked(alias) else false
                }

                withContext(Dispatchers.IO) {
                    if (pair != null) {
                        prefs.saveSoftwarePrivateKey(alias, pair.first)
                        prefs.saveFriendKey("LOCAL_PUB_$alias", nextV, pair.second)
                    } else if (acc.useTee) {
                        // For TEE, get the public key to store for LOCAL_PUB reference if needed
                        CryptoManager.getPublicKey(alias)?.let {
                            prefs.saveFriendKey("LOCAL_PUB_$alias", nextV, CryptoManager.publicKeyToString(it))
                        }
                    }
                    val updated = acc.copy(currentVersion = nextV, isHardwareVerified = isHw)
                    prefs.updateAccount(updated)
                    delay(200)
                }
                accounts = prefs.accounts
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun encryptMessage(friendName: String, plainText: String) {
        val account = activeAccount ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val keyStr = withContext(Dispatchers.IO) { prefs.getFriendKey(friendName) }
                if (keyStr != null) {
                    val result = withContext(Dispatchers.Default) {
                        val pubKey = CryptoManager.stringToPublicKey(keyStr)
                        val encrypted = CryptoManager.encrypt(plainText, pubKey)
                        val ver = withContext(Dispatchers.IO) { prefs.getFriendVersion(friendName) }
                        MessageHelper.createEncryptedMessage(account.userId, friendName, ver, encrypted)
                    }
                    lastEncryptedResult = result
                } else {
                    lastEncryptedResult = "Error: Friend '$friendName' not found"
                }
            } catch (e: Exception) {
                lastEncryptedResult = "Error: Encryption failed (${e.message})"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun decryptMessage(jsonInput: String) {
        val account = activeAccount ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val parsed = withContext(Dispatchers.Default) {
                    MessageHelper.parseEncryptedMessage(jsonInput)
                }
                
                if (parsed == null) {
                    lastDecryptedResult = "Error: Malformed JSON message"
                    lastDecryptionInfo = ""
                } else if (parsed.recipientId != account.userId) {
                    lastDecryptedResult = "Error: Recipient mismatch! Message is for '${parsed.recipientId}' but you are '${account.userId}'"
                    lastDecryptionInfo = ""
                } else {
                    val result = withContext(Dispatchers.Default) {
                        val alias = "user_${account.userId}_v${parsed.receiverVersion}"
                        try {
                            CryptoManager.decrypt(parsed.data, alias, prefs)
                        } catch (e: Exception) {
                            "DECRYPT_FAILURE_INTERNAL:${e.message}"
                        }
                    }
                    
                    if (result.startsWith("DECRYPT_FAILURE_INTERNAL:")) {
                        val error = result.removePrefix("DECRYPT_FAILURE_INTERNAL:")
                        lastDecryptedResult = "Error: Decryption failed. Possible reasons: Wrong key version (V${parsed.receiverVersion}), corrupted data, or key missing."
                        lastDecryptionInfo = "Details: $error"
                    } else {
                        lastDecryptedResult = result
                        lastDecryptionInfo = "From: ${parsed.senderId} | Using your key: V${parsed.receiverVersion}"
                    }
                }
            } catch (e: Exception) {
                lastDecryptedResult = "Error: Unexpected system failure"
                lastDecryptionInfo = e.message ?: ""
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun importAccount(importText: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            val success = withContext(Dispatchers.Default) {
                try {
                    val parts = importText.split("|")
                    if (parts.size == 4) {
                        val (uid, ver, pub, priv) = parts
                        val acc = Account(uid, false, ver.toInt(), false)
                        prefs.addAccount(acc)
                        val alias = "user_${uid}_v$ver"
                        prefs.saveSoftwarePrivateKey(alias, priv)
                        prefs.saveFriendKey("LOCAL_PUB_$alias", ver.toInt(), pub)
                        true
                    } else false
                } catch (e: Exception) { false }
            }
            if (success) accounts = prefs.accounts
            onResult(success)
            _isProcessing.value = false
        }
    }

    fun deleteFriend(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.deleteFriend(name)
        }
    }

    fun deleteAccount(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.deleteAccount(userId)
            withContext(Dispatchers.Main) {
                accounts = prefs.accounts
                activeUserId = prefs.activeUserId
            }
        }
    }
}
