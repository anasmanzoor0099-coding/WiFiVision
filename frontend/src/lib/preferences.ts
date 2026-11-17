export type BandPreference = "all" | "2.4 GHz" | "5 GHz" | "6 GHz";
export type MonitoringIntervalSeconds = 15 | 30 | 60;
export type PerformanceHistoryLimit = 20 | 50 | 100;

export interface LocalDashboardPreferences {
  defaultBand: BandPreference;
  monitoringAutoRefresh: boolean;
  monitoringIntervalSeconds: MonitoringIntervalSeconds;
  performanceHistoryLimit: PerformanceHistoryLimit;
}

const STORAGE_KEY = "wifivision.localPreferences.v1";

export const DEFAULT_LOCAL_PREFERENCES: LocalDashboardPreferences = {
  defaultBand: "all",
  monitoringAutoRefresh: false,
  monitoringIntervalSeconds: 30,
  performanceHistoryLimit: 50,
};

function isValidBand(value: unknown): value is BandPreference {
  return (
    value === "all" ||
    value === "2.4 GHz" ||
    value === "5 GHz" ||
    value === "6 GHz"
  );
}

function isValidInterval(value: unknown): value is MonitoringIntervalSeconds {
  return value === 15 || value === 30 || value === 60;
}

function isValidHistoryLimit(value: unknown): value is PerformanceHistoryLimit {
  return value === 20 || value === 50 || value === 100;
}

export function getLocalPreferences(): LocalDashboardPreferences {
  if (typeof window === "undefined") {
    return { ...DEFAULT_LOCAL_PREFERENCES };
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_LOCAL_PREFERENCES };

    const parsed = JSON.parse(raw) as Partial<LocalDashboardPreferences>;

    return {
      defaultBand: isValidBand(parsed.defaultBand)
        ? parsed.defaultBand
        : DEFAULT_LOCAL_PREFERENCES.defaultBand,
      monitoringAutoRefresh:
        typeof parsed.monitoringAutoRefresh === "boolean"
          ? parsed.monitoringAutoRefresh
          : DEFAULT_LOCAL_PREFERENCES.monitoringAutoRefresh,
      monitoringIntervalSeconds: isValidInterval(
        parsed.monitoringIntervalSeconds
      )
        ? parsed.monitoringIntervalSeconds
        : DEFAULT_LOCAL_PREFERENCES.monitoringIntervalSeconds,
      performanceHistoryLimit: isValidHistoryLimit(parsed.performanceHistoryLimit)
        ? parsed.performanceHistoryLimit
        : DEFAULT_LOCAL_PREFERENCES.performanceHistoryLimit,
    };
  } catch {
    return { ...DEFAULT_LOCAL_PREFERENCES };
  }
}

export function saveLocalPreferences(
  partial: Partial<LocalDashboardPreferences>
): { saved: LocalDashboardPreferences; storageAvailable: boolean } {
  const current = getLocalPreferences();
  const next: LocalDashboardPreferences = {
    defaultBand: isValidBand(partial.defaultBand)
      ? partial.defaultBand
      : current.defaultBand,
    monitoringAutoRefresh:
      typeof partial.monitoringAutoRefresh === "boolean"
        ? partial.monitoringAutoRefresh
        : current.monitoringAutoRefresh,
    monitoringIntervalSeconds: isValidInterval(partial.monitoringIntervalSeconds)
      ? partial.monitoringIntervalSeconds
      : current.monitoringIntervalSeconds,
    performanceHistoryLimit: isValidHistoryLimit(partial.performanceHistoryLimit)
      ? partial.performanceHistoryLimit
      : current.performanceHistoryLimit,
  };

  if (typeof window === "undefined") {
    return { saved: next, storageAvailable: false };
  }

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    return { saved: next, storageAvailable: true };
  } catch {
    return { saved: next, storageAvailable: false };
  }
}

export function resetLocalPreferences(): {
  saved: LocalDashboardPreferences;
  storageAvailable: boolean;
} {
  if (typeof window === "undefined") {
    return {
      saved: { ...DEFAULT_LOCAL_PREFERENCES },
      storageAvailable: false,
    };
  }

  try {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(DEFAULT_LOCAL_PREFERENCES)
    );
    return {
      saved: { ...DEFAULT_LOCAL_PREFERENCES },
      storageAvailable: true,
    };
  } catch {
    return {
      saved: { ...DEFAULT_LOCAL_PREFERENCES },
      storageAvailable: false,
    };
  }
}
