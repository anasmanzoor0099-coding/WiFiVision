"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  Activity,
  ChevronDown,
  LogOut,
  Menu,
  Settings,
  ShieldCheck,
  Wifi,
} from "lucide-react";

import type { AuthUser } from "@/lib/auth";

interface TopbarProps {
  onMenuClick: () => void;
  sidebarCollapsed?: boolean;
  user?: AuthUser | null;
  onSignOut?: () => void;
  signingOut?: boolean;
}

function getInitials(name?: string): string {
  if (!name) return "U";
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export default function Topbar({
  onMenuClick,
  sidebarCollapsed = false,
  user,
  onSignOut,
  signingOut = false,
}: TopbarProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!menuOpen) return;

    function handleClickOutside(event: MouseEvent) {
      if (
        menuContainerRef.current &&
        !menuContainerRef.current.contains(event.target as Node)
      ) {
        setMenuOpen(false);
      }
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [menuOpen]);

  return (
    <header className="sticky top-0 z-30 flex h-[72px] w-full min-w-0 items-center justify-between gap-3 border-b border-[#303640]/[0.09] bg-white/90 px-4 backdrop-blur-md sm:px-6 lg:px-8">
      {/* Left: Menu trigger & clickable branding */}
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={onMenuClick}
          aria-label="Toggle navigation"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#303640]/12 bg-[#F7F6F2] text-[#303640] transition-all duration-200 hover:border-[#C6A15B] hover:bg-[#C6A15B]/10"
        >
          <Menu size={18} />
        </button>

        <Link
          href="/"
          className={`group min-w-0 items-center gap-2.5 ${
            sidebarCollapsed ? "flex" : "flex lg:hidden"
          }`}
        >
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#303640] transition-transform duration-200 group-hover:scale-[1.03]">
            <Wifi size={17} className="text-[#C6A15B]" />
          </div>

          <div className="min-w-0">
            <p className="truncate text-[15px] font-bold tracking-tight text-[#303640]">
              WiFi<span className="text-[#9F7C38]">Vision</span>
            </p>
            <p className="hidden text-[9px] font-semibold tracking-[0.16em] text-[#868D98] sm:block">
              NETWORK INTELLIGENCE
            </p>
          </div>
        </Link>
      </div>

      {/* Right: Interactive User Profile Menu */}
      <div className="flex min-w-0 shrink-0 items-center gap-2 sm:gap-3">
        {user && (
          <div ref={menuContainerRef} className="relative">
            <button
              type="button"
              onClick={() => setMenuOpen((prev) => !prev)}
              aria-expanded={menuOpen}
              aria-haspopup="menu"
              aria-label="Open account menu"
              className="flex items-center gap-2.5 rounded-xl border border-[#303640]/[0.09] bg-[#F7F6F2] py-1.5 pl-2 pr-2.5 transition hover:border-[#C6A15B]/60 hover:bg-[#F1EFE9]"
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[#C6A15B]/35 bg-white text-[11px] font-bold text-[#9F7C38]">
                {getInitials(user.name)}
              </div>

              <div className="hidden max-w-[180px] min-w-0 text-left sm:block">
                <div className="flex items-center gap-1.5">
                  <p className="truncate text-xs font-semibold leading-4 text-[#303640]">
                    {user.name}
                  </p>
                  {user.isEmailVerified && (
                    <ShieldCheck
                      size={12}
                      className="shrink-0 text-[#2E7D5B]"
                      aria-label="Verified account"
                    />
                  )}
                </div>
                <p
                  className="truncate text-[10px] leading-3.5 text-[#5E6572]"
                  title={user.email}
                >
                  {user.email}
                </p>
              </div>

              <ChevronDown
                size={14}
                className={`text-[#5E6572] transition-transform ${
                  menuOpen ? "rotate-180" : ""
                }`}
              />
            </button>

            {menuOpen && (
              <div
                role="menu"
                className="absolute right-0 mt-2 w-64 rounded-2xl border border-[#303640]/12 bg-white p-2 shadow-[0_14px_36px_rgba(48,54,64,0.12)]"
              >
                <div className="border-b border-[#303640]/[0.08] px-3 py-2.5">
                  <p className="truncate text-xs font-semibold text-[#303640]">
                    {user.name}
                  </p>
                  <p className="mt-0.5 truncate text-[11px] text-[#5E6572]">
                    {user.email}
                  </p>
                  <p className="mt-1.5 inline-flex items-center gap-1 rounded-full border border-[#2E7D5B]/25 bg-[#2E7D5B]/10 px-2 py-0.5 text-[10px] font-medium text-[#2E7D5B]">
                    <ShieldCheck size={11} />
                    {user.isEmailVerified
                      ? "Email Verified"
                      : "Verification Pending"}
                  </p>
                </div>

                <div className="mt-1.5 space-y-1">
                  <Link
                    href="/settings"
                    role="menuitem"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium text-[#303640] transition hover:bg-[#F7F6F2]"
                  >
                    <Settings size={14} className="text-[#9F7C38]" />
                    Profile & Settings
                  </Link>

                  <Link
                    href="/monitoring"
                    role="menuitem"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium text-[#303640] transition hover:bg-[#F7F6F2]"
                  >
                    <Activity size={14} className="text-[#2E7D5B]" />
                    System Monitoring
                  </Link>
                </div>

                {onSignOut && (
                  <div className="mt-1.5 border-t border-[#303640]/[0.08] pt-1.5">
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setMenuOpen(false);
                        onSignOut();
                      }}
                      disabled={signingOut}
                      className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-[#B83B3B] transition hover:bg-[#B83B3B]/[0.07] disabled:opacity-50"
                    >
                      <LogOut size={14} />
                      {signingOut ? "Signing out..." : "Sign out"}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
}
