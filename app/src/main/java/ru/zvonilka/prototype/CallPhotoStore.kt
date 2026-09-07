package ru.zvonilka.prototype

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

/** App-private full-size photo, invalidated if another app changes the contact thumbnail. */
object CallPhotoStore {
    private fun file(c:Context,id:Long,source:Boolean)=File(File(c.filesDir,"call-photos").apply{mkdirs()},"$id${if(source) "-source" else ""}.jpg")
    fun hash(bytes:ByteArray?)=bytes?.let{MessageDigest.getInstance("SHA-256").digest(it).joinToString(""){b->"%02x".format(b.toInt() and 255)}} ?: "none"
    fun write(c:Context,id:Long,photo:ByteArray,original:ByteArray,thumb:ByteArray?) {
        fun put(source:Boolean,bytes:ByteArray) {
            val target=AtomicFile(file(c,id,source));val out=target.startWrite()
            try { out.write(bytes);target.finishWrite(out) } catch(e:Exception){target.failWrite(out);throw e}
        }
        put(false,photo);put(true,original)
        c.getSharedPreferences("photo-stamps",0).edit().putString(id.toString(),hash(thumb)).apply()
    }
    fun read(c:Context,id:Long,thumb:ByteArray?,source:Boolean=false):ByteArray? {
        if(c.getSharedPreferences("photo-stamps",0).getString(id.toString(),null)!=hash(thumb)) return null
        return runCatching{file(c,id,source).takeIf{it.exists()}?.readBytes()}.getOrNull()
    }
    fun remove(c:Context,id:Long) {file(c,id,false).delete();file(c,id,true).delete();c.getSharedPreferences("photo-stamps",0).edit().remove(id.toString()).apply()}
}
