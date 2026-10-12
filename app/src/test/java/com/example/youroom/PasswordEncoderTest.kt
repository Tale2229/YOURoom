package com.example.youroom

import com.example.youroom.data.LegacyPasswordEncoder
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class PasswordEncoderTest {
    @Test fun matchesIndependentlyVerifiedDummyPasswordFixture() {
        assertEquals("SzczWC9UUTdGNGFBRS9OMU02OVRzWTRvM3NVNXk0NVJzUTZEWE0wSW8wZz0=",
            LegacyPasswordEncoder.encode("Classroom_Test_123!"))
    }

    @Test fun encodesJsonQuotesUnicodeAndSpecialCharactersWithoutLoss() {
        val password = "测试&<>\"'\\\t🌿"
        val twice = Base64.getDecoder().decode(LegacyPasswordEncoder.encode(password))
        val encrypted = Base64.getDecoder().decode(String(twice, Charsets.US_ASCII))
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("qzkj1kjghd=876&*".toByteArray(), "AES"))
        assertEquals(JsonPrimitive(password).toString(), String(cipher.doFinal(encrypted), Charsets.UTF_8))
    }
}
