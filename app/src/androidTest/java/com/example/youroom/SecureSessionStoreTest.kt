package com.example.youroom

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.local.RememberedCredentials
import com.example.youroom.data.local.SecureSessionStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyStore

class SecureSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test_remembered_session"
    private val alias = "youroom.test.remembered.session"
    private val preferences get() = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun store() = SecureSessionStore(context, name, alias)
    private val dummy get() = RememberedCredentials("dummy-account-only", "dummy-password-only")

    @Before fun prepare() { cleanup() }
    @After fun cleanup() {
        context.deleteSharedPreferences(name)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test fun encryptsBothFieldsAndReopensWithoutPlaintext() {
        store().saveLogin("dummy-token-only", dummy)
        assertEquals("dummy-token-only", store().read())
        assertEquals(dummy.account, store().credentials()!!.account)
        assertEquals(dummy.password, store().credentials()!!.password)
        val stored = preferences.all.values.joinToString()
        listOf(dummy.account, dummy.password, "dummy-token-only").forEach { assertFalse(stored.contains(it)) }
        assertEquals(setOf("iv", "ciphertext", "credentials_iv", "credentials"), preferences.all.keys)
        assertNotEquals(preferences.getString("iv", null), preferences.getString("credentials_iv", null))
        assertFalse(dummy.toString().contains(dummy.password))
    }

    @Test fun legacyTokenStillWorksAndDoesNotImplyPasswordConsent() {
        store().save("dummy-legacy-token")
        assertEquals("dummy-legacy-token", store().read())
        assertNull(store().credentials())
    }

    @Test fun renewedTokenRetainsCredentialsButUncheckedLoginRemovesThem() {
        store().saveLogin("dummy-old", dummy)
        store().save("dummy-new")
        assertEquals(dummy.password, store().credentials()!!.password)
        store().saveLogin("dummy-other-account", null)
        assertEquals("dummy-other-account", store().read())
        assertNull(store().credentials())
        assertFalse(preferences.contains("credentials_iv"))
    }

    @Test fun logoutErasesEverythingAndClearTokenCanRetainCredentials() {
        store().saveLogin("dummy-token", dummy)
        store().clearToken()
        assertNull(store().read())
        assertNotNull(store().credentials())
        store().clear()
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun tamperedCredentialsFailClosedWithoutPlaintextFallback() {
        store().saveLogin("dummy-token", dummy)
        preferences.edit().putString("credentials", "not-encrypted").commit()
        assertNull(store().credentials())
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun lostKeyInvalidatesSavedSessionAndCredentials() {
        store().saveLogin("dummy-token", dummy)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        assertNull(store().read())
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun sessionFileIsExcludedFromCloudLegacyAndDeviceTransferBackups() {
        for ((resource, expected) in listOf(R.xml.backup_rules to 1, R.xml.data_extraction_rules to 2)) {
            var exclusions = 0
            context.resources.getXml(resource).use { parser ->
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "exclude" &&
                        parser.getAttributeValue(null, "domain") == "sharedpref" &&
                        parser.getAttributeValue(null, "path") == "youroom_session.xml") exclusions++
                    parser.next()
                }
            }
            assertEquals(expected, exclusions)
        }
    }
}
