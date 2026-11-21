import type { Metadata } from "next";

import { PublicShell } from "@/components/public/PublicShell";
import DownloadContent from "@/components/public/DownloadContent";

export const metadata: Metadata = {
  title: "Download WiFiVision",
  description:
    "Download the signed WiFiVision v1.1.0 Android APK with SHA-256 verification, install and update instructions, permission details, and Windows Companion setup.",
};

export default function DownloadPage() {
  return (
    <PublicShell>
      <DownloadContent />
    </PublicShell>
  );
}
