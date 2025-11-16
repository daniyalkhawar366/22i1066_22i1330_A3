package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

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
        val moreBtn: ImageView = view.findViewById(R.id.moreBtn)
        val imageViewPager: ViewPager2 = view.findViewById(R.id.imageViewPager)
        val likeBtn: ImageView = view.findViewById(R.id.likeBtn)
        val commentBtn: ImageView = view.findViewById(R.id.commentBtn)
        val shareBtn: ImageView = view.findViewById(R.id.shareBtn)
        val bookmarkBtn: ImageView = view.findViewById(R.id.bookmarkBtn)
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
        holder.caption.text = post.caption

        if (post.profilePicUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(post.profilePicUrl)
                .circleCrop()
                .placeholder(R.drawable.profile_pic)
                .error(R.drawable.profile_pic)
                .into(holder.profilePic)
        } else {
            holder.profilePic.setImageResource(R.drawable.profile_pic)
        }

        val imageAdapter = PostImageAdapter(post.imageUrls)
        holder.imageViewPager.adapter = imageAdapter

        holder.likes.text = "${post.likesCount} likes"

        val likeIcon = if (post.isLikedByCurrentUser) R.drawable.like_filled else R.drawable.like
        holder.likeBtn.setImageResource(likeIcon)

        holder.profilePic.setOnClickListener { onProfileClick(post.userId) }
        holder.username.setOnClickListener { onProfileClick(post.userId) }
        holder.likeBtn.setOnClickListener { onLikeClick(post) }
        holder.commentBtn.setOnClickListener { onCommentClick(post) }
        holder.shareBtn.setOnClickListener { onShareClick(post) }
    }

    override fun getItemCount() = posts.size

    fun updatePosts(newPosts: List<Post>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    fun updatePost(postId: String, newLikesCount: Int, isLiked: Boolean) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index != -1) {
            posts[index].likesCount = newLikesCount
            posts[index].isLikedByCurrentUser = isLiked
            notifyItemChanged(index)
        }
    }
}
