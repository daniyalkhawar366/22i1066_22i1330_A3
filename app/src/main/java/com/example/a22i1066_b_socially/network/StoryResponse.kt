package com.example.a22i1066_b_socially.network

data class StoryResponse(
    val success: Boolean,
    val stories: List<StoryItem>? = null,
    val error: String? = null
)

data class StoryItem(
    val storyId: String,
    val userId: String,
    val username: String,
    val profilePicUrl: String,
    val imageUrl: String,
    val uploadedAt: Long,
    val expiresAt: Long,
    val closeFriendsOnly: Boolean = false
)

data class UploadStoryRequest(
    val imageUrl: String,
    val closeFriendsOnly: Boolean = false
)

data class UploadStoryResponse(
    val success: Boolean,
    val storyId: String? = null,
    val uploadedAt: Long? = null,
    val expiresAt: Long? = null,
    val error: String? = null
)

data class DeleteStoryRequest(
    val storyId: String
)

data class DeleteStoryResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

