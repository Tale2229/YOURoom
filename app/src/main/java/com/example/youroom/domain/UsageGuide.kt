package com.example.youroom.domain

enum class GuideOutcome { IN_PROGRESS, COMPLETED, SKIPPED }

data class GuideRecord(val step: Int = 0, val outcome: GuideOutcome = GuideOutcome.IN_PROGRESS,
    val version: Int = GuideHistory.Version) {
    /** Migrate lesson identities, preserving completion/skip without a replay.
     * v1: 45 steps; v2: 44 steps; v3 merges expected-room entry and pinning;
     * v4 swaps the informational replay/about lessons;
     * v5 adds four waiting and two history lessons, keeping existing outcomes. */
    fun current():GuideRecord = when(version) {
        1 -> copy(step=when(step) {
            35 -> 36
            36 -> 35
            42 -> 41
            in 43..Int.MAX_VALUE -> (step-1).coerceAtMost(43)
            else -> step.coerceAtLeast(0)
        },version=2).current()
        2 -> copy(step=when {
            step<=25 -> step.coerceAtLeast(0)
            else -> (step-1).coerceAtMost(42)
        },version=3).current()
        3 -> copy(step=when(step) { 38 -> 39; 39 -> 38; else -> step },version=4).current()
        4 -> copy(step=when {
            step<=28 -> step.coerceAtLeast(0)
            step<=30 -> step+4
            else -> (step+6).coerceAtMost(48)
        },version=5)
        else -> this
    }
}

data class GuideHistory(val available: Boolean = false, val activeAccount: String? = null,
    val records: Map<String, GuideRecord> = emptyMap()) {
    fun current()=copy(records=records.mapValues { it.value.current() })
    fun shouldOffer(identity: String): Boolean = records[identity]?.current()?.let {
        it.version < Version || it.outcome == GuideOutcome.IN_PROGRESS
    } ?: true

    companion object {
        const val Version = 5
        const val Guest = "guest"
        const val ExistingSession = "existing-session"
    }
}

data class GuideRequest(val identity: String, val step: Int = 0, val manual: Boolean = false,
    val resume: Boolean = false, val serial: Long = 0)
