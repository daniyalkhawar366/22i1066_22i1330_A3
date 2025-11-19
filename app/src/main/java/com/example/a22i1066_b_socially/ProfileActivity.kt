package com.example.a22i1066_b_socially

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FieldValue

class ProfileActivity : AppCompatActivity() {

    private lateinit var toolbarUsername: TextView
    private lateinit var backArrow: ImageView
    private lateinit var menuDots: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var profileOnlineIndicator: View
    private lateinit var postsCount: TextView
    private lateinit var followersCount: TextView
    private lateinit var followingCount: TextView
    private lateinit var displayName: TextView
    private lateinit var threadsLink: LinearLayout
    private lateinit var threadsUsername: TextView
    private lateinit var displayTitle: TextView
    private lateinit var bioText: TextView
    private lateinit var followButton: Button
    private lateinit var messageButton: Button
    private lateinit var emailButton: Button
    private lateinit var addContactImage: ImageView
    private lateinit var highlightsRecycler: RecyclerView
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var scrollContainer: androidx.core.widget.NestedScrollView
    private lateinit var bottomProfileThumb: ImageView

    private lateinit var highlightAdapter: HighlightAdapter
    private lateinit var postsAdapter: ProfilePostAdapter
    private val postsList = mutableListOf<Post>()

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var highlightsListener: ListenerRegistration? = null
    private var profileStatusRef: DatabaseReference? = null
    private var profileStatusListener: ValueEventListener? = null

    private var targetUserId: String = ""
    private val currentUid get() = auth.currentUser?.uid ?: ""
    private var isFollowing = false

    private val TAG = "ProfileActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_layout)

        targetUserId = intent.getStringExtra("userId")
            ?: intent.getStringExtra("USER_ID")
            ?: ""

        if (targetUserId.isEmpty()) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            initViews()
            setupRecyclers()
            setupListeners()
            loadProfile()
            loadUserPosts()
            loadHighlights()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ProfileActivity", e)
            Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initViews() {
        toolbarUsername = findViewById(R.id.toolbarUsername)
        backArrow = findViewById(R.id.backArrow)
        menuDots = findViewById(R.id.menuDots)
        profileImage = findViewById(R.id.profileImage)
        profileOnlineIndicator = findViewById(R.id.profileOnlineIndicator)
        postsCount = findViewById(R.id.postsCount)
        followersCount = findViewById(R.id.followersCount)
        followingCount = findViewById(R.id.followingCount)
        displayName = findViewById(R.id.displayName)
        threadsLink = findViewById(R.id.threadsLink)
        threadsUsername = findViewById(R.id.threadsUsername)
        displayTitle = findViewById(R.id.displayTitle)
        bioText = findViewById(R.id.bioText)
        followButton = findViewById(R.id.followButton)
        messageButton = findViewById(R.id.messageButton)
        emailButton = findViewById(R.id.emailButton)
        addContactImage = findViewById(R.id.addContactImage)
        highlightsRecycler = findViewById(R.id.highlightsRecycler)
        postsRecyclerView = findViewById(R.id.postsRecyclerView)
        progressOverlay = findViewById(R.id.progressOverlay)
        scrollContainer = findViewById(R.id.scrollContainer)
        bottomProfileThumb = findViewById(R.id.bottomProfileThumb)

        progressOverlay.visibility = View.VISIBLE
        scrollContainer.visibility = View.GONE
        profileOnlineIndicator.visibility = View.GONE
    }

    private fun setupRecyclers() {
        highlightsRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        highlightAdapter = HighlightAdapter(
            mutableListOf(),
            { highlight -> openHighlight(highlight) },
            {} // Empty lambda - won't be used when showAddButton = false
        )
        // Hide add button on other users' profiles
        highlightAdapter.showAddButton = false
        highlightsRecycler.adapter = highlightAdapter

        postsAdapter = ProfilePostAdapter(postsList) { post ->
            val intent = Intent(this, PostDetailActivity::class.java)
            intent.putExtra("postId", post.id)
            startActivity(intent)
        }
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        postsRecyclerView.adapter = postsAdapter
    }

