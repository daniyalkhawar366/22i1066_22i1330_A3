package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class NotificationsFollowingActivity : AppCompatActivity() {
    private val TAG = "NotificationsFollowingActivity"
    private var currentUserId: String = ""

    private lateinit var explorepg: ImageView
    private lateinit var profile: ImageView
    private lateinit var post: ImageView
    private lateinit var homebutton: ImageView
    private lateinit var notificationsyou: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notifications_following)

        val sessionManager = SessionManager(this)
        currentUserId = sessionManager.getUserId() ?: ""

        explorepg = findViewById(R.id.explorepg)
        explorepg.setOnClickListener {
            val intent = Intent(this, ExplorePageActivity::class.java)
            startActivity(intent)
            finish()
        }
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
        homebutton = findViewById(R.id.homebtn)
        homebutton.setOnClickListener {
            val intent = Intent(this, FYPActivity::class.java)
            startActivity(intent)
            finish()
        }
        notificationsyou = findViewById(R.id.notificationsyou)
        notificationsyou.setOnClickListener {
            val intent = Intent(this, NotificationsYouActivity::class.java)
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
                            Glide.with(this@NotificationsFollowingActivity)
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