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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CommentsActivity : AppCompatActivity() {

    private val TAG = "CommentsActivity"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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

        currentUserId = auth.currentUser?.uid ?: ""
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
        postBtn = findViewById(R.id.postBtn)  // Make sure this ID corresponds to a TextView in XML
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
        commentInput = findViewById(R.id.commentInput)
        progressBar = findViewById(R.id.progressBar)
    }


    private fun setupRecyclerView() {
        adapter = CommentsAdapter(comments, currentUserId) { comment ->
            deleteComment(comment)
        }
        commentsRecyclerView.adapter = adapter
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadComments() {
        progressBar.visibility = View.VISIBLE

        db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading comments", error)
                    progressBar.visibility = View.GONE
                    return@addSnapshotListener
                }

                comments.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val comment = Comment(
                            commentId = doc.id,
                            userId = doc.getString("userId") ?: "",
                            username = doc.getString("username") ?: "",
                            profilePicUrl = doc.getString("profilePicUrl") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                        comments.add(comment)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing comment", e)
                    }
                }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
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

        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { userDoc ->
                val username = userDoc.getString("username") ?: ""
                val profilePicUrl = userDoc.getString("profilePicUrl") ?: ""

                val commentData = hashMapOf(
                    "userId" to currentUserId,
                    "username" to username,
                    "profilePicUrl" to profilePicUrl,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("posts").document(postId)
                    .collection("comments")
                    .add(commentData)
                    .addOnSuccessListener {
                        db.collection("posts").document(postId)
                            .update("commentsCount", FieldValue.increment(1))

                        commentInput.setText("")
                        progressBar.visibility = View.GONE
                        postBtn.isEnabled = true
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error posting comment", e)
                        Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        postBtn.isEnabled = true
                    }
            }
    }

    private fun deleteComment(comment: Comment) {
        if (comment.userId != currentUserId) return

        db.collection("posts").document(postId)
            .collection("comments")
            .document(comment.commentId)
            .delete()
            .addOnSuccessListener {
                db.collection("posts").document(postId)
                    .update("commentsCount", FieldValue.increment(-1))
                Toast.makeText(this, "Comment deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting comment", e)
                Toast.makeText(this, "Failed to delete comment", Toast.LENGTH_SHORT).show()
            }
    }
}
