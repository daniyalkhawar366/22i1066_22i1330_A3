package com.example.a22i1066_b_socially.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_chats")
data class CachedChat(
    @PrimaryKey val id: String,
    val userId: String,
    val otherUserId: String,
    val otherUsername: String?,
    val otherProfilePic: String?,
    val lastMessage: String?,
    val lastTimestamp: Long
)
