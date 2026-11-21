"use client";

import { useState } from "react";
import Link from "next/link";
import {
  Activity,
  CheckCircle2,
  Compass,
  Crosshair,
  Download,
  Gauge,
  HelpCircle,
  Info,
  Layers,
  MapPin,
  Radio,
  ShieldCheck,
  Signal,
  Smartphone,
  Wifi,
} from "lucide-react";

import {
  getAlternatingDirection,
  Reveal,
  SignalNetworkVisual,
  useReducedMotion,
} from "./MotionPrimitives";

const HERO_PILLARS = [
  {
    title: "3 Frequency Bands",
    subtitle: "2.4 GHz, 5 GHz, and 6 GHz telemetry visibility",
    badge: "RF Spectrum",
  },
  {
    title: "Local-First Scanning",
    subtitle: "Explicit user review and consent before any upload",
    badge: "Privacy",
  },
  {
    title: "GPS-Aware Mapping",
    subtitle: "Estimated coordinate precision radius per cell",
    badge: "Spatial",
  },
] as const;

const PROBLEM_FACTORS = [
  {
    title: "Wi-Fi Signal Strength",
    unit: "dBm (RSSI)",
    icon: Signal,
    accent: "text-[#2E7D5B] bg-[#2E7D5B]/10",
    description:
      "Measures radio power between your device and the local access point. Full bars only confirm proximity to the router, not upstream health.",
  },
  {
    title: "Internet Speed",
    unit: "Mbps",
    icon: Gauge,
    accent: "text-[#9F7C38] bg-[#C6A15B]/15",
    description:
      "Measures download and upload throughput across the wider internet path. Congestion or ISP throttling can bottleneck speed even with strong Wi-Fi.",
  },
  {
    title: "Latency",
    unit: "ms",
    icon: Activity,
    accent: "text-[#303640] bg-[#F1EFE9]",
    description:
      "Round-trip response time for requests. High latency causes sluggish web loads and delayed interactions regardless of signal strength.",
  },
  {
    title: "Jitter",
    unit: "ms variation",
    icon: Radio,
    accent: "text-[#6366F1] bg-[#6366F1]/10",
    description:
      "Timing fluctuation between consecutive packets. Unstable jitter degrades video calls, live audio, and real-time responsiveness.",
  },
  {
    title: "Packet Loss",
    unit: "% failed",
    icon: Crosshair,
    accent: "text-[#B83B3B] bg-[#B83B3B]/10",
    description:
      "Share of HTTP test requests that fail to complete. Even small percentages force retransmissions that stall video streams.",
  },
  {
    title: "Network Reliability",
    unit: "Consistency",
    icon: ShieldCheck,
    accent: "text-[#2E7D5B] bg-[#2E7D5B]/10",
    description:
      "Real-world stability depends on combining clean RF conditions, low channel congestion, and consistent upstream performance over time.",
  },
] as const;

const SOLUTION_FEATURES = [
  {
    title: "Wi-Fi Signal Observations",
    badge: "Telemetry",
    icon: Wifi,
    description:
      "Inspect recorded SSIDs, signal RSSI (dBm), 2.4 / 5 / 6 GHz channels, signal quality tiers, and security classifications captured on-device.",
  },
  {
    title: "Geographic Heatmap",
    badge: "Spatial",
    icon: MapPin,
    description:
      "Explore 0.001-degree grid cells colored by band-specific signal strength or observation density with interactive cell popups.",
  },
  {
    title: "Signal Quality Visualization",
    badge: "Scoring",
    icon: ShieldCheck,
    description:
      "Transparent composite scoring combining recorded signal level, frequency band capability, and observed security posture.",
  },
  {
    title: "Network Density Intelligence",
    badge: "Spectrum",
    icon: Layers,
    description:
      "Distinguish sample concentration (Low / Medium / High) and unique SSIDs per cell separately from signal strength dBm.",
  },
  {
    title: "Internet Performance Analytics",
    badge: "Diagnostics",
    icon: Gauge,
    description:
      "Track download, upload, average and median latency, jitter, and HTTP application-level packet loss history.",
  },
  {
    title: "Location-Aware GPS Precision",
    badge: "Accuracy",
    icon: Compass,
    description:
      "Surface per-cell average GPS uncertainty radius (± meters) and clearly separate high-precision fixes from coarse (> 50 m) estimates.",
  },
] as const;

