import type { Metadata } from "next";

import { PublicShell } from "@/components/public/PublicShell";
import FaqContent from "@/components/public/FaqContent";

export const metadata: Metadata = {
  title: "Frequently Asked Questions",
  description:
    "Explore answers about WiFiVision Wi-Fi signal strength (RSSI/dBm), frequency bands, geographic heatmaps, GPS accuracy, Android scanning, performance diagnostics, and security.",
};

export default function FaqPage() {
  return (
    <PublicShell>
      <FaqContent />
    </PublicShell>
  );
}
