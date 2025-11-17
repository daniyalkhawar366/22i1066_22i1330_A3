package com.example.a22i1066_b_socially

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import android.widget.Toast
import kotlin.collections.remove
import androidx.appcompat.app.AlertDialog
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch


class MyProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var loadingIndicator: ProgressBar
    private lateinit var profileContent: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var toolbarUsername: TextView
    private lateinit var menuBtn: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var displayName: TextView
    private lateinit var displayTitle: TextView
    private lateinit var bioText: TextView
    private lateinit var postsCount: TextView
    private lateinit var followersCount: TextView
    private lateinit var followingCount: TextView
    private lateinit var editProfileBtn: Button
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var highlightsRecycler: RecyclerView
    private lateinit var highlightAdapter: HighlightAdapter

    private lateinit var postsAdapter: ProfilePostAdapter
    private val postsList = mutableListOf<Post>()

    private var highlightsListener: ListenerRegistration? = null

    private val TAG = "MyProfileActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check SessionManager for login status instead of Firebase Auth
        val sessionManager = SessionManager(this)
        val uid = sessionManager.getUserId()

        if (uid.isNullOrBlank() || !sessionManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.profile)

        try {
            loadingIndicator = findViewById(R.id.loadingIndicator)
            profileContent = findViewById(R.id.profileContent)
            swipeRefresh = findViewById(R.id.swipeRefresh)
            toolbarUsername = findViewById(R.id.toolbarUsername)
            menuBtn = findViewById(R.id.menuBtn)
            profileImage = findViewById(R.id.profile_image)
            displayName = findViewById(R.id.displayName)
            displayTitle = findViewById(R.id.displayTitle)
            bioText = findViewById(R.id.bioText)
            postsCount = findViewById(R.id.postsCount)
            followersCount = findViewById(R.id.followersCount)
            followingCount = findViewById(R.id.followingCount)
            editProfileBtn = findViewById(R.id.editprofile)
            postsRecyclerView = findViewById(R.id.postsRecyclerView)
            highlightsRecycler = findViewById(R.id.highlightsRecycler)

            setupPostsGrid()
            setupHighlightsRecycler()
            setupSwipeRefresh()

            val homeBtn: ImageView = findViewById(R.id.homebtn)
            val exploreBtn: ImageView = findViewById(R.id.explorepg)
            val postBtn: ImageView = findViewById(R.id.post)
            val notifBtn: ImageView = findViewById(R.id.notificationsfollowing)

            menuBtn.setOnClickListener { showMenu(it as ImageView) }

            editProfileBtn.setOnClickListener {
                startActivity(Intent(this, EditProfileActivity::class.java))
            }

            homeBtn.setOnClickListener {
                startActivity(Intent(this, FYPActivity::class.java))
                finish()
            }

            exploreBtn.setOnClickListener {
                startActivity(Intent(this, ExplorePageActivity::class.java))
                finish()
            }
            followersCount.setOnClickListener {
                val intent = Intent(this, FollowersFollowingActivity::class.java)
                intent.putExtra("userId", uid ?: "")
                intent.putExtra("listType", "followers")
                startActivity(intent)
            }

            followingCount.setOnClickListener {
                val intent = Intent(this, FollowersFollowingActivity::class.java)
                intent.putExtra("userId", uid ?: "")
                intent.putExtra("listType", "following")
                startActivity(intent)
            }

            postBtn.setOnClickListener {
                startActivity(Intent(this, MakePostActivity::class.java))
            }

            notifBtn.setOnClickListener {
                startActivity(Intent(this, NotificationsFollowingActivity::class.java))
                finish()
            }

            loadProfile()
            loadUserPosts(uid)
            loadHighlights(uid)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MyProfileActivity", e)
            Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            resources.getColor(android.R.color.holo_red_light, null)
        )
        swipeRefresh.setOnRefreshListener {
            refreshProfile()
        }
    }

    private fun refreshProfile() {
        val sessionManager = SessionManager(this)
        val uid = sessionManager.getUserId() ?: return

        // Don't hide profileContent during refresh
        swipeRefresh.isRefreshing = true

        highlightsListener?.remove()

        loadProfile()
        loadUserPosts(uid)
        loadHighlights(uid)

        // Stop the refresh animation after a brief delay
        swipeRefresh.postDelayed({
            swipeRefresh.isRefreshing = false
        }, 500)
    }

    private fun setupHighlightsRecycler() {
        highlightsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        highlightAdapter = HighlightAdapter(
            mutableListOf(),
            { highlight -> openHighlight(highlight) },
            { startActivity(Intent(this, AddHighlightActivity::class.java)) }
        )
        highlightsRecycler.adapter = highlightAdapter
    }

    private fun setupPostsGrid() {
        postsAdapter = ProfilePostAdapter(postsList) { post ->
            val intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("postId", post.id)
            startActivity(intent)
        }
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        postsRecyclerView.adapter = postsAdapter
    }

    private fun showMenu(anchor: ImageView) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Menu")
        builder.setItems(arrayOf("Logout")) { _, which ->
            when (which) {
                0 -> confirmLogout()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProfile() {
        val sessionManager = SessionManager(this)
        val uid = sessionManager.getUserId() ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile(
                    userId = uid,
                    currentUserId = uid
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user == null) {
                        runOnUiThread {
                            showContent()
                        }
                        return@launch
                    }

                    runOnUiThread {
                        toolbarUsername.text = user.username

                        val first = user.firstName.trim()
                        val last = user.lastName.trim()
                        val fullName = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
                        displayName.text = if (fullName.isNotEmpty()) fullName else user.displayName

                        displayTitle.text = user.title
                        bioText.text = user.bio

                        postsCount.text = user.postsCount.toString()
                        followersCount.text = user.followersCount.toString()
                        followingCount.text = user.followingCount.toString()

                        val pic = user.profilePicUrl
                        if (pic.isNotBlank()) {
                            Glide.with(this@MyProfileActivity).load(pic).circleCrop().into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.profile_pic)
                        }

                        showContent()
                    }
                } else {
                    Log.e(TAG, "Failed to load profile: ${response.body()?.error}")
                    runOnUiThread {
                        showContent()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                runOnUiThread {
                    showContent()
                }
            }
        }
    }

    private fun loadUserPosts(uid: String) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@MyProfileActivity).getAuthToken()}"
                val response = RetrofitClient.instance.getUserPosts(token, uid)

                if (response.isSuccessful && response.body()?.success == true) {
                    val postsData = response.body()?.posts ?: emptyList()

                    val posts = postsData.map { postItem ->
                        Post(
                            id = postItem.id,
                            userId = postItem.userId,
                            username = postItem.username,
                            profilePicUrl = postItem.profilePicUrl,
                            imageUrls = postItem.imageUrls,
                            caption = postItem.caption,
                            timestamp = postItem.timestamp,
                            likesCount = postItem.likesCount,
                            commentsCount = postItem.commentsCount,
                            isLikedByCurrentUser = postItem.isLikedByCurrentUser
                        )
                    }

                    runOnUiThread {
                        postsAdapter.updatePosts(posts)
                    }
                } else {
                    Log.e(TAG, "Failed to load posts: ${response.body()?.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading posts", e)
            }
        }
    }

    private fun loadHighlights(uid: String) {
        highlightsListener = db.collection("highlights")
            .whereEqualTo("userId", uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to load highlights", error)
                    return@addSnapshotListener
                }

                val highlights = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Highlight(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            title = doc.getString("title") ?: "",
                            imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList(),
                            date = doc.getTimestamp("date")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing highlight", e)
                        null
                    }
                } ?: emptyList()

                highlightAdapter.updateHighlights(highlights)
            }
    }

    private fun openHighlight(highlight: Highlight) {
        val intent = Intent(this, HighlightViewActivity::class.java)
        intent.putExtra("highlightId", highlight.id)
        startActivity(intent)
    }

    private fun showContent() {
        loadingIndicator.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
        profileContent.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: We no longer use Firestore listeners for profile data
        highlightsListener?.remove()
        highlightsListener = null
    }
}