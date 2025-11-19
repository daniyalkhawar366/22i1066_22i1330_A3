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
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.example.a22i1066_b_socially.network.CallLogRequest
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoEncoderConfiguration
import io.agora.rtc2.Constants
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.Locale

class CallActivity : AppCompatActivity() {

    companion object {
        private const val AGORA_APP_ID = "caca39104a564ed5b5ee36350148f043"
        private const val KEY_START_MILLIS = "KEY_START_MILLIS"
        private const val KEY_JOINED = "KEY_JOINED"
    }

    private lateinit var sessionManager: SessionManager
    private var rtcEngine: RtcEngine? = null

    private lateinit var profileImage: ImageView
    private lateinit var contactName: TextView
    private lateinit var callStatus: TextView
    private lateinit var callDuration: TextView
    private lateinit var endCallButton: ImageView
    private lateinit var chatButton: ImageView
    private lateinit var speakerButton: ImageView

    // Video call UI elements
    private lateinit var remoteVideoContainer: android.widget.FrameLayout
    private lateinit var localVideoContainer: android.widget.FrameLayout
    private lateinit var localVideoCard: androidx.cardview.widget.CardView
    private lateinit var profileCard: androidx.cardview.widget.CardView

    private var chatId: String = ""
    private var receiverUserId: String = ""
    private var receiverUsername: String = ""
    private var currentUserIdForChat: String = ""
    private var callType: String = "audio" // "audio" or "video"

    private var startMillis: Long = 0L
    private var isJoinedChannel: Boolean = false
    private var isSpeakerOn: Boolean = true
    private var isRemoteUserJoined: Boolean = false
    private var isMuted: Boolean = false
    private var isVideoMuted: Boolean = false

