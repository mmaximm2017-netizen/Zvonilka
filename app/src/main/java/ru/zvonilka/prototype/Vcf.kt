package ru.zvonilka.prototype

object Vcf {
    private fun escape(s:String)=s.replace("\\","\\\\").replace("\n","\\n").replace(";","\\;").replace(",","\\,")
    private fun unescape(s:String):String = buildString {
        var i=0
        while(i<s.length) { if(s[i]=='\\' && i+1<s.length) { i++;append(if(s[i]=='n'||s[i]=='N') '\n' else s[i]) } else append(s[i]);i++ }
    }
    fun contactKey(p:PersonRecord)=p.name.trim().lowercase()+"\u0000"+p.numbers.map(NumberTools::key).distinct().sorted().joinToString("|")
    const val MAX_PHOTOS=12_000_000
    fun encode(people:List<PersonRecord>):String {
        require(people.size<=2000){"За один экспорт допускается до 2000 контактов"}
        require(people.sumOf{it.photo?.size ?: 0}<=MAX_PHOTOS){"Слишком много фотографий для одного VCF"}
        fun fold(line:String):String = buildString {
            var size=0
            line.codePoints().forEach { cp ->
                val part=String(Character.toChars(cp));val bytes=part.toByteArray(Charsets.UTF_8).size
                if(size+bytes>75){append("\r\n ");size=1};append(part);size+=bytes
            }
            append("\r\n")
        }
        return people.joinToString("") { p ->
            "BEGIN:VCARD\r\nVERSION:3.0\r\n"+fold("FN:${escape(p.name)}")+
            p.numbers.mapIndexed{i,n->fold("TEL;TYPE=${if(i==0) "CELL,PREF" else "CELL"}:${escape(n)}")}.joinToString("")+
            (p.photo?.let{require(it.size<=512000){"Фото слишком большое для VCF"};fold("PHOTO;ENCODING=b;TYPE=JPEG:"+java.util.Base64.getEncoder().encodeToString(it))} ?: "")+"END:VCARD\r\n"
        }
    }
    private fun quoted(value:String,charset:String):String {
        val out=java.io.ByteArrayOutputStream();var i=0
        while(i<value.length) {
            if(value[i]=='=') {require(i+2<value.length){"Повреждён quoted-printable"};out.write(value.substring(i+1,i+3).toInt(16));i+=3}
            else {out.write(value[i].code);i++}
        }
        return out.toByteArray().toString(java.nio.charset.Charset.forName(charset))
    }
    fun decode(text:String):List<PersonRecord> {
        require(text.length<=32_000_000){"VCF слишком большой"}
        val lines=mutableListOf<String>();var current:StringBuilder?=null;var qp=false;var photoLine=false
        text.removePrefix("\uFEFF").replace("\r\n","\n").lineSequence().forEach { line ->
            val previous=current
            when {
                previous!=null && qp && previous.endsWith("=") -> {previous.setLength(previous.length-1);previous.append(line.trimStart())}
                previous!=null && (line.startsWith(" ") || line.startsWith("\t")) -> previous.append(line.drop(1))
                previous!=null && photoLine && ':' !in line && line.isNotBlank() && line!="END:VCARD" -> previous.append(line)
                else -> {previous?.let{lines.add(it.toString())};current=StringBuilder(line);qp=line.substringBefore(':').contains("QUOTED-PRINTABLE",true);photoLine=line.startsWith("PHOTO",true)}
            }
        }
        current?.let{lines.add(it.toString())}
        val out=mutableListOf<PersonRecord>();var inside=false;var name="";var fallback="";var photo:ByteArray?=null
        val nums=mutableListOf<Pair<Int,String>>();var version="";var photos=0
        lines.forEach { line ->
            when(line.trim().uppercase()) {
                "BEGIN:VCARD" -> { require(!inside){"Вложенная карточка VCF"};inside=true;name="";fallback="";nums.clear();version="";photo=null }
                "END:VCARD" -> {
                    require(inside && version in listOf("2.1","3.0","4.0")){"Поддерживаются VCF 2.1, 3.0 и 4.0"}
                    if(nums.isNotEmpty()) out.add(PersonRecord(-1,name.ifBlank{fallback.ifBlank{nums.first().second}},nums.sortedBy{it.first}.map{it.second}.distinctBy(NumberTools::key),photo))
                    require(out.size<=2000){"За один импорт допускается до 2000 контактов"};inside=false
                }
                else -> if(inside && ':' in line) {
                    val head=line.substringBefore(':');val upper=head.uppercase();val raw=line.substringAfter(':')
                    val value=if("QUOTED-PRINTABLE" in upper) quoted(raw,Regex("CHARSET=([^;]+)",RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)?.trim('"') ?: "UTF-8") else raw
                    when(upper.substringBefore(';').substringAfterLast('.')) {
                        "VERSION" -> version=value.trim()
                        "FN" -> name=unescape(value)
                        "N" -> fallback=value.split(';').filter{it.isNotBlank()}.joinToString(" "){unescape(it)}
                        "TEL" -> {
                            val n=NumberTools.clean(unescape(value).replace(Regex("^tel:",RegexOption.IGNORE_CASE),""))
                            val rank=Regex("(?:^|;)PREF=(\\d+)").find(upper)?.groupValues?.get(1)?.toIntOrNull() ?: if("PREF" in upper)1 else 101
                            if(n.isNotBlank())nums.add(rank to n)
                        }
                        "PHOTO" -> {
                            val payload=when {
                                raw.startsWith("data:",true) && raw.substringBefore(',').contains(";base64",true) -> raw.substringAfter(',')
                                Regex("ENCODING=(B|BASE64)(;|$)").containsMatchIn(upper) -> raw
                                else -> null // External photo URLs are never fetched.
                            }
                            if(payload!=null) {
                                require(payload.length<=710000){"Фото VCF слишком большое"}
                                val bytes=java.util.Base64.getDecoder().decode(payload.filterNot{it.isWhitespace()})
                                require(bytes.size<=512000){"Фото VCF слишком большое"}
                                photos+=bytes.size;require(photos<=MAX_PHOTOS){"В VCF слишком много фотографий"};photo=bytes
                            }
                        }
                    }
                }
            }
        }
        require(!inside && out.isNotEmpty()){ "Нет завершённых карточек с номерами" }
        return out
    }
}
