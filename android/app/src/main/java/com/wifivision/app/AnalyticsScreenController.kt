package com.wifivision.app

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

class AnalyticsScreenController(
    private val context: Context,
    initialBand: BandFilter,
    initialHistoryLimit: Int,
    private val onRfBandChanged: (BandFilter) -> Unit,
    private val onRefreshRfClick: (BandFilter) -> Unit,
    private val onRunSpeedTestClick: () -> Unit,
    private val onRefreshPerfClick: (Int) -> Unit,
    private val onPerfLimitChanged: (Int) -> Unit
) {

    enum class SubMode {
        NETWORK_ANALYTICS,
        PERFORMANCE
    }

    val rootView: ScrollView
    private val contentCol: LinearLayout

    private var activeSubMode: SubMode = SubMode.NETWORK_ANALYTICS
    private val rfTabBtn: WiFiActionButton
    private val perfTabBtn: WiFiActionButton
    private val rfPanel: LinearLayout
    private val perfPanel: LinearLayout

    // Network Analytics state & views
    private var rfBand: BandFilter = initialBand
    private val rfBandRow: LinearLayout
    private val rfRefreshBtn: WiFiActionButton
    private val rfErrorBanner: LinearLayout
    private val rfErrorText: TextView

    private val kpiRfTotal: TextView
    private val kpiRfAvgSig: TextView
    private val kpiRfStrongest: TextView
    private val kpiRfWeakest: TextView
    private val kpiRfQuality: TextView

    private val bandDonutChart: DonutChartView
    private val securityBarChart: HorizontalBarChartView
    private val qualityBarChart: HorizontalBarChartView
    private val qualityFactorChart: HorizontalBarChartView
    private val rssiBucketChart: HorizontalBarChartView

    // Performance state & views
    enum class PerfTimeRange(val label: String) {
        LATEST("Latest Test"),
        DAYS_7("7 Days"),
        DAYS_30("30 Days")
    }

    private var historyLimit: Int = initialHistoryLimit
    private var perfTimeRange: PerfTimeRange = PerfTimeRange.DAYS_30
    private var isSpeedTestRunning = false
    private var cachedAnalytics: PerformanceAnalytics? = null
    private var cachedHistory: List<PerformanceMeasurementRecord> = emptyList()
    private var cachedTrends: List<PerformanceTrendPoint> = emptyList()
    private var cachedPerfError: String? = null

    private val runSpeedTestBtn: WiFiActionButton
    private val perfRefreshBtn: WiFiActionButton
    private val perfLimitRow: LinearLayout
    private val perfRangeRow: LinearLayout
    private val perfStatusBanner: LinearLayout
    private val perfStatusText: TextView
    private val latestTestCard: LinearLayout
    private val latestTestSummary: TextView

    private val latestStatusPill: TextView
    private val latestDownVal: TextView
    private val latestUpVal: TextView
    private val latestSpeedMeta: TextView
    private val latestLatencyVal: TextView
    private val latestJitterVal: TextView
    private val latestLossVal: TextView

    private val kpiPerfDown: TextView
    private val kpiPerfDownPeak: TextView
    private val kpiPerfUp: TextView
    private val kpiPerfUpPeak: TextView
    private val kpiPerfLatency: TextView
    private val kpiPerfLatencyMedian: TextView
    private val kpiPerfJitter: TextView
    private val kpiPerfLoss: TextView
    private val kpiPerfTotalTests: TextView

    private val throughputEmptyNote: TextView
    private val latencyEmptyNote: TextView
    private val throughputTrendChart: TrendLineChartView
    private val latencyTrendChart: TrendLineChartView
    private val perfHistoryContainer: LinearLayout

    init {
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(24))
        }
        rootView = ScrollView(context).apply {
            addView(contentCol)
        }

        val switchCard = createCard(context, paddingDp = 6)
        val switchRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        rfTabBtn = WiFiActionButton(context).apply {
            setOnClickListener { switchSubMode(SubMode.NETWORK_ANALYTICS) }
        }
        perfTabBtn = WiFiActionButton(context).apply {
            setOnClickListener { switchSubMode(SubMode.PERFORMANCE) }
        }
        switchRow.addView(rfTabBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(4) })
        switchRow.addView(perfTabBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(4) })
        switchCard.addView(switchRow)

        // =====================================================================
        // PANEL 1: NETWORK ANALYTICS
        // =====================================================================
        rfPanel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val rfCtrlCard = createCard(context, paddingDp = 14)
        val rfHdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rfHdr.addView(createSectionTitle(context, "RF & QUALITY ANALYTICS").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        rfRefreshBtn = WiFiActionButton(context).apply {
            configure("Refresh", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 11.5f)
            setOnClickListener { onRefreshRfClick(rfBand) }
        }
        rfHdr.addView(rfRefreshBtn)
        rfBandRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        rfCtrlCard.addView(rfHdr)
        rfCtrlCard.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, context.dp(10), 0, 0)
            addView(rfBandRow)
        })

        rfErrorBanner = LinearLayout(context).apply {
            visibility = View.GONE
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder, 12)
        }
        rfErrorText = TextView(context).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.crimsonText)
        }
        rfErrorBanner.addView(rfErrorText)

        val (rk1, rkv1) = buildMetricCard("TOTAL SCANS", "—")
        kpiRfTotal = rkv1
        val (rk2, rkv2) = buildMetricCard("AVG RSSI", "—")
        kpiRfAvgSig = rkv2
        val (rk3, rkv3) = buildMetricCard("STRONGEST", "—")
        kpiRfStrongest = rkv3
        val (rk4, rkv4) = buildMetricCard("WEAKEST", "—")
        kpiRfWeakest = rkv4
        val qualityCard = createCard(context, paddingDp = 14)
        qualityCard.addView(createSectionTitle(context, "COMPOSITE NETWORK QUALITY"))
        kpiRfQuality = TextView(context).apply {
            text = "—"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(6), 0, 0)
        }
        qualityCard.addView(kpiRfQuality)

        val donutCard = createCard(context, paddingDp = 14)
        donutCard.addView(createSectionTitle(context, "FREQUENCY BAND DISTRIBUTION (ALL SCANS)"))
        bandDonutChart = DonutChartView(context)
        donutCard.addView(bandDonutChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })

        val securityCard = createCard(context, paddingDp = 14)
        securityCard.addView(createSectionTitle(context, "SECURITY PROTOCOL DISTRIBUTION"))
        securityBarChart = HorizontalBarChartView(context)
        securityCard.addView(securityBarChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })

        val qualChartCard = createCard(context, paddingDp = 14)
        qualChartCard.addView(createSectionTitle(context, "NETWORK QUALITY CLASSIFICATION"))
        qualityBarChart = HorizontalBarChartView(context)
        qualChartCard.addView(qualityBarChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        qualChartCard.addView(createSectionTitle(context, "QUALITY SCORE FACTOR BREAKDOWN (AVG / 100)").apply {
            setPadding(0, context.dp(12), 0, context.dp(4))
        })
        qualityFactorChart = HorizontalBarChartView(context)
        qualChartCard.addView(qualityFactorChart)

        val rssiCard = createCard(context, paddingDp = 14)
        rssiCard.addView(createSectionTitle(context, "SIGNAL STRENGTH (RSSI) DISTRIBUTION"))
        rssiBucketChart = HorizontalBarChartView(context)
        rssiCard.addView(rssiBucketChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })

        rfPanel.addView(rfCtrlCard, marginParams(10))
        rfPanel.addView(rfErrorBanner, marginParams(10))
        rfPanel.addView(UiFormatters.twoColumnRow(context, rk1, rk2), marginParams(10))
        rfPanel.addView(UiFormatters.twoColumnRow(context, rk3, rk4), marginParams(10))
        rfPanel.addView(qualityCard, marginParams(10))
        rfPanel.addView(donutCard, marginParams(10))
        rfPanel.addView(securityCard, marginParams(10))
        rfPanel.addView(qualChartCard, marginParams(10))
        rfPanel.addView(rssiCard)

        // =====================================================================
        // PANEL 2: PERFORMANCE ANALYTICS & SPEED TEST
        // =====================================================================
        perfPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val speedCard = createCard(context, paddingDp = 14)
        speedCard.addView(createSectionTitle(context, "INTERNET PERFORMANCE DIAGNOSTICS"))
        speedCard.addView(TextView(context).apply {
            text = "Measures Cloudflare download, upload, median latency, jitter, and 20-packet HTTP request loss and saves results to your workspace."
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(4), 0, context.dp(12))
        })
        runSpeedTestBtn = WiFiActionButton(context).apply {
            configure("Run Speed & Packet Loss Test", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 12, textSizeSp = 13.5f)
            setOnClickListener { onRunSpeedTestClick() }
        }
        val perfToolsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(10), 0, 0)
        }
        perfLimitRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        perfRefreshBtn = WiFiActionButton(context).apply {
            configure("Refresh", textColor = WiFiVisionPalette.emeraldDeep, fillColor = WiFiVisionPalette.emeraldTint, strokeColor = WiFiVisionPalette.emeraldBorder, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 11.5f)
            setOnClickListener { onRefreshPerfClick(historyLimit) }
        }
        perfToolsRow.addView(perfLimitRow)
        perfToolsRow.addView(perfRefreshBtn)

        speedCard.addView(runSpeedTestBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        speedCard.addView(perfToolsRow)

        perfStatusBanner = LinearLayout(context).apply {
            visibility = View.GONE
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, 12)
        }
        perfStatusText = TextView(context).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
        }
        perfStatusBanner.addView(perfStatusText)

        latestTestCard = createCard(context, fillColor = WiFiVisionPalette.emeraldTint, strokeColor = WiFiVisionPalette.emeraldBorder, paddingDp = 14).apply {
            visibility = View.GONE
        }
        latestTestCard.addView(createSectionTitle(context, "LATEST SPEED TEST RESULT"))
        latestTestSummary = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.emeraldDeep)
            setPadding(0, context.dp(6), 0, 0)
        }
        latestTestCard.addView(latestTestSummary)

        val speedSummaryCard = createCard(context, paddingDp = 14)
        val speedHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        speedHeaderRow.addView(createSectionTitle(context, "A. INTERNET SPEED (LATEST TEST)").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        latestStatusPill = createPill(context, "No Test Yet", WiFiVisionPalette.mutedSlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        speedHeaderRow.addView(latestStatusPill)
        speedSummaryCard.addView(speedHeaderRow)

        val latestSpeedRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(10), 0, context.dp(6))
        }
        val downBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, context.dp(8), 0)
        }
        downBox.addView(TextView(context).apply {
            text = "DOWNLOAD SPEED"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.mutedSlate)
        })
        latestDownVal = TextView(context).apply {
            text = "—"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.emeraldDeep)
            setPadding(0, context.dp(2), 0, context.dp(2))
        }
        downBox.addView(latestDownVal)
        downBox.addView(TextView(context).apply {
            text = "How quickly data comes to your device (videos, downloads, pages)."; textSize = 11f; setTextColor(WiFiVisionPalette.bodySlate)
        })

        val upBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(context.dp(8), 0, 0, 0)
        }
        upBox.addView(TextView(context).apply {
            text = "UPLOAD SPEED"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.mutedSlate)
        })
        latestUpVal = TextView(context).apply {
            text = "—"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.deepGold)
            setPadding(0, context.dp(2), 0, context.dp(2))
        }
        upBox.addView(latestUpVal)
        upBox.addView(TextView(context).apply {
            text = "How quickly your device sends data (calls, backups, uploads)."; textSize = 11f; setTextColor(WiFiVisionPalette.bodySlate)
        })
        latestSpeedRow.addView(downBox)
        latestSpeedRow.addView(upBox)
        speedSummaryCard.addView(latestSpeedRow)
        latestSpeedMeta = TextView(context).apply {
            text = "Measured: No speed test recorded yet"
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(4), 0, 0)
        }
        speedSummaryCard.addView(latestSpeedMeta)

        val respCard = createCard(context, paddingDp = 14)
        respCard.addView(createSectionTitle(context, "B. CONNECTION RESPONSIVENESS (LATEST TEST)"))
        val respGrid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, 0)
        }
        fun buildRespRow(title: String, hint: String): Pair<LinearLayout, TextView> {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(5), 0, context.dp(5))
            }
            val left = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(10)
                }
            }
            left.addView(TextView(context).apply {
                text = title; textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            })
            left.addView(TextView(context).apply {
                text = hint; textSize = 11f; setTextColor(WiFiVisionPalette.bodySlate)
            })
            val valView = TextView(context).apply {
                text = "—"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            }
            row.addView(left)
            row.addView(valView)
            return row to valView
        }
        val (latRow, latV) = buildRespRow("Latency (ms)", "Lower is generally better — how fast a server responds.")
        latestLatencyVal = latV
        val (jitRow, jitV) = buildRespRow("Jitter (ms)", "Lower is generally better — how steady response times remain.")
        latestJitterVal = jitV
        val (lossRow, lossV) = buildRespRow("Packet Loss (%)", "0% is ideal — any loss can cause stutters or dropped calls.")
        latestLossVal = lossV
        respGrid.addView(latRow)
        respGrid.addView(jitRow)
        respGrid.addView(lossRow)
        respCard.addView(respGrid)

        val (pk1, pkv1, pks1) = buildPerfKpiCard("AVG DOWNLOAD", "—", "Peak: —")
        kpiPerfDown = pkv1; kpiPerfDownPeak = pks1
        val (pk2, pkv2, pks2) = buildPerfKpiCard("AVG UPLOAD", "—", "Peak: —")
        kpiPerfUp = pkv2; kpiPerfUpPeak = pks2
        val (pk3, pkv3, pks3) = buildPerfKpiCard("AVG LATENCY", "—", "Median: —")
        kpiPerfLatency = pkv3; kpiPerfLatencyMedian = pks3
        val (pk4, pkv4) = buildMetricCard("AVG JITTER", "—")
        kpiPerfJitter = pkv4
        val (pk5, pkv5) = buildMetricCard("PACKET LOSS", "—")
        kpiPerfLoss = pkv5
        val (pk6, pkv6) = buildMetricCard("TOTAL TESTS", "0")
        kpiPerfTotalTests = pkv6

        val rangeCard = createCard(context, paddingDp = 12)
        rangeCard.addView(createSectionTitle(context, "C. PERFORMANCE HISTORY RANGE"))
        perfRangeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, context.dp(8), 0, 0)
        }
        rangeCard.addView(perfRangeRow)

        val tpCard = createCard(context, paddingDp = 14)
        tpCard.addView(createSectionTitle(context, "DOWNLOAD & UPLOAD SPEED HISTORY (MBPS)"))
        throughputEmptyNote = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.mutedSlate)
            visibility = View.GONE
            setPadding(0, context.dp(6), 0, 0)
        }
        tpCard.addView(throughputEmptyNote)
        throughputTrendChart = TrendLineChartView(context)
        tpCard.addView(throughputTrendChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })

        val latCard = createCard(context, paddingDp = 14)
        latCard.addView(createSectionTitle(context, "LATENCY, JITTER & PACKET LOSS HISTORY (MS / %)"))
        latencyEmptyNote = TextView(context).apply {
            textSize = 12f
            setTextColor(WiFiVisionPalette.mutedSlate)
            visibility = View.GONE
            setPadding(0, context.dp(6), 0, 0)
        }
        latCard.addView(latencyEmptyNote)
        latencyTrendChart = TrendLineChartView(context)
        latCard.addView(latencyTrendChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })

        val historyCard = createCard(context, paddingDp = 14)
        historyCard.addView(createSectionTitle(context, "HISTORICAL MEASUREMENTS (PAST TESTS)"))
        perfHistoryContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, 0)
        }
        historyCard.addView(perfHistoryContainer)

        val signalVsSpeedCard = createCard(context, fillColor = WiFiVisionPalette.softIvory, paddingDp = 14)
        signalVsSpeedCard.addView(createSectionTitle(context, "D. WI-FI SIGNAL VS INTERNET SPEED"))
        signalVsSpeedCard.addView(TextView(context).apply {
            text = "Wi-Fi signal strength measures the wireless connection between your phone and the router. Internet speed measures how fast your internet service moves data between the router and the web. You can have full Wi-Fi bars with slow internet, or fast internet slowed down by a distant router."
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, context.dp(6), 0, 0)
        })

        perfPanel.addView(speedCard, marginParams(10))
        perfPanel.addView(perfStatusBanner, marginParams(10))
        perfPanel.addView(latestTestCard, marginParams(10))
        perfPanel.addView(speedSummaryCard, marginParams(10))
        perfPanel.addView(respCard, marginParams(10))
        perfPanel.addView(rangeCard, marginParams(10))
        perfPanel.addView(UiFormatters.twoColumnRow(context, pk1, pk2), marginParams(10))
        perfPanel.addView(UiFormatters.twoColumnRow(context, pk3, pk4), marginParams(10))
        perfPanel.addView(UiFormatters.twoColumnRow(context, pk5, pk6), marginParams(10))
        perfPanel.addView(tpCard, marginParams(10))
        perfPanel.addView(latCard, marginParams(10))
        perfPanel.addView(historyCard, marginParams(10))
        perfPanel.addView(signalVsSpeedCard)

        contentCol.addView(switchCard, marginParams(12))
        contentCol.addView(rfPanel)
        contentCol.addView(perfPanel)

        switchSubMode(SubMode.NETWORK_ANALYTICS)
        renderRfBandChips()
        renderLimitChips()
        renderRangeChips()
    }

    private fun buildMetricCard(title: String, value: String): Pair<LinearLayout, TextView> {
        val card = createCard(context, paddingDp = 13)
        card.addView(TextView(context).apply {
            text = title; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.mutedSlate)
        })
        val v = TextView(context).apply {
            text = value; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(4), 0, 0)
        }
        card.addView(v)
        return card to v
    }

    private fun buildPerfKpiCard(title: String, value: String, sub: String): Triple<LinearLayout, TextView, TextView> {
        val card = createCard(context, paddingDp = 13)
        card.addView(TextView(context).apply {
            text = title; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.mutedSlate)
        })
        val v = TextView(context).apply {
            text = value; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(4), 0, context.dp(2))
        }
        val s = TextView(context).apply {
            text = sub; textSize = 11f; setTextColor(WiFiVisionPalette.bodySlate)
        }
        card.addView(v); card.addView(s)
        return Triple(card, v, s)
    }

    fun switchSubMode(mode: SubMode) {
        activeSubMode = mode
        rfPanel.visibility = if (mode == SubMode.NETWORK_ANALYTICS) View.VISIBLE else View.GONE
        perfPanel.visibility = if (mode == SubMode.PERFORMANCE) View.VISIBLE else View.GONE

        if (mode == SubMode.NETWORK_ANALYTICS) {
            rfTabBtn.configure("Network Analytics", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12.5f)
            perfTabBtn.configure("Performance", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12.5f)
        } else {
            rfTabBtn.configure("Network Analytics", textColor = WiFiVisionPalette.bodySlate, fillColor = WiFiVisionPalette.softIvory, strokeColor = WiFiVisionPalette.hairline, rippleColor = WiFiVisionPalette.rippleDark, radiusDp = 10, textSizeSp = 12.5f)
            perfTabBtn.configure("Performance", textColor = WiFiVisionPalette.pureWhite, fillColor = WiFiVisionPalette.emeraldDeep, strokeColor = WiFiVisionPalette.emeraldDeep, rippleColor = WiFiVisionPalette.rippleLight, radiusDp = 10, textSizeSp = 12.5f)
        }
    }

    fun setDefaultPreferences(band: BandFilter, limit: Int) {
        rfBand = band
        historyLimit = limit
        renderRfBandChips()
        renderLimitChips()
    }

    fun setRfLoading(loading: Boolean) {
        rfRefreshBtn.isEnabled = !loading
        rfRefreshBtn.text = if (loading) "Loading..." else "Refresh"
    }

    fun setRfAnalyticsData(
        allBandStats: ObservationAnalytics?,
        scopedStats: ObservationAnalytics?,
        qualityResponse: NetworkQualityResponse?,
        sampleObservations: List<CloudObservation>,
        error: String?
    ) {
        if (error != null) {
            rfErrorText.text = error
            rfErrorBanner.visibility = View.VISIBLE
        } else {
            rfErrorBanner.visibility = View.GONE
        }

        val s = scopedStats ?: allBandStats
        kpiRfTotal.text = s?.totalNetworks?.toString() ?: "—"
        kpiRfAvgSig.text = UiFormatters.formatSignal(s?.averageSignal)
        kpiRfStrongest.text = UiFormatters.formatSignalInt(s?.strongestSignal)
        kpiRfWeakest.text = UiFormatters.formatSignalInt(s?.weakestSignal)

        // Frequency band donut (always from allBandStats)
        val bandColors = mapOf(
            "2.4 GHz" to WiFiVisionPalette.champagneGold,
            "5 GHz" to WiFiVisionPalette.emeraldDeep,
            "6 GHz" to WiFiVisionPalette.jade,
            "Unknown" to WiFiVisionPalette.mutedSlate
        )
        val bandSlices = (allBandStats?.bands ?: emptyList()).map { b ->
            DonutChartView.Slice(b.label, b.count, bandColors[b.label] ?: WiFiVisionPalette.bodySlate)
        }
        bandDonutChart.setData(bandSlices, "Scans")

        // Security protocol horizontal bars (scoped)
        val secEntries = (s?.security ?: emptyList())
            .sortedByDescending { it.count }
            .map { sec ->
                val color = when {
                    sec.label.equals("WPA3", true) -> WiFiVisionPalette.emeraldDeep
                    sec.label.equals("WPA2", true) -> WiFiVisionPalette.jade
                    sec.label.equals("WPA", true) -> WiFiVisionPalette.champagneGold
                    else -> WiFiVisionPalette.crimsonText
                }
                HorizontalBarChartView.BarEntry(sec.label, sec.count.toFloat(), sec.count.toString(), color)
            }
        securityBarChart.setEntries(secEntries, "No security distribution data")

        // Filter quality items and sample observations by rfBand
        val allQuality = qualityResponse?.networks ?: emptyList()
        val scopedQuality = if (rfBand == BandFilter.ALL) {
            allQuality
        } else {
            allQuality.filter { it.quality.details.band.equals(rfBand.label, true) }
        }

        if (scopedQuality.isNotEmpty()) {
            val avgScore = (scopedQuality.sumOf { it.quality.score }.toDouble() / scopedQuality.size).roundToInt()
            val cls = UiFormatters.classifyScoreLabel(avgScore)
            kpiRfQuality.text = "$avgScore / 100 ($cls • ${scopedQuality.size} scored)"

            val gradeOrder = listOf(
                "Excellent" to WiFiVisionPalette.emeraldDeep,
                "Good" to WiFiVisionPalette.jade,
                "Fair" to WiFiVisionPalette.champagneGold,
                "Poor" to WiFiVisionPalette.crimsonText
            )
            qualityBarChart.setEntries(
                gradeOrder.map { (grade, color) ->
                    val cnt = scopedQuality.count { it.quality.classification.equals(grade, true) }
                    HorizontalBarChartView.BarEntry(grade, cnt.toFloat(), cnt.toString(), color)
                },
                "No quality classifications available"
            )

            val avgSigFactor = scopedQuality.map { it.quality.components.signalScore }.average().toFloat()
            val avgLevelFactor = scopedQuality.map { it.quality.components.signalLevelScore }.average().toFloat()
            val avgBandFactor = scopedQuality.map { it.quality.components.bandScore }.average().toFloat()
            val avgSecFactor = scopedQuality.map { it.quality.components.securityScore }.average().toFloat()
            qualityFactorChart.setEntries(
                listOf(
                    HorizontalBarChartView.BarEntry("Signal Power (70%)", avgSigFactor, String.format(Locale.US, "%.0f/100", avgSigFactor), WiFiVisionPalette.emeraldDeep, maxOverride = 100f),
                    HorizontalBarChartView.BarEntry("Signal Level (15%)", avgLevelFactor, String.format(Locale.US, "%.0f/100", avgLevelFactor), WiFiVisionPalette.jade, maxOverride = 100f),
                    HorizontalBarChartView.BarEntry("Band Capability (5%)", avgBandFactor, String.format(Locale.US, "%.0f/100", avgBandFactor), WiFiVisionPalette.champagneGold, maxOverride = 100f),
                    HorizontalBarChartView.BarEntry("Security Posture (10%)", avgSecFactor, String.format(Locale.US, "%.0f/100", avgSecFactor), WiFiVisionPalette.deepGold, maxOverride = 100f)
                )
            )
        } else {
            kpiRfQuality.text = "—"
            qualityBarChart.setEntries(emptyList(), "No scored networks for ${rfBand.label}")
            qualityFactorChart.setEntries(emptyList(), "No factor data for ${rfBand.label}")
        }

        // RSSI Bucket Distribution from sampleObservations filtered by rfBand
        val scopedObs = if (rfBand == BandFilter.ALL) {
            sampleObservations
        } else {
            sampleObservations.filter { it.band.equals(rfBand.label, true) }
        }
        if (scopedObs.isNotEmpty()) {
            val b1 = scopedObs.count { it.signalDbm >= -50 }
            val b2 = scopedObs.count { it.signalDbm in -60..-51 }
            val b3 = scopedObs.count { it.signalDbm in -70..-61 }
            val b4 = scopedObs.count { it.signalDbm in -80..-71 }
            val b5 = scopedObs.count { it.signalDbm < -80 }
            rssiBucketChart.setEntries(
                listOf(
                    HorizontalBarChartView.BarEntry("Excellent (>= -50 dBm)", b1.toFloat(), b1.toString(), WiFiVisionPalette.emeraldDeep),
                    HorizontalBarChartView.BarEntry("Strong (-51 to -60 dBm)", b2.toFloat(), b2.toString(), WiFiVisionPalette.jade),
                    HorizontalBarChartView.BarEntry("Moderate (-61 to -70 dBm)", b3.toFloat(), b3.toString(), WiFiVisionPalette.champagneGold),
                    HorizontalBarChartView.BarEntry("Fair (-71 to -80 dBm)", b4.toFloat(), b4.toString(), WiFiVisionPalette.amberText),
                    HorizontalBarChartView.BarEntry("Weak (< -80 dBm)", b5.toFloat(), b5.toString(), WiFiVisionPalette.crimsonText)
                )
            )
        } else {
            rssiBucketChart.setEntries(emptyList(), "No observation samples for ${rfBand.label}")
        }
    }

    fun setPerfLoading(loading: Boolean) {
        perfRefreshBtn.isEnabled = !loading
        perfRefreshBtn.text = if (loading) "Loading..." else "Refresh"
    }

    fun setSpeedTestRunning(running: Boolean) {
        isSpeedTestRunning = running
        runSpeedTestBtn.isEnabled = !running
        runSpeedTestBtn.text = if (running) "Running Speed & Loss Test..." else "Run Speed & Packet Loss Test"
        if (running) {
            showPerfBanner("Testing Cloudflare download, upload, latency & 20-packet HTTP loss (takes ~15-30s)...", isError = false)
        }
    }

    fun showSpeedTestResult(result: ApiResult<RunPerformanceTestResponse>) {
        when (result) {
            is ApiResult.Success -> {
                val p = result.data.performance
                showPerfBanner(result.data.message, isError = false, isSuccess = true)
                latestTestSummary.text = String.format(
                    Locale.US,
                    "Down %.1f Mbps • Up %.1f Mbps • Latency %.1f ms • Jitter %.1f ms • Packet Loss %.1f%% (%d/%d ok)",
                    p.downloadMbps, p.uploadMbps, p.latencyMs, p.jitterMs,
                    p.packetLoss.packetLossPercentage, p.packetLoss.successfulPackets, p.packetLoss.totalPackets
                )
                latestTestCard.visibility = View.VISIBLE
            }
            is ApiResult.RateLimited -> {
                val wait = result.retryAfterSeconds?.let { " Retry after ${it}s." } ?: ""
                showPerfBanner("${result.message}$wait", isError = true)
            }
            else -> {
                val msg = when (result) {
                    is ApiResult.Unauthorized -> result.message
                    is ApiResult.Forbidden -> result.message
                    is ApiResult.ClientError -> result.message
                    is ApiResult.ServerError -> result.message
                    is ApiResult.NetworkError -> result.message
                    else -> "Speed test failed."
                }
                showPerfBanner(msg, isError = true)
            }
        }
    }

    fun setPerformanceData(
        analytics: PerformanceAnalytics?,
        history: List<PerformanceMeasurementRecord>,
        trends: List<PerformanceTrendPoint>,
        error: String?
    ) {
        cachedAnalytics = analytics
        cachedHistory = history
        cachedTrends = trends
        cachedPerfError = error
        renderPerformanceData()
    }

    private fun renderPerformanceData() {
        val analytics = cachedAnalytics
        val history = cachedHistory
        val trends = cachedTrends
        val error = cachedPerfError

        if (error != null && !isSpeedTestRunning) {
            showPerfBanner(error, isError = true)
        }

        val latest = history.firstOrNull()
        if (latest != null) {
            latestDownVal.text = UiFormatters.formatMbps(latest.downloadMbps)
            latestUpVal.text = UiFormatters.formatMbps(latest.uploadMbps)
            latestSpeedMeta.text = "Measured: ${UiFormatters.formatIsoDateTime(latest.measuredAt)} • Server: ${latest.testServer ?: latest.source}"
            latestLatencyVal.text = UiFormatters.formatMs(latest.latencyMs)
            latestJitterVal.text = UiFormatters.formatMs(latest.jitterMs)
            latestLossVal.text = UiFormatters.formatPercent(latest.packetLoss.packetLossPercentage)

            val (statusLabel, tc, bg, bc) = when {
                latest.downloadMbps >= 25.0 && latest.latencyMs <= 60.0 && latest.packetLoss.packetLossPercentage <= 1.0 ->
                    Quad("Good", WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
                latest.downloadMbps < 5.0 || latest.latencyMs > 150.0 || latest.packetLoss.packetLossPercentage > 5.0 ->
                    Quad("Poor", WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
                else ->
                    Quad("Needs attention", WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            }
            updatePillStyle(context, latestStatusPill, statusLabel, tc, bg, bc)
        } else {
            latestDownVal.text = "—"
            latestUpVal.text = "—"
            latestSpeedMeta.text = "Measured: No speed test recorded yet"
            latestLatencyVal.text = "—"
            latestJitterVal.text = "—"
            latestLossVal.text = "—"
            updatePillStyle(context, latestStatusPill, "No Test Yet", WiFiVisionPalette.mutedSlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        }

        val nowMs = System.currentTimeMillis()
        val cutoffMs = when (perfTimeRange) {
            PerfTimeRange.LATEST -> null
            PerfTimeRange.DAYS_7 -> nowMs - (7L * 24L * 3600L * 1000L)
            PerfTimeRange.DAYS_30 -> nowMs - (30L * 24L * 3600L * 1000L)
        }

        val scopedHistory = when (perfTimeRange) {
            PerfTimeRange.LATEST -> history.take(1)
            else -> history.filter { rec ->
                val ms = parseIsoEpochMs(rec.measuredAt)
                cutoffMs == null || ms == null || ms >= cutoffMs
            }
        }
        val scopedTrends = when (perfTimeRange) {
            PerfTimeRange.LATEST -> trends.takeLast(1)
            else -> trends.filter { pt ->
                val ms = parseIsoEpochMs(pt.measuredAt)
                cutoffMs == null || ms == null || ms >= cutoffMs
            }
        }

        if (analytics != null && analytics.totalTests > 0) {
            kpiPerfDown.text = UiFormatters.formatMbps(analytics.averageDownloadMbps)
            kpiPerfDownPeak.text = "Peak: ${UiFormatters.formatMbps(analytics.fastestDownloadMbps)}"
            kpiPerfUp.text = UiFormatters.formatMbps(analytics.averageUploadMbps)
            kpiPerfUpPeak.text = "Peak: ${UiFormatters.formatMbps(analytics.fastestUploadMbps)}"
            kpiPerfLatency.text = UiFormatters.formatMs(analytics.averageLatencyMs)
            kpiPerfLatencyMedian.text = "Median: ${UiFormatters.formatMs(analytics.averageMedianLatencyMs)}"
            kpiPerfJitter.text = UiFormatters.formatMs(analytics.averageJitterMs)
            kpiPerfLoss.text = UiFormatters.formatPercent(analytics.averagePacketLossPercentage)
            kpiPerfTotalTests.text = analytics.totalTests.toString()
        } else {
            kpiPerfDown.text = "—"
            kpiPerfDownPeak.text = "Peak: —"
            kpiPerfUp.text = "—"
            kpiPerfUpPeak.text = "Peak: —"
            kpiPerfLatency.text = "—"
            kpiPerfLatencyMedian.text = "Median: —"
            kpiPerfJitter.text = "—"
            kpiPerfLoss.text = "—"
            kpiPerfTotalTests.text = (analytics?.totalTests ?: 0).toString()
        }

        if (scopedTrends.size < 2) {
            val emptyHint = if (perfTimeRange == PerfTimeRange.LATEST) {
                "Latest Test selected — view 7 Days or 30 Days to compare historical speed tests."
            } else {
                "Insufficient data in ${perfTimeRange.label}: run at least 2 speed tests to plot a trend."
            }
            throughputEmptyNote.text = emptyHint
            throughputEmptyNote.visibility = View.VISIBLE
            latencyEmptyNote.text = emptyHint
            latencyEmptyNote.visibility = View.VISIBLE
            throughputTrendChart.visibility = View.GONE
            latencyTrendChart.visibility = View.GONE
        } else {
            throughputEmptyNote.visibility = View.GONE
            latencyEmptyNote.visibility = View.GONE
            throughputTrendChart.visibility = View.VISIBLE
            latencyTrendChart.visibility = View.VISIBLE

            val xLabels = scopedTrends.map { UiFormatters.formatIsoShort(it.measuredAt) }
            throughputTrendChart.setData(
                series = listOf(
                    TrendLineChartView.Series("Download", WiFiVisionPalette.emeraldDeep, scopedTrends.map { it.downloadMbps.toFloat() }, fillArea = true),
                    TrendLineChartView.Series("Upload", WiFiVisionPalette.champagneGold, scopedTrends.map { it.uploadMbps.toFloat() }, fillArea = false)
                ),
                labels = xLabels,
                unit = "Mbps",
                emptyText = "Run a speed test to plot throughput trends"
            )
            latencyTrendChart.setData(
                series = listOf(
                    TrendLineChartView.Series("Latency", WiFiVisionPalette.jade, scopedTrends.map { it.latencyMs.toFloat() }, fillArea = true),
                    TrendLineChartView.Series("Jitter", WiFiVisionPalette.champagneGold, scopedTrends.map { it.jitterMs.toFloat() }),
                    TrendLineChartView.Series("Loss %", WiFiVisionPalette.crimsonText, scopedTrends.map { it.packetLossPercentage.toFloat() })
                ),
                labels = xLabels,
                unit = "ms / %",
                emptyText = "Run a speed test to plot latency & loss trends"
            )
        }

        perfHistoryContainer.removeAllViews()
        if (scopedHistory.isEmpty()) {
            perfHistoryContainer.addView(TextView(context).apply {
                text = "No speed tests recorded in ${perfTimeRange.label}. Tap Run Speed & Packet Loss Test above."
                textSize = 12.5f
                setTextColor(WiFiVisionPalette.mutedSlate)
                setPadding(0, context.dp(6), 0, context.dp(4))
            })
            return
        }

        scopedHistory.forEachIndexed { idx, rec ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
            val topLine = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topLine.addView(TextView(context).apply {
                text = String.format(Locale.US, "%.1f Mbps down • %.1f Mbps up", rec.downloadMbps, rec.uploadMbps)
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(WiFiVisionPalette.darkSlate)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            topLine.addView(createMetadataChip(context, rec.testServer ?: rec.source))

            val subLine = TextView(context).apply {
                text = String.format(
                    Locale.US,
                    "Latency %.1f ms • Jitter %.1f ms • Loss %.1f%% (%d/%d) • %s",
                    rec.latencyMs, rec.jitterMs, rec.packetLoss.packetLossPercentage,
                    rec.packetLoss.successfulPackets, rec.packetLoss.totalPackets,
                    UiFormatters.formatIsoDateTime(rec.measuredAt)
                )
                textSize = 11.5f
                setTextColor(WiFiVisionPalette.bodySlate)
                setPadding(0, context.dp(4), 0, 0)
            }
            row.addView(topLine)
            row.addView(subLine)
            perfHistoryContainer.addView(row)
            if (idx < scopedHistory.lastIndex) {
                perfHistoryContainer.addView(View(context).apply {
                    setBackgroundColor(WiFiVisionPalette.hairline)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1).coerceAtLeast(1)))
            }
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun parseIsoEpochMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(iso.trim()).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun renderRangeChips() {
        perfRangeRow.removeAllViews()
        PerfTimeRange.entries.forEach { range ->
            val selected = range == perfTimeRange
            val chip = createPillChip(range.label, selected) {
                if (perfTimeRange != range) {
                    perfTimeRange = range
                    renderRangeChips()
                    renderPerformanceData()
                }
            }
            perfRangeRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun showPerfBanner(message: String, isError: Boolean, isSuccess: Boolean = false) {
        val (tc, bg, bc) = when {
            isError -> Triple(WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
            isSuccess -> Triple(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            else -> Triple(WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        }
        perfStatusText.text = message
        perfStatusText.setTextColor(tc)
        perfStatusBanner.background = roundedDrawable(context, bg, bc, 12)
        perfStatusBanner.visibility = View.VISIBLE
    }

    private fun renderRfBandChips() {
        rfBandRow.removeAllViews()
        BandFilter.entries.forEach { band ->
            val selected = band == rfBand
            val chip = createPillChip(if (band == BandFilter.ALL) "All Bands" else band.label, selected) {
                if (rfBand != band) {
                    rfBand = band
                    renderRfBandChips()
                    onRfBandChanged(band)
                }
            }
            rfBandRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            })
        }
    }

    private fun renderLimitChips() {
        perfLimitRow.removeAllViews()
        listOf(20, 50, 100).forEach { lim ->
            val chip = createPillChip("Last $lim", lim == historyLimit) {
                if (historyLimit != lim) {
                    historyLimit = lim
                    renderLimitChips()
                    onPerfLimitChanged(lim)
                }
            }
            perfLimitRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(6)
            })
        }
    }

    private fun createPillChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.dp(11), context.dp(6), context.dp(11), context.dp(6))
            if (selected) {
                setTextColor(WiFiVisionPalette.pureWhite)
                background = rippleRoundedDrawable(context, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.rippleLight, 999)
            } else {
                setTextColor(WiFiVisionPalette.bodySlate)
                background = rippleRoundedDrawable(context, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, WiFiVisionPalette.rippleDark, 999)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun marginParams(bottomDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = context.dp(bottomDp)
        }
    }
}