const WORKFLOW_STEPS = [
  {
    step: "01",
    title: "Scan",
    summary: "Collect local Wi-Fi observations",
    detail:
      "Use SCAN ONLY or local monitoring on the Android companion app to inspect nearby Wi-Fi networks, RSSI, frequency, security capabilities, and an optional device GPS fix—without automatic uploads.",
    highlight: "Auto-upload stays OFF; you review before confirming upload.",
  },
  {
    step: "02",
    title: "Analyze",
    summary: "Interpret signal and network traits",
    detail:
      "Observations are validated and evaluated for frequency band, signal quality score components, and real security evidence (preserving Unknown when evidence is ambiguous).",
    highlight: "Separates local wireless strength from wider internet tests.",
  },
  {
    step: "03",
    title: "Visualize",
    summary: "Display observations geographically",
    detail:
      "Geotagged records aggregate cleanly into geographic heatmap cells, density tiers, and band-filtered dashboards so spatial patterns stand out immediately.",
    highlight: "Clearly displays GPS uncertainty radius alongside cell RSSI.",
  },
  {
    step: "04",
    title: "Understand",
    summary: "Compare conditions and spot bottlenecks",
    detail:
      "Review signal extremes, channel congestion, latency, jitter, and packet loss trends together to see whether sluggishness stems from local Wi-Fi or upstream internet.",
    highlight: "Grounded in recorded measurements—never fabricated coverage.",
  },
] as const;

const TRANSPARENCY_CARDS = [
  {
    label: "Device Location Only",
    title: "Scan positions, not router addresses",
    description:
      "Observations are collected at the scanning device's location at the time of capture. Heatmap coordinates never identify physical router locations.",
  },
  {
    label: "GPS Uncertainty Radius",
    title: "Estimated horizontal precision",
    description:
      "GPS accuracy (reported in ± meters) is an estimated confidence radius, not a guaranteed positional error bound, and fluctuates indoors or on cached fixes.",
  },
  {
    label: "Measured Reality",
    title: "No fabricated performance promises",
    description:
      "Environmental obstacles, interference, and OS scan throttling affect readings. WiFiVision visualizes recorded observations without guaranteeing internet speeds.",
  },
] as const;

