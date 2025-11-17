package com.example.a22i1066_b_socially

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class CommentsActivity : AppCompatActivity() {

    private val TAG = "CommentsActivity"

    private lateinit var backBtn: ImageView
    private lateinit var postBtn: TextView
    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentInput: EditText
    private lateinit var progressBar: ProgressBar

    private val comments = mutableListOf<Comment>()
    private lateinit var adapter: CommentsAdapter
    private var postId = ""
    private var currentUserId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        val sessionManager = SessionManager(this)
        currentUserId = sessionManager.getUserId() ?: ""
        postId = intent.getStringExtra("postId") ?: ""

        if (currentUserId.isEmpty() || postId.isEmpty()) {
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        loadComments()

        backBtn.setOnClickListener { finish() }
        postBtn.setOnClickListener { postComment() }
    }

    private fun initViews() {
        backBtn = findViewById(R.id.backBtn)
        postBtn = findViewById(R.id.postBtn)
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
        commentInput = findViewById(R.id.commentInput)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        adapter = CommentsAdapter(comments, currentUserId) { comment ->
            // Comment deletion not yet implemented
        }
        commentsRecyclerView.adapter = adapter
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadComments() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@CommentsActivity).getAuthToken()}"
                val response = RetrofitClient.instance.getComments(token, postId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val commentsData = response.body()?.comments ?: emptyList()

                    comments.clear()
                    commentsData.forEach { commentItem ->
                        comments.add(Comment(
                            commentId = commentItem.id,
                            userId = commentItem.userId,
                            username = commentItem.username,
                            profilePicUrl = commentItem.profilePicUrl,
                            text = commentItem.text,
                            timestamp = commentItem.timestamp
                        ))
                    }

                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                        progressBar.visibility = View.GONE
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@CommentsActivity, "Failed to load comments", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading comments", e)
                runOnUiThread {
                    Toast.makeText(this@CommentsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun postComment() {
        val text = commentInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        postBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@CommentsActivity).getAuthToken()}"
                val commentId = "${currentUserId}_${System.currentTimeMillis()}"
                val timestamp = System.currentTimeMillis()

                val request = com.example.a22i1066_b_socially.network.AddCommentRequest(
                    postId = postId,
                    commentId = commentId,
                    text = text,
                    timestamp = timestamp
                )

                val response = RetrofitClient.instance.addComment(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        commentInput.setText("")
                        loadComments() // Reload comments to show the new one
                        progressBar.visibility = View.GONE
                        postBtn.isEnabled = true
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@CommentsActivity, "Failed to post comment", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        postBtn.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error posting comment", e)
                runOnUiThread {
                    Toast.makeText(this@CommentsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    postBtn.isEnabled = true
                }
            }
        }
    }

}
