package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide

class PostAdapter(
    private val posts: MutableList<Post>,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onShareClick: (Post) -> Unit,
    private val onProfileClick: (String) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePic: ImageView = view.findViewById(R.id.profilePic)
        val username: TextView = view.findViewById(R.id.username)
        val imageViewPager: ViewPager2 = view.findViewById(R.id.imageViewPager)
        val likeBtn: ImageView = view.findViewById(R.id.likeBtn)
        val commentBtn: ImageView = view.findViewById(R.id.commentBtn)
        val shareBtn: ImageView = view.findViewById(R.id.shareBtn)
        val likes: TextView = view.findViewById(R.id.likes)
        val caption: TextView = view.findViewById(R.id.caption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]

        holder.username.text = post.username
        holder.likes.text = "${post.likesCount} likes"
        holder.caption.text = if (post.caption.isNotBlank()) {
            "${post.username} ${post.caption}"
        } else {
            ""
        }

        if (post.profilePicUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(post.profilePicUrl)
                .circleCrop()
                .into(holder.profilePic)
        } else {
            holder.profilePic.setImageResource(R.drawable.profile_pic)
        }

        if (post.imageUrls.isNotEmpty()) {
            val imageAdapter = PostImageAdapter(post.imageUrls)
            holder.imageViewPager.adapter = imageAdapter
        }

        // Update like button appearance
        if (post.isLikedByCurrentUser) {
            holder.likeBtn.setImageResource(R.drawable.like_filled)
            holder.likeBtn.setColorFilter(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light))
        } else {
            holder.likeBtn.setImageResource(R.drawable.like)
            holder.likeBtn.clearColorFilter()
        }

        holder.profilePic.setOnClickListener { onProfileClick(post.userId) }
        holder.username.setOnClickListener { onProfileClick(post.userId) }
        holder.likeBtn.setOnClickListener { onLikeClick(post) }
        holder.commentBtn.setOnClickListener { onCommentClick(post) }
        holder.shareBtn.setOnClickListener { onShareClick(post) }

        holder.likes.setOnClickListener { onCommentClick(post) }
    }

    override fun getItemCount() = posts.size

    fun updatePosts(newPosts: List<Post>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    fun updatePost(postId: String, likesCount: Long, isLiked: Boolean) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index != -1) {
            posts[index].likesCount = likesCount
            posts[index].isLikedByCurrentUser = isLiked
            notifyItemChanged(index)
        }
    }
}
