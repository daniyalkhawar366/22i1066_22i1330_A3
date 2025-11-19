package com.example.a22i1066_b_socially

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: List<Message>,
    private val currentUserId: String,
    private val onEdit: (Message) -> Unit,
    private val onDelete: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount() = messages.size

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val imagesContainer: LinearLayout = itemView.findViewById(R.id.imagesContainer)

        fun bind(message: Message) {
            // Handle text
            if (message.text.isNotBlank()) {
                messageText.visibility = View.VISIBLE
                messageText.text = message.text
            } else {
                messageText.visibility = View.GONE
            }

            // Handle images
            imagesContainer.removeAllViews()
            if (message.imageUrls.isNotEmpty()) {
                imagesContainer.visibility = View.VISIBLE
                android.util.Log.d("MessageAdapter", "Loading ${message.imageUrls.size} images (sent)")
                for (imageUrl in message.imageUrls) {
                    android.util.Log.d("MessageAdapter", "Loading image: $imageUrl")
                    val imageView = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            400
                        ).apply {
                            bottomMargin = 8
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                    }
                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .centerCrop()
                        .into(imageView)
                    imagesContainer.addView(imageView)
                }
            } else {
                imagesContainer.visibility = View.GONE
            }

            // Format timestamp
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Long press for edit/delete
            itemView.setOnLongClickListener {
                val now = System.currentTimeMillis()
                val canEditOrDelete = (now - message.timestamp) <= 5 * 60 * 1000
                showMessageOptions(message, canEditOrDelete)
                true
            }
        }

        private fun showMessageOptions(message: Message, canEditOrDelete: Boolean) {
            val options = if (message.imageUrls.isNotEmpty()) {
                if (canEditOrDelete) arrayOf("Delete") else emptyArray()
            } else {
                if (canEditOrDelete) arrayOf("Edit", "Delete") else emptyArray()
            }
            if (options.isEmpty()) return
            androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                .setTitle("Message Options")
                .setItems(options) { _, which ->
                    when (options[which]) {
                        "Edit" -> onEdit(message)
                        "Delete" -> onDelete(message)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val imagesContainer: LinearLayout = itemView.findViewById(R.id.imagesContainer)

        fun bind(message: Message) {
            // Handle text
            if (message.text.isNotBlank()) {
                messageText.visibility = View.VISIBLE
                messageText.text = message.text
            } else {
                messageText.visibility = View.GONE
            }


            // Handle images
            imagesContainer.removeAllViews()
            if (message.imageUrls.isNotEmpty()) {
                imagesContainer.visibility = View.VISIBLE
                android.util.Log.d("MessageAdapter", "Loading ${message.imageUrls.size} images (received)")
                for (imageUrl in message.imageUrls) {
                    android.util.Log.d("MessageAdapter", "Loading image: $imageUrl")
                    val imageView = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            400
                        ).apply {
                            bottomMargin = 8
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                    }
                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .centerCrop()
                        .into(imageView)
                    imagesContainer.addView(imageView)
                }
            } else {
                imagesContainer.visibility = View.GONE
            }

            // Format timestamp
        }
    }
}
