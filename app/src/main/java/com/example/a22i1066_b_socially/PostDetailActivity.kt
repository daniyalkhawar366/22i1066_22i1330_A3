package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch


class PostDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val TAG = "PostDetailActivity"

    private lateinit var loadingIndicator: ProgressBar
    private lateinit var postContent: View
    private lateinit var backBtn: ImageView
    private lateinit var moreOptionsBtn: ImageView
    private lateinit var profilePic: ImageView
    private lateinit var usernameText: TextView
    private lateinit var locationText: TextView
    private lateinit var imageViewPager: ViewPager2
    private lateinit var imageCounter: TextView
    private lateinit var likeBtn: ImageView
    private lateinit var commentBtn: ImageView
    private lateinit var shareBtn: ImageView
    private lateinit var bookmarkBtn: ImageView
    private lateinit var likesText: TextView
    private lateinit var captionUsername: TextView
    private lateinit var captionText: TextView

    private var currentPost: Post? = null
    private var isLiked = false
    private var currentUserId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.postpage)

        // Use SessionManager instead of Firebase Auth
        val sessionManager = SessionManager(this)
        currentUserId = sessionManager.getUserId() ?: ""
        if (currentUserId.isEmpty()) {
            finish()
            return
        }

        val postId = intent.getStringExtra("postId")
        if (postId == null) {
            finish()
            return
        }

        initViews()
        loadPost(postId)

        backBtn.setOnClickListener { finish() }
        likeBtn.setOnClickListener { toggleLike() }
        commentBtn.setOnClickListener { handleComment() }
        shareBtn.setOnClickListener { handleShare() }
        moreOptionsBtn.setOnClickListener { showPostOptions() }

        profilePic.setOnClickListener { openProfile() }
        usernameText.setOnClickListener { openProfile() }
    }

    private fun initViews() {
        loadingIndicator = findViewById(R.id.loadingIndicator)
        postContent = findViewById(R.id.postContent)
        backBtn = findViewById(R.id.backBtn)
        moreOptionsBtn = findViewById(R.id.moreOptionsBtn)
        profilePic = findViewById(R.id.profilePic)
        usernameText = findViewById(R.id.usernameText)
        locationText = findViewById(R.id.locationText)
        imageViewPager = findViewById(R.id.imageViewPager)
        imageCounter = findViewById(R.id.imageCounter)
        likeBtn = findViewById(R.id.likeBtn)
        commentBtn = findViewById(R.id.commentBtn)
        shareBtn = findViewById(R.id.shareBtn)
        bookmarkBtn = findViewById(R.id.bookmarkBtn)
        likesText = findViewById(R.id.likesText)
        captionUsername = findViewById(R.id.captionUsername)
        captionText = findViewById(R.id.captionText)
    }

    private fun loadPost(postId: String) {
        loadingIndicator.visibility = View.VISIBLE
        postContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@PostDetailActivity).getAuthToken()}"
                val response = RetrofitClient.instance.getPostsFeed(token)

                if (response.isSuccessful && response.body()?.success == true) {
                    val postsData = response.body()?.posts ?: emptyList()
                    val postItem = postsData.find { it.id == postId }

                    if (postItem != null) {
                        val post = Post(
                            id = postItem.id,
                            userId = postItem.userId,
                            username = postItem.username,
                            profilePicUrl = postItem.profilePicUrl,
                            imageUrls = postItem.imageUrls,
                            caption = postItem.caption,
                            timestamp = postItem.timestamp,
                            likesCount = postItem.likesCount,
                            commentsCount = postItem.commentsCount,
                            isLikedByCurrentUser = postItem.isLikedByCurrentUser
                        )
                        currentPost = post

                        runOnUiThread {
                            displayPost(post)
                            loadingIndicator.visibility = View.GONE
                            postContent.visibility = View.VISIBLE
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@PostDetailActivity, "Post not found", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PostDetailActivity, "Failed to load post", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading post", e)
                runOnUiThread {
                    Toast.makeText(this@PostDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun displayPost(post: Post) {
        currentPost = post
        isLiked = post.isLikedByCurrentUser

        usernameText.text = post.username
        captionUsername.text = post.username
        captionText.text = post.caption
        likesText.text = "${post.likesCount} likes"

        if (post.profilePicUrl.isNotBlank()) {
            Glide.with(this).load(post.profilePicUrl).circleCrop().into(profilePic)
        }

        if (post.imageUrls.isNotEmpty()) {
            val adapter = PostImageAdapter(post.imageUrls)
            imageViewPager.adapter = adapter

            // Hide image counter - posts already have their own indicators
            imageCounter.visibility = View.GONE
        }

        updateLikeButton()

        loadingIndicator.visibility = View.GONE
        postContent.visibility = View.VISIBLE
    }

    private fun toggleLike() {
        val post = currentPost ?: return

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@PostDetailActivity).getAuthToken()}"
                val response = RetrofitClient.instance.togglePostLike(
                    token,
                    com.example.a22i1066_b_socially.network.ToggleLikeRequest(post.id)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val newIsLiked = response.body()?.isLiked ?: false
                    val newLikesCount = response.body()?.likesCount ?: 0

                    runOnUiThread {
                        isLiked = newIsLiked
                        currentPost = post.copy(
                            likesCount = newLikesCount,
                            isLikedByCurrentUser = newIsLiked
                        )
                        updateLikeButton()
                        likesText.text = "$newLikesCount likes"
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PostDetailActivity, "Failed to update like", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling like", e)
                runOnUiThread {
                    Toast.makeText(this@PostDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateLikeButton() {
        if (isLiked) {
            likeBtn.setImageResource(R.drawable.like_filled)
            likeBtn.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            likeBtn.setImageResource(R.drawable.like)
            likeBtn.setColorFilter(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun handleComment() {
        val post = currentPost ?: return
        val intent = Intent(this, CommentsActivity::class.java)
        intent.putExtra("postId", post.id)
        startActivity(intent)
    }

    private fun handleShare() {
        val post = currentPost ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this post by ${post.username}!")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun openProfile() {
        val post = currentPost ?: return
        if (post.userId == currentUserId) {
            startActivity(Intent(this, MyProfileActivity::class.java))
        } else {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("userId", post.userId)
            startActivity(intent)
        }
    }

    private fun showPostOptions() {
        val post = currentPost ?: return

        if (post.userId == currentUserId) {
            // Show delete option for own posts
            AlertDialog.Builder(this)
                .setTitle("Post Options")
                .setItems(arrayOf("Delete Post")) { _, which ->
                    when (which) {
                        0 -> confirmDeletePost()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Show report option for other users' posts
            AlertDialog.Builder(this)
                .setTitle("Post Options")
                .setItems(arrayOf("Report Post")) { _, which ->
                    when (which) {
                        0 -> Toast.makeText(this, "Report functionality coming soon", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun confirmDeletePost() {
        AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deletePost()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost() {
        val post = currentPost ?: return

        loadingIndicator.visibility = View.VISIBLE
        postContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@PostDetailActivity).getAuthToken()}"
                val response = RetrofitClient.instance.deletePost(
                    token,
                    com.example.a22i1066_b_socially.network.DeletePostRequest(post.id)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        Toast.makeText(this@PostDetailActivity, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@PostDetailActivity,
                            "Failed to delete post: ${response.body()?.error ?: "Unknown error"}",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadingIndicator.visibility = View.GONE
                        postContent.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting post", e)
                runOnUiThread {
                    Toast.makeText(
                        this@PostDetailActivity,
                        "Error deleting post: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingIndicator.visibility = View.GONE
                    postContent.visibility = View.VISIBLE
                }
            }
        }
    }
}

