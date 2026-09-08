package ru.zvonilka.prototype

import org.junit.Assert.*
import org.junit.Test

class GesturePolicyTest {
    @Test fun positionThresholdCompletes() {
        assertTrue(GesturePolicy.shouldComplete(72f,100f,0f,.72f,.25f,1200f))
    }
    @Test fun fastFlingCompletesAfterMinimumTravel() {
        assertTrue(GesturePolicy.shouldComplete(30f,100f,1600f,.72f,.25f,1200f))
    }
    @Test fun tinyFlingNeverCompletes() {
        assertFalse(GesturePolicy.shouldComplete(10f,100f,2500f,.72f,.25f,1200f))
    }
    @Test fun slowShortDragReturns() {
        assertFalse(GesturePolicy.shouldComplete(50f,100f,500f,.72f,.25f,1200f))
    }
}
