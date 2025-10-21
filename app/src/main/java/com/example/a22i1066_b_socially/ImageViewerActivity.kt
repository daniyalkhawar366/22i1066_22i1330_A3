package com.example.a22i1066_b_socially

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var closeButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        viewPager = findViewById(R.id.viewPager)
        closeButton = findViewById(R.id.closeButton)

        val imageUrls = intent.getStringArrayListExtra("imageUrls") ?: arrayListOf()
        val initialPosition = intent.getIntExtra("initialPosition", 0)

        val adapter = ImageViewerAdapter(imageUrls)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(initialPosition, false)

        closeButton.setOnClickListener { finish() }
    }
}
