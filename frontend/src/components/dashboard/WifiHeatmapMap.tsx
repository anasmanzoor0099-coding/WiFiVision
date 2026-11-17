"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  Circle,
  CircleMarker,
  MapContainer,
  Marker,
  Pane,
  Popup,
  TileLayer,
  useMap,
  useMapEvents,
} from "react-leaflet";

import L from "leaflet";

import {
  Activity,
  ChartNoAxesColumnIncreasing,
  Clock3,
  Crosshair,
  Layers,
  MapPin,
  MapPinned,
  Navigation,
  RadioTower,
  RefreshCw,
  ShieldCheck,
  Signal,
  Wifi,
} from "lucide-react";

import type {
  HeatmapAccuracySummary,
  HeatmapLocationSummary,
  HeatmapPoint,
  NetworkObservation,
} from "@/lib/dashboard";
import {
  type BandFilter,
  DENSITY_COLORS,
  DENSITY_THRESHOLDS,
  SIGNAL_CLASSIFICATIONS,
  buildNetworkSummaries,
  computeGeographicCenter,
  computeHeatmapAccuracySummary,
  computeObservationLocationCounts,
  getDensityVisuals,
  getLocationPrecisionInfo,
  getPointBandCount,
  getPointBandSignal,
  getPopupBandMetrics,
  getSignalClassification,
  getSignalGlowDiameter,
  getSignalMarkerRadius,
  isValidCoordinate,
  normalizeObservationBand,
} from "@/lib/heatmap";
import { getLocalPreferences } from "@/lib/preferences";

export type HeatPoint = HeatmapPoint;
export type { BandFilter } from "@/lib/heatmap";

interface WifiHeatmapMapProps {
  points: HeatPoint[];
  center?: [number, number] | null;
  observations: NetworkObservation[];
  observationsTotalCount?: number | null;
  selectedBand?: BandFilter;
  onBandChange?: (band: BandFilter) => void;
  accuracySummary?: HeatmapAccuracySummary | null;
  locationSummary?: HeatmapLocationSummary | null;
  compact?: boolean;
  showBandFilter?: boolean;
}

type MapLayer = "signal" | "density";

const TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

const panel =
  "rounded-2xl border border-[#303640]/[0.09] bg-white shadow-[0_2px_10px_rgba(48,54,64,0.03)]";

const mutedText = "text-[#5E6572]";
const goldText = "text-[#9F7C38]";

function getSignalColor(signal: number | null | undefined): string {
  return getSignalClassification(signal).color;
}

function getSignalLabel(signal: number | null | undefined): string {
  return getSignalClassification(signal).label;
}

function getSignalBackground(signal: number | null | undefined): string {
  return getSignalClassification(signal).badgeClass;
}

