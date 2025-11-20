package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.a22i1066_b_socially.offline.OfflineIntegrationHelper

class PostImageAdapter(
    private val imageUrls: List<String>
) : RecyclerView.Adapter<PostImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.postImage)
        val imageCounter: TextView = view.findViewById(R.id.imageCounter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        // Use offline image loading with proper placeholder
        OfflineIntegrationHelper.loadImage(
            holder.imageView,
            imageUrls[position],
            R.drawable.profile_pic, // Better placeholder
            R.drawable.profile_pic, // Better error image
            circular = false
        )

        // Show image counter if multiple images
        if (imageUrls.size > 1) {
            holder.imageCounter.visibility = View.VISIBLE
            holder.imageCounter.text = "${position + 1}/${imageUrls.size}"
        } else {
            holder.imageCounter.visibility = View.GONE
        }
    }

    override fun getItemCount() = imageUrls.size
}
