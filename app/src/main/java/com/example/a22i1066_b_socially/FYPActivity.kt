package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging

class FYPActivity : AppCompatActivity() {
    private val TAG = "FYPActivity"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val dbRealtime = FirebaseDatabase.getInstance().reference
    private val postsList = mutableListOf<Post>()
    private var currentUserId: String = ""

    private lateinit var storiesRow: LinearLayout
    private lateinit var addStoryBtn: ImageView
    private lateinit var chatsBtn: ImageView
    private lateinit var homeBtn: ImageView
    private lateinit var exploreBtn: ImageView
    private lateinit var postBtn: ImageView
    private lateinit var notifBtn: ImageView
    private lateinit var profileBtn: ImageView
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private lateinit var adapter: PostAdapter
    private val posts = mutableListOf<Post>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
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
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
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
        adapter = PostAdapter(
            posts = mutableListOf(),
            onLikeClick = { post -> toggleLike(post) },
            onCommentClick = { post -> openComments(post) },
            onShareClick = { post -> sharePost(post) },  // Add this
            onProfileClick = { userId -> openProfile(userId) }
        )

        postsRecyclerView.adapter = adapter
        postsRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    private fun openUserProfile(userId: String) {
        if (userId == currentUserId) {
            startActivity(Intent(this, MyProfileActivity::class.java))
        } else {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
        }
    }

    private fun sharePost(post: Post) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this post by ${post.username}!")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }



    private fun openComments(post: Post) {
        val intent = Intent(this, CommentsActivity::class.java)
        intent.putExtra("postId", post.id)
        startActivity(intent)
    }


    private fun toggleLike(post: Post) {
        val postRef = FirebaseFirestore.getInstance().collection("posts").document(post.id)
        val likeRef = postRef.collection("likes").document(currentUserId)

        if (post.isLikedByCurrentUser) {
            // Unlike
            likeRef.delete()
            postRef.update("likesCount", FieldValue.increment(-1))
                .addOnSuccessListener {
                    adapter.updatePost(post.id, post.likesCount - 1, false)
                }
        } else {
            // Like
            likeRef.set(mapOf("timestamp" to FieldValue.serverTimestamp()))
            postRef.update("likesCount", FieldValue.increment(1))
                .addOnSuccessListener {
                    adapter.updatePost(post.id, post.likesCount + 1, true)
                }
        }
    }


    private fun checkIfPostsLiked() {
        postsList.forEach { post ->
            firestore.collection("posts").document(post.id)
                .collection("likes").document(currentUserId)
                .get()
                .addOnSuccessListener { doc ->
                    post.isLikedByCurrentUser = doc.exists()
                    adapter.notifyDataSetChanged()
                }
        }
    }

