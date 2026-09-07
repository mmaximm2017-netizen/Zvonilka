package ru.zvonilka.prototype

object Vcf {
    private fun escape(s:String)=s.replace("\\","\\\\").replace("\n","\\n").replace(";","\\;").replace(",","\\,")
    private fun unescape(s:String):String = buildString {
        var i=0
        while(i<s.length) { if(s[i]=='\\' && i+1<s.length) { i++;append(if(s[i]=='n'||s[i]=='N') '\n' else s[i]) } else append(s[i]);i++ }
    }
    fun encode(people:List<PersonRecord>):String = people.joinToString("") { p->
        "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:${escape(p.name)}\r\n"+p.numbers.mapIndexed { i,n->"TEL;TYPE=${if(i==0) "CELL,PREF" else "CELL"}:${escape(n)}\r\n" }.joinToString("")+"END:VCARD\r\n"
    }
    fun decode(text:String):List<PersonRecord> {
        val unfolded=text.replace("\r\n","\n").replace(Regex("\n[ \\t]"),"")
        val out=mutableListOf<PersonRecord>();var inside=false;var name="";val nums=mutableListOf<String>();var version=""
        unfolded.lineSequence().forEach { line->
            when(line.trim().uppercase()) {
                "BEGIN:VCARD" -> { require(!inside){"Вложенная карточка VCF"};inside=true;name="";nums.clear();version="" }
                "END:VCARD" -> {
                    require(inside && version in listOf("3.0","4.0")){"Поддерживаются VCF 3.0 и 4.0 в UTF-8"}
                    if(nums.isNotEmpty()) out.add(PersonRecord(-1,name.ifBlank{nums.first()},nums.toList()))
                    require(out.size<=2000){"За один импорт допускается до 2000 контактов"};inside=false
                }
                else -> if(inside && ':' in line) {
                    val head=line.substringBefore(':').uppercase();val value=line.substringAfter(':')
                    require(!head.contains("QUOTED-PRINTABLE")){"VCF с quoted-printable пока не поддерживается"}
                    when(head.substringBefore(';').substringAfterLast('.')) {
                        "VERSION" -> version=value
                        "FN" -> name=unescape(value)
                        "TEL" -> { val n=NumberTools.clean(unescape(value).removePrefix("tel:"));if(n.isNotBlank()) { if("PREF" in head) nums.add(0,n) else nums.add(n) } }
                    }
                }
            }
        }
        require(!inside && out.isNotEmpty()){ "Нет завершённых карточек с номерами" }
        return out
    }
}
