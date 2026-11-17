"use client";

import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
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
  Loader2,
  Mail,
  RefreshCw,
  ShieldCheck,
  User,
  Wifi,
} from "lucide-react";

import {
  resendVerificationCode,
  startRegistration,
  verifyRegistrationCode,
} from "@/lib/auth";
import { PublicAuthLinksBar } from "@/components/public/PublicShell";

const OTP_LENGTH = 6;

export default function RegisterPage() {
  const router = useRouter();

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [stage, setStage] = useState<"details" | "verify">("details");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [privacyAccepted, setPrivacyAccepted] = useState(false);

  const otpRefs = useRef<Array<HTMLInputElement | null>>([]);

  useEffect(() => {
    if (resendCooldown <= 0) return;

    const timer = window.setInterval(() => {
      setResendCooldown((previous) => Math.max(0, previous - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [resendCooldown]);

  const otpDigits = Array.from(
    { length: OTP_LENGTH },
    (_, index) => code[index] || ""
  );

  function focusOtp(index: number) {
    const target = Math.max(0, Math.min(index, OTP_LENGTH - 1));

    requestAnimationFrame(() => {
      otpRefs.current[target]?.focus();
      otpRefs.current[target]?.select();
    });
  }

  function updateOtp(value: string, index: number) {
    const digits = value.replace(/\D/g, "");

    if (!digits) {
      const next = code.split("");
      next[index] = "";
      setCode(next.join("").slice(0, OTP_LENGTH));
      return;
    }

    const next = code.split("");

    if (digits.length > 1) {
      const pasted = digits.slice(0, OTP_LENGTH);
      setCode(pasted);
      focusOtp(Math.min(pasted.length, OTP_LENGTH - 1));
      setError("");
      return;
    }

    next[index] = digits[0];
    const updatedCode = next.join("").slice(0, OTP_LENGTH);

    setCode(updatedCode);
    setError("");

    if (index < OTP_LENGTH - 1) {
      focusOtp(index + 1);
    } else {
      otpRefs.current[index]?.blur();
    }
  }

  function handleOtpChange(
    event: ChangeEvent<HTMLInputElement>,
    index: number
  ) {
    updateOtp(event.target.value, index);
  }

  function handleOtpKeyDown(
    event: KeyboardEvent<HTMLInputElement>,
    index: number
  ) {
    if (event.key === "Backspace") {
      event.preventDefault();

      if (code[index]) {
        const next = code.split("");
        next[index] = "";
        setCode(next.join("").slice(0, OTP_LENGTH));
      } else if (index > 0) {
        const next = code.split("");
        next[index - 1] = "";
        setCode(next.join("").slice(0, OTP_LENGTH));
        focusOtp(index - 1);
      }

      setError("");
    }

    if (event.key === "ArrowLeft" && index > 0) {
      event.preventDefault();
      focusOtp(index - 1);
    }

    if (event.key === "ArrowRight" && index < OTP_LENGTH - 1) {
      event.preventDefault();
      focusOtp(index + 1);
    }
  }

  function handleOtpPaste(event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault();

    const pasted = event.clipboardData
      .getData("text")
      .replace(/\D/g, "")
      .slice(0, OTP_LENGTH);

    if (!pasted) return;

    setCode(pasted);
    setError("");
    focusOtp(Math.min(pasted.length, OTP_LENGTH - 1));
  }

  async function requestCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");

    const cleanName = name.trim();
    const cleanEmail = email.trim().toLowerCase();

    if (cleanName.length < 2 || cleanName.length > 60) {
      setError("Name must contain between 2 and 60 characters.");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      setError("Please enter a valid email address.");
      return;
    }

    if (!privacyAccepted) {
      setError("Please acknowledge the Privacy Policy before continuing.");
      return;
    }

    setLoading(true);

    try {
      const response = await startRegistration({
        name: cleanName,
        email: cleanEmail,
      });

      setName(cleanName);
      setEmail(cleanEmail);
      setCode("");
      setStage("verify");
      setResendCooldown(response.resendCooldown ?? 60);
      setMessage(
        response.message || "Verification code sent. Please check your email."
      );

      setTimeout(() => {
        otpRefs.current[0]?.focus();
      }, 100);
    } catch (err) {
      const retryAfter = (
        err as Error & { retryAfterSeconds?: number }
      )?.retryAfterSeconds;

      if (retryAfter && retryAfter > 0) {
        setResendCooldown(retryAfter);
      }

      setError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setLoading(false);
    }
  }

  async function verifyCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");

    if (!/^\d{6}$/.test(code)) {
      setError("Please enter the complete six-digit verification code.");
      focusOtp(code.length);
      return;
    }

    setLoading(true);

    try {
      await verifyRegistrationCode({
        email: email.trim().toLowerCase(),
        code,
      });

      router.push("/create-password");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to verify your email."
      );
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    if (resending || resendCooldown > 0 || loading) return;

    setError("");
    setMessage("");
    setResending(true);

    try {
      const cleanEmail = email.trim().toLowerCase();
      const response = await resendVerificationCode({ email: cleanEmail });

      setCode("");
      setResendCooldown(response.resendCooldown ?? 60);
      setMessage(response.message || "A new verification code has been sent.");
      focusOtp(0);
    } catch (err) {
      const retryAfter = (
        err as Error & { retryAfterSeconds?: number }
      )?.retryAfterSeconds;

      if (retryAfter && retryAfter > 0) {
        setResendCooldown(retryAfter);
      }

      setError(err instanceof Error ? err.message : "Unable to resend code.");
    } finally {
      setResending(false);
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
            Guided onboarding
          </div>
        </header>

        {/* Card shell */}
        <div className="grid w-full overflow-hidden rounded-2xl border border-[#303640]/10 bg-white shadow-[0_18px_48px_rgba(48,54,64,0.06)] lg:grid-cols-[1fr_0.95fr]">
          {/* Left luxury info panel */}
          <section className="flex flex-col justify-between border-b border-[#303640]/[0.08] bg-[#F1EFE9] p-7 sm:p-10 lg:border-b-0 lg:border-r">
            <div>
              <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-white text-[#9F7C38] shadow-2xs">
                <Wifi size={24} />
              </div>

              <span className="inline-block text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
                Your network, understood
              </span>

              <h1 className="mt-3 text-3xl font-semibold leading-tight tracking-tight text-[#303640] sm:text-[34px]">
                A clearer view
                <br />
                of your <span className="text-[#9F7C38]">connection.</span>
              </h1>

              <p className="mt-4 max-w-md text-[13px] leading-relaxed text-[#5E6572]">
                Create your verified WiFiVision workspace to map signal
                coverage, inspect wireless performance trends, and sync your
                Android companion scanner.
              </p>

              <div className="my-7 h-px w-16 bg-[#C6A15B]/60" />

              <div className="flex items-center gap-3.5 rounded-xl border border-[#303640]/[0.08] bg-white p-4 shadow-2xs">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[#C6A15B]/15 text-[#9F7C38]">
                  <ShieldCheck size={19} />
                </div>

                <div>
                  <strong className="block text-xs font-semibold text-[#303640]">
                    Three-step verified setup
                  </strong>
                  <span className="text-[11px] text-[#5E6572]">
                    Details, 6-digit email OTP, then password creation.
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-8 flex items-center justify-between border-t border-[#303640]/[0.08] pt-5 text-[10px] font-semibold tracking-[0.14em] text-[#868D98]">
              <span>BUILT FOR BETTER CONNECTIVITY</span>
              <span>EST. 2026</span>
            </div>
          </section>

          {/* Right form column */}
          <section className="p-7 sm:p-10">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/30 bg-[#C6A15B]/10 px-3 py-1 text-[10px] font-bold tracking-[0.14em] text-[#9F7C38]">
              <span>{stage === "details" ? "01" : "02"}</span>
              <span>
                {stage === "details"
                  ? "CREATE YOUR ACCOUNT"
                  : "EMAIL VERIFICATION"}
              </span>
            </div>

            <div className="mt-5 flex h-11 w-11 items-center justify-center rounded-xl border border-[#303640]/10 bg-[#F7F6F2] text-[#303640]">
              {stage === "details" ? <User size={20} /> : <Mail size={20} />}
            </div>

            <h2 className="mt-4 text-2xl font-semibold tracking-tight text-[#303640]">
              {stage === "details" ? (
                <>
                  Create your <span className="text-[#9F7C38]">account.</span>
                </>
              ) : (
                <>
                  Verify your <span className="text-[#9F7C38]">email.</span>
                </>
              )}
            </h2>

            <p className="mt-1.5 text-xs leading-relaxed text-[#5E6572]">
              {stage === "details"
                ? "Start with your full name and email address. We will send a six-digit verification code."
                : "Enter the six-digit code sent to your email address to unlock password setup."}
            </p>

            {stage === "details" ? (
              <form onSubmit={requestCode} className="mt-6 space-y-4">
                <div>
                  <label
                    htmlFor="name"
                    className="mb-1.5 block text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]"
                  >
                    Full name
                  </label>
                  <div className="relative flex items-center">
                    <User
                      size={16}
                      className="pointer-events-none absolute left-3.5 text-[#868D98]"
                    />
                    <input
                      id="name"
                      type="text"
                      autoComplete="name"
                      placeholder="Enter your full name"
                      value={name}
                      onChange={(event) => {
                        setName(event.target.value);
                        setError("");
                      }}
                      minLength={2}
                      maxLength={60}
                      required
                      className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-4 text-sm text-[#303640] placeholder-[#868D98] outline-none transition focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                    />
                  </div>
                </div>

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
                      autoComplete="email"
                      placeholder="you@example.com"
                      value={email}
                      onChange={(event) => {
                        setEmail(event.target.value);
                        setError("");
                      }}
                      required
                      className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-4 text-sm text-[#303640] placeholder-[#868D98] outline-none transition focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                    />
                  </div>
                </div>

                <div className="rounded-xl border border-[#303640]/12 bg-[#F7F6F2]/70 px-3.5 py-3">
                  <label className="flex cursor-pointer items-start gap-2.5 text-xs leading-relaxed text-[#5E6572]">
                    <input
                      id="privacy-acknowledgement"
                      type="checkbox"
                      checked={privacyAccepted}
                      onChange={(event) => {
                        setPrivacyAccepted(event.target.checked);
                        setError("");
                      }}
                      className="mt-0.5 h-4 w-4 shrink-0 rounded border-[#303640]/30 accent-[#303640]"
                    />
                    <span>
                      I have read and acknowledge the{" "}
                      <Link
                        href="/privacy-policy"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-semibold text-[#9F7C38] hover:underline"
                      >
                        WiFiVision Privacy Policy
                      </Link>
                      .
                    </span>
                  </label>
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
                  disabled={loading || !privacyAccepted}
                  className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-sm transition hover:bg-[#3E4654] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {loading ? (
                    <>
                      <Loader2
                        size={16}
                        className="animate-spin text-[#C6A15B]"
                      />
                      Sending code...
                    </>
                  ) : (
                    <>
                      Continue
                      <ArrowRight size={16} className="text-[#C6A15B]" />
                    </>
                  )}
                </button>
              </form>
            ) : (
              <form onSubmit={verifyCode} className="mt-6 space-y-4">
                <div className="flex items-center gap-3 rounded-xl border border-[#303640]/10 bg-[#F1EFE9] px-3.5 py-3">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#C6A15B]/35 bg-white text-[#9F7C38]">
                    <User size={16} />
                  </div>

                  <div className="min-w-0 flex-1">
                    <strong className="block truncate text-xs font-semibold text-[#303640]">
                      {name}
                    </strong>
                    <span className="block truncate text-[11px] text-[#5E6572]">
                      {email}
                    </span>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setStage("details");
                      setCode("");
                      setError("");
                      setMessage("");
                    }}
                    aria-label="Edit account details"
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[#303640]/12 bg-white text-[#303640] transition hover:border-[#C6A15B]"
                  >
                    <ArrowLeft size={15} />
                  </button>
                </div>

                <div>
                  <div className="mb-2 flex items-center justify-between">
                    <label className="text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]">
                      Verification code
                    </label>
                    <span className="text-[10px] font-semibold text-[#9F7C38]">
                      {code.length}/6 digits
                    </span>
                  </div>

                  <div
                    className="grid grid-cols-6 gap-2 sm:gap-2.5"
                    dir="ltr"
                  >
                    {otpDigits.map((digit, index) => (
                      <input
                        key={index}
                        ref={(element) => {
                          otpRefs.current[index] = element;
                        }}
                        type="text"
                        inputMode="numeric"
                        autoComplete={
                          index === 0 ? "one-time-code" : "off"
                        }
                        pattern="[0-9]*"
                        maxLength={1}
                        aria-label={`Verification digit ${index + 1}`}
                        value={digit}
                        onChange={(event) => handleOtpChange(event, index)}
                        onKeyDown={(event) => handleOtpKeyDown(event, index)}
                        onPaste={handleOtpPaste}
                        onFocus={(event) => event.target.select()}
                        required
                        className={`h-12 w-full rounded-xl border text-center text-lg font-bold outline-none transition ${
                          digit
                            ? "border-[#C6A15B] bg-white text-[#303640] ring-2 ring-[#C6A15B]/15"
                            : "border-[#303640]/15 bg-[#F7F6F2]/70 text-[#303640] focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                        }`}
                      />
                    ))}
                  </div>

                  <p className="mt-2 text-[11px] text-[#868D98]">
                    Enter or paste the six-digit code from your email.
                  </p>
                </div>

                {error && (
                  <div
                    role="alert"
                    className="rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] px-3.5 py-2.5 text-xs font-medium text-[#B83B3B]"
                  >
                    {error}
                  </div>
                )}

                {message && (
                  <div
                    role="status"
                    className="flex items-center gap-2 rounded-xl border border-[#2E7D5B]/30 bg-[#2E7D5B]/[0.08] px-3.5 py-2.5 text-xs font-medium text-[#2E7D5B]"
                  >
                    <CheckCircle2 size={15} className="shrink-0" />
                    <span>{message}</span>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading || code.length !== OTP_LENGTH}
                  className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-sm transition hover:bg-[#3E4654] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {loading ? (
                    <>
                      <Loader2
                        size={16}
                        className="animate-spin text-[#C6A15B]"
                      />
                      Verifying...
                    </>
                  ) : (
                    <>
                      Verify email
                      <ArrowRight size={16} className="text-[#C6A15B]" />
                    </>
                  )}
                </button>

                <button
                  type="button"
                  onClick={handleResend}
                  disabled={resending || loading || resendCooldown > 0}
                  className="flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-[#303640]/12 bg-[#F7F6F2] px-4 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B] hover:bg-[#C6A15B]/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {resending ? (
                    <>
                      <Loader2
                        size={14}
                        className="animate-spin text-[#9F7C38]"
                      />
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
            )}

            <div className="mt-6 border-t border-[#303640]/10 pt-5 text-center text-xs text-[#5E6572]">
              Already have an account?{" "}
              <Link
                href="/login"
                className="font-semibold text-[#9F7C38] hover:underline"
              >
                Sign in
              </Link>
              <span className="mx-2 text-[#868D98]">·</span>
              <Link
                href={
                  email
                    ? `/verify-email?email=${encodeURIComponent(email)}`
                    : "/verify-email"
                }
                className="font-semibold text-[#5E6572] hover:text-[#303640] hover:underline"
              >
                Have a code already?
              </Link>
            </div>

            <div className="mt-4 flex items-center justify-center gap-2 text-[11px] text-[#868D98]">
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