    private fun setupListeners() {
        backArrow.setOnClickListener { finish() }
        menuDots.setOnClickListener { showMenu() }
        followButton.setOnClickListener { toggleFollow() }
        messageButton.setOnClickListener { openMessages() }
        emailButton.setOnClickListener { copyEmailToClipboard() }
        addContactImage.setOnClickListener { addContact() }

        followersCount.setOnClickListener {
            val intent = Intent(this, FollowersFollowingActivity::class.java)
            intent.putExtra("userId", targetUserId)
            intent.putExtra("listType", "followers")
            startActivity(intent)
        }

        followingCount.setOnClickListener {
            val intent = Intent(this, FollowersFollowingActivity::class.java)
            intent.putExtra("userId", targetUserId)
            intent.putExtra("listType", "following")
            startActivity(intent)
        }

        // Bottom navigation
        findViewById<ImageView>(R.id.homebtn).setOnClickListener {
            startActivity(Intent(this, FYPActivity::class.java))
            finish()
        }
        findViewById<ImageView>(R.id.explorepg).setOnClickListener {
            startActivity(Intent(this, ExplorePageActivity::class.java))
            finish()
        }
        findViewById<ImageView>(R.id.post).setOnClickListener {
            startActivity(Intent(this, MakePostActivity::class.java))
        }
        findViewById<ImageView>(R.id.notificationsfollowing).setOnClickListener {
            startActivity(Intent(this, NotificationsFollowingActivity::class.java))
            finish()
        }
        bottomProfileThumb.setOnClickListener {
            startActivity(Intent(this, MyProfileActivity::class.java))
            finish()
        }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile(
                    userId = targetUserId,
                    currentUserId = currentUid
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user == null) {
                        runOnUiThread {
                            Toast.makeText(this@ProfileActivity, "User not found", Toast.LENGTH_SHORT).show()
                            showContent()
                            finish()
                        }
                        return@launch
                    }

                    runOnUiThread {
                        // Setup online status listener (still using Firebase Realtime DB for presence)
                        setupOnlineStatusListener()

                        toolbarUsername.text = user.username

                        val first = user.firstName.trim()
                        val last = user.lastName.trim()
                        val fullName = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
                        displayName.text = if (fullName.isNotEmpty()) fullName else user.displayName

                        displayTitle.text = user.title
                        val bio = user.bio
                        bioText.text = bio
                        if (bio.length > 100) {
                            setupBioExpandable(bio)
                        }

                        val threadsUser = user.threadsUsername.takeIf { it.isNotBlank() }
                            ?: user.username.takeIf { it.isNotBlank() } ?: ""
                        if (threadsUser.isNotEmpty()) {
                            threadsUsername.text = threadsUser
                            threadsLink.visibility = View.VISIBLE
                        } else {
                            threadsLink.visibility = View.GONE
                        }

                        postsCount.text = user.postsCount.toString()
                        followersCount.text = user.followersCount.toString()
                        followingCount.text = user.followingCount.toString()

                        val pic = user.profilePicUrl
                        if (pic.isNotBlank()) {
                            Glide.with(this@ProfileActivity).load(pic).circleCrop().into(profileImage)
                            Glide.with(this@ProfileActivity).load(pic).circleCrop().into(bottomProfileThumb)
                        } else {
                            profileImage.setImageResource(R.drawable.profile_pic)
                            bottomProfileThumb.setImageResource(R.drawable.profileicon)
                        }

                        // Hide follow button on own profile
                        if (currentUid.isNotBlank() && currentUid == targetUserId) {
                            followButton.visibility = View.GONE
                        } else {
                            followButton.visibility = View.VISIBLE
                            // Use isFollowing from API response
                            setFollowUi(user.isFollowing)
                        }

                        showContent()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Failed to load profile: ${response.body()?.error}",
                            Toast.LENGTH_SHORT
                        ).show()
                        showContent()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Error loading profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showContent()
                }
            }
        }
    }

    private fun setupOnlineStatusListener() {
        // Remove previous listener if exists
        profileStatusRef?.let { r -> profileStatusListener?.let { r.removeEventListener(it) } }

        profileStatusRef = FirebaseDatabase.getInstance().getReference("status").child(targetUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                profileOnlineIndicator.visibility = if (online) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                profileOnlineIndicator.visibility = View.GONE
            }
        }
        profileStatusRef?.addValueEventListener(listener)
        profileStatusListener = listener
    }

    private fun setupBioExpandable(fullText: String) {
        bioText.maxLines = 2
        bioText.ellipsize = android.text.TextUtils.TruncateAt.END
        bioText.post {
            val layout = bioText.layout
            if (layout != null && layout.lineCount > 2) {
                val spannable = SpannableString(fullText.take(120) + "… more")
                val clickable = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        bioText.text = fullText
                        bioText.maxLines = Integer.MAX_VALUE
                        bioText.movementMethod = null
                    }
                }
                spannable.setSpan(
                    clickable,
                    spannable.length - 4,
                    spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                bioText.text = spannable
                bioText.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    private fun loadUserPosts() {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@ProfileActivity).getAuthToken()}"
                val response = RetrofitClient.instance.getUserPosts(token, targetUserId)

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

    private fun loadHighlights() {
        highlightsListener = db.collection("highlights")
            .whereEqualTo("userId", targetUserId)
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
                            date = doc.getTimestamp("date")?.toDate()?.time ?: 0L // Convert Timestamp? to Long (milliseconds)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing highlight", e)
                        null
                    }
                } ?: emptyList()

                highlightAdapter.updateHighlights(highlights)
            }
    }

    private fun setFollowUi(following: Boolean) {
        isFollowing = following
        if (following) {
            followButton.text = "Following"
            followButton.setBackgroundColor(Color.parseColor("#EFEFEF"))
            followButton.setTextColor(Color.BLACK)
        } else {
            followButton.text = "Follow"
            followButton.setBackgroundColor(Color.parseColor("#8B5A5A"))
            followButton.setTextColor(Color.WHITE)
        }
    }

    private fun toggleFollow() {
        if (currentUid.isBlank()) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUid == targetUserId) {
            Toast.makeText(this, "Cannot follow yourself", Toast.LENGTH_SHORT).show()
            return
        }

        if (isFollowing) {
            AlertDialog.Builder(this)
                .setTitle("Unfollow")
                .setMessage("Unfollow this user?")
                .setPositiveButton("Unfollow") { _, _ -> unfollowUser() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            followUser()
        }
    }

    private fun followUser() {
        if (currentUid.isBlank() || currentUid == targetUserId) return

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@ProfileActivity).getAuthToken()}"
                val request = com.example.a22i1066_b_socially.network.FollowRequest(targetUserId)
                val response = RetrofitClient.instance.followUser(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        setFollowUi(true)
                        // Update the follower count display
                        val currentCount = followersCount.text.toString().toIntOrNull() ?: 0
                        followersCount.text = (currentCount + 1).toString()
                        Toast.makeText(this@ProfileActivity, "Followed", Toast.LENGTH_SHORT).show()

                        // Create notification request for the follow action (still using Firebase)
                        createFollowNotification()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Failed to follow: ${response.body()?.error ?: "unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to follow", e)
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Failed to follow: ${e.message ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createFollowNotification() {
        val requestData = mapOf(
            "type" to "follow_request",
            "userId" to currentUid,
            "targetUserId" to targetUserId,
            "followerId" to currentUid,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("notification_requests")
            .add(requestData)
            .addOnSuccessListener {
                Log.d(TAG, "Follow notification request created successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create follow notification", e)
            }
    }

    private fun unfollowUser() {
        if (currentUid.isBlank() || currentUid == targetUserId) return

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@ProfileActivity).getAuthToken()}"
                val request = com.example.a22i1066_b_socially.network.FollowRequest(targetUserId)
                val response = RetrofitClient.instance.unfollowUser(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        setFollowUi(false)
                        // Update the follower count display
                        val currentCount = followersCount.text.toString().toIntOrNull() ?: 0
                        followersCount.text = maxOf(0, currentCount - 1).toString()
                        Toast.makeText(this@ProfileActivity, "Unfollowed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Failed to unfollow: ${response.body()?.error ?: "unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unfollow", e)
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Failed to unfollow: ${e.message ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openMessages() {
        if (currentUid.isBlank()) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show()
            return
        }

        val usernameForIntent = toolbarUsername.text.toString()
        val intent = Intent(this, ChatDetailActivity::class.java)
        intent.putExtra("receiverUserId", targetUserId)
        intent.putExtra("receiverUsername", usernameForIntent)
        intent.putExtra("CURRENT_USER_ID", currentUid)
        intent.putExtra("otherUserId", targetUserId)
        startActivity(intent)
    }

    private fun copyEmailToClipboard() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile(
                    userId = targetUserId,
                    currentUserId = currentUid
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val email = response.body()?.user?.email?.takeIf { it.isNotBlank() }

                    runOnUiThread {
                        if (email.isNullOrBlank()) {
                            Toast.makeText(this@ProfileActivity, "No email available", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }

                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("email", email)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@ProfileActivity, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ProfileActivity, "Failed to get email", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get email", e)
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Failed to get email", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addContact() {
        Toast.makeText(this, "Add contact feature coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun openHighlight(highlight: Highlight) {
        val intent = Intent(this, HighlightViewActivity::class.java)
        intent.putExtra("highlightId", highlight.id)
        startActivity(intent)
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(arrayOf("Block User", "Report User")) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Block feature coming soon", Toast.LENGTH_SHORT)
                        .show()
                    1 -> Toast.makeText(this, "Report feature coming soon", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showContent() {
        progressOverlay.visibility = View.GONE
        scrollContainer.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Firebase Realtime Database listener for online status
        profileStatusRef?.let { r -> profileStatusListener?.let { r.removeEventListener(it) } }
        profileStatusRef = null
        profileStatusListener = null
        // Note: We no longer use Firestore listeners for profile data
        highlightsListener?.remove()
        highlightsListener = null
    }
}
