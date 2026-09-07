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
    fun label(c:Context,a:PhoneAccountHandle):String = c.getSystemService(TelecomManager::class.java).getPhoneAccount(a)?.label?.toString() ?: "SIM"
    private fun personKey(number:String) = ContactCache.find(number)?.id?.let { "contact_$it" } ?: "number_${NumberTools.key(number)}"
    fun preferred(c:Context,number:String):String? = prefs(c).getString("sim_${personKey(number)}",null) ?: prefs(c).getString("default_sim",null)
    fun selectedLabel(c:Context,number:String):String = accounts(c).let { list -> list.firstOrNull { accountKey(it)==preferred(c,number) } ?: list.firstOrNull() }?.let { label(c,it) } ?: "Системная SIM"
    fun remember(c:Context,number:String,a:PhoneAccountHandle) { prefs(c).edit().putString("sim_${personKey(number)}",accountKey(a)).apply() }
    fun choose(activity:Activity,number:String?,done:()->Unit) {
        val list=accounts(activity)
        if(list.isEmpty()) { android.widget.Toast.makeText(activity,"Нет доступных SIM. Проверьте разрешение «Телефон».",1).show();return }
        AlertDialog.Builder(activity).setTitle(if(number==null) "SIM по умолчанию" else "SIM для этого контакта")
            .setItems(list.map { label(activity,it) }.toTypedArray()) { _,i ->
                if(number==null) prefs(activity).edit().putString("default_sim",accountKey(list[i])).apply()
                else remember(activity,number,list[i])
                done()
            }.show()
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
                telecom.placeCall(Uri.fromParts("tel",NumberTools.clean(number),null),extras)
            } catch (_:RuntimeException) { android.widget.Toast.makeText(activity,"Не удалось начать вызов. Проверьте SIM и разрешения.",1).show() }
        }
        if(wanted!=null && selected==null && list.isNotEmpty()) AlertDialog.Builder(activity)
            .setTitle("Выбранная SIM недоступна").setMessage("Позвонить через ${label(activity,list.first())}?")
            .setPositiveButton("Позвонить") { _,_->call(list.first()) }.setNegativeButton("Отмена",null).show()
        else call(selected ?: list.firstOrNull())
    }
}
