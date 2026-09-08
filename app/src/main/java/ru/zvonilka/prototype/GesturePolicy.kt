package ru.zvonilka.prototype

object GesturePolicy {
    fun shouldComplete(
        offset:Float,
        distance:Float,
        velocity:Float,
        positionThreshold:Float,
        flingMinProgress:Float,
        flingVelocity:Float
    ):Boolean {
        if(distance<=0f) return false
        val progress=(offset/distance).coerceIn(0f,1f)
        return progress>=positionThreshold || (progress>=flingMinProgress && velocity>=flingVelocity)
    }
}
