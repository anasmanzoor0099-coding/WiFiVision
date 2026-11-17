export interface PairingCodeDisplayState {
  code: string;
  expiresAtMs: number;
}

export function getRemainingPairingSeconds(
  expiresAtIso: string | number | null | undefined,
  nowMs = Date.now()
): number {
  if (expiresAtIso === null || expiresAtIso === undefined) return 0;
  const targetMs =
    typeof expiresAtIso === "number"
      ? expiresAtIso
      : Date.parse(expiresAtIso);
  if (!Number.isFinite(targetMs)) return 0;
  return Math.max(0, Math.ceil((targetMs - nowMs) / 1000));
}

export function isPairingCodeExpired(
  expiresAtIso: string | number | null | undefined,
  nowMs = Date.now()
): boolean {
  return getRemainingPairingSeconds(expiresAtIso, nowMs) <= 0;
}

export function formatPairingCountdown(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${mins}:${String(secs).padStart(2, "0")}`;
}
