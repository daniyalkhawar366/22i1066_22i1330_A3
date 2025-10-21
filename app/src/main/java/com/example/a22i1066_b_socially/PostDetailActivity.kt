package com.example.a22i1066_b_socially

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
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.app.Activity


class PostDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
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

        currentUserId = auth.currentUser?.uid ?: ""
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

        db.collection("posts").document(postId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    try {
                        val post = Post(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            username = doc.getString("username") ?: "",
                            profilePicUrl = doc.getString("profilePicUrl") ?: "",
                            imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList(),
                            caption = doc.getString("caption") ?: "",
                            timestamp = doc.getTimestamp("timestamp"),
                            likesCount = doc.getLong("likesCount") ?: 0L,
                            commentsCount = doc.getLong("commentsCount") ?: 0L
                        )
                        currentPost = post
                        // Check if liked AFTER loading post
                        checkIfLiked(postId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing post", e)
                        finish()
                    }
                } else {
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load post", e)
                finish()
            }
    }

    private fun checkIfLiked(postId: String) {
        db.collection("posts").document(postId)
            .collection("likes").document(currentUserId)
            .get()
            .addOnSuccessListener { doc ->
                isLiked = doc.exists()
                currentPost?.let { post ->
                    displayPost(post.copy(isLikedByCurrentUser = isLiked))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check like status", e)
                // Still display post even if like check fails
                currentPost?.let { displayPost(it) }
            }
    }

    private fun displayPost(post: Post) {
        currentPost = post

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

            imageCounter.text = "1/${post.imageUrls.size}"
            imageCounter.visibility = if (post.imageUrls.size > 1) View.VISIBLE else View.GONE

            imageViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    imageCounter.text = "${position + 1}/${post.imageUrls.size}"
                }
            })
        }

        updateLikeButton()

        loadingIndicator.visibility = View.GONE
        postContent.visibility = View.VISIBLE
    }

    private fun toggleLike() {
        val post = currentPost ?: return
        val postRef = db.collection("posts").document(post.id)
        val likeRef = postRef.collection("likes").document(currentUserId)

        if (isLiked) {
            // Unlike
            likeRef.delete()
            postRef.update("likesCount", FieldValue.increment(-1))
                .addOnSuccessListener {
                    isLiked = false
                    currentPost = post.copy(
                        likesCount = post.likesCount - 1,
                        isLikedByCurrentUser = false
                    )
                    updateLikeButton()
                    likesText.text = "${currentPost?.likesCount} likes"
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to unlike", e)
                    Toast.makeText(this, "Failed to unlike post", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Like
            likeRef.set(mapOf("timestamp" to FieldValue.serverTimestamp()))
            postRef.update("likesCount", FieldValue.increment(1))
                .addOnSuccessListener {
                    isLiked = true
                    currentPost = post.copy(
                        likesCount = post.likesCount + 1,
                        isLikedByCurrentUser = true
                    )
                    updateLikeButton()
                    likesText.text = "${currentPost?.likesCount} likes"
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to like", e)
                    Toast.makeText(this, "Failed to like post", Toast.LENGTH_SHORT).show()
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
        Log.d(TAG, "Comment clicked for post: ${post.id}")
        Toast.makeText(this, "Comments coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun handleShare() {
        val post = currentPost ?: return
        Log.d(TAG, "Share clicked for post: ${post.id}")
        Toast.makeText(this, "Share coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun openProfile() {
        val post = currentPost ?: return
        if (post.userId == currentUserId) {
            startActivity(android.content.Intent(this, MyProfileActivity::class.java))
        } else {
            val intent = android.content.Intent(this, ProfileActivity::class.java)
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

        db.collection("posts").document(post.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting post", e)
                Toast.makeText(this, "Failed to delete post", Toast.LENGTH_SHORT).show()
                loadingIndicator.visibility = View.GONE
                postContent.visibility = View.VISIBLE
            }
    }
}
