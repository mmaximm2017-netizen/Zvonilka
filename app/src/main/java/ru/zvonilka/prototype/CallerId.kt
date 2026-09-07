package ru.zvonilka.prototype

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CallerId {
    // Results are keyed by call UUID, never recycled between calls.
    val results=mutableMapOf<String,String>()
    private data class Cached(val expires:Long,val label:String)
    private val cache=linkedMapOf<String,Cached>()
    private var cooldownUntil=0L
    fun enabled(c:Context)=c.getSharedPreferences("settings",0).getBoolean("caller_id",false)
    fun setEnabled(c:Context,value:Boolean) {
        c.getSharedPreferences("settings",0).edit().putBoolean("caller_id",value).apply()
        if(!value) { results.clear();synchronized(cache){cache.clear()} }
        CallStore.changed()
    }
    fun text(c:Context,key:String?)=if(enabled(c)) results[key] else null
    private fun canonical(number:String)=NumberTools.key(number).let { if(it.startsWith("00")) "+"+it.drop(2) else it }
    // Only call on the service IO executor, after excluding local contacts.
    fun lookup(c:Context,rawNumber:String):String? {
        if(!enabled(c)) return null
        val number=canonical(rawNumber)
        if(!Regex("\\+[1-9][0-9]{6,14}").matches(number)) return "Недостаточно данных для проверки"
        val now=android.os.SystemClock.elapsedRealtime()
        synchronized(cache) { cache[number]?.takeIf{it.expires>now}?.let{return it.label} }
        if(now<cooldownUntil) return "PhoneBlock временно недоступен"
        var connection:HttpURLConnection?=null
        return try {
            connection=URL("https://phoneblock.net/phoneblock/api/num/"+number+"?format=json").openConnection() as HttpURLConnection
            connection.connectTimeout=2500;connection.readTimeout=2500
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("User-Agent","Zvonilka/${BuildConfig.VERSION_NAME} (Android)")
            when(val status=connection.responseCode) {
                200 -> {
                    val bytes=connection.inputStream.use { it.readBytesBounded(32768) }
                    val json=JSONObject(bytes.toString(Charsets.UTF_8))
                    val matches=canonical(json.optString("phone"))==number
                    val label=if(matches) SpamVerdict.label(json.optString("rating"),json.optInt("votes"),json.optBoolean("archived"),json.optBoolean("whiteListed")) else null
                    val result=if(label!=null) "$label · PhoneBlock" else "PhoneBlock: нет подтверждённых данных"
                    if(enabled(c)) synchronized(cache) {
                        if(cache.size>=100) cache.remove(cache.keys.first())
                        cache[number]=Cached(now+6*60*60*1000L,result)
                    }
                    result
                }
                429 -> { cooldownUntil=now+60*60*1000L;"PhoneBlock: лимит запросов" }
                401,403 -> "PhoneBlock: доступ к базе недоступен"
                else -> "PhoneBlock временно недоступен ($status)"
            }
        } catch(_:Exception) { "Не удалось проверить номер" }
        finally { connection?.disconnect() }
    }
    private fun java.io.InputStream.readBytesBounded(max:Int):ByteArray {
        val output=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(4096)
        while(true) { val n=read(buffer);if(n<0)break;require(output.size()+n<=max);output.write(buffer,0,n) }
        return output.toByteArray()
    }
}
