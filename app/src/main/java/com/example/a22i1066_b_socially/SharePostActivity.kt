package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.text.chunked
import kotlin.text.clear
import kotlin.text.get

class SharePostActivity : AppCompatActivity() {

    private val TAG = "SharePostActivity"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var backBtn: ImageView
    private lateinit var searchInput: EditText
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val users = mutableListOf<User>()
    private lateinit var adapter: ShareUserAdapter
    private var postId = ""
    private var currentUserId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_post)

        currentUserId = auth.currentUser?.uid ?: ""
        postId = intent.getStringExtra("postId") ?: ""

        if (currentUserId.isEmpty() || postId.isEmpty()) {
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        loadFollowing()

        backBtn.setOnClickListener { finish() }
    }

    private fun initViews() {
        backBtn = findViewById(R.id.backBtn)
        searchInput = findViewById(R.id.searchInput)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        adapter = ShareUserAdapter(users) { user ->
            shareToUser(user)
        }
        usersRecyclerView.adapter = adapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadFollowing() {
        progressBar.visibility = View.VISIBLE

        db.collection("users").document(currentUserId)
            .collection("following")
            .get()
            .addOnSuccessListener { snapshot ->
                val followingIds = snapshot.documents.mapNotNull { it.id }

                if (followingIds.isEmpty()) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "No following users to share with", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Query in batches if more than 10 users
                val batches = followingIds.chunked(10)
                users.clear()

                var processedBatches = 0
                batches.forEach { batch ->
                    db.collection("users")
                        .whereIn("id", batch)
                        .get()
                        .addOnSuccessListener { usersSnapshot ->
                            usersSnapshot.documents.forEach { doc ->
                                try {
                                    val user = User(
                                        id = doc.id,
                                        username = doc.getString("username") ?: "",
                                        profilePicUrl = doc.getString("profilePicUrl") ?: ""
                                    )
                                    users.add(user)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing user", e)
                                }
                            }

                            processedBatches++
                            if (processedBatches == batches.size) {
                                adapter.notifyDataSetChanged()
                                progressBar.visibility = View.GONE
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error loading batch", e)
                            processedBatches++
                            if (processedBatches == batches.size) {
                                progressBar.visibility = View.GONE
                            }
                        }
                }
            }
    }


    private fun shareToUser(user: User) {
        // Create chat ID
        val chatId = if (currentUserId < user.id) {
            "${currentUserId}_${user.id}"
        } else {
            "${user.id}_$currentUserId"
        }

        // Send post link as message
        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "text" to "Check out this post!",
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to "shared_post",
            "postId" to postId
        )

        db.collection("chats").document(chatId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                Toast.makeText(this, "Shared to ${user.username}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to share post", e)
                Toast.makeText(this, "Failed to share", Toast.LENGTH_SHORT).show()
            }
    }
}
