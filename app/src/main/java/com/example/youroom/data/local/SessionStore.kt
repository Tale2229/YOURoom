package com.example.youroom.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStore {
    fun read(): String?
    fun save(token: String)
    fun clear()
}

class SecureSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences("youroom_session", Context.MODE_PRIVATE)

    override fun read(): String? {
        val ciphertext = preferences.getString("ciphertext", null) ?: return null
        return try {
            val iv = Base64.getDecoder().decode(preferences.getString("iv", null) ?: error("Missing IV"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), Charsets.UTF_8)
        } catch (_: Exception) {
            // Restored data or an invalidated device key must never fall back to plaintext.
            clear()
            null
        }
    }

    override fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plaintext = token.toByteArray(Charsets.UTF_8)
        try {
            val ciphertext = cipher.doFinal(plaintext)
            check(preferences.edit()
                .putString("iv", Base64.getEncoder().encodeToString(cipher.iv))
                .putString("ciphertext", Base64.getEncoder().encodeToString(ciphertext)).commit())
        } finally { plaintext.fill(0) }
    }

    override fun clear() { check(preferences.edit().clear().commit()) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    companion object { private const val ALIAS = "youroom.session.v1" }
}
