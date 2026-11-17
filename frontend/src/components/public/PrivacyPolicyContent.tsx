"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import {
  ChevronDown,
  FileText,
  Printer,
  ShieldCheck,
} from "lucide-react";

import {
  ExpandablePanel,
  getAlternatingDirection,
  ReadingProgressBar,
  Reveal,
  type RevealDirection,
} from "./MotionPrimitives";

export const PRIVACY_LAST_UPDATED = "November 17, 2026";

interface PolicySectionMeta {
  id: string;
  number: number;
  title: string;
}

export const PRIVACY_SECTIONS: PolicySectionMeta[] = [
  { id: "section-1", number: 1, title: "Introduction" },
  { id: "section-2", number: 2, title: "Information We Collect" },
  { id: "section-3", number: 3, title: "Account Information" },
  { id: "section-4", number: 4, title: "Wi-Fi Network Observations" },
  { id: "section-5", number: 5, title: "Location Information" },
  { id: "section-6", number: 6, title: "Internet Performance Measurements" },
  { id: "section-7", number: 7, title: "How We Use Information" },
  { id: "section-8", number: 8, title: "How Information Is Stored" },
  { id: "section-9", number: 9, title: "Data Security" },
  { id: "section-10", number: 10, title: "Data Sharing and Third Parties" },
  { id: "section-11", number: 11, title: "Cookies and Session Information" },
  { id: "section-12", number: 12, title: "Data Retention" },
  { id: "section-13", number: 13, title: "User Rights" },
  { id: "section-14", number: 14, title: "Account and Data Deletion" },
  { id: "section-15", number: 15, title: "Children's Privacy" },
  { id: "section-16", number: 16, title: "International Data Transfers" },
  { id: "section-17", number: 17, title: "Changes to This Privacy Policy" },
  { id: "section-18", number: 18, title: "Contact Information" },
];

function PolicySection({
  id,
  number,
  title,
  direction = "bottom",
  delayMs = 0,
  children,
}: {
  id: string;
  number: number;
  title: string;
  direction?: RevealDirection;
  delayMs?: number;
  children: ReactNode;
}) {
  return (
    <Reveal direction={direction} delayMs={delayMs}>
      <section
        id={id}
        className="scroll-mt-24 rounded-2xl border border-[#303640]/[0.09] bg-white p-6 shadow-[0_2px_10px_rgba(48,54,64,0.03)] transition hover:border-[#C6A15B]/35 sm:p-7"
      >
        <div className="flex items-center gap-3 border-b border-[#303640]/[0.08] pb-3.5">
          <span className="inline-flex h-7 min-w-7 items-center justify-center rounded-lg bg-[#F1EFE9] px-2 text-[11px] font-bold text-[#9F7C38]">
            {String(number).padStart(2, "0")}
          </span>
          <h2 className="text-lg font-semibold tracking-tight text-[#303640]">
            {number}. {title}
          </h2>
        </div>
        <div className="mt-4 space-y-3 text-sm leading-relaxed text-[#5E6572]">
          {children}
        </div>
      </section>
    </Reveal>
  );
}

