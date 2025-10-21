package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ShareUserAdapter(
    private val users: List<User>,
    private val onShare: (User) -> Unit
) : RecyclerView.Adapter<ShareUserAdapter.UserViewHolder>() {

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePic: ImageView = view.findViewById(R.id.profilePic)
        val username: TextView = view.findViewById(R.id.username)
        val shareBtn: ImageView = view.findViewById(R.id.shareBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_share_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        holder.username.text = user.username ?: ""

        val profileUrl = user.profilePicUrl
        if (!profileUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(profileUrl)
                .circleCrop()
                .into(holder.profilePic)
        } else {
            holder.profilePic.setImageResource(R.drawable.profile_pic)
        }

        holder.shareBtn.setOnClickListener {
            onShare(user)
        }
    }

    override fun getItemCount() = users.size
}
