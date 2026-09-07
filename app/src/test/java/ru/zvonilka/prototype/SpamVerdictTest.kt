package ru.zvonilka.prototype
import org.junit.Assert.*
import org.junit.Test
class SpamVerdictTest {
    @Test fun noEvidenceIsNotSafeOrSpam() {
        assertNull(SpamVerdict.label("G_FRAUD",0,false,false))
        assertNull(SpamVerdict.label("E_ADVERTISING",3,false,false))
        assertNull(SpamVerdict.label("A_LEGITIMATE",20,false,false))
        assertNull(SpamVerdict.label("UNKNOWN",20,false,false))
    }
    @Test fun archivedAndWhitelistedNeverWarn() {
        assertNull(SpamVerdict.label("G_FRAUD",20,true,false))
        assertNull(SpamVerdict.label("G_FRAUD",20,false,true))
    }
    @Test fun exactCommunityEvidenceIsQualified() {
        assertEquals("Жалобы на мошенничество",SpamVerdict.label("G_FRAUD",4,false,false))
        assertEquals("Возможная реклама",SpamVerdict.label("E_ADVERTISING",4,false,false))
    }
}
