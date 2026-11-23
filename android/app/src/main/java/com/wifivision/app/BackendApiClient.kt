package com.wifivision.app

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class UrlValidationResult {
    data class Valid(val normalizedUrl: String) : UrlValidationResult()
    data class Invalid(val reason: String) : UrlValidationResult()
}

data class HealthCheckResult(
    val reachable: Boolean,
    val healthy: Boolean,
    val statusCode: Int?,
    val databaseStatus: String?,
    val message: String,
    val service: String? = null,
    val status: String? = null,
    val timestamp: String? = null
)

sealed class UploadAttemptResult {
    data class Success(val statusCode: Int, val message: String) : UploadAttemptResult()
    data class Unauthorized(val statusCode: Int, val code: String?, val message: String) : UploadAttemptResult()
    data class ClientRejected(val statusCode: Int, val message: String) : UploadAttemptResult()
    data class RateLimited(val retryAfterSeconds: Long?, val message: String) : UploadAttemptResult()
    data class TransientFailure(val statusCode: Int?, val message: String) : UploadAttemptResult()
}

sealed class PairingAttemptResult {
    data class Success(val credential: PairedDeviceCredential, val message: String) : PairingAttemptResult()
    data class Rejected(val statusCode: Int, val message: String) : PairingAttemptResult()
    data class NetworkError(val message: String) : PairingAttemptResult()
}

/**
 * Pure URL/input validators, pairing code normalizer, nonce generator, and HTTP
 * helpers (JVM unit-testable without Android dependencies).
 */
object ApiContractUtils {

    private val PAIRING_CODE_REGEX = Regex("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6,8}$")
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val OTP_REGEX = Regex("^\\d{6}$")
    private val OBJECT_ID_REGEX = Regex("^[0-9a-fA-F]{24}$")
    private const val NONCE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-"
    private val secureRandom = SecureRandom()

    fun normalizeBaseUrl(rawUrl: String): String = rawUrl.trim().trimEnd('/')

    fun validateBaseUrl(rawUrl: String, allowCleartext: Boolean): UrlValidationResult {
        val trimmed = normalizeBaseUrl(rawUrl)
        if (trimmed.isEmpty()) {
            return UrlValidationResult.Invalid("API base URL cannot be empty")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return UrlValidationResult.Invalid("Invalid URL syntax")
        }

        val scheme = uri.scheme?.lowercase()
            ?: return UrlValidationResult.Invalid("URL must start with http:// or https://")

        if (scheme != "https" && scheme != "http") {
            return UrlValidationResult.Invalid("Only http:// or https:// URLs are supported")
        }
        if (scheme == "http" && !allowCleartext) {
            return UrlValidationResult.Invalid("Release builds require an HTTPS backend URL")
        }
        if (!uri.userInfo.isNullOrEmpty()) {
            return UrlValidationResult.Invalid("URL must not contain embedded credentials")
        }
        if (uri.host.isNullOrBlank()) {
            return UrlValidationResult.Invalid("URL must include a valid host")
        }
        if (!uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) {
            return UrlValidationResult.Invalid("Base URL must not include query parameters or fragments")
        }

        return UrlValidationResult.Valid(trimmed)
    }

    fun normalizeAndValidatePairingCode(rawCode: String?): String? {
        val cleaned = rawCode
            ?.trim()
            ?.uppercase()
            ?.replace(" ", "")
            ?.replace("-", "")
            ?: return null
        return if (PAIRING_CODE_REGEX.matches(cleaned)) cleaned else null
    }

    fun isValidEmail(raw: String?): Boolean {
        val cleaned = raw?.trim()?.lowercase(Locale.US) ?: return false
        return cleaned.length <= 254 && EMAIL_REGEX.matches(cleaned)
    }

    fun isValidName(raw: String?): Boolean {
        val cleaned = raw?.trim() ?: return false
        return cleaned.length in 2..60
    }

    fun isValidOtpCode(raw: String?): Boolean {
        val cleaned = raw?.trim() ?: return false
        return OTP_REGEX.matches(cleaned)
    }

