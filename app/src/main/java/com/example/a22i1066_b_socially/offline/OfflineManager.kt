package com.example.a22i1066_b_socially.offline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.a22i1066_b_socially.SessionManager
import com.example.a22i1066_b_socially.database.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OfflineManager(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val messageDao = database.messageDao()
    private val postDao = database.postDao()
    private val storyDao = database.storyDao()
    private val pendingActionDao = database.pendingActionDao()
    private val gson = Gson()
    private val sessionManager = SessionManager(context)

    companion object {
        private const val TAG = "OfflineManager"
    }

    // ==================== Messages ====================

    suspend fun cacheMessage(
        id: String,
        chatId: String,
        senderId: String,
        receiverId: String,
        message: String,
        timestamp: Long,
        type: String = "text",
        imageUrl: String? = null,
        isSent: Boolean = true,
        localImagePath: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val cachedMessage = CachedMessage(
                id = id,
                chatId = chatId,
                senderId = senderId,
                receiverId = receiverId,
                message = message,
                timestamp = timestamp,
                type = type,
                imageUrl = imageUrl,
                isSent = isSent,
                localImagePath = localImagePath
            )
            messageDao.insertMessage(cachedMessage)
            Log.d(TAG, "Message cached: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching message", e)
        }
    }

    suspend fun getMessagesForChat(chatId: String): List<CachedMessage> =
        withContext(Dispatchers.IO) {
            try {
                messageDao.getMessagesByChatIdSync(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting cached messages", e)
                emptyList()
            }
        }

    suspend fun queueMessageForSending(
        chatId: String,
        receiverId: String,
        message: String,
        type: String = "text",
        localImagePath: String? = null
    ): Long = withContext(Dispatchers.IO) {
        try {
            val currentUserId = sessionManager.getUserId() ?: ""
            // Use pending_ prefix to match ChatDetailActivity expectations
            val messageId = "pending_${System.currentTimeMillis()}_${(0..999).random()}"
            val timestamp = System.currentTimeMillis()

            // Cache the message locally
            cacheMessage(
                id = messageId,
                chatId = chatId,
                senderId = currentUserId,
                receiverId = receiverId,
                message = message,
                timestamp = timestamp,
                type = type,
                isSent = false,
                localImagePath = localImagePath
            )

            // Queue the action
            val actionData = mapOf(
                "messageId" to messageId,
                "chatId" to chatId,
                "receiverId" to receiverId,
                "message" to message,
                "type" to type,
                "localImagePath" to localImagePath,
                "timestamp" to timestamp
            )

            val pendingAction = PendingAction(
                actionType = "send_message",
                dataJson = gson.toJson(actionData),
                timestamp = timestamp
            )

            val actionId = pendingActionDao.insertAction(pendingAction)
            Log.d(TAG, "Message queued for sending: $messageId, actionId: $actionId")
            actionId
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing message", e)
            -1L
        }
    }

    // ==================== Posts ====================

    suspend fun cachePost(
        id: String,
        userId: String,
        username: String,
        profilePicUrl: String?,
        caption: String,
        imageUrls: List<String>,
        timestamp: Long,
        likesCount: Int = 0,
        commentsCount: Int = 0,
        isLiked: Boolean = false,
        isSaved: Boolean = false,
        localImagePaths: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val cachedPost = CachedPost(
                id = id,
                userId = userId,
                username = username,
                profilePicUrl = profilePicUrl,
                caption = caption,
                imageUrls = imageUrls,
                timestamp = timestamp,
                likesCount = likesCount,
                commentsCount = commentsCount,
                isLiked = isLiked,
                isSaved = isSaved,
                localImagePaths = localImagePaths
            )
            postDao.insertPost(cachedPost)
            Log.d(TAG, "Post cached: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching post", e)
        }
    }

    suspend fun getCachedPosts(limit: Int = 50): List<CachedPost> =
        withContext(Dispatchers.IO) {
            try {
                postDao.getPostsSync(limit)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting cached posts", e)
                emptyList()
            }
        }

    suspend fun queuePostForCreation(
        caption: String,
        localImagePaths: List<String>
    ): Long = withContext(Dispatchers.IO) {
        try {
            val currentUserId = sessionManager.getUserId() ?: ""
            val postId = "post_${System.currentTimeMillis()}_${currentUserId}"
            val timestamp = System.currentTimeMillis()

            val actionData = mapOf(
                "postId" to postId,
                "caption" to caption,
                "localImagePaths" to localImagePaths,
                "timestamp" to timestamp
            )

            val pendingAction = PendingAction(
                actionType = "create_post",
                dataJson = gson.toJson(actionData),
                timestamp = timestamp
            )

            val actionId = pendingActionDao.insertAction(pendingAction)
            Log.d(TAG, "Post queued for creation: $postId, actionId: $actionId")
            actionId
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing post", e)
            -1L
        }
    }

    suspend fun queuePostInteraction(
        actionType: String, // "like_post", "unlike_post", "save_post", "unsave_post"
        postId: String,
        additionalData: Map<String, Any> = emptyMap()
    ): Long = withContext(Dispatchers.IO) {
        try {
            val actionData = mutableMapOf<String, Any>(
                "postId" to postId
            )
            actionData.putAll(additionalData)

            val pendingAction = PendingAction(
                actionType = actionType,
                dataJson = gson.toJson(actionData),
                timestamp = System.currentTimeMillis()
            )

            pendingActionDao.insertAction(pendingAction)
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing post interaction", e)
            -1L
        }
    }

    // ==================== Stories ====================

    suspend fun cacheStory(
        id: String,
        userId: String,
        username: String,
        profilePicUrl: String?,
        mediaUrl: String,
        mediaType: String,
        timestamp: Long,
        expiresAt: Long,
        viewsCount: Int = 0,
        localMediaPath: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val cachedStory = CachedStory(
                id = id,
                userId = userId,
                username = username,
                profilePicUrl = profilePicUrl,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                timestamp = timestamp,
                expiresAt = expiresAt,
                viewsCount = viewsCount,
                localMediaPath = localMediaPath
            )
            storyDao.insertStory(cachedStory)
            Log.d(TAG, "Story cached: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching story", e)
        }
    }

    suspend fun getCachedStories(): List<CachedStory> =
        withContext(Dispatchers.IO) {
            try {
                storyDao.getActiveStoriesSync(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Error getting cached stories", e)
                emptyList()
            }
        }

    suspend fun queueStoryForUpload(
        localMediaPath: String,
        mediaType: String
    ): Long = withContext(Dispatchers.IO) {
        try {
            val actionData = mapOf(
                "localMediaPath" to localMediaPath,
                "mediaType" to mediaType,
                "timestamp" to System.currentTimeMillis()
            )

            val pendingAction = PendingAction(
                actionType = "upload_story",
                dataJson = gson.toJson(actionData),
                timestamp = System.currentTimeMillis()
            )

            pendingActionDao.insertAction(pendingAction)
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing story", e)
            -1L
        }
    }

    // ==================== General Actions ====================

    suspend fun queueAction(
        actionType: String,
        actionData: Map<String, Any>
    ): Long = withContext(Dispatchers.IO) {
        try {
            val pendingAction = PendingAction(
                actionType = actionType,
                dataJson = gson.toJson(actionData),
                timestamp = System.currentTimeMillis()
            )

            pendingActionDao.insertAction(pendingAction)
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing action", e)
            -1L
        }
    }

    suspend fun getPendingActions(): List<PendingAction> =
        withContext(Dispatchers.IO) {
            try {
                pendingActionDao.getPendingActions()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting pending actions", e)
                emptyList()
            }
        }

    suspend fun updateActionStatus(
        action: PendingAction,
        status: String,
        errorMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val updatedAction = action.copy(
                status = status,
                errorMessage = errorMessage,
                retryCount = if (status == "failed") action.retryCount + 1 else action.retryCount
            )
            pendingActionDao.updateAction(updatedAction)
            Log.d(TAG, "Action updated: ${action.id}, status: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating action status", e)
        }
    }

    suspend fun deleteAction(action: PendingAction) = withContext(Dispatchers.IO) {
        try {
            pendingActionDao.deleteAction(action)
            Log.d(TAG, "Action deleted: ${action.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting action", e)
        }
    }

    suspend fun getPendingActionsCount(): Int = withContext(Dispatchers.IO) {
        try {
            pendingActionDao.getPendingActionsCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending actions count", e)
            0
        }
    }

    // ==================== Cleanup ====================

    suspend fun cleanupOldData() = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000L)

            // Delete old posts (older than 7 days)
            postDao.deleteOldPosts(sevenDaysAgo)

            // Delete expired stories
            storyDao.deleteExpiredStories(currentTime)

            // Delete old completed actions (older than 7 days)
            pendingActionDao.deleteOldCompletedActions(sevenDaysAgo)

            Log.d(TAG, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    // ==================== File Handling ====================

    fun saveImageLocally(uri: Uri, prefix: String = "img"): String? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            inputStream.close()
            Log.d(TAG, "Image saved locally: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image locally", e)
            return null
        }
    }

    fun saveImageLocally(bitmap: Bitmap, prefix: String = "img"): String? {
        try {
            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            Log.d(TAG, "Image saved locally: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image locally", e)
            return null
        }
    }

    fun getLocalFile(path: String): File? {
        return try {
            val file = File(path)
            if (file.exists()) file else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local file", e)
            null
        }
    }

    suspend fun deleteMessageById(messageId: String) = withContext(Dispatchers.IO) {
        try {
            messageDao.deleteMessageById(messageId)
            Log.d(TAG, "Deleted message by ID: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message by ID", e)
        }
    }

    suspend fun getCachedChats(currentUserId: String): List<CachedChat> = withContext(Dispatchers.IO) {
        try {
            database.chatDao().getChatsForUser(currentUserId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached chats", e)
            emptyList()
        }
    }

    suspend fun cacheChat(
        id: String,
        userId: String,
        otherUserId: String,
        otherUsername: String?,
        otherProfilePic: String?,
        lastMessage: String?,
        lastTimestamp: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val cachedChat = CachedChat(
                id = id,
                userId = userId,
                otherUserId = otherUserId,
                otherUsername = otherUsername,
                otherProfilePic = otherProfilePic,
                lastMessage = lastMessage,
                lastTimestamp = lastTimestamp
            )
            database.chatDao().insertChat(cachedChat)
            Log.d(TAG, "Chat cached: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching chat", e)
        }
    }
}
