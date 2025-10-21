// kotlin
package com.example.a22i1066_b_socially

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoEncoderConfiguration
import io.agora.rtc2.Constants
import java.util.concurrent.TimeUnit

class CallActivity : AppCompatActivity() {

    companion object {
        private const val AGORA_APP_ID = "caca39104a564ed5b5ee36350148f043"
        private const val KEY_START_MILLIS = "KEY_START_MILLIS"
        private const val KEY_JOINED = "KEY_JOINED"
    }

    private val db = FirebaseFirestore.getInstance()
    private var rtcEngine: RtcEngine? = null

    private lateinit var profileImage: ImageView
    private lateinit var contactName: TextView
    private lateinit var callDuration: TextView
    private lateinit var endCallButton: ImageView
    private lateinit var chatButton: ImageView
    private lateinit var speakerButton: ImageView

    private var chatId: String = ""
    private var receiverUserId: String = ""
    private var receiverUsername: String = ""
    private var currentUserIdForChat: String = ""
    private var callType: String = "audio" // "audio" or "video"

    private var startMillis: Long = 0L
    private var isJoinedChannel: Boolean = false
    private var isSpeakerOn: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - startMillis
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
            callDuration.text = String.format("%02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        if (granted) {
            setupAgoraAndJoin()
        } else {
            Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            // mark joined if not already and ensure timer runs
            if (!isJoinedChannel) {
                isJoinedChannel = true
                if (startMillis == 0L) startMillis = System.currentTimeMillis()
                handler.post(tick)
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                if (callType == "video") {
                    // placeholder for remote video handling if required
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Toast.makeText(this@CallActivity, "User left", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ammancall)

        profileImage = findViewById(R.id.profileImage)
        contactName = findViewById(R.id.contactName)
        callDuration = findViewById(R.id.callDuration)
        endCallButton = findViewById(R.id.endCallButton)
        chatButton = findViewById(R.id.chatButton)
        speakerButton = findViewById(R.id.speakerButton)

        // read intent extras (consolidated)
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        receiverUserId = intent.getStringExtra("RECEIVER_USER_ID")
            ?: intent.getStringExtra("RECEIVER_ID")
                    ?: intent.getStringExtra("RECEIVER")
                    ?: ""
        receiverUsername = intent.getStringExtra("RECEIVER_USERNAME")
            ?: intent.getStringExtra("RECEIVER_NAME")
                    ?: ""
        callType = intent.getStringExtra("CALL_TYPE") ?: "audio"
        currentUserIdForChat = intent.getStringExtra("CURRENT_USER_ID")?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        contactName.text = receiverUsername

        if (savedInstanceState != null) {
            startMillis = savedInstanceState.getLong(KEY_START_MILLIS, 0L)
            isJoinedChannel = savedInstanceState.getBoolean(KEY_JOINED, false)
        }

        if (receiverUserId.isNotBlank()) {
            db.collection("users").document(receiverUserId).get()
                .addOnSuccessListener { doc ->
                    val pic = doc.getString("profilePicUrl").orEmpty()
                    val name = doc.getString("username")?.takeIf { it.isNotBlank() } ?: receiverUsername
                    contactName.text = name
                    if (pic.isNotBlank()) {
                        Glide.with(this).load(pic).centerCrop().into(profileImage)
                    }
                }
        } else {
            // hide chat button when no recipient info
            chatButton.visibility = android.view.View.GONE
        }

        endCallButton.setOnClickListener { finish() }

        speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
            Toast.makeText(this, if (isSpeakerOn) "Speaker on" else "Speaker off", Toast.LENGTH_SHORT).show()
        }

        chatButton.setOnClickListener {
            if (receiverUserId.isBlank()) {
                Toast.makeText(this, "No recipient available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val intent = Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("receiverUserId", receiverUserId)
                putExtra("receiverUsername", receiverUsername)
                putExtra("CURRENT_USER_ID", currentUid)
            }
            startActivity(intent)
        }

        // If activity was created with END_CALL, finish immediately
        if (intent.getBooleanExtra("END_CALL", false)) {
            finish()
            return
        }

        // mark session active for this audio/video call
        CallSession.start(chatId.takeIf { it.isNotBlank() }, if (callType == "video") "video" else "audio")

        if (isJoinedChannel && startMillis > 0L) {
            handler.post(tick)
        } else {
            requestPermissionsThenStart()
        }
    }

    private fun requestPermissionsThenStart() {
        if (isJoinedChannel || rtcEngine != null) return

        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (callType == "video" && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isEmpty()) {
            setupAgoraAndJoin()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun setupAgoraAndJoin() {
        if (isJoinedChannel || rtcEngine != null) return

        try {
            rtcEngine = RtcEngine.create(applicationContext, AGORA_APP_ID, rtcEventHandler)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to init Agora: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)

        if (callType == "video") {
            rtcEngine?.enableVideo()
            rtcEngine?.setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x360,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
                )
            )
        } else {
            rtcEngine?.disableVideo()
        }

        val token: String? = null
        val channelName = if (chatId.isNotBlank()) chatId else "call_${receiverUserId}_${System.currentTimeMillis()}"
        rtcEngine?.joinChannel(token, channelName, "", 0)

        // start timer here optimistically; rtcEventHandler will also ensure it's started on success
        if (startMillis == 0L) startMillis = System.currentTimeMillis()
        handler.post(tick)
        isJoinedChannel = true
    }

    private fun openChatDetailFromCall() {
        if (receiverUserId.isBlank()) return

        val chatId = if (currentUserIdForChat < receiverUserId)
            "${currentUserIdForChat}_$receiverUserId" else "${receiverUserId}_$currentUserIdForChat"

        val intent = Intent(this, ChatDetailActivity::class.java).apply {
            putExtra("receiverUserId", receiverUserId)
            putExtra("receiverUsername", receiverUsername)
            putExtra("RECEIVER_PROFILE_URL", intent.getStringExtra("RECEIVER_PROFILE_URL"))
            putExtra("CURRENT_USER_ID", currentUserIdForChat)
            putExtra("CHAT_ID", chatId)
        }
        startActivity(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_START_MILLIS, startMillis)
        outState.putBoolean(KEY_JOINED, isJoinedChannel)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("END_CALL", false)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
        try {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        } catch (e: Exception) {
            // ignore
        }
        CallSession.end()
    }
}
