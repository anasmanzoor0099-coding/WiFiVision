package com.wifivision.app

import java.util.Locale

/**
 * Coordinates user account registration, email OTP verification, login,
 * profile refresh, and logout/logout-all flows using Keystore-encrypted
 * [SessionCredentialStore].
 *
 * Strictly separate from [DeviceCredentialStore] so paired-device upload
 * credentials (`wvd_...`) are never modified or mixed with user sessions.
 */
class AuthSessionManager(
    private val apiClient: BackendApiClient,
    private val sessionStore: SessionCredentialStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun getActiveSession(): UserSessionCredential? {
        val loaded = sessionStore.loadSession() ?: return null
        if (loaded.isExpired(clock())) {
            sessionStore.clearSession()
            return null
        }
        return loaded
    }

    fun isSignedIn(): Boolean = getActiveSession() != null

    fun getActivePasswordSetup(): PasswordSetupCredential? {
        val loaded = sessionStore.loadPasswordSetup() ?: return null
        if (loaded.isExpired(clock())) {
            sessionStore.clearPasswordSetup()
            return null
        }
        return loaded
    }

    fun login(
        baseUrl: String,
        email: String,
        password: String,
        onResult: (ApiResult<LoginResponse>) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!ApiContractUtils.isValidEmail(cleanEmail) || password.isEmpty()) {
            onResult(ApiResult.ClientError(400, "Please enter a valid email address and password."))
            return
        }

        apiClient.login(baseUrl, cleanEmail, password) { result ->
            when (result) {
                is ApiResult.Success -> {
                    val persisted = sessionStore.saveSession(result.data.session)
                    if (!persisted) {
                        onResult(ApiResult.NetworkError("Failed to securely encrypt and persist session."))
                    } else {
                        onResult(result)
                    }
                }
                else -> onResult(result)
            }
        }
    }

    fun startRegistration(
        baseUrl: String,
        name: String,
        email: String,
        onResult: (ApiResult<RegistrationStartResponse>) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!ApiContractUtils.isValidName(cleanName) || !ApiContractUtils.isValidEmail(cleanEmail)) {
            onResult(ApiResult.ClientError(400, "Please provide a valid name (2-60 chars) and email address."))
            return
        }
        apiClient.startRegistration(baseUrl, cleanName, cleanEmail, onResult)
    }

    fun verifyRegistrationCode(
        baseUrl: String,
        email: String,
        code: String,
        onResult: (ApiResult<VerifyCodeResponse>) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        val cleanCode = code.trim()
        if (!ApiContractUtils.isValidEmail(cleanEmail) || !ApiContractUtils.isValidOtpCode(cleanCode)) {
            onResult(ApiResult.ClientError(400, "Please enter a valid email and 6-digit verification code."))
            return
        }

        apiClient.verifyRegistrationCode(baseUrl, cleanEmail, cleanCode) { result ->
            when (result) {
                is ApiResult.Success -> {
                    val saved = sessionStore.savePasswordSetup(result.data.setupCredential)
                    if (!saved) {
                        onResult(ApiResult.NetworkError("Failed to securely persist password-setup session."))
                    } else {
                        onResult(result)
                    }
                }
                else -> onResult(result)
            }
        }
    }

    fun resendVerificationCode(
        baseUrl: String,
        email: String,
        onResult: (ApiResult<ResendCodeResponse>) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!ApiContractUtils.isValidEmail(cleanEmail)) {
            onResult(ApiResult.ClientError(400, "Please provide a valid email address."))
            return
        }
        apiClient.resendVerificationCode(baseUrl, cleanEmail, onResult)
    }

    fun getPasswordSetup(
        baseUrl: String,
        onResult: (ApiResult<PasswordSetupProfile>) -> Unit
    ) {
        val setup = getActivePasswordSetup()
        if (setup == null) {
            sessionStore.clearPasswordSetup()
            onResult(ApiResult.Unauthorized(401, "Your password-setup session is missing or expired."))
            return
        }

        apiClient.getPasswordSetup(baseUrl, setup.setupToken) { result ->
            if (result is ApiResult.Unauthorized) {
                sessionStore.clearPasswordSetup()
            }
            onResult(result)
        }
    }

    fun createAccountPassword(
        baseUrl: String,
        password: String,
        onResult: (ApiResult<CreatePasswordResponse>) -> Unit
    ) {
        val strengthError = ApiContractUtils.validatePasswordStrength(password)
        if (strengthError != null) {
            onResult(ApiResult.ClientError(400, strengthError))
            return
        }

        val setup = getActivePasswordSetup()
        if (setup == null) {
            sessionStore.clearPasswordSetup()
            onResult(ApiResult.Unauthorized(401, "Your password-setup session is missing or expired."))
            return
        }

        apiClient.createAccountPassword(baseUrl, setup.setupToken, password) { result ->
            if (result is ApiResult.Success || result is ApiResult.Unauthorized) {
                sessionStore.clearPasswordSetup()
            }
            onResult(result)
        }
    }

    fun refreshProfile(
        baseUrl: String,
        onResult: (ApiResult<AuthUser>) -> Unit
    ) {
        val activeSession = getActiveSession()
        if (activeSession == null) {
            onResult(ApiResult.Unauthorized(401, "Authentication required. Please log in."))
            return
        }

        apiClient.getCurrentUser(baseUrl, activeSession.sessionToken) { result ->
            when (result) {
                is ApiResult.Success -> {
                    sessionStore.updateUser(result.data, clock())
                    onResult(result)
                }
                is ApiResult.Unauthorized -> {
                    sessionStore.clearSession()
                    onResult(result)
                }
                else -> onResult(result)
            }
        }
    }

    fun logout(
        baseUrl: String,
        onResult: (ApiResult<LogoutResponse>) -> Unit
    ) {
        val token = sessionStore.loadSession()?.sessionToken
        sessionStore.clearSession()

        if (token.isNullOrBlank() || !SessionCookieParser.isValidSessionTokenFormat(token)) {
            onResult(ApiResult.Success(LogoutResponse("Logged out successfully."), 200, "Logged out successfully."))
            return
        }

        apiClient.logout(baseUrl, token) { result ->
            sessionStore.clearSession()
            onResult(result)
        }
    }

    fun logoutAllSessions(
        baseUrl: String,
        onResult: (ApiResult<LogoutResponse>) -> Unit
    ) {
        val token = sessionStore.loadSession()?.sessionToken
        sessionStore.clearSession()

        if (token.isNullOrBlank() || !SessionCookieParser.isValidSessionTokenFormat(token)) {
            onResult(ApiResult.Success(LogoutResponse("Signed out locally."), 200, "Signed out locally."))
            return
        }

        apiClient.logoutAllSessions(baseUrl, token) { result ->
            sessionStore.clearSession()
            onResult(result)
        }
    }

    fun onUnauthorizedDetected() {
        sessionStore.clearSession()
    }
}
