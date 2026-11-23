package com.wifivision.app

import android.content.Context
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

data class UserSessionCredential(
    val sessionToken: String,
    val user: AuthUser,
    val expiresAtEpochMs: Long,
    val savedAtEpochMs: Long
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = expiresAtEpochMs <= nowMs

    override fun toString(): String {
        return "UserSessionCredential(sessionToken=[REDACTED], user=$user, " +
            "expiresAtEpochMs=$expiresAtEpochMs, savedAtEpochMs=$savedAtEpochMs)"
    }
}

data class PasswordSetupCredential(
    val setupToken: String,
    val name: String,
    val email: String,
    val expiresAtEpochMs: Long
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = expiresAtEpochMs <= nowMs

    override fun toString(): String {
        return "PasswordSetupCredential(setupToken=[REDACTED], name=$name, " +
            "email=$email, expiresAtEpochMs=$expiresAtEpochMs)"
    }
}

/**
 * Pure-Kotlin HTTP Set-Cookie extractor and token format validator for WiFiVision
 * session cookies (`wifivision_token` and `wifivision_setup_token`).
 */
object SessionCookieParser {
    const val SESSION_COOKIE_NAME = "wifivision_token"
    const val SETUP_COOKIE_NAME = "wifivision_setup_token"
    const val DEFAULT_SESSION_TTL_MS = 24L * 60L * 60L * 1000L
    const val DEFAULT_SETUP_TTL_MS = 15L * 60L * 1000L

    private val JWT_FORMAT_REGEX = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
    private val HEX_SETUP_TOKEN_REGEX = Regex("^[0-9a-fA-F]{32,128}$")

    data class ExtractedCookie(
        val name: String,
        val value: String,
        val expiresAtEpochMs: Long,
        val isCleared: Boolean
    ) {
        override fun toString(): String {
            return "ExtractedCookie(name=$name, value=[REDACTED], " +
                "expiresAtEpochMs=$expiresAtEpochMs, isCleared=$isCleared)"
        }
    }

    fun isValidSessionTokenFormat(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        if (token.any { it.isWhitespace() || it == ';' || it == '\r' || it == '\n' }) return false
        return JWT_FORMAT_REGEX.matches(token)
    }

    fun isValidSetupTokenFormat(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        if (token.any { it.isWhitespace() || it == ';' || it == '\r' || it == '\n' }) return false
        return HEX_SETUP_TOKEN_REGEX.matches(token)
    }

