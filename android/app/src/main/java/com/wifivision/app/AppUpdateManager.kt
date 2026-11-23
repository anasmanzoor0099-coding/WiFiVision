package com.wifivision.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class AppReleaseMetadata(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val fileSize: Long,
    val releaseNotes: List<String>,
    val minimumSupportedVersionCode: Int
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val release: AppReleaseMetadata,
        val installedVersionCode: Int,
        val installedVersionName: String,
        val belowMinimumSupported: Boolean
    ) : UpdateCheckResult()

    data class UpToDate(
        val release: AppReleaseMetadata,
        val installedVersionCode: Int,
        val installedVersionName: String
    ) : UpdateCheckResult()

    data class Error(val message: String) : UpdateCheckResult()
}

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : UpdateDownloadState()

    data object VerifyingChecksum : UpdateDownloadState()
    data class ReadyToInstall(
        val apkFile: File,
        val release: AppReleaseMetadata
    ) : UpdateDownloadState()

    data object Cancelled : UpdateDownloadState()
    data class Failed(val message: String) : UpdateDownloadState()
}

/**
 * Manages release metadata checking, HTTPS in-app APK download with cancellation,
 * SHA-256 + package/signature verification, anti-downgrade protection, and
 * Android PackageInstaller session commit (never silent or bypassing permissions).
 */
