# WiFiVision

**Network Intelligence & Connectivity Analysis Platform**

WiFiVision is a full-stack wireless network monitoring and connectivity analytics platform composed of four integrated applications:

- **`backend/`** — Node.js & Express 5 REST API for observation ingestion, network quality scoring, geographic heatmap/density aggregation, HTTP internet performance testing, device pairing, and cookie-based JWT authentication.
- **`frontend/`** — Next.js 16 (App Router, React 19, TypeScript, Tailwind CSS v4) web dashboard featuring interactive Leaflet geographic visualizations, analytics, and multi-step OTP account authentication.
- **`android/`** — Native Kotlin Android companion application (`com.wifivision.app`, SDK 26–36) that scans nearby Wi-Fi networks, tags observations with GPS coordinates, stores device credentials in Android Keystore, and uploads explicitly confirmed observations.
- **`windows/`** — Native Windows `.NET Framework 4.8` WinForms companion (`WiFiVisionCompanion.exe`) using Windows Native Wi-Fi (`wlanapi.dll`), Windows Location, DPAPI storage, and a token-authenticated loopback bridge (`127.0.0.1:48271`).

---

## Architecture Overview

```text
┌──────────────────────────┐        POST /api/observations        ┌──────────────────────────┐
│   Android Companion App  │ ───────────────────────────────────► │                          │
│   (Kotlin, WifiScanner)  │                                      │   WiFiVision Backend     │
└──────────────────────────┘                                      │   Express 5 + Mongoose   │
                                                                  │   MongoDB Atlas / Zod    │
┌──────────────────────────┐   REST API (JSON + HttpOnly Cookie)  │                          │
│   Next.js Web Dashboard  │ ◄──────────────────────────────────► │                          │
│   (App Router, Leaflet)  │                                      └──────────────────────────┘
└──────────────────────────┘
```

### API Surface (`backend/`)
| Prefix | Purpose |
| :--- | :--- |
| `GET /api/health` | Service and MongoDB connectivity status |
| `/api/auth/*` | Registration (OTP email verification + password setup), login, logout, current user |
| `/api/observations/*` | Create/list Wi-Fi observations, stats, geographic heatmap, and network density |
| `/api/quality` | Weighted signal/band/security quality scoring |
| `/api/performance/*` | HTTP latency, jitter, download/upload speed, packet-loss measurement, history & trends |

---

## Prerequisites

- **Node.js** `>= 20.0.0` and **npm**
- **MongoDB** (MongoDB Atlas or local MongoDB instance)
- **Android Studio / JDK 17** (for building `android/`)

---

## Local Setup

### 1. Backend (`backend/`)

```bash
cd backend
npm install
```

Copy `backend/.env.example` to `backend/.env` and fill in your credentials (`MONGODB_URI`, `JWT_SECRET`, and SMTP settings if testing email delivery):

```bash
# Development (with nodemon)
npm run dev

# Production
npm start

# Automated tests
npm test
```

The backend runs by default at `http://localhost:5000`.

### 2. Frontend (`frontend/`)

```bash
cd frontend
npm install
```

Copy `frontend/.env.example` to `frontend/.env.local` and ensure `NEXT_PUBLIC_API_URL` points to your backend (for example `http://localhost:5000/api`):

```bash
# Development
npm run dev

# Lint & Production Build
npm run lint
npm run build
npm start
```

The web dashboard runs by default at `http://localhost:3000`.

### 3. Android Companion App (`android/`)

Open `android/` in Android Studio or build via Gradle wrapper:

```bash
cd android
./gradlew assembleDebug
```

Grant location (`ACCESS_FINE_LOCATION`) and Wi-Fi permissions on the device, ensure Wi-Fi and Location Services are enabled, and configure the backend endpoint before scanning.

---

## Measurement Notes

- **GPS vs. Router Location:** Observation coordinates represent the measuring device's GPS location at scan time, not the physical location of the Wi-Fi access point.
- **Latency, Jitter & Packet Loss:** Measured via HTTP timing and request success against Cloudflare speed endpoints, not raw ICMP ping.
- **Quality Scores:** Application-defined weighted estimates based on signal strength (dBm), Android/Windows signal level, frequency band, and security protocol.

---

## Windows Companion (`windows/`)

Build and run using `.NET Framework 4.8` MSBuild (`x64`):

```powershell
cd windows
C:\Windows\Microsoft.NET\Framework64\v4.0.30319\MSBuild.exe WiFiVision.Windows.csproj /p:Configuration=Release /p:Platform=x64
.\bin\Release\WiFiVisionCompanion.exe
```

When connecting the Windows Companion's loopback bridge (`http://127.0.0.1:48271`) from a deployed HTTPS frontend rather than `http://localhost:3000`, launch `WiFiVisionCompanion.exe` with `WIFIVISION_ALLOWED_ORIGINS` set to your deployed frontend origin (e.g. `https://your-frontend.vercel.app`) and enter the HTTPS backend origin (`https://your-backend.vercel.app`) in **Backend API URL**.

---

## Production Release Preparation

### Vercel Environment Variable Checklist (Names Only)

- **Backend (`backend/`)**: `NODE_ENV` (`production`), `MONGODB_URI`, `JWT_SECRET` (32+ chars), `JWT_EXPIRES_IN` (e.g. `24h`), `BSSID_HMAC_SECRET` (32+ chars), `DEVICE_TOKEN_PEPPER` (32+ chars), `UPSTASH_REDIS_REST_URL`, `UPSTASH_REDIS_REST_TOKEN`, `CORS_ORIGIN` (comma-separated frontend HTTPS origin(s)), `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS` (and optional `SMTP_SECURE`).
- **Frontend (`frontend/`)**: `NEXT_PUBLIC_API_URL=/api` and server-only `BACKEND_ORIGIN=https://<your-backend-domain>` (rewrites `/api/*` server-side via `next.config.ts`).

### Android Signed Release Procedure (`android/`)

1. Generate a private upload keystore outside version control (for example via `keytool -genkeypair -v -keystore android/release-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias wifivision-release`).
2. Copy `android/keystore.properties.example` to gitignored `android/keystore.properties` and supply `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`.
3. Confirm release HTTPS backend endpoint configuration (release builds enforce HTTPS with `ALLOW_CLEARTEXT_DEV_URL=false` and `DEFAULT_API_BASE_URL=""` unless overridden).
4. Run `.\gradlew.bat test assembleRelease` (or `bundleRelease`) in `android/` and keep both the `.jks` keystore and `keystore.properties` strictly off Git.

### Troubleshooting Atlas `mongodb+srv://` DNS (`queryTxt ETIMEOUT`)

If local development runs behind a NAT/hotspot DNS forwarder that resolves `SRV` records but drops DNS `TXT` lookups (`queryTxt ETIMEOUT`), use MongoDB Atlas's standard non-SRV seedlist URI in local `.env` or test from a DNS resolver that forwards `TXT` records.