function hexToRgba(hex: string, alpha: number): string {
  const value = hex.replace("#", "");

  const red = parseInt(value.slice(0, 2), 16);
  const green = parseInt(value.slice(2, 4), 16);
  const blue = parseInt(value.slice(4, 6), 16);

  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

function getBandCount(point: HeatPoint, band: BandFilter): number {
  return getPointBandCount(point, band);
}

function getBandSignal(point: HeatPoint, band: BandFilter): number | null {
  return getPointBandSignal(point, band);
}

function getFrequencyBand(
  observation: NetworkObservation
): string {
  return normalizeObservationBand(observation);
}

/* =========================================
   THERMAL VISUALIZATION
========================================= */

function ThermalGlow({
  point,
  activeLayer,
  band,
  maxDensityCount,
  zoom,
}: {
  point: HeatPoint;
  activeLayer: MapLayer;
  band: BandFilter;
  maxDensityCount: number;
  zoom: number;
}) {
  const count = getBandCount(point, band);
  const densityVisuals =
    activeLayer === "density"
      ? getDensityVisuals(count, maxDensityCount, zoom)
      : null;

  const bandSignal = getBandSignal(point, band);
  const color =
    activeLayer === "signal"
      ? bandSignal !== null
        ? getSignalColor(bandSignal)
        : "#868D98"
      : densityVisuals!.color;

  const diameterPx =
    activeLayer === "signal"
      ? getSignalGlowDiameter(zoom)
      : densityVisuals!.glowDiameterPx;

  const peakOpacity =
    activeLayer === "signal" ? 0.62 : densityVisuals!.glowOpacity;

  const icon = useMemo(() => {
    const half = Math.round(diameterPx / 2);
    const background = `
      radial-gradient(
        circle,
        ${hexToRgba(color, peakOpacity)} 0%,
        ${hexToRgba(color, peakOpacity * 0.68)} 18%,
        ${hexToRgba(color, peakOpacity * 0.38)} 36%,
        ${hexToRgba(color, peakOpacity * 0.18)} 54%,
        ${hexToRgba(color, peakOpacity * 0.06)} 70%,
        transparent 80%
      )
    `;

    return L.divIcon({
      className: "wifi-thermal-glow-icon",
      html: `
        <div style="
          width: ${diameterPx}px;
          height: ${diameterPx}px;
          border-radius: 50%;
          background: ${background};
          filter: blur(10px);
          pointer-events: none;
        "></div>
      `,
      iconSize: [diameterPx, diameterPx],
      iconAnchor: [half, half],
    });
  }, [color, diameterPx, peakOpacity]);

  return (
    <Marker
      pane="thermalPane"
      position={[point.latitude, point.longitude]}
      icon={icon}
      interactive={false}
      keyboard={false}
    />
  );
}

/* =========================================
   MAP MANAGEMENT
========================================= */

function MapUpdater({
  points,
  userLocation,
}: {
  points: HeatPoint[];
  userLocation: [number, number] | null;
}) {
  const map = useMap();

  useEffect(() => {
    const locations: [number, number][] = points.map(
      (point) => [point.latitude, point.longitude]
    );

    if (userLocation) locations.push(userLocation);

    if (locations.length === 1) {
      map.flyTo(locations[0], 15, { duration: 1 });
      return;
    }

    if (locations.length > 1) {
      map.fitBounds(L.latLngBounds(locations), {
        padding: [60, 60],
        maxZoom: 15,
        animate: true,
      });
    }
  }, [map, points, userLocation]);

  return null;
}

function MapResizeHandler() {
  const map = useMap();

  useEffect(() => {
    const timer = window.setTimeout(() => {
      map.invalidateSize();
    }, 250);

    const observer = new ResizeObserver(() => {
      map.invalidateSize();
    });

    observer.observe(map.getContainer());

    return () => {
      window.clearTimeout(timer);
      observer.disconnect();
    };
  }, [map]);

  return null;
}

function MapZoomListener({
  onZoomChange,
}: {
  onZoomChange: (zoom: number) => void;
}) {
  const map = useMapEvents({
    zoomend: () => {
      onZoomChange(map.getZoom());
    },
  });

  useEffect(() => {
    onZoomChange(map.getZoom());
  }, [map, onZoomChange]);

  return null;
}

/* =========================================
   MAP CONTROLS
========================================= */

function MapControls({
  onLocate,
  locating,
}: {
  onLocate: () => void;
  locating: boolean;
}) {
  const map = useMap();

  const controlClass =
    "flex h-9 w-9 items-center justify-center rounded-lg border border-[#303640]/15 bg-white/95 text-[#303640] shadow-md backdrop-blur transition hover:border-[#C6A15B] hover:bg-[#F7F6F2] disabled:opacity-50";

  return (
    <div className="absolute right-3 top-3 z-[1000] flex flex-col gap-2">
      <button
        type="button"
        onClick={() => map.zoomIn()}
        className={controlClass}
        aria-label="Zoom in"
      >
        +
      </button>

      <button
        type="button"
        onClick={() => map.zoomOut()}
        className={controlClass}
        aria-label="Zoom out"
      >
        −
      </button>

      <button
        type="button"
        onClick={onLocate}
        disabled={locating}
        className={controlClass}
        aria-label="Locate me"
        title="Locate me"
      >
        {locating ? (
          <RefreshCw className="h-4 w-4 animate-spin" />
        ) : (
          <Crosshair className="h-4 w-4" />
        )}
      </button>
    </div>
  );
}

/* =========================================
   NETWORK INTELLIGENCE
========================================= */

function NetworkIntelligence({
  observations,
  totalRecordedCount,
}: {
  observations: NetworkObservation[];
  totalRecordedCount?: number | null;
}) {
  const networks = useMemo(
    () => buildNetworkSummaries(observations),
    [observations]
  );

  const totalRecords = networks.reduce(
    (sum, network) => sum + network.observations,
    0
  );

  const strongNetworks = networks.filter(
    (network) => network.strongestSignal >= -70
  ).length;

  const weakNetworks = networks.filter(
    (network) => network.strongestSignal < -80
  ).length;

  const moderateNetworks =
    networks.length - strongNetworks - weakNetworks;

  const isTruncatedSample =
    typeof totalRecordedCount === "number" &&
    totalRecordedCount > observations.length;

  return (
    <section className={`${panel} overflow-hidden`}>
      <div className="flex flex-col justify-between gap-3 border-b border-[#303640]/[0.08] px-4 py-3 sm:flex-row sm:items-center">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#C6A15B]/30 bg-[#C6A15B]/12 text-[#9F7C38]">
            <RadioTower className="h-4 w-4" />
          </div>

          <div className="min-w-0">
            <h3 className="text-sm font-semibold text-[#303640]">
              Detected Wi-Fi Networks
            </h3>
            <p className={`mt-1 text-[11px] ${mutedText}`}>
              {isTruncatedSample
                ? `Grouped from latest ${observations.length} of ${totalRecordedCount} recorded scans`
                : "Grouped across recorded scans (includes Hidden Network entries)"}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div className="rounded-lg border border-[#303640]/[0.08] bg-[#F1EFE9] px-3 py-2">
            <p className="text-base font-bold leading-none text-[#303640]">
              {networks.length}
            </p>
            <p className="mt-1 text-[9px] uppercase tracking-wider text-[#5E6572]">
              Networks
            </p>
          </div>

          <div className="rounded-lg border border-[#303640]/[0.08] bg-[#F1EFE9] px-3 py-2">
            <p className="text-base font-bold leading-none text-[#303640]">
              {totalRecords}
            </p>
            <p className="mt-1 text-[9px] uppercase tracking-wider text-[#5E6572]">
              Records
            </p>
          </div>
        </div>
      </div>

      {networks.length > 0 ? (
        <>
          <div className="grid grid-cols-1 gap-2 border-b border-[#303640]/[0.08] p-3 sm:grid-cols-3">
            {[
              {
                label: "Excellent / Good (≥ -70 dBm)",
                count: strongNetworks,
                icon: Signal,
                color: "text-[#2E7D5B]",
                background: "bg-[#2E7D5B]/10",
              },
              {
                label: "Fair (-71 to -80 dBm)",
                count: moderateNetworks,
                icon: Activity,
                color: "text-[#9F7C38]",
                background: "bg-[#C6A15B]/15",
              },
              {
                label: "Weak / Very Weak (< -80 dBm)",
                count: weakNetworks,
                icon: Wifi,
                color: "text-[#B83B3B]",
                background: "bg-[#B83B3B]/10",
              },
            ].map((item) => {
              const Icon = item.icon;

              return (
                <div
                  key={item.label}
                  className="flex min-w-0 items-center gap-3 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3 py-2.5"
                >
                  <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${item.background} ${item.color}`}>
                    <Icon className="h-4 w-4" />
                  </div>

                  <div className="min-w-0">
                    <p className="text-lg font-bold leading-tight text-[#303640]">
                      {item.count}
                    </p>
                    <p className={`mt-0.5 truncate text-[10px] ${mutedText}`}>
                      {item.label}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="grid grid-cols-1 gap-3 p-3 sm:grid-cols-2 xl:grid-cols-3">
            {networks.map((network) => {
              const color = getSignalColor(network.strongestSignal);

              const signalWidth = Math.max(
                4,
                Math.min(
                  100,
                  ((network.strongestSignal + 100) / 70) * 100
                )
              );

              const time = new Date(network.latestObservation);

              return (
                <article
                  key={network.groupKey}
                  className="min-w-0 rounded-xl border border-[#303640]/[0.09] bg-[#F7F6F2] p-3.5 transition hover:border-[#C6A15B]/50 hover:bg-white"
                >
                  <div className="flex min-w-0 items-start justify-between gap-2">
                    <div className="flex min-w-0 items-center gap-2.5">
                      <div
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
                        style={{
                          backgroundColor: hexToRgba(color, 0.12),
                          color,
                        }}
                      >
                        <Wifi className="h-4 w-4" />
                      </div>

                      <div className="min-w-0">
                        <h4
                          className={`truncate text-xs font-semibold ${
                            network.isHidden
                              ? "italic text-[#5E6572]"
                              : "text-[#303640]"
                          }`}
                          title={network.ssid}
                        >
                          {network.ssid}
                        </h4>
                        <p className={`mt-1 text-[10px] ${mutedText}`}>
                          {network.observations} scan records
                        </p>
                      </div>
                    </div>

                    <span
                      className={`shrink-0 rounded-md border px-2 py-1 text-[9px] font-semibold ${getSignalBackground(network.strongestSignal)}`}
                    >
                      {getSignalLabel(network.strongestSignal)}
                    </span>
                  </div>

                  <div className="mt-3">
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <span className={`text-[10px] ${mutedText}`}>
                        Strongest signal
                      </span>
                      <span
                        className="text-xs font-bold tabular-nums"
                        style={{ color }}
                      >
                        {network.strongestSignal} dBm
                      </span>
                    </div>

                    <div className="h-1.5 overflow-hidden rounded-full bg-[#303640]/10">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${signalWidth}%`,
                          backgroundColor: color,
                        }}
                      />
                    </div>
                  </div>

                  <div className="mt-3 grid grid-cols-2 gap-2">
                    <div className="rounded-lg border border-[#303640]/[0.08] bg-white px-2.5 py-2">
                      <p className="text-[9px] uppercase tracking-wider text-[#868D98]">
                        Strongest
                      </p>
                      <p className="mt-1 text-sm font-semibold tabular-nums text-[#303640]">
                        {network.strongestSignal} dBm
                      </p>
                    </div>

                    <div className="rounded-lg border border-[#303640]/[0.08] bg-white px-2.5 py-2">
                      <p className="text-[9px] uppercase tracking-wider text-[#868D98]">
                        Weakest
                      </p>
                      <p className="mt-1 text-sm font-semibold tabular-nums text-[#5E6572]">
                        {network.weakestSignal} dBm
                      </p>
                    </div>
                  </div>

                  <div className="mt-2.5 flex flex-wrap gap-1.5">
                    {network.bands.map((band) => (
                      <span
                        key={band}
                        className="rounded border border-[#C6A15B]/35 bg-[#C6A15B]/12 px-2 py-1 text-[9px] font-semibold text-[#9F7C38]"
                      >
                        {band}
                      </span>
                    ))}
                  </div>

                  <div className="mt-2.5 flex min-w-0 items-center justify-between gap-2 border-t border-[#303640]/[0.08] pt-2.5">
                    <span className={`flex min-w-0 items-center gap-1.5 truncate text-[10px] ${mutedText}`}>
                      <ShieldCheck className="h-3 w-3 shrink-0" />
                      <span className="truncate">{network.security}</span>
                    </span>

                    <span className={`flex shrink-0 items-center gap-1 text-[9px] ${mutedText}`}>
                      <Clock3 className="h-3 w-3" />
                      {Number.isNaN(time.getTime())
                        ? "Unknown"
                        : time.toLocaleTimeString([], {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                    </span>
                  </div>
                </article>
              );
            })}
          </div>
        </>
      ) : (
        <div className="px-4 py-10 text-center">
          <Wifi className="mx-auto h-7 w-7 text-[#868D98]" />
          <h4 className="mt-3 text-sm font-semibold text-[#303640]">
            No individual networks found
          </h4>
          <p className={`mx-auto mt-1 max-w-sm text-xs leading-relaxed ${mutedText}`}>
            Scan nearby Wi-Fi networks using your paired Android or Windows
            companion app and refresh the dashboard to see network intelligence.
          </p>
        </div>
      )}

      <div className="border-t border-[#303640]/[0.08] bg-[#F1EFE9]/60 px-4 py-3">
        <p className={`text-[10px] leading-relaxed ${mutedText}`}>
          Signal readings represent measurements received by your
          device at the scan location. Repeated SSIDs are grouped
          together. Separate router identities and physical router
          locations require BSSID and router-location data.
        </p>
      </div>
    </section>
  );
}

/* =========================================
   MAIN HEATMAP COMPONENT
========================================= */

export default function WifiHeatmapMap({
  points,
  center,
  observations,
  observationsTotalCount,
  selectedBand: controlledBand,
  onBandChange,
  accuracySummary: providedAccuracySummary,
  locationSummary,
  compact = false,
  showBandFilter = true,
}: WifiHeatmapMapProps) {
  const [activeLayer, setActiveLayer] =
    useState<MapLayer>("signal");

  const [mapZoom, setMapZoom] = useState<number>(13);

  const [internalBand, setInternalBand] =
    useState<BandFilter>(() => getLocalPreferences().defaultBand);

  const selectedBand = controlledBand ?? internalBand;

  const handleBandSelect = useCallback(
    (band: BandFilter) => {
      if (onBandChange) {
        onBandChange(band);
      } else {
        setInternalBand(band);
      }
    },
    [onBandChange]
  );

  const [userLocation, setUserLocation] =
    useState<[number, number] | null>(null);

  const [locationAccuracy, setLocationAccuracy] =
    useState<number | null>(null);

  const [locationError, setLocationError] = useState("");
  const [locating, setLocating] = useState(false);

  const visiblePoints = useMemo(() => {
    return points.filter((point) => {
      if (!isValidCoordinate(point.latitude, point.longitude)) {
        return false;
      }
      if (selectedBand === "all") return true;
      return getBandCount(point, selectedBand) > 0;
    });
  }, [points, selectedBand]);

  const mapCenter = useMemo<[number, number] | null>(() => {
    if (
      Array.isArray(center) &&
      isValidCoordinate(center[0], center[1]) &&
      visiblePoints.length > 0
    ) {
      return center;
    }

    return computeGeographicCenter(visiblePoints, selectedBand) ?? userLocation;
  }, [center, visiblePoints, selectedBand, userLocation]);

  const locationCounts = useMemo(
    () =>
      computeObservationLocationCounts(
        observations,
        selectedBand,
        locationSummary
      ),
    [observations, selectedBand, locationSummary]
  );

  const totalObservations = useMemo(
    () =>
      visiblePoints.reduce(
        (total, point) =>
          total + getBandCount(point, selectedBand),
        0
      ),
    [visiblePoints, selectedBand]
  );

  const maxVisibleDensityCount = useMemo(
    () =>
      visiblePoints.reduce(
        (max, point) => Math.max(max, getBandCount(point, selectedBand)),
        1
      ),
    [visiblePoints, selectedBand]
  );

  const computedAccuracySummary = useMemo(
    () =>
      computeHeatmapAccuracySummary(
        visiblePoints,
        selectedBand,
        providedAccuracySummary
      ),
    [visiblePoints, selectedBand, providedAccuracySummary]
  );

  const overallPrecision = useMemo(
    () => getLocationPrecisionInfo(computedAccuracySummary.averageAccuracyMeters),
    [computedAccuracySummary.averageAccuracyMeters]
  );

  const averageSignal = useMemo(() => {
    if (!visiblePoints.length) return null;

    let total = 0;
    let count = 0;

    for (const point of visiblePoints) {
      const pointSignal = getBandSignal(point, selectedBand);
      const pointCount = getBandCount(point, selectedBand);
      if (pointSignal !== null && pointCount > 0) {
        total += pointSignal * pointCount;
        count += pointCount;
      }
    }

    return count ? Math.round(total / count) : null;
  }, [visiblePoints, selectedBand]);

  const locateUser = useCallback(() => {
    if (!navigator.geolocation) {
      setLocationError(
        "Geolocation is not supported by this browser."
      );
      return;
    }

    setLocating(true);
    setLocationError("");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const location: [number, number] = [
          position.coords.latitude,
          position.coords.longitude,
        ];

        setUserLocation(location);
        setLocationAccuracy(position.coords.accuracy);
        setLocating(false);
      },
      (error) => {
        const messages: Record<number, string> = {
          1: "Location permission denied. Please allow location access.",
          2: "Your current location is unavailable.",
          3: "Location request timed out. Please try again.",
        };

        setLocationError(
          messages[error.code] ??
            "Unable to determine your location."
        );

        setLocating(false);
      },
      {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 10000,
      }
    );
  }, []);

  const bandOptions: BandFilter[] = [
    "all",
    "2.4 GHz",
    "5 GHz",
    "6 GHz",
  ];

  return (
    <div className="w-full min-w-0 space-y-3">

      {/* COMPACT MAP TOOLBAR */}

      <section className={`${panel} p-3`}>
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`mr-1 text-[10px] font-semibold uppercase tracking-wider ${mutedText}`}>
              Map layer
            </span>

            <button
              type="button"
              onClick={() => setActiveLayer("signal")}
              className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-[11px] font-semibold transition ${
                activeLayer === "signal"
                  ? "border-[#C6A15B]/50 bg-[#C6A15B]/15 text-[#9F7C38]"
                  : "border-[#303640]/12 bg-[#F1EFE9] text-[#5E6572] hover:border-[#C6A15B]/40 hover:text-[#303640]"
              }`}
            >
              <Signal className="h-3.5 w-3.5" />
              Signal strength
            </button>

            <button
              type="button"
              onClick={() => setActiveLayer("density")}
              className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-[11px] font-semibold transition ${
                activeLayer === "density"
                  ? "border-[#C6A15B]/50 bg-[#C6A15B]/15 text-[#9F7C38]"
                  : "border-[#303640]/12 bg-[#F1EFE9] text-[#5E6572] hover:border-[#C6A15B]/40 hover:text-[#303640]"
              }`}
            >
              <Activity className="h-3.5 w-3.5" />
              Observation density
            </button>
          </div>

          {showBandFilter && (
            <div className="flex flex-wrap items-center gap-2">
              <span className={`mr-1 text-[10px] font-semibold uppercase tracking-wider ${mutedText}`}>
                Frequency
              </span>

              {bandOptions.map((band) => (
                <button
                  key={band}
                  type="button"
                  onClick={() => handleBandSelect(band)}
                  className={`rounded-lg border px-2.5 py-2 text-[10px] font-semibold transition ${
                    selectedBand === band
                      ? "border-[#C6A15B]/50 bg-[#C6A15B]/15 text-[#9F7C38]"
                      : "border-[#303640]/12 bg-[#F1EFE9] text-[#5E6572] hover:border-[#C6A15B]/40 hover:text-[#303640]"
                  }`}
                >
                  {band === "all" ? "All" : band}
                </button>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* COMPACT MAP STATISTICS */}

      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <div className={`${panel} flex min-w-0 items-center gap-2.5 px-3.5 py-2.5`}>
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#C6A15B]/15 text-[#9F7C38]">
            <Wifi className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <p className="text-base font-bold leading-tight tabular-nums text-[#303640]">
              {totalObservations}
            </p>
            <p className={`mt-1 truncate text-[9px] uppercase tracking-wider ${mutedText}`}>
              Observations
            </p>
          </div>
        </div>

        <div className={`${panel} flex min-w-0 items-center gap-2.5 px-3.5 py-2.5`}>
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#F1EFE9] text-[#303640]">
            <MapPinned className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <p className="text-base font-bold leading-tight tabular-nums text-[#303640]">
              {visiblePoints.length}
            </p>
            <p className={`mt-1 truncate text-[9px] uppercase tracking-wider ${mutedText}`}>
              Locations
            </p>
          </div>
        </div>

        <div className={`${panel} flex min-w-0 items-center gap-2.5 px-3.5 py-2.5`}>
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#2E7D5B]/10 text-[#2E7D5B]">
            <ChartNoAxesColumnIncreasing className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-base font-bold leading-tight tabular-nums text-[#303640]">
              {averageSignal !== null ? `${averageSignal}` : "N/A"}
              {averageSignal !== null && (
                <span className="ml-1 text-[9px] font-medium text-[#5E6572]">
                  dBm
                </span>
              )}
            </p>
            <p className={`mt-1 truncate text-[9px] uppercase tracking-wider ${mutedText}`}>
              Avg. signal
            </p>
          </div>
        </div>

        <div className={`${panel} flex min-w-0 items-center gap-2.5 px-3.5 py-2.5`}>
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#F1EFE9] text-[#303640]">
            <Crosshair className="h-4 w-4 text-[#9F7C38]" />
          </div>
          <div className="min-w-0">
            <p
              className={`truncate text-base font-bold leading-tight tabular-nums ${
                overallPrecision.level === "Unknown"
                  ? "text-[#5E6572]"
                  : "text-[#303640]"
              }`}
              title={overallPrecision.label}
            >
              {overallPrecision.formattedMeters}
            </p>
            <p className={`mt-1 truncate text-[9px] uppercase tracking-wider ${mutedText}`}>
              GPS ({overallPrecision.shortLabel})
            </p>
          </div>
        </div>
      </div>

      {/* GEOGRAPHIC MAP */}

      <section className={`${panel} overflow-hidden`}>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#303640]/[0.08] px-3 py-3 sm:px-4">
          {activeLayer === "signal" ? (
            <div className="flex flex-wrap items-center gap-x-3.5 gap-y-1.5">
              {SIGNAL_CLASSIFICATIONS.map((item) => (
                <span
                  key={item.tier}
                  className="flex items-center gap-1.5 text-[10px] font-semibold text-[#303640]"
                  title={`${item.label} (${item.rangeLabel})`}
                >
                  <span
                    className="h-2 w-2 shrink-0 rounded-full"
                    style={{ backgroundColor: item.color }}
                  />
                  {item.label}
                </span>
              ))}
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              <span className="flex items-center gap-2 text-[10px] font-semibold text-[#303640]">
                <span
                  className="h-2 w-2 rounded-full"
                  style={{ backgroundColor: DENSITY_COLORS.Low }}
                />
                Low (1–{DENSITY_THRESHOLDS.lowMax})
              </span>

              <span className="flex items-center gap-2 text-[10px] font-semibold text-[#303640]">
                <span
                  className="h-2.5 w-2.5 rounded-full"
                  style={{ backgroundColor: DENSITY_COLORS.Medium }}
                />
                Medium ({DENSITY_THRESHOLDS.lowMax + 1}–{DENSITY_THRESHOLDS.mediumMax})
              </span>

              <span className="flex items-center gap-2 text-[10px] font-semibold text-[#303640]">
                <span
                  className="h-3 w-3 rounded-full"
                  style={{ backgroundColor: DENSITY_COLORS.High }}
                />
                High ({DENSITY_THRESHOLDS.mediumMax + 1}+)
              </span>
            </div>
          )}

          <span className="text-[10px] text-[#5E6572]">
            {activeLayer === "signal"
              ? "Measured RSSI (dBm)"
              : "Observation count per cell (not RSSI)"}
          </span>
        </div>

        <div
          className={`relative w-full bg-[#F1EFE9] ${
            compact
              ? "h-[360px] sm:h-[430px] lg:h-[470px]"
              : "h-[400px] sm:h-[500px] lg:h-[560px]"
          }`}
        >
          {mapCenter ? (
            <>
              <MapContainer
                center={mapCenter}
                zoom={13}
                scrollWheelZoom
                doubleClickZoom
                dragging
                zoomControl={false}
                className="z-0 h-full w-full"
              >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url={TILE_URL}
              maxZoom={19}
            />

            <MapUpdater
              points={visiblePoints}
              userLocation={userLocation}
            />

            <MapResizeHandler />

            <MapZoomListener onZoomChange={setMapZoom} />

            <Pane
              name="thermalPane"
              style={{
                zIndex: 350,
                pointerEvents: "none",
              }}
            >
              {visiblePoints.map((point, index) => (
                <ThermalGlow
                  key={`thermal-${point.latitude}-${point.longitude}-${index}`}
                  point={point}
                  activeLayer={activeLayer}
                  band={selectedBand}
                  maxDensityCount={maxVisibleDensityCount}
                  zoom={mapZoom}
                />
              ))}
            </Pane>

            {visiblePoints.map((point, index) => {
              const popupMetrics = getPopupBandMetrics(point, selectedBand);
              const bandSignal = popupMetrics.averageSignal;
              const count = popupMetrics.sampleCount;
              const densityVisuals = getDensityVisuals(
                count,
                maxVisibleDensityCount,
                mapZoom
              );
              const precision = getLocationPrecisionInfo(point.avgAccuracyMeters);
              const color =
                activeLayer === "signal"
                  ? bandSignal !== null
                    ? getSignalColor(bandSignal)
                    : "#868D98"
                  : densityVisuals.color;
              const markerRadius =
                activeLayer === "signal"
                  ? getSignalMarkerRadius(mapZoom)
                  : densityVisuals.markerRadius;

              return (
                <CircleMarker
                  key={`observation-${point.latitude}-${point.longitude}-${index}`}
                  center={[point.latitude, point.longitude]}
                  radius={markerRadius}
                  pathOptions={{
                    color: "#303640",
                    fillColor: color,
                    fillOpacity: 1,
                    weight: 2,
                  }}
                >
                  <Popup>
                    <div className="min-w-[210px] space-y-1.5 text-xs text-[#303640]">
                      <div className="flex items-center justify-between gap-2 border-b border-[#303640]/10 pb-1.5">
                        <strong className="font-semibold text-[#303640]">
                          Scan Location
                        </strong>
                        <span className="rounded-md bg-[#F1EFE9] px-1.5 py-0.5 text-[10px] font-semibold text-[#9F7C38]">
                          {activeLayer === "signal"
                            ? bandSignal !== null
                              ? getSignalLabel(bandSignal)
                              : "Unavailable"
                            : `${densityVisuals.level} Density`}
                        </span>
                      </div>

                      <div className="flex justify-between gap-3">
                        <span className="text-[#5E6572]">
                          Average signal ({popupMetrics.bandLabel})
                        </span>
                        <strong className="tabular-nums text-[#303640]">
                          {popupMetrics.averageSignal !== null
                            ? `${popupMetrics.averageSignal} dBm`
                            : "—"}
                        </strong>
                      </div>

                      <div className="flex justify-between gap-3">
                        <span className="text-[#5E6572]">
                          Strongest / Weakest ({popupMetrics.bandLabel})
                        </span>
                        <span className="tabular-nums font-medium text-[#303640]">
                          {popupMetrics.strongestSignal !== null &&
                          popupMetrics.weakestSignal !== null
                            ? `${popupMetrics.strongestSignal} / ${popupMetrics.weakestSignal} dBm`
                            : "—"}
                        </span>
                      </div>

                      <div className="flex justify-between gap-3">
                        <span className="text-[#5E6572]">
                          Samples ({popupMetrics.bandLabel})
                        </span>
                        <span className="tabular-nums font-medium text-[#303640]">
                          {popupMetrics.sampleCount} of{" "}
                          {popupMetrics.totalObservations} ({densityVisuals.level})
                        </span>
                      </div>

                      <div className="flex items-center justify-between gap-2 pt-0.5">
                        <span className="text-[#5E6572]">Avg GPS accuracy</span>
                        <span
                          className={`rounded-md border px-1.5 py-0.5 text-[10px] font-semibold tabular-nums ${precision.badgeClass}`}
                          title={precision.label}
                        >
                          {precision.formattedMeters} · {precision.shortLabel}
                        </span>
                      </div>

                      <p className="border-t border-[#303640]/10 pt-1.5 text-[10px] leading-snug text-[#5E6572]">
                        Estimated device GPS radius (not guaranteed error or router location).
                      </p>

                      <div className="font-mono text-[10px] text-[#868D98]">
                        {point.latitude.toFixed(5)}, {point.longitude.toFixed(5)}
                      </div>
                    </div>
                  </Popup>
                </CircleMarker>
              );
            })}

            {userLocation && (
              <>
                {locationAccuracy !== null && (
                  <Circle
                    center={userLocation}
                    radius={locationAccuracy}
                    pathOptions={{
                      color: "#3B82F6",
                      fillColor: "#3B82F6",
                      fillOpacity: 0.10,
                      weight: 1.5,
                    }}
                  />
                )}

                <CircleMarker
                  center={userLocation}
                  radius={8}
                  pathOptions={{
                    color: "#FFFFFF",
                    fillColor: "#2563EB",
                    fillOpacity: 1,
                    weight: 2,
                  }}
                >
                  <Popup>
                    <strong>Your Current Location</strong>
                    <br />
                    GPS location obtained from your browser.
                  </Popup>
                </CircleMarker>
              </>
            )}

            <MapControls
              onLocate={locateUser}
              locating={locating}
            />
          </MapContainer>

          <div className="pointer-events-none absolute bottom-3 left-3 z-[1000] hidden max-w-[230px] rounded-xl border border-[#303640]/12 bg-white/95 px-3 py-2.5 shadow-md backdrop-blur sm:block">
            <div className="flex items-center gap-2">
              <span
                className="h-2 w-2 shrink-0 rounded-full"
                style={{
                  backgroundColor:
                    activeLayer === "signal" ? "#C6A15B" : DENSITY_COLORS.Medium,
                }}
              />
              <span className="text-[10px] font-semibold text-[#303640]">
                {activeLayer === "signal"
                  ? "Signal strength (RSSI)"
                  : "Observation density (count)"}
              </span>
            </div>

            <p className="mt-1 text-[9px] leading-relaxed text-[#5E6572]">
              {activeLayer === "signal"
                ? "Color reflects measured RSSI at the device scan coordinate."
                : "Size, glow, and color reflect recorded observation count per cell — not signal strength."}
            </p>
          </div>
            </>
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center px-6 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#C6A15B]/30 bg-white text-[#9F7C38] shadow-xs">
                <MapPin className="h-5 w-5" />
              </div>

              <h4 className="mt-4 text-sm font-semibold text-[#303640]">
                {locationCounts.nonGeolocatedObservations > 0
                  ? `All ${locationCounts.nonGeolocatedObservations} recorded observation(s) lack valid GPS coordinates`
                  : selectedBand === "all"
                    ? "No geolocated Wi-Fi observations to map yet"
                    : `No geolocated ${selectedBand} observations to map`}
              </h4>

              <p className={`mt-1.5 max-w-md text-xs leading-relaxed ${mutedText}`}>
                {locationCounts.nonGeolocatedObservations > 0
                  ? "Scans captured without valid latitude/longitude (such as desktop scans when Location Services are off) are preserved in your workspace analytics but cannot be plotted on a map."
                  : "Collect GPS-tagged observations from your paired Android or Windows device or switch frequency bands above."}
              </p>

              <button
                type="button"
                onClick={locateUser}
                disabled={locating}
                className="mt-4 inline-flex items-center gap-2 rounded-xl border border-[#303640]/15 bg-white px-3.5 py-2 text-xs font-semibold text-[#303640] shadow-xs transition hover:border-[#C6A15B] hover:bg-[#F7F6F2] disabled:opacity-50"
              >
                {locating ? (
                  <RefreshCw className="h-3.5 w-3.5 animate-spin text-[#C6A15B]" />
                ) : (
                  <Crosshair className="h-3.5 w-3.5 text-[#9F7C38]" />
                )}
                {locating ? "Checking browser GPS..." : "Center on my browser location"}
              </button>
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#303640]/[0.08] bg-[#F1EFE9]/60 px-3 py-3 sm:px-4">
          <p className={`flex flex-wrap items-center gap-x-2 gap-y-1 text-[10px] ${mutedText}`}>
            <span className="inline-flex items-center gap-1.5 font-medium text-[#303640]">
              <MapPin className="h-3 w-3 text-[#C6A15B]" />
              {visiblePoints.length} mapped cell(s) ·{" "}
              {locationCounts.geolocatedObservations} geolocated scan(s)
            </span>
            {locationCounts.nonGeolocatedObservations > 0 && (
              <span>
                · {locationCounts.nonGeolocatedObservations} excluded (missing/invalid coordinates)
              </span>
            )}
          </p>

          <p className={`text-[10px] ${mutedText}`}>
            Recorded device measurements only (not router locations)
          </p>
        </div>
      </section>

      {/* LOCATION STATUS */}

      {(locationError || userLocation) && (
        <div className={`${panel} px-3 py-3`}>
          {locationError && (
            <p className="text-xs font-medium text-[#B83B3B]">
              {locationError}
            </p>
          )}

          {userLocation && (
            <p className="flex flex-wrap items-center gap-1.5 text-[10px] font-medium text-[#303640]">
              <Navigation className="h-3.5 w-3.5 text-[#C6A15B]" />
              Your location: {userLocation[0].toFixed(5)},{" "}
              {userLocation[1].toFixed(5)}
              {locationAccuracy !== null &&
                ` · Accuracy approximately ${Math.round(locationAccuracy)} m`}
            </p>
          )}
        </div>
      )}

      {/* NETWORK INTELLIGENCE */}

      {!compact && (
        <NetworkIntelligence
          observations={
            selectedBand === "all"
              ? observations
              : observations.filter(
                  (obs) => getFrequencyBand(obs) === selectedBand
                )
          }
          totalRecordedCount={observationsTotalCount}
        />
      )}

      {/* MAP LEGEND */}

      <section className={`${panel} p-3 sm:p-4`}>
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Layers className={`h-4 w-4 ${goldText}`} />
            <h4 className="text-xs font-semibold text-[#303640]">
              Map Legend
            </h4>
          </div>
          <span className="text-[10px] text-[#5E6572]">
            {activeLayer === "signal"
              ? "RSSI signal scale (dBm)"
              : "Observation count scale"}
          </span>
        </div>

        {activeLayer === "signal" ? (
          <>
            <div
              className="mt-3 h-2.5 w-full rounded-full"
              style={{
                background:
                  "linear-gradient(to right, #B83B3B, #D97706, #C6A15B, #65A30D, #2E7D5B)",
              }}
            />

            <div className="mt-2.5 grid grid-cols-2 gap-2 sm:grid-cols-5">
              {[...SIGNAL_CLASSIFICATIONS].reverse().map((item) => (
                <div
                  key={item.tier}
                  className="flex items-center gap-2 rounded-lg border border-[#303640]/[0.08] bg-[#F7F6F2] px-2.5 py-2 text-[10px]"
                >
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full border border-[#303640]/50"
                    style={{ backgroundColor: item.color }}
                  />
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-[#303640]">
                      {item.label}
                    </p>
                    <p className="truncate text-[9px] tabular-nums text-[#5E6572]">
                      {item.rangeLabel}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <>
            <div
              className="mt-3 h-2.5 w-full rounded-full"
              style={{
                background: `linear-gradient(to right, ${DENSITY_COLORS.Low}, ${DENSITY_COLORS.Medium}, ${DENSITY_COLORS.High})`,
              }}
            />

            <div className="mt-2.5 grid grid-cols-1 gap-2 sm:grid-cols-3">
              <div className="flex items-center gap-2.5 rounded-lg border border-[#303640]/[0.08] bg-[#F7F6F2] px-2.5 py-2 text-[10px]">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full border border-[#303640]"
                  style={{ backgroundColor: DENSITY_COLORS.Low }}
                />
                <span className="font-semibold text-[#303640]">
                  Low (1–{DENSITY_THRESHOLDS.lowMax} obs)
                </span>
              </div>

              <div className="flex items-center gap-2.5 rounded-lg border border-[#303640]/[0.08] bg-[#F7F6F2] px-2.5 py-2 text-[10px]">
                <span
                  className="h-3.5 w-3.5 shrink-0 rounded-full border border-[#303640]"
                  style={{ backgroundColor: DENSITY_COLORS.Medium }}
                />
                <span className="font-semibold text-[#303640]">
                  Medium ({DENSITY_THRESHOLDS.lowMax + 1}–{DENSITY_THRESHOLDS.mediumMax} obs)
                </span>
              </div>

              <div className="flex items-center gap-2.5 rounded-lg border border-[#303640]/[0.08] bg-[#F7F6F2] px-2.5 py-2 text-[10px]">
                <span
                  className="h-4 w-4 shrink-0 rounded-full border border-[#303640]"
                  style={{ backgroundColor: DENSITY_COLORS.High }}
                />
                <span className="font-semibold text-[#303640]">
                  High ({DENSITY_THRESHOLDS.mediumMax + 1}+ obs)
                </span>
              </div>
            </div>
          </>
        )}

        <p className={`mt-3 rounded-xl border border-[#303640]/[0.08] bg-[#F1EFE9] px-3 py-2.5 text-[10px] leading-relaxed ${mutedText}`}>
          Thermal glow illustrates recorded measurements around each scanning
          device coordinate and does not verify coverage in unmeasured areas or
          identify physical router locations. GPS accuracy is an estimated
          uncertainty radius reported by the device, not guaranteed positional
          error.
        </p>
      </section>
    </div>
  );
}