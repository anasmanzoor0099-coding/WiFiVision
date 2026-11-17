"use client";

import {
  useEffect,
  useRef,
  useState,
  type ClipboardEvent,
  type FormEvent,
  type KeyboardEvent,
} from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Fingerprint,
  Loader2,
  LockKeyhole,
  Mail,
  RefreshCw,
  ShieldCheck,
  Wifi,
} from "lucide-react";

import {
  resendVerificationCode,
  verifyRegistrationCode,
} from "@/lib/auth";

export default function VerifyEmailPage() {
  const router = useRouter();

  const [email, setEmail] = useState(() => {
    if (typeof window === "undefined") return "";
    return new URLSearchParams(window.location.search).get("email") ?? "";
  });
  const [code, setCode] = useState(["", "", "", "", "", ""]);
  const [loading, setLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [resendMessage, setResendMessage] = useState("");
  const [error, setError] = useState("");

  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    inputRefs.current[0]?.focus();
  }, []);

  useEffect(() => {
    if (resendCooldown <= 0) return;

    const timer = window.setInterval(() => {
      setResendCooldown((previous) => Math.max(0, previous - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [resendCooldown]);

  const otpValue = code.join("");

  const handleCodeChange = (index: number, value: string) => {
    const digit = value.replace(/\D/g, "").slice(-1);

    setError("");

    setCode((previous) => {
      const updated = [...previous];
      updated[index] = digit;
      return updated;
    });

    if (digit && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (
    index: number,
    event: KeyboardEvent<HTMLInputElement>
  ) => {
    if (event.key === "Backspace") {
      if (!code[index] && index > 0) {
        inputRefs.current[index - 1]?.focus();
      }
    }

    if (event.key === "ArrowLeft" && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }

    if (event.key === "ArrowRight" && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handlePaste = (event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();

    const pasted = event.clipboardData
      .getData("text")
      .replace(/\D/g, "")
      .slice(0, 6);

    if (!pasted) return;

    const updated = Array.from({ length: 6 }, (_, index) => {
      return pasted[index] || "";
    });

    setCode(updated);
    setError("");

    const nextIndex = Math.min(pasted.length, 5);
    inputRefs.current[nextIndex]?.focus();
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setError("");
    setResendMessage("");

    const normalizedEmail = email.trim().toLowerCase();

    if (!normalizedEmail) {
      setError("Please enter your registered email address.");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
      setError("Please enter a valid email address.");
      return;
    }

    if (otpValue.length !== 6) {
      setError("Please enter all six verification digits.");
      return;
    }

    setLoading(true);

    try {
      const response = await verifyRegistrationCode({
        email: normalizedEmail,
        code: otpValue,
      });

      if (response.success) {
        router.push("/create-password");
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Verification failed. Please try again."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setError("");
    setResendMessage("");

    const normalizedEmail = email.trim().toLowerCase();

    if (!normalizedEmail) {
      setError("Please enter your email address first.");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
      setError("Please enter a valid email address.");
      return;
    }

    if (resendCooldown > 0 || resendLoading) return;

    setResendLoading(true);

    try {
      const response = await resendVerificationCode({
        email: normalizedEmail,
      });

      setCode(["", "", "", "", "", ""]);
      setResendCooldown(response.resendCooldown ?? 60);

      setResendMessage(
        response.message || "Please check your email for your verification code."
      );

      inputRefs.current[0]?.focus();
    } catch (err) {
      const retryAfter = (
        err as Error & { retryAfterSeconds?: number }
      )?.retryAfterSeconds;

      if (retryAfter && retryAfter > 0) {
        setResendCooldown(retryAfter);
      }

      setError(
        err instanceof Error
          ? err.message
          : "Unable to resend the code. Please try again."
      );
    } finally {
      setResendLoading(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#F7F6F2] px-4 py-8 text-[#303640] sm:px-6 lg:py-12">
      <div className="w-full max-w-[1040px] min-w-0">
        {/* Brand header */}
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
            Secure verification
          </div>
        </header>

        {/* Unified card layout */}
        <div className="grid w-full overflow-hidden rounded-2xl border border-[#303640]/10 bg-white shadow-[0_18px_48px_rgba(48,54,64,0.06)] lg:grid-cols-[1fr_0.95fr]">
          {/* Left panel */}
          <section className="flex flex-col justify-between border-b border-[#303640]/[0.08] bg-[#F1EFE9] p-7 sm:p-10 lg:border-b-0 lg:border-r">
            <div>
              <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-white text-[#9F7C38] shadow-2xs">
                <Fingerprint size={24} />
              </div>

              <span className="inline-block text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
                Identity protection
              </span>

              <h1 className="mt-3 text-3xl font-semibold leading-tight tracking-tight text-[#303640] sm:text-[34px]">
                Your network.
                <br />
                Your <span className="text-[#9F7C38]">security.</span>
              </h1>

              <p className="mt-4 max-w-md text-[13px] leading-relaxed text-[#5E6572]">
                Confirm ownership of your email address with the six-digit OTP
                before configuring your account password.
              </p>

              <div className="my-7 h-px w-16 bg-[#C6A15B]/60" />

              <div className="space-y-3">
                <div className="flex items-center gap-3.5 rounded-xl border border-[#303640]/[0.08] bg-white p-4 shadow-2xs">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[#C6A15B]/15 text-[#9F7C38]">
                    <LockKeyhole size={18} />
                  </div>
                  <div>
                    <strong className="block text-xs font-semibold text-[#303640]">
                      Time-limited code
                    </strong>
                    <span className="text-[11px] text-[#5E6572]">
                      Verification codes expire automatically after 10 minutes.
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-8 flex items-center justify-between border-t border-[#303640]/[0.08] pt-5 text-[10px] font-semibold tracking-[0.14em] text-[#868D98]">
              <span>PROTECTED ACTIVATION</span>
              <span>EST. 2026</span>
            </div>
          </section>

          {/* Right verification form */}
          <section className="p-7 sm:p-10">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/30 bg-[#C6A15B]/10 px-3 py-1 text-[10px] font-bold tracking-[0.14em] text-[#9F7C38]">
              <span>02</span>
              <span>EMAIL VERIFICATION</span>
            </div>

            <div className="mt-5 flex h-11 w-11 items-center justify-center rounded-xl border border-[#303640]/10 bg-[#F7F6F2] text-[#303640]">
              <Mail size={20} />
            </div>

            <h2 className="mt-4 text-2xl font-semibold tracking-tight text-[#303640]">
              Verify your <span className="text-[#9F7C38]">email.</span>
            </h2>

            <p className="mt-1.5 text-xs leading-relaxed text-[#5E6572]">
              Enter your registered email and the six-digit verification code to
              continue to password setup.
            </p>

            <form onSubmit={handleSubmit} className="mt-6 space-y-4">
              <div>
                <label
                  htmlFor="verify-email-input"
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
                    id="verify-email-input"
                    type="email"
                    value={email}
                    onChange={(event) => {
                      setEmail(event.target.value);
                      setError("");
                      setResendMessage("");
                    }}
                    placeholder="you@example.com"
                    autoComplete="email"
                    required
                    className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-4 text-sm text-[#303640] placeholder-[#868D98] outline-none transition focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                  />
                </div>
              </div>

              <div>
                <div className="mb-2 flex items-center justify-between">
                  <label className="text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]">
                    Verification code
                  </label>
                  <span className="text-[10px] font-semibold text-[#9F7C38]">
                    {otpValue.length}/6 digits
                  </span>
                </div>

                <div className="grid grid-cols-6 gap-2 sm:gap-2.5" dir="ltr">
                  {code.map((digit, index) => (
                    <input
                      key={index}
                      ref={(element) => {
                        inputRefs.current[index] = element;
                      }}
                      type="text"
                      inputMode="numeric"
                      autoComplete={index === 0 ? "one-time-code" : "off"}
                      maxLength={1}
                      value={digit}
                      disabled={loading || resendLoading}
                      aria-label={`Verification digit ${index + 1}`}
                      onChange={(event) =>
                        handleCodeChange(index, event.target.value)
                      }
                      onKeyDown={(event) => handleKeyDown(index, event)}
                      onPaste={handlePaste}
                      className={`h-12 w-full rounded-xl border text-center text-lg font-bold outline-none transition ${
                        digit
                          ? "border-[#C6A15B] bg-white text-[#303640] ring-2 ring-[#C6A15B]/15"
                          : "border-[#303640]/15 bg-[#F7F6F2]/70 text-[#303640] focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                      }`}
                    />
                  ))}
                </div>
              </div>

              <div className="flex items-center gap-2 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3.5 py-2.5 text-[11px] text-[#5E6572]">
                <LockKeyhole size={14} className="shrink-0 text-[#9F7C38]" />
                <span>Your verification code expires after 10 minutes.</span>
              </div>

              {resendMessage && (
                <div
                  role="status"
                  className="flex items-center gap-2 rounded-xl border border-[#2E7D5B]/30 bg-[#2E7D5B]/[0.08] px-3.5 py-2.5 text-xs font-medium text-[#2E7D5B]"
                >
                  <CheckCircle2 size={15} className="shrink-0" />
                  <span>{resendMessage}</span>
                </div>
              )}

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
                disabled={loading || resendLoading || otpValue.length !== 6}
                className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-sm transition hover:bg-[#3E4654] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {loading ? (
                  <>
                    <Loader2 size={16} className="animate-spin text-[#C6A15B]" />
                    Verifying email...
                  </>
                ) : (
                  <>
                    Verify and continue
                    <ArrowRight size={16} className="text-[#C6A15B]" />
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={handleResend}
                disabled={resendLoading || resendCooldown > 0 || loading}
                className="flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-[#303640]/12 bg-[#F7F6F2] px-4 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#C6A15B]/10 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {resendLoading ? (
                  <>
                    <Loader2 size={14} className="animate-spin text-[#9F7C38]" />
                    Sending new code...
                  </>
                ) : resendCooldown > 0 ? (
                  <>
                    <RefreshCw size={14} className="text-[#868D98]" />
                    Resend available in {resendCooldown}s
                  </>
                ) : (
                  <>
                    <RefreshCw size={14} className="text-[#9F7C38]" />
                    Resend verification code
                  </>
                )}
              </button>
            </form>

            <div className="mt-6 flex items-center justify-between border-t border-[#303640]/10 pt-5 text-xs text-[#5E6572]">
              <Link
                href="/register"
                className="inline-flex items-center gap-1.5 font-semibold text-[#5E6572] transition hover:text-[#303640]"
              >
                <ArrowLeft size={14} />
                Back to registration
              </Link>

              <Link
                href="/login"
                className="font-semibold text-[#9F7C38] hover:underline"
              >
                Sign in
              </Link>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}
