package com.example.youroom

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Both normal and isolated test APKs must target their matching app.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expected = instrumentation.context.packageName.removeSuffix(".test")
        assertTrue(expected in setOf("com.example.youroom", "com.example.youroom.uitest"))
        assertEquals(expected, instrumentation.targetContext.packageName)
    }
}
