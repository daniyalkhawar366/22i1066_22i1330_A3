package com.example.a22i1066_b_socially

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.example.a22i1066_b_socially.network.SendMessageRequest
import com.example.a22i1066_b_socially.network.EditMessageRequest
import com.example.a22i1066_b_socially.network.DeleteMessageRequest
import com.example.a22i1066_b_socially.offline.OfflineManager
import com.example.a22i1066_b_socially.offline.OfflineIntegrationHelper
import com.example.a22i1066_b_socially.offline.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ChatDetailActivity : AppCompatActivity() {

    private val TAG = "ChatDetailActivity"
    private var screenshotObserver: ContentObserver? = null
    private var lastScreenshotTime = 0L

    private lateinit var backButton: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var usernameText: TextView
    private lateinit var audioCallButton: ImageView
    private lateinit var videoCallButton: ImageView
    private lateinit var infoButton: ImageView

    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var cameraIcon: ImageView
    private lateinit var micIcon: ImageView
    private lateinit var galleryIcon: ImageView
    private lateinit var stickerIcon: ImageView
    private lateinit var sessionManager: SessionManager

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private var receiverUserId: String = ""
    private var receiverUsername: String = ""
    private var receiverProfileUrl: String? = null
    private var currentUserId: String = ""
    private var chatId: String = ""

    private val REQ_AUDIO = 3001
    private val REQ_VIDEO = 3002

    private val selectedImages = mutableListOf<Uri>()
    private val MAX_IMAGES = 10
    private var isUploadingImages = false
    private var isPolling = false
    private var isPollingIncomingCalls = false

    private lateinit var networkMonitor: NetworkMonitor


    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult

        selectedImages.clear()
        selectedImages.addAll(uris.take(MAX_IMAGES))

        Toast.makeText(this, "${selectedImages.size} image(s) selected", Toast.LENGTH_SHORT).show()
        uploadImagesAndSend()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        sessionManager = SessionManager(this)

        backButton = findViewById(R.id.backButton)
        profileImage = findViewById(R.id.profileImage)
        usernameText = findViewById(R.id.usernameText)
        audioCallButton = findViewById(R.id.audioCallButton)
        videoCallButton = findViewById(R.id.videoCallButton)
        infoButton = findViewById(R.id.infoButton)

        messageRecyclerView = findViewById(R.id.messageRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        cameraIcon = findViewById(R.id.cameraIcon)
        micIcon = findViewById(R.id.micIcon)
        galleryIcon = findViewById(R.id.galleryIcon)
        stickerIcon = findViewById(R.id.stickerIcon)

        receiverUserId = intent.getStringExtra("receiverUserId") ?: ""
        receiverUsername = intent.getStringExtra("receiverUsername") ?: ""
        receiverProfileUrl = intent.getStringExtra("RECEIVER_PROFILE_URL")

        currentUserId = sessionManager.getUserId() ?: ""

        if (currentUserId.isBlank()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (receiverUserId.isBlank()) {
            Toast.makeText(this, "Receiver not specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatId = if (currentUserId < receiverUserId) "${currentUserId}_$receiverUserId" else "${receiverUserId}_$currentUserId"

        usernameText.text = receiverUsername.ifBlank { "(unknown)" }

        val defaultDrawable = R.drawable.profileicon
        Glide.with(this)
            .load(receiverProfileUrl)
            .apply(RequestOptions.circleCropTransform())
            .placeholder(defaultDrawable)
            .error(defaultDrawable)
            .into(profileImage)

        adapter = MessageAdapter(messages, currentUserId,
            onEdit = { message -> editMessage(message) },
            onDelete = { message -> deleteMessage(message) }
        )
        messageRecyclerView.adapter = adapter
        messageRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        backButton.setOnClickListener { finish() }
        infoButton.setOnClickListener { Toast.makeText(this, "Info", Toast.LENGTH_SHORT).show() }

        sendButton.setOnClickListener { sendMessage() }

        audioCallButton.setOnClickListener { startCallWithPermission(isVideo = false) }
        videoCallButton.setOnClickListener { startCallWithPermission(isVideo = true) }

        cameraIcon.setOnClickListener { Toast.makeText(this, "Camera tapped", Toast.LENGTH_SHORT).show() }
        galleryIcon.setOnClickListener { openImagePicker() }
        stickerIcon.setOnClickListener { Toast.makeText(this, "Sticker tapped", Toast.LENGTH_SHORT).show() }
        micIcon.setOnClickListener { Toast.makeText(this, "Mic tapped", Toast.LENGTH_SHORT).show() }

        startScreenshotDetection()
        loadMessages()
        startPolling()
        startIncomingCallPolling()

        // Monitor network changes
        networkMonitor = NetworkMonitor(this)
        networkMonitor.isOnline.observe(this) { isOnline ->
            if (isOnline) {
                Log.d(TAG, "Network is back online - reloading messages")
                // Trigger immediate sync and reload messages
                com.example.a22i1066_b_socially.offline.SyncWorker.scheduleImmediateSync(this)
                // Give sync a moment to complete, then reload
                lifecycleScope.launch {
                    delay(2000) // Wait 2 seconds for sync to process
                    loadMessages(silent = true)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isPolling = false
        isPollingIncomingCalls = false
    }


    private fun startPolling() {
        isPolling = true
        lifecycleScope.launch {
            while (isPolling) {
                delay(2000) // Poll every 2 seconds
                loadMessages(silent = true)
            }
        }
    }

    private fun loadMessages(silent: Boolean = false) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank() || chatId.isBlank()) return

        lifecycleScope.launch {
            try {
                val offlineManager = OfflineManager(this@ChatDetailActivity)
                val isOnline = OfflineIntegrationHelper.isOnline(this@ChatDetailActivity)

                messages.clear()

                if (isOnline) {
                    // Online: Load from server and cache
                    try {
                        val response = RetrofitClient.instance.getMessages(
                            "Bearer $token",
                            chatId,
                            limit = 50
                        )

                        if (response.isSuccessful && response.body()?.success == true) {
                            val messageItems = response.body()?.messages ?: emptyList<com.example.a22i1066_b_socially.network.MessageItem>()

                            val serverMessageIds = mutableSetOf<String>()
                            val serverMessageContents = mutableSetOf<String>() // Track message text content

                            messageItems.forEach { msg ->
                                serverMessageIds.add(msg.id)
                                serverMessageContents.add("${msg.senderId}_${msg.text}_${msg.timestamp / 1000}") // Use second precision

                                // Cache each message
                                offlineManager.cacheMessage(
                                    id = msg.id,
                                    chatId = chatId,
                                    senderId = msg.senderId,
                                    receiverId = if (msg.senderId == currentUserId) receiverUserId else currentUserId,
                                    message = msg.text,
                                    timestamp = msg.timestamp,
                                    type = msg.type,
                                    imageUrl = msg.imageUrls.firstOrNull(),
                                    isSent = true
                                )

                                messages.add(
                                    Message(
                                        id = msg.id,
                                        text = msg.text,
                                        senderId = msg.senderId,
                                        timestamp = msg.timestamp,
                                        imageUrls = msg.imageUrls,
                                        type = msg.type,
                                        status = "sent"
                                    )
                                )
                            }

                            // Add any pending messages that are not on server yet
                            val cachedMessages = offlineManager.getMessagesForChat(chatId)
                            cachedMessages.forEach { cached ->
                                val cachedContent = "${cached.senderId}_${cached.message}_${cached.timestamp / 1000}"

                                // Only show if:
                                // 1. Not sent yet (isSent = false)
                                // 2. Not already on server (by ID)
                                // 3. Not already on server (by content - to catch duplicates with different IDs)
                                // 4. Is a pending_ ID (our format for offline messages)
                                if (!cached.isSent &&
                                    !serverMessageIds.contains(cached.id) &&
                                    !serverMessageContents.contains(cachedContent) &&
                                    cached.id.startsWith("pending_")) {

                                    Log.d(TAG, "Adding pending message: ${cached.id} - ${cached.message}")
                                    messages.add(
                                        Message(
                                            id = cached.id,
                                            text = cached.message,
                                            senderId = cached.senderId,
                                            timestamp = cached.timestamp,
                                            imageUrls = cached.imageUrl?.let { listOf(it) } ?: emptyList(),
                                            type = cached.type,
                                            status = "pending"
                                        )
                                    )
                                } else if (!cached.isSent && serverMessageContents.contains(cachedContent)) {
                                    // This pending message is now on server, clean it up
                                    Log.d(TAG, "Cleaning up pending message that's now on server: ${cached.id}")
                                    offlineManager.deleteMessageById(cached.id)
                                }
                            }
                        } else if (!silent) {
                            Toast.makeText(this@ChatDetailActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading from server, falling back to cache", e)
                        // Fall back to cache on network error
                        loadFromCache(offlineManager)
                    }
                } else {
                    // Offline: Load only from cache
                    loadFromCache(offlineManager)
                }

                // Sort by timestamp
                messages.sortBy { it.timestamp }

                withContext(Dispatchers.Main) {
                    // Use notifyDataSetChanged on main thread with proper synchronization
                    try {
                        adapter.notifyDataSetChanged()
                        if (messages.isNotEmpty()) {
                            messageRecyclerView.post {
                                messageRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating RecyclerView", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatDetailActivity, "Error loading messages", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun loadFromCache(offlineManager: OfflineManager) {
        val cachedMessages = offlineManager.getMessagesForChat(chatId)
        cachedMessages.forEach { cached ->
            messages.add(
                Message(
                    id = cached.id,
                    text = cached.message,
                    senderId = cached.senderId,
                    timestamp = cached.timestamp,
                    imageUrls = cached.imageUrl?.let { listOf(it) } ?: emptyList(),
                    type = cached.type,
                    status = if (cached.isSent) "sent" else "pending"
                )
            )
        }
    }


    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun uploadImagesAndSend() {
        if (selectedImages.isEmpty()) return
        if (isUploadingImages) return

        isUploadingImages = true
        sendButton.isEnabled = false
        galleryIcon.isEnabled = false

        Toast.makeText(this, "Uploading ${selectedImages.size} image(s)...", Toast.LENGTH_SHORT).show()

        val uploadedUrls = mutableListOf<String>()
        var uploadedCount = 0

        for (uri in selectedImages) {
            uploadImageToBackend(uri) { success, url ->
                uploadedCount++
                if (success && url != null) {
                    uploadedUrls.add(url)
                }

                if (uploadedCount == selectedImages.size) {
                    isUploadingImages = false
                    sendButton.isEnabled = true
                    galleryIcon.isEnabled = true

                    if (uploadedUrls.isNotEmpty()) {
                        sendImageMessage(uploadedUrls)
                        selectedImages.clear()
                    } else {
                        Toast.makeText(this, "All uploads failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun uploadImageToBackend(imageUri: Uri, callback: (Boolean, String?) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting image upload for URI: $imageUri")
                val inputStream = contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream for URI: $imageUri")
                    withContext(Dispatchers.Main) { callback(false, null) }
                    return@launch
                }

                val bytes = inputStream.readBytes()
                inputStream.close()
                Log.d(TAG, "Read ${bytes.size} bytes from image")

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        "message_image_${System.currentTimeMillis()}.jpg",
                        bytes.toRequestBody("image/*".toMediaTypeOrNull())
                    )
                    .build()

                val token = sessionManager.getToken() ?: ""
                val url = "http://192.168.18.55/backend/api/messages.php?action=uploadImage"
                Log.d(TAG, "Uploading to: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "Upload response code: ${response.code}")
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: "{}"
                        Log.d(TAG, "Upload response: $responseBody")
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.getBoolean("success")) {
                            val imageUrl = jsonResponse.getString("url")
                            Log.d(TAG, "Image uploaded successfully: $imageUrl")
                            withContext(Dispatchers.Main) { callback(true, imageUrl) }
                        } else {
                            val error = jsonResponse.optString("error", "Unknown error")
                            Log.e(TAG, "Upload failed: $error")
                            withContext(Dispatchers.Main) { callback(false, null) }
                        }
                    } else {
                        Log.e(TAG, "Upload request failed with code: ${response.code}")
                        withContext(Dispatchers.Main) { callback(false, null) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image upload failed with exception", e)
                withContext(Dispatchers.Main) { callback(false, null) }
            }
        }
    }

    private fun sendImageMessage(imageUrls: List<String>) {
        val text = messageInput.text.toString().trim()
        val token = sessionManager.getToken() ?: return

        Log.d(TAG, "Sending image message with ${imageUrls.size} images")

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.sendMessage(
                    token = "Bearer $token",
                    request = SendMessageRequest(
                        receiverId = receiverUserId,
                        text = text,
                        imageUrls = imageUrls
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Image message sent successfully")
                    messageInput.setText("")
                    loadMessages()
                    Toast.makeText(this@ChatDetailActivity, "Image(s) sent", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = response.body()?.error ?: "Unknown error"
                    Log.e(TAG, "Failed to send image message: $errorMsg")
                    Toast.makeText(this@ChatDetailActivity, "Failed to send: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending image message", e)
                Toast.makeText(this@ChatDetailActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startScreenshotDetection() {
        val handler = Handler(Looper.getMainLooper())
        screenshotObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val now = System.currentTimeMillis()
                if (now - lastScreenshotTime > 2000) {
                    lastScreenshotTime = now
                    onScreenshotDetected()
                }
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register observer", e)
        }
    }

    private fun onScreenshotDetected() {
        Log.d(TAG, "Screenshot detected in chat: $chatId")
        Toast.makeText(this, "Screenshot detected", Toast.LENGTH_SHORT).show()
    }

    private fun editMessage(message: Message) {
        if (message.imageUrls.isNotEmpty()) {
            Toast.makeText(this, "Cannot edit image messages", Toast.LENGTH_SHORT).show()
            return
        }

        val editText = EditText(this).apply { setText(message.text) }
        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    val token = sessionManager.getToken() ?: return@setPositiveButton

                    lifecycleScope.launch {
                        try {
                            val response = RetrofitClient.instance.editMessage(
                                token = "Bearer $token",
                                request = EditMessageRequest(
                                    messageId = message.id,
                                    text = newText
                                )
                            )

                            if (response.isSuccessful && response.body()?.success == true) {
                                Toast.makeText(this@ChatDetailActivity, "Message updated", Toast.LENGTH_SHORT).show()
                                loadMessages()
                            } else {
                                Toast.makeText(this@ChatDetailActivity, "Failed to update", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error editing message", e)
                            Toast.makeText(this@ChatDetailActivity, "Network error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(message: Message) {
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                val token = sessionManager.getToken() ?: return@setPositiveButton

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.deleteMessage(
                            token = "Bearer $token",
                            request = DeleteMessageRequest(messageId = message.id)
                        )

                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@ChatDetailActivity, "Message deleted", Toast.LENGTH_SHORT).show()
                            loadMessages()
                        } else {
                            Toast.makeText(this@ChatDetailActivity, "Failed to delete", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting message", e)
                        Toast.makeText(this@ChatDetailActivity, "Network error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val token = sessionManager.getToken() ?: return
        val currentUserId = sessionManager.getUserId() ?: return

        sendButton.isEnabled = false
        Log.d(TAG, "Sending text message to: $receiverUserId")

        lifecycleScope.launch {
            try {
                // Check if online
                val isOnline = OfflineIntegrationHelper.isOnline(this@ChatDetailActivity)

                if (!isOnline) {
                    val currentTime = System.currentTimeMillis()

                    // Check if this exact message already exists (prevent duplicates)
                    val alreadyQueued = messages.any {
                        it.status == "pending" && it.text == text && it.senderId == currentUserId
                    }

                    if (alreadyQueued) {
                        Log.w(TAG, "Message already queued, skipping duplicate")
                        messageInput.setText("")
                        sendButton.isEnabled = true
                        return@launch
                    }

                    // Queue message for offline sending
                    val offlineManager = OfflineManager(this@ChatDetailActivity)
                    val actionId = offlineManager.queueMessageForSending(
                        chatId = chatId,
                        receiverId = receiverUserId,
                        message = text,
                        type = "text"
                    )

                    if (actionId > 0) {
                        // Get the cached message to show in UI
                        val cachedMessages = offlineManager.getMessagesForChat(chatId)
                        val newlyCachedMessage = cachedMessages.lastOrNull {
                            !it.isSent && it.message == text
                        }

                        if (newlyCachedMessage != null) {
                            // Add to UI immediately with pending status
                            val newMessage = Message(
                                id = newlyCachedMessage.id,
                                text = newlyCachedMessage.message,
                                senderId = currentUserId,
                                timestamp = newlyCachedMessage.timestamp,
                                imageUrls = emptyList(),
                                type = "text",
                                status = "pending"
                            )

                            val position = messages.size
                            messages.add(newMessage)
                            messageInput.setText("")

                            // Update UI with specific item insertion
                            runOnUiThread {
                                adapter.notifyItemInserted(position)
                                messageRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        }

                        // Don't show toast, just visual indicator
                    } else {
                        Toast.makeText(
                            this@ChatDetailActivity,
                            "Failed to queue message",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    sendButton.isEnabled = true
                    return@launch
                }

                // Send online
                val response = RetrofitClient.instance.sendMessage(
                    token = "Bearer $token",
                    request = SendMessageRequest(
                        receiverId = receiverUserId,
                        text = text,
                        imageUrls = emptyList()
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Text message sent successfully")
                    messageInput.setText("")

                    // Cache the message for offline access
                    val offlineManager = OfflineManager(this@ChatDetailActivity)
                    response.body()?.message?.let { msg ->
                        offlineManager.cacheMessage(
                            id = msg.id,
                            chatId = chatId,
                            senderId = currentUserId,
                            receiverId = receiverUserId,
                            message = msg.text,
                            timestamp = msg.timestamp,
                            type = msg.type,
                            isSent = true
                        )
                    }

                    loadMessages()

                    // Trigger immediate sync to ensure any pending messages are sent
                    com.example.a22i1066_b_socially.offline.SyncWorker.scheduleImmediateSync(this@ChatDetailActivity)
                } else {
                    val errorMsg = response.body()?.error ?: "Unknown error"
                    Log.e(TAG, "Failed to send message: $errorMsg")
                    Toast.makeText(this@ChatDetailActivity, "Failed to send: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)

                // If network error, treat as offline
                val tempMessageId = "pending_${System.currentTimeMillis()}_${(0..999).random()}"
                val currentTime = System.currentTimeMillis()
                val offlineManager = OfflineManager(this@ChatDetailActivity)

                // Queue for sending
                offlineManager.queueMessageForSending(
                    chatId = chatId,
                    receiverId = receiverUserId,
                    message = text,
                    type = "text"
                )

                // Cache as pending
                offlineManager.cacheMessage(
                    id = tempMessageId,
                    chatId = chatId,
                    senderId = currentUserId,
                    receiverId = receiverUserId,
                    message = text,
                    timestamp = currentTime,
                    type = "text",
                    isSent = false
                )

                // Add to UI immediately
                messages.add(
                    Message(
                        id = tempMessageId,
                        text = text,
                        senderId = currentUserId,
                        timestamp = currentTime,
                        imageUrls = emptyList(),
                        type = "text",
                        status = "pending"
                    )
                )

                messageInput.setText("")

                // Update UI
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                    messageRecyclerView.scrollToPosition(messages.size - 1)
                }
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun startCallWithPermission(isVideo: Boolean) {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO)
        if (isVideo && !hasPermission(Manifest.permission.CAMERA)) needed.add(Manifest.permission.CAMERA)

        if (needed.isNotEmpty()) {
            val reqCode = if (isVideo) REQ_VIDEO else REQ_AUDIO
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), reqCode)
            return
        }

        startCall(isVideo)
    }

    private fun startCall(isVideo: Boolean) {
        val requestedType = if (isVideo) "video" else "audio"
        val requestedChatId = chatId.takeIf { it.isNotBlank() }

        if (CallSession.isActive) {
            Toast.makeText(this, "Already in a call", Toast.LENGTH_SHORT).show()
            return
        }

        // Initiate call via PHP backend
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken() ?: ""
                val response = RetrofitClient.instance.initiateCall(
                    "Bearer $token",
                    com.example.a22i1066_b_socially.network.InitiateCallRequest(
                        receiverId = receiverUserId,
                        callType = requestedType
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val callId = response.body()?.callId ?: ""
                    val channelName = response.body()?.channelName ?: requestedChatId ?: ""

                    CallSession.start(channelName, requestedType)

                    // Use CallActivity for both audio and video calls
                    val intent = android.content.Intent(this@ChatDetailActivity, CallActivity::class.java).apply {
                        putExtra("CHAT_ID", channelName)
                        putExtra("CALL_ID", callId)
                        putExtra("RECEIVER_USER_ID", receiverUserId)
                        putExtra("RECEIVER_USERNAME", receiverUsername)
                        putExtra("RECEIVER_PROFILE_URL", receiverProfileUrl)
                        putExtra("CURRENT_USER_ID", currentUserId)
                        putExtra("CALL_TYPE", requestedType) // "audio" or "video"
                    }

                    startActivity(intent)
                } else {
                    // Check if user is offline
                    val errorBody = response.body()
                    if (errorBody?.isOnline == false) {
                        val username = errorBody.username ?: receiverUsername
                        runOnUiThread {
                            Toast.makeText(
                                this@ChatDetailActivity,
                                "$username is offline",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@ChatDetailActivity,
                                "Failed to initiate call: ${errorBody?.error}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating call", e)
                runOnUiThread {
                    Toast.makeText(
                        this@ChatDetailActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val grantedAll = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (!grantedAll) {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            return
        }
        when (requestCode) {
            REQ_AUDIO -> startCall(isVideo = false)
            REQ_VIDEO -> startCall(isVideo = true)
        }
    }

    private fun startIncomingCallPolling() {
        isPollingIncomingCalls = true
        lifecycleScope.launch {
            while (isPollingIncomingCalls) {
                try {
                    val token = sessionManager.getToken() ?: ""
                    if (token.isNotBlank()) {
                        val response = RetrofitClient.instance.pollIncomingCall("Bearer $token")
                        if (response.isSuccessful && response.body()?.success == true) {
                            val hasIncoming = response.body()?.hasIncomingCall ?: false
                            if (hasIncoming) {
                                val call = response.body()?.call
                                if (call != null) {
                                    Log.d(TAG, "Incoming call detected: ${call.callerId}")
                                    val intent = Intent(this@ChatDetailActivity, IncomingCallActivity::class.java)
                                    intent.putExtra("CALL_ID", call.callId)
                                    intent.putExtra("CHAT_ID", call.channelName)
                                    intent.putExtra("CALLER_USER_ID", call.callerId)
                                    intent.putExtra("CALLER_USERNAME", call.callerUsername)
                                    intent.putExtra("CALLER_PROFILE_URL", call.callerProfileUrl)
                                    intent.putExtra("CURRENT_USER_ID", currentUserId)
                                    intent.putExtra("CALL_TYPE", call.callType)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    startActivity(intent)
                                    isPollingIncomingCalls = false
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling incoming calls", e)
                }
                delay(2000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup network monitor
        networkMonitor.unregister()

        screenshotObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister observer", e)
            }
        }
        isPolling = false
        isPollingIncomingCalls = false
    }
}
