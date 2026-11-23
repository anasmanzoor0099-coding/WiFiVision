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

class SessionCredentialStoreTest {

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

    private fun sampleJwt(expSeconds: Long = 4_102_444_800L): String {
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val header = b64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray(StandardCharsets.UTF_8))
        val payload = b64.encodeToString(
            """{"sub":"65f000000000000000000001","email":"alice@example.com","tokenVersion":0,"exp":$expSeconds}"""
                .toByteArray(StandardCharsets.UTF_8)
        )
        val sig = b64.encodeToString("test_signature_bytes_for_jwt".toByteArray(StandardCharsets.UTF_8))
        return "$header.$payload.$sig"
    }

    private fun sampleUser(): AuthUser {
        return AuthUser(
            id = "65f000000000000000000001",
            name = "Alice Example",
            email = "alice@example.com",
            isEmailVerified = true
        )
    }

    @Test
    fun extractCookie_parsesMaxAgeExpiresJwtExpAndMultipleSetCookieHeaders() {
        val nowMs = 1_700_000_000_000L
        val jwt = sampleJwt(expSeconds = 1_900_000_000L)

        // Multiple headers with case-insensitive key; Max-Age takes precedence over Expires
        val headersWithMaxAge = mapOf<String?, List<String>>(
            "set-cookie" to listOf(
                "other_cookie=abc123; Path=/",
                "wifivision_token=$jwt; Max-Age=3600; Path=/; Expires=Wed, 21 Oct 2030 07:28:00 GMT; HttpOnly; SameSite=Strict"
            )
        )
        val extracted = SessionCookieParser.extractCookie(
            headersWithMaxAge,
            SessionCookieParser.SESSION_COOKIE_NAME,
            nowMs = nowMs
        )
        assertNotNull(extracted)
        assertFalse(extracted!!.isCleared)
        assertEquals(jwt, extracted.value)
        assertEquals(nowMs + 3_600_000L, extracted.expiresAtEpochMs)
        assertFalse(extracted.toString().contains(jwt))

        // No Max-Age, parses RFC-1123 Expires
        val headersWithExpires = mapOf<String?, List<String>>(
            "Set-Cookie" to listOf(
                "wifivision_setup_token=${"a".repeat(64)}; Path=/; Expires=Sun, 01 Jan 2040 00:00:00 GMT; HttpOnly"
            )
        )
        val setupCookie = SessionCookieParser.extractCookie(
            headersWithExpires,
            SessionCookieParser.SETUP_COOKIE_NAME,
            nowMs = nowMs
        )
        assertNotNull(setupCookie)
        assertFalse(setupCookie!!.isCleared)
        assertEquals("a".repeat(64), setupCookie.value)
        assertEquals(2_208_988_800_000L, setupCookie.expiresAtEpochMs)
        assertFalse(setupCookie.toString().contains("a".repeat(64)))

        // Neither Max-Age nor Expires -> falls back to JWT exp claim
        val headersJwtOnly = mapOf<String?, List<String>>(
            "Set-Cookie" to listOf("wifivision_token=$jwt; Path=/; HttpOnly")
        )
        val jwtOnly = SessionCookieParser.extractCookie(
            headersJwtOnly,
            SessionCookieParser.SESSION_COOKIE_NAME,
            nowMs = nowMs
        )
        assertNotNull(jwtOnly)
        assertEquals(1_900_000_000_000L, jwtOnly!!.expiresAtEpochMs)
    }

    @Test
    fun extractCookie_detectsClearedCookiesAndRejectsMalformedTokens() {
        val emptyCleared = mapOf<String?, List<String>>(
            "Set-Cookie" to listOf("wifivision_token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
        )
        val parsedEmpty = SessionCookieParser.extractCookie(
            emptyCleared,
            SessionCookieParser.SESSION_COOKIE_NAME
        )
        assertNotNull(parsedEmpty)
        assertTrue(parsedEmpty!!.isCleared)

        val maxAgeZeroCleared = mapOf<String?, List<String>>(
            "Set-Cookie" to listOf("wifivision_token=stale.jwt.value; Max-Age=0; Path=/")
        )
        val parsedZero = SessionCookieParser.extractCookie(
            maxAgeZeroCleared,
            SessionCookieParser.SESSION_COOKIE_NAME
        )
        assertNotNull(parsedZero)
        assertTrue(parsedZero!!.isCleared)

        // Format validators reject whitespace, semicolons, newlines, and wrong formats
        assertTrue(SessionCookieParser.isValidSessionTokenFormat(sampleJwt()))
        assertFalse(SessionCookieParser.isValidSessionTokenFormat("not-a-jwt"))
        assertFalse(SessionCookieParser.isValidSessionTokenFormat("${sampleJwt()}\r\nX-Injected: 1"))
        assertFalse(SessionCookieParser.isValidSessionTokenFormat("${sampleJwt()};admin=true"))

        assertTrue(SessionCookieParser.isValidSetupTokenFormat("0123456789abcdef".repeat(4)))
        assertFalse(SessionCookieParser.isValidSetupTokenFormat("short_hex"))
        assertFalse(SessionCookieParser.isValidSetupTokenFormat("${"a".repeat(64)}\n"))
    }

    @Test
    fun sessionStore_encryptsPersistsExpiresClearsAndIsolatesFromDeviceStore() {
        val sessionStorage = InMemoryStorage()
        val deviceStorage = InMemoryStorage()
        val cipher = JvmAesGcmCipher()

        val sessionStore = SessionCredentialStore(sessionStorage, cipher)
        val deviceStore = DeviceCredentialStore(deviceStorage, cipher)

        val jwt = sampleJwt()
        val nowMs = 1_700_000_000_000L
        val session = UserSessionCredential(
            sessionToken = jwt,
            user = sampleUser(),
            expiresAtEpochMs = nowMs + 86_400_000L,
            savedAtEpochMs = nowMs
        )

        // Pair a device credential concurrently to verify isolation
        val deviceCred = PairedDeviceCredential(
            deviceId = "65f0000000000000000000bb",
            deviceName = "TECNO KN4",
            deviceToken = "wvd_65f0000000000000000000bb_${"f".repeat(64)}",
            expiresAtIso = "2099-01-01T00:00:00Z"
        )
        assertTrue(deviceStore.saveCredential(deviceCred))

        // Save encrypted user session and verify ciphertext never exposes plaintext secrets
        assertTrue(sessionStore.saveSession(session))
        val rawStored = sessionStorage.map.values.first()
        assertFalse(rawStored.contains(jwt))
        assertFalse(rawStored.contains("alice@example.com"))
        assertFalse(session.toString().contains(jwt))

        // Round-trip load
        val loaded = sessionStore.loadSession()
        assertEquals(session, loaded)
        assertNotNull(sessionStore.getActiveSession(nowMs + 1000L))

        // Expired timestamp returns null from getActiveSession
        assertNull(sessionStore.getActiveSession(nowMs + 86_400_000L))
        assertNull(sessionStore.getActiveSession(nowMs + 90_000_000L))

        // Update cached user on active session
        val updatedUser = sampleUser().copy(name = "Alice Updated")
        assertTrue(sessionStore.updateUser(updatedUser, nowMs + 1000L))
        assertEquals("Alice Updated", sessionStore.getActiveSession(nowMs + 1000L)?.user?.name)

        // Password setup credential encrypted persistence & redaction
        val setupToken = "b".repeat(64)
        val setup = PasswordSetupCredential(
            setupToken = setupToken,
            name = "Alice Example",
            email = "alice@example.com",
            expiresAtEpochMs = nowMs + 900_000L
        )
        assertFalse(setup.toString().contains(setupToken))
        assertTrue(sessionStore.savePasswordSetup(setup))
        assertEquals(setup, sessionStore.getActivePasswordSetup(nowMs + 1000L))
        assertNull(sessionStore.getActivePasswordSetup(nowMs + 900_000L))

        // Invalid token format rejected
        assertFalse(sessionStore.saveSession(session.copy(sessionToken = "bad")))
        assertFalse(sessionStore.savePasswordSetup(setup.copy(setupToken = "bad")))

        // Tampered ciphertext clears corrupted entry fail-closed without throwing
        sessionStorage.putString("user_session_v1", "corrupt:payload_data")
        assertNull(sessionStore.loadSession())
        assertNull(sessionStorage.getString("user_session_v1"))

        // ClearAll wipes session & setup while leaving DeviceCredentialStore untouched
        sessionStore.saveSession(session)
        sessionStore.savePasswordSetup(setup)
        sessionStore.clearAll()
        assertTrue(sessionStorage.map.isEmpty())
        assertEquals(deviceCred, deviceStore.getActiveCredential())
    }
}
