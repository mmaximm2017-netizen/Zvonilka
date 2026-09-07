package ru.zvonilka.prototype
import org.junit.Assert.*
import org.junit.Test
class NumberToolsTest {
    @Test fun russianNormalizationDoesNotAlterForeignOrShortNumbers() {
        assertEquals("+79991234567",NumberTools.key("8 (999) 123-45-67"))
        assertEquals("+49123",NumberTools.key("+49 123"))
        assertEquals("112",NumberTools.key("112"))
        assertEquals("*100#",NumberTools.key("*100#"))
        assertEquals("+7 (999) 123-45-67",NumberTools.display("89991234567"))
    }
    @Test fun t9WorksForRussianAndLatin() {
        assertEquals("5246",NumberTools.t9("Макс"))
        assertEquals("262",NumberTools.t9("Bob"))
        assertTrue(NumberTools.matches("Макс",listOf("89991234567"),"+7999"))
        assertFalse(NumberTools.matches("Макс",listOf("89991234567"),"Иван"))
    }
    @Test fun vcfRoundTripEscapesDelimitersAndUnicode() {
        val p=PersonRecord(1,"Анна; тест, \\дом",listOf("+79991234567","123"))
        val decoded=Vcf.decode(Vcf.encode(listOf(p))).single()
        assertEquals(p.name,decoded.name);assertEquals(p.numbers,decoded.numbers)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsIncompleteVcf() { Vcf.decode("BEGIN:VCARD\nVERSION:3.0\nTEL:123") }
}
