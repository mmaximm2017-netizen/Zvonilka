package ru.zvonilka.prototype

import android.content.Context

/** Bounded local technical events. Never store numbers, contact names or exception messages. */
object CallDiagnostics {
    @Synchronized fun record(c: Context, event: String, error: Throwable? = null) {
        val details = error?.let { e ->
            generateSequence(e) { it.cause }.take(4).joinToString("\n") { cause ->
                cause.javaClass.name + "\n" + cause.stackTrace.take(8).joinToString("\n") { it.toString() }
            }
        }.orEmpty()
        val line = "${java.time.LocalTime.now().withNano(0)} $event" + if(details.isEmpty()) "" else "\n$details"
        runCatching {
            val prefs=c.getSharedPreferences("call_diagnostics", Context.MODE_PRIVATE)
            val old=prefs.getString("events", "").orEmpty()
            prefs.edit().putString("events", (old+"\n"+line).takeLast(14000)).commit()
        }
    }
    fun report(c: Context): String = buildString {
        appendLine("Звонилка 0.3.1 · Android ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MODEL}")
        appendLine("Роль: " + c.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_DIALER))
        appendLine("Telecom default: " + c.getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage)
        appendLine("Служба в процессе: ${CallStore.service != null}; вызовов: ${CallStore.liveCalls().size}")
        val manager=c.getSystemService(android.app.NotificationManager::class.java)
        appendLine("Уведомления: ${manager.areNotificationsEnabled()}")
        if(android.os.Build.VERSION.SDK_INT>=34) appendLine("Полный экран: ${manager.canUseFullScreenIntent()}")
        if(android.os.Build.VERSION.SDK_INT>=30) runCatching {
            c.getSystemService(android.app.ActivityManager::class.java).getHistoricalProcessExitReasons(null,0,3).forEach {
                appendLine("Завершение процесса: reason=${it.reason}, status=${it.status}, time=${it.timestamp}")
            }
        }
        append(c.getSharedPreferences("call_diagnostics",Context.MODE_PRIVATE).getString("events", "Событий пока нет"))
    }
}
