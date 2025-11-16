package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.example.a22i1066_b_socially.network.RetrofitClient


class FYPActivity : AppCompatActivity() {
    private val TAG = "FYPActivity"
    private val auth = FirebaseAuth.getInstance()
    private var currentUserId: String = ""

    private lateinit var storiesRow: LinearLayout
    private lateinit var cameraBtn: ImageView
    private lateinit var chatsBtn: ImageView
    private lateinit var homeBtn: ImageView
    private lateinit var exploreBtn: ImageView
    private lateinit var postBtn: ImageView
    private lateinit var notifBtn: ImageView
    private lateinit var profileBtn: ImageView
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        currentUserId = sessionManager.getUserId() ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.foryou)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                token?.let {
                    savePendingToken(this, it)
                    uploadPendingTokenIfNeeded(this)
                }
            } else {
                Log.w(TAG, "Token fetch failed", task.exception)
            }
        }



        initializeViews()
        setupRecyclerView()
        setupBottomNavigation()
        setupSwipeRefresh()

        loadCurrentUserProfile()
        fetchAndDisplayStories()
        loadPosts()
    }

    private fun setupPostsAdapter() {
        postsRecyclerView.adapter = adapter
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this post by ${post.username}!")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }



    private fun openComments(post: Post) {
    private fun initializeViews() {
        storiesRow = findViewById(R.id.storiesRow)
        cameraBtn = findViewById(R.id.cameraBtn)
        chatsBtn = findViewById(R.id.chats)
        homeBtn = findViewById(R.id.homebtn)
        exploreBtn = findViewById(R.id.explorepg)
        postBtn = findViewById(R.id.post)
        notifBtn = findViewById(R.id.notificationsfollowing)
        profileBtn = findViewById(R.id.profile)
        postsRecyclerView = findViewById(R.id.postsRecyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
    }

    private fun setupRecyclerView() {
        adapter = PostAdapter(
            posts = posts,
            onLikeClick = { post -> handleLike(post) },
            onCommentClick = { post -> handleComment(post) },
            onShareClick = { post -> sharePost(post) },
            onProfileClick = { userId -> openProfile(userId) }
        )
        postsRecyclerView.layoutManager = LinearLayoutManager(this)
        postsRecyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            resources.getColor(android.R.color.holo_red_light, null),
            resources.getColor(android.R.color.holo_blue_light, null),
            resources.getColor(android.R.color.holo_green_light, null)
        )

        swipeRefresh.setOnRefreshListener {
            fetchAndDisplayStories()
            loadPosts(shuffle = true)
        }
    }

    private fun setupBottomNavigation() {
        cameraBtn.setOnClickListener {
            startActivity(Intent(this, AddStoryActivity::class.java))
        }

        chatsBtn.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        homeBtn.setOnClickListener {
            // Already on home
        }

        exploreBtn.setOnClickListener {
            startActivity(Intent(this, ExplorePageActivity::class.java))
        }

        postBtn.setOnClickListener {
            startActivity(Intent(this, MakePostActivity::class.java))
        }

        notifBtn.setOnClickListener {
            startActivity(Intent(this, NotificationsFollowingActivity::class.java))
        }

        profileBtn.setOnClickListener {
            startActivity(Intent(this, MyProfileActivity::class.java))
        }
    }

    private fun loadCurrentUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile("profile", userId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val userProfileResponse = response.body()
                    if (userProfileResponse?.success == true && userProfileResponse.user != null) {
                        val profilePic = userProfileResponse.user.profilePicUrl
                        runOnUiThread {
                            // Load profile pic in bottom navigation
                            if (!profilePic.isNullOrBlank()) {
                                Glide.with(this@FYPActivity)
                                    .load(profilePic)
                                    .circleCrop()
                                    .placeholder(R.drawable.profile_pic)
                                    .error(R.drawable.profile_pic)
                                    .into(profileBtn)
                            } else {
                                profileBtn.setImageResource(R.drawable.profile_pic)
                            }
                        }
                    } else {
                        val errorMsg = userProfileResponse?.error ?: "Failed to load profile"
                        Log.e(TAG, errorMsg)
                    }
                } else {
                    Log.e(TAG, "Failed to load profile: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
            }
        }
    }




    private fun loadPosts(shuffle: Boolean = false) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@FYPActivity).getAuthToken()}"
                val response = RetrofitClient.instance.getPostsFeed(token)

                if (response.isSuccessful && response.body()?.success == true) {
                    val postsData = response.body()?.posts ?: emptyList()

                    posts.clear()
                    postsData.forEach { postItem ->
                        posts.add(Post(
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
                        ))
                    }

                    if (shuffle) posts.shuffle()

                    runOnUiThread {
                        adapter.updatePosts(posts)
                        swipeRefresh.isRefreshing = false
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@FYPActivity, "Failed to load posts", Toast.LENGTH_SHORT).show()
                        swipeRefresh.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading posts", e)
                runOnUiThread {
                    Toast.makeText(this@FYPActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }



    private fun handleLike(post: Post) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@FYPActivity).getAuthToken()}"
                val response = RetrofitClient.instance.togglePostLike(
                    token,
                    com.example.a22i1066_b_socially.network.ToggleLikeRequest(post.id)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val isLiked = response.body()?.isLiked ?: false
                    val likesCount = response.body()?.likesCount ?: 0

                    runOnUiThread {
                        val index = posts.indexOfFirst { it.id == post.id }
                        if (index != -1) {
                            posts[index].isLikedByCurrentUser = isLiked
                            posts[index].likesCount = likesCount
                            adapter.notifyItemChanged(index)
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@FYPActivity, "Failed to update like", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling like", e)
                runOnUiThread {
                    Toast.makeText(this@FYPActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }




    private fun handleComment(post: Post) {
        val intent = Intent(this, CommentsActivity::class.java)
        intent.putExtra("postId", post.id)
        startActivity(intent)
    }

    private fun openProfile(userId: String) {
        val currentUserId = auth.currentUser?.uid
        if (userId == currentUserId) {
            startActivity(Intent(this, MyProfileActivity::class.java))
        } else {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
        }
    }

    private fun fetchAndDisplayStories() {
        storiesRow.removeAllViews()

        // Add current user's story first
        addCurrentUserStory()

        // Load all active stories from backend
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@FYPActivity).getAuthToken()}"
                val response = RetrofitClient.instance.getActiveStories(token)

                if (response.isSuccessful && response.body()?.success == true) {
                    val stories = response.body()?.stories ?: emptyList()

                    // Group stories by userId to show one circle per user
                    val storiesByUser = stories.groupBy { it.userId }

                    runOnUiThread {
                        storiesByUser.forEach { (userId, userStories) ->
                            if (userId != currentUserId) {
                                val story = userStories.first() // Take first story for the user

                                val storyView = LayoutInflater.from(this@FYPActivity)
                                    .inflate(R.layout.story_item, storiesRow, false)

                                val storyImage = storyView.findViewById<ImageView>(R.id.storyImage)
                                val storyRing = storyView.findViewById<View>(R.id.storyRing)
                                val storyUsername = storyView.findViewById<TextView>(R.id.storyUsername)
                                val addIcon = storyView.findViewById<ImageView>(R.id.addStoryIcon)

                                storyUsername.text = story.username

                                if (story.profilePicUrl.isNotBlank()) {
                                    Glide.with(this@FYPActivity)
                                        .load(story.profilePicUrl)
                                        .circleCrop()
                                        .into(storyImage)
                                } else {
                                    storyImage.setImageResource(R.drawable.profile_pic)
                                }

                                storyRing.visibility = View.VISIBLE
                                addIcon.visibility = View.GONE

                                storyView.setOnClickListener {
                                    val intent = Intent(this@FYPActivity, OtherUserStoryActivity::class.java)
                                    intent.putExtra("storyUserId", userId)
                                    startActivity(intent)
                                }

                                storiesRow.addView(storyView)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to load stories: ${response.body()?.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stories", e)
            }
        }
    }

    private fun addCurrentUserStory() {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@FYPActivity).getAuthToken()}"

                // Get current user's profile
                val profileResponse = RetrofitClient.instance.getUserProfile("profile", currentUserId)

                // Get current user's stories
                val storiesResponse = RetrofitClient.instance.getUserStories(token, currentUserId)

                if (profileResponse.isSuccessful && profileResponse.body()?.success == true) {
                    val user = profileResponse.body()?.user
                    val hasActiveStory = storiesResponse.isSuccessful &&
                                        storiesResponse.body()?.success == true &&
                                        !storiesResponse.body()?.stories.isNullOrEmpty()

                    runOnUiThread {
                        val storyView = LayoutInflater.from(this@FYPActivity)
                            .inflate(R.layout.story_item, storiesRow, false)

                        val storyImage = storyView.findViewById<ImageView>(R.id.storyImage)
                        val storyRing = storyView.findViewById<View>(R.id.storyRing)
                        val storyUsername = storyView.findViewById<TextView>(R.id.storyUsername)
                        val addIcon = storyView.findViewById<ImageView>(R.id.addStoryIcon)

                        storyUsername.text = "Your Story"

                        val profilePicUrl = user?.profilePicUrl ?: ""
                        if (profilePicUrl.isNotBlank()) {
                            Glide.with(this@FYPActivity)
                                .load(profilePicUrl)
                                .circleCrop()
                                .placeholder(R.drawable.profile_pic)
                                .error(R.drawable.profile_pic)
                                .into(storyImage)
                        } else {
                            storyImage.setImageResource(R.drawable.profile_pic)
                        }

                        if (hasActiveStory) {
                            storyRing.visibility = View.VISIBLE
                            addIcon.visibility = View.GONE
                        } else {
                            storyRing.visibility = View.GONE
                            addIcon.visibility = View.VISIBLE
                        }

                        storyView.setOnClickListener {
                            if (hasActiveStory) {
                                startActivity(Intent(this@FYPActivity, MyStoryActivity::class.java))
                            } else {
                                startActivity(Intent(this@FYPActivity, AddStoryActivity::class.java))
                            }
                        }

                        storiesRow.addView(storyView, 0)
                    }
                } else {
                    Log.e(TAG, "Failed to load user profile for story")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading current user story", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPosts()
        loadCurrentUserProfile()
    }
}