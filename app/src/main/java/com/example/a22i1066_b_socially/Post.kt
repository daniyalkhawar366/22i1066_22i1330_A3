package com.example.a22i1066_b_socially

data class Post(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val profilePicUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val caption: String = "",
    val timestamp: Long = 0L,
    var likesCount: Int = 0,
    var commentsCount: Int = 0,
    var isLikedByCurrentUser: Boolean = false
)
