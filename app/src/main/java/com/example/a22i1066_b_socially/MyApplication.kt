// kotlin
package com.example.a22i1066_b_socially

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class MyApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var started = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    private fun setOnline(online: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("status").child(uid)
        val now = mapOf("lastChanged" to ServerValue.TIMESTAMP)
        if (online) {
            // ensure onDisconnect will mark offline if connection drops
            ref.onDisconnect().setValue(mapOf("online" to false, "lastChanged" to ServerValue.TIMESTAMP))
            ref.setValue(mapOf("online" to true) + now)
        } else {
            ref.setValue(mapOf("online" to false) + now)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (++started == 1) setOnline(true)
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started == 0) setOnline(false)
    }

    // unused lifecycle callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
