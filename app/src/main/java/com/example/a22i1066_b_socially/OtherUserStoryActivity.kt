package com.example.a22i1066_b_socially

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

class OtherUserStoryActivity : AppCompatActivity() {

    private val TAG = "OtherUserStoryActivity"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dbRealtime = FirebaseDatabase.getInstance().reference

    private lateinit var ivStoryImage: ImageView
    private lateinit var topProfileImage: ImageView
    private lateinit var tvStoryUsername: TextView
    private lateinit var tvStoryTime: TextView
    private lateinit var homebtn: ImageView
    private lateinit var progressBarsContainer: LinearLayout
    private lateinit var etMessage: EditText
    private lateinit var ivLike: ImageView
    private lateinit var ivSend: ImageView

    private val stories = mutableListOf<Story>()
    private var currentStoryIndex = 0
    private val progressBars = mutableListOf<ProgressBar>()
    private var handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var isPaused = false
    private var startTime = 0L
    private var pausedAt = 0L
    private var totalPausedTime = 0L

    private val STORY_DURATION = 5000L
    private var storyUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ammanstory)

        storyUserId = intent.getStringExtra("storyUserId")

        if (storyUserId == null) {
            Toast.makeText(this, "Invalid story", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ivStoryImage = findViewById(R.id.ivStoryImage)
        topProfileImage = findViewById(R.id.topProfileImage)
        tvStoryUsername = findViewById(R.id.tvStoryUsername)
        tvStoryTime = findViewById(R.id.tvStoryTime)
        homebtn = findViewById(R.id.homebtn)
        progressBarsContainer = findViewById(R.id.progress_bars)
        etMessage = findViewById(R.id.etMessage)
        ivLike = findViewById(R.id.ivLike)
        ivSend = findViewById(R.id.ivSend)

        homebtn.setOnClickListener {
            finish()
        }

        ivLike.setOnClickListener {
            Toast.makeText(this, "Liked!", Toast.LENGTH_SHORT).show()
        }

        ivSend.setOnClickListener {
            val message = etMessage.text.toString()
            if (message.isNotBlank()) {
                Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()
                etMessage.text.clear()
            }
        }

        loadUserProfile()
        loadStories()
        setupTouchListeners()
    }

    private fun loadUserProfile() {
        val userId = storyUserId ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val username = doc?.getString("username") ?: "User"
                val profilePic = doc?.getString("profilePicUrl").orEmpty()

                tvStoryUsername.text = username
                if (profilePic.isNotBlank()) {
                    Glide.with(this).load(profilePic).circleCrop().into(topProfileImage)
                } else {
                    topProfileImage.setImageResource(R.drawable.profile_pic)
                }
            }
    }

    private fun loadStories() {
        val userId = storyUserId ?: return
        dbRealtime.child("stories").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    stories.clear()
                    for (child in snapshot.children) {
                        val story = child.getValue(Story::class.java)
                        if (story != null && !isStoryExpired(story)) {
                            stories.add(story)
                        }
                    }

                    if (stories.isEmpty()) {
                        Toast.makeText(this@OtherUserStoryActivity, "No stories available", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }

                    stories.sortBy { it.uploadedAt }
                    Log.d(TAG, "Loaded ${stories.size} stories")
                    setupProgressBars()
                    showStory(0)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load stories", error.toException())
                    Toast.makeText(this@OtherUserStoryActivity, "Failed to load stories", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
    }

    private fun isStoryExpired(story: Story): Boolean {
        return System.currentTimeMillis() > story.expiresAt
    }

    private fun setupProgressBars() {
        progressBarsContainer.removeAllViews()
        progressBars.clear()

        val totalStories = stories.size

        for (i in 0 until totalStories) {
            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    if (i < totalStories - 1) {
                        marginEnd = 8
                    }
                }
                max = 100
                progress = 0
                progressTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x40FFFFFF)
            }
            progressBarsContainer.addView(progressBar)
            progressBars.add(progressBar)
        }
    }

    private fun showStory(index: Int) {
        if (index < 0 || index >= stories.size) {
            finish()
            return
        }

        currentStoryIndex = index
        val story = stories[index]

        Glide.with(this).load(story.imageUrl).centerCrop().into(ivStoryImage)

        val timeAgo = getTimeAgo(story.uploadedAt)
        tvStoryTime.text = timeAgo

        for (i in progressBars.indices) {
            val progress = when {
                i < index -> 100
                i == index -> 0
                else -> 0
            }
            progressBars[i].progress = progress
        }

        startStoryProgress()
    }

    private fun startStoryProgress() {
        stopStoryProgress()

        if (currentStoryIndex >= progressBars.size) return

        val progressBar = progressBars[currentStoryIndex]
        startTime = System.currentTimeMillis()
        totalPausedTime = 0L
        pausedAt = 0L

        progressRunnable = object : Runnable {
            override fun run() {
                if (isPaused) {
                    if (pausedAt == 0L) {
                        pausedAt = System.currentTimeMillis()
                    }
                    handler.postDelayed(this, 16)
                    return
                }

                if (pausedAt > 0L) {
                    totalPausedTime += System.currentTimeMillis() - pausedAt
                    pausedAt = 0L
                }

                val elapsed = System.currentTimeMillis() - startTime - totalPausedTime
                val progress = ((elapsed.toFloat() / STORY_DURATION) * 100).toInt()

                if (progress >= 100) {
                    progressBar.progress = 100
                    handler.postDelayed({
                        moveToNextStory()
                    }, 100)
                } else {
                    progressBar.progress = progress
                    handler.postDelayed(this, 16)
                }
            }
        }

        progressBar.progress = 0
        handler.post(progressRunnable!!)
    }

    private fun stopStoryProgress() {
        progressRunnable?.let {
            handler.removeCallbacks(it)
        }
        progressRunnable = null
    }

    private fun moveToNextStory() {
        stopStoryProgress()
        if (currentStoryIndex < stories.size - 1) {
            showStory(currentStoryIndex + 1)
        } else {
            finish()
        }
    }

    private fun moveToPreviousStory() {
        stopStoryProgress()
        if (currentStoryIndex > 0) {
            showStory(currentStoryIndex - 1)
        } else {
            showStory(0)
        }
    }

    private fun setupTouchListeners() {
        val gestureListener = object : View.OnTouchListener {
            private var downX = 0f
            private var downTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downTime = System.currentTimeMillis()
                        isPaused = true
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val upTime = System.currentTimeMillis()
                        val upX = event.x
                        isPaused = false

                        if (upTime - downTime < 200) {
                            val screenWidth = v.width
                            if (upX < screenWidth / 2) {
                                moveToPreviousStory()
                            } else {
                                moveToNextStory()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        }

        ivStoryImage.setOnTouchListener(gestureListener)
    }

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStoryProgress()
    }
}
