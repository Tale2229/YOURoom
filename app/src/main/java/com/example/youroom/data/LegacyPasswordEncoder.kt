package com.example.youroom.data

import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Compatibility with the school's public frontend, NOT a scheme for storing passwords. */
object LegacyPasswordEncoder {
    fun encode(password: String): String {
        val key = SecretKeySpec("qzkj1kjghd=876&*".toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        // Java's PKCS5Padding on AES implements the same block padding as PKCS7.
        val plaintext = JsonPrimitive(password).toString().toByteArray(Charsets.UTF_8)
        return try {
            val first = Base64.getEncoder().encodeToString(cipher.doFinal(plaintext))
            Base64.getEncoder().encodeToString(first.toByteArray(Charsets.US_ASCII))
        } finally { plaintext.fill(0) }
    }
}
