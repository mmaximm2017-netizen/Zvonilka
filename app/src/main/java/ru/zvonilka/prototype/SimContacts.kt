package ru.zvonilka.prototype

import android.content.*
import android.os.Build
import android.provider.SimPhonebookContract.ElementaryFiles as EF
import android.provider.SimPhonebookContract.SimRecords as SR

// Slot and record snapshots are rechecked before every explicit user mutation.
data class SimBook(val subscription:Int,val slot:Int,val maxName:Int,val maxNumber:Int) { val label get()="SIM ${slot+1}" }
data class SimSource(val book:SimBook,val record:Int,val name:String,val number:String) {
    val id get()= -2L - ((book.subscription.toLong() shl 32) or record.toLong())
}
object SimMerge {
    fun merge(local:List<PersonRecord>,sim:List<SimSource>):List<PersonRecord> {
        val out=local.toMutableList()
        sim.forEach { source ->
            val number=NumberTools.key(source.number)
            val i=out.indexOfFirst{p->number.isNotBlank() && p.numbers.any{NumberTools.key(it)==number}}
            if(i>=0) out[i]=out[i].copy(simSources=out[i].simSources+source)
            else out.add(PersonRecord(source.id,source.name.ifBlank{source.number},listOf(source.number),simSources=listOf(source)))
        }
        return out
    }
}
class SimContacts(private val c:Context) {
    private val cr get()=c.contentResolver
    var status="";private set
    fun books():List<SimBook> {
        if(Build.VERSION.SDK_INT<31) {status="SIM-книга требует Android 12 или новее";return emptyList()}
        return try {
            val out=mutableListOf<SimBook>()
            cr.query(EF.CONTENT_URI,arrayOf(EF.SUBSCRIPTION_ID,EF.SLOT_INDEX,EF.EF_TYPE,EF.NAME_MAX_LENGTH,EF.PHONE_NUMBER_MAX_LENGTH),null,null,null)?.use { cursor->
                while(cursor.moveToNext()) if(cursor.getInt(2)==EF.EF_ADN) out.add(SimBook(cursor.getInt(0),cursor.getInt(1),cursor.getInt(3),cursor.getInt(4)))
            } ?: error("SIM-книга не предоставлена устройством")
            status=if(out.isEmpty()) "Устройство не предоставило SIM-книгу" else "Доступно SIM-книг: ${out.size}"
            out
        } catch(_:Exception) {status="SIM-книга недоступна: проверьте разрешения или поддержку устройства";emptyList()}
    }
    fun read():List<SimSource> {
        if(Build.VERSION.SDK_INT<31)return emptyList()
        val out=mutableListOf<SimSource>()
        books().forEach { book ->
            try {
                cr.query(SR.getContentUri(book.subscription,EF.EF_ADN),arrayOf(SR.RECORD_NUMBER,SR.NAME,SR.PHONE_NUMBER),null,null,null)?.use { cursor->
                    while(cursor.moveToNext()) { val number=cursor.getString(2).orEmpty();if(number.isNotBlank())out.add(SimSource(book,cursor.getInt(0),cursor.getString(1).orEmpty(),number)) }
                } ?: error("Нет доступа к SIM")
            } catch(_:Exception) {status="Не удалось прочитать ${book.label}; локальные контакты доступны"}
        }
        return out
    }
    private fun requireCurrent(source:SimSource) {
        if(Build.VERSION.SDK_INT<31)error("SIM-книга требует Android 12")
        check(books().any{it.subscription==source.book.subscription && it.slot==source.book.slot}) {"SIM изменилась. Откройте контакт заново"}
        cr.query(SR.getItemUri(source.book.subscription,EF.EF_ADN,source.record),arrayOf(SR.NAME,SR.PHONE_NUMBER),null,null,null)?.use {
            check(it.moveToFirst() && it.getString(0).orEmpty()==source.name && it.getString(1).orEmpty()==source.number) {"Запись SIM изменилась. Откройте контакт заново"}
        } ?: error("Не удалось проверить запись SIM")
    }
    fun save(book:SimBook,source:SimSource?,name:String,number:String) {
        if(Build.VERSION.SDK_INT<31)error("SIM-книга требует Android 12")
        check(c.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {"Нет разрешения на изменение контактов"}
        val current=books().firstOrNull{it.subscription==book.subscription && it.slot==book.slot} ?: error("SIM недоступна")
        if(source!=null) requireCurrent(source)
        require(name.isNotBlank() && number.isNotBlank()) {"Введите имя и номер"}
        val size=SR.getEncodedNameLength(cr,name)
        require(size>=0 && size<=current.maxName) {"Имя слишком длинное или содержит неподдерживаемые SIM символы"}
        require(number.length<=current.maxNumber && number.all{android.telephony.PhoneNumberUtils.isDialable(it)}) {"Номер не соответствует ограничениям SIM"}
        val values=ContentValues().apply{put(SR.NAME,name);put(SR.PHONE_NUMBER,number)}
        try {
            if(source==null) check(cr.insert(SR.getContentUri(book.subscription,EF.EF_ADN),values)!=null) {"SIM не сохранила контакт"}
            else check(cr.update(SR.getItemUri(book.subscription,EF.EF_ADN,source.record),values,null,null)==1) {"SIM не сохранила изменения"}
        } catch(_:SecurityException) {error("SIM-карта не разрешает редактирование этого контакта")}
          catch(_:UnsupportedOperationException) {error("SIM-карта не разрешает редактирование этого контакта")}
    }
    fun delete(source:SimSource) {
        if(Build.VERSION.SDK_INT<31)error("SIM-книга требует Android 12")
        requireCurrent(source)
        try {check(cr.delete(SR.getItemUri(source.book.subscription,EF.EF_ADN,source.record),null,null)==1) {"SIM не удалила запись"}}
        catch(_:SecurityException) {error("SIM-карта не разрешает удаление этого контакта")}
        catch(_:UnsupportedOperationException) {error("SIM-карта не разрешает удаление этого контакта")}
    }
}
