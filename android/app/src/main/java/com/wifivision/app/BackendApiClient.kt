package com.wifivision.app

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
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
    val message: String
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
 * Pure URL validator, pairing code validator, nonce generator, and HTTP response
 * classifier (unit-testable on JVM) plus HttpURLConnection client for WiFiVision APIs.
 */
object ApiContractUtils {

    private val PAIRING_CODE_REGEX = Regex("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6,8}$")
    private val NONCE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-"
    private val secureRandom = SecureRandom()

    fun validateBaseUrl(rawUrl: String, allowCleartext: Boolean): UrlValidationResult {
        val trimmed = rawUrl.trim().trimEnd('/')
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
    private val resultDispatcher: ((Runnable) -> Unit)? = null
) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
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

    fun checkHealth(baseUrl: String, onResult: (HealthCheckResult) -> Unit) {
        if (isShutdown) return
        executor.execute {
            val result = performHealthCheck(baseUrl)
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
            val result = performPairingCall("$baseUrl/api/devices/pair", body, deviceToken = null)
            postResult { onResult(result) }
        }
    }

    fun rotateDeviceCredential(
        baseUrl: String,
        deviceToken: String,
        onResult: (PairingAttemptResult) -> Unit
    ) {
        if (isShutdown) return
        if (!DeviceCredentialStore.isValidTokenFormat(deviceToken)) {
            postResult {
                onResult(PairingAttemptResult.Rejected(401, "Missing or invalid local device credential."))
            }
            return
        }

        executor.execute {
            val result = performPairingCall("$baseUrl/api/devices/rotate", "{}", deviceToken = deviceToken)
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
        val errors = payload.validationErrors()
        if (errors.isNotEmpty()) {
            postResult {
                onResult(UploadAttemptResult.ClientRejected(400, errors.first()))
            }
            return
        }

        executor.execute {
            val result = performUpload(baseUrl, payload.toJsonString(), deviceToken)
            postResult { onResult(result) }
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
            val message = parsed?.get("message") as? String
                ?: parsed?.get("status") as? String
                ?: "HTTP $code"

            when (code) {
                200 -> HealthCheckResult(
                    reachable = true,
                    healthy = true,
                    statusCode = code,
                    databaseStatus = dbStatus,
                    message = "Connected (${dbStatus ?: "healthy"})"
                )
                503 -> HealthCheckResult(
                    reachable = true,
                    healthy = false,
                    statusCode = code,
                    databaseStatus = dbStatus,
                    message = "Backend degraded (database ${dbStatus ?: "disconnected"})"
                )
                else -> HealthCheckResult(
                    reachable = true,
                    healthy = false,
                    statusCode = code,
                    databaseStatus = dbStatus,
                    message = "Unexpected response ($message)"
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
