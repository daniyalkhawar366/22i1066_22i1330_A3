package com.example.a22i1066_b_socially

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class SearchBarActivity : AppCompatActivity() {
    private val TAG = "SearchBarActivity"
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

        Log.d(TAG, "Performing search for: $q")

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@SearchBarActivity).getAuthToken()}"
                val response = RetrofitClient.instance.searchUsers(token, q, 200)

                if (response.isSuccessful && response.body()?.success == true) {
                    val usersData = response.body()?.users ?: emptyList()
                    Log.d(TAG, "Search found ${usersData.size} users")

                    val results = usersData.map { userItem ->
                        SearchUser(
                            id = userItem.id,
                            username = userItem.username,
                            displayName = userItem.displayName,
                            subtitle = userItem.subtitle,
                            profilePicUrl = userItem.profilePicUrl
                        )
                    }

                    runOnUiThread {
                        adapter.updateList(results)
                    }
                } else {
                    Log.e(TAG, "Search failed: ${response.errorBody()?.string()}")
                    runOnUiThread {
                        adapter.updateList(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing search", e)
                runOnUiThread {
                    adapter.updateList(emptyList())
                }
            }
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

            // Validate users exist in database and remove deleted ones
            validateRecentSearches(recentUsers)
        } catch (e: Exception) {
            adapter.updateList(emptyList())
        }
    }

    private fun validateRecentSearches(recentUsers: List<SearchUser>) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@SearchBarActivity).getAuthToken()}"

                // Get user IDs from recent searches
                val userIds = recentUsers.map { it.id }

                // Fetch current users from database to validate
                val validUsers = mutableListOf<SearchUser>()

                for (user in recentUsers) {
                    // Quick validation - search for this specific user
                    val response = RetrofitClient.instance.searchUsers(token, user.username, 1)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val users = response.body()?.users ?: emptyList()
                        // Check if this user still exists in the database
                        val exists = users.any { it.id == user.id }
                        if (exists) {
                            validUsers.add(user)
                        }
                    }
                }

                // Update stored recent searches with only valid users
                if (validUsers.size != recentUsers.size) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val newJson = Gson().toJson(validUsers)
                    prefs.edit().putString(KEY_RECENT_SEARCHES, newJson).apply()
                    Log.d(TAG, "Cleaned ${recentUsers.size - validUsers.size} deleted users from search history")
                }

                runOnUiThread {
                    adapter.updateList(validUsers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error validating recent searches", e)
                // On error, show the original list
                runOnUiThread {
                    adapter.updateList(recentUsers)
                }
            }
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
