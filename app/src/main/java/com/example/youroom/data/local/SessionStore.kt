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
import org.json.JSONObject

/** Never include secrets in generated toString/equals/log output. */
class RememberedCredentials(val account: String, val password: String) {
    override fun toString() = "RememberedCredentials([redacted])"
}

interface SessionStore {
    fun read(): String?
    fun save(token: String)
    fun clear()
    fun credentials(): RememberedCredentials? = null
    fun currentAccount():String? = credentials()?.account
    fun saveLogin(token: String, credentials: RememberedCredentials?) {
        check(credentials == null) { "Credential storage is unavailable" }
        save(token)
    }
    fun clearToken() = clear()
    fun saveLogin(token:String,credentials:RememberedCredentials?,account:String?) = saveLogin(token,credentials)
}

class SecureSessionStore(context: Context,
    preferencesName: String = "youroom_session",
    private val keyAlias: String = "youroom.session.v1",
) : SessionStore {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun read(): String? {
        return decrypt("ciphertext", "iv")
    }

    private fun decrypt(valueKey: String, ivKey: String): String? {
        val ciphertext = preferences.getString(valueKey, null) ?: return null
        return try {
            val iv = Base64.getDecoder().decode(preferences.getString(ivKey, null) ?: error("Missing IV"))
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
        val (iv, ciphertext) = encrypt(token)
        check(preferences.edit().putString("iv", iv).putString("ciphertext", ciphertext).commit())
    }

    override fun credentials(): RememberedCredentials? {
        val json = decrypt("credentials", "credentials_iv") ?: return null
        return try {
            val value = JSONObject(json)
            RememberedCredentials(value.getString("account"), value.getString("password")).also {
                require(it.account.isNotBlank() && it.password.isNotEmpty())
            }
        } catch (_: Exception) { clear(); null }
    }

    override fun saveLogin(token: String, credentials: RememberedCredentials?) {
        saveLogin(token,credentials,null)
    }

    override fun currentAccount():String? = decrypt("account","account_iv") ?: credentials()?.account

    override fun saveLogin(token: String, credentials: RememberedCredentials?, account:String?) {
        // Encrypt everything before committing: never persist a half-updated account/session.
        val encryptedToken = encrypt(token)
        val encryptedCredentials = credentials?.let {
            encrypt(JSONObject().put("account", it.account).put("password", it.password).toString())
        }
        val encryptedAccount=account?.let(::encrypt)
        val edit = preferences.edit().putString("iv", encryptedToken.first)
            .putString("ciphertext", encryptedToken.second)
            .remove("credentials").remove("credentials_iv").remove("account").remove("account_iv")
        encryptedCredentials?.let { edit.putString("credentials_iv", it.first).putString("credentials", it.second) }
        encryptedAccount?.let { edit.putString("account_iv",it.first).putString("account",it.second) }
        check(edit.commit())
    }

    override fun clearToken() {
        check(preferences.edit().remove("ciphertext").remove("iv").remove("account").remove("account_iv").commit())
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plaintext = value.toByteArray(Charsets.UTF_8)
        try {
            val ciphertext = cipher.doFinal(plaintext)
            return Base64.getEncoder().encodeToString(cipher.iv) to Base64.getEncoder().encodeToString(ciphertext)
        } finally { plaintext.fill(0) }
    }

    override fun clear() { check(preferences.edit().clear().commit()) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

}
