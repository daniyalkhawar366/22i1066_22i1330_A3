package com.example.a22i1066_b_socially

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.example.a22i1066_b_socially.offline.*
import kotlinx.coroutines.launch

/**
 * Example Activity demonstrating offline support usage
 * This shows how to integrate offline features in your activities
 */
class OfflineExampleActivity : AppCompatActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var offlineManager: OfflineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize offline manager
        offlineManager = OfflineManager(this)

        // Setup network monitoring
        setupNetworkMonitoring()

        // Example: Load images with offline support
        loadImagesExample()

        // Example: Queue offline action
        queueOfflineActionExample()

        // Example: Retrieve cached data
        getCachedDataExample()
    }

    private fun setupNetworkMonitoring() {
        networkMonitor = NetworkMonitor(this)
        networkMonitor.isOnline.observe(this) { isOnline ->
            if (isOnline) {
                Toast.makeText(this, "Back online! Syncing data...", Toast.LENGTH_SHORT).show()
                // Trigger immediate sync when coming online
                SyncWorker.scheduleImmediateSync(this)
            } else {
                Toast.makeText(this, "You are offline", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImagesExample() {
        // Example: Load profile picture with offline caching
        // Note: This is just an example - you'd use your actual ImageView
        val profileImageView = ImageView(this) // Create example ImageView
        val profilePicUrl = "https://example.com/profile.jpg"

        // Using extension function - automatically caches for offline use
        profileImageView.loadImageOffline(
            url = profilePicUrl,
            placeholder = R.drawable.ic_launcher_foreground,
            error = R.drawable.ic_launcher_foreground,
            circular = true  // Makes it circular for profile pics
        )

        // You can also prefetch images for offline use
        PicassoImageLoader.prefetchImages(listOf(
            "https://example.com/image1.jpg",
            "https://example.com/image2.jpg"
        ))
    }

    private fun queueOfflineActionExample() {
        lifecycleScope.launch {
            // Check if online before performing action
            if (isOnline()) {
                // Perform online action directly
                sendMessageOnline()
            } else {
                // Queue for offline sync
                queueMessageOffline()
                showOfflineToast()
            }
        }
    }

    private suspend fun sendMessageOnline() {
        // Example: Send message directly when online
        try {
            val sessionManager = SessionManager(this)
            val token = sessionManager.getToken() ?: return

            val response = RetrofitClient.instance.sendMessage(
                "Bearer $token",
                com.example.a22i1066_b_socially.network.SendMessageRequest(
                    receiverId = "user123",
                    text = "Hello!",
                    imageUrls = emptyList()
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()

                // Cache the message locally for offline access
                val messageItem = response.body()?.message
                if (messageItem != null) {
                    offlineManager.cacheMessage(
                        id = messageItem.id,
                        chatId = "chat123",
                        senderId = sessionManager.getUserId() ?: "",
                        receiverId = "user123",
                        message = messageItem.text,
                        timestamp = messageItem.timestamp,
                        type = messageItem.type
                    )
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun queueMessageOffline() {
        // Queue message to be sent when online
        val sessionManager = SessionManager(this)
        val currentUserId = sessionManager.getUserId() ?: return

        val actionId = offlineManager.queueMessageForSending(
            chatId = "chat123",
            receiverId = "user123",
            message = "Hello! (sent offline)",
            type = "text"
        )

        if (actionId > 0) {
            // Also cache the message locally so it shows up immediately
            offlineManager.cacheMessage(
                id = "msg_${System.currentTimeMillis()}",
                chatId = "chat123",
                senderId = currentUserId,
                receiverId = "user123",
                message = "Hello! (sent offline)",
                timestamp = System.currentTimeMillis(),
                type = "text",
                isSent = false  // Mark as not sent yet
            )

            Toast.makeText(this, "Message queued. Will send when online.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCachedDataExample() {
        lifecycleScope.launch {
            // Example: Get cached messages for a chat
            @Suppress("UNUSED_VARIABLE")
            val messages = offlineManager.getMessagesForChat("chat123")
            // Use messages to populate UI even when offline

            // Example: Get cached posts
            @Suppress("UNUSED_VARIABLE")
            val posts = offlineManager.getCachedPosts(limit = 50)
            // Show posts even when offline

            // Example: Get cached stories
            @Suppress("UNUSED_VARIABLE")
            val stories = offlineManager.getCachedStories()
            // Display stories even when offline

            // Example: Check pending actions count
            val pendingCount = offlineManager.getPendingActionsCount()
            if (pendingCount > 0) {
                Toast.makeText(
                    this@OfflineExampleActivity,
                    "$pendingCount actions pending sync",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up network monitor
        networkMonitor.unregister()
    }
}

