import type { NextConfig } from "next";

function parseOrigin(rawValue?: string): string | null {
  const raw = rawValue?.trim();
  if (!raw) return null;
  try {
    return new URL(raw).origin;
  } catch {
    return null;
  }
}

const publicApiOrigin = parseOrigin(process.env.NEXT_PUBLIC_API_URL);
const backendOrigin = process.env.BACKEND_ORIGIN?.trim().replace(/\/+$/, "");

const connectSources = [
  "'self'",
  "http://localhost:5000",
  "http://127.0.0.1:5000",
  "http://127.0.0.1:48271",
  "http://localhost:48271",
  ...(publicApiOrigin ? [publicApiOrigin] : []),
  "https:",
].join(" ");

const contentSecurityPolicy = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https://tile.openstreetmap.org https://*.tile.openstreetmap.org",
  "font-src 'self' data:",
  `connect-src ${connectSources}`,
  "frame-ancestors 'none'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

const securityHeaders = [
  {
    key: "Content-Security-Policy",
    value: contentSecurityPolicy,
  },
  {
    key: "X-Content-Type-Options",
    value: "nosniff",
  },
  {
    key: "X-Frame-Options",
    value: "DENY",
  },
  {
    key: "Referrer-Policy",
    value: "strict-origin-when-cross-origin",
  },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(self)",
  },
  {
    key: "X-DNS-Prefetch-Control",
    value: "off",
  },
  {
    key: "Strict-Transport-Security",
    value: "max-age=31536000; includeSubDomains",
  },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async headers() {
    return [
      {
        source: "/downloads/latest.json",
        headers: [
          {
            key: "Content-Type",
            value: "application/json; charset=utf-8",
          },
          {
            key: "Cache-Control",
            value: "no-cache, no-store, must-revalidate",
          },
        ],
      },
      {
        source: "/downloads/:path*.apk",
        headers: [
          {
            key: "Content-Type",
            value: "application/vnd.android.package-archive",
          },
          {
            key: "Cache-Control",
            value: "public, max-age=3600",
          },
        ],
      },
      {
        source: "/downloads/wifivision-1.1.4.apk",
        headers: [
          {
            key: "Content-Disposition",
            value: 'attachment; filename="wifivision-1.1.4.apk"',
          },
        ],
      },
      {
        source: "/downloads/wifivision-1.1.3.apk",
        headers: [
          {
            key: "Content-Disposition",
            value: 'attachment; filename="wifivision-1.1.3.apk"',
          },
        ],
      },
      {
        source: "/downloads/wifivision-1.1.2.apk",
        headers: [
          {
            key: "Content-Disposition",
            value: 'attachment; filename="wifivision-1.1.2.apk"',
          },
        ],
      },
      {
        source: "/downloads/wifivision-1.1.1.apk",
        headers: [
          {
            key: "Content-Disposition",
            value: 'attachment; filename="wifivision-1.1.1.apk"',
          },
        ],
      },
      {
        source: "/downloads/wifivision-1.1.0.apk",
        headers: [
          {
            key: "Content-Disposition",
            value: 'attachment; filename="wifivision-1.1.0.apk"',
          },
        ],
      },
      {
        source: "/(.*)",
        headers: securityHeaders,
      },
    ];
  },
  async rewrites() {
    if (!backendOrigin) {
      return [];
    }

    return [
      {
        source: "/api/:path*",
        destination: `${backendOrigin}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
