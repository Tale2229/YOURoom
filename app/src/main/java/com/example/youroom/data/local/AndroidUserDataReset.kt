package com.example.youroom.data.local

import android.app.ActivityManager
import android.content.Context

/** Android erases this app's entire private data after stopping its processes.
 * No path enumeration, manual database deletion or second package is involved.
 * A true result means accepted, not completed: the process normally dies next. */
class AndroidUserDataReset(context:Context) {
    private val application=context.applicationContext
    fun request():Boolean=application.getSystemService(ActivityManager::class.java)?.clearApplicationUserData()==true
}
