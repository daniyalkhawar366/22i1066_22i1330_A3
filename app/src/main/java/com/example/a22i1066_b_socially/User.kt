package com.example.a22i1066_b_socially

// User.kt

data class User(
    val id: String = "",
    val email: String? = null,
    val dob: String? = null,
    val profilePicUrl: String? = null, // renamed to match Firestore keys used in app
    val lastMessage: String? = null,
    val lastTimestamp: Long? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val verified: Boolean = false,
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val postsCount: Long = 0,
    val username: String? = null,
    val createdAt: Any? = null // server timestamp when created
)


