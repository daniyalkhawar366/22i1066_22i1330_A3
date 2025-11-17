package com.example.a22i1066_b_socially

import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CommentPreviewAdapter(
    private val comments: List<Comment>
) : RecyclerView.Adapter<CommentPreviewAdapter.CommentPreviewViewHolder>() {

    inner class CommentPreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commentText: TextView = view.findViewById(R.id.commentPreviewText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentPreviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment_preview, parent, false)
        return CommentPreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentPreviewViewHolder, position: Int) {
        val comment = comments[position]

        // Create spannable text with bold username
        val fullText = "${comment.username} ${comment.text}"
        val spannable = SpannableString(fullText)
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            comment.username.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        holder.commentText.text = spannable
    }

    override fun getItemCount() = comments.size.coerceAtMost(2) // Show max 2 comments
}

