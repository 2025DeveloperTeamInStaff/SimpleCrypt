package com.qkqwork.simplecrypt

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SimpleCryptPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"
        private const val KEY_FRIEND_PREFIX = "friend_"
        private const val KEY_SOFTWARE_PRIVATE_KEY_PREFIX = "sw_priv_"
    }

    var accounts: List<Account>
        get() {
            val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
            val type = object : TypeToken<List<Account>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString(KEY_ACCOUNTS, json).apply()
        }

    var activeUserId: String?
        get() = prefs.getString(KEY_ACTIVE_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_USER_ID, value).apply()

    fun getActiveAccount(): Account? {
        val uid = activeUserId ?: return null
        return accounts.find { it.userId == uid }
    }

    fun updateAccount(account: Account) {
        val list = accounts.toMutableList()
        val index = list.indexOfFirst { it.userId == account.userId }
        if (index != -1) {
            list[index] = account
            accounts = list
        }
    }

    fun addAccount(account: Account) {
        val list = accounts.toMutableList()
        if (list.none { it.userId == account.userId }) {
            list.add(account)
            accounts = list
        }
    }

    // Software Key Storage (for non-TEE)
    fun saveSoftwarePrivateKey(alias: String, privateKeyBase64: String) {
        prefs.edit().putString(KEY_SOFTWARE_PRIVATE_KEY_PREFIX + alias, privateKeyBase64).apply()
    }

    fun getSoftwarePrivateKey(alias: String): String? {
        return prefs.getString(KEY_SOFTWARE_PRIVATE_KEY_PREFIX + alias, null)
    }

    // Friend Management
    fun saveFriendKey(friendUserId: String, version: Int, publicKeyString: String) {
        prefs.edit()
            .putString(KEY_FRIEND_PREFIX + friendUserId, publicKeyString)
            .putInt(KEY_FRIEND_PREFIX + friendUserId + "_version", version)
            .apply()
    }

    fun getFriendKey(friendUserId: String): String? {
        return prefs.getString(KEY_FRIEND_PREFIX + friendUserId, null)
    }

    fun getFriendVersion(friendUserId: String): Int {
        return prefs.getInt(KEY_FRIEND_PREFIX + friendUserId + "_version", 0)
    }

    fun getAllFriends(): List<String> {
        return prefs.all.keys.filter { it.startsWith(KEY_FRIEND_PREFIX) && !it.endsWith("_version") }
            .map { it.removePrefix(KEY_FRIEND_PREFIX) }
    }

    fun exportAllDataJson(): String {
        return gson.toJson(prefs.all)
    }

    fun deleteFriend(friendUserId: String) {
        prefs.edit()
            .remove(KEY_FRIEND_PREFIX + friendUserId)
            .remove(KEY_FRIEND_PREFIX + friendUserId + "_version")
            .apply()
    }

    fun deleteAccount(userId: String) {
        val list = accounts.toMutableList()
        list.removeAll { it.userId == userId }
        accounts = list
        if (activeUserId == userId) {
            activeUserId = null
        }
        // Optional: clean up software keys for this user's versions
        // In a real app we'd iterate and remove KEY_SOFTWARE_PRIVATE_KEY_PREFIX + "user_${userId}_vX"
    }
}
