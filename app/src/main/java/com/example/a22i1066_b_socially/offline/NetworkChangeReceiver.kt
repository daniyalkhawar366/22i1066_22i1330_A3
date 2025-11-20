package com.example.a22i1066_b_socially.offline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Broadcast receiver to detect network connectivity changes
 * Triggers sync when device comes online
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val isOnline = isNetworkAvailable(context)
        Log.d("NetworkChangeReceiver", "Network state changed: online=$isOnline")

        if (isOnline) {
            Log.d("NetworkChangeReceiver", "Device is online - triggering immediate sync")
            SyncWorker.scheduleImmediateSync(context)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

