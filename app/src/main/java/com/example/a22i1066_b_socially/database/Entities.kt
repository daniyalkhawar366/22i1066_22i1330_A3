package com.example.a22i1066_b_socially.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val message: String,
    val timestamp: Long,
    val type: String, // "text", "image", "call"
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val isSent: Boolean = true, // false if pending offline send
    val localImagePath: String? = null
)

@Entity(tableName = "cached_posts")
data class CachedPost(
    @PrimaryKey
    val id: String,
    val userId: String,
    val username: String,
    val profilePicUrl: String?,
    val caption: String,
    val imageUrls: List<String>,
    val timestamp: Long,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val localImagePaths: List<String>? = null,
    val isSynced: Boolean = true
)

@Entity(tableName = "cached_stories")
data class CachedStory(
    @PrimaryKey
    val id: String,
    val userId: String,
    val username: String,
    val profilePicUrl: String?,
    val mediaUrl: String,
    val mediaType: String, // "image" or "video"
    val timestamp: Long,
    val expiresAt: Long,
    val viewsCount: Int = 0,
    val localMediaPath: String? = null,
    val isSynced: Boolean = true
)

@Entity(tableName = "pending_actions")
data class PendingAction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionType: String, // "send_message", "create_post", "upload_story", "like_post", "comment", etc.
    val dataJson: String, // JSON representation of the action data
    val timestamp: Long,
    val retryCount: Int = 0,
    val status: String = "pending", // "pending", "processing", "failed", "completed"
    val errorMessage: String? = null
)

