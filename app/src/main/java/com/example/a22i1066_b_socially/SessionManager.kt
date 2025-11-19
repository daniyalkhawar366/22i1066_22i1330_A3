package com.example.a22i1066_b_socially

import android.content.Context
import android.content.SharedPreferences

class SessionManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SociallySession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_PROFILE_PIC = "profile_pic_url"
    }

    fun saveAuthToken(token: String, userId: String, username: String? = null, email: String? = null, profilePicUrl: String? = null) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, userId)
            username?.let { putString(KEY_USERNAME, it) }
            email?.let { putString(KEY_EMAIL, it) }
            profilePicUrl?.let { putString(KEY_PROFILE_PIC, it) }
            apply()
        }

        // Also save to MultiAccountManager
        if (username != null && email != null) {
            val multiAccountManager = MultiAccountManager(context)
            multiAccountManager.addAccount(
                AccountInfo(
                    userId = userId,
                    username = username,
                    email = email,
                    token = token,
                    profilePicUrl = profilePicUrl ?: ""
                )
            )
        }
    }

    fun getAuthToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getToken(): String? = getAuthToken()

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getProfilePicUrl(): String? = prefs.getString(KEY_PROFILE_PIC, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getAuthToken() != null
}
