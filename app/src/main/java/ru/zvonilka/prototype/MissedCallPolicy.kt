package ru.zvonilka.prototype

enum class DelegatedMissedAction { IGNORE, SUPPRESS_SYSTEM, RESTORE_OWN }

object MissedCallPolicy {
    /**
     * Decides what to do with Telecom's delegated missed-call broadcast.
     *
     * Zero/negative counts can be acknowledgements of our own cancellation and must not erase state.
     * If we already own an unread count, suppress only the system notification.
     * If the in-call service is gone and we have no own state, restore from Telecom after a cold start.
     */
    fun delegated(systemCount:Int,ownCount:Int,serviceBound:Boolean):DelegatedMissedAction = when {
        systemCount<=0 -> DelegatedMissedAction.IGNORE
        ownCount>0 -> DelegatedMissedAction.SUPPRESS_SYSTEM
        !serviceBound -> DelegatedMissedAction.RESTORE_OWN
        else -> DelegatedMissedAction.IGNORE
    }
}