export default function PrivacyPolicyContent() {
  const [activeSectionId, setActiveSectionId] = useState<string>(
    PRIVACY_SECTIONS[0].id
  );
  const [mobileTocOpen, setMobileTocOpen] = useState(false);

  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") return;

    const observed = PRIVACY_SECTIONS.map((sec) =>
      document.getElementById(sec.id)
    ).filter((el): el is HTMLElement => el !== null);

    if (observed.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort(
            (a, b) => a.boundingClientRect.top - b.boundingClientRect.top
          );
        if (visible.length > 0) {
          setActiveSectionId(visible[0].target.id);
        }
      },
      {
        rootMargin: "-90px 0px -65% 0px",
        threshold: 0.05,
      }
    );

    observed.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, []);

  function scrollToSection(id: string) {
    setActiveSectionId(id);
    setMobileTocOpen(false);
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }

  return (
    <div className="w-full overflow-x-clip">
      <ReadingProgressBar />

      {/* Header */}
      <section className="border-b border-[#303640]/[0.08] bg-gradient-to-b from-[#F7F6F2] to-[#F1EFE9]/60 py-12 sm:py-16">
        <div className="mx-auto w-full max-w-[1200px] px-4 sm:px-6">
          <Reveal direction="bottom" immediate>
            <div className="flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
              <div className="max-w-2xl">
                <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/35 bg-white px-3.5 py-1.5 text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38] shadow-2xs">
                  <ShieldCheck size={13} />
                  Privacy &amp; Data Governance
                </div>

                <h1 className="mt-4 text-3xl font-semibold tracking-tight text-[#303640] sm:text-4xl">
                  Privacy Policy
                </h1>

                <p className="mt-3 text-sm leading-relaxed text-[#5E6572]">
                  How WiFiVision handles account registration data, Android and
                  Windows Wi-Fi observations, optional device GPS coordinates,
                  and internet performance measurements—written to reflect
                  actual system behavior without overstatement.
                </p>

                <div className="mt-5 flex flex-wrap items-center gap-3 text-xs text-[#5E6572]">
                  <span className="inline-flex items-center gap-1.5 rounded-lg border border-[#303640]/10 bg-white px-3 py-1.5 font-medium text-[#303640]">
                    <FileText size={13} className="text-[#9F7C38]" />
                    Published / Last updated: {PRIVACY_LAST_UPDATED}
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-lg border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.08] px-3 py-1.5 font-medium text-[#2E7D5B]">
                    Companion auto-upload: OFF
                  </span>
                </div>
              </div>

              <div data-no-print className="flex items-center gap-2.5">
                <button
                  type="button"
                  onClick={() => window.print()}
                  className="wv-btn-secondary inline-flex h-10 items-center gap-2 rounded-xl border border-[#303640]/15 bg-white px-4 text-xs font-semibold text-[#303640] shadow-xs"
                >
                  <Printer size={14} className="text-[#9F7C38]" />
                  Print Policy
                </button>
              </div>
            </div>
          </Reveal>
        </div>
      </section>

      {/* Body with Sticky Desktop TOC + Collapsible Mobile TOC */}
      <div className="mx-auto w-full max-w-[1200px] px-4 py-10 sm:px-6 lg:py-12">
        {/* Mobile Collapsible TOC */}
        <div
          data-no-print
          className="mb-6 rounded-2xl border border-[#303640]/12 bg-white lg:hidden"
        >
          <button
            type="button"
            aria-expanded={mobileTocOpen}
            aria-controls="mobile-policy-toc"
            onClick={() => setMobileTocOpen((open) => !open)}
            className="flex w-full items-center justify-between px-4 py-3.5 text-left text-xs font-semibold text-[#303640]"
          >
            <span>Table of Contents ({PRIVACY_SECTIONS.length} sections)</span>
            <ChevronDown
              size={16}
              className={`text-[#9F7C38] transition-transform duration-200 ${
                mobileTocOpen ? "rotate-180" : ""
              }`}
            />
          </button>
          <ExpandablePanel id="mobile-policy-toc" isOpen={mobileTocOpen}>
            <nav
              aria-label="Mobile Privacy Policy contents"
              className="grid grid-cols-1 gap-1 border-t border-[#303640]/[0.08] px-3 py-3 sm:grid-cols-2"
            >
              {PRIVACY_SECTIONS.map((sec) => {
                const active = activeSectionId === sec.id;
                return (
                  <button
                    key={sec.id}
                    type="button"
                    onClick={() => scrollToSection(sec.id)}
                    className={`wv-btn-pill rounded-lg px-2.5 py-2 text-left text-xs ${
                      active
                        ? "bg-[#C6A15B]/15 font-semibold text-[#9F7C38]"
                        : "text-[#5E6572] hover:bg-[#F7F6F2] hover:text-[#303640]"
                    }`}
                  >
                    {sec.number}. {sec.title}
                  </button>
                );
              })}
            </nav>
          </ExpandablePanel>
        </div>

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[280px_1fr] lg:items-start">
          {/* Desktop Sticky TOC */}
          <aside
            data-no-print
            className="hidden lg:sticky lg:top-24 lg:block"
          >
            <div className="rounded-2xl border border-[#303640]/[0.09] bg-white p-4 shadow-[0_2px_12px_rgba(48,54,64,0.03)]">
              <p className="px-2 text-[10px] font-bold uppercase tracking-[0.16em] text-[#868D98]">
                Contents
              </p>
              <nav
                aria-label="Privacy Policy contents"
                className="mt-3 max-h-[calc(100vh-170px)] space-y-0.5 overflow-y-auto pr-1"
              >
                {PRIVACY_SECTIONS.map((sec) => {
                  const active = activeSectionId === sec.id;
                  return (
                    <a
                      key={sec.id}
                      href={`#${sec.id}`}
                      onClick={(event) => {
                        event.preventDefault();
                        scrollToSection(sec.id);
                      }}
                      className={`block rounded-lg border-l-2 px-3 py-1.5 text-xs transition ${
                        active
                          ? "border-[#C6A15B] bg-[#F7F6F2] font-semibold text-[#303640]"
                          : "border-transparent text-[#5E6572] hover:bg-[#F7F6F2]/70 hover:text-[#303640]"
                      }`}
                    >
                      {sec.number}. {sec.title}
                    </a>
                  );
                })}
              </nav>
            </div>
          </aside>

          {/* 18 Required Policy Sections */}
          <div className="space-y-5">
            <PolicySection
              id="section-1"
              number={1}
              title="Introduction"
              direction={getAlternatingDirection(0, 1)}
            >
              <p>
                This Privacy Policy explains how WiFiVision (&ldquo;WiFiVision&rdquo;,
                &ldquo;we&rdquo;, &ldquo;our&rdquo;, or the &ldquo;Service&rdquo;)
                collects, stores, and uses information when you interact with
                the WiFiVision web dashboard and Android companion application.
              </p>
              <p>
                WiFiVision isolates private user telemetry by associating newly
                uploaded Wi-Fi observations and performance measurements with
                your authenticated <code>userId</code> (and paired{" "}
                <code>deviceId</code>), so no signed-in user can view another
                user&apos;s private heatmap, signal feed, or speed history.
                Unassigned legacy records without a <code>userId</code> remain
                excluded from private user APIs. Even within a private
                workspace, telemetry is{" "}
                <strong className="font-semibold text-[#303640]">
                  not anonymous
                </strong>{" "}
                because records can include device GPS coordinates, network
                SSIDs, keyed BSSID HMAC digests, and timestamps. Operator
                controller identity and verified legal contact details are not
                hardcoded in this release and must be provided by your
                deployment operator. Review our{" "}
                <Link
                  href="/about"
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  About overview
                </Link>{" "}
                and{" "}
                <Link
                  href="/faq"
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  FAQs
                </Link>{" "}
                for additional technical context.
              </p>
            </PolicySection>

            <PolicySection
              id="section-2"
              number={2}
              title="Information We Collect"
              direction={getAlternatingDirection(1, 1)}
              delayMs={80}
            >
              <p>
                Depending on how you interact with WiFiVision, three distinct
                categories of data may be processed:
              </p>
              <div className="grid grid-cols-1 gap-3 pt-1 md:grid-cols-3">
                <Reveal
                  direction={getAlternatingDirection(0, 0)}
                  delayMs={0}
                  className="h-full"
                >
                  <div className="flex h-full flex-col rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
                    <p className="text-xs font-semibold text-[#303640]">
                      Account Information
                    </p>
                    <p className="mt-1.5 flex-1 text-xs leading-relaxed text-[#5E6572]">
                      Registration and login details you submit to access the
                      authenticated web dashboard.
                    </p>
                  </div>
                </Reveal>
                <Reveal
                  direction={getAlternatingDirection(1, 0)}
                  delayMs={130}
                  className="h-full"
                >
                  <div className="flex h-full flex-col rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
                    <p className="text-xs font-semibold text-[#303640]">
                      Wi-Fi &amp; Location Telemetry
                    </p>
                    <p className="mt-1.5 flex-1 text-xs leading-relaxed text-[#5E6572]">
                      Wireless scan telemetry explicitly uploaded by you from
                      the Android companion app or web submission endpoints.
                    </p>
                  </div>
                </Reveal>
                <Reveal
                  direction={getAlternatingDirection(2, 0)}
                  delayMs={260}
                  className="h-full"
                >
                  <div className="flex h-full flex-col rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
                    <p className="text-xs font-semibold text-[#303640]">
                      Performance Metrics
                    </p>
                    <p className="mt-1.5 flex-1 text-xs leading-relaxed text-[#5E6572]">
                      Speed, latency, jitter, and HTTP request packet-loss
                      metrics generated when you explicitly run a test.
                    </p>
                  </div>
                </Reveal>
              </div>
            </PolicySection>

            <PolicySection
              id="section-3"
              number={3}
              title="Account Information"
              direction={getAlternatingDirection(2, 1)}
            >
              <p>
                When you register and verify an account via the current email
                and password flow on{" "}
                <Link
                  href="/register"
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  Create Account
                </Link>
                , WiFiVision stores:
              </p>
              <ul className="list-disc space-y-1.5 pl-5">
                <li>Your full name (2–60 characters, trimmed).</li>
                <li>Your email address (normalized to lowercase).</li>
                <li>
                  Your password, stored exclusively as a one-way cryptographic
                  bcrypt hash (never in plaintext).
                </li>
                <li>
                  Account verification state (<code>isEmailVerified</code>),
                  temporary cryptographic hashes and expiry timestamps for the
                  6-digit verification code and onboarding setup token, and your
                  most recent login timestamp (<code>lastLoginAt</code>).
                </li>
              </ul>
              <p>
                Account information is used solely for authentication,
                onboarding verification, and account access management. It is
                stored separately from wireless observation documents.
              </p>
            </PolicySection>

            <PolicySection
              id="section-4"
              number={4}
              title="Wi-Fi Network Observations"
              direction={getAlternatingDirection(3, 2)}
            >
              <p>
                Scanning Wi-Fi in either the Android or Windows companion app (
                <code>SCAN ONLY</code> or local monitoring) stays on your
                device by default because{" "}
                <strong className="font-semibold text-[#303640]">
                  auto-upload is disabled
                </strong>
                . Observations are sent to your private backend workspace only
                after you pair your Android or Windows device using a
                short-lived pairing code from Settings, review the upload
                preview, and explicitly confirm <code>UPLOAD NOW</code>.
              </p>
              <p>Uploaded observation records contain:</p>
              <ul className="list-disc space-y-1.5 pl-5">
                <li>
                  Network SSID (intentionally hidden scan SSIDs default to{" "}
                  <code>Hidden Network</code> when stored; Android
                  connected-network snapshots whose SSID is unavailable due to
                  missing runtime permissions are skipped rather than converted
                  into <code>Hidden Network</code>).
                </li>
                <li>
                  Keyed BSSID digest (<code>bssidHash</code>): when Android or
                  Windows submits a valid hardware MAC address (excluding{" "}
                  <code>02:00:00:00:00:00</code>), the backend computes a
                  server-side HMAC-SHA256 digest and{" "}
                  <strong className="font-semibold text-[#303640]">
                    discards the raw BSSID
                  </strong>{" "}
                  before saving new observations. Neither raw{" "}
                  <code>bssid</code> nor <code>bssidHash</code> is returned in
                  observation API responses (historical unassigned baseline
                  records are preserved separately without alteration).
                </li>
                <li>
                  Signal strength (<code>signalDbm</code>), radio frequency (
                  <code>frequencyMhz</code>), band (<code>2.4 GHz</code>,{" "}
                  <code>5 GHz</code>, <code>6 GHz</code>, or{" "}
                  <code>Unknown</code>), security classification, derived signal
                  quality (<code>0–4</code>), source (<code>android</code>,{" "}
                  <code>windows</code>, or <code>web</code>), and observation
                  timestamp (<code>observedAt</code>).
                </li>
              </ul>
            </PolicySection>

            <PolicySection
              id="section-5"
              number={5}
              title="Location Information"
              direction={getAlternatingDirection(4, 2)}
            >
              <p>
                When location permissions are enabled on your scanning device and
                you confirm an upload, an observation may include your
                Android or Windows device&apos;s GPS/Windows Location latitude,
                longitude, and estimated accuracy radius in meters (
                <code>accuracyMeters</code>). If Windows Location is disabled or
                unavailable on a laptop, observations upload with{" "}
                <code>null</code> location rather than guessed or default
                coordinates.
              </p>
              <p>
                <strong className="font-semibold text-[#303640]">
                  Important distinction:
                </strong>{" "}
                Location coordinates represent the scanning device&apos;s
                position at the time of measurement—not the physical street
                address or hardware position of a Wi-Fi router. In the web
                dashboard, pressing <em>Locate me</em> also uses your browser&apos;s
                geolocation API strictly locally in the browser to center your
                map view without uploading your browser position to the backend.
              </p>
            </PolicySection>

            <PolicySection
              id="section-6"
              number={6}
              title="Internet Performance Measurements"
              direction={getAlternatingDirection(5, 2)}
            >
              <p>
                Wi-Fi network observations are distinct from internet
                performance measurements. When an authenticated user triggers a
                performance test from the web dashboard (
                <code>POST /api/performance</code>), tests execute from the{" "}
                <strong className="font-semibold text-[#303640]">
                  WiFiVision backend host
                </strong>{" "}
                against configured Cloudflare test endpoints and save latency
                (mean and median in ms across 10 probes after 1 warm-up), jitter
                (ms), 1 MB download and upload throughput (Mbps), a 20-probe
                HTTP application-level packet-loss summary, source label (
                <code>backend</code>), target test server label (
                <code>Cloudflare</code>), timestamp (<code>measuredAt</code>),
                and your account owner <code>userId</code>. Unauthenticated and{" "}
                <code>GET /api/performance</code> requests are rejected (
                <code>401</code> / <code>405</code>).
              </p>
            </PolicySection>

            <PolicySection
              id="section-7"
              number={7}
              title="How We Use Information"
              direction={getAlternatingDirection(6, 0)}
            >
              <p>Collected data is used strictly to operate WiFiVision:</p>
              <ul className="list-disc space-y-1.5 pl-5">
                <li>
                  To create, verify, and authenticate your user session.
                </li>
                <li>
                  To compute signal quality scores, band analytics, geographic
                  0.001-degree heatmap cells, observation density counts, and
                  cell GPS precision summaries.
                </li>
                <li>
                  To display historical throughput, latency, jitter, and packet
                  loss trends.
                </li>
                <li>
                  To protect backend services from abuse via rate limiting and
                  request validation.
                </li>
              </ul>
            </PolicySection>

            <PolicySection
              id="section-8"
              number={8}
              title="How Information Is Stored"
              direction={getAlternatingDirection(7, 0)}
            >
              <p>
                Account records, temporary 5-minute pairing-code hashes, paired
                device credential hashes, Wi-Fi observation records, and
                performance measurements are stored in MongoDB collections
                configured for the WiFiVision deployment. On Android (with
                cloud backup disabled via{" "}
                <code>allowBackup=&quot;false&quot;</code>), your paired device
                upload credential is encrypted with Android Keystore (
                <code>AES-GCM</code>) in{" "}
                <code>wifivision_device_credentials</code>. On Windows, paired
                device credentials and queued observations (up to 100 entries)
                are encrypted using current-user Windows DPAPI (
                <code>ProtectedData</code>) under{" "}
                <code>%LOCALAPPDATA%\WiFiVisionCompanion</code>; any browser
                session token linking the Windows Companion loopback API stays
                strictly in React memory rather than Web Storage. In your web
                browser, dashboard UI preferences (default band, monitoring
                interval, and history limit) are saved locally in{" "}
                <code>localStorage</code>.
              </p>
            </PolicySection>

            <PolicySection
              id="section-9"
              number={9}
              title="Data Security"
              direction={getAlternatingDirection(8, 0)}
            >
              <p>
                WiFiVision enforces per-user authentication (<code>protect</code>{" "}
                middleware + <code>{`{ userId }`}</code> database isolation)
                across dashboard observation feeds, heatmaps, density, quality,
                performance history/tests, and paired-device management APIs.
                Core controls include bcrypt password hashing, HS256 JWT
                authentication stored in <code>httpOnly</code>,{" "}
                <code>sameSite: strict</code> cookies with{" "}
                <code>tokenVersion</code> revocation support, short-lived
                platform-scoped pairing codes, scoped Android and Windows device
                upload tokens with server-side pepper hashing and
                timestamp/nonce replay protection, loopback-only companion
                binding (<code>127.0.0.1</code>) with Host/Origin/token checks,
                HMAC-SHA256 hashing of newly submitted BSSIDs, distributed
                Upstash Redis rate limiting (failing closed in production when
                unavailable), Zod input validation, Helmet HTTP headers, and
                same-origin Next.js API proxying.
              </p>
              <p>
                WiFiVision does not claim end-to-end encryption of stored
                telemetry, independent security audits, or third-party
                compliance certifications. No method of electronic storage or
                transmission is 100% secure.
              </p>
            </PolicySection>

            <PolicySection
              id="section-10"
              number={10}
              title="Data Sharing and Third Parties"
              direction={getAlternatingDirection(9, 1)}
            >
              <p>
                WiFiVision does not sell user account records or Wi-Fi
                observations to advertisers or data brokers. External network
                requests occur only when operating platform features:
              </p>
              <ul className="list-disc space-y-1.5 pl-5">
                <li>
                  Interactive geographic heatmaps load map tiles from{" "}
                  <a
                    href="https://www.openstreetmap.org/copyright"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-semibold text-[#9F7C38] hover:underline"
                  >
                    OpenStreetMap
                  </a>{" "}
                  (<code>tile.openstreetmap.org</code>).
                </li>
                <li>
                  Performance diagnostics measure HTTP connectivity against
                  configured test endpoints (Cloudflare).
                </li>
                <li>
                  Distributed rate limiting and replay-nonce checks communicate
                  with the deployment&apos;s Upstash Redis REST store when
                  configured for production.
                </li>
                <li>
                  Email verification codes are delivered through the SMTP mail
                  provider configured for the deployment.
                </li>
              </ul>
            </PolicySection>

            <PolicySection
              id="section-11"
              number={11}
              title="Cookies and Session Information"
              direction={getAlternatingDirection(10, 1)}
            >
              <p>
                WiFiVision uses strictly necessary same-origin cookies for
                authentication and onboarding—not advertising or cross-site
                behavioral tracking cookies:
              </p>
              <ul className="list-disc space-y-1.5 pl-5">
                <li>
                  <code>wifivision_token</code> (<code>httpOnly</code>,{" "}
                  <code>sameSite: strict</code>, <code>secure</code> in
                  production, 24-hour default expiry): maintains your signed-in
                  authenticated session with <code>tokenVersion</code>{" "}
                  revocation verification.
                </li>
                <li>
                  <code>wifivision_setup_token</code> (<code>httpOnly</code>,{" "}
                  <code>sameSite: strict</code>, <code>secure</code> in
                  production, 15-minute expiry): temporary cookie bridging email
                  OTP verification to password creation.
                </li>
                <li>
                  Browser <code>localStorage</code> key{" "}
                  <code>wifivision.localPreferences.v1</code>: stores your local
                  display preferences manageable in{" "}
                  <Link
                    href="/settings"
                    className="font-semibold text-[#9F7C38] hover:underline"
                  >
                    Settings
                  </Link>
                  .
                </li>
              </ul>
            </PolicySection>

            <PolicySection
              id="section-12"
              number={12}
              title="Data Retention"
              direction={getAlternatingDirection(11, 1)}
            >
              <p>
                Account records, uploaded Wi-Fi observations, and performance
                measurements remain stored in MongoDB until explicitly removed;
                WiFiVision does not define a fixed retention schedule or run an
                automated time-based deletion job in application code. Any
                formal retention period must be confirmed and enforced by your
                deployment operator.
              </p>
            </PolicySection>

            <PolicySection
              id="section-13"
              number={13}
              title="User Rights"
              direction={getAlternatingDirection(12, 2)}
            >
              <p>
                You can inspect your signed-in account details, generate or
                revoke paired Android or Windows device credentials, sign out
                all active sessions, and reset local display preferences anytime
                in{" "}
                <Link
                  href="/settings"
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  Settings
                </Link>
                , revoke location/Wi-Fi runtime permissions in Android or
                Windows Settings, or refrain from confirming companion uploads
                while keeping auto-upload off. Self-service profile editing (name,
                email, password), password reset, and automated data
                export/deletion are not implemented in the current release
                interface. Any formal access, correction, or removal requests
                depend on your deployment operator; see{" "}
                <a
                  href="#section-14"
                  onClick={(event) => {
                    event.preventDefault();
                    scrollToSection("section-14");
                  }}
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  Section 14
                </a>{" "}
                and{" "}
                <a
                  href="#section-18"
                  onClick={(event) => {
                    event.preventDefault();
                    scrollToSection("section-18");
                  }}
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  Section 18
                </a>
                .
              </p>
            </PolicySection>

            <PolicySection
              id="section-14"
              number={14}
              title="Account and Data Deletion"
              direction={getAlternatingDirection(13, 2)}
            >
              <p>
                Self-service account or telemetry deletion is not currently
                implemented in the web dashboard UI or backend API endpoints.
                Removing stored MongoDB records requires manual administrative
                action by the deployment operator (see{" "}
                <a
                  href="#section-18"
                  onClick={(event) => {
                    event.preventDefault();
                    scrollToSection("section-18");
                  }}
                  className="font-semibold text-[#9F7C38] hover:underline"
                >
                  Section 18
                </a>
                ). On Android, clearing app storage or uninstalling the app
                permanently removes local queue entries and saved API settings
                on your device.
              </p>
            </PolicySection>

            <PolicySection
              id="section-15"
              number={15}
              title="Children's Privacy"
              direction={getAlternatingDirection(14, 2)}
            >
              <p>
                Registration collects full name and email address without an
                automated age-verification gate in application code. WiFiVision
                is not intended for children under the minimum digital consent
                age defined by applicable law; eligible age thresholds and
                compliance oversight must be confirmed by your deployment
                operator.
              </p>
            </PolicySection>

            <PolicySection
              id="section-16"
              number={16}
              title="International Data Transfers"
              direction={getAlternatingDirection(15, 0)}
            >
              <p>
                Depending on where the MongoDB cluster and application servers
                are hosted, submitted records may be processed in regions
                outside your immediate jurisdiction as configured for your
                WiFiVision environment. Hosting countries and applicable
                cross-border transfer safeguards depend on the deployment
                operator&apos;s infrastructure and are not defined inside
                application code.
              </p>
            </PolicySection>

            <PolicySection
              id="section-17"
              number={17}
              title="Changes to This Privacy Policy"
              direction={getAlternatingDirection(16, 0)}
            >
              <p>
                We may update this Privacy Policy as WiFiVision evolves (for
                example, when future sign-in methods, self-service password
                reset, or account deletion controls are added). Any updates will
                revise the &ldquo;Last updated&rdquo; date at the top of this
                page.
              </p>
            </PolicySection>

            <PolicySection
              id="section-18"
              number={18}
              title="Contact Information"
              direction={getAlternatingDirection(17, 0)}
            >
              <p>
                Verified legal controller identity, physical address, and privacy
                contact details are not embedded in this release&apos;s source
                code and must be provided by your deployment operator. For
                privacy inquiries, data questions, or manual deletion requests,
                contact the administrator of your WiFiVision deployment or
                consult the platform links below:
              </p>
              <div className="grid grid-cols-1 gap-2.5 rounded-xl border border-[#303640]/10 bg-[#F7F6F2] p-4 text-xs sm:grid-cols-2">
                <Link
                  href="/about"
                  className="wv-btn-secondary flex items-center justify-between rounded-lg border border-[#303640]/12 bg-white px-3.5 py-2.5 font-semibold text-[#303640]"
                >
                  <span>About WiFiVision</span>
                  <span className="text-[10px] font-medium text-[#9F7C38]">
                    /about
                  </span>
                </Link>
                <Link
                  href="/faq"
                  className="wv-btn-secondary flex items-center justify-between rounded-lg border border-[#303640]/12 bg-white px-3.5 py-2.5 font-semibold text-[#303640]"
                >
                  <span>Privacy &amp; Technical FAQs</span>
                  <span className="text-[10px] font-medium text-[#9F7C38]">
                    /faq
                  </span>
                </Link>
                <Link
                  href="/login"
                  className="wv-btn-secondary flex items-center justify-between rounded-lg border border-[#303640]/12 bg-white px-3.5 py-2.5 font-semibold text-[#303640]"
                >
                  <span>Account Sign In</span>
                  <span className="text-[10px] font-medium text-[#9F7C38]">
                    /login
                  </span>
                </Link>
                <Link
                  href="/register"
                  className="wv-btn-secondary flex items-center justify-between rounded-lg border border-[#303640]/12 bg-white px-3.5 py-2.5 font-semibold text-[#303640]"
                >
                  <span>Create Account</span>
                  <span className="text-[10px] font-medium text-[#9F7C38]">
                    /register
                  </span>
                </Link>
              </div>
              <div data-no-print className="pt-3 text-xs">
                <Link
                  href="/faq"
                  className="wv-btn-secondary inline-flex h-9 items-center justify-center rounded-xl border border-[#303640]/14 bg-white px-4 text-xs font-semibold text-[#303640]"
                >
                  Explore Privacy &amp; Technical FAQs
                </Link>
              </div>
            </PolicySection>
          </div>
        </div>
      </div>
    </div>
  );
}
