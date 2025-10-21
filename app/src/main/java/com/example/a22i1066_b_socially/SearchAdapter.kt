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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SearchAdapter(
    private val items: MutableList<SearchUser>,
    private val onUserClick: ((SearchUser) -> Unit)? = null
) : RecyclerView.Adapter<SearchAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()

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
        holder.subtext?.text = u.subtitle ?: ""
        val ctx = holder.itemView.context

        holder.profileImage?.let { imageView ->
            imageView.tag = u.id

            val provided = u.profilePicUrl?.trim().orEmpty()
            if (provided.isNotBlank()) {
                loadImageInto(provided, imageView, ctx, u.id)
            } else {
                db.collection("users").document(u.id).get()
                    .addOnSuccessListener { doc ->
                        if (imageView.tag != u.id) return@addOnSuccessListener
                        if (!doc.exists()) {
                            imageView.setImageResource(R.drawable.profileicon)
                            return@addOnSuccessListener
                        }
                        val pic = firstAvailableString(doc, listOf("profilePicUrl", "profilePic", "profile_pic", "photoUrl", "avatar"))
                        if (pic.isNotBlank()) {
                            loadImageInto(pic, imageView, ctx, u.id)
                        } else {
                            imageView.setImageResource(R.drawable.profileicon)
                        }
                    }
                    .addOnFailureListener {
                        if (imageView.tag == u.id) imageView.setImageResource(R.drawable.profileicon)
                    }
            }
        }

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

        holder.messageBtn?.setOnClickListener {
            val currentId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            try {
                val intent = Intent(ctx, ChatDetailActivity::class.java).apply {
                    putExtra("receiverUserId", u.id)
                    putExtra("receiverUsername", u.username)
                    putExtra("CURRENT_USER_ID", currentId)
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

    private fun firstAvailableString(doc: DocumentSnapshot, keys: List<String>): String {
        for (k in keys) {
            val v = doc.getString(k)
            if (!v.isNullOrBlank()) return v.trim()
        }
        val data = doc.data
        if (data != null) {
            for (k in keys) {
                val any = data[k]
                if (any is String && any.isNotBlank()) return any.trim()
            }
        }
        return ""
    }

    private fun loadImageInto(raw: String, imageView: ImageView, ctx: android.content.Context, expectedTag: String) {
        if (imageView.tag != expectedTag) return

        var pic = raw.trim()
        if (pic.contains("api.cloudinary.com/v1_1", ignoreCase = true) && pic.contains("/image/upload/", ignoreCase = true)) {
            pic = pic.replaceFirst("api.cloudinary.com/v1_1", "res.cloudinary.com", ignoreCase = true)
        }

        if (pic.startsWith("gs://", ignoreCase = true)) {
            try {
                val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(pic)
                storageRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        if (imageView.tag != expectedTag) return@addOnSuccessListener
                        Glide.with(ctx)
                            .load(uri)
                            .circleCrop()
                            .placeholder(R.drawable.profileicon)
                            .error(R.drawable.profileicon)
                            .into(imageView)
                    }
                    .addOnFailureListener {
                        if (imageView.tag == expectedTag) imageView.setImageResource(R.drawable.profileicon)
                    }
            } catch (ex: Exception) {
                if (imageView.tag == expectedTag) imageView.setImageResource(R.drawable.profileicon)
            }
            return
        }

        try {
            Glide.with(ctx)
                .load(pic)
                .circleCrop()
                .placeholder(R.drawable.profileicon)
                .error(R.drawable.profileicon)
                .into(imageView)
        } catch (ex: Exception) {
            if (imageView.tag == expectedTag) imageView.setImageResource(R.drawable.profileicon)
        }
    }
}
