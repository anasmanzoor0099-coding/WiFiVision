package com.wifivision.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairedDeviceCredential(
    val deviceId: String,
    val deviceName: String,
    val deviceToken: String,
    val expiresAtIso: String
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return try {
            Instant.parse(expiresAtIso).toEpochMilli() <= nowMs
        } catch (_: Exception) {
            true
        }
    }
}

interface CredentialCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(encodedCiphertext: String): String
}

interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/**
 * Android Keystore AES-256-GCM cipher wrapper. Hardware-backed on supported
 * devices (API 26+); key material never leaves the Android Keystore.
 */
class AndroidKeystoreCredentialCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : CredentialCipher {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEFAULT_KEY_ALIAS = "wifivision_device_cred_aes_key"
        private const val GCM_TAG_BITS = 128
        private const val SEPARATOR = ":"
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(ciphertext)
    }

    override fun decrypt(encodedCiphertext: String): String {
        val parts = encodedCiphertext.split(SEPARATOR, limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("Malformed encrypted payload")

        val decoder = Base64.getDecoder()
        val iv = decoder.decode(parts[0])
        val ciphertext = decoder.decode(parts[1])

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, StandardCharsets.UTF_8)
    }
}

class SharedPrefsKeyValueStorage(context: Context) : KeyValueStorage {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "wifivision_device_credentials"
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/**
 * Encrypted persistent store for scoped Android device upload credentials.
 * Uses Android Keystore AES-GCM encryption and MODE_PRIVATE preferences.
 */
class DeviceCredentialStore(
    private val storage: KeyValueStorage,
    private val cipher: CredentialCipher
) {
    constructor(context: Context) : this(
        storage = SharedPrefsKeyValueStorage(context),
        cipher = AndroidKeystoreCredentialCipher()
    )

    companion object {
        private const val KEY_ENCRYPTED_CREDENTIAL = "paired_device_cred_v1"
        private val TOKEN_REGEX = Regex("^wvd_[0-9a-fA-F]{24}_[0-9a-fA-F]{64}$")

        fun isValidTokenFormat(token: String?): Boolean {
            return !token.isNullOrBlank() && TOKEN_REGEX.matches(token.trim())
        }
    }

    @Synchronized
    fun saveCredential(credential: PairedDeviceCredential): Boolean {
        if (credential.deviceId.isBlank() ||
            credential.deviceName.isBlank() ||
            !isValidTokenFormat(credential.deviceToken)
        ) {
            return false
        }

        val json = "{" +
            "\"deviceId\":${JsonSupport.quote(credential.deviceId)}," +
            "\"deviceName\":${JsonSupport.quote(credential.deviceName)}," +
            "\"deviceToken\":${JsonSupport.quote(credential.deviceToken)}," +
            "\"expiresAt\":${JsonSupport.quote(credential.expiresAtIso)}" +
            "}"

        return try {
            val encrypted = cipher.encrypt(json)
            storage.putString(KEY_ENCRYPTED_CREDENTIAL, encrypted)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun loadCredential(): PairedDeviceCredential? {
        val raw = storage.getString(KEY_ENCRYPTED_CREDENTIAL) ?: return null
        return try {
            val decrypted = cipher.decrypt(raw)
            val parsed = JsonSupport.parseJson(decrypted) as? Map<String, Any?> ?: run {
                clearCredential()
                return null
            }
            val deviceId = parsed["deviceId"] as? String
            val deviceName = parsed["deviceName"] as? String
            val deviceToken = parsed["deviceToken"] as? String
            val expiresAt = parsed["expiresAt"] as? String

            if (deviceId.isNullOrBlank() ||
                deviceName.isNullOrBlank() ||
                !isValidTokenFormat(deviceToken) ||
                expiresAt.isNullOrBlank()
            ) {
                clearCredential()
                return null
            }

            PairedDeviceCredential(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceToken = deviceToken!!,
                expiresAtIso = expiresAt
            )
        } catch (_: Exception) {
            clearCredential()
            null
        }
    }

    @Synchronized
    fun clearCredential() {
        storage.remove(KEY_ENCRYPTED_CREDENTIAL)
    }

    @Synchronized
    fun getActiveCredential(nowMs: Long = System.currentTimeMillis()): PairedDeviceCredential? {
        val cred = loadCredential() ?: return null
        return if (cred.isExpired(nowMs)) null else cred
    }
}
