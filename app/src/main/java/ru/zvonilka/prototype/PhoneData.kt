package ru.zvonilka.prototype

import android.content.*
import android.provider.CallLog
import android.provider.ContactsContract as CC
import android.net.Uri
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.text.Collator
import java.util.Locale

// A record belongs to one local RawContact, never to an aggregated cloud contact.
data class PersonRecord(val id: Long, val name: String, val numbers: List<String>, val photo: ByteArray? = null) {
    val primary get() = numbers.firstOrNull().orEmpty()
}
data class HistoryRecord(val id: Long, val number: String, val type: Int, val date: Long, val seconds: Long, val accountId: String?)

class PhoneData(private val context: Context) {
    private val cr get() = context.contentResolver
    private val local = "(${CC.RawContacts.ACCOUNT_TYPE} IS NULL OR ${CC.RawContacts.ACCOUNT_TYPE} = 'vnd.sec.contact.phone') AND ${CC.RawContacts.DELETED} = 0"
    fun allowed(permission: String) = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun contacts(): List<PersonRecord> {
        if (!allowed(android.Manifest.permission.READ_CONTACTS)) return emptyList()
        val ids = mutableListOf<Long>()
        cr.query(CC.RawContacts.CONTENT_URI, arrayOf(CC.RawContacts._ID), local, null, null)?.use { c -> while(c.moveToNext()) ids.add(c.getLong(0)) }
        val result = mutableListOf<PersonRecord>()
        ids.chunked(300).forEach { chunk ->
            data class Acc(var name: String = "", val nums: MutableList<Pair<Boolean,String>> = mutableListOf(), var photo: ByteArray? = null)
            val map = chunk.associateWith { Acc() }
            cr.query(CC.Data.CONTENT_URI, arrayOf(CC.Data.RAW_CONTACT_ID,CC.Data.MIMETYPE,CC.Data.DATA1,CC.Data.IS_SUPER_PRIMARY,CC.Data.DATA15),
                "${CC.Data.RAW_CONTACT_ID} IN (${chunk.joinToString(",") { "?" }}) AND ${CC.Data.MIMETYPE} IN (?,?,?)",
                (chunk.map { it.toString() } + listOf(CC.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,CC.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,CC.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)).toTypedArray(), null)?.use { c ->
                while(c.moveToNext()) {
                    val item=map[c.getLong(0)] ?: continue
                    when(c.getString(1)) {
                        CC.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> item.name=c.getString(2).orEmpty()
                        CC.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> item.nums.add((c.getInt(3)==1) to c.getString(2).orEmpty())
                        CC.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> item.photo=if(c.isNull(4)) null else c.getBlob(4)
                    }
                }
            }
            map.forEach { (id,a) -> result.add(PersonRecord(id,a.name.ifBlank { "Без имени" },a.nums.sortedByDescending { it.first }.map { it.second }.distinctBy(NumberTools::key),a.photo)) }
        }
        val collator=Collator.getInstance(Locale("ru"))
        return result.sortedWith { a,b -> collator.compare(a.name,b.name) }
    }
    private fun requireLocal(id: Long) {
        cr.query(CC.RawContacts.CONTENT_URI,arrayOf(CC.RawContacts._ID),"${CC.RawContacts._ID}=? AND $local",arrayOf(id.toString()),null)?.use { require(it.moveToFirst()) { "Контакт недоступен или принадлежит другому аккаунту" } }
            ?: error("Не удалось проверить контакт")
    }
    fun save(id: Long?, name: String, numbers: List<String>, photo: ByteArray?, replacePhoto: Boolean): Long {
        require(name.isNotBlank()) { "Введите имя" }
        val nums=numbers.map(NumberTools::clean).filter { it.isNotBlank() }.distinctBy(NumberTools::key)
        require(nums.isNotEmpty()) { "Добавьте номер" }
        if(id!=null) requireLocal(id)
        val ops= arrayListOf<ContentProviderOperation>()
        if(id==null) ops.add(ContentProviderOperation.newInsert(CC.RawContacts.CONTENT_URI)
            .withValue(CC.RawContacts.ACCOUNT_TYPE,null).withValue(CC.RawContacts.ACCOUNT_NAME,null).build())
        else {
            ops.add(ContentProviderOperation.newDelete(CC.Data.CONTENT_URI).withSelection("${CC.Data.RAW_CONTACT_ID}=? AND ${CC.Data.MIMETYPE} IN (?,?)",arrayOf(id.toString(),CC.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,CC.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)).build())
            if(replacePhoto) ops.add(ContentProviderOperation.newDelete(CC.Data.CONTENT_URI).withSelection("${CC.Data.RAW_CONTACT_ID}=? AND ${CC.Data.MIMETYPE}=?",arrayOf(id.toString(),CC.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)).build())
        }
        fun row(mime:String):ContentProviderOperation.Builder {
            val b=ContentProviderOperation.newInsert(CC.Data.CONTENT_URI).withValue(CC.Data.MIMETYPE,mime)
            return if(id==null) b.withValueBackReference(CC.Data.RAW_CONTACT_ID,0) else b.withValue(CC.Data.RAW_CONTACT_ID,id)
        }
        ops.add(row(CC.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(CC.CommonDataKinds.StructuredName.DISPLAY_NAME,name.trim()).build())
        nums.forEachIndexed { index,num -> ops.add(row(CC.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(CC.CommonDataKinds.Phone.NUMBER,num).withValue(CC.CommonDataKinds.Phone.TYPE,CC.CommonDataKinds.Phone.TYPE_MOBILE)
            .withValue(CC.Data.IS_PRIMARY,if(index==0) 1 else 0).withValue(CC.Data.IS_SUPER_PRIMARY,if(index==0) 1 else 0).build()) }
        if(photo!=null && (id==null || replacePhoto)) ops.add(row(CC.CommonDataKinds.Photo.CONTENT_ITEM_TYPE).withValue(CC.CommonDataKinds.Photo.PHOTO,photo).build())
        val results=cr.applyBatch(CC.AUTHORITY,ops)
        return id ?: ContentUris.parseId(requireNotNull(results[0].uri))
    }
    fun deleteContact(id:Long) {
        requireLocal(id)
        check(cr.delete(CC.RawContacts.CONTENT_URI,"${CC.RawContacts._ID}=? AND $local",arrayOf(id.toString()))==1) { "Контакт не удалён" }
    }
    fun history():List<HistoryRecord> {
        if(!allowed(android.Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out=mutableListOf<HistoryRecord>()
        cr.query(CallLog.Calls.CONTENT_URI,arrayOf(CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.TYPE,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.PHONE_ACCOUNT_ID),null,null,"${CallLog.Calls.DATE} DESC")?.use { c ->
            while(c.moveToNext()) out.add(HistoryRecord(c.getLong(0),c.getString(1).orEmpty(),c.getInt(2),c.getLong(3),c.getLong(4),c.getString(5)))
        }
        return out
    }
    fun deleteHistory(ids:Set<Long>) {
        // Atomic and explicitly limited to the records selected in the UI.
        val ops=ArrayList(ids.map { ContentProviderOperation.newDelete(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,it)).build() })
        if(ops.isNotEmpty()) cr.applyBatch(CallLog.AUTHORITY,ops)
    }
    fun photo(uri:Uri):ByteArray {
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr,uri)) { decoder, info, _ ->
            val scale=minOf(1f,1200f/maxOf(info.size.width,info.size.height))
            decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,out); bitmap.recycle();out.toByteArray() }
    }
}
