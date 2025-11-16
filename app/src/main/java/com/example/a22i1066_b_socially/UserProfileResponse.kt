package com.example.a22i1066_b_socially

data class UserProfileResponse(
    val success: Boolean,
    val user: UserProfile? = null,
    val error: String? = null
)

data class UserProfile(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val profilePicUrl: String = ""
)
