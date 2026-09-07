package ru.zvonilka.prototype

import android.app.*
import android.content.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.*

class PhoneService : InCallService(), android.hardware.SensorEventListener {
    private val seenEnded = mutableSetOf<Call>()
    private var sawFaceUp = false
    private var downSamples = 0
    private val sensors get() = getSystemService(android.hardware.SensorManager::class.java)
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    private val ids = mutableMapOf<Call, Int>()
    private var nextId = 100
    private val handler = Handler(Looper.getMainLooper())
    private val publishedStates = mutableMapOf<Call, Int>()
    // A bounded-to-service fallback for missed/delayed lifecycle callbacks.
    private val reconcile = object : Runnable {
        override fun run() {
            val telecomCalls = calls.toSet()
            CallStore.calls.toMap().forEach { (key, call) ->
                if (call !in telecomCalls || call.state == Call.STATE_DISCONNECTED) removeCall(call)
                else if (publishedStates[call] != call.state) refresh(call, key)
            }
            if (ids.isNotEmpty()) handler.postDelayed(this, 1000)
        }
    }
    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        CallDiagnostics.record(this, "service_create")
        CallStore.service = this
        io.execute { runCatching { PhoneData(this).contacts() }.onSuccess { ContactCache.people=it;handler.post { CallStore.changed() } } }
        // Telecom plays the ringtone: do not declare IN_CALL_SERVICE_RINGING.
        manager.createNotificationChannel(NotificationChannel("calls", "Входящие вызовы", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel("ongoing", "Текущий разговор", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
    }

    override fun onBind(intent: Intent): android.os.IBinder? {
        CallDiagnostics.record(this, "service_bind")
        return super.onBind(intent).also { CallDiagnostics.record(this, "binder_present=${it != null}") }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallStore.service = this
        if (call in callbacks) return
        CallDiagnostics.record(this, "call_added state=${call.state}")
        val key = CallStore.add(call)
        ids[call] = nextId++
        if(call.state==Call.STATE_RINGING && CallStore.calls.values.count{it.state==Call.STATE_RINGING}==1) {
            sawFaceUp=false;downSamples=0
            sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this,it,android.hardware.SensorManager.SENSOR_DELAY_NORMAL) }
        }
        val callback = object : Call.Callback() {
            override fun onCallDestroyed(call: Call) = removeCall(call)
            override fun onStateChanged(call: Call, state: Int) = refresh(call, key)
            override fun onDetailsChanged(call: Call, details: Call.Details) = refresh(call, key)
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = refresh(call, key)
        }
        callbacks[call] = callback
        call.registerCallback(callback, handler)
        refresh(call, key)
        handler.removeCallbacks(reconcile)
        handler.postDelayed(reconcile, 1000)
        if (call.state != Call.STATE_RINGING && call.state != Call.STATE_DISCONNECTED) showCall(key)
    }

