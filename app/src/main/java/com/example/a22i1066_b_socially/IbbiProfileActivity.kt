package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class IbbiProfileActivity : AppCompatActivity() {
    private lateinit var ibbichat: Button
    private lateinit var ibbiHighlight1: LinearLayout
    private lateinit var ibbiuni: LinearLayout
    private lateinit var ibbifootball: LinearLayout
    private lateinit var ibbicars: LinearLayout
    private lateinit var closebtn: ImageView
    private lateinit var explorepg: ImageView
    private lateinit var profile: ImageView
    private lateinit var post: ImageView
    private lateinit var notificationsfollowing: ImageView
    private lateinit var homebutton: ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ibbiprofile)
        ibbichat = findViewById(R.id.ibbichat)

        ibbiHighlight1 = findViewById(R.id.kashmir)
        ibbiHighlight1.setOnClickListener {
            val intent = Intent(this, ibbiHighlights1Activity::class.java)
            startActivity(intent)
            finish()
        }
        ibbiuni = findViewById(R.id.university)
        ibbiuni.setOnClickListener {
            val intent = Intent(this, ibbiHighlights2Activity::class.java)
            startActivity(intent)
            finish()
        }
        ibbifootball = findViewById(R.id.football)
        ibbifootball.setOnClickListener {
            val intent = Intent(this, ibbiHighlights3Activity::class.java)
            startActivity(intent)
            finish()
        }
        ibbicars = findViewById(R.id.cars)
        ibbicars.setOnClickListener {
            val intent = Intent(this, ibbiHighlights4Activity::class.java)
            startActivity(intent)
            finish()
        }
        closebtn = findViewById(R.id.homebtn)
        closebtn.setOnClickListener {
            val intent = Intent(this, FYPActivity::class.java)
            startActivity(intent)
            finish()
        }
        explorepg = findViewById(R.id.explorepg)
        explorepg.setOnClickListener {
            val intent = Intent(this, ExplorePageActivity::class.java)
            startActivity(intent)
            finish()
        }
        homebutton = findViewById(R.id.homebtn2)
        homebutton.setOnClickListener {
            val intent = Intent(this, FYPActivity::class.java)
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
        notificationsfollowing = findViewById(R.id.notificationsfollowing)
        notificationsfollowing.setOnClickListener {
            val intent = Intent(this, NotificationsFollowingActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}