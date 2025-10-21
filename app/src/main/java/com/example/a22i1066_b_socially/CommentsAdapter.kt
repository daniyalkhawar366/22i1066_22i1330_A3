package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class CommentsAdapter(
    private val comments: List<Comment>,
    private val currentUserId: String,
    private val onDelete: (Comment) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePic: ImageView = view.findViewById(R.id.profilePic)
        val username: TextView = view.findViewById(R.id.username)
        val commentText: TextView = view.findViewById(R.id.commentText)
        val timestamp: TextView = view.findViewById(R.id.timestamp)
        val deleteBtn: ImageView = view.findViewById(R.id.deleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]

        holder.username.text = comment.username
        holder.commentText.text = comment.text

        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        holder.timestamp.text = sdf.format(Date(comment.timestamp))

        if (comment.profilePicUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(comment.profilePicUrl)
                .circleCrop()
                .into(holder.profilePic)
        } else {
            holder.profilePic.setImageResource(R.drawable.profile_pic)
        }

        if (comment.userId == currentUserId) {
            holder.deleteBtn.visibility = View.VISIBLE
            holder.deleteBtn.setOnClickListener { onDelete(comment) }
        } else {
            holder.deleteBtn.visibility = View.GONE
        }
    }

    override fun getItemCount() = comments.size
}