export default function AboutContent() {
  const reducedMotion = useReducedMotion();
  const [activeStepIndex, setActiveStepIndex] = useState(0);
  const activeStep = WORKFLOW_STEPS[activeStepIndex];

  return (
    <div className="w-full overflow-x-clip">
      {/* Section A: Hero */}
      <section className="relative border-b border-[#303640]/[0.08] bg-gradient-to-b from-[#F7F6F2] via-[#F7F6F2] to-[#F1EFE9]/70 py-14 sm:py-20 lg:py-24">
        <div className="mx-auto w-full max-w-[1200px] px-4 sm:px-6">
          <div className="grid grid-cols-1 items-center gap-10 lg:grid-cols-[1.08fr_0.92fr] lg:gap-12">
            <Reveal direction="bottom" immediate>
              <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/35 bg-white px-3.5 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38] shadow-2xs">
                <span className="h-1.5 w-1.5 rounded-full bg-[#2E7D5B]" />
                Wireless Telemetry &amp; Spatial Intelligence
              </div>

              <h1 className="mt-5 text-4xl font-semibold leading-[1.12] tracking-tight text-[#303640] sm:text-5xl">
                See Beyond the{" "}
                <span className="text-[#9F7C38]">Signal.</span>
              </h1>

              <p className="mt-5 max-w-xl text-base leading-relaxed text-[#5E6572]">
                WiFiVision helps you understand wireless network conditions
                through real-world observations, signal intelligence and
                location-based visualization.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3.5">
                <a
                  href="#solution"
                  className="wv-btn-secondary inline-flex h-11 items-center justify-center rounded-xl border border-[#303640]/15 bg-white px-6 text-xs font-semibold text-[#303640] shadow-xs"
                >
                  Explore Features
                </a>

                <Link
                  href="/register"
                  className="wv-btn-primary inline-flex h-11 items-center justify-center rounded-xl border border-[#303640] bg-[#303640] px-6 text-xs font-semibold text-white shadow-sm"
                >
                  Create Your Account
                </Link>

                <Link
                  href="/download"
                  className="wv-btn-gold inline-flex h-11 items-center justify-center gap-2 rounded-xl bg-[#C6A15B] px-6 text-xs font-semibold text-[#303640] shadow-xs"
                >
                  <Download size={15} />
                  Download App
                </Link>
              </div>
            </Reveal>

            <Reveal delayMs={140} direction="right" immediate>
              <SignalNetworkVisual />
            </Reveal>
          </div>

          {/* Sequential Single-Column Hero Highlight Cards */}
          <div className="mt-10 grid max-w-3xl grid-cols-1 gap-3.5">
            {HERO_PILLARS.map((pillar, idx) => (
              <Reveal
                key={pillar.title}
                direction={getAlternatingDirection(idx, 0)}
                delayMs={idx * 90}
              >
                <div className="flex items-center justify-between gap-4 rounded-2xl border border-[#303640]/[0.09] bg-white px-5 py-4 shadow-[0_2px_10px_rgba(48,54,64,0.03)] transition hover:border-[#C6A15B]/45 hover:shadow-[0_10px_26px_rgba(48,54,64,0.06)]">
                  <div>
                    <p className="text-sm font-bold tracking-tight text-[#303640] sm:text-base">
                      {pillar.title}
                    </p>
                    <p className="mt-0.5 text-xs text-[#5E6572]">
                      {pillar.subtitle}
                    </p>
                  </div>
                  <span className="shrink-0 rounded-lg border border-[#C6A15B]/30 bg-[#F1EFE9] px-3 py-1 text-[10px] font-bold uppercase tracking-wider text-[#9F7C38]">
                    {pillar.badge}
                  </span>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Section B: The Problem (1 Card Per Row — Sequential Scroll Reveal) */}
      <section className="mx-auto w-full max-w-[1200px] px-4 py-16 sm:px-6 lg:py-20">
        <Reveal direction="bottom">
          <div className="max-w-2xl">
            <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
              The Problem
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#303640] sm:text-3xl">
              Why full Wi-Fi bars don&apos;t guarantee a smooth internet
              experience
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-[#5E6572]">
              A strong Wi-Fi icon only tells you your phone or laptop is close
              enough to hear your router clearly. Real usability breaks down
              across six distinct layers—scroll through each layer below.
            </p>
          </div>
        </Reveal>

        <div className="mt-10 grid max-w-4xl grid-cols-1 gap-4">
          {PROBLEM_FACTORS.map((item, idx) => {
            const Icon = item.icon;
            return (
              <Reveal
                key={item.title}
                direction={getAlternatingDirection(idx, idx % 2)}
                delayMs={70}
              >
                <article className="flex flex-col gap-4 rounded-2xl border border-[#303640]/[0.09] bg-white p-5 shadow-[0_2px_12px_rgba(48,54,64,0.03)] transition hover:-translate-y-0.5 hover:border-[#C6A15B]/45 hover:shadow-[0_12px_30px_rgba(48,54,64,0.06)] sm:flex-row sm:items-center sm:justify-between sm:p-6">
                  <div className="flex items-start gap-4">
                    <div
                      className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${item.accent}`}
                    >
                      <Icon size={20} />
                    </div>
                    <div>
                      <div className="flex flex-wrap items-center gap-2.5">
                        <span className="text-[10px] font-bold tracking-wider text-[#9F7C38]">
                          {String(idx + 1).padStart(2, "0")}
                        </span>
                        <h3 className="text-base font-semibold text-[#303640]">
                          {item.title}
                        </h3>
                      </div>
                      <p className="mt-1.5 max-w-2xl text-xs leading-relaxed text-[#5E6572] sm:text-sm">
                        {item.description}
                      </p>
                    </div>
                  </div>
                  <span className="shrink-0 self-start rounded-lg bg-[#F1EFE9] px-3 py-1.5 text-[11px] font-semibold text-[#5E6572] sm:self-center">
                    {item.unit}
                  </span>
                </article>
              </Reveal>
            );
          })}
        </div>
      </section>

      {/* Section C: Our Solution (1 Card Per Row — Sequential Scroll Reveal) */}
      <section
        id="solution"
        className="border-y border-[#303640]/[0.08] bg-white py-16 lg:py-20"
      >
        <div className="mx-auto w-full max-w-[1200px] px-4 sm:px-6">
          <Reveal direction="left">
            <div className="max-w-2xl">
              <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
                Our Solution
              </p>
              <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#303640] sm:text-3xl">
                Unified signal telemetry, spatial heatmaps, and real performance
                diagnostics
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-[#5E6572]">
                WiFiVision captures wireless environment measurements alongside
                application-level performance snapshots—giving you clear,
                evidence-backed visibility into every layer.
              </p>
            </div>
          </Reveal>

          <div className="mt-10 grid max-w-4xl grid-cols-1 gap-4">
            {SOLUTION_FEATURES.map((feature, idx) => {
              const Icon = feature.icon;
              return (
                <Reveal
                  key={feature.title}
                  direction={getAlternatingDirection(idx, 2)}
                  delayMs={70}
                >
                  <article className="group flex flex-col gap-4 rounded-2xl border border-[#303640]/[0.09] bg-[#F7F6F2] p-5 transition hover:-translate-y-0.5 hover:border-[#C6A15B]/50 hover:bg-white hover:shadow-[0_12px_30px_rgba(48,54,64,0.06)] sm:flex-row sm:items-center sm:justify-between sm:p-6">
                    <div className="flex items-start gap-4">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/30 bg-white text-[#9F7C38] transition-transform duration-200 group-hover:scale-105">
                        <Icon size={20} />
                      </div>
                      <div>
                        <h3 className="text-base font-semibold text-[#303640]">
                          {feature.title}
                        </h3>
                        <p className="mt-1.5 max-w-2xl text-xs leading-relaxed text-[#5E6572] sm:text-sm">
                          {feature.description}
                        </p>
                      </div>
                    </div>
                    <span className="shrink-0 self-start rounded-full border border-[#C6A15B]/30 bg-[#C6A15B]/12 px-3 py-1 text-[10px] font-semibold text-[#9F7C38] sm:self-center">
                      {feature.badge}
                    </span>
                  </article>
                </Reveal>
              );
            })}
          </div>
        </div>
      </section>

      {/* Section D: How WiFiVision Works (1 Card Per Step + Interactive Detail) */}
      <section className="mx-auto w-full max-w-[1200px] px-4 py-16 sm:px-6 lg:py-20">
        <Reveal direction="bottom">
          <div className="max-w-2xl">
            <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
              How WiFiVision Works
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#303640] sm:text-3xl">
              From local device scan to spatial network understanding
            </h2>
          </div>
        </Reveal>

        <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-[0.95fr_1.05fr] lg:items-start">
          <div className="grid grid-cols-1 gap-3">
            {WORKFLOW_STEPS.map((item, index) => {
              const isSelected = index === activeStepIndex;
              return (
                <Reveal
                  key={item.step}
                  direction={getAlternatingDirection(index, 1)}
                  delayMs={70}
                >
                  <button
                    type="button"
                    onClick={() => setActiveStepIndex(index)}
                    aria-pressed={isSelected}
                    className={`wv-btn-pill flex w-full items-center justify-between gap-4 rounded-2xl border p-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B] ${
                      isSelected
                        ? "border-[#C6A15B] bg-white shadow-[0_10px_28px_rgba(48,54,64,0.06)]"
                        : "border-[#303640]/[0.09] bg-white/80 hover:border-[#C6A15B]/45 hover:bg-white"
                    }`}
                  >
                    <div className="flex items-center gap-3.5">
                      <span
                        className={`inline-flex h-8 shrink-0 items-center rounded-lg px-2.5 text-[11px] font-bold tracking-wider transition-colors ${
                          isSelected
                            ? "bg-[#303640] text-[#C6A15B]"
                            : "bg-[#F1EFE9] text-[#5E6572]"
                        }`}
                      >
                        STEP {item.step}
                      </span>
                      <div>
                        <h3 className="text-sm font-semibold text-[#303640]">
                          {item.title}
                        </h3>
                        <p className="mt-0.5 text-xs text-[#5E6572]">
                          {item.summary}
                        </p>
                      </div>
                    </div>
                    <span
                      className={`h-2.5 w-2.5 shrink-0 rounded-full transition-colors ${
                        isSelected ? "bg-[#2E7D5B]" : "bg-[#303640]/20"
                      }`}
                    />
                  </button>
                </Reveal>
              );
            })}
          </div>

          <Reveal direction="right" delayMs={120}>
            <div className="flex flex-col justify-between rounded-2xl border border-[#303640]/[0.10] bg-white p-6 shadow-[0_8px_28px_rgba(48,54,64,0.05)] lg:sticky lg:top-24">
              <div>
                <div className="flex items-center justify-between gap-3 border-b border-[#303640]/[0.08] pb-4">
                  <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38]">
                    Step {activeStep.step} · {activeStep.title}
                  </span>
                  <span className="rounded-md bg-[#F1EFE9] px-2.5 py-1 text-[10px] font-semibold text-[#303640]">
                    {activeStepIndex + 1} / {WORKFLOW_STEPS.length}
                  </span>
                </div>

                <h3 className="mt-4 text-xl font-semibold text-[#303640]">
                  {activeStep.summary}
                </h3>
                <p className="mt-3 text-sm leading-relaxed text-[#5E6572]">
                  {activeStep.detail}
                </p>
              </div>

              <div className="mt-6 rounded-xl border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.07] px-4 py-3 text-xs font-medium text-[#2E7D5B]">
                <CheckCircle2
                  size={15}
                  className="mr-2 inline-block align-text-bottom"
                />
                {activeStep.highlight}
              </div>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Section E: Web + Android Ecosystem */}
      <section className="border-y border-[#303640]/[0.08] bg-[#F1EFE9]/60 py-16 lg:py-20">
        <div className="mx-auto grid w-full max-w-[1200px] grid-cols-1 items-center gap-10 px-4 sm:px-6 lg:grid-cols-[0.95fr_1.05fr]">
          <Reveal direction="left">
            <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
              Web + Android Ecosystem
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#303640] sm:text-3xl">
              Field scanning on Android. Spatial clarity on the web.
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-[#5E6572]">
              Smartphones see real-world radio conditions across rooms and
              walkways, while a desktop browser excels at inspecting heatmaps,
              density breakdowns, and performance trends. WiFiVision connects
              both through an explicit, user-confirmed workflow.
            </p>

            <div className="mt-6 space-y-3">
              <Reveal direction="bottom" delayMs={60}>
                <div className="rounded-xl border border-[#303640]/[0.09] bg-white p-4 transition hover:border-[#C6A15B]/45">
                  <p className="text-xs font-semibold text-[#303640]">
                    Android Companion Scanner
                  </p>
                  <p className="mt-1 text-xs text-[#5E6572]">
                    Local-first scanning (SCAN ONLY), foreground connectivity
                    monitoring, GPS measurement snapshots, offline 100-item
                    queue, and an explicit confirmation preview before every
                    upload.
                  </p>
                </div>
              </Reveal>
              <Reveal direction="left" delayMs={120}>
                <div className="rounded-xl border border-[#303640]/[0.09] bg-white p-4 transition hover:border-[#C6A15B]/45">
                  <p className="text-xs font-semibold text-[#303640]">
                    Authenticated Web Intelligence Platform
                  </p>
                  <p className="mt-1 text-xs text-[#5E6572]">
                    Interactive Signal &amp; Density heatmaps, band filters, GPS
                    precision summaries, quality scoring breakdowns, and HTTP
                    performance analytics.
                  </p>
                </div>
              </Reveal>
            </div>
          </Reveal>

          {/* Animated Dual-Device Preview Illustration */}
          <Reveal delayMs={140} direction="right">
            <div className="relative mx-auto max-w-[520px]">
              <div className="rounded-2xl border border-[#303640]/12 bg-white p-5 shadow-[0_16px_42px_rgba(48,54,64,0.08)]">
                <div className="flex items-center justify-between border-b border-[#303640]/[0.08] pb-3">
                  <div className="flex items-center gap-2">
                    <span className="h-2.5 w-2.5 rounded-full bg-[#2E7D5B]" />
                    <span className="text-xs font-semibold text-[#303640]">
                      Web Heatmap Explorer
                    </span>
                  </div>
                  <span className="rounded-md bg-[#F1EFE9] px-2 py-0.5 text-[10px] font-semibold text-[#9F7C38]">
                    2.4 / 5 / 6 GHz
                  </span>
                </div>

                <div className="mt-4 grid grid-cols-3 gap-2">
                  <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-2.5">
                    <p className="text-[9px] font-bold uppercase tracking-wider text-[#868D98]">
                      Signal Layer
                    </p>
                    <p className="mt-1 text-xs font-semibold text-[#2E7D5B]">
                      RSSI (dBm)
                    </p>
                  </div>
                  <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-2.5">
                    <p className="text-[9px] font-bold uppercase tracking-wider text-[#868D98]">
                      Density Layer
                    </p>
                    <p className="mt-1 text-xs font-semibold text-[#3730A3]">
                      Low / Med / High
                    </p>
                  </div>
                  <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-2.5">
                    <p className="text-[9px] font-bold uppercase tracking-wider text-[#868D98]">
                      GPS Radius
                    </p>
                    <p className="mt-1 text-xs font-semibold text-[#9F7C38]">
                      Precision tier
                    </p>
                  </div>
                </div>

                <div className="mt-4 flex items-center justify-between rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9]/80 px-3.5 py-3">
                  <span className="text-[11px] font-medium text-[#5E6572]">
                    Visualizes uploaded device measurements
                  </span>
                  <span className="rounded-md bg-[#303640] px-2 py-0.5 text-[10px] font-semibold text-[#C6A15B]">
                    Web UI
                  </span>
                </div>
              </div>

              <div
                className={`mt-4 rounded-2xl border border-[#C6A15B]/40 bg-[#FFFFFF] p-4 shadow-[0_14px_34px_rgba(48,54,64,0.10)] sm:ml-auto sm:-mt-8 sm:w-[300px] ${
                  reducedMotion ? "" : "wv-anim-float-slow"
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#303640] text-[#C6A15B]">
                      <Smartphone size={15} />
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-[#303640]">
                        Android Companion
                      </p>
                      <p className="text-[10px] text-[#5E6572]">
                        Auto-upload OFF
                      </p>
                    </div>
                  </div>
                  <span className="rounded-full bg-[#2E7D5B]/12 px-2 py-0.5 text-[9px] font-bold text-[#2E7D5B]">
                    LOCAL
                  </span>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2 text-[10px]">
                  <span className="rounded-lg bg-[#C6A15B] px-2.5 py-1.5 text-center font-semibold text-white">
                    SCAN ONLY
                  </span>
                  <span className="rounded-lg border border-[#303640]/15 bg-[#F7F6F2] px-2.5 py-1.5 text-center font-semibold text-[#303640]">
                    UPLOAD PREVIEW
                  </span>
                </div>
              </div>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Section F: Data Accuracy & Transparency (1 Card Per Row — Sequential Scroll Reveal) */}
      <section className="mx-auto w-full max-w-[1200px] px-4 py-16 sm:px-6 lg:py-20">
        <Reveal direction="bottom">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
              <Info size={14} />
              Data Accuracy &amp; Transparency
            </div>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#303640] sm:text-3xl">
              Honest telemetry, without inflated claims
            </h2>
            <p className="mt-2 text-sm leading-relaxed text-[#5E6572]">
              Every chart and map cell surfaces measurement uncertainty
              explicitly so conclusions stay grounded in reality.
            </p>
          </div>
        </Reveal>

        <div className="mt-8 grid max-w-4xl grid-cols-1 gap-4">
          {TRANSPARENCY_CARDS.map((card, idx) => (
            <Reveal
              key={card.label}
              direction={getAlternatingDirection(idx, 1)}
              delayMs={70}
            >
              <article className="flex flex-col justify-between gap-4 rounded-2xl border border-[#C6A15B]/35 bg-white p-6 shadow-[0_4px_18px_rgba(48,54,64,0.04)] transition hover:-translate-y-0.5 hover:border-[#C6A15B] hover:shadow-[0_12px_28px_rgba(48,54,64,0.07)] sm:flex-row sm:items-center">
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38]">
                    {card.label}
                  </span>
                  <h3 className="mt-1.5 text-base font-semibold text-[#303640]">
                    {card.title}
                  </h3>
                  <p className="mt-1.5 max-w-2xl text-xs leading-relaxed text-[#5E6572] sm:text-sm">
                    {card.description}
                  </p>
                </div>
                <CheckCircle2 size={20} className="shrink-0 text-[#2E7D5B]" />
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      {/* Section G: Final CTA */}
      <section className="pb-16 pt-2 sm:pb-20">
        <div className="mx-auto w-full max-w-[1200px] px-4 sm:px-6">
          <Reveal direction="bottom">
            <div className="relative overflow-hidden rounded-2xl border border-[#303640]/12 bg-[#303640] px-6 py-12 text-center text-white shadow-[0_20px_50px_rgba(48,54,64,0.12)] sm:px-10">
              <div
                aria-hidden="true"
                className="pointer-events-none absolute -right-12 -top-12 h-52 w-52 rounded-full"
                style={{
                  background:
                    "radial-gradient(circle, rgba(198,161,91,0.25) 0%, transparent 70%)",
                }}
              />
              <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-[#C6A15B]">
                Ready to inspect your wireless environment?
              </p>
              <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">
                Understand Your Wireless World.
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-white/80">
                Create an email-verified account to access the interactive
                heatmap, signal quality breakdown, and performance diagnostics.
              </p>

              <div className="mt-7 flex flex-wrap items-center justify-center gap-3.5">
                <Link
                  href="/register"
                  className="wv-btn-gold inline-flex h-11 items-center justify-center rounded-xl bg-[#C6A15B] px-6 text-xs font-semibold text-[#303640] shadow-sm"
                >
                  Create Account
                </Link>
                <Link
                  href="/download"
                  className="wv-btn-pill inline-flex h-11 items-center gap-2 rounded-xl border border-white/25 bg-white/10 px-6 text-xs font-semibold text-white hover:border-[#C6A15B] hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B]"
                >
                  <Download size={15} className="text-[#C6A15B]" />
                  Download App
                </Link>
                <Link
                  href="/faq"
                  className="wv-btn-pill inline-flex h-11 items-center gap-2 rounded-xl border border-white/25 bg-white/10 px-6 text-xs font-semibold text-white hover:border-[#C6A15B] hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B]"
                >
                  <HelpCircle size={15} className="text-[#C6A15B]" />
                  Explore FAQs
                </Link>
              </div>
            </div>
          </Reveal>
        </div>
      </section>
    </div>
  );
}
