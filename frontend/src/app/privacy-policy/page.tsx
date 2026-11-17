import type { Metadata } from "next";

import { PublicShell } from "@/components/public/PublicShell";
import PrivacyPolicyContent from "@/components/public/PrivacyPolicyContent";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description:
    "Read the WiFiVision Privacy Policy covering account data, local-first Android Wi-Fi observations, optional device GPS coordinates, performance measurements, cookies, and data rights.",
};

export default function PrivacyPolicyPage() {
  return (
    <PublicShell>
      <PrivacyPolicyContent />
    </PublicShell>
  );
}