    fun validatePasswordStrength(password: String): String? {
        if (password.length < 8) return "Password must contain at least 8 characters."
        if (password.length > 72) return "Password cannot exceed 72 characters."
        if (password.none { it in 'A'..'Z' }) return "Password must contain an uppercase letter."
        if (password.none { it in 'a'..'z' }) return "Password must contain a lowercase letter."
        if (password.none { it in '0'..'9' }) return "Password must contain a number."
        if (password.none { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' }) {
            return "Password must contain at least one special character."
        }
        return null
    }

    fun isValidObjectId(raw: String?): Boolean {
        val cleaned = raw?.trim() ?: return false
        return OBJECT_ID_REGEX.matches(cleaned)
    }

    fun encodeQueryParam(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    fun generateRequestNonce(length: Int = 24): String {
        val safeLen = length.coerceIn(16, 64)
        val sb = StringBuilder(safeLen)
        repeat(safeLen) {
            sb.append(NONCE_CHARS[secureRandom.nextInt(NONCE_CHARS.length)])
        }
        return sb.toString()
    }

    fun parseRetryAfterSeconds(headerValue: String?): Long? {
        val trimmed = headerValue?.trim() ?: return null
        return trimmed.toLongOrNull()?.takeIf { it > 0L }
    }
}

class BackendApiClient(
    private val resultDispatcher: ((Runnable) -> Unit)? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val mainHandler: Handler? by lazy {
        if (resultDispatcher == null) Handler(Looper.getMainLooper()) else null
    }

    @Volatile
    private var isShutdown = false

    companion object {
        private const val HEALTH_CONNECT_TIMEOUT_MS = 5_000
        private const val HEALTH_READ_TIMEOUT_MS = 5_000
        private const val UPLOAD_CONNECT_TIMEOUT_MS = 8_000
        private const val UPLOAD_READ_TIMEOUT_MS = 10_000
        private const val API_CONNECT_TIMEOUT_MS = 8_000
        private const val API_READ_TIMEOUT_MS = 12_000
        private const val PERFORMANCE_TEST_READ_TIMEOUT_MS = 60_000

        private val VALID_STATS_BANDS = setOf("2.4 GHz", "5 GHz", "6 GHz", "Unknown")
        private val VALID_HEATMAP_BANDS = setOf("2.4 GHz", "5 GHz", "6 GHz")
        private val VALID_PLATFORMS = setOf("android", "windows", "any")
    }

    private fun postResult(action: () -> Unit) {
        if (isShutdown) return
        val runnable = Runnable { action() }
        val dispatcher = resultDispatcher
        if (dispatcher != null) {
            dispatcher(runnable)
        } else {
            mainHandler?.post(runnable)
        }
    }

    // -------------------------------------------------------------------------
    // Public Health & Device-Scoped Endpoints (Existing Contract Preserved)
    // -------------------------------------------------------------------------

    fun checkHealth(baseUrl: String, onResult: (HealthCheckResult) -> Unit) {
        if (isShutdown) return
        val normalized = ApiContractUtils.normalizeBaseUrl(baseUrl)
        executor.execute {
            val result = performHealthCheck(normalized)
            postResult { onResult(result) }
        }
    }

    fun pairDevice(
        baseUrl: String,
        pairingCode: String,
        deviceName: String?,
        onResult: (PairingAttemptResult) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        val normalizedCode = ApiContractUtils.normalizeAndValidatePairingCode(pairingCode)
        if (normalizedCode == null) {
            postResult {
                onResult(
                    PairingAttemptResult.Rejected(
                        400,
                        "Enter the 6-8 character pairing code generated in web Settings."
                    )
                )
            }
            return
        }

        val cleanedName = deviceName?.trim()?.takeIf { it.isNotEmpty() }?.take(60)
        val body = buildString {
            append("{\"pairingCode\":")
            append(JsonSupport.quote(normalizedCode))
            if (cleanedName != null) {
                append(",\"deviceName\":")
                append(JsonSupport.quote(cleanedName))
            }
            append("}")
        }

        executor.execute {
            val result = performPairingCall("$normalizedUrl/api/devices/pair", body, deviceToken = null)
            postResult { onResult(result) }
        }
    }

    fun rotateDeviceCredential(
        baseUrl: String,
        deviceToken: String,
        onResult: (PairingAttemptResult) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!DeviceCredentialStore.isValidTokenFormat(deviceToken)) {
            postResult {
                onResult(PairingAttemptResult.Rejected(401, "Missing or invalid local device credential."))
            }
            return
        }

        executor.execute {
            val result = performPairingCall("$normalizedUrl/api/devices/rotate", "{}", deviceToken = deviceToken)
            postResult { onResult(result) }
        }
    }

    fun uploadObservation(
        baseUrl: String,
        payload: ObservationPayload,
        deviceToken: String? = null,
        onResult: (UploadAttemptResult) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        val errors = payload.validationErrors()
        if (errors.isNotEmpty()) {
            postResult {
                onResult(UploadAttemptResult.ClientRejected(400, errors.first()))
            }
            return
        }

        executor.execute {
            val result = performUpload(normalizedUrl, payload.toJsonString(), deviceToken)
            postResult { onResult(result) }
        }
    }

    // -------------------------------------------------------------------------
    // User Account Authentication Endpoints (HTTP Cookie Auth, Separate from Device Auth)
    // -------------------------------------------------------------------------

    fun login(
        baseUrl: String,
        email: String,
        password: String,
        onResult: (ApiResult<LoginResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!ApiContractUtils.isValidEmail(cleanEmail) || password.isEmpty()) {
            postResult {
                onResult(ApiResult.ClientError(400, "Please enter a valid email address and password."))
            }
            return
        }

        val jsonBody = "{\"email\":${JsonSupport.quote(cleanEmail)},\"password\":${JsonSupport.quote(password)}}"
        executor.execute {
            val result = executeJsonRequest(
                method = "POST",
                endpointUrl = "$normalizedUrl/api/auth/login",
                jsonBody = jsonBody
            ) { code, headers, root ->
                val user = with(ApiJsonParsers) { parseAuthUser(root.obj("user")) }
                    ?: return@executeJsonRequest ApiResult.NetworkError("Login response missing user profile.")
                val nowMs = clock()
                val cookie = SessionCookieParser.extractCookie(
                    headers,
                    SessionCookieParser.SESSION_COOKIE_NAME,
                    nowMs = nowMs,
                    defaultTtlMs = SessionCookieParser.DEFAULT_SESSION_TTL_MS
                )
                if (cookie == null || cookie.isCleared || !SessionCookieParser.isValidSessionTokenFormat(cookie.value)) {
                    return@executeJsonRequest ApiResult.NetworkError("Server did not issue a valid session cookie.")
                }

                val session = UserSessionCredential(
                    sessionToken = cookie.value,
                    user = user,
                    expiresAtEpochMs = cookie.expiresAtEpochMs,
                    savedAtEpochMs = nowMs
                )
                ApiResult.Success(
                    data = LoginResponse(
                        user = user,
                        session = session,
                        message = root["message"] as? String ?: "Login successful."
                    ),
                    statusCode = code,
                    message = root["message"] as? String
                )
            }
            postResult { onResult(result) }
        }
    }

    fun startRegistration(
        baseUrl: String,
        name: String,
        email: String,
        onResult: (ApiResult<RegistrationStartResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!ApiContractUtils.isValidName(cleanName) || !ApiContractUtils.isValidEmail(cleanEmail)) {
            postResult {
                onResult(ApiResult.ClientError(400, "Please provide a valid name (2-60 chars) and email address."))
            }
            return
        }

        val jsonBody = "{\"name\":${JsonSupport.quote(cleanName)},\"email\":${JsonSupport.quote(cleanEmail)}}"
        executor.execute {
            val result = executeJsonRequest(
                method = "POST",
                endpointUrl = "$normalizedUrl/api/auth/register/start",
                jsonBody = jsonBody
            ) { code, _, root ->
                val cooldown = (root["resendCooldown"] as? Number)?.toLong()
                    ?: (root["retryAfter"] as? Number)?.toLong()
                ApiResult.Success(
                    data = RegistrationStartResponse(
                        name = root["name"] as? String ?: cleanName,
                        email = root["email"] as? String ?: cleanEmail,
                        message = root["message"] as? String ?: "Verification code sent successfully.",
                        resendCooldownSeconds = cooldown
                    ),
                    statusCode = code,
                    message = root["message"] as? String
                )
            }
            postResult { onResult(result) }
        }
    }

    fun verifyRegistrationCode(
        baseUrl: String,
        email: String,
        code: String,
        onResult: (ApiResult<VerifyCodeResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        val cleanEmail = email.trim().lowercase(Locale.US)
        val cleanCode = code.trim()
        if (!ApiContractUtils.isValidEmail(cleanEmail) || !ApiContractUtils.isValidOtpCode(cleanCode)) {
            postResult {
                onResult(ApiResult.ClientError(400, "Please enter a valid email and 6-digit verification code."))
            }
            return
        }

        val jsonBody = "{\"email\":${JsonSupport.quote(cleanEmail)},\"code\":${JsonSupport.quote(cleanCode)}}"
        executor.execute {
            val result = executeJsonRequest(
                method = "POST",
                endpointUrl = "$normalizedUrl/api/auth/register/verify",
                jsonBody = jsonBody
            ) { statusCode, headers, root ->
                val nowMs = clock()
                val cookie = SessionCookieParser.extractCookie(
                    headers,
                    SessionCookieParser.SETUP_COOKIE_NAME,
                    nowMs = nowMs,
                    defaultTtlMs = SessionCookieParser.DEFAULT_SETUP_TTL_MS
                )
                if (cookie == null || cookie.isCleared || !SessionCookieParser.isValidSetupTokenFormat(cookie.value)) {
                    return@executeJsonRequest ApiResult.NetworkError("Server did not issue a valid password-setup session cookie.")
                }

                @Suppress("UNCHECKED_CAST")
                val userMap = root["user"] as? Map<String, Any?>
                val resolvedName = (userMap?.get("name") as? String)?.trim().orEmpty()
                val resolvedEmail = (userMap?.get("email") as? String)?.trim()?.ifEmpty { cleanEmail } ?: cleanEmail

                val setupCred = PasswordSetupCredential(
                    setupToken = cookie.value,
                    name = resolvedName,
                    email = resolvedEmail,
                    expiresAtEpochMs = cookie.expiresAtEpochMs
                )
                ApiResult.Success(
                    data = VerifyCodeResponse(
                        name = resolvedName,
                        email = resolvedEmail,
                        setupCredential = setupCred,
                        message = root["message"] as? String ?: "Email verified. You can now create your password."
                    ),
                    statusCode = statusCode,
                    message = root["message"] as? String
                )
            }
            postResult { onResult(result) }
        }
    }

    fun getPasswordSetup(
        baseUrl: String,
        setupToken: String,
        onResult: (ApiResult<PasswordSetupProfile>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSetupTokenFormat(setupToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Your password-setup session is missing or expired."))
            }
            return
        }

        executor.execute {
            val result = executeJsonRequest(
                method = "GET",
                endpointUrl = "$normalizedUrl/api/auth/register/password-setup",
                cookieHeader = "${SessionCookieParser.SETUP_COOKIE_NAME}=$setupToken"
            ) { code, _, root ->
                @Suppress("UNCHECKED_CAST")
                val userMap = root["user"] as? Map<String, Any?>
                    ?: return@executeJsonRequest ApiResult.NetworkError("Missing setup user profile.")
                val email = (userMap["email"] as? String)?.trim()
                    ?: return@executeJsonRequest ApiResult.NetworkError("Missing setup user email.")
                val name = (userMap["name"] as? String)?.trim() ?: ""
                ApiResult.Success(
                    data = PasswordSetupProfile(name = name, email = email),
                    statusCode = code
                )
            }
            postResult { onResult(result) }
        }
    }

    fun createAccountPassword(
        baseUrl: String,
        setupToken: String,
        password: String,
        onResult: (ApiResult<CreatePasswordResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSetupTokenFormat(setupToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Your password-setup session is missing or expired."))
            }
            return
        }
        val passwordError = ApiContractUtils.validatePasswordStrength(password)
        if (passwordError != null) {
            postResult {
                onResult(ApiResult.ClientError(400, passwordError))
            }
            return
        }

        val jsonBody = "{\"password\":${JsonSupport.quote(password)}}"
        executor.execute {
            val result = executeJsonRequest(
                method = "POST",
                endpointUrl = "$normalizedUrl/api/auth/register/create-password",
                jsonBody = jsonBody,
                cookieHeader = "${SessionCookieParser.SETUP_COOKIE_NAME}=$setupToken"
            ) { code, _, root ->
                val user = with(ApiJsonParsers) { parseAuthUser(root.obj("user")) }
                    ?: return@executeJsonRequest ApiResult.NetworkError("Missing created user profile.")
                val message = root["message"] as? String
                    ?: "Your account has been created successfully. You can now log in."
                ApiResult.Success(
                    data = CreatePasswordResponse(user = user, message = message),
                    statusCode = code,
                    message = message
                )
            }
            postResult { onResult(result) }
        }
    }

    fun resendVerificationCode(
        baseUrl: String,
        email: String,
        onResult: (ApiResult<ResendCodeResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!ApiContractUtils.isValidEmail(cleanEmail)) {
            postResult {
                onResult(ApiResult.ClientError(400, "Please provide a valid email address."))
            }
            return
        }

        val jsonBody = "{\"email\":${JsonSupport.quote(cleanEmail)}}"
        executor.execute {
            val result = executeJsonRequest(
                method = "POST",
                endpointUrl = "$normalizedUrl/api/auth/resend-code",
                jsonBody = jsonBody
            ) { code, _, root ->
                val cooldown = (root["resendCooldown"] as? Number)?.toLong()
                    ?: (root["retryAfter"] as? Number)?.toLong()
                val msg = root["message"] as? String ?: "A new verification code has been sent."
                ApiResult.Success(
                    data = ResendCodeResponse(message = msg, resendCooldownSeconds = cooldown),
                    statusCode = code,
                    message = msg
                )
            }
            postResult { onResult(result) }
        }
    }

    fun getCurrentUser(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<AuthUser>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", "/api/auth/me", sessionToken) { code, _, root ->
                val user = with(ApiJsonParsers) { parseAuthUser(root.obj("user")) }
                    ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Response missing user profile.")
                ApiResult.Success(data = user, statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun logout(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<LogoutResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "No active session token."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "POST", "/api/auth/logout", sessionToken, jsonBody = "{}") { code, _, root ->
                val msg = root["message"] as? String ?: "Logged out successfully."
                ApiResult.Success(data = LogoutResponse(msg), statusCode = code, message = msg)
            }
            postResult { onResult(result) }
        }
    }

    fun logoutAllSessions(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<LogoutResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "No active session token."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "POST", "/api/auth/logout-all", sessionToken, jsonBody = "{}") { code, _, root ->
                val msg = root["message"] as? String ?: "Signed out of all active sessions."
                ApiResult.Success(data = LogoutResponse(msg), statusCode = code, message = msg)
            }
            postResult { onResult(result) }
        }
    }

