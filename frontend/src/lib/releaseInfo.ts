export interface AndroidReleaseInfo {
  appName: string;
  versionName: string;
  versionCode: number;
  packageId: string;
  minAndroidVersion: string;
  minSdk: number;
  targetSdk: number;
  fileName: string;
  downloadPath: string;
  latestMetadataPath: string;
  sizeBytes: number;
  sizeShortLabel: string;
  sizeLabel: string;
  sha256: string;
  minimumSupportedVersionCode: number;
  signingScheme: string;
  signerSummary: string;
  signerCertSha256: string;
  productionBackendUrl: string;
}

export const ANDROID_RELEASE: AndroidReleaseInfo = {
  appName: "WiFiVision",
  versionName: "1.1.4",
  versionCode: 7,
  packageId: "com.wifivision.app",
  minAndroidVersion: "Android 8.0 (Oreo · API 26)",
  minSdk: 26,
  targetSdk: 36,
  fileName: "wifivision-1.1.4.apk",
  downloadPath: "/downloads/wifivision-1.1.4.apk",
  latestMetadataPath: "/downloads/latest.json",
  sizeBytes: 311856,
  sizeShortLabel: "304.5 KB (0.30 MB)",
  sizeLabel: "304.5 KB (0.30 MB · 311,856 bytes)",
  sha256: "1ec2a5e02b1cf28df531aa58d4e940d01f6f083ccddc81e797456e78a2842fd4",
  minimumSupportedVersionCode: 2,
  signingScheme: "APK Signature Scheme v2 (RSA 4096)",
  signerSummary: "CN=M Anas Manzoor, OU=WiFiVision, O=WiFiVision",
  signerCertSha256:
    "08aff154e5a6058204042e9c77061de364abf7911cedd71699ba86bca108b684",
  productionBackendUrl: "https://wi-fi-vision-backend.vercel.app",
};
