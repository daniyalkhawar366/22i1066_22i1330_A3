package com.example.a22i1066_b_socially

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.UserListItem

class FollowListAdapter(
    private val items: MutableList<UserListItem>,
    private val currentUserId: String,
    private val onFollowToggle: (UserListItem, Boolean) -> Unit,
    private val listType: String = "default" // "followers", "following", or "default"
) : RecyclerView.Adapter<FollowListAdapter.ViewHolder>() {

    private val followStatusMap = mutableMapOf<String, Boolean>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: ImageView = view.findViewById(R.id.searchProfileImage)
        val username: TextView = view.findViewById(R.id.username)
        val subtext: TextView = view.findViewById(R.id.subtext)
        val followButton: Button = view.findViewById(R.id.followButton)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val user = items[position]
                    // Navigate to profile
                    if (user.userId == currentUserId) {
                        view.context.startActivity(Intent(view.context, MyProfileActivity::class.java))
                    } else {
                        val intent = Intent(view.context, ProfileActivity::class.java)
                        intent.putExtra("userId", user.userId)
                        view.context.startActivity(intent)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = items[position]

        holder.username.text = user.username

        // Show display name or bio as subtitle
        val first = user.firstName?.trim().orEmpty()
        val last = user.lastName?.trim().orEmpty()
        val fullName = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
        val displayName = fullName.ifEmpty { user.displayName.orEmpty() }

        val subtitle = if (displayName.isNotBlank() && displayName != user.username) {
            displayName
        } else {
            user.bio.orEmpty()
        }
        holder.subtext.text = subtitle

        // Load profile image
        val profilePicUrl = user.profilePic?.trim()
        if (!profilePicUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(profilePicUrl)
                .circleCrop()
                .placeholder(R.drawable.profileicon)
                .error(R.drawable.profileicon)
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.profileicon)
        }

        // Handle follow button
        if (user.userId == currentUserId) {
            holder.followButton.visibility = View.GONE
        } else {
            holder.followButton.visibility = View.VISIBLE

            // Determine initial follow status based on list type
            val initialStatus = when (listType) {
                "following" -> true  // If viewing "following" list, we are following them
                "followers" -> false // If viewing "followers" list, check map or default to false
                else -> followStatusMap[user.userId] ?: false
            }

            // Update map if not set
            if (!followStatusMap.containsKey(user.userId)) {
                followStatusMap[user.userId] = initialStatus
            }

            val isFollowing = followStatusMap[user.userId] ?: initialStatus
            updateFollowButton(holder.followButton, isFollowing)

            holder.followButton.setOnClickListener {
                val currentStatus = followStatusMap[user.userId] ?: false
                onFollowToggle(user, currentStatus)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<UserListItem>) {
        items.clear()
        items.addAll(newItems)

        // Initialize follow status based on list type
        when (listType) {
            "following" -> newItems.forEach { followStatusMap[it.userId] = true }
            "followers" -> newItems.forEach {
                if (!followStatusMap.containsKey(it.userId)) {
                    followStatusMap[it.userId] = false
                }
            }
            else -> newItems.forEach {
                if (!followStatusMap.containsKey(it.userId)) {
                    followStatusMap[it.userId] = false
                }
            }
        }

        notifyDataSetChanged()
    }

    fun updateFollowStatus(userId: String, isFollowing: Boolean) {
        followStatusMap[userId] = isFollowing
        val position = items.indexOfFirst { it.userId == userId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    private fun updateFollowButton(button: Button, isFollowing: Boolean) {
        if (isFollowing) {
            button.text = "Following"
            button.setBackgroundColor(Color.parseColor("#EFEFEF"))
            button.setTextColor(Color.BLACK)
        } else {
            button.text = "Follow"
            button.setBackgroundColor(Color.parseColor("#8B5A5A"))
            button.setTextColor(Color.WHITE)
        }
    }
}

