export const WINDOWS_SCANNING_NOTICE_SESSION_KEY =
  "wifivision_windows_scanning_notice_dismissed_v1";

interface NavigatorUADataLike {
  mobile?: boolean;
  platform?: string;
}

export interface NavigatorLike {
  userAgent?: string;
  platform?: string;
  userAgentData?: NavigatorUADataLike;
}

const NON_WINDOWS_OR_MOBILE_UA_REGEX =
  /android|iphone|ipad|ipod|ios|mobile|windows phone|iemobile|wpdesktop|blackberry|bb10|opera mini|webos|silk|kindle|tablet/i;

const MOBILE_PLATFORM_REGEX = /android|iphone|ipad|ipod/i;
const WINDOWS_PLATFORM_REGEX = /^(win32|win64|windows|wow64)/i;
const WINDOWS_DESKTOP_UA_REGEX = /\bwindows nt\b|\bwin64\b|\bwow64\b/i;

let memoryDismissedForSession = false;

/**
 * Safely detects Windows desktop/laptop browsers during client-side execution.
 * Returns false during SSR and explicitly excludes Android, iOS, and mobile browsers.
 */
export function isWindowsDesktopBrowser(
  navOverride?: NavigatorLike | null
): boolean {
  const nav: NavigatorLike | undefined =
    navOverride !== undefined
      ? (navOverride ?? undefined)
      : typeof window !== "undefined" && typeof navigator !== "undefined"
        ? (navigator as unknown as NavigatorLike)
        : undefined;

  if (!nav) {
    return false;
  }

  const ua = (nav.userAgent ?? "").trim();
  const platform = (nav.platform ?? "").trim();
  const uaData = nav.userAgentData;

  if (uaData?.mobile === true) {
    return false;
  }

  if (ua && NON_WINDOWS_OR_MOBILE_UA_REGEX.test(ua)) {
    return false;
  }

  if (platform && MOBILE_PLATFORM_REGEX.test(platform)) {
    return false;
  }

  const uaDataPlatform = (uaData?.platform ?? "").trim().toLowerCase();
  if (uaDataPlatform) {
    return uaDataPlatform === "windows";
  }

  if (platform && WINDOWS_PLATFORM_REGEX.test(platform)) {
    return true;
  }

  return WINDOWS_DESKTOP_UA_REGEX.test(ua);
}

export function isNoticeDismissedForSession(): boolean {
  if (memoryDismissedForSession) {
    return true;
  }
  if (typeof window === "undefined") {
    return false;
  }
  try {
    return (
      window.sessionStorage.getItem(WINDOWS_SCANNING_NOTICE_SESSION_KEY) === "1"
    );
  } catch {
    return false;
  }
}

export function markNoticeDismissedForSession(): void {
  memoryDismissedForSession = true;
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(WINDOWS_SCANNING_NOTICE_SESSION_KEY, "1");
  } catch {
    // Fallback to in-memory flag when sessionStorage is restricted.
  }
}
