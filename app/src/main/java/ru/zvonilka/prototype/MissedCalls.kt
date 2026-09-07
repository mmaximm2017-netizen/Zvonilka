package ru.zvonilka.prototype

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager

object MissedCalls {
    fun add(c:Context,number:String) {
        val key=NumberTools.key(number).ifBlank { "hidden" }
        val prefs=c.getSharedPreferences("missed",0)
        val count=prefs.getInt(key,0)+1;prefs.edit().putInt(key,count).apply()
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("missed","Пропущенные вызовы",NotificationManager.IMPORTANCE_DEFAULT))
        fun action(name:String)=PendingIntent.getBroadcast(c,0,Intent(c,MissedReceiver::class.java).setAction(name)
            .setData(Uri.fromParts("tel",key,null)).putExtra("number",number),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).setAction("recent"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val name=ContactCache.find(number)?.name ?: NumberTools.display(number).ifBlank { "Скрытый номер" }
        val builder=Notification.Builder(c,"missed").setSmallIcon(android.R.drawable.sym_call_missed).setContentTitle(name)
            .setContentText("Пропущенных вызовов: $count").setNumber(count).setAutoCancel(true).setContentIntent(open)
            .setDeleteIntent(action("close")).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null,"Закрыть",action("close")).build())
        if(number.isNotBlank()) builder.addAction(Notification.Action.Builder(null,"Перезвонить",action("callback")).build())
        if(Build.VERSION.SDK_INT<33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) manager.notify("missed:$key",1,builder.build())
    }
    fun clear(c:Context) {
        val m=c.getSystemService(NotificationManager::class.java)
        m.activeNotifications.filter { it.notification.channelId=="missed" }.forEach { m.cancel(it.tag,it.id) }
        c.getSharedPreferences("missed",0).edit().clear().apply()
    }
}
class MissedReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val number=i.getStringExtra("number").orEmpty();val key=NumberTools.key(number).ifBlank{"hidden"}
        c.getSystemService(NotificationManager::class.java).cancel("missed:$key",1)
        c.getSharedPreferences("missed",0).edit().remove(key).apply()
        if(i.action=="callback" && number.isNotBlank() && c.checkSelfPermission(android.Manifest.permission.CALL_PHONE)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val telecom=c.getSystemService(TelecomManager::class.java)
            if(telecom.defaultDialerPackage==c.packageName) telecom.placeCall(Uri.fromParts("tel",number,null),android.os.Bundle())
        }
    }
}
