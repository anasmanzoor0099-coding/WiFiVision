"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  BarChart3,
  ChevronRight,
  Download,
  Gauge,
  LayoutDashboard,
  LogOut,
  Map,
  Radio,
  Settings,
  Wifi,
  X,
} from "lucide-react";

import type { AuthUser } from "@/lib/auth";

interface SidebarProps {
  open: boolean;
  collapsed: boolean;
  onClose: () => void;
  user?: AuthUser | null;
  onSignOut?: () => void;
  signingOut?: boolean;
}

const navigation = [
  {
    title: "WORKSPACE",
    items: [
      { label: "Network Overview", href: "/", icon: LayoutDashboard },
      { label: "Heatmap Explorer", href: "/heatmap", icon: Map },
      { label: "Network Explorer", href: "/networks", icon: Radio },
    ],
  },
  {
    title: "ANALYTICS",
    items: [
      { label: "Performance Analytics", href: "/performance", icon: Gauge },
      { label: "Network Analytics", href: "/analytics", icon: BarChart3 },
      { label: "System Monitoring", href: "/monitoring", icon: Activity },
    ],
  },
  {
    title: "PREFERENCES",
    items: [
      { label: "Download App", href: "/download", icon: Download },
      { label: "Settings", href: "/settings", icon: Settings },
    ],
  },
];

function getInitials(name?: string): string {
  if (!name) return "U";
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export default function Sidebar({
  open,
  collapsed,
  onClose,
  user,
  onSignOut,
  signingOut = false,
}: SidebarProps) {
  const pathname = usePathname();

  useEffect(() => {
    if (!open) return;

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }

    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [open, onClose]);

  return (
    <>
      {open && (
        <button
          type="button"
          aria-label="Close navigation"
          onClick={onClose}
          className="fixed inset-0 z-40 bg-[#303640]/30 backdrop-blur-[2px] lg:hidden"
        />
      )}

      <aside
        className={`
          fixed inset-y-0 left-0 z-50 flex h-screen
          w-[256px] flex-col overflow-hidden
          border-r border-[#303640]/[0.09] bg-white
          transition-all duration-300 ease-in-out
          lg:sticky lg:top-0 lg:z-30 lg:shrink-0
          ${open ? "translate-x-0" : "-translate-x-full"}
          ${
            collapsed
              ? "lg:w-0 lg:-translate-x-full lg:border-transparent"
              : "lg:w-[256px] lg:translate-x-0"
          }
        `}
      >
        {/* Brand header */}
        <div className="flex h-[72px] min-w-[256px] shrink-0 items-center justify-between border-b border-[#303640]/[0.08] px-5">
          <Link
            href="/"
            onClick={onClose}
            className="group flex min-w-0 items-center gap-3"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[#C6A15B]/35 bg-[#303640] text-[#F7F6F2] shadow-sm transition-transform duration-200 group-hover:scale-[1.03]">
              <Wifi size={19} strokeWidth={2} className="text-[#C6A15B]" />
            </div>

            <div className="flex min-w-0 flex-col justify-center">
              <span className="text-[16px] font-bold leading-5 tracking-tight text-[#303640]">
                WiFi<span className="text-[#9F7C38]">Vision</span>
              </span>
              <span className="mt-0.5 whitespace-nowrap text-[8px] font-bold leading-3 tracking-[0.18em] text-[#868D98]">
                NETWORK INTELLIGENCE
              </span>
            </div>
          </Link>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close sidebar"
            className="ml-2 shrink-0 rounded-lg p-1.5 text-[#868D98] transition hover:bg-[#F1EFE9] hover:text-[#303640] lg:hidden"
          >
            <X size={18} />
          </button>
        </div>

        {/* Navigation */}
        <nav className="min-w-[256px] flex-1 space-y-7 overflow-y-auto overflow-x-hidden px-4 py-6">
          {navigation.map((group) => (
            <div key={group.title}>
              <p className="mb-2.5 px-3 text-[9px] font-bold leading-4 tracking-[0.19em] text-[#868D98]">
                {group.title}
              </p>

              <div className="space-y-1">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  const active =
                    pathname === item.href ||
                    (item.href !== "/" && pathname.startsWith(`${item.href}/`));

                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      onClick={onClose}
                      aria-current={active ? "page" : undefined}
                      className={`group relative flex min-h-[42px] items-center gap-3 rounded-xl border px-3 py-2 text-[12.5px] transition-all duration-200 ${
                        active
                          ? "border-[#C6A15B]/35 bg-[#C6A15B]/[0.10] font-semibold text-[#303640]"
                          : "border-transparent font-medium text-[#5E6572] hover:border-[#303640]/[0.07] hover:bg-[#F7F6F2] hover:text-[#303640]"
                      }`}
                    >
                      {active && (
                        <span className="absolute bottom-2 left-0 top-2 w-[3px] rounded-r-full bg-[#C6A15B]" />
                      )}

                      <span
                        className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg transition-colors ${
                          active
                            ? "bg-[#C6A15B]/20 text-[#9F7C38]"
                            : "text-[#868D98] group-hover:bg-white group-hover:text-[#303640]"
                        }`}
                      >
                        <Icon size={16} strokeWidth={active ? 2.1 : 1.8} />
                      </span>

                      <span className="min-w-0 flex-1 truncate leading-5">
                        {item.label}
                      </span>

                      {active && (
                        <ChevronRight
                          size={14}
                          className="shrink-0 text-[#9F7C38]"
                        />
                      )}
                    </Link>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        {/* Account & status footer */}
        <div className="min-w-[256px] shrink-0 border-t border-[#303640]/[0.08] bg-[#FAF9F5] px-4 py-4">
          {user && (
            <div className="mb-3 rounded-xl border border-[#303640]/[0.09] bg-white p-3 shadow-xs">
              <Link
                href="/settings"
                onClick={onClose}
                className="group flex items-center gap-2.5 rounded-lg p-0.5 transition hover:bg-[#F7F6F2]"
                title="Open account settings"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#C6A15B]/35 bg-[#C6A15B]/15 text-xs font-bold text-[#9F7C38]">
                  {getInitials(user.name)}
                </div>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-semibold text-[#303640] group-hover:text-[#9F7C38]">
                    {user.name}
                  </p>
                  <p
                    className="truncate text-[10px] text-[#5E6572]"
                    title={user.email}
                  >
                    {user.email}
                  </p>
                </div>
              </Link>

              {onSignOut && (
                <button
                  type="button"
                  onClick={onSignOut}
                  disabled={signingOut}
                  className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg border border-[#303640]/12 bg-[#F7F6F2] px-3 py-2 text-[11px] font-semibold text-[#303640] transition hover:border-[#B83B3B]/35 hover:bg-[#B83B3B]/[0.06] hover:text-[#B83B3B] disabled:opacity-50"
                >
                  <LogOut size={13} />
                  {signingOut ? "Signing out..." : "Sign out"}
                </button>
              )}
            </div>
          )}

          <div className="flex items-center justify-between rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9]/80 px-3 py-2.5">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 shrink-0 rounded-full bg-[#2E7D5B]" />
              <span className="text-[10px] font-medium text-[#5E6572]">
                Dashboard active
              </span>
            </div>

            <span className="text-[9px] font-semibold tracking-wider text-[#9F7C38]">
              V1.1
            </span>
          </div>
        </div>
      </aside>
    </>
  );
}
