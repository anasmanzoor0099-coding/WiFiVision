package com.wifivision.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var scanner: WifiScanner
    private lateinit var permissionManager: WifiPermissionManager

    private lateinit var statusText: TextView
    private lateinit var networkContainer: LinearLayout
    private lateinit var scanButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val cyan = Color.rgb(0, 210, 230)
    private val backgroundColor = Color.rgb(10, 17, 29)
    private val surfaceColor = Color.rgb(19, 29, 45)
    private val white = Color.WHITE
    private val muted = Color.rgb(160, 175, 195)

    companion object {
        private const val LOCATION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scanner = WifiScanner(this)
        permissionManager = WifiPermissionManager(this)

        buildDashboard()
    }

    private fun buildDashboard() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(20, 24, 20, 20)
        }

        val title = TextView(this).apply {
            text = "WiFiVision"
            textSize = 28f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "NETWORK INTELLIGENCE"
            textSize = 11f
            letterSpacing = 0.2f
            setTextColor(muted)
        }

        root.addView(title)
        root.addView(subtitle)

        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 20)
        }

        val statusCard = createCard()

        statusText = TextView(this).apply {
            text = "Ready to scan"
            textSize = 16f
            setTextColor(white)
        }

        statusCard.addView(statusText)
        content.addView(statusCard)

        scanButton = Button(this).apply {
            text = "SCAN NEARBY NETWORKS"
            setTextColor(Color.BLACK)
            setBackgroundColor(cyan)
            textSize = 14f

            setOnClickListener {
                requestPermissionAndScan()
            }
        }

        content.addView(
            scanButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                58
            ).apply {
                topMargin = 18
                bottomMargin = 20
            }
        )

        val sectionTitle = TextView(this).apply {
            text = "DETECTED NETWORKS"
            textSize = 14f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
        }

        content.addView(sectionTitle)

        networkContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(networkContainer)

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 20)
            setBackgroundColor(surfaceColor)
        }
    }

    private fun requestPermissionAndScan() {

        if (!permissionManager.hasLocationPermission()) {

            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )

            return
        }

        if (!permissionManager.isWifiEnabled()) {
            statusText.text = "Please enable Wi-Fi"
            return
        }

        if (!permissionManager.isLocationEnabled()) {
            statusText.text = "Please enable device location"
            return
        }

        scanWifiNetworks()
    }

    @Deprecated("Use the platform permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == LOCATION_REQUEST_CODE) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionAndScan()
            } else {
                statusText.text = "Location permission denied"
            }
        }
    }

    private fun scanWifiNetworks() {

        statusText.text = "Scanning nearby networks..."
        scanButton.isEnabled = false

        val started = scanner.startScan()

        if (!started) {
            statusText.text =
                "Scan unavailable. Android may be limiting scan requests."
            scanButton.isEnabled = true
            return
        }

        handler.postDelayed({

            val networks = scanner.getAvailableNetworks()

            networkContainer.removeAllViews()

            if (networks.isEmpty()) {

                statusText.text =
                    "No networks found. Try again or check Wi-Fi settings."

            } else {

                statusText.text = "${networks.size} networks detected"

                networks.forEach { network ->
                    addNetworkCard(network)
                }
            }

            scanButton.isEnabled = true

        }, 2500)
    }

    private fun addNetworkCard(network: WifiNetwork) {

        val card = createCard()

        val name = TextView(this).apply {
            text = network.ssid
            textSize = 17f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }

        val details = TextView(this).apply {
            text = "${network.signalDbm} dBm  •  ${network.band}\n" +
                    "${network.frequencyMhz} MHz  •  ${network.security}"

            textSize = 12f
            setTextColor(muted)
            setPadding(0, 8, 0, 0)
        }

        val quality = TextView(this).apply {
            text = "Signal quality: ${network.signalQuality}/4"
            textSize = 12f
            setTextColor(cyan)
            setPadding(0, 8, 0, 0)
        }

        card.addView(name)
        card.addView(details)
        card.addView(quality)

        networkContainer.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
        )
    }
}