"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import Sidebar from "./Sidebar";
import Topbar from "./Topbar";
import { getCurrentUser, logoutUser, type AuthUser } from "@/lib/auth";

interface DashboardShellProps {
  children: React.ReactNode;
}

type AuthStatus = "checking" | "authenticated" | "unauthenticated";

export default function DashboardShell({ children }: DashboardShellProps) {
  const router = useRouter();

  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [authStatus, setAuthStatus] = useState<AuthStatus>("checking");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function verifySession() {
      try {
        const response = await getCurrentUser();

        if (!isMounted) return;

        if (response.user) {
          setUser(response.user);
          setAuthStatus("authenticated");
        } else {
          setUser(null);
          setAuthStatus("unauthenticated");
          router.replace("/login");
        }
      } catch {
        if (!isMounted) return;

        setUser(null);
        setAuthStatus("unauthenticated");
        router.replace("/login");
      }
    }

    verifySession();

    return () => {
      isMounted = false;
    };
  }, [router]);

  useEffect(() => {
    function handleResize() {
      if (window.innerWidth >= 1024) {
        setMobileOpen(false);
      }
    }

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  function handleMenuClick() {
    if (window.innerWidth < 1024) {
      setMobileOpen((current) => !current);
    } else {
      setSidebarCollapsed((current) => !current);
    }
  }

  const handleSignOut = useCallback(async () => {
    if (signingOut) return;

    setSigningOut(true);
    try {
      await logoutUser();
    } catch {
      // Proceed to login even if cookie already expired
    } finally {
      setUser(null);
      setAuthStatus("unauthenticated");
      setSigningOut(false);
      router.replace("/login");
    }
  }, [router, signingOut]);

  if (authStatus !== "authenticated") {
    return (
      <div className="wv-dashboard flex min-h-screen items-center justify-center bg-[#F7F6F2] px-5">
        <div className="w-full max-w-sm rounded-2xl border border-[#303640]/10 bg-white p-8 text-center shadow-[0_12px_32px_rgba(48,54,64,0.06)]">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl border border-[#C6A15B]/30 bg-[#C6A15B]/10">
            <div className="h-5 w-5 animate-spin rounded-full border-[2.5px] border-[#C6A15B]/25 border-t-[#C6A15B]" />
          </div>

          <h2 className="mt-5 text-xs font-bold uppercase tracking-[0.18em] text-[#9F7C38]">
            Verifying Your Session
          </h2>

          <p className="mt-2 text-xs leading-relaxed text-[#5E6572]">
            Please wait while we securely verify your account.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="wv-dashboard relative flex min-h-screen w-full overflow-x-clip bg-[#F7F6F2] text-[#303640]">
      <Sidebar
        open={mobileOpen}
        collapsed={sidebarCollapsed}
        onClose={() => setMobileOpen(false)}
        user={user}
        onSignOut={handleSignOut}
        signingOut={signingOut}
      />

      <div className="relative z-10 flex min-h-screen min-w-0 flex-1 flex-col">
        <Topbar
          onMenuClick={handleMenuClick}
          sidebarCollapsed={sidebarCollapsed}
          user={user}
          onSignOut={handleSignOut}
          signingOut={signingOut}
        />

        <main className="page-content mx-auto w-full max-w-[1600px] min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          {children}
        </main>
      </div>
    </div>
  );
}
