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
import com.example.a22i1066_b_socially.offline.OfflineIntegrationHelper
import com.example.a22i1066_b_socially.offline.OfflineManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class ChatActivity : AppCompatActivity() {
    private val TAG = "ChatActivity"
    private lateinit var homebutton: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatUserAdapter
    private lateinit var tvUsername: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var sessionManager: SessionManager
    private lateinit var currentUserId: String

    private val allUsers = mutableListOf<User>()
    private val displayUsers = mutableListOf<User>()
    private var isLoading = false
    private var currentQuery: String = ""
    private var isPolling = false
    private var isPollingIncomingCalls = false

    private var showSingleChat: Boolean = false
    private var singleChatUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.chats)

            sessionManager = SessionManager(this)
            currentUserId = sessionManager.getUserId() ?: ""

            homebutton = findViewById(R.id.backArrow)
            homebutton.setOnClickListener { finish() }

            // Add icon to show users not in chat list
            val addIcon = findViewById<ImageView>(R.id.addIcon)
            addIcon.setOnClickListener {
                showNewChatDialog()
            }

            tvUsername = findViewById(R.id.tvUsername)
            tvUsername.text = sessionManager.getUsername() ?: ""

            progressLoading = findViewById(R.id.progressLoading)
            progressLoading.visibility = View.VISIBLE

            recyclerView = findViewById(R.id.chatRecyclerView)
            adapter = ChatUserAdapter(displayUsers) { user ->
                try {
                    val intent = Intent(this, ChatDetailActivity::class.java).apply {
                        putExtra("receiverUserId", user.id)
                        putExtra("receiverUsername", user.username)
                        putExtra("RECEIVER_PROFILE_URL", user.profilePicUrl)
                        putExtra("CURRENT_USER_ID", sessionManager.getUserId())
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening chat", e)
                    Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show()
                }
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
            startIncomingCallPolling()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - Reloading chat list")
        showAllChats() // Always show all chats when returning to chat list
        loadChats() // Force reload

        // Restart polling if stopped
        if (!isPollingIncomingCalls) {
            startIncomingCallPolling()
        }
    }

    override fun onPause() {
        super.onPause()
        isPolling = false
        isPollingIncomingCalls = false
    }

    private fun startPolling() {
        isPolling = true
        lifecycleScope.launch {
            while (isPolling) {
                delay(3000) // Poll every 3 seconds
                if (!isLoading) {
                    loadChats(silent = true)
                }
                // Update activity status every 3 seconds
                updateActivity()
            }
        }
    }

    private fun updateActivity() {
        val token = sessionManager.getToken() ?: return
        lifecycleScope.launch {
            try {
                RetrofitClient.instance.updateActivity("Bearer $token")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update activity", e)
            }
        }
    }

    // Call this after sending/receiving a message
    fun onMessageSentOrReceived(userId: String) {
        // Call this from ChatDetailActivity after sending/receiving a message
        showSingleChat(userId)
    }

    private fun showSingleChat(userId: String) {
        showSingleChat = true
        singleChatUserId = userId
        loadChats()
    }

    private fun loadChats(silent: Boolean = false) {
        if (isLoading) {
            Log.d(TAG, "loadChats - Already loading, skipping")
            return
        }
        isLoading = true

        Log.d(TAG, "loadChats - Starting, silent=$silent")

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

        val isOnline = OfflineIntegrationHelper.isOnline(this)
        if (!isOnline) {
            // Load cached chats/messages if offline
            lifecycleScope.launch {
                val offlineManager = OfflineManager(this@ChatActivity)
                val cachedChats = offlineManager.getCachedChats(currentUserId)
                allUsers.clear()
                cachedChats.forEach { chat ->
                    if (chat.otherUserId != currentUserId) {
                        allUsers.add(
                            User(
                                id = chat.otherUserId,
                                username = chat.otherUsername,
                                profilePicUrl = chat.otherProfilePic,
                                lastMessage = chat.lastMessage,
                                lastTimestamp = chat.lastTimestamp,
                                isOnline = false // Can't know online status offline
                            )
                        )
                    }
                }
                sortAllUsers()
                applyFilter(currentQuery)
                recyclerView.visibility = View.VISIBLE
                progressLoading.visibility = View.GONE
                isLoading = false
            }
            return
        }

        lifecycleScope.launch {
            try {
                // First try to load existing chats
                Log.d(TAG, "Fetching chat list from backend")
                val chatResponse = RetrofitClient.instance.getChatList("Bearer $token")

                if (chatResponse.isSuccessful && chatResponse.body()?.success == true) {
                    val chatItems = chatResponse.body()?.chats ?: emptyList()
                    Log.d(TAG, "Received ${chatItems.size} chats from backend")
                    allUsers.clear()

                    // Cache chats for offline access
                    val offlineManager = OfflineManager(this@ChatActivity)

                    chatItems.forEach { chat ->
                        // Skip if other user is current user (don't show self)
                        if (chat.otherUserId != currentUserId) {
                            Log.d(TAG, "Adding chat: ${chat.otherUsername} - ${chat.lastMessage} (online: ${chat.isOnline})")

                            // Cache each chat (already in coroutine, no need for nested launch)
                            offlineManager.cacheChat(
                                id = chat.chatId,
                                userId = currentUserId,
                                otherUserId = chat.otherUserId,
                                otherUsername = chat.otherUsername,
                                otherProfilePic = chat.otherProfilePic,
                                lastMessage = chat.lastMessage,
                                lastTimestamp = chat.lastTimestamp
                            )

                            allUsers.add(
                                User(
                                    id = chat.otherUserId,
                                    username = chat.otherUsername,
                                    profilePicUrl = chat.otherProfilePic,
                                    lastMessage = chat.lastMessage,
                                    lastTimestamp = chat.lastTimestamp,
                                    isOnline = chat.isOnline
                                )
                            )
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to load chats: ${chatResponse.body()?.error}")
                }

                // If only showing single chat (after message sent/received)
                if (showSingleChat && singleChatUserId != null) {
                    val filtered = allUsers.filter { it.id == singleChatUserId }
                    displayUsers.clear()
                    displayUsers.addAll(filtered)
                    runOnUiThread { adapter.notifyDataSetChanged() }
                    return@launch
                }

                // If no chats exist, load all users instead
                if (allUsers.isEmpty()) {
                    val usersResponse = RetrofitClient.instance.getAllUsers("Bearer $token")

                    if (usersResponse.isSuccessful && usersResponse.body()?.success == true) {
                        val userItems = usersResponse.body()?.users ?: emptyList()

                        userItems.forEach { user ->
                            // Skip current user (don't show self in list)
                            if (user.userId != currentUserId) {
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
                }

                sortAllUsers()
                applyFilter(currentQuery)

                Log.d(TAG, "Chat list updated: ${allUsers.size} total, ${displayUsers.size} displayed")

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

    // Call this when returning to chat list (e.g. after viewing a chat)
    fun showAllChats() {
        Log.d(TAG, "showAllChats - Resetting filter")
        showSingleChat = false
        singleChatUserId = null
    }

    private fun sortAllUsers() {
        Log.d(TAG, "Sorting ${allUsers.size} users by timestamp")
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
        Log.d(TAG, "applyFilter - Displaying ${displayUsers.size} users (query: '$q')")
        runOnUiThread { adapter.notifyDataSetChanged() }
    }

    private fun showNewChatDialog() {
        val token = sessionManager.getToken() ?: return

        lifecycleScope.launch {
            try {
                // Get all users
                val usersResponse = RetrofitClient.instance.getAllUsers("Bearer $token")

                if (usersResponse.isSuccessful && usersResponse.body()?.success == true) {
                    val allUsersList = usersResponse.body()?.users ?: emptyList()

                    // Filter out users already in chat list
                    val existingChatUserIds = allUsers.map { it.id }.toSet()
                    val newUsers = allUsersList.filter { user ->
                        user.userId != currentUserId && !existingChatUserIds.contains(user.userId)
                    }

                    if (newUsers.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@ChatActivity, "No new users to chat with", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Show dialog with new users
                    runOnUiThread {
                        val dialogView = layoutInflater.inflate(R.layout.dialog_new_chat, null)
                        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.newChatRecyclerView)

                        val adapter = NewChatUserAdapter(newUsers) { user ->
                            // Open chat with selected user
                            val intent = Intent(this@ChatActivity, ChatDetailActivity::class.java).apply {
                                putExtra("receiverUserId", user.userId)
                                putExtra("receiverUsername", user.username)
                                putExtra("RECEIVER_PROFILE_URL", user.profilePic)
                                putExtra("CURRENT_USER_ID", sessionManager.getUserId())
                            }
                            startActivity(intent)
                        }

                        recyclerView.adapter = adapter
                        recyclerView.layoutManager = LinearLayoutManager(this@ChatActivity)

                        androidx.appcompat.app.AlertDialog.Builder(this@ChatActivity)
                            .setTitle("Start New Chat")
                            .setView(dialogView)
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Failed to load users", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading users for new chat", e)
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startIncomingCallPolling() {
        isPollingIncomingCalls = true
        lifecycleScope.launch {
            while (isPollingIncomingCalls) {
                try {
                    val token = sessionManager.getToken() ?: ""

                    if (token.isNotBlank()) {
                        val response = RetrofitClient.instance.pollIncomingCall("Bearer $token")

                        if (response.isSuccessful && response.body()?.success == true) {
                            val hasIncoming = response.body()?.hasIncomingCall ?: false

                            if (hasIncoming) {
                                val call = response.body()?.call
                                if (call != null) {
                                    Log.d(TAG, "Incoming call detected: ${call.callerId}")

                                    // Show incoming call activity
                                    val intent = Intent(this@ChatActivity, IncomingCallActivity::class.java).apply {
                                        putExtra("CALL_ID", call.callId)
                                        putExtra("CHAT_ID", call.channelName)
                                        putExtra("CALLER_USER_ID", call.callerId)
                                        putExtra("CALLER_USERNAME", call.callerUsername)
                                        putExtra("CALLER_PROFILE_URL", call.callerProfileUrl)
                                        putExtra("CURRENT_USER_ID", currentUserId)
                                        putExtra("CALL_TYPE", call.callType)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    startActivity(intent)

                                    // Stop polling temporarily while handling call
                                    isPollingIncomingCalls = false
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling incoming calls", e)
                }

                // Poll every 2 seconds
                kotlinx.coroutines.delay(2000)
            }
        }
    }
}