class AppUpdateManager(
    context: Context,
    private val preferences: AppPreferences
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val downloadCancelled = AtomicBoolean(false)
    @Volatile
    private var activeConnection: HttpURLConnection? = null
    @Volatile
    private var isBusy = false

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_METADATA_BYTES = 64 * 1024
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L
        private const val MAX_REDIRECTS = 3
        private const val BUFFER_SIZE = 16 * 1024
        private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.2f MB", mb)
        }

        fun getExpectedReleaseHost(): String? {
            return try {
                URI(BuildConfig.RELEASE_METADATA_URL).host?.trim()?.lowercase(Locale.US)
            } catch (_: Exception) {
                null
            }
        }

        fun isTrustedOfficialReleaseUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return false
            val expectedHost = getExpectedReleaseHost() ?: return false
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
                val host = uri.host?.lowercase(Locale.US) ?: return false
                val path = uri.path ?: return false
                scheme == "https" &&
                    host == expectedHost &&
                    path.startsWith("/downloads/") &&
                    !path.contains("..")
            } catch (_: Exception) {
                false
            }
        }

        fun parseReleaseMetadata(rawJson: String): AppReleaseMetadata {
            val root = JsonSupport.parseJson(rawJson) as? Map<*, *>
                ?: throw IllegalArgumentException("Release metadata must be a JSON object.")

            val versionName = (root["versionName"] as? String)?.trim().orEmpty()
            val versionCode = (root["versionCode"] as? Number)?.toInt() ?: 0
            val apkUrl = (root["apkUrl"] as? String)?.trim().orEmpty()
            val sha256 = (root["sha256"] as? String)?.trim()?.lowercase(Locale.US).orEmpty()
            val fileSize = (root["fileSize"] as? Number)?.toLong() ?: 0L
            val minSupported = (root["minimumSupportedVersionCode"] as? Number)?.toInt() ?: 1

            val notesRaw = root["releaseNotes"]
            val releaseNotes = when (notesRaw) {
                is List<*> -> notesRaw.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                is String -> notesRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }
                else -> emptyList()
            }

            if (versionName.isEmpty()) {
                throw IllegalArgumentException("Release metadata missing versionName.")
            }
            if (versionCode <= 0) {
                throw IllegalArgumentException("Release metadata contains invalid versionCode.")
            }
            if (!isTrustedOfficialReleaseUrl(apkUrl) || !apkUrl.lowercase(Locale.US).endsWith(".apk")) {
                throw IllegalArgumentException("Release apkUrl does not point to an official HTTPS WiFiVision APK.")
            }
            if (!SHA256_HEX_REGEX.matches(sha256)) {
                throw IllegalArgumentException("Release metadata missing valid SHA-256 checksum.")
            }
            if (fileSize <= 0L || fileSize > MAX_APK_BYTES) {
                throw IllegalArgumentException("Release fileSize is out of expected range.")
            }

            return AppReleaseMetadata(
                versionName = versionName,
                versionCode = versionCode,
                apkUrl = apkUrl,
                sha256 = sha256,
                fileSize = fileSize,
                releaseNotes = if (releaseNotes.isEmpty()) {
                    listOf("Stability and security improvements.")
                } else {
                    releaseNotes
                },
                minimumSupportedVersionCode = minSupported
            )
        }
    }

    fun getInstalledVersionCode(): Int {
        return try {
            val info = getInstalledPackageInfo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            getInstalledPackageInfo().versionName?.takeIf { it.isNotBlank() }
                ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return try {
            appContext.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) {
            false
        }
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}")
        )
    }

    fun checkForUpdates(onResult: (UpdateCheckResult) -> Unit) {
        if (isBusy) return
        isBusy = true

        executor.execute {
            try {
                val metadataUrl = BuildConfig.RELEASE_METADATA_URL
                if (!isTrustedOfficialReleaseUrl(metadataUrl)) {
                    postCheckResult(
                        UpdateCheckResult.Error("Invalid official release metadata configuration."),
                        onResult
                    )
                    return@execute
                }

                val jsonText = fetchTextOverTrustedHttps(metadataUrl)
                val release = parseReleaseMetadata(jsonText)
                val nowMs = System.currentTimeMillis()
                preferences.setLastUpdateCheckEpochMs(nowMs)
                preferences.setLatestKnownRelease(release.versionName, release.versionCode)

                val installedCode = getInstalledVersionCode()
                val installedName = getInstalledVersionName()

                if (release.versionCode > installedCode) {
                    postCheckResult(
                        UpdateCheckResult.UpdateAvailable(
                            release = release,
                            installedVersionCode = installedCode,
                            installedVersionName = installedName,
                            belowMinimumSupported = installedCode < release.minimumSupportedVersionCode
                        ),
                        onResult
                    )
                } else {
                    postCheckResult(
                        UpdateCheckResult.UpToDate(
                            release = release,
                            installedVersionCode = installedCode,
                            installedVersionName = installedName
                        ),
                        onResult
                    )
                }
            } catch (ex: Exception) {
                val reason = ex.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to reach update service."
                postCheckResult(
                    UpdateCheckResult.Error("Update check failed: $reason"),
                    onResult
                )
            } finally {
                isBusy = false
            }
        }
    }

    fun downloadUpdate(
        release: AppReleaseMetadata,
        onState: (UpdateDownloadState) -> Unit
    ) {
        if (isBusy) return
        val installedCode = getInstalledVersionCode()
        if (release.versionCode <= installedCode) {
            onState(UpdateDownloadState.Failed("Downgrade blocked: version ${release.versionName} is not newer than installed version."))
            return
        }
        if (!isTrustedOfficialReleaseUrl(release.apkUrl)) {
            onState(UpdateDownloadState.Failed("Untrusted APK download URL blocked."))
            return
        }

        isBusy = true
        downloadCancelled.set(false)
        postDownloadState(UpdateDownloadState.Downloading(0L, release.fileSize, 0), onState)

        executor.execute {
            var partFile: File? = null
            try {
                val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                // Clean stale partial or old update binaries first
                updatesDir.listFiles()?.forEach { old ->
                    if (old.isFile) old.delete()
                }

                val safeName = "wifivision-${release.versionCode}.apk"
                val temp = File(updatesDir, "$safeName.part")
                val finalFile = File(updatesDir, safeName)
                partFile = temp

                val conn = openTrustedHttpsConnection(release.apkUrl)
                activeConnection = conn

                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $code while downloading APK.")
                }

                val contentLength = conn.contentLengthLong.takeIf { it > 0L } ?: release.fileSize
                if (contentLength > MAX_APK_BYTES) {
                    throw IllegalStateException("Downloaded APK size exceeds safety limit.")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var totalRead = 0L
                var lastUiUpdateMs = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (downloadCancelled.get()) {
                                throw DownloadCancelledException()
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue

                            totalRead += read
                            if (totalRead > MAX_APK_BYTES) {
                                throw IllegalStateException("APK download exceeded maximum allowed size.")
                            }

                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastUiUpdateMs >= 120L || totalRead >= contentLength) {
                                lastUiUpdateMs = nowMs
                                val pct = if (contentLength > 0L) {
                                    ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                postDownloadState(
                                    UpdateDownloadState.Downloading(totalRead, contentLength, pct),
                                    onState
                                )
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (downloadCancelled.get()) {
                    throw DownloadCancelledException()
                }

                postDownloadState(UpdateDownloadState.VerifyingChecksum, onState)

                if (totalRead != release.fileSize) {
                    temp.delete()
                    throw SecurityException(
                        "Downloaded APK size ($totalRead bytes) does not match expected size (${release.fileSize} bytes)."
                    )
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha256.equals(release.sha256, ignoreCase = true)) {
                    temp.delete()
                    throw SecurityException(
                        "SHA-256 verification failed. The downloaded APK may be corrupted or tampered."
                    )
                }

                if (finalFile.exists()) finalFile.delete()
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }

                verifyDownloadedApkPackageAndSignature(finalFile, release)
                postDownloadState(UpdateDownloadState.ReadyToInstall(finalFile, release), onState)
            } catch (_: DownloadCancelledException) {
                partFile?.delete()
                postDownloadState(UpdateDownloadState.Cancelled, onState)
            } catch (ex: Exception) {
                partFile?.delete()
                val msg = ex.message?.takeIf { it.isNotBlank() }
                    ?: "Download interrupted."
                postDownloadState(UpdateDownloadState.Failed(msg), onState)
            } finally {
                try {
                    activeConnection?.disconnect()
                } catch (_: Exception) {
                }
                activeConnection = null
                isBusy = false
            }
        }
    }

    fun cancelDownload() {
        downloadCancelled.set(true)
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }
    }

    fun commitPackageInstall(
        apkFile: File,
        release: AppReleaseMetadata,
        onError: (String) -> Unit
    ) {
        if (!apkFile.exists() || !apkFile.isFile) {
            onError("Downloaded APK is no longer available. Please download the update again.")
            return
        }

        executor.execute {
            var session: PackageInstaller.Session? = null
            try {
                // Re-verify integrity, anti-downgrade, and signature immediately before install.
                if (apkFile.length() != release.fileSize) {
                    apkFile.delete()
                    throw SecurityException("APK size mismatch detected before installation.")
                }
                val actualSha256 = computeFileSha256(apkFile)
                if (!actualSha256.equals(release.sha256, ignoreCase = true)) {
                    apkFile.delete()
                    throw SecurityException("SHA-256 verification failed before installation.")
                }
                verifyDownloadedApkPackageAndSignature(apkFile, release)

                val installer = appContext.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    setAppPackageName(appContext.packageName)
                    setSize(apkFile.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }
                }

                val sessionId = installer.createSession(params)
                session = installer.openSession(sessionId)

                FileInputStream(apkFile).use { input ->
                    session.openWrite("wifivision_update.apk", 0L, apkFile.length()).use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        session.fsync(out)
                    }
                }

                val callbackIntent = Intent(appContext, UpdateInstallReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pendingIntent.intentSender)
                session.close()
                session = null
            } catch (ex: Exception) {
                try {
                    session?.abandon()
                } catch (_: Exception) {
                }
                val msg = ex.message?.takeIf { it.isNotBlank() }
                    ?: "Failed to launch Android installer."
                mainHandler.post { onError(msg) }
            }
        }
    }

    fun shutdown() {
        cancelDownload()
        executor.shutdownNow()
    }

    private fun verifyDownloadedApkPackageAndSignature(
        apkFile: File,
        release: AppReleaseMetadata
    ) {
        val archiveInfo = getArchivePackageInfo(apkFile)
            ?: throw SecurityException("Downloaded file is not a valid Android APK package.")

        if (archiveInfo.packageName != appContext.packageName) {
            apkFile.delete()
            throw SecurityException(
                "APK package mismatch (${archiveInfo.packageName}); installation aborted."
            )
        }

        val archiveVersionCode = extractVersionCode(archiveInfo)
        val installedCode = getInstalledVersionCode().toLong()
        if (archiveVersionCode <= installedCode) {
            apkFile.delete()
            throw SecurityException(
                "Downgrade blocked: downloaded APK version code ($archiveVersionCode) is not newer than installed ($installedCode)."
            )
        }
        if (archiveVersionCode != release.versionCode.toLong()) {
            apkFile.delete()
            throw SecurityException(
                "APK version code ($archiveVersionCode) does not match release metadata (${release.versionCode})."
            )
        }

        val installedCertDigests = extractSigningCertDigests(getInstalledPackageInfo())
        val archiveCertDigests = extractSigningCertDigests(archiveInfo)
        if (archiveCertDigests.isEmpty() ||
            (installedCertDigests.isNotEmpty() && installedCertDigests.intersect(archiveCertDigests).isEmpty())
        ) {
            apkFile.delete()
            throw SecurityException(
                "Downloaded APK is unsigned or signed with an unrecognized certificate. Installation aborted to preserve app data."
            )
        }
    }

    private fun getInstalledPackageInfo(): PackageInfo {
        val pm = appContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            } else {
                0L
            }
            pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            pm.getPackageInfo(appContext.packageName, flags)
        }
    }

    private fun getArchivePackageInfo(apkFile: File): PackageInfo? {
        val pm = appContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    private fun extractVersionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun extractSigningCertDigests(info: PackageInfo): Set<String> {
        val digests = HashSet<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val signers = if (signingInfo == null) {
                emptyArray()
            } else if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners ?: emptyArray()
            } else {
                signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners ?: emptyArray()
            }
            for (sig in signers) {
                digests.add(sha256Hex(sig.toByteArray()))
            }
        } else {
            @Suppress("DEPRECATION")
            val sigs = info.signatures ?: emptyArray()
            for (sig in sigs) {
                digests.add(sha256Hex(sig.toByteArray()))
            }
        }
        return digests
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun fetchTextOverTrustedHttps(rawUrl: String): String {
        val conn = openTrustedHttpsConnection(rawUrl)
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    total += read
                    if (total > MAX_METADATA_BYTES) {
                        throw IllegalStateException("Release metadata exceeds size limit.")
                    }
                    out.write(buf, 0, read)
                }
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openTrustedHttpsConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        for (hop in 0..MAX_REDIRECTS) {
            if (!isTrustedOfficialReleaseUrl(currentUrl)) {
                throw SecurityException("Untrusted release URL blocked: $currentUrl")
            }
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val status = conn.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")?.trim()
                conn.disconnect()
                if (location.isNullOrEmpty()) {
                    throw IllegalStateException("Redirect missing target location.")
                }
                currentUrl = URI(currentUrl).resolve(location).toString()
                continue
            }
            return conn
        }
        throw IllegalStateException("Too many redirects while fetching release asset.")
    }

    private fun postCheckResult(result: UpdateCheckResult, onResult: (UpdateCheckResult) -> Unit) {
        mainHandler.post { onResult(result) }
    }

    private fun postDownloadState(state: UpdateDownloadState, onState: (UpdateDownloadState) -> Unit) {
        mainHandler.post { onState(state) }
    }

    private class DownloadCancelledException : Exception()
}
