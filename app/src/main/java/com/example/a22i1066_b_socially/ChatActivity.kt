// kotlin
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*
import kotlin.compareTo
import kotlin.dec
import kotlin.text.get
import kotlin.text.set

class ChatActivity : AppCompatActivity() {
    private val TAG = "ChatActivity"
    private lateinit var homebutton: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatUserAdapter
    private lateinit var tvUsername: TextView
    private lateinit var progressLoading: ProgressBar

    private val allUsers = mutableListOf<User>()
    private val displayUsers = mutableListOf<User>()
    private var authRetryAttempts = 0
    private val MAX_AUTH_RETRIES = 3

    private val db = FirebaseFirestore.getInstance()
    private var isLoading = false
    private var currentQuery: String = ""
    private var pendingLastFetches = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats)

        homebutton = findViewById(R.id.backArrow)
        homebutton.setOnClickListener { finish() }

        tvUsername = findViewById(R.id.tvUsername)
        tvUsername.text = ""

        progressLoading = findViewById(R.id.progressLoading)
        progressLoading.visibility = View.VISIBLE

        val currentId = getCurrentUserId()
        if (currentId.isBlank()) {
            tvUsername.text = ""
        } else {
            db.collection("users").document(currentId).get()
                .addOnSuccessListener { doc ->
                    val username = doc.getString("username") ?: doc.getString("displayName") ?: ""
                    tvUsername.text = username.ifBlank { "" }
                }
                .addOnFailureListener {
                    tvUsername.text = ""
                }
        }

        recyclerView = findViewById(R.id.chatRecyclerView)
        adapter = ChatUserAdapter(displayUsers) { user ->
            val intent = Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("receiverUserId", user.id)
                putExtra("receiverUsername", user.username)
                putExtra("RECEIVER_PROFILE_URL", user.profilePicUrl)
                putExtra("CURRENT_USER_ID", getCurrentUserId())
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

        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        if (!isLoading) loadUsers()
    }

    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    private fun loadUsers() {
        if (isLoading) return
        isLoading = true
        progressLoading.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        allUsers.clear()
        displayUsers.clear()
        adapter.notifyDataSetChanged()

        val currentId = getCurrentUserId()
        if (currentId.isBlank()) {
            authRetryAttempts++
            if (authRetryAttempts <= MAX_AUTH_RETRIES) {
                progressLoading.postDelayed({
                    isLoading = false
                    loadUsers()
                }, 400)
                return
            } else {
                isLoading = false
                progressLoading.visibility = View.GONE
                Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
                return
            }
        }

        db.collection("users")
            .get()
            .addOnSuccessListener { snap ->
                val fetched = mutableListOf<User>()
                for (doc in snap.documents) {
                    val id = doc.id
                    if (id == currentId) continue

                    // prefer username/displayName; skip unknown/blank users
                    val uname = doc.getString("username") ?: doc.getString("displayName") ?: ""
                    if (uname.isBlank()) continue

                    val user = User(
                        id = id,
                        username = uname,
                        profilePicUrl = doc.getString("profilePicUrl"),
                        lastMessage = null,
                        lastTimestamp = 0L
                    )
                    fetched.add(user)
                }

                if (fetched.isEmpty()) {
                    allUsers.clear()
                    displayUsers.clear()
                    adapter.notifyDataSetChanged()
                    isLoading = false
                    progressLoading.visibility = View.GONE
                    return@addOnSuccessListener
                }

                allUsers.clear()
                allUsers.addAll(fetched)
                pendingLastFetches = allUsers.size
                for (u in allUsers) {
                    fetchLastMessageForUser(currentId, u.id)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to load users", e)
                isLoading = false
                progressLoading.visibility = View.GONE
                Toast.makeText(this, "Failed to load chats", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchLastMessageForUser(currentId: String, otherUserId: String) {
        val chatId = if (currentId < otherUserId) "${currentId}_${otherUserId}" else "${otherUserId}_${currentId}"
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val lastDoc = snap.documents.firstOrNull()
                val messageType = lastDoc?.getString("type") ?: "text"

                val lastText = when (messageType) {
                    "image" -> "📷 Image"
                    "mixed" -> {
                        val text = lastDoc?.getString("text")
                        if (text.isNullOrBlank()) "📷 Image" else text
                    }
                    else -> lastDoc?.getString("text")
                }

                val ts = lastDoc?.getTimestamp("timestamp")
                val tsMillis = ts?.toDate()?.time ?: lastDoc?.getLong("timestamp") ?: 0L

                val idx = allUsers.indexOfFirst { it.id == otherUserId }
                if (idx != -1) {
                    val u = allUsers[idx]
                    allUsers[idx] = u.copy(lastMessage = lastText, lastTimestamp = tsMillis)
                }

                pendingLastFetches--
                if (pendingLastFetches <= 0) finalizeUserLoad()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch last message for $otherUserId", e)
                pendingLastFetches--
                if (pendingLastFetches <= 0) finalizeUserLoad()
            }
    }


    private fun finalizeUserLoad() {
        runOnUiThread {
            sortAllUsers()
            applyFilter(currentQuery)
            progressLoading.visibility = View.GONE
            isLoading = false
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun sortAllUsers() {
        allUsers.sortWith(compareByDescending<User> { it.lastTimestamp }
            .thenComparator(Comparator { a, b -> (a.username ?: "").compareTo(b.username ?: "", ignoreCase = true) }))
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

    private fun <T> Comparator<T>.thenComparator(other: Comparator<T>): Comparator<T> {
        return Comparator { a, b ->
            val first = this.compare(a, b)
            if (first != 0) first else other.compare(a, b)
        }
    }
}
