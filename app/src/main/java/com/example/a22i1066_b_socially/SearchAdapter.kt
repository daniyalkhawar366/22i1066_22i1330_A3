package com.example.a22i1066_b_socially

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SearchAdapter(
    private val items: MutableList<SearchUser>,
    private val onUserClick: ((SearchUser) -> Unit)? = null
) : RecyclerView.Adapter<SearchAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: ImageView? = run {
            val candidates = listOf(
                "searchProfileImage",
                "profileImage",
                "profilePic",
                "imgProfile",
                "profile_image"
            )
            for (name in candidates) {
                val id = view.resources.getIdentifier(name, "id", view.context.packageName)
                if (id != 0) {
                    val found = view.findViewById<ImageView?>(id)
                    if (found != null) return@run found
                }
            }
            null
        }

        val username: TextView? = run {
            val id = view.resources.getIdentifier("username", "id", view.context.packageName)
            if (id != 0) view.findViewById<TextView?>(id) else null
        }

        val subtext: TextView? = run {
            val id = view.resources.getIdentifier("subtext", "id", view.context.packageName)
            if (id != 0) view.findViewById<TextView?>(id) else null
        }

        val messageBtn: View? = run {
            val id = view.resources.getIdentifier("messageBtn", "id", view.context.packageName)
            if (id != 0) view.findViewById<View?>(id) else null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.username?.text = u.username

        // Show display name or bio as subtitle
        val subtitle = if (!u.displayName.isNullOrBlank() && u.displayName != u.username) {
            u.displayName
        } else {
            u.subtitle ?: ""
        }
        holder.subtext?.text = subtitle

        val ctx = holder.itemView.context

        // Load profile image
        holder.profileImage?.let { imageView ->
            val profilePicUrl = u.profilePicUrl?.trim().orEmpty()

            if (profilePicUrl.isNotBlank()) {
                try {
                    Glide.with(ctx)
                        .load(profilePicUrl)
                        .circleCrop()
                        .placeholder(R.drawable.profileicon)
                        .error(R.drawable.profileicon)
                        .into(imageView)
                } catch (ex: Exception) {
                    imageView.setImageResource(R.drawable.profileicon)
                }
            } else {
                imageView.setImageResource(R.drawable.profileicon)
            }
        }

        // Click to view profile
        holder.itemView.setOnClickListener {
            try {
                onUserClick?.invoke(u) // Save to recent searches
                val intent = Intent(ctx, ProfileActivity::class.java).apply {
                    putExtra("userId", u.id)
                }
                if (ctx !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } catch (ex: Exception) {
                Log.e("SearchAdapter", "Failed to open profile for ${u.id}", ex)
            }
        }

        // Click to message
        holder.messageBtn?.setOnClickListener {
            val sessionManager = SessionManager(ctx)
            val currentUserId = sessionManager.getUserId() ?: ""

            try {
                val intent = Intent(ctx, ChatDetailActivity::class.java).apply {
                    putExtra("receiverUserId", u.id)
                    putExtra("receiverUsername", u.username)
                    putExtra("CURRENT_USER_ID", currentUserId)
                }
                if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (ex: Exception) {
                Log.e("SearchAdapter", "Failed to open chat for ${u.id}", ex)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<SearchUser>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
