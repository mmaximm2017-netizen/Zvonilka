package ru.zvonilka.prototype

import android.content.Context

internal object CancelledCalls {
    private fun key(time:Long, number:String) = time.toString()+":"+NumberTools.key(number).hashCode()
    private fun prefs(c:Context)=c.getSharedPreferences("cancelled_outgoing",Context.MODE_PRIVATE)
    fun contains(c:Context,time:Long,number:String)=prefs(c).contains(key(time,number))
    fun record(c:Context,time:Long,number:String) {
        if(time<=0) return
        val p=prefs(c)
        val edit=p.edit()
        if(p.all.size>=500) p.all.keys.sortedBy { it.substringBefore(':').toLongOrNull() ?: 0L }.take(50).forEach { edit.remove(it) }
        edit.putBoolean(key(time,number),true).apply()
    }
}
