package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryAdapter(
    private val images: List<GalleryImage>,
    private val onImageClick: (GalleryImage, Int) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.galleryImageView)
        val selectionOverlay: View = view.findViewById(R.id.selectionOverlay)
        val selectionCheckmark: ImageView = view.findViewById(R.id.selectionCheckmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = images[position]

        Glide.with(holder.itemView.context)
            .load(image.uri)
            .centerCrop()
            .into(holder.imageView)

        holder.selectionOverlay.visibility = if (image.isSelected) View.VISIBLE else View.GONE
        holder.selectionCheckmark.visibility = if (image.isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onImageClick(image, position)
        }
    }

    override fun getItemCount() = images.size
}
