package com.example.youroom

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandIdentityTest {
    @Test fun installedLabelApplicationAndThemeUseTheNewBrandWithoutChangingThePackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("YOURoom", context.getString(R.string.app_name))
        assertEquals("YOURoom", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals("com.example.youroom.YOURoomApplication", context.applicationInfo.className)
        assertTrue(context.applicationContext is YOURoomApplication)
        assertEquals("com.example.youroom.uitest", context.packageName)
        assertEquals("Theme.YOURoom", context.resources.getResourceEntryName(context.applicationInfo.theme))
    }
}
