package com.example.a22i1066_b_socially

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class SuggestedUsersActivity : AppCompatActivity() {
    private val TAG = "SuggestedUsers"

    private lateinit var backArrow: ImageView
    private lateinit var titleText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var adapter: FollowListAdapter
    private lateinit var sessionManager: SessionManager

    private var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followers_following)

        sessionManager = SessionManager(this)
        currentUserId = sessionManager.getUserId() ?: ""

        if (currentUserId.isEmpty()) {
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        loadSuggestedUsers()
    }

    private fun initViews() {
        backArrow = findViewById(R.id.backArrow)
        titleText = findViewById(R.id.titleText)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)

        titleText.text = "Suggested for you"

        backArrow.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FollowListAdapter(
            items = mutableListOf(),
            currentUserId = currentUserId,
            onFollowToggle = { user: com.example.a22i1066_b_socially.network.UserListItem, isFollowing: Boolean ->
                handleFollowToggle(user, isFollowing)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadSuggestedUsers() {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val token = "Bearer ${sessionManager.getAuthToken()}"
                val response = RetrofitClient.instance.getAllUsers(token)

                if (response.isSuccessful && response.body()?.success == true) {
                    val usersList = response.body()?.users ?: emptyList()

                    // Filter out current user
                    val filteredUsers = usersList.filter { it.userId != currentUserId }

                    // Check follow status for each user
                    checkFollowStatusForUsers(filteredUsers)

                    runOnUiThread {
                        if (filteredUsers.isEmpty()) {
                            showEmpty()
                        } else {
                            adapter.updateList(filteredUsers)
                            progressBar.visibility = View.GONE
                        }
                    }
                } else {
                    runOnUiThread {
                        Log.e(TAG, "Failed to load users: ${response.body()?.error}")
                        showEmpty()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading users", e)
                runOnUiThread {
                    showEmpty()
                }
            }
        }
    }

    private fun checkFollowStatusForUsers(users: List<com.example.a22i1066_b_socially.network.UserListItem>) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${sessionManager.getAuthToken()}"

                // Check follow status for each user
                for (user in users) {
                    try {
                        val response = RetrofitClient.instance.checkFollowStatus(token, user.userId)
                        if (response.isSuccessful && response.body()?.success == true) {
                            val isFollowing = response.body()?.isFollowing ?: false
                            runOnUiThread {
                                adapter.updateFollowStatus(user.userId, isFollowing)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking follow status for ${user.userId}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking follow statuses", e)
            }
        }
    }

    private fun handleFollowToggle(user: com.example.a22i1066_b_socially.network.UserListItem, isFollowing: Boolean) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${sessionManager.getAuthToken()}"
                val request = com.example.a22i1066_b_socially.network.FollowRequest(user.userId)

                val response = if (isFollowing) {
                    RetrofitClient.instance.unfollowUser(token, request)
                } else {
                    RetrofitClient.instance.followUser(token, request)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        adapter.updateFollowStatus(user.userId, !isFollowing)
                    }
                } else {
                    runOnUiThread {
                        Log.e(TAG, "Failed to toggle follow: ${response.body()?.error}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling follow", e)
            }
        }
    }

    private fun showEmpty() {
        progressBar.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = "No users found"
    }
}

