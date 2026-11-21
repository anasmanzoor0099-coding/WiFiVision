"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Download,
  Globe,
  Info,
  KeyRound,
  Laptop,
  LogOut,
  Mail,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  SlidersHorizontal,
  Smartphone,
  Trash2,
  User,
  XCircle,
} from "lucide-react";

import DashboardShell from "@/components/layout/DashboardShell";
import WindowsCompanionCard from "@/components/settings/WindowsCompanionCard";
import {
  getCurrentUser,
  logoutAllSessions,
  logoutUser,
  type AuthUser,
} from "@/lib/auth";
import {
  generateDevicePairingCode,
  getConfiguredApiUrl,
  getSystemHealth,
  listPairedDevices,
  revokePairedDevice,
  type DevicePlatform,
  type PairedDevice,
  type SystemHealth,
} from "@/lib/dashboard";
import {
  formatPairingCountdown,
  getRemainingPairingSeconds,
} from "@/lib/devicePairing";
import {
  getLocalPreferences,
  resetLocalPreferences,
  saveLocalPreferences,
  type BandPreference,
  type LocalDashboardPreferences,
  type MonitoringIntervalSeconds,
  type PerformanceHistoryLimit,
} from "@/lib/preferences";

function formatTimestamp(value?: string | null): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return parsed.toLocaleString();
}

