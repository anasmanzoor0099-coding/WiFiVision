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
  versionName: "1.1.3",
  versionCode: 6,
  packageId: "com.wifivision.app",
  minAndroidVersion: "Android 8.0 (Oreo · API 26)",
  minSdk: 26,
  targetSdk: 36,
  fileName: "wifivision-1.1.3.apk",
  downloadPath: "/downloads/wifivision-1.1.3.apk",
  latestMetadataPath: "/downloads/latest.json",
  sizeBytes: 310616,
  sizeShortLabel: "303.3 KB (0.30 MB)",
  sizeLabel: "303.3 KB (0.30 MB · 310,616 bytes)",
  sha256: "537b8d9a5648d1180d5ab76b529e9dec38da464e122fa7f21fd9471a503c4339",
  minimumSupportedVersionCode: 2,
  signingScheme: "APK Signature Scheme v2 (RSA 4096)",
  signerSummary: "CN=M Anas Manzoor, OU=WiFiVision, O=WiFiVision",
  signerCertSha256:
    "08aff154e5a6058204042e9c77061de364abf7911cedd71699ba86bca108b684",
  productionBackendUrl: "https://wi-fi-vision-backend.vercel.app",
};
