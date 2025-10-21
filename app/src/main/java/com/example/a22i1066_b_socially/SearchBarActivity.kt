package com.example.a22i1066_b_socially

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchBarActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var searchInput: EditText
    private lateinit var clearBtn: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: SearchAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val DEBOUNCE_MS = 300L

    private val PREFS_NAME = "SearchPrefs"
    private val KEY_RECENT_SEARCHES = "recent_searches"
    private val MAX_RECENT = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.searchbar)

        searchInput = findViewById(R.id.searchInput)
        clearBtn = findViewById(R.id.homebtn)
        recycler = findViewById(R.id.resultsRecycler)

        adapter = SearchAdapter(
            items = mutableListOf(),
            onUserClick = { user ->
                saveRecentSearch(user)
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Load recent searches on start
        loadRecentSearches()

        clearBtn.setOnClickListener {
            searchInput.setText("")
            loadRecentSearches() // Show recent searches when cleared
            hideKeyboard()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }

                val text = s?.toString() ?: ""
                if (text.trim().isEmpty()) {
                    loadRecentSearches() // Show recent searches when empty
                    return
                }

                searchRunnable = Runnable {
                    performSearch(text)
                }
                handler.postDelayed(searchRunnable!!, DEBOUNCE_MS)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun performSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            loadRecentSearches()
            return
        }

        val limit = 200L
        val currentUserId = auth.currentUser?.uid ?: ""
        val qLower = q.lowercase()

        db.collection("users").limit(limit).get()
            .addOnSuccessListener { snap ->
                val results = mutableListOf<SearchUser>()
                for (doc in snap.documents) {
                    val id = doc.id
                    if (id == currentUserId) continue

                    val username = (doc.getString("username") ?: "").trim()
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

                    if (username.lowercase().contains(qLower) || display.lowercase().contains(qLower)) {
                        results.add(
                            SearchUser(
                                id = id,
                                username = username,
                                subtitle = bio,
                                displayName = display,
                                profilePicUrl = pic
                            )
                        )
                    }
                }
                adapter.updateList(results)
            }
            .addOnFailureListener {
                adapter.updateList(emptyList())
            }
    }

    private fun loadRecentSearches() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_SEARCHES, null) ?: run {
            adapter.updateList(emptyList())
            return
        }

        try {
            val type = object : TypeToken<List<SearchUser>>() {}.type
            val recentUsers: List<SearchUser> = Gson().fromJson(json, type)
            adapter.updateList(recentUsers)
        } catch (e: Exception) {
            adapter.updateList(emptyList())
        }
    }

    private fun saveRecentSearch(user: SearchUser) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_SEARCHES, null)

        val recentUsers = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<SearchUser>>() {}.type
                Gson().fromJson<MutableList<SearchUser>>(json, type)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        // Remove if already exists
        recentUsers.removeAll { it.id == user.id }
        // Add to front
        recentUsers.add(0, user)
        // Keep only MAX_RECENT
        if (recentUsers.size > MAX_RECENT) {
            recentUsers.subList(MAX_RECENT, recentUsers.size).clear()
        }

        val newJson = Gson().toJson(recentUsers)
        prefs.edit().putString(KEY_RECENT_SEARCHES, newJson).apply()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }
}
