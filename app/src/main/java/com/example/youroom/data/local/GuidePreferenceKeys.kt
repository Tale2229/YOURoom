package com.example.youroom.data.local

import androidx.datastore.preferences.core.*
import com.example.youroom.domain.*
import kotlinx.serialization.json.*
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Separate progress keys. No school credentials, tokens or plaintext account IDs. */
internal object GuidePreferenceKeys {
    private val records = stringPreferencesKey("guide.records.v1")
    private val active = stringPreferencesKey("guide.active_account")
    private val salt = stringPreferencesKey("guide.local_identity_key")

    fun read(values: Preferences): GuideHistory {
        val parsed = runCatching {
            Json.parseToJsonElement(values[records] ?: "{}").jsonObject.mapNotNull { (id, element) ->
                runCatching {
                    val record = element.jsonObject
                    id to GuideRecord(record.getValue("step").jsonPrimitive.int.coerceAtLeast(0),
                        GuideOutcome.valueOf(record.getValue("outcome").jsonPrimitive.content),
                        record.getValue("version").jsonPrimitive.int).current()
                }.getOrNull()
            }.toMap()
        }.getOrDefault(emptyMap())
        return GuideHistory(true, values[active], parsed)
    }

    fun write(values: MutablePreferences, identity: String, record: GuideRecord) {
        require(identity.isNotBlank() && record.step >= 0)
        val updated = read(values).records + (identity to record)
        values[records] = buildJsonObject {
            updated.forEach { (id, saved) -> put(id, buildJsonObject {
                put("step", saved.step); put("outcome", saved.outcome.name); put("version", saved.version)
            }) }
        }.toString()
    }

    fun account(values: MutablePreferences, account: String): String {
        require(account.isNotBlank())
        val key = values[salt] ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it.toInt() and 255) }.also { values[salt] = it }
        val identity=identity(key,account)
        values[active] = identity
        return identity
    }

    /** Migration fallback for sessions created before encrypted account metadata. */
    fun matchesActiveAccount(values:Preferences,account:String):Boolean? {
        val key=values[salt] ?: return null
        val expected=values[active] ?: return null
        return runCatching {
            MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8),identity(key,account).toByteArray(Charsets.UTF_8))
        }.getOrDefault(false)
    }

    private fun identity(key:String,account:String):String {
        val bytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(bytes,"HmacSHA256")) }
        return "account:" + mac.doFinal(account.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
