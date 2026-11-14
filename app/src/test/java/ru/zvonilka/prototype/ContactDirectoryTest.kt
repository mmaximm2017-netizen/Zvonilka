package ru.zvonilka.prototype
import org.junit.Assert.assertEquals
import org.junit.Test
class ContactDirectoryTest {
    @Test fun displayGroupsKeepOriginalsAndSeparateNamesakes(){
        val a=PersonRecord(1,"Мария",listOf("+7 (999) 123-45-67"),contactId=10)
        val linked=a.copy(id=2,numbers=listOf("222"))
        val copy=a.copy(id=3,contactId=20,numbers=listOf("222","8 999 123 45 67"))
        val namesake=a.copy(id=4,contactId=30,numbers=listOf("333"))
        assertEquals(listOf(listOf(1L,2L,3L),listOf(4L)),ContactDirectory.group(listOf(a,linked,copy,namesake)).map{g->g.map{it.id}})
    }
}