    private var callId: String = ""
    private var isPollingCallStatus: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - startMillis
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
            callDuration.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
            android.util.Log.d("CallActivity", "✅ Successfully joined channel: $channel with uid: $uid")
            runOnUiThread {
                Toast.makeText(this@CallActivity, "Joined call channel", Toast.LENGTH_SHORT).show()
            }
            // Mark as joined but DON'T start timer yet - wait for remote user
            if (!isJoinedChannel) {
                isJoinedChannel = true
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            android.util.Log.d("CallActivity", "✅ Remote user joined! uid: $uid")
            runOnUiThread {
                isRemoteUserJoined = true
                callStatus.text = getString(R.string.connected)

                // Start timer when call is accepted (remote user joins)
                if (startMillis == 0L) {
                    startMillis = System.currentTimeMillis()
                    handler.post(tick)
                    android.util.Log.d("CallActivity", "✅ Timer started")
                }

                if (callType == "video") {
                    // Hide profile image and show remote video
                    profileCard.visibility = android.view.View.GONE
                    remoteVideoContainer.visibility = android.view.View.VISIBLE

                    // Setup remote video view
                    setupRemoteVideo(uid)
                }
                Toast.makeText(this@CallActivity, "User connected", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            android.util.Log.d("CallActivity", "❌ Remote user offline. uid: $uid, reason: $reason")
            runOnUiThread {
                isRemoteUserJoined = false
                callStatus.text = getString(R.string.call_ended)
                if (callType == "video") {
                    remoteVideoContainer.removeAllViews()
                    remoteVideoContainer.visibility = android.view.View.GONE
                    profileCard.visibility = android.view.View.VISIBLE
                }
                Toast.makeText(this@CallActivity, "User left", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onError(err: Int) {
            android.util.Log.e("CallActivity", "❌ Agora Error: $err")
            runOnUiThread {
                Toast.makeText(this@CallActivity, "Call error: $err", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            android.util.Log.d("CallActivity", "Connection state: $state, reason: $reason")
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            android.util.Log.d("CallActivity", "Remote video state: $state, reason: $reason")
            runOnUiThread {
                // Remote video states: 0=STOPPED, 1=STARTING, 2=DECODING, 3=FROZEN, 4=FAILED
                if (state == 1 || state == 2) { // STARTING or DECODING
                    // Remote video is available
                    profileCard.visibility = android.view.View.GONE
                    remoteVideoContainer.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ammancall)

        sessionManager = SessionManager(this)

        profileImage = findViewById(R.id.profileImage)
        contactName = findViewById(R.id.contactName)
        callStatus = findViewById(R.id.callStatus)
        callDuration = findViewById(R.id.callDuration)
        endCallButton = findViewById(R.id.endCallButton)
        chatButton = findViewById(R.id.chatButton)
        speakerButton = findViewById(R.id.speakerButton)

        // Initialize video containers
        remoteVideoContainer = findViewById(R.id.remoteVideoContainer)
        localVideoContainer = findViewById(R.id.localVideoContainer)
        localVideoCard = findViewById(R.id.localVideoCard)
        profileCard = findViewById(R.id.profileCard)

        // read intent extras (consolidated)
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        callId = intent.getStringExtra("CALL_ID") ?: ""
        receiverUserId = intent.getStringExtra("RECEIVER_USER_ID")
            ?: intent.getStringExtra("RECEIVER_ID")
                    ?: intent.getStringExtra("RECEIVER")
                    ?: ""
        receiverUsername = intent.getStringExtra("RECEIVER_USERNAME")
            ?: intent.getStringExtra("RECEIVER_NAME")
                    ?: ""
        callType = intent.getStringExtra("CALL_TYPE") ?: "audio"
        currentUserIdForChat = intent.getStringExtra("CURRENT_USER_ID")?.takeIf { it.isNotBlank() }
            ?: sessionManager.getUserId().orEmpty()

        contactName.text = receiverUsername

        // Start polling for call status to detect when other user ends call
        startCallStatusPolling()

        if (savedInstanceState != null) {
            startMillis = savedInstanceState.getLong(KEY_START_MILLIS, 0L)
            isJoinedChannel = savedInstanceState.getBoolean(KEY_JOINED, false)
        }

        if (receiverUserId.isNotBlank()) {
            // Load user info from PHP backend
            lifecycleScope.launch {
                try {
                    val token = sessionManager.getToken() ?: ""
                    val response = RetrofitClient.instance.getUserInfo("Bearer $token", receiverUserId)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.user
                        val name = user?.username?.takeIf { it.isNotBlank() } ?: receiverUsername
                        val pic = user?.profile_pic_url.orEmpty()

                        contactName.text = name
                        if (pic.isNotBlank()) {
                            Glide.with(this@CallActivity)
                                .load(pic)
                                .centerCrop()
                                .into(profileImage)
                        }
                    }
                } catch (e: Exception) {
                    // Continue with existing data
                    android.util.Log.e("CallActivity", "Error loading user info", e)
                }
            }
        } else {
            // hide chat button when no recipient info
            chatButton.visibility = android.view.View.GONE
        }

        endCallButton.setOnClickListener { finish() }

        speakerButton.setOnClickListener {
            if (callType == "audio") {
                // For audio calls, toggle speaker
                isSpeakerOn = !isSpeakerOn
                rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
                Toast.makeText(this, if (isSpeakerOn) "Speaker on" else "Speaker off", Toast.LENGTH_SHORT).show()
            } else {
                // For video calls, toggle mute
                isMuted = !isMuted
                rtcEngine?.muteLocalAudioStream(isMuted)
                // Update icon based on mute state
                speakerButton.alpha = if (isMuted) 0.5f else 1.0f
                Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
            }
        }

        chatButton.setOnClickListener {
            if (receiverUserId.isBlank()) {
                Toast.makeText(this, "No recipient available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentUid = sessionManager.getUserId().orEmpty()
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

        // Add camera switch on local video click
        localVideoCard.setOnClickListener {
            if (callType == "video") {
                rtcEngine?.switchCamera()
                Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
            }
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
            // Setup local video preview
            setupLocalVideo()
        } else {
            rtcEngine?.disableVideo()
        }

        val token: String? = null
        // Use a SHORT channel name (Agora has 64 char limit)
        // Use callId if available (already short), otherwise create short channel name
        val channelName = if (callId.isNotBlank()) {
            callId // e.g., "call_abc123"
        } else {
            // Create deterministic short channel name so both users join same channel
            val userId1 = currentUserIdForChat.takeLast(8)
            val userId2 = receiverUserId.takeLast(8)
            val sorted = listOf(userId1, userId2).sorted()
            "call_${sorted[0]}_${sorted[1]}"
        }

        android.util.Log.d("CallActivity", "Joining Agora channel: $channelName")
        val result = rtcEngine?.joinChannel(token, channelName, "", 0)
        android.util.Log.d("CallActivity", "Join channel result: $result")

        // Don't start timer here - it will start when remote user joins
        isJoinedChannel = true
    }

    private fun setupLocalVideo() {
        // Create local video view
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        surfaceView.setZOrderMediaOverlay(true)
        localVideoContainer.addView(surfaceView)

        // Setup local video to render your own camera
        rtcEngine?.setupLocalVideo(io.agora.rtc2.video.VideoCanvas(
            surfaceView,
            io.agora.rtc2.video.VideoCanvas.RENDER_MODE_HIDDEN,
            0
        ))

        // Start local preview
        rtcEngine?.startPreview()

        // Show local video card
        localVideoCard.visibility = android.view.View.VISIBLE
    }

    private fun setupRemoteVideo(uid: Int) {
        // Create remote video view
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        remoteVideoContainer.addView(surfaceView)

        // Setup remote video to render other user's camera
        rtcEngine?.setupRemoteVideo(io.agora.rtc2.video.VideoCanvas(
            surfaceView,
            io.agora.rtc2.video.VideoCanvas.RENDER_MODE_HIDDEN,
            uid
        ))
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

    private fun startCallStatusPolling() {
        isPollingCallStatus = true
        lifecycleScope.launch {
            while (isPollingCallStatus) {
                try {
                    val token = sessionManager.getToken() ?: ""
                    if (token.isNotBlank() && callId.isNotBlank()) {
                        val response = RetrofitClient.instance.getCallStatus("Bearer $token", callId)
                        if (response.isSuccessful && response.body()?.success == true) {
                            val status = response.body()?.status ?: ""
                            if (status == "ended" || status == "rejected" || status == "missed") {
                                runOnUiThread {
                                    Toast.makeText(this@CallActivity, "Call ended", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                                isPollingCallStatus = false
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors and continue polling
                }
                kotlinx.coroutines.delay(2000)
            }
        }
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
        isPollingCallStatus = false

        // Update call status to 'ended' in backend so other user's call closes
        if (callId.isNotBlank()) {
            lifecycleScope.launch {
                try {
                    val token = sessionManager.getToken() ?: ""
                    RetrofitClient.instance.updateCallStatus(
                        "Bearer $token",
                        com.example.a22i1066_b_socially.network.UpdateCallStatusRequest(
                            callId = callId,
                            status = "ended"
                        )
                    )
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // Log call to chat only if call was actually connected (remote user joined)
        if (isRemoteUserJoined && startMillis > 0L) {
            logCallToChat()
        }

        try {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        } catch (e: Exception) {
            // ignore
        }
        CallSession.end()
    }

    private fun logCallToChat() {
        if (chatId.isBlank() || receiverUserId.isBlank()) return

        val duration = (System.currentTimeMillis() - startMillis) / 1000 // seconds

        // Log call via PHP backend
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken() ?: ""
                val request = CallLogRequest(
                    receiverId = receiverUserId,
                    callType = callType,
                    duration = duration
                )

                val response = RetrofitClient.instance.logCall("Bearer $token", request)

                if (response.isSuccessful && response.body()?.success == true) {
                    android.util.Log.d("CallActivity", "Call logged successfully")
                } else {
                    android.util.Log.e("CallActivity", "Failed to log call: ${response.body()?.error}")
                }
            } catch (e: Exception) {
                android.util.Log.e("CallActivity", "Error logging call", e)
            }
        }
    }
}
