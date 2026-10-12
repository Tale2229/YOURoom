package com.example.youroom

import android.app.Application
import androidx.room.Room
import com.example.youroom.data.ClassroomRepository
import com.example.youroom.data.SchoolApi
import com.example.youroom.data.local.SecureSessionStore
import com.example.youroom.data.local.SettingsStore
import com.example.youroom.data.local.YouRoomDatabase

class YouRoomApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(application, YouRoomDatabase::class.java, "youroom.db").build()
    val repository = ClassroomRepository(SchoolApi(), database.dao(), SecureSessionStore(application))
    val settings = SettingsStore(application)
}
