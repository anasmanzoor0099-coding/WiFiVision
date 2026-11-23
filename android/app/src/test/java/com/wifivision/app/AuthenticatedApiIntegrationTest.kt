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

class AuthenticatedApiIntegrationTest {

    private data class MockHttpResponse(
        val statusCode: Int,
        val statusText: String,
        val body: String,
        val headers: Map<String, String> = emptyMap()
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

    private fun sampleSessionJwt(): String {
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val header = b64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray(StandardCharsets.UTF_8))
        val payload = b64.encodeToString("""{"sub":"65f000000000000000000001","exp":4102444800}""".toByteArray(StandardCharsets.UTF_8))
        val sig = b64.encodeToString("valid_sig_bytes".toByteArray(StandardCharsets.UTF_8))
        return "$header.$payload.$sig"
    }

    private fun <T> awaitResult(block: ((ApiResult<T>) -> Unit) -> Unit): ApiResult<T> {
        val latch = CountDownLatch(1)
        var captured: ApiResult<T>? = null
        block { res ->
            captured = res
            latch.countDown()
        }
        assertTrue("Timed out awaiting API result", latch.await(5, TimeUnit.SECONDS))
        return captured!!
    }

    @Test
    fun observations_stats_heatmap_density_and_quality_endpoints_parseAndEnforceCookieAuth() {
        val jwt = sampleSessionJwt()
        handler = { req ->
            assertEquals("wifivision_token=$jwt", req.headers["cookie"])
            assertNull("User routes must never receive Bearer device token", req.headers["authorization"])

            when {
                req.method == "GET" && req.path == "/api/observations?page=2&limit=50" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """
                        {
                          "success": true,
                          "count": 1,
                          "totalCount": 51,
                          "page": 2,
                          "limit": 50,
                          "hasMore": false,
                          "observations": [
                            {
                              "_id": "65f111111111111111111111",
                              "ssid": "Studio_5G",
                              "signalDbm": -52,
                              "frequencyMhz": 5180,
                              "band": "5 GHz",
                              "security": "WPA3",
                              "signalQuality": 4,
                              "location": {"latitude": 29.395, "longitude": 71.683, "accuracyMeters": 11.5},
                              "source": "android",
                              "observedAt": "2026-11-21T10:00:00.000Z"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                req.method == "GET" && req.path == "/api/observations/stats?band=5%20GHz" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """
                        {
                          "success": true,
                          "analytics": {
                            "filteredBand": "5 GHz",
                            "totalNetworks": 8,
                            "averageSignal": -58,
                            "strongestSignal": -41,
                            "weakestSignal": -77,
                            "bands": [{"_id": "5 GHz", "count": 8}],
                            "security": [{"_id": "WPA3", "count": 5}, {"_id": null, "count": 3}]
                          }
                        }
                        """.trimIndent()
                    )
                req.method == "GET" && req.path == "/api/observations/heatmap?band=2.4%20GHz" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """
                        {
                          "success": true,
                          "count": 1,
                          "heatmap": [
                            {
                              "latitude": 29.396,
                              "longitude": 71.684,
                              "totalObservations": 4,
                              "averageSignal": -62.5,
                              "strongestSignal": -51,
                              "weakestSignal": -74,
                              "avgAccuracyMeters": 12.4,
                              "accuracySampleCount": 4,
                              "missingAccuracyCount": 0,
                              "coarseAccuracyCount": 0,
                              "frequencyDistribution": [
                                {"band": "2.4 GHz", "count": 4, "averageSignal": -62.5, "strongestSignal": -51, "weakestSignal": -74}
                              ]
                            }
                          ],
                          "accuracySummary": {
                            "averageAccuracyMeters": 12.4,
                            "withAccuracyCount": 4,
                            "withoutAccuracyCount": 0,
                            "coarseAccuracyCount": 0
                          },
                          "locationSummary": {
                            "geolocatedObservations": 4,
                            "nonGeolocatedObservations": 1,
                            "totalObservations": 5
                          }
                        }
                        """.trimIndent()
                    )
                req.method == "GET" && req.path == "/api/observations/density" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """
                        {
                          "success": true,
                          "count": 1,
                          "density": [
                            {
                              "latitude": 29.396,
                              "longitude": 71.684,
                              "totalObservations": 4,
                              "uniqueNetworkCount": 3,
                              "averageSignal": -62.5,
                              "densityLevel": "Medium",
                              "frequencyDistribution": [{"band": "2.4 GHz", "count": 4}]
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                req.method == "GET" && req.path == "/api/quality" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """
                        {
                          "success": true,
                          "count": 1,
                          "averageScore": 91,
                          "networks": [
                            {
                              "observationId": "65f111111111111111111111",
                              "ssid": "Studio_5G",
                              "quality": {
                                "score": 91,
                                "classification": "Excellent",
                                "components": {"signalScore": 85, "signalLevelScore": 100, "bandScore": 100, "securityScore": 100},
                                "details": {"signalDbm": -52, "band": "5 GHz", "security": "WPA3"}
                              },
                              "observedAt": "2026-11-21T10:00:00.000Z"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                else -> MockHttpResponse(404, "Not Found", """{"success":false,"message":"Not found"}""")
            }
        }

        // Observations list
        val obsRes = awaitResult<ObservationListResponse> { client.getObservations("$baseUrl/", jwt, page = 2, limit = 50, onResult = it) }
        assertTrue(obsRes is ApiResult.Success)
        val obsList = (obsRes as ApiResult.Success).data
        assertEquals(51, obsList.totalCount)
        assertEquals("Studio_5G", obsList.observations.first().ssid)
        assertEquals(11.5, obsList.observations.first().location!!.accuracyMeters!!, 0.001)

        // Observation stats with encoded band filter
        val statsRes = awaitResult<ObservationAnalytics> { client.getObservationStats(baseUrl, jwt, band = "5 GHz", onResult = it) }
        assertTrue(statsRes is ApiResult.Success)
        val stats = (statsRes as ApiResult.Success).data
        assertEquals(8, stats.totalNetworks)
        assertEquals("Unknown", stats.security.last().label)

        // Heatmap rejects "Unknown" band upfront
        val invalidHeatmap = awaitResult<HeatmapResponse> { client.getHeatmapData(baseUrl, jwt, band = "Unknown", onResult = it) }
        assertTrue(invalidHeatmap is ApiResult.ClientError)

        // Heatmap 2.4 GHz
        val hmRes = awaitResult<HeatmapResponse> { client.getHeatmapData(baseUrl, jwt, band = "2.4 GHz", onResult = it) }
        assertTrue(hmRes is ApiResult.Success)
        val hm = (hmRes as ApiResult.Success).data
        assertEquals(1, hm.heatmap.size)
        assertEquals(12.4, hm.accuracySummary!!.averageAccuracyMeters!!, 0.01)
        assertEquals(1, hm.locationSummary!!.nonGeolocatedObservations)

        // Density
        val densRes = awaitResult<NetworkDensityResponse> { client.getNetworkDensity(baseUrl, jwt, onResult = it) }
        assertTrue(densRes is ApiResult.Success)
        assertEquals("Medium", (densRes as ApiResult.Success).data.density.first().densityLevel)

        // Quality
        val qualRes = awaitResult<NetworkQualityResponse> { client.getNetworkQuality(baseUrl, jwt, onResult = it) }
        assertTrue(qualRes is ApiResult.Success)
        val qData = (qualRes as ApiResult.Success).data
        assertEquals(91, qData.averageScore)
        assertEquals(100, qData.networks.first().quality.components.securityScore)
    }

    @Test
    fun performanceAndDeviceManagementEndpoints_workCorrectly() {
        val jwt = sampleSessionJwt()
        val sampleDeviceId = "65f0000000000000000000aa"
        val emptyAnalyticsCall = booleanArrayOf(false)

        handler = { req ->
            assertEquals("wifivision_token=$jwt", req.headers["cookie"])
            assertNull(req.headers["authorization"])
            when {
                req.method == "POST" && req.path == "/api/performance" ->
                    MockHttpResponse(
                        201,
                        "Created",
                        """
                        {
                          "success": true,
                          "message": "Performance measured and saved successfully.",
                          "savedMeasurementId": "65f222222222222222222222",
                          "performance": {
                            "latencyMs": 28.4,
                            "medianLatencyMs": 27.0,
                            "jitterMs": 2.1,
                            "downloadMbps": 92.6,
                            "uploadMbps": 38.4,
                            "measuredAt": "2026-11-21T11:00:00.000Z",
                            "packetLoss": {
                              "totalPackets": 20,
                              "successfulPackets": 20,
                              "lostPackets": 0,
                              "packetLossPercentage": 0.0,
                              "averageResponseMs": 26.8,
                              "measurementType": "HTTP application-level request success"
                            }
                          }
                        }
                        """.trimIndent()
                    )
                req.method == "GET" && req.path == "/api/performance/analytics" -> {
                    if (!emptyAnalyticsCall[0]) {
                        emptyAnalyticsCall[0] = true
                        MockHttpResponse(
                            200,
                            "OK",
                            """{"success":true,"analytics":{"totalTests":0,"message":"No performance measurements available."}}"""
                        )
                    } else {
                        MockHttpResponse(
                            200,
                            "OK",
                            """{"success":true,"analytics":{"totalTests":4,"averageDownloadMbps":88.5,"averageUploadMbps":34.2,"averageLatencyMs":31.1,"averageMedianLatencyMs":29.4,"averageJitterMs":2.8,"averagePacketLossPercentage":0.5,"fastestDownloadMbps":110.2,"slowestDownloadMbps":61.0,"fastestUploadMbps":42.1,"latestMeasurement":"2026-11-21T11:00:00.000Z"}}"""
                        )
                    }
                }
                req.method == "GET" && req.path == "/api/performance/history?limit=20" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"count":1,"history":[{"_id":"65f222222222222222222222","latencyMs":28.4,"medianLatencyMs":27.0,"jitterMs":2.1,"downloadMbps":92.6,"uploadMbps":38.4,"packetLoss":{"totalPackets":20,"successfulPackets":20,"lostPackets":0,"packetLossPercentage":0.0,"averageResponseMs":26.8,"measurementType":"HTTP"},"source":"backend","testServer":"Cloudflare","measuredAt":"2026-11-21T11:00:00.000Z"}]}"""
                    )
                req.method == "GET" && req.path == "/api/performance/trends?limit=20" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"count":1,"trends":[{"latencyMs":28.4,"medianLatencyMs":27.0,"jitterMs":2.1,"downloadMbps":92.6,"uploadMbps":38.4,"packetLossPercentage":0.0,"source":"backend","measuredAt":"2026-11-21T11:00:00.000Z"}]}"""
                    )
                req.method == "GET" && req.path == "/api/devices" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"count":1,"devices":[{"deviceId":"$sampleDeviceId","deviceName":"TECNO KN4","platform":"android","scopes":["observations:write"],"status":"active","revokedAt":null,"expiresAt":"2027-02-01T00:00:00.000Z","lastUsedAt":null,"createdAt":"2026-11-21T08:00:00.000Z"}]}"""
                    )
                req.method == "POST" && req.path == "/api/devices/pairing-code" ->
                    MockHttpResponse(
                        201,
                        "Created",
                        """{"success":true,"pairingCode":"ABCD2345","platform":"windows","expiresAt":"2026-11-21T11:05:00.000Z","expiresInSeconds":300}"""
                    )
                req.method == "DELETE" && req.path == "/api/devices/$sampleDeviceId" ->
                    MockHttpResponse(
                        200,
                        "OK",
                        """{"success":true,"message":"Device revoked immediately.","device":{"deviceId":"$sampleDeviceId","deviceName":"TECNO KN4","platform":"android","status":"revoked","revokedAt":"2026-11-21T11:01:00.000Z"}}"""
                    )
                else -> MockHttpResponse(404, "Not Found", "{}")
            }
        }

        // Performance empty analytics
        val emptyPerf = awaitResult<PerformanceAnalytics> { client.getPerformanceAnalytics(baseUrl, jwt, it) }
        assertTrue(emptyPerf is ApiResult.Success)
        assertEquals(0, (emptyPerf as ApiResult.Success).data.totalTests)
        assertNull(emptyPerf.data.averageDownloadMbps)

        // Populated performance analytics
        val popPerf = awaitResult<PerformanceAnalytics> { client.getPerformanceAnalytics(baseUrl, jwt, it) }
        assertEquals(88.5, (popPerf as ApiResult.Success).data.averageDownloadMbps!!, 0.01)

        // Run speed test
        val runRes = awaitResult<RunPerformanceTestResponse> { client.runPerformanceTest(baseUrl, jwt, it) }
        assertTrue(runRes is ApiResult.Success)
        assertEquals(92.6, (runRes as ApiResult.Success).data.performance.downloadMbps, 0.01)
        assertEquals(20, runRes.data.performance.packetLoss.totalPackets)

        // History & trends
        val histRes = awaitResult<PerformanceHistoryResponse> { client.getPerformanceHistory(baseUrl, jwt, limit = 20, onResult = it) }
        assertEquals("Cloudflare", (histRes as ApiResult.Success).data.history.first().testServer)

        val trendRes = awaitResult<PerformanceTrendsResponse> { client.getPerformanceTrends(baseUrl, jwt, limit = 20, onResult = it) }
        assertEquals(38.4, (trendRes as ApiResult.Success).data.trends.first().uploadMbps, 0.01)

        // Device list, generate pairing code, revoke (and reject path traversal)
        val devList = awaitResult<DeviceListResponse> { client.listPairedDevices(baseUrl, jwt, it) }
        assertEquals("TECNO KN4", (devList as ApiResult.Success).data.devices.first().deviceName)

        val codeRes = awaitResult<PairingCodeResponse> { client.generatePairingCode(baseUrl, jwt, "windows", it) }
        assertEquals("ABCD2345", (codeRes as ApiResult.Success).data.pairingCode)
        assertEquals("windows", codeRes.data.platform)

        val badRevoke = awaitResult<RevokeDeviceResponse> { client.revokePairedDevice(baseUrl, jwt, "../etc/passwd", it) }
        assertTrue(badRevoke is ApiResult.ClientError)

        val revokeRes = awaitResult<RevokeDeviceResponse> { client.revokePairedDevice(baseUrl, jwt, sampleDeviceId, it) }
        assertTrue(revokeRes is ApiResult.Success)
        assertEquals("revoked", (revokeRes as ApiResult.Success).data.status)
    }

