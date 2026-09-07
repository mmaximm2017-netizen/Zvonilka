package ru.zvonilka.prototype

import org.junit.Assert.*
import org.junit.Test

class DataTransferTest {
    @Test fun foldedPhotosAndLongUnicodeNamesRoundTrip() {
        val p=PersonRecord(1,"Александр ".repeat(30),listOf("+79991234567","123"),ByteArray(10000){(it%251).toByte()})
        val encoded=Vcf.encode(listOf(p))
        assertTrue(encoded.split("\r\n").all{it.toByteArray(Charsets.UTF_8).size<=75})
        val back=Vcf.decode(encoded).single()
        assertEquals(p.name,back.name);assertEquals(p.numbers,back.numbers);assertArrayEquals(p.photo,back.photo)
    }
    @Test fun legacyQuotedPrintableAndNumericPreference() {
        val text="BEGIN:VCARD\nVERSION:2.1\nFN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=D0=90=\n=D0=BD=D0=BD=D0=B0\nTEL:123\nEND:VCARD"
        assertEquals("Анна",Vcf.decode(text).single().name)
        val v4="BEGIN:VCARD\nVERSION:4.0\nFN:Тест\nTEL;PREF=50:tel:123\nTEL;PREF=1:tel:456\nEND:VCARD"
        assertEquals(listOf("456","123"),Vcf.decode(v4).single().numbers)
    }
    @Test fun remotePhotoIsNotLoadedAndDataPhotoIsDecoded() {
        val base="BEGIN:VCARD\nVERSION:4.0\nFN:Тест\nTEL:123\n"
        assertNull(Vcf.decode(base+"PHOTO:https://example.invalid/private.jpg\nEND:VCARD").single().photo)
        assertArrayEquals(byteArrayOf(1,2,3),Vcf.decode(base+"PHOTO:data:image/jpeg;base64,AQID\nEND:VCARD").single().photo)
    }
    @Test fun simMergeRetainsLocalIdentityAndSourceRecords() {
        val a=SimBook(1,0,20,20);val b=SimBook(2,1,20,20)
        val local=PersonRecord(44,"Локальное имя",listOf("89991234567"),byteArrayOf(1))
        val sources=listOf(SimSource(a,1,"Другое имя","+79991234567"),SimSource(b,1,"Ещё имя","+79991234567"),SimSource(a,2,"SIM only","123"))
        val merged=SimMerge.merge(listOf(local),sources)
        assertEquals(2,merged.size);assertEquals(44L,merged[0].id);assertEquals(local.name,merged[0].name)
        assertArrayEquals(local.photo,merged[0].photo);assertEquals(sources.take(2),merged[0].simSources)
        assertTrue(merged[1].id < -1);assertNotEquals(sources[0].id,sources[1].id)
        assertEquals(listOf("89991234567"),merged[0].numbers)
    }
    @Test fun duplicateKeyRequiresSameNameAndCompleteNumberSet() {
        assertEquals(Vcf.contactKey(PersonRecord(1," Анна ",listOf("89991234567","123"))),Vcf.contactKey(PersonRecord(2,"анна",listOf("123","+79991234567"))))
        assertNotEquals(Vcf.contactKey(PersonRecord(1,"Анна",listOf("123"))),Vcf.contactKey(PersonRecord(2,"Борис",listOf("123"))))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsOversizedPhotoBeforeImport() {
        Vcf.decode("BEGIN:VCARD\nVERSION:3.0\nFN:Test\nTEL:123\nPHOTO;ENCODING=b:"+"A".repeat(710001)+"\nEND:VCARD")
    }
}
