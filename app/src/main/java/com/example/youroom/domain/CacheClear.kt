package com.example.youroom.domain

enum class CacheClearPhase { INFORMATION, CONFIRM, CREDENTIALS, VERIFYING, CLEARING, REQUESTED }

/** No account, password or reusable verification token belongs in UI state. */
data class CacheClearState(val phase:CacheClearPhase,val error:String?=null) {
    val busy get()=phase in setOf(CacheClearPhase.VERIFYING,CacheClearPhase.CLEARING,CacheClearPhase.REQUESTED)
    val irreversible get()=phase==CacheClearPhase.CLEARING || phase==CacheClearPhase.REQUESTED
}
