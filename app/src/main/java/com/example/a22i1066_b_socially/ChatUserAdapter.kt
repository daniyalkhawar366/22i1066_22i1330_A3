// kotlin
package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.text.DateFormat
import java.util.*

class ChatUserAdapter(
    private val users: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<ChatUserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.chatProfileImage)
        val username: TextView = itemView.findViewById(R.id.chatUsername)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        val lastTimestamp: TextView = itemView.findViewById(R.id.lastTimestamp)
        val onlineIndicator: View = itemView.findViewById(R.id.onlineIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        // Hide username view when blank to avoid placeholder text
        val name = user.username?.takeIf { it.isNotBlank() }
        if (name != null) {
            holder.username.visibility = View.VISIBLE
            holder.username.text = name
        } else {
            holder.username.visibility = View.GONE
        }

        holder.lastMessage.text = user.lastMessage ?: ""
        holder.lastTimestamp.visibility = View.GONE
        user.lastTimestamp?.let { ts ->
            if (ts > 0L) {
                val date = Date(ts)
                holder.lastTimestamp.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
                holder.lastTimestamp.visibility = View.VISIBLE
            }
        }

        holder.itemView.setOnClickListener { onUserClick(user) }

        // Safe default drawable lookup (falls back to R.drawable.bilal)
        val defaultDrawable = holder.profileImage.context.resources.getIdentifier(
            "profileicon", "drawable", holder.profileImage.context.packageName
        ).takeIf { it != 0 } ?: R.drawable.bilal

        val picUrl = user.profilePicUrl?.trim().orEmpty()
        if (picUrl.isNotBlank()) {
            Glide.with(holder.profileImage.context)
                .load(picUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(defaultDrawable)
                .error(defaultDrawable)
                .into(holder.profileImage)
        } else {
            Glide.with(holder.profileImage.context)
                .load(defaultDrawable)
                .apply(RequestOptions.circleCropTransform())
                .into(holder.profileImage)
        }

        // Show online indicator for online users
        holder.onlineIndicator.visibility = if (user.isOnline) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = users.size
}
