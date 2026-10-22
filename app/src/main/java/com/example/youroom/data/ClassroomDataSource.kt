package com.example.youroom.data

import com.example.youroom.domain.*
import kotlinx.coroutines.flow.Flow

interface ClassroomDataSource {
    val profiles: Flow<Map<String, RoomProfile>>
    suspend fun hasSession(): Boolean
    suspend fun login(account: String, password: String)
    suspend fun login(account: String, password: String, rememberPassword: Boolean) = login(account, password)
    suspend fun rememberedAccount(): String? = null
    suspend fun rememberedCredentials(): com.example.youroom.data.local.RememberedCredentials? = null
    suspend fun currentAccount():String? = rememberedAccount()
    /** Verify remotely without replacing the local session or saving credentials. */
    suspend fun verifyCacheClearCredentials(account:String,password:String) { error("Identity verification unavailable") }
    suspend fun logout()
    suspend fun cached(campus: Campus): ClassroomSnapshot?
    suspend fun refresh(campus: Campus): ClassroomSnapshot
    suspend fun saveProfile(profile: RoomProfile)
    suspend fun deleteProfile(roomId: String)
}
