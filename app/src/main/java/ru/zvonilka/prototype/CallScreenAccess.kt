package ru.zvonilka.prototype

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings

object CallScreenAccess {
    fun issue(c:Context):String? {
        val nm=c.getSystemService(NotificationManager::class.java)
        if(!nm.areNotificationsEnabled()) return "Разрешите уведомления о звонках"
        if(Build.VERSION.SDK_INT>=34 && !nm.canUseFullScreenIntent()) return "Разрешите вызовы поверх блокировки"
        val importance=nm.getNotificationChannel("calls")?.importance
        if(importance!=null && importance<NotificationManager.IMPORTANCE_HIGH) return "Включите всплывающие входящие вызовы"
        return null
    }
    fun openSettings(c:Context) {
        val nm=c.getSystemService(NotificationManager::class.java)
        val intent=when {
            !nm.areNotificationsEnabled() -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName)
            Build.VERSION.SDK_INT>=34 && !nm.canUseFullScreenIntent() -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${c.packageName}"))
            nm.getNotificationChannel("calls")!=null -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,"calls")
            else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName)
        }
        try { c.startActivity(intent) } catch(_:ActivityNotFoundException) { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))) }
    }
    // Grant only immutable, explicit call-screen PendingIntents to SystemUI.
    fun pending(c:Context,id:Int,key:String,answer:Boolean=false):PendingIntent {
        val intent=Intent(c,CallActivity::class.java).putExtra("call_id",key).apply {
            if(answer) action="answer"
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val options=if(Build.VERSION.SDK_INT>=35) ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle() else null
        return PendingIntent.getActivity(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,options)
    }
}
