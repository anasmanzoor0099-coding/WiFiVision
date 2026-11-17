import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";

import "leaflet/dist/leaflet.css";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "WiFiVision | Network Intelligence",
    template: "%s | WiFiVision",
  },
  description:
    "Monitor wireless network quality, visualize Wi-Fi coverage, and analyze internet performance with WiFiVision.",
  applicationName: "WiFiVision",
  keywords: [
    "WiFiVision",
    "WiFi monitoring",
    "Network intelligence",
    "WiFi heatmap",
    "Network analytics",
    "Internet performance",
  ],
  robots: {
    index: false,
    follow: false,
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#F7F6F2",
  colorScheme: "light",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}