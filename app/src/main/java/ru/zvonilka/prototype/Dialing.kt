package ru.zvonilka.prototype

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

object ContactCache {
    @Volatile var people: List<PersonRecord> = emptyList()
    fun find(number:String) = people.firstOrNull { p -> p.numbers.any { NumberTools.key(it)==NumberTools.key(number) } }
}
object Dialing {
    private fun prefs(c:Context)=c.getSharedPreferences("settings",Context.MODE_PRIVATE)
    fun accountKey(a:PhoneAccountHandle)=a.componentName.flattenToString()+"/"+a.id
    fun accounts(c:Context):List<PhoneAccountHandle> = if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        c.getSystemService(TelecomManager::class.java).callCapablePhoneAccounts else emptyList()
    fun label(c:Context,a:PhoneAccountHandle):String {
        val fallback=c.getSystemService(TelecomManager::class.java).getPhoneAccount(a)?.label?.toString() ?: "SIM"
        if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return fallback
        return try {
            val tm=c.getSystemService(android.telephony.TelephonyManager::class.java).createForPhoneAccountHandle(a) ?: return fallback
            val operator=tm.simOperatorName.orEmpty().trim()
            val slot=if(android.os.Build.VERSION.SDK_INT>=30) c.getSystemService(android.telephony.SubscriptionManager::class.java).getActiveSubscriptionInfo(tm.subscriptionId)?.simSlotIndex else null
            val sim=slot?.takeIf{it>=0}?.let{"SIM ${it+1}"} ?: fallback
            if(operator.isBlank() || sim.contains(operator,true)) sim else "$sim · $operator"
        } catch(_:SecurityException) { fallback } catch(_:UnsupportedOperationException) { fallback }
    }
    private fun personKey(number:String) = ContactCache.find(number)?.id?.let { "contact_$it" } ?: "number_${NumberTools.key(number)}"
    fun preferred(c:Context,number:String):String? = prefs(c).getString("sim_${personKey(number)}",null) ?: prefs(c).getString("default_sim",null)
    fun selectedLabel(c:Context,number:String):String {
        val list=accounts(c);val wanted=preferred(c,number)
        val selected=list.firstOrNull{accountKey(it)==wanted}
        if(wanted!=null && selected==null) return "SIM недоступна · выбрать"
        return (selected ?: list.firstOrNull())?.let{label(c,it)} ?: "Системная SIM"
    }
    fun remember(c:Context,number:String,a:PhoneAccountHandle) { prefs(c).edit().putString("sim_${personKey(number)}",accountKey(a)).apply() }
    fun choose(activity:Activity,number:String?,done:()->Unit) {
        val list=accounts(activity)
        if(list.isEmpty()) { android.widget.Toast.makeText(activity,"Нет доступных SIM. Проверьте разрешение «Телефон».",android.widget.Toast.LENGTH_LONG).show();return }
        val perContact=number!=null
        val saved=if(perContact) prefs(activity).getString("sim_${personKey(number!!)}",null) else prefs(activity).getString("default_sim",null)
        val labels=(if(perContact) listOf("Как в настройках по умолчанию") else emptyList())+list.map{label(activity,it)}
        val checked=if(perContact && saved==null) 0 else list.indexOfFirst{accountKey(it)==saved}.let{if(it<0) -1 else it+if(perContact) 1 else 0}
        AlertDialog.Builder(activity).setTitle(if(!perContact) "SIM по умолчанию" else "SIM для этого контакта")
            .setSingleChoiceItems(labels.toTypedArray(),checked) { dialog,i ->
                if(perContact && i==0) prefs(activity).edit().remove("sim_${personKey(number!!)}").apply()
                else {
                    val account=list[i-if(perContact) 1 else 0]
                    if(number==null) prefs(activity).edit().putString("default_sim",accountKey(account)).apply()
                    else remember(activity,number,account)
                }
                dialog.dismiss();done()
            }.setNegativeButton("Отмена",null).show()
    }
    fun place(activity:Activity,number:String,onSetup:()->Unit) {
        if(number.isBlank()) return
        val telecom=activity.getSystemService(TelecomManager::class.java)
        if(telecom.defaultDialerPackage!=activity.packageName || activity.checkSelfPermission(Manifest.permission.CALL_PHONE)!=android.content.pm.PackageManager.PERMISSION_GRANTED) { onSetup(); return }
        val list=accounts(activity)
        val wanted=preferred(activity,number)
        val selected=list.firstOrNull { accountKey(it)==wanted }
        fun call(account:PhoneAccountHandle?) {
            try {
                val extras=Bundle()
                if(account!=null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,account)
                CallDiagnostics.record(activity, "outgoing_requested")
                telecom.placeCall(Uri.fromParts("tel",NumberTools.clean(number),null),extras)
            } catch (error:RuntimeException) { CallDiagnostics.record(activity,"outgoing_error",error);android.widget.Toast.makeText(activity,"Не удалось начать вызов. Проверьте SIM и разрешения.",android.widget.Toast.LENGTH_LONG).show() }
        }
        if(wanted!=null && selected==null && list.isNotEmpty()) AlertDialog.Builder(activity)
            .setTitle("Выбранная SIM недоступна").setMessage("Позвонить через ${label(activity,list.first())}?")
            .setPositiveButton("Позвонить") { _,_->call(list.first()) }.setNegativeButton("Отмена",null).show()
        else call(selected ?: list.firstOrNull())
    }
}