    private fun refresh(call: Call, key: String) {
        val id = ids[call] ?: return
        if (call.state == Call.STATE_DISCONNECTED) { removeCall(call); return }
        val previousState = publishedStates.put(call, call.state)
        if(previousState != call.state) CallDiagnostics.record(this, "call_state=${call.state}")
        val ringing = call.state == Call.STATE_RINGING
        if(call.state==Call.STATE_ACTIVE) call.details.accountHandle?.let { Dialing.remember(this,CallStore.label(call),it) }
        if(CallStore.calls.values.none{it.state==Call.STATE_RINGING}) sensors.unregisterListener(this)
        // Notification errors must not abort Telecom callbacks or opening the call UI.
        try {
        if(previousState != null && (previousState == Call.STATE_RINGING) != ringing) manager.cancel(id)
        val open = PendingIntent.getActivity(this, id, Intent(this, CallActivity::class.java).apply {
            putExtra("call_id", key)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String): PendingIntent = if(name=="answer") PendingIntent.getActivity(this,id,
            Intent(this,CallActivity::class.java).setAction("answer").putExtra("call_id",key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            else PendingIntent.getBroadcast(this, id,
            Intent(this, CallActionReceiver::class.java).setAction(name).putExtra("call_id", key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, if (ringing) "calls" else "ongoing")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(ContactCache.find(CallStore.label(call))?.name ?: CallStore.label(call)).setContentText(CallStore.state(call))
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open)
        if (ringing) builder.setFullScreenIntent(open, true)
        if (Build.VERSION.SDK_INT >= 31) {
            val person = Person.Builder().setName(ContactCache.find(CallStore.label(call))?.name ?: CallStore.label(call)).setImportant(true).build()
            builder.setStyle(if (ringing) Notification.CallStyle.forIncomingCall(person, action("reject"), action("answer"))
                else Notification.CallStyle.forOngoingCall(person, action("hangup")))
        } else {
            if (ringing) builder.addAction(Notification.Action.Builder(null, "Принять", action("answer")).build())
            builder.addAction(Notification.Action.Builder(null, if (ringing) "Отклонить" else "Завершить", action(if (ringing) "reject" else "hangup")).build())
        }
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(id, builder.build())
        }
        } catch (error: RuntimeException) {
            CallDiagnostics.record(this, "notification_error", error)
        }
        // Notification cleanup/update must not depend on the activity rendering successfully.
        CallStore.changed()
    }

    private fun showCall(key: String? = null) {
        CallDiagnostics.record(this, "screen_requested")
        try {
            startActivity(Intent(this, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("call_id", key)
            })
        } catch (error: RuntimeException) {
            CallDiagnostics.record(this, "screen_launch_error", error)
        }
    }
    override fun onBringToForeground(showDialpad: Boolean) = showCall()
    override fun onCallAudioStateChanged(audioState: CallAudioState?) { CallStore.changed() }
    private fun removeCall(call: Call) {
        if(call in ids) CallDiagnostics.record(this, "call_removed state=${call.state}")
        if(call.details.disconnectCause.code==DisconnectCause.MISSED && seenEnded.add(call)) MissedCalls.add(this,call.details.handle?.schemeSpecificPart.orEmpty())
        ids.remove(call)?.let { manager.cancel(it) }
        publishedStates.remove(call)
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        CallStore.calls.entries.removeAll { it.value == call }
        if (ids.isEmpty()) handler.removeCallbacks(reconcile)
        CallStore.changed()
    }
    override fun onCallRemoved(call: Call) {
        removeCall(call)
        super.onCallRemoved(call)
    }
    private fun clearSession() {
        handler.removeCallbacks(reconcile)
        sensors.unregisterListener(this)
        seenEnded.clear()
        // Includes notifications left behind by a previous service instance.
        CallNotifications.clear(this)
        callbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callbacks.clear()
        ids.clear()
        publishedStates.clear()
        CallStore.calls.clear()
        CallStore.service = null
        CallStore.changed()
    }
    override fun onUnbind(intent: Intent?): Boolean {
        CallDiagnostics.record(this, "service_unbind")
        clearSession()
        return super.onUnbind(intent)
    }
    override fun onSensorChanged(event: android.hardware.SensorEvent) {
        if(CallStore.calls.values.none{it.state==Call.STATE_RINGING}) { sensors.unregisterListener(this);return }
        val z=event.values[2]
        if(z>3f) sawFaceUp=true
        downSamples=if(sawFaceUp && z < -7f) downSamples+1 else 0
        if(downSamples>=3) {
            silenceForDialerRole()
            sensors.unregisterListener(this)
        }
    }
    // Android documents ROLE_DIALER as an alternative to privileged MODIFY_PHONE_STATE.
    // Lint models only the privileged permission; enforce the documented role at runtime.
    @android.annotation.SuppressLint("MissingPermission")
    private fun silenceForDialerRole() {
        val telecom=getSystemService(TelecomManager::class.java)
        if(telecom.defaultDialerPackage!=packageName) return
        try { telecom.silenceRinger() } catch (_:SecurityException) {
            android.util.Log.w("Zvonilka", "Ringer control unavailable after role change")
        }
    }
    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    override fun onDestroy() {
        CallDiagnostics.record(this, "service_destroy")
        clearSession()
        io.shutdown()
        super.onDestroy()
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallStore.calls[intent.getStringExtra("call_id")] ?: return
        when (intent.action) {
            "answer" -> if (call.state == Call.STATE_RINGING) call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            "reject" -> if (call.state == Call.STATE_RINGING) call.reject(false, null)
            "hangup" -> if (call.state != Call.STATE_DISCONNECTED) call.disconnect()
        }
    }
}
