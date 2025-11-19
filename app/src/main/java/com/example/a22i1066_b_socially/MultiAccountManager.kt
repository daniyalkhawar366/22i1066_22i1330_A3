package com.example.a22i1066_b_socially

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AccountInfo(
    val userId: String,
    val username: String,
    val email: String,
    val token: String,
    val profilePicUrl: String = ""
)

class MultiAccountManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MultiAccountSession", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACCOUNTS = "saved_accounts"
        private const val KEY_CURRENT_ACCOUNT_ID = "current_account_id"
    }

    // Get all saved accounts
    fun getAllAccounts(): List<AccountInfo> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val type = object : TypeToken<List<AccountInfo>>() {}.type
        return gson.fromJson(json, type)
    }

    // Add or update an account
    fun addAccount(account: AccountInfo) {
        val accounts = getAllAccounts().toMutableList()

        // Remove existing account with same userId if exists
        accounts.removeAll { it.userId == account.userId }

        // Add the new/updated account
        accounts.add(account)

        // Save back to preferences
        val json = gson.toJson(accounts)
        prefs.edit().putString(KEY_ACCOUNTS, json).apply()

        // Set as current account
        setCurrentAccountId(account.userId)
    }

    // Get current account
    fun getCurrentAccount(): AccountInfo? {
        val currentId = prefs.getString(KEY_CURRENT_ACCOUNT_ID, null) ?: return null
        return getAllAccounts().find { it.userId == currentId }
    }

    // Switch to a different account
    fun switchAccount(userId: String): AccountInfo? {
        val account = getAllAccounts().find { it.userId == userId }
        if (account != null) {
            setCurrentAccountId(userId)

            // Update SessionManager with this account's credentials
            val sessionManager = SessionManager(prefs.context)
            sessionManager.saveAuthToken(account.token, account.userId, account.username)
        }
        return account
    }

    // Remove an account
    fun removeAccount(userId: String) {
        val accounts = getAllAccounts().toMutableList()
        accounts.removeAll { it.userId == userId }

        val json = gson.toJson(accounts)
        prefs.edit().putString(KEY_ACCOUNTS, json).apply()

        // If removing current account, clear it
        if (getCurrentAccountId() == userId) {
            prefs.edit().remove(KEY_CURRENT_ACCOUNT_ID).apply()
        }
    }

    // Set current account ID
    private fun setCurrentAccountId(userId: String) {
        prefs.edit().putString(KEY_CURRENT_ACCOUNT_ID, userId).apply()
    }

    // Get current account ID
    fun getCurrentAccountId(): String? {
        return prefs.getString(KEY_CURRENT_ACCOUNT_ID, null)
    }

    // Clear all accounts
    fun clearAllAccounts() {
        prefs.edit().clear().apply()
    }

    private val SharedPreferences.context: Context
        get() = this@MultiAccountManager.prefs.let {
            // Get context from the SharedPreferences instance
            android.content.ContextWrapper(null)
        }
}

