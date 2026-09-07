package ru.zvonilka.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class MissedCallPolicyTest {
    @Test fun zeroBroadcastNeverErasesExistingState() {
        assertEquals(DelegatedMissedAction.IGNORE,MissedCallPolicy.delegated(0,3,false))
        assertEquals(DelegatedMissedAction.IGNORE,MissedCallPolicy.delegated(0,0,true))
    }

    @Test fun existingOwnCountWinsOverTelecomBroadcast() {
        assertEquals(DelegatedMissedAction.SUPPRESS_SYSTEM,MissedCallPolicy.delegated(1,1,true))
        assertEquals(DelegatedMissedAction.SUPPRESS_SYSTEM,MissedCallPolicy.delegated(4,2,false))
    }

    @Test fun coldStartRestoresTelecomCountWhenOwnStateIsEmpty() {
        assertEquals(DelegatedMissedAction.RESTORE_OWN,MissedCallPolicy.delegated(1,0,false))
        assertEquals(DelegatedMissedAction.RESTORE_OWN,MissedCallPolicy.delegated(3,0,false))
    }

    @Test fun liveServiceRemainsSoleCounterWriter() {
        assertEquals(DelegatedMissedAction.IGNORE,MissedCallPolicy.delegated(1,0,true))
    }
}
