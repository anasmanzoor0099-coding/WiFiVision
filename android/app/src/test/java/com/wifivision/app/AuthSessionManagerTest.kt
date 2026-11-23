package com.wifivision.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthSessionManagerTest {

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

    private data class MockHttpResponse(
        val statusCode: Int,
        val statusText: String,
        val body: String,
        val headers: List<Pair<String, String>> = emptyList()
    )

    private data class RecordedHttpRequest(
        val method: String,
        val path: String,
        val body: String,
        val headers: Map<String, String>
    )

    private lateinit var serverSocket: ServerSocket
    private lateinit var baseUrl: String
    private lateinit var client: BackendApiClient
    private lateinit var sessionStorage: InMemoryStorage
    private lateinit var deviceStorage: InMemoryStorage
    private lateinit var sessionStore: SessionCredentialStore
    private lateinit var deviceStore: DeviceCredentialStore
    private lateinit var manager: AuthSessionManager

    private val currentClockMs = AtomicLong(1_700_000_000_000L)
    private val serverExecutor = Executors.newCachedThreadPool()
    private val receivedRequests = mutableListOf<RecordedHttpRequest>()

    @Volatile
    private var handler: ((RecordedHttpRequest) -> MockHttpResponse)? = null

    @Volatile
    private var running = true

    @Before
    fun setUp() {
        serverSocket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        val clockFn = { currentClockMs.get() }
        client = BackendApiClient(
            resultDispatcher = { runnable -> runnable.run() },
            clock = clockFn
        )
        val cipher = JvmAesGcmCipher()
        sessionStorage = InMemoryStorage()
        deviceStorage = InMemoryStorage()
        sessionStore = SessionCredentialStore(sessionStorage, cipher)
        deviceStore = DeviceCredentialStore(deviceStorage, cipher)
        manager = AuthSessionManager(client, sessionStore, clockFn)

        serverExecutor.execute {
            while (running && !serverSocket.isClosed) {
                try {
                    val socket = serverSocket.accept()
                    serverExecutor.execute { handleSocket(socket) }
                } catch (_: Exception) {
                }
            }
        }
    }

    @After
    fun tearDown() {
        running = false
        client.shutdown()
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
        serverExecutor.shutdownNow()
    }

    private fun handleSocket(socket: java.net.Socket) {
        socket.use { s ->
            val input = BufferedInputStream(s.getInputStream())
            val headerBytes = ArrayList<Byte>()
            while (true) {
                val b = input.read()
                if (b == -1) return
                headerBytes.add(b.toByte())
                val n = headerBytes.size
                if (n >= 4 &&
                    headerBytes[n - 4] == '\r'.code.toByte() &&
                    headerBytes[n - 3] == '\n'.code.toByte() &&
                    headerBytes[n - 2] == '\r'.code.toByte() &&
                    headerBytes[n - 1] == '\n'.code.toByte()
                ) {
                    break
                }
            }

            val headerText = String(headerBytes.toByteArray(), StandardCharsets.UTF_8)
            val lines = headerText.split("\r\n")
            val requestLineParts = lines.firstOrNull()?.split(" ") ?: emptyList()
            val method = requestLineParts.getOrElse(0) { "GET" }
            val path = requestLineParts.getOrElse(1) { "/" }

            val contentLength = lines
                .firstOrNull { it.lowercase().startsWith("content-length:") }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?: 0

            val bodyBuffer = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val read = input.read(bodyBuffer, totalRead, contentLength - totalRead)
                if (read == -1) break
                totalRead += read
            }
            val body = String(bodyBuffer, 0, totalRead, StandardCharsets.UTF_8)
            val parsedHeaders = LinkedHashMap<String, String>()
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    parsedHeaders[line.substring(0, idx).trim().lowercase()] =
                        line.substring(idx + 1).trim()
                }
            }
            val recorded = RecordedHttpRequest(method, path, body, parsedHeaders)
            synchronized(receivedRequests) { receivedRequests.add(recorded) }

            val resp = handler?.invoke(recorded) ?: MockHttpResponse(404, "Not Found", "{}")
            val respBytes = resp.body.toByteArray(StandardCharsets.UTF_8)
            val responseHeaders = buildString {
                append("HTTP/1.1 ${resp.statusCode} ${resp.statusText}\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${respBytes.size}\r\n")
                append("Connection: close\r\n")
                resp.headers.forEach { (k, v) -> append("$k: $v\r\n") }
                append("\r\n")
            }

            val out = s.getOutputStream()
            out.write(responseHeaders.toByteArray(StandardCharsets.UTF_8))
            out.write(respBytes)
            out.flush()
        }
    }

    private fun sampleJwt(expSeconds: Long = 1_900_000_000L): String {
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val header = b64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray(StandardCharsets.UTF_8))
        val payload = b64.encodeToString("""{"sub":"65f000000000000000000001","exp":$expSeconds}""".toByteArray(StandardCharsets.UTF_8))
        val sig = b64.encodeToString("sig_bytes_ok".toByteArray(StandardCharsets.UTF_8))
        return "$header.$payload.$sig"
    }

    @Test
    fun login_persistsEncryptedSessionOnSuccess_andFailsCleanlyOn401Or403OrMissingCookie() {
        val jwt = sampleJwt()
        val attempt = AtomicInteger(0)
        handler = { req ->
            assertEquals("POST", req.method)
            assertEquals("/api/auth/login", req.path)
            assertNull(req.headers["authorization"])
            when (attempt.incrementAndGet()) {
                1 -> MockHttpResponse(401, "Unauthorized", """{"success":false,"message":"Invalid email or password."}""")
                2 -> MockHttpResponse(403, "Forbidden", """{"success":false,"message":"Please complete email verification and account setup first."}""")
                3 -> MockHttpResponse(
                    200,
                    "OK",
                    """{"success":true,"message":"Login successful.","user":{"id":"65f000000000000000000001","name":"Alice","email":"alice@example.com","isEmailVerified":true}}"""
                ) // missing Set-Cookie!
                else -> MockHttpResponse(
                    200,
                    "OK",
                    """{"success":true,"message":"Login successful.","user":{"id":"65f000000000000000000001","name":"Alice","email":"alice@example.com","isEmailVerified":true}}""",
                    headers = listOf("Set-Cookie" to "wifivision_token=$jwt; Max-Age=86400; Path=/; HttpOnly")
                )
            }
        }

        // Client-side validation rejects bad email before hitting network
        var badInputRes: ApiResult<LoginResponse>? = null
        manager.login(baseUrl, "not-an-email", "secret") { badInputRes = it }
        assertTrue(badInputRes is ApiResult.ClientError)
        assertEquals(0, receivedRequests.size)

        // 1. 401 Invalid credentials
        val latch1 = CountDownLatch(1)
        var res1: ApiResult<LoginResponse>? = null
        manager.login(baseUrl, "Alice@Example.com", "WrongPass1!") { res1 = it; latch1.countDown() }
        assertTrue(latch1.await(5, TimeUnit.SECONDS))
        assertTrue(res1 is ApiResult.Unauthorized)
        assertFalse(manager.isSignedIn())

        // 2. 403 Unverified
        val latch2 = CountDownLatch(1)
        var res2: ApiResult<LoginResponse>? = null
        manager.login(baseUrl, "alice@example.com", "Pass123!") { res2 = it; latch2.countDown() }
        assertTrue(latch2.await(5, TimeUnit.SECONDS))
        assertTrue(res2 is ApiResult.Forbidden)
        assertFalse(manager.isSignedIn())

        // 3. 200 without Set-Cookie fails closed
        val latch3 = CountDownLatch(1)
        var res3: ApiResult<LoginResponse>? = null
        manager.login(baseUrl, "alice@example.com", "Pass123!") { res3 = it; latch3.countDown() }
        assertTrue(latch3.await(5, TimeUnit.SECONDS))
        assertTrue(res3 is ApiResult.NetworkError)
        assertFalse(manager.isSignedIn())

        // 4. Success persists encrypted session
        val latch4 = CountDownLatch(1)
        var res4: ApiResult<LoginResponse>? = null
        manager.login("$baseUrl/", "  Alice@Example.com ", "Pass123!") { res4 = it; latch4.countDown() }
        assertTrue(latch4.await(5, TimeUnit.SECONDS))
        assertTrue(res4 is ApiResult.Success)
        assertTrue(manager.isSignedIn())
        assertEquals("alice@example.com", manager.getActiveSession()?.user?.email)
        val sentJson = JsonSupport.parseJson(receivedRequests.last().body) as Map<*, *>
        assertEquals("alice@example.com", sentJson["email"])
    }

    @Test
    fun registrationFlow_otpVerify_resendRateLimit_passwordSetupAndCreation_workEndToEnd() {
        val setupToken = "c".repeat(64)
        handler = { req ->
            when {
                req.method == "POST" && req.path == "/api/auth/register/start" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"message":"Verification code sent successfully.","email":"bob@example.com","name":"Bob Builder"}"""
                    )
                req.method == "POST" && req.path == "/api/auth/resend-code" ->
                    MockHttpResponse(
                        429,
                        "Too Many Requests",
                        """{"success":false,"message":"Please wait before requesting another code.","retryAfter":45}"""
                    )
                req.method == "POST" && req.path == "/api/auth/register/verify" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"message":"Email verified.","user":{"name":"Bob Builder","email":"bob@example.com"}}""",
                        headers = listOf("Set-Cookie" to "wifivision_setup_token=$setupToken; Max-Age=900; Path=/; HttpOnly")
                    )
                req.method == "GET" && req.path == "/api/auth/register/password-setup" -> {
                    assertEquals("wifivision_setup_token=$setupToken", req.headers["cookie"])
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"user":{"name":"Bob Builder","email":"bob@example.com"}}"""
                    )
                }
                req.method == "POST" && req.path == "/api/auth/register/create-password" -> {
                    assertEquals("wifivision_setup_token=$setupToken", req.headers["cookie"])
                    MockHttpResponse(
                        201,
                        "Created",
                        """{"success":true,"message":"Your account has been created successfully.","user":{"id":"65f000000000000000000002","name":"Bob Builder","email":"bob@example.com","isEmailVerified":true}}""",
                        headers = listOf("Set-Cookie" to "wifivision_setup_token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                    )
                }
                else -> MockHttpResponse(404, "Not Found", "{}")
            }
        }

        // Validation guards before network calls
        var invalidName: ApiResult<RegistrationStartResponse>? = null
        manager.startRegistration(baseUrl, "A", "bob@example.com") { invalidName = it }
        assertTrue(invalidName is ApiResult.ClientError)

        var invalidOtp: ApiResult<VerifyCodeResponse>? = null
        manager.verifyRegistrationCode(baseUrl, "bob@example.com", "12a456") { invalidOtp = it }
        assertTrue(invalidOtp is ApiResult.ClientError)

        // 1. Start registration
        val l1 = CountDownLatch(1)
        var r1: ApiResult<RegistrationStartResponse>? = null
        manager.startRegistration(baseUrl, "Bob Builder", "bob@example.com") { r1 = it; l1.countDown() }
        assertTrue(l1.await(5, TimeUnit.SECONDS))
        assertTrue(r1 is ApiResult.Success)
        assertEquals("Bob Builder", (r1 as ApiResult.Success).data.name)

        // 2. Resend 429 extracts retryAfter from JSON body
        val l2 = CountDownLatch(1)
        var r2: ApiResult<ResendCodeResponse>? = null
        manager.resendVerificationCode(baseUrl, "bob@example.com") { r2 = it; l2.countDown() }
        assertTrue(l2.await(5, TimeUnit.SECONDS))
        assertTrue(r2 is ApiResult.RateLimited)
        assertEquals(45L, (r2 as ApiResult.RateLimited).retryAfterSeconds)

        // 3. Verify 6-digit OTP persists encrypted setup credential
        val l3 = CountDownLatch(1)
        var r3: ApiResult<VerifyCodeResponse>? = null
        manager.verifyRegistrationCode(baseUrl, "bob@example.com", "123456") { r3 = it; l3.countDown() }
        assertTrue(l3.await(5, TimeUnit.SECONDS))
        assertTrue(r3 is ApiResult.Success)
        assertEquals(setupToken, manager.getActivePasswordSetup()?.setupToken)

        // 4. Get password setup sends setup cookie
        val l4 = CountDownLatch(1)
        var r4: ApiResult<PasswordSetupProfile>? = null
        manager.getPasswordSetup(baseUrl) { r4 = it; l4.countDown() }
        assertTrue(l4.await(5, TimeUnit.SECONDS))
        assertTrue(r4 is ApiResult.Success)
        assertEquals("bob@example.com", (r4 as ApiResult.Success).data.email)

        // 5. Weak password rejected client-side without hitting backend
        var weakPwd: ApiResult<CreatePasswordResponse>? = null
        manager.createAccountPassword(baseUrl, "weakpass") { weakPwd = it }
        assertTrue(weakPwd is ApiResult.ClientError)
        assertNotNull(manager.getActivePasswordSetup()) // still preserved for retry

        // 6. Strong password completes registration and clears setup cookie
        val l6 = CountDownLatch(1)
        var r6: ApiResult<CreatePasswordResponse>? = null
        manager.createAccountPassword(baseUrl, "StrongPass!2026") { r6 = it; l6.countDown() }
        assertTrue(l6.await(5, TimeUnit.SECONDS))
        assertTrue(r6 is ApiResult.Success)
        assertEquals("bob@example.com", (r6 as ApiResult.Success).data.user.email)
        assertNull(manager.getActivePasswordSetup())
    }

    @Test
    fun refreshProfile_handlesUpdate401RevocationAndLocalExpiry() {
        val jwt = sampleJwt()
        val nowMs = currentClockMs.get()
        sessionStore.saveSession(
            UserSessionCredential(
                sessionToken = jwt,
                user = AuthUser("65f000000000000000000001", "Old Name", "alice@example.com", true),
                expiresAtEpochMs = nowMs + 3_600_000L,
                savedAtEpochMs = nowMs
            )
        )

        val callCount = AtomicInteger(0)
        handler = { req ->
            assertEquals("GET", req.method)
            assertEquals("/api/auth/me", req.path)
            assertEquals("wifivision_token=$jwt", req.headers["cookie"])
            if (callCount.incrementAndGet() == 1) {
                MockHttpResponse(
                    200,
                    "OK",
                    """{"success":true,"user":{"id":"65f000000000000000000001","name":"Verified Alice","email":"alice@example.com","isEmailVerified":true}}"""
                )
            } else {
                MockHttpResponse(401, "Unauthorized", """{"success":false,"message":"Your session has been revoked."}""")
            }
        }

        // 1. 200 updates cached user
        val l1 = CountDownLatch(1)
        var r1: ApiResult<AuthUser>? = null
        manager.refreshProfile(baseUrl) { r1 = it; l1.countDown() }
        assertTrue(l1.await(5, TimeUnit.SECONDS))
        assertTrue(r1 is ApiResult.Success)
        assertEquals("Verified Alice", manager.getActiveSession()?.user?.name)

        // 2. Server 401 revokes local session
        val l2 = CountDownLatch(1)
        var r2: ApiResult<AuthUser>? = null
        manager.refreshProfile(baseUrl) { r2 = it; l2.countDown() }
        assertTrue(l2.await(5, TimeUnit.SECONDS))
        assertTrue(r2 is ApiResult.Unauthorized)
        assertFalse(manager.isSignedIn())

        // 3. Expired session clears locally and returns Unauthorized without network request
        sessionStore.saveSession(
            UserSessionCredential(
                sessionToken = jwt,
                user = AuthUser("65f000000000000000000001", "Alice", "alice@example.com", true),
                expiresAtEpochMs = nowMs + 10_000L,
                savedAtEpochMs = nowMs
            )
        )
        currentClockMs.set(nowMs + 20_000L)
        val countBefore = receivedRequests.size
        var expiredRes: ApiResult<AuthUser>? = null
        manager.refreshProfile(baseUrl) { expiredRes = it }
        assertTrue(expiredRes is ApiResult.Unauthorized)
        assertNull(sessionStore.loadSession())
        assertEquals(countBefore, receivedRequests.size)
    }

    @Test
    fun logoutAndLogoutAll_sendSessionCookie_clearLocalSessionAlways_andPreserveDeviceCredential() {
        val jwt = sampleJwt()
        val nowMs = currentClockMs.get()
        val pairedDevice = PairedDeviceCredential(
            deviceId = "65f0000000000000000000cc",
            deviceName = "My Phone",
            deviceToken = "wvd_65f0000000000000000000cc_${"e".repeat(64)}",
            expiresAtIso = "2099-01-01T00:00:00Z"
        )
        assertTrue(deviceStore.saveCredential(pairedDevice))

        fun seedSession() {
            assertTrue(
                sessionStore.saveSession(
                    UserSessionCredential(
                        sessionToken = jwt,
                        user = AuthUser("65f000000000000000000001", "Alice", "alice@example.com", true),
                        expiresAtEpochMs = nowMs + 3_600_000L,
                        savedAtEpochMs = nowMs
                    )
                )
            )
        }

        handler = { req ->
            when (req.path) {
                "/api/auth/logout" -> {
                    assertEquals("POST", req.method)
                    assertEquals("wifivision_token=$jwt", req.headers["cookie"])
                    MockHttpResponse(200, "OK", """{"success":true,"message":"Logged out successfully."}""")
                }
                "/api/auth/logout-all" -> {
                    assertEquals("POST", req.method)
                    assertEquals("wifivision_token=$jwt", req.headers["cookie"])
                    MockHttpResponse(200, "OK", """{"success":true,"message":"Signed out of all active sessions."}""")
                }
                else -> MockHttpResponse(404, "Not Found", "{}")
            }
        }

        // 1. Standard logout
        seedSession()
        val l1 = CountDownLatch(1)
        var r1: ApiResult<LogoutResponse>? = null
        manager.logout(baseUrl) { r1 = it; l1.countDown() }
        assertTrue(l1.await(5, TimeUnit.SECONDS))
        assertTrue(r1 is ApiResult.Success)
        assertNull(sessionStore.loadSession())
        assertEquals(pairedDevice, deviceStore.getActiveCredential())

        // 2. Logout all sessions
        seedSession()
        val l2 = CountDownLatch(1)
        var r2: ApiResult<LogoutResponse>? = null
        manager.logoutAllSessions(baseUrl) { r2 = it; l2.countDown() }
        assertTrue(l2.await(5, TimeUnit.SECONDS))
        assertTrue(r2 is ApiResult.Success)
        assertNull(sessionStore.loadSession())
        assertEquals(pairedDevice, deviceStore.getActiveCredential())

        // 3. Offline failure during logout-all still keeps local session cleared
        seedSession()
        running = false
        serverSocket.close()
        val l3 = CountDownLatch(1)
        var r3: ApiResult<LogoutResponse>? = null
        manager.logoutAllSessions(baseUrl) { r3 = it; l3.countDown() }
        assertTrue(l3.await(5, TimeUnit.SECONDS))
        assertTrue(r3 is ApiResult.NetworkError)
        assertNull(sessionStore.loadSession())
        assertEquals(pairedDevice, deviceStore.getActiveCredential())
    }
}
