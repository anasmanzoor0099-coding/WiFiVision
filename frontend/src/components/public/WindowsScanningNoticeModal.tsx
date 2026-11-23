"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Laptop, Smartphone, Sparkles, X } from "lucide-react";

import {
  isNoticeDismissedForSession,
  isWindowsDesktopBrowser,
  markNoticeDismissedForSession,
} from "@/lib/platformDetection";

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

export default function WindowsScanningNoticeModal() {
  const [isOpen, setIsOpen] = useState(false);

  const hasEvaluatedRef = useRef(false);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const primaryButtonRef = useRef<HTMLButtonElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  const handleDismiss = useCallback(() => {
    markNoticeDismissedForSession();
    setIsOpen(false);
  }, []);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(() => {
      if (!active || hasEvaluatedRef.current) return;
      hasEvaluatedRef.current = true;
      if (isWindowsDesktopBrowser() && !isNoticeDismissedForSession()) {
        previousFocusRef.current =
          document.activeElement instanceof HTMLElement
            ? document.activeElement
            : null;
        setIsOpen(true);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!isOpen) return;

    const rafId = window.requestAnimationFrame(() => {
      primaryButtonRef.current?.focus();
    });

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        handleDismiss();
        return;
      }

      if (event.key !== "Tab") return;
      const panel = panelRef.current;
      if (!panel) return;

      const focusables = Array.from(
        panel.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
      ).filter((el) => el.offsetParent !== null);

      if (focusables.length === 0) return;

      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const current = document.activeElement;

      if (event.shiftKey) {
        if (current === first || !panel.contains(current)) {
          event.preventDefault();
          last.focus();
        }
      } else if (current === last || !panel.contains(current)) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(rafId);
      document.removeEventListener("keydown", handleKeyDown);
      if (previousFocusRef.current?.isConnected) {
        previousFocusRef.current.focus();
      }
      previousFocusRef.current = null;
    };
  }, [handleDismiss, isOpen]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="windows-scanning-notice-title"
      aria-describedby="windows-scanning-notice-description"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          handleDismiss();
        }
      }}
      className="wv-modal-backdrop fixed inset-0 z-[60] flex items-center justify-center bg-[#303640]/30 px-4 py-6 backdrop-blur-[3px]"
    >
      <div
        ref={panelRef}
        className="wv-modal-panel relative w-full max-w-lg overflow-hidden rounded-2xl border border-[#C6A15B]/45 bg-gradient-to-b from-white via-white to-[#F7F6F2] shadow-[0_24px_64px_rgba(48,54,64,0.16)]"
      >
        <div
          aria-hidden="true"
          className="h-1.5 w-full bg-gradient-to-r from-[#2E7D5B] via-[#1F6A4F] to-[#C6A15B]"
        />

        <div className="p-6 sm:p-7">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3.5">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-[#C6A15B]/40 bg-[#2E7D5B]/10 text-[#2E7D5B] shadow-2xs">
                <Laptop size={22} strokeWidth={1.9} />
              </div>

              <div>
                <div className="inline-flex items-center gap-1.5 rounded-full border border-[#C6A15B]/40 bg-[#FBF9F4] px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-[0.16em] text-[#9F7C38]">
                  <Sparkles size={11} className="text-[#C6A15B]" />
                  Platform Notice
                </div>

                <h2
                  id="windows-scanning-notice-title"
                  className="mt-1.5 text-lg font-semibold tracking-tight text-[#303640] sm:text-xl"
                >
                  Windows Scanning Coming Soon
                </h2>
              </div>
            </div>

            <button
              type="button"
              onClick={handleDismiss}
              aria-label="Close notice"
              className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[#303640]/10 bg-[#F7F6F2] text-[#5E6572] transition hover:border-[#C6A15B] hover:bg-[#ECE9E0] hover:text-[#303640] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B]"
            >
              <X size={16} />
            </button>
          </div>

          <div
            id="windows-scanning-notice-description"
            className="mt-4 space-y-3 text-xs leading-relaxed text-[#5E6572] sm:text-[13px]"
          >
            <p className="font-medium text-[#303640]">
              WiFiVision currently supports nearby Wi-Fi network scanning
              through its Android application.
            </p>
            <p>
              Direct Wi-Fi scanning on Windows laptops and desktop browsers is
              not available yet. We plan to introduce Windows scanning support
              in a future update.
            </p>
            <p className="rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9]/80 px-3.5 py-2.5 text-[11.5px] text-[#5E6572] sm:text-xs">
              You can continue using the website to explore your synced network
              observations, heatmaps, analytics, and other available features.
            </p>
          </div>

          <div className="mt-6 flex flex-col-reverse gap-2.5 sm:flex-row sm:items-center sm:justify-end sm:gap-3">
            <Link
              href="/download"
              onClick={handleDismiss}
              className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl border border-[#C6A15B]/55 bg-[#FBF9F4] px-4 text-xs font-semibold text-[#303640] shadow-2xs transition hover:border-[#C6A15B] hover:bg-[#F1EFE9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B] sm:w-auto"
            >
              <Smartphone size={14} className="shrink-0 text-[#9F7C38]" />
              Download Android App
            </Link>

            <button
              ref={primaryButtonRef}
              type="button"
              onClick={handleDismiss}
              className="inline-flex h-10 w-full items-center justify-center rounded-xl border border-[#C6A15B]/55 bg-gradient-to-r from-[#2E7D5B] to-[#1F6A4F] px-5 text-xs font-semibold text-white shadow-[0_6px_18px_rgba(46,125,91,0.22)] transition hover:border-[#C6A15B] hover:from-[#266B4D] hover:to-[#195941] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#C6A15B] sm:w-auto"
            >
              Got it
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
