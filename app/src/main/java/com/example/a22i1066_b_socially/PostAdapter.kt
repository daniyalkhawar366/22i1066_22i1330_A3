package com.example.a22i1066_b_socially

import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.a22i1066_b_socially.offline.OfflineIntegrationHelper

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

        // Instagram-style caption section
        val captionSection: LinearLayout = view.findViewById(R.id.captionSection)
        val captionProfilePic: ImageView = view.findViewById(R.id.captionProfilePic)
        val captionWithUsername: TextView = view.findViewById(R.id.captionWithUsername)

        // Comments preview
        val commentsPreviewRecycler: RecyclerView = view.findViewById(R.id.commentsPreviewRecycler)
        val viewAllComments: TextView = view.findViewById(R.id.viewAllComments)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]

        holder.username.text = post.username

        // Load profile pic with offline caching
        if (post.profilePicUrl.isNotBlank()) {
            OfflineIntegrationHelper.loadImage(
                holder.profilePic,
                post.profilePicUrl,
                R.drawable.profile_pic,
                R.drawable.profile_pic,
                circular = true
            )

            OfflineIntegrationHelper.loadImage(
                holder.captionProfilePic,
                post.profilePicUrl,
                R.drawable.profile_pic,
                R.drawable.profile_pic,
                circular = true
            )
        } else {
            holder.profilePic.setImageResource(R.drawable.profile_pic)
            holder.captionProfilePic.setImageResource(R.drawable.profile_pic)
        }

        // Setup image ViewPager
        val imageAdapter = PostImageAdapter(post.imageUrls)
        holder.imageViewPager.adapter = imageAdapter

        // Likes count
        holder.likes.text = "${post.likesCount} likes"

        // Like button icon
        val likeIcon = if (post.isLikedByCurrentUser) R.drawable.like_filled else R.drawable.like
        holder.likeBtn.setImageResource(likeIcon)

        // Caption section (Instagram style)
        if (post.caption.isNotBlank()) {
            holder.captionSection.visibility = View.VISIBLE

            // Create spannable string with bold username
            val fullCaption = "${post.username} ${post.caption}"
            val spannable = SpannableString(fullCaption)
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                post.username.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            holder.captionWithUsername.text = spannable
        } else {
            holder.captionSection.visibility = View.GONE
        }

        // Comments preview (show max 2 comments)
        if (post.previewComments.isNotEmpty()) {
            holder.commentsPreviewRecycler.visibility = View.VISIBLE
            val commentsAdapter = CommentPreviewAdapter(post.previewComments)
            holder.commentsPreviewRecycler.adapter = commentsAdapter
            holder.commentsPreviewRecycler.layoutManager = LinearLayoutManager(holder.itemView.context)

            // Show "View all comments" if there are more than 2 comments
            if (post.commentsCount > 2) {
                holder.viewAllComments.visibility = View.VISIBLE
                holder.viewAllComments.text = "View all ${post.commentsCount} comments"
                holder.viewAllComments.setOnClickListener { onCommentClick(post) }
            } else {
                holder.viewAllComments.visibility = View.GONE
            }
        } else {
            holder.commentsPreviewRecycler.visibility = View.GONE
            holder.viewAllComments.visibility = View.GONE
        }

        // Click listeners
        holder.profilePic.setOnClickListener { onProfileClick(post.userId) }
        holder.username.setOnClickListener { onProfileClick(post.userId) }
        holder.likeBtn.setOnClickListener { onLikeClick(post) }
        holder.commentBtn.setOnClickListener { onCommentClick(post) }
        holder.shareBtn.setOnClickListener { onShareClick(post) }
    }

    override fun getItemCount() = posts.size

    fun updatePosts(newPosts: List<Post>) {
        android.util.Log.d("PostAdapter", "updatePosts called with ${newPosts.size} posts")
        posts.clear()
        android.util.Log.d("PostAdapter", "After clear: ${posts.size}")
        posts.addAll(newPosts)
        android.util.Log.d("PostAdapter", "After addAll: ${posts.size}")
        notifyDataSetChanged()
        android.util.Log.d("PostAdapter", "notifyDataSetChanged called, itemCount: ${itemCount}")
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