    private fun initializeViews() {
        storiesRow = findViewById(R.id.storiesRow)
        addStoryBtn = findViewById(R.id.addstory)
        chatsBtn = findViewById(R.id.chats)
        homeBtn = findViewById(R.id.homebtn)
        exploreBtn = findViewById(R.id.explorepg)
        postBtn = findViewById(R.id.post)
        notifBtn = findViewById(R.id.notificationsfollowing)
        profileBtn = findViewById(R.id.profile)
        postsRecyclerView = findViewById(R.id.postsRecyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
    }
    private fun handleShare(post: Post) {
        val intent = Intent(this, SharePostActivity::class.java)
        intent.putExtra("postId", post.id)
        startActivity(intent)
    }


    private fun setupRecyclerView() {
        adapter = PostAdapter(
            posts = posts,
            onLikeClick = { post -> handleLike(post) },
            onCommentClick = { post -> handleComment(post) },
            onShareClick = { post -> handleShare(post) },
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
        addStoryBtn.setOnClickListener {
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
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val profilePicUrl = doc.getString("profilePicUrl")
                        ?: doc.getString("profilePic")
                        ?: doc.getString("photoUrl")
                        ?: ""

                    if (profilePicUrl.isNotBlank()) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .circleCrop()
                            .placeholder(R.drawable.profileicon)
                            .error(R.drawable.profileicon)
                            .into(profileBtn)
                    } else {
                        profileBtn.setImageResource(R.drawable.profileicon)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load profile: ${e.message}")
                profileBtn.setImageResource(R.drawable.profileicon)
            }
    }


    private fun loadPosts(shuffle: Boolean = false) {
        FirebaseFirestore.getInstance().collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                postsList.clear()
                val postsToCheck = mutableListOf<Post>()

                for (doc in snapshot.documents) {
                    val post = Post(
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
                    postsToCheck.add(post)
                }

                // Check like status for all posts
                var checkedCount = 0
                postsToCheck.forEach { post ->
                    checkIfLiked(post.id) { isLiked ->
                        post.isLikedByCurrentUser = isLiked
                        checkedCount++

                        if (checkedCount == postsToCheck.size) {
                            postsList.addAll(postsToCheck)
                            if (shuffle) postsList.shuffle()
                            adapter.updatePosts(postsList)
                            swipeRefresh.isRefreshing = false
                        }
                    }
                }

                if (postsToCheck.isEmpty()) {
                    swipeRefresh.isRefreshing = false
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load posts", e)
                swipeRefresh.isRefreshing = false
            }
    }

    private fun checkIfLiked(postId: String, callback: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("posts").document(postId)
            .collection("likes").document(currentUserId)
            .get()
            .addOnSuccessListener { doc ->
                callback(doc.exists())
            }
            .addOnFailureListener {
                callback(false)
            }
    }



    private fun handleLike(post: Post) {
        val postRef = firestore.collection("posts").document(post.id)
        val likeRef = postRef.collection("likes").document(currentUserId)

        if (post.isLikedByCurrentUser) {
            // Unlike
            likeRef.delete()
            postRef.update("likesCount", FieldValue.increment(-1))
                .addOnSuccessListener {
                    val index = posts.indexOfFirst { it.id == post.id }
                    if (index != -1) {
                        posts[index].likesCount--
                        posts[index].isLikedByCurrentUser = false
                        adapter.notifyItemChanged(index)
                    }
                }
        } else {
            // Like
            likeRef.set(mapOf("timestamp" to FieldValue.serverTimestamp()))
            postRef.update("likesCount", FieldValue.increment(1))
                .addOnSuccessListener {
                    val index = posts.indexOfFirst { it.id == post.id }
                    if (index != -1) {
                        posts[index].likesCount++
                        posts[index].isLikedByCurrentUser = true
                        adapter.notifyItemChanged(index)
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
        val currentUserId = auth.currentUser?.uid ?: return
        val storiesRef = dbRealtime.child("stories")
        val now = System.currentTimeMillis()

        storiesRow.removeAllViews()

        addCurrentUserStory(currentUserId, now)

        storiesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    if (userId == currentUserId) continue

                    val hasActiveStory = userSnapshot.children.any { storySnapshot ->
                        val story = storySnapshot.getValue(Story::class.java)
                        story != null && story.expiresAt > now
                    }

                    if (hasActiveStory) {
                        firestore.collection("users").document(userId).get()
                            .addOnSuccessListener { doc ->
                                val username = doc?.getString("username") ?: "User"
                                val profilePicUrl = doc?.getString("profilePicUrl").orEmpty()

                                val storyView = LayoutInflater.from(this@FYPActivity)
                                    .inflate(R.layout.story_item, storiesRow, false)

                                val storyImage = storyView.findViewById<ImageView>(R.id.storyImage)
                                val storyRing = storyView.findViewById<View>(R.id.storyRing)
                                val storyUsername = storyView.findViewById<TextView>(R.id.storyUsername)
                                val addIcon = storyView.findViewById<ImageView>(R.id.addStoryIcon)

                                storyUsername.text = username

                                if (profilePicUrl.isNotBlank()) {
                                    Glide.with(this@FYPActivity)
                                        .load(profilePicUrl)
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
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load stories", error.toException())
            }
        })
    }

    private fun addCurrentUserStory(userId: String, now: Long) {
        val storiesRef = dbRealtime.child("stories").child(userId)

        storiesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val hasActiveStory = snapshot.children.any { storySnapshot ->
                    val story = storySnapshot.getValue(Story::class.java)
                    story != null && story.expiresAt > now
                }

                firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val username = doc?.getString("username") ?: "Your Story"
                        val profilePicUrl = doc?.getString("profilePicUrl").orEmpty()

                        val storyView = LayoutInflater.from(this@FYPActivity)
                            .inflate(R.layout.story_item, storiesRow, false)

                        val storyImage = storyView.findViewById<ImageView>(R.id.storyImage)
                        val storyRing = storyView.findViewById<View>(R.id.storyRing)
                        val storyUsername = storyView.findViewById<TextView>(R.id.storyUsername)
                        val addIcon = storyView.findViewById<ImageView>(R.id.addStoryIcon)

                        storyUsername.text = username

                        if (profilePicUrl.isNotBlank()) {
                            Glide.with(this@FYPActivity)
                                .load(profilePicUrl)
                                .circleCrop()
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
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check current user's stories", error.toException())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadPosts()
        loadCurrentUserProfile()
    }
}