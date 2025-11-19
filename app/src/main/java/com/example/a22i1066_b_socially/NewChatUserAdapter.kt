package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.a22i1066_b_socially.network.UserListItem

class NewChatUserAdapter(
    private val users: List<UserListItem>,
    private val onMessageClick: (UserListItem) -> Unit
) : RecyclerView.Adapter<NewChatUserAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.userProfileImage)
        val username: TextView = itemView.findViewById(R.id.userUsername)
        val messageButton: Button = itemView.findViewById(R.id.messageButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_new_chat_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]

        holder.username.text = user.username

        // Load profile picture
        val picUrl = user.profilePic?.trim().orEmpty()
        if (picUrl.isNotBlank()) {
            Glide.with(holder.profileImage.context)
                .load(picUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.bilal)
                .error(R.drawable.bilal)
                .into(holder.profileImage)
        } else {
            Glide.with(holder.profileImage.context)
                .load(R.drawable.bilal)
                .apply(RequestOptions.circleCropTransform())
                .into(holder.profileImage)
        }

        holder.messageButton.setOnClickListener {
            onMessageClick(user)
        }
    }

    override fun getItemCount() = users.size
}

