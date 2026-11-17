"use client";

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, ShieldCheck, Wifi, X } from "lucide-react";

const PUBLIC_NAV_LINKS = [
  { href: "/about", label: "About" },
  { href: "/faq", label: "FAQs" },
  { href: "/privacy-policy", label: "Privacy Policy" },
] as const;

interface PillRect {
  left: number;
  width: number;
  visible: boolean;
}

export function PublicHeader() {
  const pathname = usePathname();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [hoveredHref, setHoveredHref] = useState<string | null>(null);
  const [pillRect, setPillRect] = useState<PillRect>({
    left: 0,
    width: 0,
    visible: false,
  });

  const navRef = useRef<HTMLElement | null>(null);
  const itemRefs = useRef<Record<string, HTMLAnchorElement | null>>({});

  const targetHref = hoveredHref ?? pathname;

  const measurePill = useCallback((href: string) => {
    const el = itemRefs.current[href];
    const navEl = navRef.current;
    if (!el || !navEl) {
      setPillRect((prev) =>
        prev.visible ? { ...prev, visible: false } : prev
      );
      return;
    }

    setPillRect({
      left: el.offsetLeft,
      width: el.offsetWidth,
      visible: true,
    });
  }, []);

  useLayoutEffect(() => {
    const raf = window.requestAnimationFrame(() => measurePill(targetHref));
    return () => window.cancelAnimationFrame(raf);
  }, [targetHref, measurePill]);

  useEffect(() => {
    function handleResize() {
      measurePill(targetHref);
    }

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [targetHref, measurePill]);

  useEffect(() => {
    if (!mobileMenuOpen) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobileMenuOpen(false);
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [mobileMenuOpen]);

  return (
    <header
      data-public-header
      className="sticky top-0 z-40 border-b border-[#303640]/[0.09] bg-[#F7F6F2]/90 backdrop-blur-md"
    >
      <div className="mx-auto flex h-[70px] w-full max-w-[1200px] items-center justify-between gap-4 px-4 sm:px-6">
        <Link
          href="/about"
          className="group inline-flex min-w-0 items-center gap-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B]"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#303640] shadow-xs transition-transform duration-200 group-hover:scale-[1.03]">
            <Wifi size={19} className="text-[#C6A15B]" />
          </div>
          <div className="min-w-0">
            <span className="block truncate text-[18px] font-bold leading-5 tracking-tight text-[#303640]">
              WiFi<span className="text-[#9F7C38]">Vision</span>
            </span>
            <span className="mt-0.5 block truncate text-[9px] font-bold tracking-[0.18em] text-[#868D98]">
              NETWORK INTELLIGENCE
            </span>
          </div>
        </Link>

        {/* Liquid Morphing Pill Navigation */}
        <nav
          ref={navRef}
          aria-label="Public navigation"
          onMouseLeave={() => setHoveredHref(null)}
          className="relative hidden items-center gap-1 rounded-2xl border border-[#303640]/[0.08] bg-white/85 p-1 shadow-[0_2px_10px_rgba(48,54,64,0.03)] md:flex"
        >
          <span
            aria-hidden="true"
            data-liquid-pill
            style={{
              transform: `translate3d(${pillRect.left - 4}px, 0, 0)`,
              width: `${pillRect.width}px`,
              opacity: pillRect.visible ? 1 : 0,
              background:
                "linear-gradient(135deg, rgba(198,161,91,0.22) 0%, rgba(241,239,233,0.95) 55%, rgba(198,161,91,0.28) 100%)",
              transition:
                "transform 380ms cubic-bezier(0.22, 1.24, 0.36, 1), width 320ms cubic-bezier(0.22, 1.24, 0.36, 1), opacity 180ms ease",
            }}
            className="pointer-events-none absolute bottom-1 top-1 left-1 rounded-xl border border-[#C6A15B]/45 shadow-[0_3px_12px_rgba(159,124,56,0.14)]"
          />

          {PUBLIC_NAV_LINKS.map((item) => {
            const isTarget = targetHref === item.href;
            const isSelected = pathname === item.href;

            return (
              <Link
                key={item.href}
                ref={(el) => {
                  itemRefs.current[item.href] = el;
                }}
                href={item.href}
                aria-current={isSelected ? "page" : undefined}
                onMouseEnter={() => setHoveredHref(item.href)}
                onFocus={() => setHoveredHref(item.href)}
                onBlur={() => setHoveredHref(null)}
                className={`relative z-10 inline-flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-semibold transition-[color,transform] duration-200 active:scale-[0.96] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B] ${
                  isTarget
                    ? "text-[#303640]"
                    : "text-[#5E6572] hover:text-[#303640]"
                }`}
              >
                {isSelected && (
                  <span
                    aria-hidden="true"
                    className="h-1.5 w-1.5 rounded-full bg-[#9F7C38] transition-transform duration-300"
                  />
                )}
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="hidden items-center gap-2.5 md:flex">
          <Link
            href="/login"
            className="wv-btn-secondary inline-flex h-9 items-center justify-center rounded-xl border border-[#303640]/14 bg-white px-4 text-xs font-semibold text-[#303640]"
          >
            Sign in
          </Link>
          <Link
            href="/register"
            className="wv-btn-primary inline-flex h-9 items-center justify-center rounded-xl border border-[#303640] bg-[#303640] px-4 text-xs font-semibold text-white shadow-xs"
          >
            Create account
          </Link>
        </div>

        <button
          type="button"
          onClick={() => setMobileMenuOpen((open) => !open)}
          aria-expanded={mobileMenuOpen}
          aria-controls="public-mobile-nav"
          aria-label={mobileMenuOpen ? "Close menu" : "Open menu"}
          className="wv-btn-pill inline-flex h-10 w-10 items-center justify-center rounded-xl border border-[#303640]/12 bg-white text-[#303640] hover:border-[#C6A15B] md:hidden"
        >
          {mobileMenuOpen ? <X size={18} /> : <Menu size={18} />}
        </button>
      </div>

      {mobileMenuOpen && (
        <div
          id="public-mobile-nav"
          className="border-t border-[#303640]/[0.08] bg-white px-4 py-4 shadow-md md:hidden"
        >
          <nav aria-label="Mobile public navigation" className="space-y-1">
            {PUBLIC_NAV_LINKS.map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`wv-btn-pill flex items-center justify-between rounded-xl px-3.5 py-2.5 text-xs font-semibold ${
                    active
                      ? "border border-[#C6A15B]/45 bg-gradient-to-r from-[#C6A15B]/20 to-[#F1EFE9] text-[#303640]"
                      : "text-[#303640] hover:bg-[#F1EFE9]"
                  }`}
                >
                  <span>{item.label}</span>
                  {active && (
                    <span className="h-1.5 w-1.5 rounded-full bg-[#9F7C38]" />
                  )}
                </Link>
              );
            })}
          </nav>

          <div className="mt-3 grid grid-cols-2 gap-2 border-t border-[#303640]/[0.08] pt-3">
            <Link
              href="/login"
              onClick={() => setMobileMenuOpen(false)}
              className="wv-btn-secondary inline-flex h-10 items-center justify-center rounded-xl border border-[#303640]/14 bg-[#F7F6F2] text-xs font-semibold text-[#303640]"
            >
              Sign in
            </Link>
            <Link
              href="/register"
              onClick={() => setMobileMenuOpen(false)}
              className="wv-btn-primary inline-flex h-10 items-center justify-center rounded-xl border border-[#303640] bg-[#303640] text-xs font-semibold text-white"
            >
              Create account
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}

export function PublicFooter() {
  return (
    <footer
      data-public-footer
      className="border-t border-[#303640]/[0.09] bg-white"
    >
      <div className="mx-auto w-full max-w-[1200px] px-4 py-10 sm:px-6">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-[1.4fr_1fr_1fr]">
          <div>
            <Link href="/about" className="inline-flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#303640]">
                <Wifi size={16} className="text-[#C6A15B]" />
              </div>
              <span className="text-base font-bold tracking-tight text-[#303640]">
                WiFi<span className="text-[#9F7C38]">Vision</span>
              </span>
            </Link>
            <p className="mt-3 max-w-sm text-xs leading-relaxed text-[#5E6572]">
              Wireless network intelligence, geographic signal heatmaps, and
              performance measurement analytics built around transparent device
              observations.
            </p>
            <p className="mt-3 inline-flex items-center gap-1.5 text-[11px] font-medium text-[#2E7D5B]">
              <ShieldCheck size={14} />
              Local-first Android scanning · Explicit user uploads
            </p>
          </div>

          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-[#868D98]">
              Documentation &amp; Legal
            </p>
            <ul className="mt-3 space-y-2 text-xs text-[#5E6572]">
              <li>
                <Link
                  href="/about"
                  className="transition hover:text-[#9F7C38]"
                >
                  About WiFiVision
                </Link>
              </li>
              <li>
                <Link href="/faq" className="transition hover:text-[#9F7C38]">
                  Frequently Asked Questions
                </Link>
              </li>
              <li>
                <Link
                  href="/privacy-policy"
                  className="transition hover:text-[#9F7C38]"
                >
                  Privacy Policy
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-[#868D98]">
              Account Access
            </p>
            <ul className="mt-3 space-y-2 text-xs text-[#5E6572]">
              <li>
                <Link
                  href="/login"
                  className="transition hover:text-[#9F7C38]"
                >
                  Sign in (Email &amp; Password)
                </Link>
              </li>
              <li>
                <Link
                  href="/register"
                  className="transition hover:text-[#9F7C38]"
                >
                  Create Account
                </Link>
              </li>
              <li>
                <Link
                  href="/verify-email"
                  className="transition hover:text-[#9F7C38]"
                >
                  Verify Email Code
                </Link>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-8 flex flex-col justify-between gap-3 border-t border-[#303640]/[0.08] pt-6 text-[11px] text-[#868D98] sm:flex-row sm:items-center">
          <p>
            WiFiVision Platform · Observations reflect scanning device location
            only.
          </p>
          <p>Email &amp; password onboarding active in this release.</p>
        </div>
      </div>
    </footer>
  );
}

export function PublicAuthLinksBar() {
  return (
    <div className="mt-4 flex flex-wrap items-center justify-center gap-x-3 gap-y-1.5 text-[11px] font-medium text-[#5E6572]">
      <Link href="/about" className="transition hover:text-[#9F7C38] hover:underline">
        About
      </Link>
      <span aria-hidden="true" className="text-[#868D98]">
        ·
      </span>
      <Link href="/faq" className="transition hover:text-[#9F7C38] hover:underline">
        FAQs
      </Link>
      <span aria-hidden="true" className="text-[#868D98]">
        ·
      </span>
      <Link
        href="/privacy-policy"
        className="transition hover:text-[#9F7C38] hover:underline"
      >
        Privacy Policy
      </Link>
    </div>
  );
}

export function PublicShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-[#F7F6F2] text-[#303640]">
      <PublicHeader />
      <main className="flex-1">{children}</main>
      <PublicFooter />
    </div>
  );
}
