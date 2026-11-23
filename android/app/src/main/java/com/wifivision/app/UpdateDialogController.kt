package com.wifivision.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Native WiFiVision light-theme update dialog supporting release preview,
 * live download progress, SHA-256 verification state, unknown-sources permission
 * guidance, cancellation, and retry without redirecting to external browsers.
 */
class UpdateDialogController(
    private val context: Context,
    private val onUpdateNowClick: (AppReleaseMetadata) -> Unit,
    private val onLaterClick: (Int) -> Unit,
    private val onCancelDownloadClick: () -> Unit,
    private val onOpenInstallSettingsClick: () -> Unit
) {
    private var dialog: AlertDialog? = null
    private var activeRelease: AppReleaseMetadata? = null
    private var isDownloadingOrVerifying = false

    private lateinit var badgePill: TextView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var notesBox: LinearLayout
    private lateinit var notesText: TextView
    private lateinit var statusBox: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusDetailText: TextView
    private lateinit var primaryBtn: WiFiActionButton
    private lateinit var secondaryBtn: WiFiActionButton

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun showUpdatePrompt(
        release: AppReleaseMetadata,
        installedVersionName: String,
        installedVersionCode: Int,
        belowMinimumSupported: Boolean
    ) {
        activeRelease = release
        isDownloadingOrVerifying = false
        ensureDialogCreated()

        if (belowMinimumSupported) {
            updatePillStyle(
                context,
                badgePill,
                label = "REQUIRED UPDATE",
                textColor = WiFiVisionPalette.amberText,
                bgColor = WiFiVisionPalette.amberBg,
                borderColor = WiFiVisionPalette.amberBorder,
                dotColor = WiFiVisionPalette.amberText
            )
        } else {
            updatePillStyle(
                context,
                badgePill,
                label = "UPDATE AVAILABLE",
                textColor = WiFiVisionPalette.emeraldDeep,
                bgColor = WiFiVisionPalette.emeraldTint,
                borderColor = WiFiVisionPalette.emeraldBorder,
                dotColor = WiFiVisionPalette.jade
            )
        }

        titleText.text = "WiFiVision v${release.versionName} Available"
        subtitleText.text =
            "Installed: v$installedVersionName (code $installedVersionCode)  ->  Latest: v${release.versionName} (code ${release.versionCode})  •  Size: ${AppUpdateManager.formatBytes(release.fileSize)}"

        notesText.text = release.releaseNotes.joinToString("\n") { "• $it" }
        notesBox.visibility = View.VISIBLE
        statusBox.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        statusDetailText.setTextColor(WiFiVisionPalette.bodySlate)
        statusDetailText.text =
            "Downloaded inside WiFiVision over official HTTPS and verified by SHA-256 before Android prompts you to install. Saved login & device credentials remain preserved."
        statusBox.background = roundedDrawable(
            context,
            WiFiVisionPalette.warmIvory,
            WiFiVisionPalette.hairline,
            12
        )

        showReadyButtons(release)
        configureCancelable(true, dismissVersionCode = release.versionCode)
        showDialogIfHidden()
    }

    fun onDownloadStateChanged(state: UpdateDownloadState) {
        val release = activeRelease ?: return
        if (dialog == null) ensureDialogCreated()

        when (state) {
            is UpdateDownloadState.Idle -> {
                isDownloadingOrVerifying = false
                configureCancelable(true, dismissVersionCode = release.versionCode)
            }

            is UpdateDownloadState.Downloading -> {
                isDownloadingOrVerifying = true
                configureCancelable(false, dismissVersionCode = null)
                updatePillStyle(
                    context,
                    badgePill,
                    label = "DOWNLOADING APK",
                    textColor = WiFiVisionPalette.emeraldDeep,
                    bgColor = WiFiVisionPalette.emeraldTint,
                    borderColor = WiFiVisionPalette.emeraldBorder,
                    dotColor = WiFiVisionPalette.jade
                )
                statusBox.visibility = View.VISIBLE
                statusBox.background = roundedDrawable(
                    context,
                    WiFiVisionPalette.emeraldTint,
                    WiFiVisionPalette.emeraldBorder,
                    12
                )
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = state.totalBytes <= 0L
                progressBar.max = 100
                progressBar.progress = state.progressPercent
                statusDetailText.setTextColor(WiFiVisionPalette.emeraldDeep)
                statusDetailText.text =
                    "Downloading v${release.versionName}... ${state.progressPercent}% (${AppUpdateManager.formatBytes(state.downloadedBytes)} / ${AppUpdateManager.formatBytes(state.totalBytes)})"

                primaryBtn.visibility = View.GONE
                secondaryBtn.visibility = View.VISIBLE
                secondaryBtn.configure(
                    label = "Cancel Download",
                    textColor = WiFiVisionPalette.crimsonText,
                    fillColor = WiFiVisionPalette.crimsonBg,
                    strokeColor = WiFiVisionPalette.crimsonBorder,
                    rippleColor = WiFiVisionPalette.rippleDark,
                    radiusDp = 12,
                    textSizeSp = 12.5f
                )
                secondaryBtn.setOnClickListener { onCancelDownloadClick() }
            }

            is UpdateDownloadState.VerifyingChecksum -> {
                isDownloadingOrVerifying = true
                configureCancelable(false, dismissVersionCode = null)
                updatePillStyle(
                    context,
                    badgePill,
                    label = "VERIFYING SHA-256",
                    textColor = WiFiVisionPalette.deepGold,
                    bgColor = WiFiVisionPalette.softGoldBg,
                    borderColor = WiFiVisionPalette.goldBorder,
                    dotColor = WiFiVisionPalette.champagneGold
                )
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
                statusDetailText.setTextColor(WiFiVisionPalette.darkSlate)
                statusDetailText.text = "Verifying APK SHA-256 checksum and signing certificate..."
                primaryBtn.visibility = View.GONE
                secondaryBtn.visibility = View.GONE
            }

            is UpdateDownloadState.ReadyToInstall -> {
                isDownloadingOrVerifying = false
                configureCancelable(true, dismissVersionCode = null)
                updatePillStyle(
                    context,
                    badgePill,
                    label = "CHECKSUM VERIFIED",
                    textColor = WiFiVisionPalette.emeraldDeep,
                    bgColor = WiFiVisionPalette.emeraldTint,
                    borderColor = WiFiVisionPalette.emeraldBorder,
                    dotColor = WiFiVisionPalette.jade
                )
                progressBar.visibility = View.GONE
                statusDetailText.setTextColor(WiFiVisionPalette.emeraldDeep)
                statusDetailText.text = "SHA-256 verified. Preparing Android installation confirmation..."
                primaryBtn.visibility = View.GONE
                secondaryBtn.visibility = View.VISIBLE
                secondaryBtn.configure(
                    label = "Close",
                    textColor = WiFiVisionPalette.bodySlate,
                    fillColor = WiFiVisionPalette.softIvory,
                    strokeColor = WiFiVisionPalette.hairline,
                    rippleColor = WiFiVisionPalette.rippleDark,
                    radiusDp = 12,
                    textSizeSp = 12.5f
                )
                secondaryBtn.setOnClickListener { dismiss() }
            }

            is UpdateDownloadState.Cancelled -> {
                isDownloadingOrVerifying = false
                configureCancelable(true, dismissVersionCode = release.versionCode)
                updatePillStyle(
                    context,
                    badgePill,
                    label = "DOWNLOAD CANCELLED",
                    textColor = WiFiVisionPalette.amberText,
                    bgColor = WiFiVisionPalette.amberBg,
                    borderColor = WiFiVisionPalette.amberBorder,
                    dotColor = WiFiVisionPalette.amberText
                )
                progressBar.visibility = View.GONE
                statusBox.background = roundedDrawable(
                    context,
                    WiFiVisionPalette.amberBg,
                    WiFiVisionPalette.amberBorder,
                    12
                )
                statusDetailText.setTextColor(WiFiVisionPalette.amberText)
                statusDetailText.text = "Download cancelled. You can retry updating whenever ready."
                showRetryButtons(release)
            }

            is UpdateDownloadState.Failed -> {
                isDownloadingOrVerifying = false
                configureCancelable(true, dismissVersionCode = release.versionCode)
                updatePillStyle(
                    context,
                    badgePill,
                    label = "UPDATE ERROR",
                    textColor = WiFiVisionPalette.crimsonText,
                    bgColor = WiFiVisionPalette.crimsonBg,
                    borderColor = WiFiVisionPalette.crimsonBorder,
                    dotColor = WiFiVisionPalette.crimsonText
                )
                progressBar.visibility = View.GONE
                statusBox.background = roundedDrawable(
                    context,
                    WiFiVisionPalette.crimsonBg,
                    WiFiVisionPalette.crimsonBorder,
                    12
                )
                statusDetailText.setTextColor(WiFiVisionPalette.crimsonText)
                statusDetailText.text = state.message
                showRetryButtons(release)
            }
        }
    }

    fun showPermissionGuidance(release: AppReleaseMetadata) {
        activeRelease = release
        isDownloadingOrVerifying = false
        ensureDialogCreated()
        configureCancelable(true, dismissVersionCode = release.versionCode)

        updatePillStyle(
            context,
            badgePill,
            label = "PERMISSION NEEDED",
            textColor = WiFiVisionPalette.amberText,
            bgColor = WiFiVisionPalette.amberBg,
            borderColor = WiFiVisionPalette.amberBorder,
            dotColor = WiFiVisionPalette.amberText
        )
        progressBar.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        statusBox.background = roundedDrawable(
            context,
            WiFiVisionPalette.amberBg,
            WiFiVisionPalette.amberBorder,
            12
        )
        statusDetailText.setTextColor(WiFiVisionPalette.amberText)
        statusDetailText.text =
            "Allow 'Install unknown apps' for WiFiVision in Android Settings so the system installer can launch. Return to the app after enabling it to continue."

        primaryBtn.visibility = View.VISIBLE
        primaryBtn.configure(
            label = "Open Install Settings",
            textColor = WiFiVisionPalette.pureWhite,
            fillColor = WiFiVisionPalette.emeraldDeep,
            strokeColor = WiFiVisionPalette.emeraldDeep,
            rippleColor = WiFiVisionPalette.rippleLight,
            radiusDp = 12,
            textSizeSp = 12.5f
        )
        primaryBtn.setOnClickListener { onOpenInstallSettingsClick() }

        secondaryBtn.visibility = View.VISIBLE
        secondaryBtn.configure(
            label = "Later",
            textColor = WiFiVisionPalette.deepGold,
            fillColor = WiFiVisionPalette.softGoldBg,
            strokeColor = WiFiVisionPalette.goldBorder,
            rippleColor = WiFiVisionPalette.rippleDark,
            radiusDp = 12,
            textSizeSp = 12.5f
        )
        secondaryBtn.setOnClickListener {
            onLaterClick(release.versionCode)
            dismiss()
        }
        showDialogIfHidden()
    }

    fun onInstallEvent(event: UpdateInstallEvent) {
        val dlg = dialog ?: return
        if (!dlg.isShowing) return
        val release = activeRelease

        when (event) {
            is UpdateInstallEvent.PendingUserAction -> {
                statusDetailText.setTextColor(WiFiVisionPalette.emeraldDeep)
                statusDetailText.text = "Confirm the update on Android's installation dialog."
            }

            is UpdateInstallEvent.Succeeded -> {
                dismiss()
            }

            is UpdateInstallEvent.Cancelled -> {
                updatePillStyle(
                    context,
                    badgePill,
                    label = "INSTALL CANCELLED",
                    textColor = WiFiVisionPalette.amberText,
                    bgColor = WiFiVisionPalette.amberBg,
                    borderColor = WiFiVisionPalette.amberBorder,
                    dotColor = WiFiVisionPalette.amberText
                )
                statusBox.background = roundedDrawable(
                    context,
                    WiFiVisionPalette.amberBg,
                    WiFiVisionPalette.amberBorder,
                    12
                )
                statusDetailText.setTextColor(WiFiVisionPalette.amberText)
                statusDetailText.text = "Installation cancelled on system prompt."
                if (release != null) showRetryButtons(release)
            }

            is UpdateInstallEvent.Failed -> {
                updatePillStyle(
                    context,
                    badgePill,
                    label = "INSTALL FAILED",
                    textColor = WiFiVisionPalette.crimsonText,
                    bgColor = WiFiVisionPalette.crimsonBg,
                    borderColor = WiFiVisionPalette.crimsonBorder,
                    dotColor = WiFiVisionPalette.crimsonText
                )
                statusBox.background = roundedDrawable(
                    context,
                    WiFiVisionPalette.crimsonBg,
                    WiFiVisionPalette.crimsonBorder,
                    12
                )
                statusDetailText.setTextColor(WiFiVisionPalette.crimsonText)
                statusDetailText.text = event.message
                if (release != null) showRetryButtons(release)
            }
        }
    }

    fun dismiss() {
        dialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        dialog = null
        isDownloadingOrVerifying = false
    }

    private fun showReadyButtons(release: AppReleaseMetadata) {
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.configure(
            label = "Update Now",
            iconRes = R.drawable.ic_upload_cloud,
            textColor = WiFiVisionPalette.pureWhite,
            fillColor = WiFiVisionPalette.emeraldDeep,
            strokeColor = WiFiVisionPalette.emeraldDeep,
            rippleColor = WiFiVisionPalette.rippleLight,
            radiusDp = 12,
            textSizeSp = 13f
        )
        primaryBtn.setOnClickListener { onUpdateNowClick(release) }

        secondaryBtn.visibility = View.VISIBLE
        secondaryBtn.configure(
            label = "Later",
            textColor = WiFiVisionPalette.deepGold,
            fillColor = WiFiVisionPalette.softGoldBg,
            strokeColor = WiFiVisionPalette.goldBorder,
            rippleColor = WiFiVisionPalette.rippleDark,
            radiusDp = 12,
            textSizeSp = 13f
        )
        secondaryBtn.setOnClickListener {
            onLaterClick(release.versionCode)
            dismiss()
        }
    }

    private fun showRetryButtons(release: AppReleaseMetadata) {
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.configure(
            label = "Retry Update",
            textColor = WiFiVisionPalette.pureWhite,
            fillColor = WiFiVisionPalette.emeraldDeep,
            strokeColor = WiFiVisionPalette.emeraldDeep,
            rippleColor = WiFiVisionPalette.rippleLight,
            radiusDp = 12,
            textSizeSp = 12.5f
        )
        primaryBtn.setOnClickListener { onUpdateNowClick(release) }

        secondaryBtn.visibility = View.VISIBLE
        secondaryBtn.configure(
            label = "Later",
            textColor = WiFiVisionPalette.bodySlate,
            fillColor = WiFiVisionPalette.softIvory,
            strokeColor = WiFiVisionPalette.hairline,
            rippleColor = WiFiVisionPalette.rippleDark,
            radiusDp = 12,
            textSizeSp = 12.5f
        )
        secondaryBtn.setOnClickListener {
            onLaterClick(release.versionCode)
            dismiss()
        }
    }

    private fun configureCancelable(cancelable: Boolean, dismissVersionCode: Int?) {
        val dlg = dialog ?: return
        dlg.setCancelable(cancelable)
        dlg.setCanceledOnTouchOutside(cancelable)
        dlg.setOnCancelListener {
            if (cancelable && dismissVersionCode != null) {
                onLaterClick(dismissVersionCode)
            }
        }
    }

    private fun showDialogIfHidden() {
        val dlg = dialog ?: return
        if (!dlg.isShowing) {
            dlg.show()
            dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun ensureDialogCreated() {
        if (dialog != null) return

        val outerCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                context,
                WiFiVisionPalette.pureWhite,
                WiFiVisionPalette.champagneGold,
                18,
                strokeWidthDp = 1
            )
        }

        // Subtle emerald-to-champagne-gold top accent bar
        val accentStrip = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    WiFiVisionPalette.emeraldDeep,
                    WiFiVisionPalette.jade,
                    WiFiVisionPalette.champagneGold
                )
            ).apply {
                val r = context.dpF(18f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        outerCard.addView(
            accentStrip,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(6))
        )

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20), context.dp(18), context.dp(20), context.dp(18))
        }

        badgePill = createPill(
            context,
            label = "UPDATE AVAILABLE",
            textColor = WiFiVisionPalette.emeraldDeep,
            bgColor = WiFiVisionPalette.emeraldTint,
            borderColor = WiFiVisionPalette.emeraldBorder,
            dotColor = WiFiVisionPalette.jade
        )

        titleText = TextView(context).apply {
            textSize = 18.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(10), 0, context.dp(4))
        }

        subtitleText = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, 0, 0, context.dp(12))
        }

        notesBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(
                context,
                WiFiVisionPalette.warmIvory,
                WiFiVisionPalette.hairline,
                12
            )
        }
        notesBox.addView(createSectionTitle(context, "WHAT'S NEW"))
        notesText = TextView(context).apply {
            textSize = 12.5f
            setLineSpacing(context.dpF(3f), 1f)
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(6), 0, 0)
        }
        notesBox.addView(notesText)

        statusBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(
                context,
                WiFiVisionPalette.warmIvory,
                WiFiVisionPalette.hairline,
                12
            )
        }
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(WiFiVisionPalette.emeraldDeep)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(WiFiVisionPalette.jade)
        }
        statusDetailText = TextView(context).apply {
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(4), 0, 0)
        }
        statusBox.addView(
            progressBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(8))
        )
        statusBox.addView(statusDetailText)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(14), 0, 0)
        }
        secondaryBtn = WiFiActionButton(context)
        primaryBtn = WiFiActionButton(context)
        btnRow.addView(
            secondaryBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(6)
            }
        )
        btnRow.addView(
            primaryBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.25f).apply {
                marginStart = context.dp(6)
            }
        )

        scrollContent.addView(
            badgePill,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        scrollContent.addView(titleText)
        scrollContent.addView(subtitleText)
        scrollContent.addView(
            notesBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dp(10) }
        )
        scrollContent.addView(
            statusBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        scrollContent.addView(btnRow)

        outerCard.addView(ScrollView(context).apply { addView(scrollContent) })

        dialog = AlertDialog.Builder(context)
            .setView(outerCard)
            .create()
    }
}
