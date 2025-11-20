package com.example.a22i1066_b_socially.offline

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.a22i1066_b_socially.SessionManager
import com.example.a22i1066_b_socially.database.PendingAction
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val offlineManager = OfflineManager(context)
    private val sessionManager = SessionManager(context)
    private val gson = Gson()

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_COUNT = 3
        const val WORK_NAME = "sync_offline_data"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "Periodic sync scheduled")
        }

        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
            Log.d(TAG, "Immediate sync scheduled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync work")

        try {
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No auth token available")
                return@withContext Result.failure()
            }

            val pendingActions = offlineManager.getPendingActions()
            Log.d(TAG, "Found ${pendingActions.size} pending actions to sync")

            if (pendingActions.isEmpty()) {
                return@withContext Result.success()
            }

            var successCount = 0
            var failureCount = 0

            for (action in pendingActions) {
                try {
                    // Skip if retry limit exceeded
                    if (action.retryCount >= MAX_RETRY_COUNT) {
                        Log.w(TAG, "Action ${action.id} exceeded retry limit, marking as failed")
                        offlineManager.updateActionStatus(action, "failed", "Max retries exceeded")
                        failureCount++
                        continue
                    }

                    // Update status to processing
                    offlineManager.updateActionStatus(action, "processing")

                    val success = processAction(action, token)

                    if (success) {
                        offlineManager.updateActionStatus(action, "completed")
                        successCount++
                        Log.d(TAG, "Action ${action.id} completed successfully")
                    } else {
                        offlineManager.updateActionStatus(action, "pending", "Failed to process")
                        failureCount++
                        Log.e(TAG, "Action ${action.id} failed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing action ${action.id}", e)
                    offlineManager.updateActionStatus(action, "pending", e.message)
                    failureCount++
                }
            }

            Log.d(TAG, "Sync completed: $successCount successful, $failureCount failed")

            // Cleanup old data
            offlineManager.cleanupOldData()

            return@withContext if (failureCount > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed", e)
            return@withContext Result.retry()
        }
    }

    private suspend fun processAction(action: PendingAction, token: String): Boolean {
        return try {
            when (action.actionType) {
                "send_message" -> processSendMessage(action, token)
                "create_post" -> processCreatePost(action, token)
                "upload_story" -> processUploadStory(action, token)
                "like_post" -> processLikePost(action, token)
                "unlike_post" -> processUnlikePost(action, token)
                "save_post" -> processSavePost(action, token)
                "unsave_post" -> processUnsavePost(action, token)
                "add_comment" -> processAddComment(action, token)
                "follow_user" -> processFollowUser(action, token)
                "unfollow_user" -> processUnfollowUser(action, token)
                else -> {
                    Log.w(TAG, "Unknown action type: ${action.actionType}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing action type: ${action.actionType}", e)
            false
        }
    }

    private suspend fun processSendMessage(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val receiverId = data["receiverId"] as? String ?: return false
            val message = data["message"] as? String ?: ""
            val type = data["type"] as? String ?: "text"
            val localImagePath = data["localImagePath"] as? String
            val chatId = data["chatId"] as? String

            if (type == "image" && localImagePath != null) {
                // Upload image first
                val imageFile = File(localImagePath)
                if (!imageFile.exists()) {
                    Log.e(TAG, "Image file not found: $localImagePath")
                    return false
                }

                val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
                val receiverPart = receiverId.toRequestBody("text/plain".toMediaTypeOrNull())

                val uploadResponse = RetrofitClient.instance.uploadMessageImage(
                    "Bearer $token",
                    imagePart,
                    receiverPart
                )

                if (uploadResponse.isSuccessful && uploadResponse.body()?.success == true) {
                    val imageUrl = uploadResponse.body()?.url ?: return false

                    // Send message with image URL
                    val sendResponse = RetrofitClient.instance.sendMessage(
                        "Bearer $token",
                        com.example.a22i1066_b_socially.network.SendMessageRequest(
                            receiverId = receiverId,
                            text = message,
                            imageUrls = listOf(imageUrl)
                        )
                    )

                    if (sendResponse.isSuccessful && sendResponse.body()?.success == true) {
                        // Clean up pending cached messages for this chat
                        if (chatId != null) {
                            cleanupPendingMessages(chatId)
                        }
                        return true
                    }
                    return false
                } else {
                    return false
                }
            } else {
                // Send text message
                val response = RetrofitClient.instance.sendMessage(
                    "Bearer $token",
                    com.example.a22i1066_b_socially.network.SendMessageRequest(
                        receiverId = receiverId,
                        text = message
                    )
                )

                val success = response.isSuccessful && response.body()?.success == true

                // Clean up pending cached messages for this chat if successful
                if (success && chatId != null) {
                    cleanupPendingMessages(chatId)
                }

                return success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    private suspend fun cleanupPendingMessages(chatId: String) {
        try {
            val offlineManager = OfflineManager(applicationContext)
            val cachedMessages = offlineManager.getMessagesForChat(chatId)

            // Remove all pending messages from cache (they're now on server)
            cachedMessages.filter { !it.isSent && it.id.startsWith("pending_") }.forEach { msg ->
                // Use public method to delete by ID
                offlineManager.deleteMessageById(msg.id)
                Log.d(TAG, "Cleaned up pending message: ${msg.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up pending messages", e)
        }
    }

    private suspend fun processCreatePost(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val caption = data["caption"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val localImagePaths = data["localImagePaths"] as? List<String> ?: return false

            val imageParts = mutableListOf<MultipartBody.Part>()

            for (imagePath in localImagePaths) {
                val imageFile = File(imagePath)
                if (!imageFile.exists()) {
                    Log.e(TAG, "Image file not found: $imagePath")
                    continue
                }

                val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("images[]", imageFile.name, requestFile)
                imageParts.add(imagePart)
            }

            if (imageParts.isEmpty()) {
                Log.e(TAG, "No valid images found for post")
                return false
            }

            val captionPart = caption.toRequestBody("text/plain".toMediaTypeOrNull())

            // Use the multipart method with different name
            val response = RetrofitClient.instance.createPostMultipart(
                "Bearer $token",
                imageParts,
                captionPart
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating post", e)
            false
        }
    }

    private suspend fun processUploadStory(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val localMediaPath = data["localMediaPath"] as? String ?: return false
            val mediaType = data["mediaType"] as? String ?: "image"

            val mediaFile = File(localMediaPath)
            if (!mediaFile.exists()) {
                Log.e(TAG, "Media file not found: $localMediaPath")
                return false
            }

            val requestFile = mediaFile.asRequestBody("${mediaType}/*".toMediaTypeOrNull())
            val mediaPart = MultipartBody.Part.createFormData("media", mediaFile.name, requestFile)
            val typePart = mediaType.toRequestBody("text/plain".toMediaTypeOrNull())

            // Use the multipart method with different name
            val response = RetrofitClient.instance.uploadStoryMultipart(
                "Bearer $token",
                mediaPart,
                typePart
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading story", e)
            false
        }
    }

    private suspend fun processLikePost(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val postId = data["postId"] as? String ?: return false

            val response = RetrofitClient.instance.togglePostLike(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.ToggleLikeRequest(postId = postId)
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error liking post", e)
            false
        }
    }

    private suspend fun processUnlikePost(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val postId = data["postId"] as? String ?: return false

            val response = RetrofitClient.instance.togglePostLike(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.ToggleLikeRequest(postId = postId)
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error unliking post", e)
            false
        }
    }

    private suspend fun processSavePost(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val postId = data["postId"] as? String ?: return false

            // Note: You may need to implement savePost endpoint in your backend
            // For now, using togglePostLike as fallback
            val response = RetrofitClient.instance.togglePostLike(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.ToggleLikeRequest(postId = postId)
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving post", e)
            false
        }
    }

    private suspend fun processUnsavePost(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val postId = data["postId"] as? String ?: return false

            // Note: You may need to implement unsavePost endpoint in your backend
            // For now, using togglePostLike as fallback
            val response = RetrofitClient.instance.togglePostLike(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.ToggleLikeRequest(postId = postId)
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error unsaving post", e)
            false
        }
    }

    private suspend fun processAddComment(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val postId = data["postId"] as? String ?: return false
            val comment = data["comment"] as? String ?: return false

            val response = RetrofitClient.instance.addComment(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.AddCommentRequest(
                    postId = postId,
                    commentId = "comment_${System.currentTimeMillis()}",
                    text = comment,
                    timestamp = System.currentTimeMillis()
                )
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding comment", e)
            false
        }
    }

    private suspend fun processFollowUser(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val userId = data["userId"] as? String ?: return false

            val response = RetrofitClient.instance.followUser(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.FollowRequest(targetUserId = userId)
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error following user", e)
            false
        }
    }

    private suspend fun processUnfollowUser(action: PendingAction, token: String): Boolean {
        return try {
            val dataType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(action.dataJson, dataType)

            val userId = data["userId"] as? String ?: return false

            val response = RetrofitClient.instance.unfollowUser(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.FollowRequest(targetUserId = userId)
            )

            return response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Error unfollowing user", e)
            false
        }
    }
}
