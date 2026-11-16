package com.wifivision.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BackendApiClientIntegrationTest {

    private data class MockHttpResponse(
        val statusCode: Int,
        val statusText: String,
        val body: String,
        val headers: Map<String, String> = emptyMap()
    )

    private data class RecordedHttpRequest(
        val method: String,
        val path: String,
        val body: String
    )

    private lateinit var serverSocket: ServerSocket
    private lateinit var baseUrl: String
    private lateinit var client: BackendApiClient
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
        client = BackendApiClient(resultDispatcher = { runnable -> runnable.run() })

        serverExecutor.execute {
            while (running && !serverSocket.isClosed) {
                try {
                    val socket = serverSocket.accept()
                    serverExecutor.execute { handleSocket(socket) }
                } catch (_: Exception) {
                    // Socket closed during teardown
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
            val recorded = RecordedHttpRequest(method, path, body)
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

    private fun validPayload(): ObservationPayload {
        return ObservationPayload(
            ssid = "Lab_WiFi",
            bssid = "AA:BB:CC:11:22:33",
            signalDbm = -54,
            frequencyMhz = 5180,
            band = "5 GHz",
            security = "WPA3",
            signalQuality = 4,
            location = ObservationLocation(29.3956, 71.6836, 9.5),
            observedAtEpochMs = 1_700_000_000_000L
        )
    }

    @Test
    fun checkHealth_handlesHealthyDegradedAndOffline() {
        val callCount = AtomicInteger(0)
        handler = { req ->
            assertEquals("GET", req.method)
            assertEquals("/api/health", req.path)
            if (callCount.incrementAndGet() == 1) {
                MockHttpResponse(200, "OK", """{"success":true,"status":"healthy","database":"connected"}""")
            } else {
                MockHttpResponse(503, "Service Unavailable", """{"success":false,"status":"degraded","database":"disconnected"}""")
            }
        }

        val healthyLatch = CountDownLatch(1)
        var healthyResult: HealthCheckResult? = null
        client.checkHealth(baseUrl) {
            healthyResult = it
            healthyLatch.countDown()
        }
        assertTrue(healthyLatch.await(5, TimeUnit.SECONDS))
        assertTrue(healthyResult!!.reachable)
        assertTrue(healthyResult!!.healthy)
        assertEquals("connected", healthyResult!!.databaseStatus)

        val degradedLatch = CountDownLatch(1)
        var degradedResult: HealthCheckResult? = null
        client.checkHealth(baseUrl) {
            degradedResult = it
            degradedLatch.countDown()
        }
        assertTrue(degradedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(degradedResult!!.reachable)
        assertFalse(degradedResult!!.healthy)
        assertEquals(503, degradedResult!!.statusCode)

        // Simulate backend unavailable
        running = false
        serverSocket.close()
        val offlineLatch = CountDownLatch(1)
        var offlineResult: HealthCheckResult? = null
        client.checkHealth(baseUrl) {
            offlineResult = it
            offlineLatch.countDown()
        }
        assertTrue(offlineLatch.await(5, TimeUnit.SECONDS))
        assertFalse(offlineResult!!.reachable)
        assertFalse(offlineResult!!.healthy)
    }

    @Test
    fun uploadObservation_handlesSuccess400Rejection429AndRecovery() {
        val attemptCounter = AtomicInteger(0)
        handler = { req ->
            assertEquals("POST", req.method)
            assertEquals("/api/observations", req.path)
            when (attemptCounter.incrementAndGet()) {
                1 -> MockHttpResponse(503, "Service Unavailable", """{"success":false,"message":"Temporary unavailable"}""")
                2 -> MockHttpResponse(
                    429,
                    "Too Many Requests",
                    """{"success":false,"message":"Too many observations submitted."}""",
                    headers = mapOf("Retry-After" to "30")
                )
                3 -> MockHttpResponse(201, "Created", """{"success":true,"message":"Network observation saved successfully."}""")
                else -> MockHttpResponse(400, "Bad Request", """{"success":false,"message":"Invalid observation data."}""")
            }
        }

        val queue = ObservationQueueLogic()
        val t0 = 10_000_000L
        queue.enqueueObservations(listOf(validPayload()), t0)
        val queuedItem = queue.peekNextReady(t0)!!

        // 1. Transient 503 -> retained in queue with backoff
        val latch1 = CountDownLatch(1)
        var res1: UploadAttemptResult? = null
        client.uploadObservation(baseUrl, queuedItem.payload) {
            res1 = it
            latch1.countDown()
        }
        assertTrue(latch1.await(5, TimeUnit.SECONDS))
        assertTrue(res1 is UploadAttemptResult.TransientFailure)
        assertTrue(queue.markTransientFailure(queuedItem.id, t0))
        assertEquals(1, queue.size())

        // 2. HTTP 429 -> honors Retry-After and pauses queue
        val latch2 = CountDownLatch(1)
        var res2: UploadAttemptResult? = null
        client.uploadObservation(baseUrl, queuedItem.payload) {
            res2 = it
            latch2.countDown()
        }
        assertTrue(latch2.await(5, TimeUnit.SECONDS))
        assertTrue(res2 is UploadAttemptResult.RateLimited)
        assertEquals(30L, (res2 as UploadAttemptResult.RateLimited).retryAfterSeconds)
        val pauseMs = queue.markRateLimited(30L, t0 + 10_000L)
        assertTrue(pauseMs >= 60_000L)
        assertEquals(1, queue.size())

        // 3. Recovery 201 -> item dequeued and JSON matches backend contract
        val recoveredItem = queue.peekNextReady(t0 + 10_000L + pauseMs)
        assertNotNull(recoveredItem)
        val latch3 = CountDownLatch(1)
        var res3: UploadAttemptResult? = null
        client.uploadObservation(baseUrl, recoveredItem!!.payload) {
            res3 = it
            latch3.countDown()
        }
        assertTrue(latch3.await(5, TimeUnit.SECONDS))
        assertTrue(res3 is UploadAttemptResult.Success)
        assertTrue(queue.markSuccess(recoveredItem.id, t0 + 10_000L + pauseMs))
        assertEquals(0, queue.size())

        val sentJson = JsonSupport.parseJson(receivedRequests.first().body) as Map<*, *>
        assertEquals("Lab_WiFi", sentJson["ssid"])
        assertEquals("AA:BB:CC:11:22:33", sentJson["bssid"])
        assertEquals(-54L, sentJson["signalDbm"])
        assertEquals(5180L, sentJson["frequencyMhz"])
        assertEquals("android", sentJson["source"])

        // 4. Invalid payload rejected client-side before network send
        val invalidLatch = CountDownLatch(1)
        var invalidRes: UploadAttemptResult? = null
        client.uploadObservation(baseUrl, validPayload().copy(signalDbm = -200)) {
            invalidRes = it
            invalidLatch.countDown()
        }
        assertTrue(invalidLatch.await(5, TimeUnit.SECONDS))
        assertTrue(invalidRes is UploadAttemptResult.ClientRejected)
        assertEquals(3, receivedRequests.size)
    }
}
