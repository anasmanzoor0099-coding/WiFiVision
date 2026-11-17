"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Activity,
  ArrowRight,
  Eye,
  EyeOff,
  Fingerprint,
  Loader2,
  Lock,
  Mail,
  ShieldCheck,
  User,
  Wifi,
} from "lucide-react";

import { createAccountPassword, getPasswordSetup } from "@/lib/auth";

interface AccountDetails {
  name: string;
  email: string;
}

export default function CreatePasswordPage() {
  const router = useRouter();

  const [account, setAccount] = useState<AccountDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadAccount() {
      try {
        const response = await getPasswordSetup();

        if (!response.user) {
          throw new Error("Unable to retrieve your account details.");
        }

        if (active) {
          setAccount({
            name: response.user.name,
            email: response.user.email,
          });
        }
      } catch (err) {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Your password setup session has expired."
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    loadAccount();

    return () => {
      active = false;
    };
  }, []);

  const passwordValid =
    password.length >= 8 &&
    password.length <= 72 &&
    /[A-Z]/.test(password) &&
    /[a-z]/.test(password) &&
    /[0-9]/.test(password) &&
    /[^A-Za-z0-9]/.test(password);

  const passwordsMatch =
    confirmPassword.length > 0 && password === confirmPassword;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    if (!passwordValid) {
      setError("Please follow all the password requirements.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Your passwords do not match.");
      return;
    }

    try {
      setSubmitting(true);

      await createAccountPassword({ password });

      router.replace("/login?registered=true");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to create your account. Please try again."
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
            Secure account setup
          </div>
        </header>

        {/* Main layout card */}
        <div className="grid w-full overflow-hidden rounded-2xl border border-[#303640]/10 bg-white shadow-[0_18px_48px_rgba(48,54,64,0.06)] lg:grid-cols-[1fr_0.95fr]">
          {/* Left panel */}
          <section className="flex flex-col justify-between border-b border-[#303640]/[0.08] bg-[#F1EFE9] p-7 sm:p-10 lg:border-b-0 lg:border-r">
            <div>
              <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-white text-[#9F7C38] shadow-2xs">
                <Fingerprint size={24} />
              </div>

              <span className="inline-block text-[10px] font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
                One final step
              </span>

              <h1 className="mt-3 text-3xl font-semibold leading-tight tracking-tight text-[#303640] sm:text-[34px]">
                Your account.
                <br />
                Your <span className="text-[#9F7C38]">security.</span>
              </h1>

              <p className="mt-4 max-w-md text-[13px] leading-relaxed text-[#5E6572]">
                Create a strong password to complete your WiFiVision
                registration. Your network insights and analytics dashboard are
                just ahead.
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
                    Make every connection count.
                  </span>
                </div>
              </div>
            </div>

            <div className="mt-8 flex items-center justify-between border-t border-[#303640]/[0.08] pt-5 text-[10px] font-semibold tracking-[0.14em] text-[#868D98]">
              <span>BUILT FOR BETTER CONNECTIVITY</span>
              <span>EST. 2026</span>
            </div>
          </section>

          {/* Right password form */}
          <section className="p-7 sm:p-10">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#C6A15B]/30 bg-[#C6A15B]/10 px-3 py-1 text-[10px] font-bold tracking-[0.14em] text-[#9F7C38]">
              <span>03</span>
              <span>PASSWORD SETUP</span>
            </div>

            <div className="mt-5 flex h-11 w-11 items-center justify-center rounded-xl border border-[#303640]/10 bg-[#F7F6F2] text-[#303640]">
              <Lock size={20} />
            </div>

            <h2 className="mt-4 text-2xl font-semibold tracking-tight text-[#303640]">
              Create your <span className="text-[#9F7C38]">password.</span>
            </h2>

            <p className="mt-1.5 text-xs leading-relaxed text-[#5E6572]">
              Your email has been verified. Set a strong password to finish
              creating your WiFiVision account.
            </p>

            {loading ? (
              <div className="mt-8 flex items-center justify-center gap-3 rounded-xl border border-[#303640]/10 bg-[#F7F6F2] px-4 py-6 text-xs font-medium text-[#5E6572]">
                <Loader2 size={18} className="animate-spin text-[#C6A15B]" />
                Loading your verified account...
              </div>
            ) : !account ? (
              <div
                role="alert"
                className="mt-6 rounded-xl border border-[#B83B3B]/30 bg-[#B83B3B]/[0.07] p-5 text-xs text-[#B83B3B]"
              >
                <strong className="block text-sm font-semibold">
                  Password setup unavailable
                </strong>
                <p className="mt-1 leading-relaxed text-[#B83B3B]/90">
                  {error || "Your setup session may have expired."}
                </p>

                <Link
                  href="/register"
                  className="mt-4 inline-flex items-center gap-2 rounded-xl border border-[#B83B3B]/30 bg-white px-4 py-2 text-xs font-semibold text-[#303640] transition hover:border-[#C6A15B]"
                >
                  Return to registration
                  <ArrowRight size={14} className="text-[#9F7C38]" />
                </Link>
              </div>
            ) : (
              <>
                <div className="mt-5 grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                  <div className="flex items-center gap-2.5 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3.5 py-2.5">
                    <User size={15} className="shrink-0 text-[#9F7C38]" />
                    <div className="min-w-0">
                      <span className="block text-[9px] font-bold tracking-[0.14em] text-[#868D98]">
                        ACCOUNT NAME
                      </span>
                      <strong className="block truncate text-xs font-semibold text-[#303640]">
                        {account.name}
                      </strong>
                    </div>
                  </div>

                  <div className="flex items-center gap-2.5 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3.5 py-2.5">
                    <Mail size={15} className="shrink-0 text-[#2E7D5B]" />
                    <div className="min-w-0">
                      <span className="block text-[9px] font-bold tracking-[0.14em] text-[#868D98]">
                        VERIFIED EMAIL
                      </span>
                      <strong className="block truncate text-xs font-semibold text-[#303640]">
                        {account.email}
                      </strong>
                    </div>
                  </div>
                </div>

                <form onSubmit={handleSubmit} className="mt-5 space-y-4">
                  <div>
                    <label
                      htmlFor="password"
                      className="mb-1.5 block text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]"
                    >
                      Create password
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
                        maxLength={72}
                        autoComplete="new-password"
                        placeholder="Create a strong password"
                        required
                        className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-11 text-sm text-[#303640] placeholder-[#868D98] outline-none transition focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                      />

                      <button
                        type="button"
                        onClick={() => setShowPassword((current) => !current)}
                        aria-label={
                          showPassword ? "Hide password" : "Show password"
                        }
                        className="absolute right-3 rounded-lg p-1 text-[#868D98] transition hover:text-[#303640]"
                      >
                        {showPassword ? (
                          <EyeOff size={16} />
                        ) : (
                          <Eye size={16} />
                        )}
                      </button>
                    </div>
                  </div>

                  <div>
                    <label
                      htmlFor="confirmPassword"
                      className="mb-1.5 block text-[10px] font-bold uppercase tracking-[0.15em] text-[#5E6572]"
                    >
                      Confirm password
                    </label>

                    <div className="relative flex items-center">
                      <Lock
                        size={16}
                        className="pointer-events-none absolute left-3.5 text-[#868D98]"
                      />

                      <input
                        id="confirmPassword"
                        type={showConfirmPassword ? "text" : "password"}
                        value={confirmPassword}
                        onChange={(event) => {
                          setConfirmPassword(event.target.value);
                          setError("");
                        }}
                        maxLength={72}
                        autoComplete="new-password"
                        placeholder="Enter your password again"
                        required
                        className="h-11 w-full rounded-xl border border-[#303640]/15 bg-[#F7F6F2]/60 pl-10 pr-11 text-sm text-[#303640] placeholder-[#868D98] outline-none transition focus:border-[#C6A15B] focus:bg-white focus:ring-3 focus:ring-[#C6A15B]/15"
                      />

                      <button
                        type="button"
                        onClick={() =>
                          setShowConfirmPassword((current) => !current)
                        }
                        aria-label={
                          showConfirmPassword
                            ? "Hide password"
                            : "Show password"
                        }
                        className="absolute right-3 rounded-lg p-1 text-[#868D98] transition hover:text-[#303640]"
                      >
                        {showConfirmPassword ? (
                          <EyeOff size={16} />
                        ) : (
                          <Eye size={16} />
                        )}
                      </button>
                    </div>
                  </div>

                  <div className="flex items-start gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] p-3.5">
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#C6A15B]/18 text-[#9F7C38]">
                      <ShieldCheck size={16} />
                    </div>
                    <div>
                      <strong className="block text-[10px] font-bold tracking-[0.14em] text-[#303640]">
                        PASSWORD REQUIREMENTS
                      </strong>
                      <p className="mt-0.5 text-[11px] leading-relaxed text-[#5E6572]">
                        8 to 72 characters with uppercase, lowercase, a number,
                        and a special character.
                      </p>
                    </div>
                  </div>

                  {confirmPassword && (
                    <p
                      className={`text-xs font-semibold ${
                        passwordsMatch ? "text-[#2E7D5B]" : "text-[#B83B3B]"
                      }`}
                    >
                      {passwordsMatch
                        ? "Your passwords match."
                        : "Your passwords do not match."}
                    </p>
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
                    disabled={submitting || !passwordValid || !passwordsMatch}
                    className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-[#303640] px-4 text-xs font-semibold text-white shadow-sm transition hover:bg-[#3E4654] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {submitting ? (
                      <>
                        <Loader2
                          size={16}
                          className="animate-spin text-[#C6A15B]"
                        />
                        Creating your account...
                      </>
                    ) : (
                      <>
                        Complete account setup
                        <ArrowRight size={16} className="text-[#C6A15B]" />
                      </>
                    )}
                  </button>
                </form>

                <div className="mt-5 flex items-center justify-center gap-2 text-[11px] text-[#868D98]">
                  <ShieldCheck size={14} className="text-[#2E7D5B]" />
                  Your account details are securely processed.
                </div>
              </>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}
