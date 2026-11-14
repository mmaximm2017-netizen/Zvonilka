package ru.zvonilka.prototype

import java.util.Locale

object ContactDirectory {
    data class Label(val text:String, val first:String)
    fun label(p:PersonRecord, surname:Boolean):Label {
        val words=p.name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if(words.isEmpty()) return Label(p.name,p.name)
        val field=(if(surname) p.family else p.given).trim()
        val tokens=if(field.isNotEmpty()) field.split(Regex("\\s+")) else listOf(
            if(surname && p.given.isBlank() && words.last().any(Char::isLetter)) words.last() else words.first()
        )
        val at=words.windowed(tokens.size).indexOfFirst {part->part.zip(tokens).all{(a,b)->a.equals(b,true)}}
        val first=if(at>=0) words.subList(at,at+tokens.size).joinToString(" ") else tokens.joinToString(" ")
        val rest=words.filterIndexed{index,_->at<0 || index !in at until at+tokens.size}
        return Label((listOf(first)+rest).joinToString(" "),first)
    }
    private fun phoneKey(value:String):String {
        val clean=androidFreeNormalize(value)
        return if(clean.length==11 && clean.startsWith("8") && clean.all(Char::isDigit)) "7"+clean.drop(1)
            else if(clean.startsWith("+7") && clean.length==12 && clean.drop(1).all(Char::isDigit)) clean.drop(1)
            else clean
    }
    private fun androidFreeNormalize(value:String)=value.filterNot{it.isWhitespace() || it in "()-."}
    fun group(rows:List<PersonRecord>):List<List<PersonRecord>> {
        val linked=rows.groupBy { if(it.contactId>0 && it.id>=0) "c:${it.contactId}" else "r:${it.id}" }
        val result=linked.values.groupBy { group->
            val names=group.map{it.name.trim().lowercase(Locale.ROOT)}.distinct()
            val nums=group.flatMap{it.numbers}.map(::phoneKey).filter(String::isNotEmpty).toSortedSet()
            if(names.size==1 && names.first().isNotBlank() && nums.isNotEmpty()) Triple(names.first(),nums,null)
            else Triple("",emptySet<String>(),group.first().id)
        }
        return result.values.map { groups->groups.flatten().sortedWith(compareByDescending<PersonRecord>{it.id>=0 && it.writableLocal && !it.readOnly}.thenByDescending{it.photo!=null}.thenBy{it.id}) }
    }
}