    @Test
    fun systemMonitoringAndAuthSeparation_verifyPartialAvailabilityAndDeviceUploadBoundary() {
        val jwt = sampleSessionJwt()
        val deviceToken = "wvd_65f0000000000000000000aa_${"d".repeat(64)}"

        handler = { req ->
            when {
                req.method == "GET" && req.path == "/api/health" ->
                    MockHttpResponse(200, "OK", """{"success":true,"status":"healthy","service":"WiFiVision Backend","database":"connected","timestamp":"2026-11-21T12:00:00Z"}""")
                req.method == "GET" && req.path.startsWith("/api/observations?") ->
                    MockHttpResponse(200, "OK", """{"success":true,"count":1,"totalCount":1,"page":1,"limit":100,"hasMore":false,"observations":[{"_id":"65f1","ssid":"Office","signalDbm":-60,"frequencyMhz":2437,"band":"2.4 GHz","security":"WPA2","source":"windows","observedAt":"2026-11-21T12:00:00Z"}]}""")
                req.method == "GET" && req.path == "/api/quality" ->
                    MockHttpResponse(503, "Service Unavailable", """{"success":false,"message":"Scoring degraded"}""")
                req.method == "GET" && req.path.startsWith("/api/performance/history") ->
                    MockHttpResponse(200, "OK", """{"success":true,"count":0,"history":[]}""")
                req.method == "GET" && req.path == "/api/observations/stats" ->
                    MockHttpResponse(200, "OK", """{"success":true,"analytics":{"totalNetworks":1,"averageSignal":-60,"strongestSignal":-60,"weakestSignal":-60,"bands":[],"security":[]}}""")
                req.method == "GET" && req.path == "/api/performance/analytics" ->
                    MockHttpResponse(200, "OK", """{"success":true,"analytics":{"totalTests":0}}""")
                req.method == "POST" && req.path == "/api/observations" -> {
                    assertEquals("Bearer $deviceToken", req.headers["authorization"])
                    assertNull("Device upload must never send user session Cookie", req.headers["cookie"])
                    assertNotNull(req.headers["x-wifivision-timestamp"])
                    assertNotNull(req.headers["x-wifivision-nonce"])
                    MockHttpResponse(201, "Created", """{"success":true,"message":"Saved"}""")
                }
                else -> MockHttpResponse(404, "Not Found", "{}")
            }
        }

        // System monitoring detects partial endpoint degradation honestly
        val monLatch = CountDownLatch(1)
        var snapshot: SystemMonitoringSnapshot? = null
        client.fetchSystemMonitoringSnapshot(baseUrl, jwt) {
            snapshot = it
            monLatch.countDown()
        }
        assertTrue(monLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(snapshot)
        assertTrue(snapshot!!.health.healthy)
        assertTrue(snapshot!!.endpoints.first { it.id == "health" }.available)
        assertTrue(snapshot!!.endpoints.first { it.id == "observations" }.available)
        assertFalse(snapshot!!.endpoints.first { it.id == "quality" }.available)
        assertTrue(snapshot!!.endpoints.first { it.id == "performance" }.available)
        assertEquals(1, snapshot!!.recentObservations.size)

        // Device observation upload still uses Bearer + Timestamp + Nonce and NO session Cookie
        val uploadLatch = CountDownLatch(1)
        var uploadRes: UploadAttemptResult? = null
        val payload = ObservationPayload(
            ssid = "Lab_AP",
            bssid = "AA:BB:CC:DD:EE:FF",
            signalDbm = -58,
            frequencyMhz = 2437,
            band = "2.4 GHz",
            security = "WPA2",
            signalQuality = 3,
            location = ObservationLocation(29.39, 71.68, 8.0),
            observedAtEpochMs = 1_700_000_000_000L
        )
        client.uploadObservation(baseUrl, payload, deviceToken) {
            uploadRes = it
            uploadLatch.countDown()
        }
        assertTrue(uploadLatch.await(5, TimeUnit.SECONDS))
        assertTrue(uploadRes is UploadAttemptResult.Success)
    }
}
