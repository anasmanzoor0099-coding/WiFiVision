package com.wifivision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DeviceCredentialAndPairingTest {

    private class InMemoryStorage : KeyValueStorage {
        val map = LinkedHashMap<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class JvmAesGcmCipher : CredentialCipher {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        override fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val out = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            return Base64.getEncoder().encodeToString(cipher.iv) +
                ":" +
                Base64.getEncoder().encodeToString(out)
        }

        override fun decrypt(encodedCiphertext: String): String {
            val parts = encodedCiphertext.split(":", limit = 2)
            val iv = Base64.getDecoder().decode(parts[0])
            val body = Base64.getDecoder().decode(parts[1])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(body), StandardCharsets.UTF_8)
        }
    }

    @Test
    fun pairingCodeValidation_normalizesAndRejectsMalformedCodes() {
        assertEquals("ABCDEF23", ApiContractUtils.normalizeAndValidatePairingCode("  abcd-ef23 "))
        assertEquals("234567", ApiContractUtils.normalizeAndValidatePairingCode("234567"))
        assertNull(ApiContractUtils.normalizeAndValidatePairingCode("12345")) // too short
        assertNull(ApiContractUtils.normalizeAndValidatePairingCode("O1IL2345")) // ambiguous chars excluded
        assertNull(ApiContractUtils.normalizeAndValidatePairingCode("ABCD!@#4"))
    }

    @Test
    fun credentialStore_encryptsPersistsLoadsAndHandlesTamperingOrExpiry() {
        val storage = InMemoryStorage()
        val store = DeviceCredentialStore(storage, JvmAesGcmCipher())

        val sampleToken = "wvd_65f0000000000000000000ab_${"c".repeat(64)}"
        val validCred = PairedDeviceCredential(
            deviceId = "65f0000000000000000000ab",
            deviceName = "TECNO KN4",
            deviceToken = sampleToken,
            expiresAtIso = "2099-01-01T00:00:00Z"
        )

        assertTrue(store.saveCredential(validCred))
        val rawStored = storage.map.values.first()
        assertFalse("Ciphertext must not expose raw token", rawStored.contains(sampleToken))

        val loaded = store.loadCredential()
        assertNotNull(loaded)
        assertEquals(validCred, loaded)
        assertNotNull(store.getActiveCredential())

        // Invalid token format rejected cleanly
        assertFalse(
            store.saveCredential(validCred.copy(deviceToken = "bad_token_format"))
        )

        // Tampered ciphertext safely cleared without crashing
        storage.putString("paired_device_cred_v1", "corrupt:data_payload")
        assertNull(store.loadCredential())
        assertTrue(storage.map.isEmpty())

        // Expired credential returns null from getActiveCredential()
        val expiredCred = validCred.copy(expiresAtIso = "2020-01-01T00:00:00Z")
        assertTrue(store.saveCredential(expiredCred))
        assertNull(store.getActiveCredential())

        // Clear credential works cleanly
        store.clearCredential()
        assertNull(store.loadCredential())
    }
}
