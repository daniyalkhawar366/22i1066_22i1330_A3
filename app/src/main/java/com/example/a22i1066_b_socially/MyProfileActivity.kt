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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import android.widget.Toast
import kotlin.collections.remove
import androidx.appcompat.app.AlertDialog
import com.google.firebase.messaging.FirebaseMessaging


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

    private var profileListener: ListenerRegistration? = null
    private var postsListener: ListenerRegistration? = null
    private var highlightsListener: ListenerRegistration? = null

    private val TAG = "MyProfileActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
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
                intent.putExtra("userId", auth.currentUser?.uid ?: "")
                intent.putExtra("listType", "followers")
                startActivity(intent)
            }

            followingCount.setOnClickListener {
                val intent = Intent(this, FollowersFollowingActivity::class.java)
                intent.putExtra("userId", auth.currentUser?.uid ?: "")
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
        val uid = auth.currentUser?.uid ?: return

        // Don't hide profileContent during refresh
        swipeRefresh.isRefreshing = true

        profileListener?.remove()
        postsListener?.remove()
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
        val uid = auth.currentUser?.uid ?: return

        profileListener = db.collection("users").document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to listen profile", error)
                    showContent()
                    return@addSnapshotListener
                }
                if (doc == null || !doc.exists()) {
                    showContent()
                    return@addSnapshotListener
                }

                val username = doc.getString("username").orEmpty()
                toolbarUsername.text = username

                val first = doc.getString("firstName").orEmpty().trim()
                val last = doc.getString("lastName").orEmpty().trim()
                val fullName = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
                displayName.text = if (fullName.isNotEmpty()) fullName else doc.getString("displayName").orEmpty()

                displayTitle.text = doc.getString("title").orEmpty()
                bioText.text = doc.getString("bio").orEmpty()

                val postsCountVal = (doc.getLong("postsCount") ?: 0L)
                val followersCountVal = (doc.getLong("followersCount") ?: 0L)
                val followingCountVal = (doc.getLong("followingCount") ?: 0L)

                postsCount.text = postsCountVal.toString()
                followersCount.text = followersCountVal.toString()
                followingCount.text = followingCountVal.toString()

                val pic = doc.getString("profilePicUrl").orEmpty()
                if (pic.isNotBlank()) {
                    Glide.with(this).load(pic).circleCrop().into(profileImage)
                } else {
                    profileImage.setImageResource(R.drawable.profile_pic)
                }

                showContent()
            }
    }

    private fun loadUserPosts(uid: String) {
        postsListener = db.collection("posts")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to load posts", error)
                    return@addSnapshotListener
                }

                val posts = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Post(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            username = doc.getString("username") ?: "",
                            profilePicUrl = doc.getString("profilePicUrl") ?: "",
                            imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList(),
                            caption = doc.getString("caption") ?: "",
                            timestamp = doc.getTimestamp("timestamp"),
                            likesCount = doc.getLong("likesCount") ?: 0L,
                            commentsCount = doc.getLong("commentsCount") ?: 0L
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing post", e)
                        null
                    }
                } ?: emptyList()

                postsAdapter.updatePosts(posts)
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
        profileListener?.remove()
        profileListener = null
        postsListener?.remove()
        postsListener = null
        highlightsListener?.remove()
        highlightsListener = null
    }
}