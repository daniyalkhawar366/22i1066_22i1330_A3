package com.example.a22i1066_b_socially

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
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
import com.example.a22i1066_b_socially.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.text.clear

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
    private val client = OkHttpClient()
    private var isUploadingImages = false
    private var isPolling = false

    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dihbswob7/image/upload"
    private val UPLOAD_PRESET = "mobile_unsigned_preset"

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

        val defaultDrawable = R.drawable.profileicon.takeIf { resources.getIdentifier("profileicon", "drawable", packageName) != 0 } ?: R.drawable.bilal
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
    }

    override fun onPause() {
        super.onPause()
        isPolling = false
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
                val response = RetrofitClient.instance.getMessages(
                    "Bearer $token",
                    chatId,
                    limit = 50
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val messageItems = response.body()?.messages ?: emptyList<com.example.a22i1066_b_socially.network.MessageItem>()

                    messages.clear()
                    messageItems.forEach { msg: com.example.a22i1066_b_socially.network.MessageItem ->
                        messages.add(
                            Message(
                                id = msg.id,
                                text = msg.text,
                                senderId = msg.senderId,
                                timestamp = msg.timestamp,
                                imageUrls = msg.imageUrls,
                                type = msg.type
                            )
                        )
                    }

                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                        messageRecyclerView.scrollToPosition(messages.size - 1)
                    }
                } else if (!silent) {
                    Toast.makeText(this@ChatDetailActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                if (!silent) {
                    Toast.makeText(this@ChatDetailActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
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
            uploadImageToCloudinary(uri) { success, url ->
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

    private fun uploadImageToCloudinary(imageUri: Uri, callback: (Boolean, String?) -> Unit) {
        val bytes = try {
            contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image", e)
            null
        }

        if (bytes == null) {
            callback(false, null)
            return
        }

        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = bytes.toRequestBody(mediaType)
        val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "upload.jpg", fileBody)
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .build()

        val request = Request.Builder()
            .url(CLOUDINARY_URL)
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Cloudinary upload failed", e)
                runOnUiThread {
                    callback(false, null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Cloudinary error: ${it.code}")
                        runOnUiThread { callback(false, null) }
                        return
                    }

                    val bodyStr = it.body?.string().orEmpty()
                    try {
                        val json = JSONObject(bodyStr)
                        val secureUrl = json.optString("secure_url", json.optString("url"))
                        runOnUiThread {
                            callback(secureUrl.isNotBlank(), secureUrl.takeIf { url -> url.isNotBlank() })
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response", e)
                        runOnUiThread { callback(false, null) }
                    }
                }
            }
        })
    }

    private fun sendImageMessage(imageUrls: List<String>) {
        val text = messageInput.text.toString().trim()
        val token = sessionManager.getToken() ?: return

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
                    messageInput.setText("")
                    loadMessages()
                } else {
                    Toast.makeText(this@ChatDetailActivity, "Failed to send", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending image message", e)
                Toast.makeText(this@ChatDetailActivity, "Network error", Toast.LENGTH_SHORT).show()
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

        sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.sendMessage(
                    token = "Bearer $token",
                    request = SendMessageRequest(
                        receiverId = receiverUserId,
                        text = text,
                        imageUrls = emptyList()
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    messageInput.setText("")
                    loadMessages()
                } else {
                    Toast.makeText(this@ChatDetailActivity, "Failed to send", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                Toast.makeText(this@ChatDetailActivity, "Network error", Toast.LENGTH_SHORT).show()
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

        val callTypeStr = requestedType
        CallSession.start(requestedChatId, callTypeStr)

        val intent = if (isVideo) {
            android.content.Intent(this, VideoCallActivity::class.java)
        } else {
            android.content.Intent(this, CallActivity::class.java)
        }.apply {
            putExtra("CHAT_ID", requestedChatId ?: "")
            putExtra("RECEIVER_USER_ID", receiverUserId)
            putExtra("RECEIVER_USERNAME", receiverUsername)
            putExtra("RECEIVER_PROFILE_URL", receiverProfileUrl)
            putExtra("CURRENT_USER_ID", currentUserId)
            putExtra("CALL_TYPE", callTypeStr)
        }

        startActivity(intent)
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

    override fun onDestroy() {
        super.onDestroy()
        screenshotObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister observer", e)
            }
        }
        isPolling = false
    }
}
