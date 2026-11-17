"use client";

import {
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

function subscribeReducedMotion(callback: () => void): () => void {
  if (typeof window === "undefined" || !window.matchMedia) {
    return () => {};
  }
  const media = window.matchMedia(REDUCED_MOTION_QUERY);
  media.addEventListener("change", callback);
  return () => media.removeEventListener("change", callback);
}

function getReducedMotionSnapshot(): boolean {
  if (typeof window === "undefined" || !window.matchMedia) {
    return false;
  }
  return window.matchMedia(REDUCED_MOTION_QUERY).matches;
}

function getReducedMotionServerSnapshot(): boolean {
  return false;
}

export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    subscribeReducedMotion,
    getReducedMotionSnapshot,
    getReducedMotionServerSnapshot
  );
}

export type RevealDirection = "bottom" | "left" | "right" | "none";

export const STAGGER_SEQUENCES: readonly (readonly RevealDirection[])[] = [
  ["bottom", "left", "right"],
  ["left", "bottom", "right"],
  ["bottom", "right", "left"],
];

export function getAlternatingDirection(
  index: number,
  sequenceIndex = 0
): RevealDirection {
  const sequence =
    STAGGER_SEQUENCES[
      ((sequenceIndex % STAGGER_SEQUENCES.length) + STAGGER_SEQUENCES.length) %
        STAGGER_SEQUENCES.length
    ];
  return sequence[index % sequence.length];
}

interface RevealProps {
  children: ReactNode;
  className?: string;
  delayMs?: number;
  direction?: RevealDirection;
  immediate?: boolean;
}

export function Reveal({
  children,
  className = "",
  delayMs = 0,
  direction = "bottom",
  immediate = false,
}: RevealProps) {
  const reducedMotion = useReducedMotion();
  const ref = useRef<HTMLDivElement | null>(null);
  const [revealed, setRevealed] = useState(immediate);

  useEffect(() => {
    if (immediate || reducedMotion) return;

    const node = ref.current;
    if (!node || typeof IntersectionObserver === "undefined") {
      const id = window.requestAnimationFrame(() => setRevealed(true));
      return () => window.cancelAnimationFrame(id);
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setRevealed(true);
          observer.disconnect();
        }
      },
      { threshold: 0.16, rootMargin: "0px 0px -48px 0px" }
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [immediate, reducedMotion]);

  const isShown = revealed || reducedMotion;

  const hiddenClass =
    direction === "left"
      ? "wv-reveal-left"
      : direction === "right"
        ? "wv-reveal-right"
        : direction === "none"
          ? "opacity-0"
          : "wv-reveal-bottom";

  return (
    <div
      ref={ref}
      data-reveal-direction={direction}
      data-revealed={isShown ? "true" : "false"}
      style={
        reducedMotion || delayMs <= 0
          ? undefined
          : { transitionDelay: `${delayMs}ms` }
      }
      className={`wv-reveal-base ${
        isShown ? "wv-reveal-visible" : hiddenClass
      } ${className}`}
    >
      {children}
    </div>
  );
}

export function ReadingProgressBar() {
  const reducedMotion = useReducedMotion();
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    let rafId = 0;

    function updateProgress() {
      rafId = 0;
      const scrollTop = window.scrollY;
      const docHeight =
        document.documentElement.scrollHeight - window.innerHeight;
      if (docHeight <= 0) {
        setProgress(0);
        return;
      }
      setProgress(Math.min(1, Math.max(0, scrollTop / docHeight)));
    }

    function onScroll() {
      if (rafId) return;
      rafId = window.requestAnimationFrame(updateProgress);
    }

    const initId = window.requestAnimationFrame(updateProgress);
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll, { passive: true });

    return () => {
      window.cancelAnimationFrame(initId);
      if (rafId) window.cancelAnimationFrame(rafId);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  return (
    <div
      data-reading-progress
      aria-hidden="true"
      className="fixed left-0 top-0 z-50 h-[3px] w-full bg-[#303640]/[0.06]"
    >
      <div
        className={`h-full origin-left bg-gradient-to-r from-[#C6A15B] via-[#9F7C38] to-[#2E7D5B] ${
          reducedMotion ? "" : "transition-transform duration-150 ease-out"
        }`}
        style={{ transform: `scaleX(${progress})` }}
      />
    </div>
  );
}

export function ExpandablePanel({
  id,
  isOpen,
  children,
  labelledBy,
}: {
  id: string;
  isOpen: boolean;
  children: ReactNode;
  labelledBy?: string;
}) {
  return (
    <div
      id={id}
      role="region"
      aria-labelledby={labelledBy}
      aria-hidden={!isOpen}
      className={`grid transition-[grid-template-rows,opacity] duration-300 ease-out ${
        isOpen ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0"
      }`}
    >
      <div className="overflow-hidden">{children}</div>
    </div>
  );
}

