package com.example.youroom.ui

/** Monotonic, leading-edge throttle. Rejected taps do not extend the cooldown. */
internal class PlanningTapCooldown(private val intervalMillis: Long=2500L) {
    private var acceptedAt: Long?=null
    fun acquire(now: Long): Boolean {
        val previous=acceptedAt
        if(previous!=null && now-previous<intervalMillis) return false
        acceptedAt=now
        return true
    }
}
