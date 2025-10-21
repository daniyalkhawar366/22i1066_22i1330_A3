package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class HighlightAdapter(
    private val highlights: MutableList<Highlight>,
    private val onHighlightClick: (Highlight) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var showAddButton = true

    companion object {
        private const val TYPE_ADD = 0
        private const val TYPE_HIGHLIGHT = 1
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val addImage: ImageView = view.findViewById(R.id.highlightImage)
        val titleText: TextView = view.findViewById(R.id.highlightTitle)

        init {
            addImage.setImageResource(R.drawable.plus)
            addImage.setPadding(20, 20, 20, 20)
            addImage.scaleType = ImageView.ScaleType.FIT_CENTER
            addImage.setColorFilter(0xFF8B5A5A.toInt())
            titleText.text = "New Highlight"
            addImage.setOnClickListener { onAddClick() }
        }
    }

    inner class HighlightViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val highlightImage: ImageView = view.findViewById(R.id.highlightImage)
        val highlightTitle: TextView = view.findViewById(R.id.highlightTitle)

        init {
            highlightImage.setPadding(0, 0, 0, 0)
            highlightImage.scaleType = ImageView.ScaleType.CENTER_CROP
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val highlightIndex = if (showAddButton) position - 1 else position
                    if (highlightIndex >= 0 && highlightIndex < highlights.size) {
                        onHighlightClick(highlights[highlightIndex])
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (showAddButton && position == 0) TYPE_ADD else TYPE_HIGHLIGHT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_highlight, parent, false)
        return if (viewType == TYPE_ADD) AddViewHolder(view) else HighlightViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HighlightViewHolder) {
            val highlightIndex = if (showAddButton) position - 1 else position
            if (highlightIndex >= 0 && highlightIndex < highlights.size) {
                val highlight = highlights[highlightIndex]
                holder.highlightTitle.text = highlight.title

                if (highlight.imageUrls.isNotEmpty()) {
                    Glide.with(holder.itemView.context)
                        .load(highlight.imageUrls.first())
                        .centerCrop()
                        .into(holder.highlightImage)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return highlights.size + if (showAddButton) 1 else 0
    }

    fun updateHighlights(newHighlights: List<Highlight>) {
        highlights.clear()
        highlights.addAll(newHighlights)
        notifyDataSetChanged()
    }
}
