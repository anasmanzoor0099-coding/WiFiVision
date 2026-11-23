package com.wifivision.app

/**
 * Typed HTTP/API outcome wrapper used for account authentication and
 * authenticated backend API calls (separate from device-scoped upload results).
 */
sealed class ApiResult<out T> {
    data class Success<T>(
        val data: T,
        val statusCode: Int,
        val message: String? = null
    ) : ApiResult<T>()

    data class Unauthorized(
        val statusCode: Int,
        val message: String,
        val code: String? = null
    ) : ApiResult<Nothing>()

    data class Forbidden(
        val statusCode: Int,
        val message: String
    ) : ApiResult<Nothing>()

    data class RateLimited(
        val retryAfterSeconds: Long?,
        val message: String
    ) : ApiResult<Nothing>()

    data class ClientError(
        val statusCode: Int,
        val message: String
    ) : ApiResult<Nothing>()

    data class ServerError(
        val statusCode: Int,
        val message: String
    ) : ApiResult<Nothing>()

    data class NetworkError(
        val message: String
    ) : ApiResult<Nothing>()
}

data class AuthUser(
    val id: String,
    val name: String,
    val email: String,
    val isEmailVerified: Boolean
)

data class LoginResponse(
    val user: AuthUser,
    val session: UserSessionCredential,
    val message: String
)

data class RegistrationStartResponse(
    val name: String,
    val email: String,
    val message: String,
    val resendCooldownSeconds: Long? = null
)

data class VerifyCodeResponse(
    val name: String,
    val email: String,
    val setupCredential: PasswordSetupCredential,
    val message: String
)

data class PasswordSetupProfile(
    val name: String,
    val email: String
)

data class CreatePasswordResponse(
    val user: AuthUser,
    val message: String
)

data class ResendCodeResponse(
    val message: String,
    val resendCooldownSeconds: Long? = null
)

data class LogoutResponse(
    val message: String
)