    // -------------------------------------------------------------------------
    // Authenticated Dashboard & Telemetry APIs (User Session Cookie Auth)
    // -------------------------------------------------------------------------

    fun getObservations(
        baseUrl: String,
        sessionToken: String,
        page: Int = 1,
        limit: Int = 100,
        onResult: (ApiResult<ObservationListResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        if (page !in 1..10_000 || limit !in 1..100) {
            postResult {
                onResult(ApiResult.ClientError(400, "Invalid pagination parameters."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(
                normalizedUrl,
                "GET",
                "/api/observations?page=$page&limit=$limit",
                sessionToken
            ) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parseObservationList(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun getObservationStats(
        baseUrl: String,
        sessionToken: String,
        band: String? = null,
        onResult: (ApiResult<ObservationAnalytics>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        val cleanBand = band?.trim()?.takeIf { it.isNotEmpty() && !it.equals("all", ignoreCase = true) }
        if (cleanBand != null && cleanBand !in VALID_STATS_BANDS) {
            postResult {
                onResult(ApiResult.ClientError(400, "Invalid frequency band."))
            }
            return
        }

        val path = if (cleanBand != null) {
            "/api/observations/stats?band=${ApiContractUtils.encodeQueryParam(cleanBand)}"
        } else {
            "/api/observations/stats"
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", path, sessionToken) { code, _, root ->
                val analytics = ApiJsonParsers.parseObservationStats(root)
                    ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Response missing observation analytics.")
                ApiResult.Success(analytics, statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun getHeatmapData(
        baseUrl: String,
        sessionToken: String,
        band: String? = null,
        onResult: (ApiResult<HeatmapResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        val cleanBand = band?.trim()?.takeIf { it.isNotEmpty() && !it.equals("all", ignoreCase = true) }
        if (cleanBand != null && cleanBand !in VALID_HEATMAP_BANDS) {
            postResult {
                onResult(ApiResult.ClientError(400, "Invalid frequency band for heatmap."))
            }
            return
        }

        val path = if (cleanBand != null) {
            "/api/observations/heatmap?band=${ApiContractUtils.encodeQueryParam(cleanBand)}"
        } else {
            "/api/observations/heatmap"
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", path, sessionToken) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parseHeatmapResponse(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun getNetworkDensity(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<NetworkDensityResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", "/api/observations/density", sessionToken) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parseNetworkDensity(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun getNetworkQuality(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<NetworkQualityResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", "/api/quality", sessionToken) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parseNetworkQuality(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun runPerformanceTest(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<RunPerformanceTestResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(
                normalizedUrl,
                "POST",
                "/api/performance",
                sessionToken,
                jsonBody = "{}",
                readTimeoutMs = PERFORMANCE_TEST_READ_TIMEOUT_MS
            ) { code, _, root ->
                val parsed = ApiJsonParsers.parseRunPerformance(root)
                    ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Response missing performance measurement.")
                ApiResult.Success(parsed, statusCode = code, message = parsed.message)
            }
            postResult { onResult(result) }
        }
    }

    fun getPerformanceAnalytics(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<PerformanceAnalytics>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", "/api/performance/analytics", sessionToken) { code, _, root ->
                val analytics = ApiJsonParsers.parsePerformanceAnalytics(root)
                    ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Response missing performance analytics.")
                ApiResult.Success(analytics, statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun getPerformanceHistory(
        baseUrl: String,
        sessionToken: String,
        limit: Int = 50,
        onResult: (ApiResult<PerformanceHistoryResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        if (limit !in 1..100) {
            postResult {
                onResult(ApiResult.ClientError(400, "Limit must be between 1 and 100."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(
                normalizedUrl,
                "GET",
                "/api/performance/history?limit=$limit",
                sessionToken
            ) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parsePerformanceHistory(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun getPerformanceTrends(
        baseUrl: String,
        sessionToken: String,
        limit: Int = 50,
        onResult: (ApiResult<PerformanceTrendsResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        if (limit !in 1..100) {
            postResult {
                onResult(ApiResult.ClientError(400, "Limit must be between 1 and 100."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(
                normalizedUrl,
                "GET",
                "/api/performance/trends?limit=$limit",
                sessionToken
            ) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parsePerformanceTrends(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun listPairedDevices(
        baseUrl: String,
        sessionToken: String,
        onResult: (ApiResult<DeviceListResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(normalizedUrl, "GET", "/api/devices", sessionToken) { code, _, root ->
                ApiResult.Success(ApiJsonParsers.parseDeviceList(root), statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun generatePairingCode(
        baseUrl: String,
        sessionToken: String,
        platform: String = "android",
        onResult: (ApiResult<PairingCodeResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        val cleanPlatform = platform.trim().lowercase(Locale.US)
        if (cleanPlatform !in VALID_PLATFORMS) {
            postResult {
                onResult(ApiResult.ClientError(400, "Invalid pairing platform specified."))
            }
            return
        }

        val jsonBody = "{\"platform\":${JsonSupport.quote(cleanPlatform)}}"
        executor.execute {
            val result = executeAuthenticatedRequest(
                normalizedUrl,
                "POST",
                "/api/devices/pairing-code",
                sessionToken,
                jsonBody = jsonBody
            ) { code, _, root ->
                val pairing = ApiJsonParsers.parsePairingCode(root)
                    ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Response missing pairing code.")
                ApiResult.Success(pairing, statusCode = code)
            }
            postResult { onResult(result) }
        }
    }

    fun revokePairedDevice(
        baseUrl: String,
        sessionToken: String,
        deviceId: String,
        onResult: (ApiResult<RevokeDeviceResponse>) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        if (!SessionCookieParser.isValidSessionTokenFormat(sessionToken)) {
            postResult {
                onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            }
            return
        }
        val cleanId = deviceId.trim()
        if (!ApiContractUtils.isValidObjectId(cleanId)) {
            postResult {
                onResult(ApiResult.ClientError(400, "Invalid device identifier."))
            }
            return
        }

        executor.execute {
            val result = executeAuthenticatedRequest(
                normalizedUrl,
                "DELETE",
                "/api/devices/$cleanId",
                sessionToken
            ) { code, _, root ->
                val revoked = ApiJsonParsers.parseRevokeDevice(root)
                    ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Response missing revoked device details.")
                ApiResult.Success(revoked, statusCode = code, message = revoked.message)
            }
            postResult { onResult(result) }
        }
    }

    fun fetchSystemMonitoringSnapshot(
        baseUrl: String,
        sessionToken: String,
        onResult: (SystemMonitoringSnapshot) -> Unit
    ) {
        if (isShutdown) return
        val normalizedUrl = ApiContractUtils.normalizeBaseUrl(baseUrl)
        executor.execute {
            val health = performHealthCheck(normalizedUrl)
            val hasValidSession = SessionCookieParser.isValidSessionTokenFormat(sessionToken)

            val obsRes: ApiResult<ObservationListResponse>
            val qualityRes: ApiResult<NetworkQualityResponse>
            val perfHistRes: ApiResult<PerformanceHistoryResponse>
            val obsStatsRes: ApiResult<ObservationAnalytics>
            val perfStatsRes: ApiResult<PerformanceAnalytics>

            if (!hasValidSession) {
                val unauth = ApiResult.Unauthorized(401, "Sign in required")
                obsRes = unauth
                qualityRes = unauth
                perfHistRes = unauth
                obsStatsRes = unauth
                perfStatsRes = unauth
            } else {
                obsRes = executeAuthenticatedRequest(
                    normalizedUrl,
                    "GET",
                    "/api/observations?page=1&limit=100",
                    sessionToken
                ) { code, _, root ->
                    ApiResult.Success(ApiJsonParsers.parseObservationList(root), statusCode = code)
                }
                qualityRes = if (isShutdown) {
                    ApiResult.NetworkError("Cancelled")
                } else {
                    executeAuthenticatedRequest(normalizedUrl, "GET", "/api/quality", sessionToken) { code, _, root ->
                        ApiResult.Success(ApiJsonParsers.parseNetworkQuality(root), statusCode = code)
                    }
                }
                perfHistRes = if (isShutdown) {
                    ApiResult.NetworkError("Cancelled")
                } else {
                    executeAuthenticatedRequest(normalizedUrl, "GET", "/api/performance/history?limit=10", sessionToken) { code, _, root ->
                        ApiResult.Success(ApiJsonParsers.parsePerformanceHistory(root), statusCode = code)
                    }
                }
                obsStatsRes = if (isShutdown) {
                    ApiResult.NetworkError("Cancelled")
                } else {
                    executeAuthenticatedRequest(normalizedUrl, "GET", "/api/observations/stats", sessionToken) { code, _, root ->
                        val analytics = ApiJsonParsers.parseObservationStats(root)
                            ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Missing observation analytics.")
                        ApiResult.Success(analytics, statusCode = code)
                    }
                }
                perfStatsRes = if (isShutdown) {
                    ApiResult.NetworkError("Cancelled")
                } else {
                    executeAuthenticatedRequest(normalizedUrl, "GET", "/api/performance/analytics", sessionToken) { code, _, root ->
                        val analytics = ApiJsonParsers.parsePerformanceAnalytics(root)
                            ?: return@executeAuthenticatedRequest ApiResult.NetworkError("Missing performance analytics.")
                        ApiResult.Success(analytics, statusCode = code)
                    }
                }
            }

            val endpoints = listOf(
                EndpointProbeStatus(
                    id = "health",
                    label = "Core API Health",
                    endpoint = "GET /api/health",
                    available = health.reachable && health.healthy,
                    detail = if (health.reachable) {
                        "${health.service ?: "WiFiVision"} · DB: ${health.databaseStatus ?: "unknown"}"
                    } else {
                        health.message
                    }
                ),
                EndpointProbeStatus(
                    id = "observations",
                    label = "Observations Feed",
                    endpoint = "GET /api/observations",
                    available = obsRes is ApiResult.Success,
                    detail = when (obsRes) {
                        is ApiResult.Success -> "${obsRes.data.count} observation(s) returned"
                        else -> extractFailureMessage(obsRes)
                    }
                ),
                EndpointProbeStatus(
                    id = "quality",
                    label = "Quality Scoring Engine",
                    endpoint = "GET /api/quality",
                    available = qualityRes is ApiResult.Success,
                    detail = when (qualityRes) {
                        is ApiResult.Success -> "${qualityRes.data.count} scored network(s)"
                        else -> extractFailureMessage(qualityRes)
                    }
                ),
                EndpointProbeStatus(
                    id = "performance",
                    label = "Performance History",
                    endpoint = "GET /api/performance/history",
                    available = perfHistRes is ApiResult.Success,
                    detail = when (perfHistRes) {
                        is ApiResult.Success -> "${perfHistRes.data.count} recent test(s)"
                        else -> extractFailureMessage(perfHistRes)
                    }
                )
            )

            val snapshot = SystemMonitoringSnapshot(
                health = health,
                endpoints = endpoints,
                recentObservations = (obsRes as? ApiResult.Success)?.data?.observations?.take(8) ?: emptyList(),
                recentPerformance = (perfHistRes as? ApiResult.Success)?.data?.history?.take(8) ?: emptyList(),
                observationAnalytics = (obsStatsRes as? ApiResult.Success)?.data,
                performanceAnalytics = (perfStatsRes as? ApiResult.Success)?.data,
                checkedAtEpochMs = clock()
            )
            postResult { onResult(snapshot) }
        }
    }

    private fun extractFailureMessage(result: ApiResult<*>): String {
        return when (result) {
            is ApiResult.Unauthorized -> result.message
            is ApiResult.Forbidden -> result.message
            is ApiResult.RateLimited -> result.message
            is ApiResult.ClientError -> result.message
            is ApiResult.ServerError -> result.message
            is ApiResult.NetworkError -> result.message
            is ApiResult.Success -> "Available"
        }
    }

    // -------------------------------------------------------------------------
    // Internal HTTP Transport Helpers
    // -------------------------------------------------------------------------

    private fun <T> executeAuthenticatedRequest(
        normalizedBaseUrl: String,
        method: String,
        pathWithQuery: String,
        sessionToken: String,
        jsonBody: String? = null,
        readTimeoutMs: Int = API_READ_TIMEOUT_MS,
        mapper: (Int, Map<String?, List<String>>, Map<String, Any?>) -> ApiResult<T>
    ): ApiResult<T> {
        return executeJsonRequest(
            method = method,
            endpointUrl = "$normalizedBaseUrl$pathWithQuery",
            jsonBody = jsonBody,
            cookieHeader = "${SessionCookieParser.SESSION_COOKIE_NAME}=$sessionToken",
            readTimeoutMs = readTimeoutMs,
            mapper = mapper
        )
    }

    private fun <T> executeJsonRequest(
        method: String,
        endpointUrl: String,
        jsonBody: String? = null,
        cookieHeader: String? = null,
        connectTimeoutMs: Int = API_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = API_READ_TIMEOUT_MS,
        mapper: (Int, Map<String?, List<String>>, Map<String, Any?>) -> ApiResult<T>
    ): ApiResult<T> {
        if (endpointUrl.isBlank() || (!endpointUrl.startsWith("https://") && !endpointUrl.startsWith("http://"))) {
            return ApiResult.NetworkError("API base URL must start with https:// or http://")
        }

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
            }

            if (jsonBody != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val code = connection.responseCode
            val headers = connection.headerFields ?: emptyMap()
            val rawBody = readBody(connection, code)
            val parsed = parseJsonMap(rawBody)
            val message = parsed?.get("message") as? String ?: "HTTP $code"
            val errorCode = parsed?.get("code") as? String

            when {
                code in 200..299 -> {
                    if (parsed == null) {
                        ApiResult.NetworkError("Malformed response returned by backend.")
                    } else if (parsed["success"] == false) {
                        ApiResult.ClientError(code, message)
                    } else {
                        mapper(code, headers, parsed)
                    }
                }
                code == 401 -> ApiResult.Unauthorized(code, message, errorCode)
                code == 403 -> ApiResult.Forbidden(code, message)
                code == 429 -> {
                    val retryAfter = ApiContractUtils.parseRetryAfterSeconds(connection.getHeaderField("Retry-After"))
                        ?: (parsed?.get("retryAfterSeconds") as? Number)?.toLong()?.takeIf { it > 0L }
                        ?: (parsed?.get("retryAfter") as? Number)?.toLong()?.takeIf { it > 0L }
                    ApiResult.RateLimited(retryAfter, message)
                }
                code in 400..499 -> ApiResult.ClientError(code, message)
                else -> ApiResult.ServerError(code, message)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Network request failed")
        } finally {
            connection?.disconnect()
        }
    }

    private fun performHealthCheck(baseUrl: String): HealthCheckResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/api/health")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HEALTH_CONNECT_TIMEOUT_MS
                readTimeout = HEALTH_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            val body = readBody(connection, code)
            val parsed = parseJsonMap(body)
            val dbStatus = parsed?.get("database") as? String
            val service = parsed?.get("service") as? String
            val status = parsed?.get("status") as? String
            val timestamp = parsed?.get("timestamp") as? String
            val message = parsed?.get("message") as? String
                ?: status
                ?: "HTTP $code"

            when (code) {
                200 -> HealthCheckResult(
                    reachable = true,
                    healthy = true,
                    statusCode = code,
                    databaseStatus = dbStatus,
                    message = "Connected (${dbStatus ?: "healthy"})",
                    service = service,
                    status = status,
                    timestamp = timestamp
                )
                503 -> HealthCheckResult(
                    reachable = true,
                    healthy = false,
                    statusCode = code,
                    databaseStatus = dbStatus,
                    message = "Backend degraded (database ${dbStatus ?: "disconnected"})",
                    service = service,
                    status = status,
                    timestamp = timestamp
                )
                else -> HealthCheckResult(
                    reachable = true,
                    healthy = false,
                    statusCode = code,
                    databaseStatus = dbStatus,
                    message = "Unexpected response ($message)",
                    service = service,
                    status = status,
                    timestamp = timestamp
                )
            }
        } catch (e: Exception) {
            HealthCheckResult(
                reachable = false,
                healthy = false,
                statusCode = null,
                databaseStatus = null,
                message = "Unreachable: ${e.message ?: "connection failed"}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun performPairingCall(
        endpointUrl: String,
        jsonBody: String,
        deviceToken: String?
    ): PairingAttemptResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = UPLOAD_CONNECT_TIMEOUT_MS
                readTimeout = UPLOAD_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (!deviceToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $deviceToken")
                    setRequestProperty("X-WiFiVision-Timestamp", System.currentTimeMillis().toString())
                    setRequestProperty("X-WiFiVision-Nonce", ApiContractUtils.generateRequestNonce())
                }
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val code = connection.responseCode
            val body = readBody(connection, code)
            val parsed = parseJsonMap(body)
            val message = parsed?.get("message") as? String ?: "HTTP $code"

            if (code in 200..299 && parsed != null) {
                val issuedToken = parsed["deviceToken"] as? String
                val deviceMap = parsed["device"] as? Map<String, Any?>
                val deviceId = deviceMap?.get("deviceId") as? String
                val deviceName = deviceMap?.get("deviceName") as? String ?: "Android Companion"
                val expiresAt = deviceMap?.get("expiresAt") as? String

                if (DeviceCredentialStore.isValidTokenFormat(issuedToken) &&
                    !deviceId.isNullOrBlank() &&
                    !expiresAt.isNullOrBlank()
                ) {
                    PairingAttemptResult.Success(
                        credential = PairedDeviceCredential(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            deviceToken = issuedToken!!,
                            expiresAtIso = expiresAt
                        ),
                        message = message
                    )
                } else {
                    PairingAttemptResult.NetworkError("Invalid pairing credential returned by backend.")
                }
            } else if (code in 400..499) {
                PairingAttemptResult.Rejected(code, message)
            } else {
                PairingAttemptResult.NetworkError("Server error (HTTP $code): $message")
            }
        } catch (e: Exception) {
            PairingAttemptResult.NetworkError(e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }

    private fun performUpload(
        baseUrl: String,
        jsonBody: String,
        deviceToken: String?
    ): UploadAttemptResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/api/observations")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = UPLOAD_CONNECT_TIMEOUT_MS
                readTimeout = UPLOAD_READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (!deviceToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $deviceToken")
                    setRequestProperty("X-WiFiVision-Timestamp", System.currentTimeMillis().toString())
                    setRequestProperty("X-WiFiVision-Nonce", ApiContractUtils.generateRequestNonce())
                }
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val code = connection.responseCode
            val body = readBody(connection, code)
            val parsed = parseJsonMap(body)
            val message = parsed?.get("message") as? String ?: "HTTP $code"
            val errorCode = parsed?.get("code") as? String

            when {
                code in 200..299 -> UploadAttemptResult.Success(code, message)
                code == 401 -> UploadAttemptResult.Unauthorized(code, errorCode, message)
                code == 429 -> {
                    val retryAfter = ApiContractUtils.parseRetryAfterSeconds(
                        connection.getHeaderField("Retry-After")
                    )
                    UploadAttemptResult.RateLimited(retryAfter, message)
                }
                code in 400..499 -> UploadAttemptResult.ClientRejected(code, message)
                else -> UploadAttemptResult.TransientFailure(code, message)
            }
        } catch (e: Exception) {
            UploadAttemptResult.TransientFailure(
                statusCode = null,
                message = e.message ?: "Network error"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..399) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonMap(raw: String): Map<String, Any?>? {
        if (raw.isBlank()) return null
        return try {
            JsonSupport.parseJson(raw) as? Map<String, Any?>
        } catch (_: Exception) {
            null
        }
    }

    fun shutdown() {
        isShutdown = true
        executor.shutdownNow()
    }
}
