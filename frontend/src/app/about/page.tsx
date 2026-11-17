import type { Metadata } from "next";

import { PublicShell } from "@/components/public/PublicShell";
import AboutContent from "@/components/public/AboutContent";

export const metadata: Metadata = {
  title: "About WiFiVision",
  description:
    "Learn how WiFiVision transforms real-world Wi-Fi observations, geographic heatmaps, GPS accuracy metadata, and internet performance tests into clear wireless intelligence.",
};

export default function AboutPage() {
  return (
    <PublicShell>
      <AboutContent />
    </PublicShell>
  );
}