export default function SettingsPage() {
  const router = useRouter();

  const [user, setUser] = useState<AuthUser | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileRefreshedAt, setProfileRefreshedAt] = useState<string | null>(
    null
  );

  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [healthLoading, setHealthLoading] = useState(true);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [healthCheckedAt, setHealthCheckedAt] = useState<string | null>(null);

  const [preferences, setPreferences] = useState<LocalDashboardPreferences>(() =>
    getLocalPreferences()
  );
  const [prefFeedback, setPrefFeedback] = useState<string | null>(null);
  const [prefError, setPrefError] = useState<string | null>(null);

  const [signingOut, setSigningOut] = useState(false);
  const [signingOutAll, setSigningOutAll] = useState(false);

  const [devices, setDevices] = useState<PairedDevice[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(true);
  const [devicesError, setDevicesError] = useState<string | null>(null);
  const [pairingPlatform, setPairingPlatform] =
    useState<DevicePlatform>("android");
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [pairingCodePlatform, setPairingCodePlatform] =
    useState<DevicePlatform | null>(null);
  const [pairingExpiresAt, setPairingExpiresAt] = useState<string | null>(null);
  const [pairingSecondsLeft, setPairingSecondsLeft] = useState(0);
  const [generatingCode, setGeneratingCode] = useState(false);
  const [confirmRevokeId, setConfirmRevokeId] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [deviceFeedback, setDeviceFeedback] = useState<string | null>(null);

  const apiBaseUrl = getConfiguredApiUrl();

  const fetchProfile = useCallback(async () => {
    try {
      const response = await getCurrentUser();
      if (response.user) {
        setUser(response.user);
        setProfileError(null);
        setProfileRefreshedAt(new Date().toISOString());
      } else {
        setUser(null);
        setProfileError("No active authenticated user returned by /api/auth/me.");
      }
    } catch (err) {
      setProfileError(
        err instanceof Error
          ? err.message
          : "Unable to refresh authenticated profile details."
      );
    } finally {
      setProfileLoading(false);
    }
  }, []);

  const refreshProfile = useCallback(() => {
    setProfileLoading(true);
    setProfileError(null);
    void fetchProfile();
  }, [fetchProfile]);

  const fetchHealth = useCallback(async () => {
    try {
      const result = await getSystemHealth();
      setHealth(result);
      setHealthError(null);
      setHealthCheckedAt(new Date().toISOString());
    } catch (err) {
      setHealth(null);
      setHealthError(
        err instanceof Error
          ? err.message
          : "Backend health check failed or service unavailable."
      );
      setHealthCheckedAt(new Date().toISOString());
    } finally {
      setHealthLoading(false);
    }
  }, []);

  const refreshHealth = useCallback(() => {
    setHealthLoading(true);
    setHealthError(null);
    void fetchHealth();
  }, [fetchHealth]);

  const fetchDevices = useCallback(async () => {
    try {
      const response = await listPairedDevices();
      setDevices(response.devices || []);
      setDevicesError(null);
    } catch (err) {
      setDevicesError(
        err instanceof Error
          ? err.message
          : "Unable to load paired devices."
      );
    } finally {
      setDevicesLoading(false);
    }
  }, []);

  const refreshDevices = useCallback(() => {
    setDevicesLoading(true);
    setDevicesError(null);
    void fetchDevices();
  }, [fetchDevices]);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (!active) return;
      void fetchProfile();
      void fetchHealth();
      void fetchDevices();
    });
    return () => {
      active = false;
    };
  }, [fetchDevices, fetchHealth, fetchProfile]);

  useEffect(() => {
    if (!pairingExpiresAt) return;

    const timer = window.setInterval(() => {
      const remaining = getRemainingPairingSeconds(pairingExpiresAt);
      setPairingSecondsLeft(remaining);
      if (remaining <= 0) {
        window.clearInterval(timer);
      }
    }, 1000);

    return () => window.clearInterval(timer);
  }, [pairingExpiresAt]);

  async function handleGeneratePairingCode() {
    if (generatingCode) return;
    setGeneratingCode(true);
    setDevicesError(null);
    setDeviceFeedback(null);

    try {
      const response = await generateDevicePairingCode(pairingPlatform);
      setPairingCode(response.pairingCode);
      setPairingCodePlatform(pairingPlatform);
      setPairingExpiresAt(response.expiresAt);
      setPairingSecondsLeft(
        getRemainingPairingSeconds(response.expiresAt) ||
          response.expiresInSeconds
      );
    } catch (err) {
      setDevicesError(
        err instanceof Error
          ? err.message
          : "Unable to generate a pairing code."
      );
    } finally {
      setGeneratingCode(false);
    }
  }

  async function handleRevokeDevice(deviceId: string) {
    if (revokingId) return;
    setRevokingId(deviceId);
    setDevicesError(null);
    setDeviceFeedback(null);

    try {
      const response = await revokePairedDevice(deviceId);
      setDevices((previous) =>
        previous.map((item) =>
          item.deviceId === deviceId
            ? {
                ...item,
                status: "revoked",
                revokedAt:
                  response.device?.revokedAt ?? new Date().toISOString(),
              }
            : item
        )
      );
      setConfirmRevokeId(null);
      setDeviceFeedback("Device access revoked immediately.");
    } catch (err) {
      setDevicesError(
        err instanceof Error ? err.message : "Unable to revoke device."
      );
    } finally {
      setRevokingId(null);
    }
  }

  function updatePreference<K extends keyof LocalDashboardPreferences>(
    key: K,
    value: LocalDashboardPreferences[K]
  ) {
    setPrefError(null);
    const { saved, storageAvailable } = saveLocalPreferences({ [key]: value });
    setPreferences(saved);

    if (storageAvailable) {
      setPrefFeedback("Saved locally in this browser.");
    } else {
      setPrefError(
        "Browser storage (localStorage) is unavailable; preferences could not be saved."
      );
      setPrefFeedback(null);
    }
  }

  function handleResetPreferences() {
    setPrefError(null);
    const { saved, storageAvailable } = resetLocalPreferences();
    setPreferences(saved);

    if (storageAvailable) {
      setPrefFeedback("Local preferences reset to default values.");
    } else {
      setPrefError(
        "Browser storage (localStorage) is unavailable; defaults could not be persisted."
      );
      setPrefFeedback(null);
    }
  }

  async function handleLogout() {
    if (signingOut || signingOutAll) return;
    setSigningOut(true);

    try {
      await logoutUser();
    } catch {
      // Redirect even if cookie was already cleared or expired.
    } finally {
      router.replace("/login");
    }
  }

  async function handleLogoutAll() {
    if (signingOut || signingOutAll) return;
    setSigningOutAll(true);

    try {
      await logoutAllSessions();
    } catch {
      // Proceed to login on expiry.
    } finally {
      router.replace("/login");
    }
  }

  const backendHealthy =
    health !== null &&
    health.success &&
    (health.status === "healthy" || health.status === "ok");

  const cardClass =
    "rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_12px_rgba(48,54,64,0.04)]";

  return (
    <DashboardShell>
      <div className="space-y-6">
        <div className="border-b border-[#303640]/[0.08] pb-5">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[#9F7C38]">
            <span className="h-1.5 w-1.5 rounded-full bg-[#C6A15B]" />
            Account, Preferences &amp; Environment
          </div>
          <h1 className="mt-1.5 text-2xl font-semibold tracking-tight text-[#303640] sm:text-[26px]">
            Workspace Settings
          </h1>
          <p className="mt-1 text-sm text-[#5E6572]">
            Manage your verified session, refresh profile and health checks,
            and configure browser-local dashboard defaults.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* Account Settings */}
          <section className={`${cardClass} p-6`}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
                  <User className="h-5 w-5" />
                </div>
                <div>
                  <h2 className="text-base font-semibold text-[#303640]">
                    Account Settings
                  </h2>
                  <p className="text-xs text-[#5E6572]">
                    Identity verified via GET /api/auth/me
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => void refreshProfile()}
                disabled={profileLoading}
                className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] disabled:opacity-50"
              >
                <RefreshCw
                  size={13}
                  className={
                    profileLoading ? "animate-spin text-[#9F7C38]" : "text-[#9F7C38]"
                  }
                />
                {profileLoading ? "Refreshing..." : "Refresh Profile"}
              </button>
            </div>

            {profileError && (
              <div
                role="alert"
                className="mt-4 flex items-center gap-2 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-3.5 py-2.5 text-xs text-[#B83B3B]"
              >
                <AlertCircle size={15} className="shrink-0" />
                <span>{profileError}</span>
              </div>
            )}

            {profileLoading && !user ? (
              <div className="mt-5 h-36 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : (
              <dl className="mt-5 space-y-3 text-sm">
                <div className="flex items-center justify-between gap-4 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <dt className="shrink-0 text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    Full Name
                  </dt>
                  <dd className="min-w-0 truncate text-right font-medium text-[#303640]">
                    {user?.name || "—"}
                  </dd>
                </div>

                <div className="flex items-center justify-between gap-4 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <dt className="flex shrink-0 items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    <Mail className="h-3.5 w-3.5" />
                    Email Address
                  </dt>
                  <dd className="min-w-0 truncate text-right font-medium text-[#303640]">
                    {user?.email || "—"}
                  </dd>
                </div>

                <div className="flex items-center justify-between gap-4 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <dt className="shrink-0 text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    Account ID
                  </dt>
                  <dd className="min-w-0 truncate text-right font-mono text-xs text-[#5E6572]">
                    {user?.id || "—"}
                  </dd>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <dt className="text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    Verification Status
                  </dt>
                  <dd>
                    {user ? (
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${
                          user.isEmailVerified
                            ? "border-[#2E7D5B]/25 bg-[#2E7D5B]/10 text-[#2E7D5B]"
                            : "border-[#B83B3B]/25 bg-[#B83B3B]/10 text-[#B83B3B]"
                        }`}
                      >
                        {user.isEmailVerified ? (
                          <>
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            Verified
                          </>
                        ) : (
                          <>
                            <XCircle className="h-3.5 w-3.5" />
                            Unverified
                          </>
                        )}
                      </span>
                    ) : (
                      "—"
                    )}
                  </dd>
                </div>
              </dl>
            )}

            <div className="mt-4 flex items-start gap-2.5 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9]/80 px-3.5 py-3 text-xs text-[#5E6572]">
              <Info size={15} className="mt-0.5 shrink-0 text-[#9F7C38]" />
              <div className="space-y-1">
                <p>
                  <strong className="font-semibold text-[#303640]">
                    Profile editing unavailable:
                  </strong>{" "}
                  Updating account name, email, or password after registration is
                  not exposed by the backend authentication API.
                </p>
                {profileRefreshedAt && (
                  <p className="text-[11px] text-[#868D98]">
                    Last synced: {formatTimestamp(profileRefreshedAt)}
                  </p>
                )}
              </div>
            </div>
          </section>

          {/* Security, Session & System Info */}
          <div className="space-y-6">
            <section className={`${cardClass} p-6`}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
                    <ShieldCheck className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-base font-semibold text-[#303640]">
                      Security & Session
                    </h2>
                    <p className="text-xs text-[#5E6572]">
                      Protected by HTTP-only cookie authentication
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void handleLogoutAll()}
                    disabled={signingOut || signingOutAll}
                    className="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] disabled:opacity-50"
                  >
                    {signingOutAll ? "Revoking..." : "Sign out all"}
                  </button>

                  <button
                    type="button"
                    onClick={() => void handleLogout()}
                    disabled={signingOut || signingOutAll}
                    className="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-xl border border-[#B83B3B]/30 bg-white px-3 py-2 text-xs font-semibold text-[#B83B3B] transition hover:bg-[#B83B3B]/[0.06] disabled:opacity-50"
                  >
                    <LogOut size={13} />
                    {signingOut ? "Signing out..." : "Sign out"}
                  </button>
                </div>
              </div>

              <div className="mt-5 space-y-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <span className="shrink-0 text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    Session State
                  </span>
                  {user && !profileError ? (
                    <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#2E7D5B]">
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      Authenticated (HTTP-only Cookie)
                    </span>
                  ) : profileLoading ? (
                    <span className="text-xs font-medium text-[#5E6572]">
                      Verifying...
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#B83B3B]">
                      <XCircle className="h-3.5 w-3.5" />
                      Session Check Failed
                    </span>
                  )}
                </div>

                <p className="rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9]/70 px-3.5 py-2.5 text-[11px] leading-relaxed text-[#5E6572]">
                  Session tokens are stored exclusively in secure HTTP-only
                  cookies and are intentionally unreadable from client JavaScript.
                </p>
              </div>
            </section>

            {/* System Information */}
            <section className={`${cardClass} p-6`}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
                    <Globe className="h-5 w-5" />
                  </div>
                  <div>
                    <h2 className="text-base font-semibold text-[#303640]">
                      System Information
                    </h2>
                    <p className="text-xs text-[#5E6572]">
                      Configured backend API and live service health
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => void refreshHealth()}
                  disabled={healthLoading}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] disabled:opacity-50"
                >
                  <RefreshCw
                    size={13}
                    className={
                      healthLoading
                        ? "animate-spin text-[#9F7C38]"
                        : "text-[#9F7C38]"
                    }
                  />
                  {healthLoading ? "Checking..." : "Refresh Health"}
                </button>
              </div>

              <div className="mt-5 space-y-3 text-sm">
                <div className="flex items-center justify-between gap-4 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <span className="shrink-0 text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    Backend API URL
                  </span>
                  <code className="truncate rounded bg-white px-2 py-0.5 text-xs text-[#303640]">
                    {apiBaseUrl ?? "Not configured (NEXT_PUBLIC_API_URL)"}
                  </code>
                </div>

                <div className="flex items-center justify-between gap-4 rounded-xl border border-[#303640]/[0.07] bg-[#F7F6F2] px-4 py-3">
                  <span className="shrink-0 text-xs font-medium uppercase tracking-wider text-[#868D98]">
                    Service & Database
                  </span>
                  {healthLoading && !health && !healthError ? (
                    <span className="text-right text-xs text-[#5E6572]">
                      Checking...
                    </span>
                  ) : backendHealthy ? (
                    <span className="text-right text-xs font-semibold text-[#2E7D5B]">
                      {health?.service ?? "WiFiVision Backend"} · DB{" "}
                      {health?.database ?? "connected"}
                    </span>
                  ) : (
                    <span className="text-right text-xs font-semibold text-[#B83B3B]">
                      {healthError ||
                        `Degraded (DB: ${health?.database ?? "unavailable"})`}
                    </span>
                  )}
                </div>

                {healthCheckedAt && (
                  <p className="text-[11px] text-[#868D98]">
                    Last health check: {formatTimestamp(healthCheckedAt)}
                    {health?.timestamp
                      ? ` · Server clock: ${formatTimestamp(health.timestamp)}`
                      : ""}
                  </p>
                )}
              </div>
            </section>
          </div>
        </div>

        {/* Application Settings (Local Browser Preferences) */}
        <section className={`${cardClass} p-6`}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
                <SlidersHorizontal className="h-5 w-5" />
              </div>
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-semibold text-[#303640]">
                    Application Settings
                  </h2>
                  <span className="rounded-full border border-[#C6A15B]/35 bg-[#F1EFE9] px-2.5 py-0.5 text-[10px] font-semibold text-[#9F7C38]">
                    Saved locally in browser
                  </span>
                </div>
                <p className="text-xs text-[#5E6572]">
                  These preferences persist in browser <code>localStorage</code>{" "}
                  and apply immediately to dashboard pages. Server-synced user
                  preferences are not currently supported by the backend API.
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={handleResetPreferences}
              className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9]"
            >
              <RotateCcw size={13} className="text-[#9F7C38]" />
              Reset Defaults
            </button>
          </div>

          {prefFeedback && (
            <div
              role="status"
              className="mt-4 flex items-center gap-2 rounded-xl border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.07] px-3.5 py-2.5 text-xs font-medium text-[#2E7D5B]"
            >
              <CheckCircle2 size={15} className="shrink-0" />
              <span>{prefFeedback}</span>
            </div>
          )}

          {prefError && (
            <div
              role="alert"
              className="mt-4 flex items-center gap-2 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-3.5 py-2.5 text-xs font-medium text-[#B83B3B]"
            >
              <AlertCircle size={15} className="shrink-0" />
              <span>{prefError}</span>
            </div>
          )}

          <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <label
                htmlFor="pref-default-band"
                className="block text-xs font-semibold text-[#303640]"
              >
                Default Frequency Filter
              </label>
              <p className="mt-1 text-[11px] text-[#5E6572]">
                Used when opening Heatmap, Explorer, and Analytics.
              </p>
              <select
                id="pref-default-band"
                value={preferences.defaultBand}
                onChange={(e) =>
                  updatePreference("defaultBand", e.target.value as BandPreference)
                }
                className="mt-3 h-9 w-full rounded-lg border border-[#303640]/15 bg-white px-2.5 text-xs font-semibold text-[#303640] outline-none focus:border-[#C6A15B]"
              >
                <option value="all">All Bands</option>
                <option value="2.4 GHz">2.4 GHz</option>
                <option value="5 GHz">5 GHz</option>
                <option value="6 GHz">6 GHz</option>
              </select>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <span className="block text-xs font-semibold text-[#303640]">
                Monitoring Auto-Refresh
              </span>
              <p className="mt-1 text-[11px] text-[#5E6572]">
                Automatically start polling on System Monitoring.
              </p>
              <label className="mt-3 flex cursor-pointer items-center justify-between rounded-lg border border-[#303640]/15 bg-white px-3 py-2 text-xs font-semibold text-[#303640]">
                <span>
                  {preferences.monitoringAutoRefresh ? "Enabled" : "Disabled"}
                </span>
                <input
                  type="checkbox"
                  checked={preferences.monitoringAutoRefresh}
                  onChange={(e) =>
                    updatePreference("monitoringAutoRefresh", e.target.checked)
                  }
                  className="h-4 w-4 accent-[#C6A15B]"
                />
              </label>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <label
                htmlFor="pref-monitoring-interval"
                className="block text-xs font-semibold text-[#303640]"
              >
                Monitoring Poll Interval
              </label>
              <p className="mt-1 text-[11px] text-[#5E6572]">
                Refresh interval when auto-refresh is enabled.
              </p>
              <select
                id="pref-monitoring-interval"
                value={preferences.monitoringIntervalSeconds}
                onChange={(e) =>
                  updatePreference(
                    "monitoringIntervalSeconds",
                    Number(e.target.value) as MonitoringIntervalSeconds
                  )
                }
                className="mt-3 h-9 w-full rounded-lg border border-[#303640]/15 bg-white px-2.5 text-xs font-semibold text-[#303640] outline-none focus:border-[#C6A15B]"
              >
                <option value={15}>Every 15 seconds</option>
                <option value={30}>Every 30 seconds</option>
                <option value={60}>Every 60 seconds</option>
              </select>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <label
                htmlFor="pref-history-limit"
                className="block text-xs font-semibold text-[#303640]"
              >
                Performance History Window
              </label>
              <p className="mt-1 text-[11px] text-[#5E6572]">
                Default sample count on Performance Analytics.
              </p>
              <select
                id="pref-history-limit"
                value={preferences.performanceHistoryLimit}
                onChange={(e) =>
                  updatePreference(
                    "performanceHistoryLimit",
                    Number(e.target.value) as PerformanceHistoryLimit
                  )
                }
                className="mt-3 h-9 w-full rounded-lg border border-[#303640]/15 bg-white px-2.5 text-xs font-semibold text-[#303640] outline-none focus:border-[#C6A15B]"
              >
                <option value={20}>Last 20 measurements</option>
                <option value={50}>Last 50 measurements</option>
                <option value={100}>Last 100 measurements</option>
              </select>
            </div>
          </div>
        </section>

        {/* Paired Devices (Android & Windows) */}
        <section className={`${cardClass} p-6`}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
                <KeyRound className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Paired Devices (Android &amp; Windows)
                </h2>
                <p className="text-xs text-[#5E6572]">
                  Generate temporary platform-scoped pairing codes and manage
                  scoped device upload credentials for your private workspace.
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div
                role="group"
                aria-label="Target device platform"
                className="inline-flex rounded-xl border border-[#303640]/15 bg-[#F7F6F2] p-0.5 text-xs font-semibold"
              >
                <button
                  type="button"
                  onClick={() => setPairingPlatform("android")}
                  className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 transition ${
                    pairingPlatform === "android"
                      ? "bg-[#303640] text-white"
                      : "text-[#5E6572] hover:text-[#303640]"
                  }`}
                >
                  <Smartphone size={12} />
                  Android
                </button>
                <button
                  type="button"
                  onClick={() => setPairingPlatform("windows")}
                  className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 transition ${
                    pairingPlatform === "windows"
                      ? "bg-[#303640] text-white"
                      : "text-[#5E6572] hover:text-[#303640]"
                  }`}
                >
                  <Laptop size={12} />
                  Windows
                </button>
              </div>

              <button
                type="button"
                onClick={() => void refreshDevices()}
                disabled={devicesLoading}
                className="inline-flex items-center gap-1.5 rounded-xl border border-[#303640]/15 bg-[#F7F6F2] px-3 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] disabled:opacity-50"
              >
                <RefreshCw
                  size={13}
                  className={
                    devicesLoading
                      ? "animate-spin text-[#9F7C38]"
                      : "text-[#9F7C38]"
                  }
                />
                Refresh
              </button>

              <button
                type="button"
                onClick={() => void handleGeneratePairingCode()}
                disabled={generatingCode}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[#303640] px-4 py-2 text-xs font-semibold text-white shadow-xs transition hover:bg-[#3E4654] disabled:opacity-50"
              >
                <KeyRound size={13} className="text-[#C6A15B]" />
                {generatingCode
                  ? "Generating..."
                  : `Generate ${pairingPlatform === "windows" ? "Windows" : "Android"} Code`}
              </button>
            </div>
          </div>

          {pairingCode && (
            <div className="mt-5 rounded-xl border border-[#C6A15B]/45 bg-[#F7F6F2] p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38]">
                    Single-Use{" "}
                    {pairingCodePlatform === "windows" ? "Windows" : "Android"}{" "}
                    Pairing Code
                  </p>
                  <p className="mt-1 font-mono text-2xl font-bold tracking-[0.22em] text-[#303640]">
                    {pairingCode}
                  </p>
                </div>

                <div className="text-right">
                  {pairingSecondsLeft > 0 ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-[#2E7D5B]/25 bg-[#2E7D5B]/10 px-3 py-1 text-xs font-semibold tabular-nums text-[#2E7D5B]">
                      <Clock size={12} />
                      Expires in {formatPairingCountdown(pairingSecondsLeft)}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-[#B83B3B]/30 bg-[#B83B3B]/10 px-3 py-1 text-xs font-semibold text-[#B83B3B]">
                      <XCircle size={12} />
                      Expired — generate a new code
                    </span>
                  )}
                  <p className="mt-1 text-[11px] text-[#5E6572]">
                    {pairingCodePlatform === "windows"
                      ? "Enter this code in the WiFiVision Windows Companion app (DPAPI storage)."
                      : "Enter this code in the Android companion app's Pairing panel."}
                  </p>
                </div>
              </div>
            </div>
          )}

          {deviceFeedback && (
            <div
              role="status"
              className="mt-4 flex items-center gap-2 rounded-xl border border-[#2E7D5B]/25 bg-[#2E7D5B]/[0.07] px-3.5 py-2.5 text-xs font-medium text-[#2E7D5B]"
            >
              <CheckCircle2 size={15} className="shrink-0" />
              <span>{deviceFeedback}</span>
            </div>
          )}

          {devicesError && (
            <div
              role="alert"
              className="mt-4 flex items-center gap-2 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.06] px-3.5 py-2.5 text-xs font-medium text-[#B83B3B]"
            >
              <AlertCircle size={15} className="shrink-0" />
              <span>{devicesError}</span>
            </div>
          )}

          <div className="mt-5 space-y-2.5">
            {devicesLoading && devices.length === 0 ? (
              <div className="h-20 animate-pulse rounded-xl bg-[#F1EFE9]" />
            ) : devices.length === 0 ? (
              <div className="rounded-xl border border-dashed border-[#303640]/15 bg-[#F7F6F2] px-4 py-6 text-center text-xs text-[#5E6572]">
                No Android or Windows companion devices paired yet. Select a
                platform above, generate a temporary code, and pair your scanner
                to authorize private observation uploads.
              </div>
            ) : (
              devices.map((device) => {
                const isActive = device.status === "active";
                const isWindows = device.platform === "windows";
                const confirming = confirmRevokeId === device.deviceId;
                const revoking = revokingId === device.deviceId;

                return (
                  <div
                    key={device.deviceId}
                    className="flex flex-col justify-between gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4 sm:flex-row sm:items-center"
                  >
                    <div className="min-w-0 space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="truncate text-sm font-semibold text-[#303640]">
                          {device.deviceName}
                        </span>
                        <span className="inline-flex items-center gap-1 rounded-full border border-[#303640]/15 bg-white px-2 py-0.5 text-[10px] font-semibold text-[#303640]">
                          {isWindows ? (
                            <Laptop size={10} className="text-[#C6A15B]" />
                          ) : (
                            <Smartphone size={10} className="text-[#C6A15B]" />
                          )}
                          {isWindows ? "Windows" : "Android"}
                        </span>
                        <span
                          className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${
                            isActive
                              ? "border-[#2E7D5B]/25 bg-[#2E7D5B]/10 text-[#2E7D5B]"
                              : "border-[#B83B3B]/25 bg-[#B83B3B]/10 text-[#B83B3B]"
                          }`}
                        >
                          {isActive ? "Active" : "Revoked"}
                        </span>
                      </div>
                      <p className="text-[11px] text-[#5E6572]">
                        Last used:{" "}
                        {device.lastUsedAt
                          ? formatTimestamp(device.lastUsedAt)
                          : "Never"}{" "}
                        · Expires: {formatTimestamp(device.expiresAt)}
                      </p>
                    </div>

                    {isActive && (
                      <div className="flex shrink-0 items-center gap-2">
                        {confirming ? (
                          <>
                            <button
                              type="button"
                              onClick={() =>
                                void handleRevokeDevice(device.deviceId)
                              }
                              disabled={revoking}
                              className="inline-flex items-center gap-1 rounded-lg bg-[#B83B3B] px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-[#992F2F] disabled:opacity-50"
                            >
                              {revoking ? "Revoking..." : "Confirm Revoke"}
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmRevokeId(null)}
                              disabled={revoking}
                              className="rounded-lg border border-[#303640]/15 bg-white px-2.5 py-1.5 text-xs font-semibold text-[#303640] hover:bg-[#F1EFE9]"
                            >
                              Cancel
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            onClick={() => setConfirmRevokeId(device.deviceId)}
                            className="inline-flex items-center gap-1.5 rounded-xl border border-[#B83B3B]/30 bg-white px-3 py-1.5 text-xs font-semibold text-[#B83B3B] transition hover:bg-[#B83B3B]/[0.06]"
                          >
                            <Trash2 size={12} />
                            Revoke
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </section>

        {/* Live Windows Companion Bridge */}
        <WindowsCompanionCard />

        {/* Companion Setup Instructions */}
        <section className={`${cardClass} p-6`}>
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#F1EFE9] text-[#9F7C38]">
                <Smartphone className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-semibold text-[#303640]">
                  Android App &amp; Windows Companion Setup
                </h2>
                <p className="text-xs text-[#5E6572]">
                  Install the signed WiFiVision Android APK directly from the
                  public Download page, or run the native Windows Companion from{" "}
                  <code>windows/</code> for laptop 802.11 scanning.
                </p>
              </div>
            </div>

            <Link
              href="/download"
              className="inline-flex items-center gap-2 rounded-xl bg-[#303640] px-4 py-2.5 text-xs font-semibold text-white shadow-xs transition hover:bg-[#3E4654]"
            >
              <Download size={14} className="text-[#C6A15B]" />
              Download App (v1.1.0)
            </Link>
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-3">
            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-[#9F7C38]">
                1. Install Android or Build Windows
              </p>
              <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
                Download the signed Android APK v1.1.0 from{" "}
                <Link
                  href="/download"
                  className="font-semibold text-[#303640] underline hover:text-[#9F7C38]"
                >
                  /download
                </Link>{" "}
                (configured for{" "}
                <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
                  {apiBaseUrl ?? "https://wi-fi-vision-backend.vercel.app"}
                </code>
                ), or build{" "}
                <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
                  windows/WiFiVision.Windows.csproj
                </code>{" "}
                with MSBuild to run{" "}
                <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
                  WiFiVisionCompanion.exe
                </code>
                .
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-[#9F7C38]">
                2. Sign In, Pair &amp; Link Bridge
              </p>
              <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
                Android supports native account sign-in plus optional scoped
                device pairing (Android Keystore + EncryptedSharedPreferences).
                Windows pairs via single-use code (DPAPI storage) and connects
                at{" "}
                <code className="rounded bg-white px-1 py-0.5 text-[#303640]">
                  127.0.0.1:48271
                </code>{" "}
                using its in-memory session token.
              </p>
            </div>

            <div className="rounded-xl border border-[#303640]/[0.08] bg-[#F7F6F2] p-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-[#9F7C38]">
                3. Scan Locally &amp; Confirm Uploads
              </p>
              <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
                Use <strong>Start Scan</strong> or{" "}
                <strong>Start Monitoring</strong> first — scans stay local with
                auto-upload OFF. Review detected SSIDs and location status, then
                explicitly confirm <strong>UPLOAD NOW</strong> to sync across
                Dashboard, Network Explorer, and Heatmap.
              </p>
            </div>
          </div>
        </section>
      </div>
    </DashboardShell>
  );
}
