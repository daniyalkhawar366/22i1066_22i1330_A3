package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FollowersFollowingActivity : AppCompatActivity() {
    private val TAG = "FollowersFollowing"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var backArrow: ImageView
    private lateinit var titleText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var adapter: SearchAdapter

    private var userId: String = ""
    private var listType: String = "" // "followers" or "following"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followers_following)

        userId = intent.getStringExtra("userId") ?: auth.currentUser?.uid ?: ""
        listType = intent.getStringExtra("listType") ?: "followers"

        if (userId.isEmpty()) {
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        loadUsers()
    }

    private fun initViews() {
        backArrow = findViewById(R.id.backArrow)
        titleText = findViewById(R.id.titleText)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)

        titleText.text = if (listType == "followers") "Followers" else "Following"

        backArrow.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter(
            items = mutableListOf(),
            onUserClick = { user ->
                val currentUserId = auth.currentUser?.uid
                if (user.id == currentUserId) {
                    startActivity(Intent(this, MyProfileActivity::class.java))
                } else {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.putExtra("userId", user.id)
                    startActivity(intent)
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadUsers() {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("users").document(userId)
            .collection(listType)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    showEmpty()
                    return@addOnSuccessListener
                }

                val userIds = snapshot.documents.map { it.id }
                loadUserDetails(userIds)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load $listType", e)
                showEmpty()
            }
    }

    private fun loadUserDetails(userIds: List<String>) {
        if (userIds.isEmpty()) {
            showEmpty()
            return
        }

        // Firestore has a limit of 10 items per 'in' query, so we batch
        val batches = userIds.chunked(10)
        val allUsers = mutableListOf<SearchUser>()
        var completedBatches = 0

        for (batch in batches) {
            db.collection("users")
                .whereIn("__name__", batch)
                .get()
                .addOnSuccessListener { docs ->
                    for (doc in docs) {
                        val username = doc.getString("username") ?: ""
                        if (username.isEmpty()) continue

                        val bio = doc.getString("bio") ?: ""
                        val first = doc.getString("firstName").orEmpty().trim()
                        val last = doc.getString("lastName").orEmpty().trim()
                        val display = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
                            .ifEmpty { doc.getString("displayName").orEmpty() }

                        val pic = doc.getString("profilePicUrl")
                            ?: doc.getString("profilePic")
                            ?: doc.getString("photoUrl")
                            ?: ""

                        allUsers.add(
                            SearchUser(
                                id = doc.id,
                                username = username,
                                subtitle = bio,
                                displayName = display,
                                profilePicUrl = pic
                            )
                        )
                    }

                    completedBatches++
                    if (completedBatches == batches.size) {
                        if (allUsers.isEmpty()) {
                            showEmpty()
                        } else {
                            adapter.updateList(allUsers)
                            progressBar.visibility = View.GONE
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load user details", e)
                    completedBatches++
                    if (completedBatches == batches.size) {
                        if (allUsers.isEmpty()) {
                            showEmpty()
                        } else {
                            adapter.updateList(allUsers)
                            progressBar.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun showEmpty() {
        progressBar.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = if (listType == "followers") "No followers yet" else "Not following anyone yet"
    }
}