data class CloudObservation(
    val id: String,
    val ssid: String,
    val signalDbm: Int,
    val frequencyMhz: Int,
    val band: String,
    val security: String,
    val signalQuality: Int?,
    val location: ObservationLocation?,
    val source: String,
    val observedAt: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ObservationListResponse(
    val count: Int,
    val totalCount: Int,
    val page: Int,
    val limit: Int,
    val hasMore: Boolean,
    val observations: List<CloudObservation>
)

data class CategoryCount(
    val label: String,
    val count: Int
)

data class ObservationAnalytics(
    val filteredBand: String?,
    val totalNetworks: Int,
    val averageSignal: Double?,
    val strongestSignal: Int?,
    val weakestSignal: Int?,
    val bands: List<CategoryCount>,
    val security: List<CategoryCount>
)

data class HeatmapBandDistribution(
    val band: String,
    val count: Int,
    val averageSignal: Double?,
    val strongestSignal: Int?,
    val weakestSignal: Int?
)

data class HeatmapPoint(
    val latitude: Double,
    val longitude: Double,
    val totalObservations: Int,
    val averageSignal: Double,
    val strongestSignal: Int,
    val weakestSignal: Int,
    val avgAccuracyMeters: Double?,
    val accuracySampleCount: Int,
    val missingAccuracyCount: Int,
    val coarseAccuracyCount: Int,
    val frequencyDistribution: List<HeatmapBandDistribution>
)

data class HeatmapAccuracySummary(
    val averageAccuracyMeters: Double?,
    val withAccuracyCount: Int,
    val withoutAccuracyCount: Int,
    val coarseAccuracyCount: Int
)

data class HeatmapLocationSummary(
    val geolocatedObservations: Int,
    val nonGeolocatedObservations: Int,
    val totalObservations: Int
)

data class HeatmapResponse(
    val count: Int,
    val heatmap: List<HeatmapPoint>,
    val accuracySummary: HeatmapAccuracySummary?,
    val locationSummary: HeatmapLocationSummary?
)

data class DensityBandCount(
    val band: String,
    val count: Int
)

data class NetworkDensityPoint(
    val latitude: Double,
    val longitude: Double,
    val totalObservations: Int,
    val uniqueNetworkCount: Int,
    val averageSignal: Double,
    val densityLevel: String?,
    val frequencyDistribution: List<DensityBandCount>
)

data class NetworkDensityResponse(
    val count: Int,
    val density: List<NetworkDensityPoint>
)

data class NetworkQualityComponents(
    val signalScore: Int,
    val signalLevelScore: Int,
    val bandScore: Int,
    val securityScore: Int
)

data class NetworkQualityDetails(
    val signalDbm: Int,
    val band: String,
    val security: String
)

data class NetworkQualityScore(
    val score: Int,
    val classification: String,
    val components: NetworkQualityComponents,
    val details: NetworkQualityDetails
)

data class NetworkQualityItem(
    val observationId: String,
    val ssid: String,
    val quality: NetworkQualityScore,
    val observedAt: String
)

data class NetworkQualityResponse(
    val count: Int,
    val averageScore: Int?,
    val networks: List<NetworkQualityItem>
)

data class PacketLossSummary(
    val totalPackets: Int,
    val successfulPackets: Int,
    val lostPackets: Int,
    val packetLossPercentage: Double,
    val averageResponseMs: Double?,
    val measurementType: String
)

data class PerformanceTestResult(
    val latencyMs: Double,
    val medianLatencyMs: Double?,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val measuredAt: String,
    val packetLoss: PacketLossSummary
)

data class RunPerformanceTestResponse(
    val message: String,
    val savedMeasurementId: String,
    val performance: PerformanceTestResult
)

data class PerformanceMeasurementRecord(
    val id: String,
    val latencyMs: Double,
    val medianLatencyMs: Double?,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val packetLoss: PacketLossSummary,
    val source: String,
    val testServer: String?,
    val measuredAt: String,
    val createdAt: String? = null
)

data class PerformanceHistoryResponse(
    val count: Int,
    val history: List<PerformanceMeasurementRecord>
)

data class PerformanceTrendPoint(
    val latencyMs: Double,
    val medianLatencyMs: Double?,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val packetLossPercentage: Double,
    val source: String,
    val measuredAt: String
)

data class PerformanceTrendsResponse(
    val count: Int,
    val trends: List<PerformanceTrendPoint>
)

data class PerformanceAnalytics(
    val totalTests: Int,
    val averageDownloadMbps: Double?,
    val averageUploadMbps: Double?,
    val averageLatencyMs: Double?,
    val averageMedianLatencyMs: Double?,
    val averageJitterMs: Double?,
    val averagePacketLossPercentage: Double?,
    val fastestDownloadMbps: Double?,
    val slowestDownloadMbps: Double?,
    val fastestUploadMbps: Double?,
    val latestMeasurement: String?
)

data class PairedDeviceSummary(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val scopes: List<String>,
    val status: String,
    val revokedAt: String?,
    val expiresAt: String?,
    val lastUsedAt: String?,
    val createdAt: String?
)

data class DeviceListResponse(
    val count: Int,
    val devices: List<PairedDeviceSummary>
)

data class PairingCodeResponse(
    val pairingCode: String,
    val platform: String,
    val expiresAt: String,
    val expiresInSeconds: Int
)

data class RevokeDeviceResponse(
    val message: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val status: String,
    val revokedAt: String?
)

data class EndpointProbeStatus(
    val id: String,
    val label: String,
    val endpoint: String,
    val available: Boolean,
    val detail: String
)

data class SystemMonitoringSnapshot(
    val health: HealthCheckResult,
    val endpoints: List<EndpointProbeStatus>,
    val recentObservations: List<CloudObservation>,
    val recentPerformance: List<PerformanceMeasurementRecord>,
    val observationAnalytics: ObservationAnalytics?,
    val performanceAnalytics: PerformanceAnalytics?,
    val checkedAtEpochMs: Long
)

/**
 * Pure-Kotlin response parsers backed by [JsonSupport] so all dashboard and
 * auth responses decode identically on Android and in JVM unit tests.
 */
@Suppress("UNCHECKED_CAST")
internal object ApiJsonParsers {

    fun Map<String, Any?>.str(key: String): String? = this[key] as? String

    fun Map<String, Any?>.bool(key: String, default: Boolean = false): Boolean =
        this[key] as? Boolean ?: default

    fun Map<String, Any?>.intVal(key: String): Int? =
        (this[key] as? Number)?.toInt()

    fun Map<String, Any?>.longVal(key: String): Long? =
        (this[key] as? Number)?.toLong()

    fun Map<String, Any?>.doubleVal(key: String): Double? {
        val v = (this[key] as? Number)?.toDouble() ?: return null
        return if (v.isFinite()) v else null
    }

    fun Map<String, Any?>.obj(key: String): Map<String, Any?>? =
        this[key] as? Map<String, Any?>

    fun Map<String, Any?>.objList(key: String): List<Map<String, Any?>> {
        val raw = this[key] as? List<*> ?: return emptyList()
        return raw.mapNotNull { it as? Map<String, Any?> }
    }

    fun Map<String, Any?>.strList(key: String): List<String> {
        val raw = this[key] as? List<*> ?: return emptyList()
        return raw.mapNotNull { it as? String }
    }

    fun parseAuthUser(map: Map<String, Any?>?): AuthUser? {
        if (map == null) return null
        val id = (map.str("id") ?: map.str("_id"))?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val email = map.str("email")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = map.str("name")?.trim()?.ifEmpty { email } ?: email
        val verified = map.bool("isEmailVerified", default = false)
        return AuthUser(
            id = id,
            name = name,
            email = email,
            isEmailVerified = verified
        )
    }

    fun parseCloudObservation(map: Map<String, Any?>): CloudObservation? {
        val id = (map.str("_id") ?: map.str("id")) ?: return null
        val signalDbm = map.intVal("signalDbm") ?: return null
        val frequencyMhz = map.intVal("frequencyMhz") ?: 0
        val band = map.str("band") ?: "Unknown"
        val security = map.str("security") ?: "Unknown"
        val observedAt = map.str("observedAt") ?: map.str("createdAt") ?: ""

        val locMap = map.obj("location")
        val location = if (locMap != null) {
            val lat = locMap.doubleVal("latitude")
            val lon = locMap.doubleVal("longitude")
            if (lat != null && lon != null) {
                val acc = locMap.doubleVal("accuracyMeters")?.takeIf { it >= 0.0 }
                ObservationLocation(lat, lon, acc).takeIf { it.isValid() }
            } else {
                null
            }
        } else {
            null
        }

        return CloudObservation(
            id = id,
            ssid = map.str("ssid") ?: "",
            signalDbm = signalDbm,
            frequencyMhz = frequencyMhz,
            band = band,
            security = security,
            signalQuality = map.intVal("signalQuality"),
            location = location,
            source = map.str("source") ?: "unknown",
            observedAt = observedAt,
            createdAt = map.str("createdAt"),
            updatedAt = map.str("updatedAt")
        )
    }

    fun parseObservationList(root: Map<String, Any?>): ObservationListResponse {
        val list = root.objList("observations").mapNotNull { parseCloudObservation(it) }
        val count = root.intVal("count") ?: list.size
        val totalCount = root.intVal("totalCount") ?: count
        val page = root.intVal("page") ?: 1
        val limit = root.intVal("limit") ?: 100
        val hasMore = root.bool("hasMore", default = false)
        return ObservationListResponse(
            count = count,
            totalCount = totalCount,
            page = page,
            limit = limit,
            hasMore = hasMore,
            observations = list
        )
    }

    fun parseObservationStats(root: Map<String, Any?>): ObservationAnalytics? {
        val analytics = root.obj("analytics") ?: return null
        val bands = analytics.objList("bands").mapNotNull { item ->
            val cnt = item.intVal("count") ?: return@mapNotNull null
            CategoryCount(label = item.str("_id") ?: "Unknown", count = cnt)
        }
        val security = analytics.objList("security").mapNotNull { item ->
            val cnt = item.intVal("count") ?: return@mapNotNull null
            CategoryCount(label = item.str("_id") ?: "Unknown", count = cnt)
        }
        return ObservationAnalytics(
            filteredBand = analytics.str("filteredBand"),
            totalNetworks = analytics.intVal("totalNetworks") ?: 0,
            averageSignal = analytics.doubleVal("averageSignal"),
            strongestSignal = analytics.intVal("strongestSignal"),
            weakestSignal = analytics.intVal("weakestSignal"),
            bands = bands,
            security = security
        )
    }

    fun parseHeatmapResponse(root: Map<String, Any?>): HeatmapResponse {
        val points = root.objList("heatmap").mapNotNull { p ->
            val lat = p.doubleVal("latitude") ?: return@mapNotNull null
            val lon = p.doubleVal("longitude") ?: return@mapNotNull null
            val avg = p.doubleVal("averageSignal") ?: return@mapNotNull null
            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return@mapNotNull null

            val freqDist = p.objList("frequencyDistribution").mapNotNull { fd ->
                val band = fd.str("band") ?: return@mapNotNull null
                HeatmapBandDistribution(
                    band = band,
                    count = fd.intVal("count") ?: 0,
                    averageSignal = fd.doubleVal("averageSignal"),
                    strongestSignal = fd.intVal("strongestSignal"),
                    weakestSignal = fd.intVal("weakestSignal")
                )
            }

            HeatmapPoint(
                latitude = lat,
                longitude = lon,
                totalObservations = p.intVal("totalObservations") ?: 0,
                averageSignal = avg,
                strongestSignal = p.intVal("strongestSignal") ?: avg.toInt(),
                weakestSignal = p.intVal("weakestSignal") ?: avg.toInt(),
                avgAccuracyMeters = p.doubleVal("avgAccuracyMeters"),
                accuracySampleCount = p.intVal("accuracySampleCount") ?: 0,
                missingAccuracyCount = p.intVal("missingAccuracyCount") ?: 0,
                coarseAccuracyCount = p.intVal("coarseAccuracyCount") ?: 0,
                frequencyDistribution = freqDist
            )
        }

        val accMap = root.obj("accuracySummary")
        val accuracySummary = accMap?.let {
            HeatmapAccuracySummary(
                averageAccuracyMeters = it.doubleVal("averageAccuracyMeters"),
                withAccuracyCount = it.intVal("withAccuracyCount") ?: 0,
                withoutAccuracyCount = it.intVal("withoutAccuracyCount") ?: 0,
                coarseAccuracyCount = it.intVal("coarseAccuracyCount") ?: 0
            )
        }

        val locMap = root.obj("locationSummary")
        val locationSummary = locMap?.let {
            HeatmapLocationSummary(
                geolocatedObservations = it.intVal("geolocatedObservations") ?: 0,
                nonGeolocatedObservations = it.intVal("nonGeolocatedObservations") ?: 0,
                totalObservations = it.intVal("totalObservations") ?: 0
            )
        }

        return HeatmapResponse(
            count = root.intVal("count") ?: points.size,
            heatmap = points,
            accuracySummary = accuracySummary,
            locationSummary = locationSummary
        )
    }

    fun parseNetworkDensity(root: Map<String, Any?>): NetworkDensityResponse {
        val points = root.objList("density").mapNotNull { d ->
            val lat = d.doubleVal("latitude") ?: return@mapNotNull null
            val lon = d.doubleVal("longitude") ?: return@mapNotNull null
            val avg = d.doubleVal("averageSignal") ?: return@mapNotNull null
            val dist = d.objList("frequencyDistribution").mapNotNull { fd ->
                val band = fd.str("band") ?: return@mapNotNull null
                DensityBandCount(band = band, count = fd.intVal("count") ?: 0)
            }
            NetworkDensityPoint(
                latitude = lat,
                longitude = lon,
                totalObservations = d.intVal("totalObservations") ?: 0,
                uniqueNetworkCount = d.intVal("uniqueNetworkCount") ?: 0,
                averageSignal = avg,
                densityLevel = d.str("densityLevel"),
                frequencyDistribution = dist
            )
        }
        return NetworkDensityResponse(
            count = root.intVal("count") ?: points.size,
            density = points
        )
    }

    fun parseNetworkQuality(root: Map<String, Any?>): NetworkQualityResponse {
        val items = root.objList("networks").mapNotNull { item ->
            val obsId = item.str("observationId") ?: return@mapNotNull null
            val qMap = item.obj("quality") ?: return@mapNotNull null
            val compMap = qMap.obj("components") ?: return@mapNotNull null
            val detMap = qMap.obj("details") ?: emptyMap()

            val score = qMap.intVal("score") ?: return@mapNotNull null
            val components = NetworkQualityComponents(
                signalScore = compMap.intVal("signalScore") ?: 0,
                signalLevelScore = compMap.intVal("signalLevelScore") ?: 0,
                bandScore = compMap.intVal("bandScore") ?: 0,
                securityScore = compMap.intVal("securityScore") ?: 0
            )
            val details = NetworkQualityDetails(
                signalDbm = detMap.intVal("signalDbm") ?: 0,
                band = detMap.str("band") ?: "Unknown",
                security = detMap.str("security") ?: "Unknown"
            )
            NetworkQualityItem(
                observationId = obsId,
                ssid = item.str("ssid") ?: "",
                quality = NetworkQualityScore(
                    score = score,
                    classification = qMap.str("classification") ?: "Unknown",
                    components = components,
                    details = details
                ),
                observedAt = item.str("observedAt") ?: ""
            )
        }

        return NetworkQualityResponse(
            count = root.intVal("count") ?: items.size,
            averageScore = root.intVal("averageScore"),
            networks = items
        )
    }

    fun parsePacketLoss(map: Map<String, Any?>?): PacketLossSummary {
        if (map == null) {
            return PacketLossSummary(
                totalPackets = 0,
                successfulPackets = 0,
                lostPackets = 0,
                packetLossPercentage = 0.0,
                averageResponseMs = null,
                measurementType = "Unknown"
            )
        }
        return PacketLossSummary(
            totalPackets = map.intVal("totalPackets") ?: 0,
            successfulPackets = map.intVal("successfulPackets") ?: 0,
            lostPackets = map.intVal("lostPackets") ?: 0,
            packetLossPercentage = map.doubleVal("packetLossPercentage") ?: 0.0,
            averageResponseMs = map.doubleVal("averageResponseMs"),
            measurementType = map.str("measurementType") ?: "HTTP"
        )
    }

    fun parseRunPerformance(root: Map<String, Any?>): RunPerformanceTestResponse? {
        val perf = root.obj("performance") ?: return null
        val download = perf.doubleVal("downloadMbps") ?: return null
        val upload = perf.doubleVal("uploadMbps") ?: return null
        val latency = perf.doubleVal("latencyMs") ?: return null
        val jitter = perf.doubleVal("jitterMs") ?: 0.0
        val measuredAt = perf.str("measuredAt") ?: ""
        return RunPerformanceTestResponse(
            message = root.str("message") ?: "Performance measured and saved successfully.",
            savedMeasurementId = root.str("savedMeasurementId") ?: "",
            performance = PerformanceTestResult(
                latencyMs = latency,
                medianLatencyMs = perf.doubleVal("medianLatencyMs"),
                jitterMs = jitter,
                downloadMbps = download,
                uploadMbps = upload,
                measuredAt = measuredAt,
                packetLoss = parsePacketLoss(perf.obj("packetLoss"))
            )
        )
    }

    fun parsePerformanceHistory(root: Map<String, Any?>): PerformanceHistoryResponse {
        val records = root.objList("history").mapNotNull { item ->
            val id = (item.str("_id") ?: item.str("id")) ?: return@mapNotNull null
            val download = item.doubleVal("downloadMbps") ?: return@mapNotNull null
            val upload = item.doubleVal("uploadMbps") ?: return@mapNotNull null
            val latency = item.doubleVal("latencyMs") ?: return@mapNotNull null
            PerformanceMeasurementRecord(
                id = id,
                latencyMs = latency,
                medianLatencyMs = item.doubleVal("medianLatencyMs"),
                jitterMs = item.doubleVal("jitterMs") ?: 0.0,
                downloadMbps = download,
                uploadMbps = upload,
                packetLoss = parsePacketLoss(item.obj("packetLoss")),
                source = item.str("source") ?: "backend",
                testServer = item.str("testServer"),
                measuredAt = item.str("measuredAt") ?: item.str("createdAt") ?: "",
                createdAt = item.str("createdAt")
            )
        }
        return PerformanceHistoryResponse(
            count = root.intVal("count") ?: records.size,
            history = records
        )
    }

    fun parsePerformanceAnalytics(root: Map<String, Any?>): PerformanceAnalytics? {
        val a = root.obj("analytics") ?: return null
        return PerformanceAnalytics(
            totalTests = a.intVal("totalTests") ?: 0,
            averageDownloadMbps = a.doubleVal("averageDownloadMbps"),
            averageUploadMbps = a.doubleVal("averageUploadMbps"),
            averageLatencyMs = a.doubleVal("averageLatencyMs"),
            averageMedianLatencyMs = a.doubleVal("averageMedianLatencyMs"),
            averageJitterMs = a.doubleVal("averageJitterMs"),
            averagePacketLossPercentage = a.doubleVal("averagePacketLossPercentage"),
            fastestDownloadMbps = a.doubleVal("fastestDownloadMbps"),
            slowestDownloadMbps = a.doubleVal("slowestDownloadMbps"),
            fastestUploadMbps = a.doubleVal("fastestUploadMbps"),
            latestMeasurement = a.str("latestMeasurement")
        )
    }

    fun parsePerformanceTrends(root: Map<String, Any?>): PerformanceTrendsResponse {
        val trends = root.objList("trends").mapNotNull { t ->
            val download = t.doubleVal("downloadMbps") ?: return@mapNotNull null
            val upload = t.doubleVal("uploadMbps") ?: return@mapNotNull null
            val latency = t.doubleVal("latencyMs") ?: return@mapNotNull null
            PerformanceTrendPoint(
                latencyMs = latency,
                medianLatencyMs = t.doubleVal("medianLatencyMs"),
                jitterMs = t.doubleVal("jitterMs") ?: 0.0,
                downloadMbps = download,
                uploadMbps = upload,
                packetLossPercentage = t.doubleVal("packetLossPercentage") ?: 0.0,
                source = t.str("source") ?: "backend",
                measuredAt = t.str("measuredAt") ?: ""
            )
        }
        return PerformanceTrendsResponse(
            count = root.intVal("count") ?: trends.size,
            trends = trends
        )
    }

    fun parseDeviceSummary(d: Map<String, Any?>): PairedDeviceSummary? {
        val deviceId = d.str("deviceId") ?: d.str("_id") ?: return null
        val deviceName = d.str("deviceName") ?: "Companion Device"
        return PairedDeviceSummary(
            deviceId = deviceId,
            deviceName = deviceName,
            platform = d.str("platform") ?: "android",
            scopes = d.strList("scopes"),
            status = d.str("status") ?: "active",
            revokedAt = d.str("revokedAt"),
            expiresAt = d.str("expiresAt"),
            lastUsedAt = d.str("lastUsedAt"),
            createdAt = d.str("createdAt")
        )
    }

    fun parseDeviceList(root: Map<String, Any?>): DeviceListResponse {
        val list = root.objList("devices").mapNotNull { parseDeviceSummary(it) }
        return DeviceListResponse(
            count = root.intVal("count") ?: list.size,
            devices = list
        )
    }

    fun parsePairingCode(root: Map<String, Any?>): PairingCodeResponse? {
        val code = root.str("pairingCode") ?: return null
        val expiresAt = root.str("expiresAt") ?: return null
        return PairingCodeResponse(
            pairingCode = code,
            platform = root.str("platform") ?: "any",
            expiresAt = expiresAt,
            expiresInSeconds = root.intVal("expiresInSeconds") ?: 300
        )
    }

    fun parseRevokeDevice(root: Map<String, Any?>): RevokeDeviceResponse? {
        val dev = root.obj("device") ?: return null
        val deviceId = dev.str("deviceId") ?: dev.str("_id") ?: return null
        return RevokeDeviceResponse(
            message = root.str("message") ?: "Device revoked immediately.",
            deviceId = deviceId,
            deviceName = dev.str("deviceName") ?: "Companion Device",
            platform = dev.str("platform") ?: "android",
            status = dev.str("status") ?: "revoked",
            revokedAt = dev.str("revokedAt")
        )
    }
}
