package ru.zvonilka.prototype

object NumberTools {
    fun clean(value: String): String = buildString {
        value.forEach { if (it in '0'..'9' || it == '*' || it == '#' || (it == '+' && isEmpty())) append(it) }
    }
    fun key(value: String): String {
        val s = clean(value)
        return if (s.length == 11 && s.startsWith("8") && s.all { it.isDigit() }) "+7" + s.drop(1) else s
    }
    fun display(value: String): String {
        val s = key(value)
        return if (s.startsWith("+7") && s.length == 12 && s.drop(1).all { it.isDigit() })
            "+7 (${s.substring(2,5)}) ${s.substring(5,8)}-${s.substring(8,10)}-${s.substring(10,12)}" else s
    }
    fun t9(value: String): String = value.uppercase().mapNotNull { ch ->
        listOf("ABCАБВГ", "DEFДЕЁЖЗ", "GHIИЙКЛ", "JKLМНО", "MNOПРС", "PQRSТУФХ", "TUVЦЧШЩ", "WXYZЪЫЬЭЮЯ")
            .indexOfFirst { ch in it }.takeIf { it >= 0 }?.let { ('2'.code + it).toChar() }
    }.joinToString("")
    fun matches(name: String, numbers: List<String>, query: String): Boolean =
        query.isBlank() || name.contains(query, true) || numbers.any { key(it).contains(key(query)) && key(query).isNotEmpty() }
    fun duration(seconds: Long) = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
    fun initials(name: String) = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
}