export function SignalNetworkVisual({
  compact = false,
}: {
  compact?: boolean;
}) {
  const reducedMotion = useReducedMotion();

  return (
    <div
      aria-hidden="true"
      className={`relative overflow-hidden rounded-2xl border border-[#303640]/10 bg-gradient-to-br from-white via-[#F7F6F2] to-[#F1EFE9] shadow-[0_18px_48px_rgba(48,54,64,0.06)] ${
        compact ? "h-44 w-full" : "h-[330px] w-full sm:h-[370px]"
      }`}
    >
      {/* Soft champagne gold radial depth */}
      <div
        className="pointer-events-none absolute -right-12 -top-12 h-56 w-56 rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(198,161,91,0.22) 0%, rgba(198,161,91,0.05) 55%, transparent 75%)",
        }}
      />
      <div
        className="pointer-events-none absolute -bottom-16 -left-10 h-52 w-52 rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(46,125,91,0.12) 0%, rgba(46,125,91,0.03) 55%, transparent 75%)",
        }}
      />

      {/* SVG Network Topology & Animated Signal Waves */}
      <svg
        viewBox="0 0 480 320"
        className="h-full w-full"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <pattern
            id="wv-grid"
            width="32"
            height="32"
            patternUnits="userSpaceOnUse"
          >
            <path
              d="M 32 0 L 0 0 0 32"
              stroke="rgba(48,54,64,0.045)"
              strokeWidth="1"
            />
          </pattern>
        </defs>

        <rect width="480" height="320" fill="url(#wv-grid)" />

        {/* Connection lines */}
        <path
          d="M110 100 L240 160 L370 92"
          stroke="#C6A15B"
          strokeOpacity="0.48"
          strokeWidth="1.6"
          strokeDasharray="6 8"
          className={reducedMotion ? "" : "wv-anim-dash"}
        />
        <path
          d="M126 236 L240 160 L364 232"
          stroke="#303640"
          strokeOpacity="0.22"
          strokeWidth="1.4"
          strokeDasharray="5 9"
          className={reducedMotion ? "" : "wv-anim-dash"}
        />
        <path
          d="M240 160 L240 52"
          stroke="#2E7D5B"
          strokeOpacity="0.35"
          strokeWidth="1.5"
          strokeDasharray="4 6"
          className={reducedMotion ? "" : "wv-anim-dash"}
        />

        {/* Central signal wave rings */}
        <circle
          cx="240"
          cy="160"
          r="56"
          stroke="#C6A15B"
          strokeOpacity="0.3"
          strokeWidth="1.25"
          className={reducedMotion ? "" : "origin-[240px_160px] wv-anim-pulse-ring"}
        />
        <circle
          cx="240"
          cy="160"
          r="92"
          stroke="#C6A15B"
          strokeOpacity="0.18"
          strokeWidth="1"
        />
        <circle
          cx="240"
          cy="160"
          r="128"
          stroke="#303640"
          strokeOpacity="0.08"
          strokeWidth="1"
        />

        {/* Center core node */}
        <circle
          cx="240"
          cy="160"
          r="24"
          fill="#303640"
          stroke="#C6A15B"
          strokeWidth="2.5"
        />
        <path
          d="M229 156C235.5 150.5 244.5 150.5 251 156M233 161.5C237 158 243 158 247 161.5M240 167.5H240.02"
          stroke="#C6A15B"
          strokeWidth="2.2"
          strokeLinecap="round"
        />

        {/* Floating perimeter telemetry nodes */}
        <g className={reducedMotion ? "" : "wv-anim-float"}>
          <circle
            cx="110"
            cy="100"
            r="14"
            fill="#FFFFFF"
            stroke="#2E7D5B"
            strokeWidth="2"
          />
          <circle cx="110" cy="100" r="4.5" fill="#2E7D5B" />
        </g>

        <g className={reducedMotion ? "" : "wv-anim-float-slow"}>
          <circle
            cx="370"
            cy="92"
            r="15"
            fill="#FFFFFF"
            stroke="#C6A15B"
            strokeWidth="2"
          />
          <circle cx="370" cy="92" r="5" fill="#9F7C38" />
        </g>

        <g className={reducedMotion ? "" : "wv-anim-float-slow"}>
          <circle
            cx="126"
            cy="236"
            r="13"
            fill="#FFFFFF"
            stroke="#6366F1"
            strokeWidth="2"
          />
          <circle cx="126" cy="236" r="4.5" fill="#6366F1" />
        </g>

        <g className={reducedMotion ? "" : "wv-anim-float"}>
          <circle
            cx="364"
            cy="232"
            r="14"
            fill="#FFFFFF"
            stroke="#303640"
            strokeWidth="2"
          />
          <circle cx="364" cy="232" r="4.5" fill="#303640" />
        </g>

        <circle
          cx="240"
          cy="52"
          r="10"
          fill="#FFFFFF"
          stroke="#2E7D5B"
          strokeWidth="2"
        />
        <circle cx="240" cy="52" r="3.5" fill="#2E7D5B" />
      </svg>

      {!compact && (
        <>
          <div className="absolute left-4 top-4 rounded-xl border border-[#303640]/10 bg-white/95 px-3 py-2 shadow-sm backdrop-blur-xs">
            <p className="text-[9px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
              Signal telemetry
            </p>
            <p className="mt-0.5 text-xs font-semibold text-[#2E7D5B]">
              2.4 / 5 / 6 GHz
            </p>
          </div>

          <div className="absolute bottom-4 right-4 rounded-xl border border-[#303640]/10 bg-white/95 px-3 py-2 shadow-sm backdrop-blur-xs">
            <p className="text-[9px] font-bold uppercase tracking-[0.14em] text-[#868D98]">
              GPS coordinates
            </p>
            <p className="mt-0.5 text-xs font-semibold text-[#9F7C38]">
              Device scan location
            </p>
          </div>
        </>
      )}
    </div>
  );
}
