package com.example.a22i1066_b_socially.offline

import android.content.Context
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.a22i1066_b_socially.R
import kotlinx.coroutines.launch

/**
 * Extension functions for easy offline support integration
 */

// ImageView extension for loading images with offline support
fun ImageView.loadImageOffline(
    url: String?,
    placeholder: Int = R.drawable.ic_launcher_foreground,
    error: Int = R.drawable.ic_launcher_foreground,
    circular: Boolean = false
) {
    PicassoImageLoader.loadImage(this, url, placeholder, error, circular)
}

// ImageView extension for loading local images
fun ImageView.loadLocalImage(
    filePath: String,
    placeholder: Int = R.drawable.ic_launcher_foreground,
    error: Int = R.drawable.ic_launcher_foreground,
    circular: Boolean = false
) {
    PicassoImageLoader.loadLocalImage(this, filePath, placeholder, error, circular)
}

// Context extension to check if online
fun Context.isOnline(): Boolean {
    val networkMonitor = NetworkMonitor(this)
    val isOnline = networkMonitor.isCurrentlyOnline()
    networkMonitor.unregister()
    return isOnline
}

// Context extension to get offline manager
fun Context.getOfflineManager(): OfflineManager {
    return OfflineManager(this)
}

// Show offline toast
fun Context.showOfflineToast() {
    Toast.makeText(this, "You are offline. Action will be synced when online.", Toast.LENGTH_SHORT).show()
}

// Show online toast
fun Context.showOnlineToast() {
    Toast.makeText(this, "Back online! Syncing data...", Toast.LENGTH_SHORT).show()
}

// LifecycleOwner extension for queueing actions
fun LifecycleOwner.queueOfflineAction(
    context: Context,
    actionType: String,
    actionData: Map<String, Any>,
    onQueued: (() -> Unit)? = null
) {
    lifecycleScope.launch {
        val offlineManager = context.getOfflineManager()
        val actionId = offlineManager.queueAction(actionType, actionData)

        if (actionId > 0) {
            context.showOfflineToast()
            onQueued?.invoke()
        } else {
            Toast.makeText(context, "Failed to queue action", Toast.LENGTH_SHORT).show()
        }
    }
}

// Check if device is online and execute action
inline fun Context.executeOnlineAction(
    crossinline onlineAction: suspend () -> Unit,
    crossinline offlineAction: suspend () -> Unit = {}
) {
    if (isOnline()) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            onlineAction()
        }
    } else {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            offlineAction()
            showOfflineToast()
        }
    }
}

