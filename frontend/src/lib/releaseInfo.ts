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
  versionName: "1.1.2",
  versionCode: 5,
  packageId: "com.wifivision.app",
  minAndroidVersion: "Android 8.0 (Oreo · API 26)",
  minSdk: 26,
  targetSdk: 36,
  fileName: "wifivision-1.1.2.apk",
  downloadPath: "/downloads/wifivision-1.1.2.apk",
  latestMetadataPath: "/downloads/latest.json",
  sizeBytes: 304080,
  sizeShortLabel: "297.0 KB (0.29 MB)",
  sizeLabel: "297.0 KB (0.29 MB · 304,080 bytes)",
  sha256: "9b3d9725258d9fe21f84b95f978e766e6d08f5646c7cb06ff027626c13ecb213",
  minimumSupportedVersionCode: 2,
  signingScheme: "APK Signature Scheme v2 (RSA 4096)",
  signerSummary: "CN=M Anas Manzoor, OU=WiFiVision, O=WiFiVision",
  signerCertSha256:
    "08aff154e5a6058204042e9c77061de364abf7911cedd71699ba86bca108b684",
  productionBackendUrl: "https://wi-fi-vision-backend.vercel.app",
};