    @Suppress("UNCHECKED_CAST")
    fun extractJwtExpiryMs(jwt: String): Long? {
        if (!isValidSessionTokenFormat(jwt)) return null
        val parts = jwt.split('.')
        if (parts.size != 3) return null
        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
            val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
            val map = JsonSupport.parseJson(payloadJson) as? Map<String, Any?> ?: return null
            val expSeconds = (map["exp"] as? Number)?.toLong() ?: return null
            if (expSeconds > 0L) expSeconds * 1000L else null
        } catch (_: Exception) {
            null
        }
    }

    fun extractCookie(
        headerFields: Map<String?, List<String>>,
        cookieName: String,
        nowMs: Long = System.currentTimeMillis(),
        defaultTtlMs: Long = DEFAULT_SESSION_TTL_MS
    ): ExtractedCookie? {
        var matched: ExtractedCookie? = null
        for ((key, values) in headerFields) {
            if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
            for (rawHeader in values) {
                val parsed = parseSingleSetCookie(rawHeader, cookieName, nowMs, defaultTtlMs)
                if (parsed != null) {
                    matched = parsed
                }
            }
        }
        return matched
    }

    private fun parseSingleSetCookie(
        rawHeader: String,
        cookieName: String,
        nowMs: Long,
        defaultTtlMs: Long
    ): ExtractedCookie? {
        val segments = rawHeader.split(';')
        val nameValueSegment = segments.firstOrNull() ?: return null
        val eqIdx = nameValueSegment.indexOf('=')
        if (eqIdx <= 0) return null

        val name = nameValueSegment.substring(0, eqIdx).trim()
        if (name != cookieName) return null

        val value = nameValueSegment.substring(eqIdx + 1).trim()
        var maxAgeSeconds: Long? = null
        var expiresHeaderMs: Long? = null

        for (attribute in segments.drop(1)) {
            val attrEq = attribute.indexOf('=')
            val attrName = (if (attrEq >= 0) attribute.substring(0, attrEq) else attribute)
                .trim()
                .lowercase(Locale.US)
            val attrVal = if (attrEq >= 0) attribute.substring(attrEq + 1).trim() else ""

            when (attrName) {
                "max-age" -> {
                    attrVal.toLongOrNull()?.let { maxAgeSeconds = it }
                }
                "expires" -> {
                    parseRfc1123Millis(attrVal)?.let { expiresHeaderMs = it }
                }
            }
        }

        val isCleared = value.isEmpty() ||
            (maxAgeSeconds != null && maxAgeSeconds <= 0L) ||
            (expiresHeaderMs != null && expiresHeaderMs <= 0L)

        if (isCleared) {
            return ExtractedCookie(
                name = name,
                value = "",
                expiresAtEpochMs = 0L,
                isCleared = true
            )
        }

        val resolvedExpiryMs = when {
            maxAgeSeconds != null && maxAgeSeconds > 0L -> nowMs + maxAgeSeconds!! * 1000L
            expiresHeaderMs != null -> expiresHeaderMs!!
            else -> extractJwtExpiryMs(value) ?: (nowMs + defaultTtlMs)
        }

        return ExtractedCookie(
            name = name,
            value = value,
            expiresAtEpochMs = resolvedExpiryMs,
            isCleared = false
        )
    }

    private fun parseRfc1123Millis(raw: String): Long? {
        if (raw.isEmpty()) return null
        return try {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

class SessionSharedPrefsStorage(context: Context) : KeyValueStorage {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "wifivision_user_session"
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
 * Encrypted persistent store for the signed-in user's account session cookie
 * (`wifivision_token`) and temporary registration setup token (`wifivision_setup_token`).
 *
 * Strictly isolated from [DeviceCredentialStore] (`wifivision_device_credentials`)
 * and backed by its own dedicated Android Keystore AES-256-GCM key alias.
 */
class SessionCredentialStore(
    private val storage: KeyValueStorage,
    private val cipher: CredentialCipher
) {
    constructor(context: Context) : this(
        storage = SessionSharedPrefsStorage(context),
        cipher = AndroidKeystoreCredentialCipher(keyAlias = USER_SESSION_KEY_ALIAS)
    )

    companion object {
        private const val USER_SESSION_KEY_ALIAS = "wifivision_user_session_aes_key"
        private const val KEY_ENCRYPTED_SESSION = "user_session_v1"
        private const val KEY_ENCRYPTED_SETUP = "password_setup_v1"
    }

    @Synchronized
    fun saveSession(session: UserSessionCredential): Boolean {
        if (!SessionCookieParser.isValidSessionTokenFormat(session.sessionToken)) {
            return false
        }
        if (session.user.id.isBlank() || session.user.email.isBlank()) {
            return false
        }
        if (session.expiresAtEpochMs <= 0L) {
            return false
        }

        val json = "{" +
            "\"sessionToken\":${JsonSupport.quote(session.sessionToken)}," +
            "\"expiresAtEpochMs\":${session.expiresAtEpochMs}," +
            "\"savedAtEpochMs\":${session.savedAtEpochMs}," +
            "\"user\":{" +
            "\"id\":${JsonSupport.quote(session.user.id)}," +
            "\"name\":${JsonSupport.quote(session.user.name)}," +
            "\"email\":${JsonSupport.quote(session.user.email)}," +
            "\"isEmailVerified\":${session.user.isEmailVerified}" +
            "}" +
            "}"

        return try {
            val encrypted = cipher.encrypt(json)
            storage.putString(KEY_ENCRYPTED_SESSION, encrypted)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun loadSession(): UserSessionCredential? {
        val raw = storage.getString(KEY_ENCRYPTED_SESSION) ?: return null
        return try {
            val decrypted = cipher.decrypt(raw)
            val parsed = JsonSupport.parseJson(decrypted) as? Map<String, Any?> ?: run {
                clearSession()
                return null
            }

            val token = parsed["sessionToken"] as? String
            val expiresAt = (parsed["expiresAtEpochMs"] as? Number)?.toLong()
            val savedAt = (parsed["savedAtEpochMs"] as? Number)?.toLong() ?: 0L
            val userMap = parsed["user"] as? Map<String, Any?>
            val user = ApiJsonParsers.parseAuthUser(userMap)

            if (!SessionCookieParser.isValidSessionTokenFormat(token) ||
                expiresAt == null ||
                expiresAt <= 0L ||
                user == null
            ) {
                clearSession()
                return null
            }

            UserSessionCredential(
                sessionToken = token!!,
                user = user,
                expiresAtEpochMs = expiresAt,
                savedAtEpochMs = savedAt
            )
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    @Synchronized
    fun getActiveSession(nowMs: Long = System.currentTimeMillis()): UserSessionCredential? {
        val session = loadSession() ?: return null
        return if (session.isExpired(nowMs)) null else session
    }

    @Synchronized
    fun updateUser(user: AuthUser, nowMs: Long = System.currentTimeMillis()): Boolean {
        val current = getActiveSession(nowMs) ?: return false
        return saveSession(current.copy(user = user))
    }

    @Synchronized
    fun clearSession() {
        storage.remove(KEY_ENCRYPTED_SESSION)
    }

    @Synchronized
    fun savePasswordSetup(credential: PasswordSetupCredential): Boolean {
        if (!SessionCookieParser.isValidSetupTokenFormat(credential.setupToken)) {
            return false
        }
        if (credential.email.isBlank() || credential.expiresAtEpochMs <= 0L) {
            return false
        }

        val json = "{" +
            "\"setupToken\":${JsonSupport.quote(credential.setupToken)}," +
            "\"name\":${JsonSupport.quote(credential.name)}," +
            "\"email\":${JsonSupport.quote(credential.email)}," +
            "\"expiresAtEpochMs\":${credential.expiresAtEpochMs}" +
            "}"

        return try {
            val encrypted = cipher.encrypt(json)
            storage.putString(KEY_ENCRYPTED_SETUP, encrypted)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun loadPasswordSetup(): PasswordSetupCredential? {
        val raw = storage.getString(KEY_ENCRYPTED_SETUP) ?: return null
        return try {
            val decrypted = cipher.decrypt(raw)
            val parsed = JsonSupport.parseJson(decrypted) as? Map<String, Any?> ?: run {
                clearPasswordSetup()
                return null
            }

            val setupToken = parsed["setupToken"] as? String
            val name = parsed["name"] as? String
            val email = parsed["email"] as? String
            val expiresAt = (parsed["expiresAtEpochMs"] as? Number)?.toLong()

            if (!SessionCookieParser.isValidSetupTokenFormat(setupToken) ||
                email.isNullOrBlank() ||
                expiresAt == null ||
                expiresAt <= 0L
            ) {
                clearPasswordSetup()
                return null
            }

            PasswordSetupCredential(
                setupToken = setupToken!!,
                name = name?.trim().orEmpty(),
                email = email.trim(),
                expiresAtEpochMs = expiresAt
            )
        } catch (_: Exception) {
            clearPasswordSetup()
            null
        }
    }

    @Synchronized
    fun getActivePasswordSetup(nowMs: Long = System.currentTimeMillis()): PasswordSetupCredential? {
        val cred = loadPasswordSetup() ?: return null
        return if (cred.isExpired(nowMs)) null else cred
    }

    @Synchronized
    fun clearPasswordSetup() {
        storage.remove(KEY_ENCRYPTED_SETUP)
    }

    @Synchronized
    fun clearAll() {
        clearSession()
        clearPasswordSetup()
    }
}
