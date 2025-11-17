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
    val firstName: String = "",
    val lastName: String = "",
    val profilePicUrl: String = "",
    val bio: String = "",
    val title: String = "",
    val threadsUsername: String = "",
    val website: String = "",
    val email: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isOnline: Boolean = false,
    val isFollowing: Boolean = false
)
