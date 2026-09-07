package ru.zvonilka.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySnapshotCodecTest {
    @Test fun roundTripPreservesRecentHistory() {
        val source=listOf(
            HistoryRecord(7,"+79991234567",2,123456789L,41,"SIM 1"),
            HistoryRecord(8,"",3,123456790L,0,null)
        )
        assertEquals(source,HistorySnapshotCodec.decode(HistorySnapshotCodec.encode(source)))
    }

    @Test fun malformedRowsAreIgnored() {
        val valid=HistorySnapshotCodec.encode(listOf(HistoryRecord(1,"123",1,2,3,null)))
        assertEquals(1,HistorySnapshotCodec.decode("broken\n$valid\n1\t2").size)
    }

    @Test fun snapshotIsBounded() {
        val source=(1L..350L).map { HistoryRecord(it,it.toString(),1,it,0,null) }
        assertEquals(300,HistorySnapshotCodec.decode(HistorySnapshotCodec.encode(source)).size)
    }
}
