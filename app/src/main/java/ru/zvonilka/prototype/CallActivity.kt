package ru.zvonilka.prototype

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.view.*
import android.widget.*

class CallActivity : Activity() {
    private lateinit var root: LinearLayout
    private var selected: String? = null
    private var keypad = false
    private var toneCall: Call? = null
    private var duration: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val changed: () -> Unit = { render() }
    private val ticker = object : Runnable {
        override fun run() {
            val call = CallStore.calls[selected]
            val connected = call?.details?.connectTimeMillis ?: 0L
            duration?.text = if (connected > 0 && call?.state == Call.STATE_ACTIVE) {
                val seconds = ((System.currentTimeMillis() - connected) / 1000).coerceAtLeast(0)
                "%02d:%02d".format(seconds / 60, seconds % 60)
            } else ""
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        selected = savedInstanceState?.getString("selected") ?: intent.getStringExtra("call_id")
        keypad = savedInstanceState?.getBoolean("keypad") ?: false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(20, 20, 22))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(24, insets.systemWindowInsetTop + 24, 24, insets.systemWindowInsetBottom + 24)
                insets
            }
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selected = intent.getStringExtra("call_id") ?: selected
        render()
    }
    override fun onStart() {
        super.onStart()
        CallStore.listeners.add(changed)
        render()
        handler.post(ticker)
    }
    override fun onStop() {
        CallStore.listeners.remove(changed)
        handler.removeCallbacks(ticker)
        stopTone()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected)
        outState.putBoolean("keypad", keypad)
        super.onSaveInstanceState(outState)
    }
    private fun label(text: String, size: Float = 22f): TextView = TextView(this).apply {
        this.text = text; textSize = size; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); setPadding(0, 12, 0, 12)
        root.addView(this)
    }
    private fun button(text: String, enabled: Boolean = true, action: () -> Unit) {
        root.addView(Button(this).apply {
            this.text = text; isEnabled = enabled
            setOnClickListener { action() }
        })
    }
    private fun stopTone() { toneCall?.stopDtmfTone(); toneCall = null }
    private fun render() {
        stopTone()
        val live = CallStore.liveCalls()
        if (live.isEmpty()) { finish(); return }
        if (selected !in live) selected = live.entries.firstOrNull { it.value.state == Call.STATE_RINGING }?.key ?: live.keys.first()
        val call = live[selected] ?: return
        root.removeAllViews()
        label(CallStore.label(call), 32f)
        label(CallStore.state(call))
        duration = label("")
        if (live.size > 1) live.forEach { (id, other) ->
            if (id != selected) button("${CallStore.label(other)} · ${CallStore.state(other)}") { selected = id; render() }
        }
        if (call.state == Call.STATE_RINGING) {
            button("Принять") { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
            button("Отклонить") { call.reject(false, null) }
            return
        }
        if (call.state == Call.STATE_SELECT_PHONE_ACCOUNT) {
            @Suppress("DEPRECATION")
            val accounts = call.details.intentExtras?.getParcelableArrayList<PhoneAccountHandle>(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLES).orEmpty()
            val telecom = getSystemService(TelecomManager::class.java)
            accounts.forEachIndexed { index, account ->
                val name = telecom.getPhoneAccount(account)?.label ?: "SIM ${index + 1}"
                button(name.toString()) { call.phoneAccountSelected(account, false) }
            }
            if (accounts.isEmpty()) label("SIM-карта недоступна. Завершите вызов и проверьте настройки SIM.", 16f)
        }
        val service = CallStore.service
        val audio = service?.callAudioState
        button(if (audio?.isMuted == true) "Включить микрофон" else "Выключить микрофон", audio != null) {
            service?.setMuted(audio?.isMuted != true)
        }
        button("Звук: " + when (audio?.route) {
            CallAudioState.ROUTE_SPEAKER -> "Динамик"
            CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
            CallAudioState.ROUTE_WIRED_HEADSET -> "Гарнитура"
            else -> "Телефон"
        }, audio != null) {
            val routes = listOf(CallAudioState.ROUTE_EARPIECE to "Телефон", CallAudioState.ROUTE_SPEAKER to "Динамик",
                CallAudioState.ROUTE_WIRED_HEADSET to "Гарнитура", CallAudioState.ROUTE_BLUETOOTH to "Bluetooth")
                .filter { (route, _) -> (audio?.supportedRouteMask ?: 0) and route != 0 }
            android.app.AlertDialog.Builder(this).setTitle("Куда выводить звук")
                .setItems(routes.map { it.second }.toTypedArray()) { _, index -> service?.setAudioRoute(routes[index].first) }.show()
        }
        if (call.details.can(Call.Details.CAPABILITY_HOLD)) {
            if (call.state == Call.STATE_HOLDING) button("Продолжить разговор") { call.unhold() }
            else if (call.state == Call.STATE_ACTIVE) button("Удержание") { call.hold() }
        }
        button(if (keypad) "Скрыть клавиатуру" else "Клавиатура", call.state == Call.STATE_ACTIVE) { keypad = !keypad; render() }
        if (keypad && call.state == Call.STATE_ACTIVE) listOf("123", "456", "789", "*0#").forEach { digits ->
            val row = LinearLayout(this)
            digits.forEach { digit ->
                row.addView(Button(this).apply {
                    text = digit.toString(); textSize = 24f
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> { stopTone(); toneCall = call; call.playDtmfTone(digit); true }
                            MotionEvent.ACTION_UP -> { stopTone(); view.performClick(); true }
                            MotionEvent.ACTION_CANCEL -> { stopTone(); true }
                            else -> true
                        }
                    }
                }, LinearLayout.LayoutParams(0, (64 * resources.displayMetrics.density).toInt(), 1f))
            }
            root.addView(row)
        }
        button("Завершить вызов") { call.disconnect() }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val ringing = CallStore.calls.values.firstOrNull { it.state == Call.STATE_RINGING }
            if (ringing != null) { ringing.reject(false, null); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
