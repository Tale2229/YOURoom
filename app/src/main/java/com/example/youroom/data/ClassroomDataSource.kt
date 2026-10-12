package com.example.youroom.data

import com.example.youroom.domain.*
import kotlinx.coroutines.flow.Flow

interface ClassroomDataSource {
    val profiles: Flow<Map<String, RoomProfile>>
    suspend fun hasSession(): Boolean
    suspend fun login(account: String, password: String)
    suspend fun logout()
    suspend fun cached(campus: Campus): ClassroomSnapshot?
    suspend fun refresh(campus: Campus): ClassroomSnapshot
    suspend fun saveProfile(profile: RoomProfile)
    suspend fun deleteProfile(roomId: String)
}
