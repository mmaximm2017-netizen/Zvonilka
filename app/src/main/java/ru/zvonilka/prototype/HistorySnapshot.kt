package ru.zvonilka.prototype

import android.content.Context
import java.util.Base64

object HistorySnapshotCodec {
    private val encoder=Base64.getUrlEncoder().withoutPadding()
    private val decoder=Base64.getUrlDecoder()
    private fun enc(value:String)=encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun dec(value:String)=String(decoder.decode(value),Charsets.UTF_8)

    fun encode(items:List<HistoryRecord>):String = items.take(300).joinToString("\n") { item ->
        listOf(item.id.toString(),enc(item.number),item.type.toString(),item.date.toString(),item.seconds.toString(),enc(item.accountId.orEmpty())).joinToString("\t")
    }

    fun decode(text:String):List<HistoryRecord> = text.lineSequence().mapNotNull { line ->
        runCatching {
            val p=line.split('\t')
            require(p.size==6)
            HistoryRecord(p[0].toLong(),dec(p[1]),p[2].toInt(),p[3].toLong(),p[4].toLong(),dec(p[5]).ifBlank { null })
        }.getOrNull()
    }.toList()
}

object HistorySnapshot {
    private const val FILE="history_snapshot_v1"
    fun load(context:Context):List<HistoryRecord> = runCatching {
        val file=context.getFileStreamPath(FILE)
        if(!file.exists()) emptyList() else HistorySnapshotCodec.decode(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    fun save(context:Context,items:List<HistoryRecord>) {
        runCatching {
            val target=context.getFileStreamPath(FILE)
            val tmp=context.getFileStreamPath("$FILE.tmp")
            val text=HistorySnapshotCodec.encode(items)
            tmp.writeText(text,Charsets.UTF_8)
            if(!tmp.renameTo(target)) { target.writeText(text,Charsets.UTF_8);tmp.delete() }
        }.onFailure { CallDiagnostics.record(context,"history_snapshot_error",it) }
    }
}
