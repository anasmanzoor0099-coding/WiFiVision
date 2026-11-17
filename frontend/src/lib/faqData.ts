export const FAQ_CATEGORIES = [
  "General",
  "Wi-Fi & Networking",
  "Heatmap & GPS",
  "Android Application",
  "Internet Performance",
  "Accounts & Authentication",
  "Privacy & Security",
  "Troubleshooting",
] as const;

export type FaqCategory = (typeof FAQ_CATEGORIES)[number];

export type FaqCategoryFilter = "All" | FaqCategory;

export interface FaqItem {
  id: string;
  category: FaqCategory;
  question: string;
  answer: string;
  tags: string[];
}

export const FAQ_ITEMS: FaqItem[] = [
  {
    id: "what-is-wifivision",
    category: "General",
    question: "What is WiFiVision?",
    answer:
      "WiFiVision is a wireless network telemetry and analytics platform combining native Android and Windows companion scanners with a web dashboard. It records Wi-Fi observations (such as SSID, signal strength in dBm, frequency band, and security type), optional device coordinates, and HTTP-based internet performance measurements so you can inspect signal coverage and network quality over time.",
    tags: ["overview", "platform", "android", "windows", "dashboard"],
  },
  {
    id: "full-bars-still-buffer",
    category: "Wi-Fi & Networking",
    question: "Why can Wi-Fi have full bars but still buffer videos?",
    answer:
      "Signal bars only measure the radio link between your device and the local Wi-Fi router or access point. You can have strong Wi-Fi signal (-50 dBm) and still experience buffering if the router's upstream internet connection is congested, ISP bandwidth is saturated, DNS or upstream servers have high latency or jitter, or multiple active devices are competing on the same wireless channel.",
    tags: ["bars", "buffering", "speed", "congestion"],
  },
  {
    id: "what-is-rssi",
    category: "Wi-Fi & Networking",
    question: "What is RSSI?",
    answer:
      "RSSI stands for Received Signal Strength Indicator. In WiFiVision, RSSI is measured in dBm (decibels relative to one milliwatt) as negative numbers typically ranging from -30 dBm (very strong close-range signal) down to -90 dBm or lower (weak, noise-limited signal).",
    tags: ["rssi", "signal", "strength", "radio"],
  },
  {
    id: "what-does-dbm-mean",
    category: "Wi-Fi & Networking",
    question: "What does dBm mean?",
    answer:
      "dBm is a logarithmic unit measuring absolute signal power relative to 1 milliwatt. Because the scale is logarithmic, every 3 dBm change represents roughly a doubling or halving of signal power. On WiFiVision's heatmap, -60 dBm and above is classified as Excellent, -61 to -70 dBm Good, -71 to -80 dBm Fair, -81 to -90 dBm Weak, and below -90 dBm Very Weak.",
    tags: ["dbm", "logarithmic", "thresholds"],
  },
  {
    id: "frequency-bands-meaning",
    category: "Wi-Fi & Networking",
    question: "What do 2.4 GHz, 5 GHz and 6 GHz mean?",
    answer:
      "These refer to the unlicensed radio frequency bands used by Wi-Fi routers. 2.4 GHz travels farther and penetrates walls better but has fewer non-overlapping channels and more interference. 5 GHz provides higher throughput and less congestion over shorter distances. 6 GHz (Wi-Fi 6E / Wi-Fi 7) offers wide, low-interference spectrum at closer range on supported hardware.",
    tags: ["2.4 ghz", "5 ghz", "6 ghz", "frequency", "band"],
  },
  {
    id: "signal-vs-internet-speed",
    category: "Wi-Fi & Networking",
    question: "What is the difference between Wi-Fi signal and internet speed?",
    answer:
      "Wi-Fi signal strength describes how clearly your device hears your local wireless access point, whereas internet speed (download and upload Mbps) measures end-to-end throughput to remote internet endpoints. Poor Wi-Fi signal can bottleneck throughput, but strong Wi-Fi cannot make a slow ISP plan or congested upstream link faster. Note that triggering the dashboard's Performance test (authenticated POST /api/performance) runs speed and latency probes from the WiFiVision backend host to Cloudflare rather than directly measuring your phone's local radio link.",
    tags: ["speed", "mbps", "signal", "isp"],
  },
  {
    id: "what-heatmap-represents",
    category: "Heatmap & GPS",
    question: "What does the Wi-Fi heatmap represent?",
    answer:
      "The heatmap groups geotagged observations into 0.001-degree geographic cells and visualizes either average measured signal strength (RSSI in dBm) or recorded observation density per cell, along with per-cell average GPS accuracy. Thermal glow is strictly a visual aid around recorded scan points—it does not verify coverage in unmeasured areas.",
    tags: ["heatmap", "cells", "coverage", "thermal"],
  },
  {
    id: "density-levels-explained",
    category: "Heatmap & GPS",
    question: "What are Low, Medium and High density levels?",
    answer:
      "WiFiVision uses grounded counts in two places: on the map's Observation Density layer, cells are styled by recorded sample count as Low (1–5 observations), Medium (6–20 observations), and High (21+ observations). Separately, the cell breakdown table reports unique SSID count per cell from /api/observations/density, where backend density tiers represent Low (<=2 unique SSIDs), Medium (3–5), and High (>5). Neither metric measures RSSI signal strength.",
    tags: ["density", "low", "medium", "high", "unique ssids"],
  },
  {
    id: "does-pin-mean-router-location",
    category: "Heatmap & GPS",
    question: "Does a heatmap pin represent a router's physical location?",
    answer:
      "No. Every coordinate recorded by WiFiVision reflects where the scanning Android device was located when it captured the measurement—not the physical position of the wireless router or access point.",
    tags: ["router", "pin", "coordinates", "location"],
  },
  {
    id: "what-gps-accuracy-means",
    category: "Heatmap & GPS",
    question: "What does GPS accuracy mean?",
    answer:
      "GPS accuracy (reported in meters, such as ±15.6 m) is the device's estimated horizontal uncertainty radius (typically ~68% confidence), not a guaranteed maximum positional error. WiFiVision groups cell accuracy into High location precision (<= 20 m), Moderate location precision (> 20 m to 50 m), Low location precision (> 50 m, coarse), or Unknown when unavailable.",
    tags: ["gps", "accuracy", "precision", "radius"],
  },
  {
    id: "why-gps-accuracy-changes",
    category: "Heatmap & GPS",
    question: "Why can GPS accuracy change?",
    answer:
      "Location accuracy varies based on satellite visibility, indoor walls or metal roofs, urban multipath reflections, atmospheric conditions, whether the device returns a fresh live fix or a cached fused location estimate, and device power-saving settings.",
    tags: ["gps", "indoor", "fused", "cached", "drift"],
  },
  {
    id: "unknown-security-reason",
    category: "Wi-Fi & Networking",
    question: "Why do some networks display Unknown security?",
    answer:
      "WiFiVision intentionally preserves Unknown rather than guessing WPA2 or WPA3 when definitive evidence is unavailable. For example, Android's WifiInfo API on newer OS versions reports generic PSK/EAP types that cannot distinguish WPA from WPA2 or mixed modes without a matching fresh ScanResult capabilities string, or older observations may lack complete capability metadata.",
    tags: ["security", "unknown", "wpa2", "wpa3", "capabilities"],
  },
  {
    id: "android-how-scans",
    category: "Android Application",
    question: "How does the Android application scan Wi-Fi?",
    answer:
      "The Android companion app provides a local-only SCAN ONLY action that triggers Android's WifiManager scan alongside a single device GPS fix snapshot, plus a foreground-only START MONITOR mode that listens to ConnectivityManager callbacks (with 30-second active connection refreshes and up to 60-second local scan updates while the app stays open). Scan results stay on-device without automatically uploading observations; the app only checks backend /api/health on startup or URL updates.",
    tags: ["android", "scan only", "wifimanager", "monitor"],
  },
  {
    id: "android-permissions-required",
    category: "Android Application",
    question: "Why does Android require location and nearby Wi-Fi permissions?",
    answer:
      "Android enforces runtime location permissions (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) and NEARBY_WIFI_DEVICES (on Android 13+) because nearby Wi-Fi BSSID/SSID scan results and GPS coordinates are protected device location signals. Without them, Android redacts SSIDs/scan entries or withholds coordinates.",
    tags: ["permissions", "location", "nearby wifi", "android 13"],
  },
  {
    id: "does-scan-auto-upload",
    category: "Android Application",
    question: "Does scanning automatically upload observations?",
    answer:
      "No. Auto-upload is turned OFF by design. Running SCAN ONLY or local START MONITOR / STOP MONITOR never automatically enqueues observations and never sends POST requests to the backend.",
    tags: ["privacy", "auto-upload", "local-first", "android"],
  },
  {
    id: "when-observations-uploaded",
    category: "Android Application",
    question: "When are observations uploaded?",
    answer:
      "Observations are uploaded only after you pair your Android or Windows companion app using a temporary pairing code generated in web Settings (encrypted via Android Keystore or Windows DPAPI), explicitly trigger upload confirmation, review the preview dialog, and choose UPLOAD NOW.",
    tags: ["upload", "confirmation", "manual", "queue", "pairing"],
  },
  {
    id: "windows-companion-scanner",
    category: "General",
    question: "Can I scan Wi-Fi from a Windows laptop as well as Android?",
    answer:
      "Yes. WiFiVision includes a native Windows Companion executable (using wlanapi.dll 802.11 scans, Windows DPAPI credential storage, and a hardened loopback bridge on 127.0.0.1:48271). Both Android and Windows observations merge into your private authenticated workspace, while Windows scans cleanly attach null coordinates instead of guessing if Windows Location is unavailable.",
    tags: ["windows", "wlanapi", "dpapi", "laptop", "private workspace"],
  },
  {
    id: "backend-unavailable-queue",
    category: "Android Application",
    question: "What happens when the backend is unavailable?",
    answer:
      "Confirmed observations awaiting upload (up to 10 unique items queued per confirmation batch and up to 100 entries total in SharedPreferences) remain saved locally in the app's offline queue with duplicate suppression, transient retry limits (up to 5 attempts), and backend 429 rate-limit backoff. Local scanning and monitoring continue normally while offline.",
    tags: ["offline", "queue", "backend", "retry"],
  },
  {
    id: "what-is-latency",
    category: "Internet Performance",
    question: "What is latency?",
    answer:
      "Latency (measured in milliseconds, ms) is the round-trip time required for a request to travel to a test server and return a response. Lower latency feels snappier for interactive browsing, calls, and gaming. WiFiVision also tracks median latency to reduce skew from isolated spikes.",
    tags: ["latency", "ms", "ping", "round-trip"],
  },
  {
    id: "what-is-jitter",
    category: "Internet Performance",
    question: "What is jitter?",
    answer:
      "Jitter (measured in milliseconds, ms) is the variation between consecutive latency measurements over time. Even when average latency looks reasonable, high jitter causes choppy voice/video calls and unstable real-time streams.",
    tags: ["jitter", "variation", "stability", "calls"],
  },
  {
    id: "what-is-packet-loss",
    category: "Internet Performance",
    question: "What is packet loss?",
    answer:
      "Packet loss percentage indicates the share of test requests that fail to complete successfully. WiFiVision measures HTTP application-level request success across probe requests rather than raw OS-level ICMP ping, helping surface severe instability or dropped connections.",
    tags: ["packet loss", "http", "reliability"],
  },
  {
    id: "why-speed-tests-vary",
    category: "Internet Performance",
    question: "Why can speed tests vary?",
    answer:
      "Download (1 MB) and upload (1 MB) probes against Cloudflare execute from the WiFiVision backend host when started by an authenticated user in the dashboard (POST /api/performance), so results fluctuate due to server network conditions, concurrent traffic, ISP routing, and upstream server load. Individual runs reflect point-in-time snapshots rather than guaranteed sustained bandwidth.",
    tags: ["speed test", "variation", "throughput", "cloudflare"],
  },
  {
    id: "how-to-register",
    category: "Accounts & Authentication",
    question: "How can I register?",
    answer:
      "Open the Create Account page (/register), enter your full name and email address, review and acknowledge the Privacy Policy, submit to receive a 6-digit email verification code, verify the code, and then set your password on the password setup step. Email/password registration is supported in this release.",
    tags: ["register", "signup", "onboarding", "otp"],
  },
  {
    id: "how-to-verify-email",
    category: "Accounts & Authentication",
    question: "How do I verify my email?",
    answer:
      "After submitting your name and email on /register, enter the 6-digit verification code sent to your inbox directly in the verification stage (or visit /verify-email if returning with an active code). Codes expire after 10 minutes, and you can request a resend after the 60-second cooldown.",
    tags: ["verify email", "otp", "code", "resend"],
  },
  {
    id: "how-to-reset-password",
    category: "Accounts & Authentication",
    question: "How can I reset my password?",
    answer:
      "Self-service password reset is not implemented in the current release UI or active backend auth routes (only database/validator scaffolding exists for future updates). If you are locked out of a verified account, manual recovery depends on your deployment administrator outside the application.",
    tags: ["password", "reset", "recovery", "support"],
  },
  {
    id: "how-data-protected",
    category: "Privacy & Security",
    question: "How is my data protected?",
    answer:
      "Passwords are hashed with bcrypt, authenticated user sessions use 24-hour signed HS256 JWTs in httpOnly sameSite-strict cookies with tokenVersion revocation (Sign out all), and all dashboard observation, heatmap, quality, performance, and device APIs enforce per-user ownership isolation. Android uploads require a paired device credential stored encrypted via Android Keystore with timestamp/nonce replay defense, newly submitted BSSIDs are keyed HMAC-SHA256 hashed with raw BSSID discarded, and production deployments enforce distributed Upstash Redis rate limiting plus Helmet, CORS, and Zod validation. However, geotagged coordinates and network names mean observations are not anonymous.",
    tags: ["security", "bcrypt", "httponly", "cookies", "bssid", "keystore"],
  },
  {
    id: "request-account-deletion",
    category: "Privacy & Security",
    question: "How can I request account or data deletion?",
    answer:
      "Self-service account or telemetry deletion is not implemented in the web dashboard UI or backend API endpoints in this release. Removing stored MongoDB records requires manual action by your deployment operator (refer to Sections 14 and 18 of the Privacy Policy); on Android, clearing app storage or uninstalling removes local queue and URL settings.",
    tags: ["deletion", "privacy", "rights", "account"],
  },
  {
    id: "troubleshooting-empty-heatmap",
    category: "Troubleshooting",
    question: "Why is my heatmap empty or showing fewer cells than expected?",
    answer:
      "Only real observations containing valid GPS coordinates appear on the heatmap (development test entries and observations without coordinates are excluded), and band filters (2.4 GHz, 5 GHz, 6 GHz) hide cells with zero samples in that band. Ensure location permission was granted before running SCAN ONLY and that you confirmed UPLOAD NOW in the Android app.",
    tags: ["troubleshooting", "empty", "gps", "filter"],
  },
];

export function filterFaqItems(
  items: FaqItem[],
  category: FaqCategoryFilter,
  searchQuery: string
): FaqItem[] {
  const normalizedQuery = searchQuery.trim().toLowerCase();

  return items.filter((item) => {
    if (category !== "All" && item.category !== category) {
      return false;
    }

    if (!normalizedQuery) {
      return true;
    }

    return (
      item.question.toLowerCase().includes(normalizedQuery) ||
      item.answer.toLowerCase().includes(normalizedQuery) ||
      item.category.toLowerCase().includes(normalizedQuery) ||
      item.tags.some((tag) => tag.toLowerCase().includes(normalizedQuery))
    );
  });
}
