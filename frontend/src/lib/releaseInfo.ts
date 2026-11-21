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
  sizeBytes: number;
  sizeLabel: string;
  sha256: string;
  signingScheme: string;
  signerSummary: string;
  signerCertSha256: string;
  productionBackendUrl: string;
}

export const ANDROID_RELEASE: AndroidReleaseInfo = {
  appName: "WiFiVision",
  versionName: "1.1.0",
  versionCode: 3,
  packageId: "com.wifivision.app",
  minAndroidVersion: "Android 8.0 (Oreo · API 26)",
  minSdk: 26,
  targetSdk: 36,
  fileName: "wifivision-1.1.0.apk",
  downloadPath: "/downloads/wifivision-1.1.0.apk",
  sizeBytes: 274204,
  sizeLabel: "267.8 KB (0.26 MB · 274,204 bytes)",
  sha256: "e8d4650090465f6eedeefeba528bef23c86f919b4468cede45038411579d182d",
  signingScheme: "APK Signature Scheme v2 (RSA 4096)",
  signerSummary: "CN=M Anas Manzoor, OU=WiFiVision, O=WiFiVision",
  signerCertSha256:
    "08aff154e5a6058204042e9c77061de364abf7911cedd71699ba86bca108b684",
  productionBackendUrl: "https://wi-fi-vision-backend.vercel.app",
};
