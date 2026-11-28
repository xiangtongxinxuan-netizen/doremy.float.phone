export const NETEASE_MEDIA_STATE_EVENT = "dear-leon:netease-media-state" as const;

export type NeteaseMediaAvailability =
  | "active"
  | "idle"
  | "permission_required"
  | "listener_disconnected"
  | "listener_unavailable"
  | "unavailable"
  | "unsupported";

export interface NeteaseMediaState {
  availability: NeteaseMediaAvailability;
  sourcePackage: string;
  sessionActive: boolean;
  playbackState: string;
  title: string;
  artist: string;
  album: string;
  durationMs: number;
  basePositionMs: number;
  positionUpdatedAtElapsedRealtimeMs: number;
  speed: number;
  estimatedPositionMs: number;
  availableActions: number;
  observedAtEpochMs: number;
  revision: number;
  mediaId?: string;
  mediaUri?: string;
}

interface AndroidMusicShell {
  isNotificationListenerEnabled?: () => Promise<boolean>;
  openNotificationListenerSettings?: () => Promise<boolean>;
  getNeteaseMediaState?: () => Promise<string>;
}

declare global {
  interface Window {
    AndroidShell?: AndroidMusicShell;
  }
}

export type NativeMusicResult<T> =
  | { supported: true; value: T }
  | { supported: false; reason: "unsupported" | "invalid_state" };

function shell(): AndroidMusicShell | undefined {
  return typeof window === "undefined" ? undefined : window.AndroidShell;
}

function isMediaState(value: unknown): value is NeteaseMediaState {
  if (!value || typeof value !== "object") return false;
  const state = value as Partial<NeteaseMediaState>;
  return typeof state.availability === "string"
    && typeof state.sourcePackage === "string"
    && typeof state.sessionActive === "boolean"
    && typeof state.playbackState === "string"
    && typeof state.title === "string"
    && typeof state.artist === "string"
    && typeof state.album === "string"
    && typeof state.durationMs === "number"
    && typeof state.basePositionMs === "number"
    && typeof state.positionUpdatedAtElapsedRealtimeMs === "number"
    && typeof state.speed === "number"
    && typeof state.estimatedPositionMs === "number"
    && typeof state.availableActions === "number"
    && typeof state.observedAtEpochMs === "number"
    && typeof state.revision === "number";
}

export function hasNativeNeteaseMediaBridge(): boolean {
  const bridge = shell();
  return typeof bridge?.isNotificationListenerEnabled === "function"
    && typeof bridge.openNotificationListenerSettings === "function"
    && typeof bridge.getNeteaseMediaState === "function";
}

export async function isNotificationListenerEnabled(): Promise<NativeMusicResult<boolean>> {
  const bridge = shell();
  if (typeof bridge?.isNotificationListenerEnabled !== "function") {
    return { supported: false, reason: "unsupported" };
  }
  try {
    return { supported: true, value: Boolean(await bridge.isNotificationListenerEnabled()) };
  } catch {
    return { supported: false, reason: "invalid_state" };
  }
}

export async function openNotificationListenerSettings(): Promise<NativeMusicResult<true>> {
  const bridge = shell();
  if (typeof bridge?.openNotificationListenerSettings !== "function") {
    return { supported: false, reason: "unsupported" };
  }
  try {
    await bridge.openNotificationListenerSettings();
    return { supported: true, value: true };
  } catch {
    return { supported: false, reason: "invalid_state" };
  }
}

export function parseNeteaseMediaState(json: string): NeteaseMediaState | null {
  try {
    const value: unknown = JSON.parse(json);
    return isMediaState(value) ? value : null;
  } catch {
    return null;
  }
}

export async function getNeteaseMediaState(): Promise<NativeMusicResult<NeteaseMediaState>> {
  const bridge = shell();
  if (typeof bridge?.getNeteaseMediaState !== "function") {
    return { supported: false, reason: "unsupported" };
  }
  try {
    const state = parseNeteaseMediaState(await bridge.getNeteaseMediaState());
    return state
      ? { supported: true, value: state }
      : { supported: false, reason: "invalid_state" };
  } catch {
    return { supported: false, reason: "invalid_state" };
  }
}

export function subscribeNeteaseMediaState(
  listener: (state: NeteaseMediaState) => void,
): () => void {
  if (typeof window === "undefined") return () => undefined;
  const handler = (event: Event) => {
    const detail = (event as CustomEvent<unknown>).detail;
    if (isMediaState(detail)) listener(detail);
  };
  window.addEventListener(NETEASE_MEDIA_STATE_EVENT, handler);
  return () => window.removeEventListener(NETEASE_MEDIA_STATE_EVENT, handler);
}

export function estimateNeteasePosition(
  state: Pick<NeteaseMediaState, "playbackState" | "estimatedPositionMs" | "durationMs" | "speed">,
  elapsedSinceSnapshotMs: number,
): number {
  const elapsed = Number.isFinite(elapsedSinceSnapshotMs) ? Math.max(0, elapsedSinceSnapshotMs) : 0;
  const raw = state.playbackState === "PLAYING"
    ? state.estimatedPositionMs + elapsed * state.speed
    : state.estimatedPositionMs;
  const nonNegative = Math.max(0, Math.round(raw));
  return state.durationMs > 0 ? Math.min(nonNegative, state.durationMs) : nonNegative;
}
