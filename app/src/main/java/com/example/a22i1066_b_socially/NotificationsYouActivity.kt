package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class NotificationsYouActivity : AppCompatActivity() {
    private val TAG = "NotificationsYouActivity"
    private var currentUserId: String = ""

    private lateinit var explorepg: ImageView
    private lateinit var profile: ImageView
    private lateinit var post: ImageView
    private lateinit var homebutton: ImageView
    private lateinit var tabfollowing: TextView
    private lateinit var rafaychat: Button
    private lateinit var ibbiprofile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notifications_you)

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
        tabfollowing = findViewById(R.id.tabFollowing)
        tabfollowing.setOnClickListener {
            val intent = Intent(this, NotificationsFollowingActivity::class.java)
            startActivity(intent)
            finish()
        }

        ibbiprofile = findViewById(R.id.ibbiprofile)
        ibbiprofile.setOnClickListener {
            val intent = Intent(this, IbbiProfileActivity::class.java)
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
                            Glide.with(this@NotificationsYouActivity)
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