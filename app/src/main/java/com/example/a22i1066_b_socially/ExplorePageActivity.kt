package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class ExplorePageActivity : AppCompatActivity() {
    private val TAG = "ExplorePageActivity"
    private var currentUserId: String = ""

    private lateinit var profile: ImageView
    private lateinit var post: ImageView
    private lateinit var notificationsfollowing: ImageView
    private lateinit var homebutton: ImageView
    private lateinit var searchBar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explore)

        val sessionManager = SessionManager(this)
        currentUserId = sessionManager.getUserId() ?: ""

        profile = findViewById(R.id.profile)
        profile.setOnClickListener {
            val intent = Intent(this, MyProfileActivity::class.java)
            startActivity(intent)
            finish()
        }
        post = findViewById(R.id.post)
        post.setOnClickListener {
            val intent = Intent(this, MakePostActivity::class.java)
            startActivity(intent)
            finish()
        }
        notificationsfollowing = findViewById(R.id.notificationsfollowing)
        notificationsfollowing.setOnClickListener {
            val intent = Intent(this, NotificationsFollowingActivity::class.java)
            startActivity(intent)
            finish()
        }
        homebutton = findViewById(R.id.homebtn)
        homebutton.setOnClickListener {
            val intent = Intent(this, FYPActivity::class.java)
            startActivity(intent)
            finish()
        }
        searchBar = findViewById(R.id.searchBar)
        searchBar.setOnClickListener {
            val intent = Intent(this, SearchBarActivity::class.java)
            startActivity(intent)
            finish()
        }

        loadUserProfilePic()
    }

    private fun loadUserProfilePic() {
        if (currentUserId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile(
                    userId = currentUserId,
                    currentUserId = currentUserId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val profilePic = response.body()?.user?.profilePicUrl
                    runOnUiThread {
                        if (!profilePic.isNullOrBlank()) {
                            Glide.with(this@ExplorePageActivity)
                                .load(profilePic)
                                .circleCrop()
                                .placeholder(R.drawable.profile_pic)
                                .error(R.drawable.profile_pic)
                                .into(profile)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile pic", e)
            }
        }
    }
}