package com.example.a22i1066_b_socially

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ChatDetailActivity : AppCompatActivity() {

    private val TAG = "ChatDetailActivity"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
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

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter
    private var messagesListener: ListenerRegistration? = null

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

        currentUserId = intent.getStringExtra("CURRENT_USER_ID")?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.uid ?: ""

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
        listenMessages()
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
        val authUid = auth.currentUser?.uid

        if (authUid.isNullOrBlank()) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val colRef = db.collection("chats").document(chatId).collection("messages")
        val docRef = colRef.document()

        val messageType = when {
            imageUrls.isNotEmpty() && text.isNotEmpty() -> "mixed"
            imageUrls.isNotEmpty() -> "image"
            else -> "text"
        }

        val payload = mutableMapOf<String, Any>(
            "senderId" to currentUserId,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to messageType
        )

        if (imageUrls.isNotEmpty()) {
            payload["imageUrls"] = imageUrls
        }

        docRef.set(payload)
            .addOnSuccessListener {
                messageInput.setText("")
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send message", e)
                Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show()
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

        val requestData = mapOf(
            "type" to "screenshot",
            "senderId" to currentUserId,
            "receiverId" to receiverUserId,
            "chatId" to chatId,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("notification_requests")
            .add(requestData)
            .addOnSuccessListener {
                Log.d(TAG, "Screenshot notification queued")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to queue screenshot notification", e)
            }
    }

    private fun listenMessages() {
        messagesListener?.remove()
        messages.clear()
        adapter.notifyDataSetChanged()

        val colRef = db.collection("chats").document(chatId).collection("messages")
        messagesListener = colRef.orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Listen failed", err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                for (dc in snap.documentChanges) {
                    val doc = dc.document
                    val id = doc.id
                    val senderId = doc.getString("senderId") ?: ""
                    val text = doc.getString("text") ?: ""
                    val type = doc.getString("type") ?: "text"

                    // Parse imageUrls array safely
                    val imageUrls = try {
                        @Suppress("UNCHECKED_CAST")
                        (doc.get("imageUrls") as? List<String>) ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse imageUrls for message $id", e)
                        emptyList<String>()
                    }

                    val ts = doc.getTimestamp("timestamp")
                    val tsMillis = ts?.toDate()?.time ?: System.currentTimeMillis()

                    val message = Message(
                        id = id,
                        text = text,
                        senderId = senderId,
                        timestamp = tsMillis,
                        imageUrls = imageUrls,
                        type = type
                    )

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            val idx = messages.indexOfFirst { it.id == id }
                            if (idx == -1) {
                                messages.add(message)
                                adapter.notifyItemInserted(messages.size - 1)
                                messageRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            val idx = messages.indexOfFirst { it.id == id }
                            if (idx != -1) {
                                messages[idx] = message
                                adapter.notifyItemChanged(idx)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            val idx = messages.indexOfFirst { it.id == id }
                            if (idx != -1) {
                                messages.removeAt(idx)
                                adapter.notifyItemRemoved(idx)
                            }
                        }
                    }
                }
            }
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
                    db.collection("chats").document(chatId).collection("messages")
                        .document(message.id)
                        .update("text", newText)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Message updated", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to update message", e)
                            Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
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
                db.collection("chats").document(chatId).collection("messages")
                    .document(message.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to delete message", e)
                        Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        if (currentUserId.isBlank()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        if (receiverUserId.isBlank()) {
            Toast.makeText(this, "Receiver not specified", Toast.LENGTH_SHORT).show()
            return
        }

        val authUid = auth.currentUser?.uid
        if (authUid.isNullOrBlank()) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val colRef = db.collection("chats").document(chatId).collection("messages")
        val docRef = colRef.document()

        val payload = mapOf(
            "senderId" to currentUserId,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to "text"
        )

        sendButton.isEnabled = false

        docRef.set(payload)
            .addOnSuccessListener {
                messageInput.setText("")
                sendButton.isEnabled = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send message", e)
                Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show()
                sendButton.isEnabled = true
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
            Intent(this, VideoCallActivity::class.java)
        } else {
            Intent(this, CallActivity::class.java)
        }

        // Pass all required data dynamically
        intent.putExtra("CHAT_ID", requestedChatId ?: "")
        intent.putExtra("RECEIVER_USER_ID", receiverUserId)
        intent.putExtra("RECEIVER_USERNAME", receiverUsername)
        intent.putExtra("RECEIVER_PROFILE_URL", receiverProfileUrl)
        intent.putExtra("CURRENT_USER_ID", currentUserId)
        intent.putExtra("CALL_TYPE", callTypeStr)

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
        messagesListener?.remove()
        messagesListener = null
    }
}
