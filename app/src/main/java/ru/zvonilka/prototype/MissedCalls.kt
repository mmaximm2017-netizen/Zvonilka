package ru.zvonilka.prototype

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager

object MissedCalls {
    private val io=java.util.concurrent.Executors.newSingleThreadExecutor()
    fun count(c:Context)=c.getSharedPreferences("missed",0).all.values.filterIsInstance<Int>().sum()
    fun add(c:Context,number:String,amount:Int=1) {
        val key=NumberTools.key(number).ifBlank { "hidden" }
        val prefs=c.getSharedPreferences("missed",0)
        val safeAmount=amount.coerceIn(1,10000)
        val count=prefs.getInt(key,0)+safeAmount; prefs.edit().putInt(key,count).apply()
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
        if(Build.VERSION.SDK_INT<33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify("missed:$key",1,builder.build())
            clearSystem(c,number,safeAmount)
        }
    }
    // ROLE_DIALER is the documented alternative to privileged MODIFY_PHONE_STATE.
    @android.annotation.SuppressLint("MissingPermission")
    fun clearSystem(c:Context,number:String="",amount:Int=1) {
        val telecom=c.getSystemService(TelecomManager::class.java)
        if(telecom.defaultDialerPackage!=c.packageName)return
        try {telecom.cancelMissedCallsNotification()} catch(e:SecurityException){CallDiagnostics.record(c,"missed_system_clear_denied",e)}
        // Automatic reconciliation must never mark unrelated missed calls as read.
        // Only rows matching the exact normalized number are touched, and at most the number of calls we own.
        if(number.isBlank()) return
        val app=c.applicationContext
        val normalized=NumberTools.key(number)
        val cutoff=System.currentTimeMillis()
        io.execute {
            if(app.getSystemService(TelecomManager::class.java).defaultDialerPackage!=app.packageName)return@execute
            if(app.checkSelfPermission(android.Manifest.permission.WRITE_CALL_LOG)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return@execute
            try {
                val ids=mutableListOf<Long>()
                app.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls._ID,CallLog.Calls.NUMBER),
                    "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.DATE}<=? AND (${CallLog.Calls.NEW}=1 OR ${CallLog.Calls.IS_READ}=0)",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(),cutoff.toString()),
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val idIndex=cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                    val numberIndex=cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val limit=amount.coerceIn(1,100)
                    while(cursor.moveToNext() && ids.size<limit) {
                        if(NumberTools.key(cursor.getString(numberIndex).orEmpty())==normalized) ids.add(cursor.getLong(idIndex))
                    }
                }
                if(ids.isNotEmpty()) {
                    val values=ContentValues().apply{put(CallLog.Calls.NEW,0);put(CallLog.Calls.IS_READ,1)}
                    ids.forEach { id -> app.contentResolver.update(CallLog.Calls.CONTENT_URI,values,"${CallLog.Calls._ID}=?",arrayOf(id.toString())) }
                }
            } catch(e:RuntimeException){CallDiagnostics.record(app,"missed_read_flags_error",e)}
        }
    }
    private fun markAllSystemRead(c:Context) {
        val app=c.applicationContext
        io.execute {
            if(app.getSystemService(TelecomManager::class.java).defaultDialerPackage!=app.packageName)return@execute
            if(app.checkSelfPermission(android.Manifest.permission.WRITE_CALL_LOG)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return@execute
            try {
                val values=ContentValues().apply{put(CallLog.Calls.NEW,0);put(CallLog.Calls.IS_READ,1)}
                app.contentResolver.update(CallLog.Calls.CONTENT_URI,values,
                    "${CallLog.Calls.TYPE}=? AND (${CallLog.Calls.NEW}=1 OR ${CallLog.Calls.IS_READ}=0)",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString()))
            } catch(e:RuntimeException){CallDiagnostics.record(app,"missed_mark_all_read_error",e)}
        }
    }
    fun clear(c:Context) {
        clearSystem(c)
        markAllSystemRead(c)
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
        clearForUserAction(c,number)
        if(i.action=="callback" && number.isNotBlank() && c.checkSelfPermission(android.Manifest.permission.CALL_PHONE)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val telecom=c.getSystemService(TelecomManager::class.java)
            if(telecom.defaultDialerPackage==c.packageName) telecom.placeCall(Uri.fromParts("tel",number,null),android.os.Bundle())
        }
    }
    private fun clearForUserAction(c:Context,number:String) {
        if(number.isBlank()) MissedCalls.clearSystem(c) else MissedCalls.clearSystem(c,number)
    }
}

/** Telecom discovers this protected receiver and delegates missed-call notifications to us.
 * The InCallService is the sole counter writer while it is alive. On cold start Telecom may restore the counter.
 */
class SystemMissedReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        if(i.action!=TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION)return
        if(c.getSystemService(TelecomManager::class.java).defaultDialerPackage!=c.packageName)return
        val count=i.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT,0)
        when(MissedCallPolicy.delegated(count,MissedCalls.count(c),CallStore.service!=null)) {
            DelegatedMissedAction.IGNORE -> return
            DelegatedMissedAction.SUPPRESS_SYSTEM -> {
                CallDiagnostics.record(c,"system_missed_suppressed")
                MissedCalls.clearSystem(c)
            }
            DelegatedMissedAction.RESTORE_OWN -> {
                CallDiagnostics.record(c,"system_missed_restored")
                val number=if(count==1)i.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty() else ""
                MissedCalls.add(c,number,count)
            }
        }
    }
}
