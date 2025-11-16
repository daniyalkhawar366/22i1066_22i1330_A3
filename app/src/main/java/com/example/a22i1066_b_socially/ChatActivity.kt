package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.a22i1066_b_socially.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.text.clear

class ChatActivity : AppCompatActivity() {
    private val TAG = "ChatActivity"
    private lateinit var homebutton: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatUserAdapter
    private lateinit var tvUsername: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var sessionManager: SessionManager

    private val allUsers = mutableListOf<User>()
    private val displayUsers = mutableListOf<User>()
    private var isLoading = false
    private var currentQuery: String = ""
    private var isPolling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats)

        sessionManager = SessionManager(this)

        homebutton = findViewById(R.id.backArrow)
        homebutton.setOnClickListener { finish() }

        tvUsername = findViewById(R.id.tvUsername)
        tvUsername.text = sessionManager.getUsername() ?: ""

        progressLoading = findViewById(R.id.progressLoading)
        progressLoading.visibility = View.VISIBLE

        recyclerView = findViewById(R.id.chatRecyclerView)
        adapter = ChatUserAdapter(displayUsers) { user ->
            val intent = Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("receiverUserId", user.id)
                putExtra("receiverUsername", user.username)
                putExtra("RECEIVER_PROFILE_URL", user.profilePicUrl)
                putExtra("CURRENT_USER_ID", sessionManager.getUserId())
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.visibility = View.GONE

        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString() ?: ""
                applyFilter(currentQuery)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadChats()
        startPolling()
    }

    override fun onResume() {
        super.onResume()
        if (!isLoading) loadChats()
    }

    override fun onPause() {
        super.onPause()
        isPolling = false
    }

    private fun startPolling() {
        isPolling = true
        lifecycleScope.launch {
            while (isPolling) {
                delay(3000) // Poll every 3 seconds
                if (!isLoading) {
                    loadChats(silent = true)
                }
            }
        }
    }

    private fun loadChats(silent: Boolean = false) {
        if (isLoading) return
        isLoading = true

        if (!silent) {
            progressLoading.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            isLoading = false
            progressLoading.visibility = View.GONE
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // First try to load existing chats
                val chatResponse = RetrofitClient.instance.getChatList("Bearer $token")

                if (chatResponse.isSuccessful && chatResponse.body()?.success == true) {
                    val chatItems = chatResponse.body()?.chats ?: emptyList()
                    allUsers.clear()

                    chatItems.forEach { chat ->
                        allUsers.add(
                            User(
                                id = chat.otherUserId,
                                username = chat.otherUsername,
                                profilePicUrl = chat.otherProfilePic,
                                lastMessage = chat.lastMessage,
                                lastTimestamp = chat.lastTimestamp
                            )
                        )
                    }
                }

                // If no chats exist, load all users instead
                if (allUsers.isEmpty()) {
                    val usersResponse = RetrofitClient.instance.getAllUsers("Bearer $token")

                    if (usersResponse.isSuccessful && usersResponse.body()?.success == true) {
                        val userItems = usersResponse.body()?.users ?: emptyList()

                        userItems.forEach { user ->
                            allUsers.add(
                                User(
                                    id = user.userId,
                                    username = user.username,
                                    profilePicUrl = user.profilePic,
                                    lastMessage = "Start a conversation",
                                    lastTimestamp = 0L
                                )
                            )
                        }
                    }
                }

                sortAllUsers()
                applyFilter(currentQuery)

                if (!silent) {
                    recyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chats/users", e)
                if (!silent) {
                    Toast.makeText(this@ChatActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoading = false
                if (!silent) {
                    progressLoading.visibility = View.GONE
                }
            }
        }
    }


    private fun sortAllUsers() {
        allUsers.sortWith(compareByDescending<User> { it.lastTimestamp }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.username ?: "" })
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        displayUsers.clear()
        if (q.isEmpty()) {
            displayUsers.addAll(allUsers)
        } else {
            for (u in allUsers) {
                val uname = u.username?.lowercase(Locale.getDefault()) ?: ""
                val lmsg = u.lastMessage?.lowercase(Locale.getDefault()) ?: ""
                if (uname.contains(q) || lmsg.contains(q)) displayUsers.add(u)
            }
        }
        runOnUiThread { adapter.notifyDataSetChanged() }
    }
}
