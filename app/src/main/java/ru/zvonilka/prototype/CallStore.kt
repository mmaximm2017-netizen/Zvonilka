package ru.zvonilka.prototype

import android.telecom.Call
import android.telecom.InCallService
import java.util.UUID

/** Telecom owns calls; only retain them for the lifetime of its bound service. */
object CallStore {
    val calls = linkedMapOf<String, Call>()
    val listeners = linkedSetOf<() -> Unit>()
    var service: InCallService? = null
    fun add(call: Call): String = UUID.randomUUID().toString().also { calls[it] = call }
    fun changed() = listeners.toList().forEach { it() }
    fun liveCalls() = calls.filterValues { it.state != Call.STATE_DISCONNECTED }
    fun label(call: Call): String = if (call.details.handlePresentation == android.telecom.TelecomManager.PRESENTATION_ALLOWED)
        call.details.handle?.schemeSpecificPart ?: "Неизвестный номер" else "Скрытый номер"
    fun state(call: Call): String = when (call.state) {
        Call.STATE_RINGING -> "Входящий вызов"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Набор номера…"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "Выберите SIM-карту"
        Call.STATE_ACTIVE -> "Разговор"
        Call.STATE_HOLDING -> "На удержании"
        Call.STATE_DISCONNECTING -> "Завершение…"
        Call.STATE_DISCONNECTED -> "Вызов завершён"
        else -> "Соединение…"
    }
}
