package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NotificationsYouActivity : AppCompatActivity() {
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
    }
}