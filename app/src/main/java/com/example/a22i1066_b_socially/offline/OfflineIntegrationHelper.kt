package com.example.a22i1066_b_socially.offline

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.a22i1066_b_socially.R

/**
 * Helper class to integrate offline features with existing code
 * Uses Glide with disk caching for offline image support
 */
object OfflineIntegrationHelper {

    /**
     * Load image with offline caching support
     * Uses Glide with aggressive disk caching
     */
    fun loadImage(
        imageView: ImageView,
        url: String?,
        placeholder: Int = R.drawable.profile_pic,
        error: Int = R.drawable.profile_pic,
        circular: Boolean = false
    ) {
        if (url.isNullOrBlank()) {
            imageView.setImageResource(placeholder)
            return
        }

        try {
            val glideRequest = Glide.with(imageView.context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache original + resized
                .placeholder(placeholder)
                .error(error)

            if (circular) {
                glideRequest.circleCrop().into(imageView)
            } else {
                glideRequest.centerCrop().into(imageView)
            }
        } catch (e: Exception) {
            // Fallback to placeholder
            imageView.setImageResource(error)
        }
    }

    /**
     * Check if device is online
     */
    fun isOnline(context: Context): Boolean {
        val networkMonitor = NetworkMonitor(context)
        val isOnline = networkMonitor.isCurrentlyOnline()
        networkMonitor.unregister()
        return isOnline
    }
}

