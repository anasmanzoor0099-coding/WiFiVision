"use client";

import { Suspense, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  Download,
  Eye,
  EyeOff,
  Fingerprint,
  Loader2,
  Lock,
  Mail,
  ShieldCheck,
  Wifi,
} from "lucide-react";

import { loginUser } from "@/lib/auth";
import { PublicAuthLinksBar } from "@/components/public/PublicShell";

function LoginFormContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const isRegistered =
    searchParams.get("registered") === "true" ||
    searchParams.get("registered") === "1";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const showRegisteredBanner = isRegistered;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    const cleanEmail = email.trim().toLowerCase();

    if (!cleanEmail || !password) {
      setError("Please enter your email address and password.");
      return;
    }

    try {
      setSubmitting(true);

      await loginUser({
        email: cleanEmail,
        password,
      });

      router.replace("/");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to sign in. Please try again."
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#F7F6F2] px-4 py-8 text-[#303640] sm:px-6 lg:py-12">
      <div className="w-full max-w-[1040px] min-w-0">
        {/* Top brand bar */}
        <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
          <Link href="/login" className="inline-flex items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#303640] shadow-xs">
              <Wifi size={20} className="text-[#C6A15B]" />
            </div>

            <div>
              <div className="text-[19px] font-bold leading-5 tracking-tight text-[#303640]">
                WiFi<span className="text-[#9F7C38]">Vision</span>
              </div>
              <div className="mt-1 text-[9px] font-bold tracking-[0.18em] text-[#868D98]">
                NETWORK INTELLIGENCE
              </div>
            </div>
          </Link>

          <div className="inline-flex items-center gap-2 rounded-full border border-[#303640]/10 bg-white px-3.5 py-2 text-[11px] font-semibold text-[#5E6572] shadow-2xs">
            <ShieldCheck size={14} className="text-[#C6A15B]" />
            Secure sign in
          </div>
        </header>

        {/* Main card */}
        <div className="grid w-full overflow-hidden rounded-2xl border border-[#303640]/10 bg-white shadow-[0_18px_48px_rgba(48,54,64,0.06)] lg:grid-cols-[1fr_0.95fr]">
          {/* Left editorial ivory panel */}
          <section className="flex flex-col justify-between border-b border-[#303640]/[0.08] bg-[#F1EFE9] p-7 sm:p-10 lg:border-b-0 lg:border-r">
            <div>
              <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-white text-[#9F7C38] shadow-2xs">
                <Fingerprint size={24} />
              </div>

              <span className="inline-block text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
                Your network, understood
              </span>

              <h1 className="mt-3 text-3xl font-semibold leading-tight tracking-tight text-[#303640] sm:text-[34px]">
                Your network.
                <br />
                Your <span className="text-[#9F7C38]">insights.</span>
              </h1>

              <p className="mt-4 max-w-md text-[13px] leading-relaxed text-[#5E6572]">
                Sign in to explore Wi-Fi signal strength, inspect recorded GPS
                heatmap observations, and monitor real connection performance.
              </p>

              <div className="my-7 h-px w-16 bg-[#C6A15B]/60" />

              <div className="flex items-center gap-3.5 rounded-xl border border-[#303640]/[0.08] bg-white p-4 shadow-2xs">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[#C6A15B]/15 text-[#9F7C38]">
                  <Activity size={19} />
                </div>

                <div>
                  <strong className="block text-xs font-semibold text-[#303640]">
                    Network intelligence
                  </strong>
                  <span className="text-[11px] text-[#5E6572]">
                    Measured telemetry without guesswork.
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-8 flex items-center justify-between border-t border-[#303640]/[0.08] pt-5 text-[10px] font-semibold tracking-[0.14em] text-[#868D98]">
              <span>BUILT FOR BETTER CONNECTIVITY</span>
              <span>EST. 2026</span>
            </div>
          </section>

          {/* Right login form */}
          <section className="p-7 sm:p-10">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/30 bg-[#C6A15B]/10 px-3 py-1 text-[10px] font-bold tracking-[0.14em] text-[#9F7C38]">
              <span>01</span>
              <span>ACCOUNT ACCESS</span>
            </div>

            <div className="mt-5 flex h-11 w-11 items-center justify-center rounded-xl border border-[#303640]/10 bg-[#F7F6F2] text-[#303640]">
              <Lock size={20} />
            </div>

            <h2 className="mt-4 text-2xl font-semibold tracking-tight text-[#303640]">
              Welcome <span className="text-[#9F7C38]">back.</span>
            </h2>

            <p className="mt-1.5 text-xs leading-relaxed text-[#5E6572]">
              Enter your account credentials to continue to your WiFiVision
              dashboard.
            </p>

            {showRegisteredBanner && (
              <div
                role="status"
                className="mt-5 flex items-start gap-3 rounded-xl border border-[#2E7D5B]/30 bg-[#2E7D5B]/[0.08] px-4 py-3 text-xs text-[#2E7D5B]"
              >
                <CheckCircle2 size={17} className="mt-0.5 shrink-0" />
                <div>
                  <p className="font-semibold">
                    Registration completed successfully
                  </p>
                  <p className="mt-0.5 text-[11px] text-[#2E7D5B]/90">
                    Your email is verified and your password is active. Sign in
                    below to open your dashboard.
                  </p>
                </div>
              </div>
            )}

            <form onSubmit={handleSubmit} className="mt-6 space-y-4">
              <div>
                <label
                  htmlFor="email"
                  className="mb-1.5 block text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]"
                >
                  Email address
                </label>

                <div className="relative flex items-center">
                  <Mail
                    size={16}
                    className="pointer-events-none absolute left-3.5 text-[#868D98]"
                  />

                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(event) => {
                      setEmail(event.target.value);
                      setError("");
                    }}
                    autoComplete="email"
                    placeholder="you@example.com"
                    required
                    className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-4 text-sm text-[#303640] placeholder-[#868D98] outline-none transition-colors focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="password"
                  className="mb-1.5 block text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]"
                >
                  Password
                </label>

                <div className="relative flex items-center">
                  <Lock
                    size={16}
                    className="pointer-events-none absolute left-3.5 text-[#868D98]"
                  />

                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(event) => {
                      setPassword(event.target.value);
                      setError("");
                    }}
                    autoComplete="current-password"
                    placeholder="Enter your password"
                    required
                    className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-11 text-sm text-[#303640] placeholder-[#868D98] outline-none transition-colors focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                  />

                  <button
                    type="button"
                    onClick={() => setShowPassword((current) => !current)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    className="absolute right-3 rounded-lg p-1 text-[#868D98] transition-colors hover:text-[#303640]"
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {error && (
                <div
                  role="alert"
                  className="rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] px-3.5 py-2.5 text-xs font-medium text-[#B83B3B]"
                >
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={submitting || !email.trim() || !password}
                className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-[#3E4654] disabled:cursor-not-allowed disabled:bg-[#767D8A] disabled:text-white/85"
              >
                {submitting ? (
                  <>
                    <span className="inline-flex h-4 w-4 shrink-0 items-center justify-center">
                      <Loader2 size={16} className="animate-spin text-[#C6A15B]" />
                    </span>
                    Signing in...
                  </>
                ) : (
                  <>
                    Sign in to your account
                    <span className="inline-flex h-4 w-4 shrink-0 items-center justify-center">
                      <ArrowRight size={16} className="text-[#C6A15B]" />
                    </span>
                  </>
                )}
              </button>
            </form>

            <div className="my-6 flex items-center gap-3">
              <span className="h-px flex-1 bg-[#303640]/10" />
              <p className="text-[10px] font-bold tracking-[0.14em] text-[#868D98]">
                NEW TO WIFIVISION?
              </p>
              <span className="h-px flex-1 bg-[#303640]/10" />
            </div>

            <Link
              href="/register"
              className="flex h-11 w-full items-center justify-center rounded-xl border border-[#C6A15B]/45 bg-[#F7F6F2] text-xs font-semibold text-[#303640] transition-colors hover:border-[#C6A15B] hover:bg-[#C6A15B]/12"
            >
              Create a new account
            </Link>

            <Link
              href="/download"
              className="mt-2.5 flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-[#303640]/12 bg-white text-xs font-semibold text-[#303640] transition-colors hover:border-[#C6A15B] hover:bg-[#FAF9F5]"
            >
              <Download size={14} className="text-[#9F7C38]" />
              Download App (Android v1.1.0 &amp; Laptop Setup)
            </Link>

            <div className="mt-5 flex items-center justify-center gap-2 text-[11px] text-[#868D98]">
              <ShieldCheck size={14} className="text-[#2E7D5B]" />
              Your account details are securely processed.
            </div>

            <PublicAuthLinksBar />
          </section>
        </div>
      </div>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-[#F7F6F2]">
          <Loader2 size={24} className="animate-spin text-[#C6A15B]" />
        </div>
      }
    >
      <LoginFormContent />
    </Suspense>
  );
}
