package ru.zvonilka.prototype

import android.app.Application
import android.app.NotificationManager
import android.content.Context

object CallNotifications {
    fun clear(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Keep future missed-call and other notifications untouched.
        manager.activeNotifications.filter { it.notification.channelId in setOf("calls", "ongoing") }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}

class ZvonilkaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Process death does not deliver Service.onDestroy. Telecom will re-add
        // any real calls after binding; old PendingIntents must not survive as ghosts.
        CallNotifications.clear(this)
    }
}
