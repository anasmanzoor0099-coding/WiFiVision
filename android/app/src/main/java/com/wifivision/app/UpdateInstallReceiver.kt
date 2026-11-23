package com.wifivision.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

sealed class UpdateInstallEvent {
    data class PendingUserAction(
        val confirmIntent: Intent,
        val sessionId: Int
    ) : UpdateInstallEvent()

    data object Succeeded : UpdateInstallEvent()
    data object Cancelled : UpdateInstallEvent()
    data class Failed(val message: String) : UpdateInstallEvent()
}

object AppUpdateInstallBus {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingEvent: UpdateInstallEvent? = null

    var listener: ((UpdateInstallEvent) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                val queued = pendingEvent
                if (queued != null) {
                    pendingEvent = null
                    mainHandler.post { field?.invoke(queued) }
                }
            }
        }

    fun dispatch(event: UpdateInstallEvent) {
        mainHandler.post {
            val activeListener = listener
            if (activeListener != null) {
                activeListener(event)
            } else {
                pendingEvent = event
            }
        }
    }

    fun clearPending() {
        pendingEvent = null
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent: Intent? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }

                if (confirmIntent != null) {
                    AppUpdateInstallBus.dispatch(
                        UpdateInstallEvent.PendingUserAction(confirmIntent, sessionId)
                    )
                } else {
                    AppUpdateInstallBus.dispatch(
                        UpdateInstallEvent.Failed("Android installer confirmation screen could not be opened.")
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                cleanupUpdateCache(context)
                AppUpdateInstallBus.dispatch(UpdateInstallEvent.Succeeded)
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                AppUpdateInstallBus.dispatch(UpdateInstallEvent.Cancelled)
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                AppUpdateInstallBus.dispatch(
                    UpdateInstallEvent.Failed(
                        buildFailureMessage("Installation was blocked by Android settings.", rawMessage)
                    )
                )
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                AppUpdateInstallBus.dispatch(
                    UpdateInstallEvent.Failed(
                        buildFailureMessage(
                            "Signature or package conflict with an existing installation.",
                            rawMessage
                        )
                    )
                )
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                AppUpdateInstallBus.dispatch(
                    UpdateInstallEvent.Failed(
                        buildFailureMessage("This update package is incompatible with this device.", rawMessage)
                    )
                )
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                AppUpdateInstallBus.dispatch(
                    UpdateInstallEvent.Failed(
                        buildFailureMessage("The downloaded APK package is invalid.", rawMessage)
                    )
                )
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                AppUpdateInstallBus.dispatch(
                    UpdateInstallEvent.Failed(
                        buildFailureMessage("Insufficient device storage to complete update.", rawMessage)
                    )
                )
            }

            else -> {
                AppUpdateInstallBus.dispatch(
                    UpdateInstallEvent.Failed(
                        buildFailureMessage("Update installation failed (status $status).", rawMessage)
                    )
                )
            }
        }
    }

    private fun buildFailureMessage(fallback: String, rawMessage: String?): String {
        val cleaned = rawMessage?.trim().orEmpty()
        return if (cleaned.isNotEmpty()) "$fallback ($cleaned)" else fallback
    }

    private fun cleanupUpdateCache(context: Context) {
        try {
            val updatesDir = File(context.applicationContext.cacheDir, "updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
            // Non-fatal cache cleanup
        }
    }
}
