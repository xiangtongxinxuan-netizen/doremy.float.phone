import assert from "node:assert/strict";
import test from "node:test";
import {
  estimateNeteasePosition,
  getNeteaseMediaState,
  isNotificationListenerEnabled,
  NETEASE_MEDIA_STATE_EVENT,
  parseNeteaseMediaState,
  subscribeNeteaseMediaState,
  type NeteaseMediaState,
} from "./native-music-bridge.ts";

const activeState: NeteaseMediaState = {
  availability: "active",
  sourcePackage: "com.netease.cloudmusic",
  sessionActive: true,
  playbackState: "PLAYING",
  title: "Song",
  artist: "Artist",
  album: "Album",
  durationMs: 10_000,
  basePositionMs: 1_000,
  positionUpdatedAtElapsedRealtimeMs: 2_000,
  speed: 1,
  estimatedPositionMs: 4_000,
  availableActions: 822,
  observedAtEpochMs: 1,
  revision: 2,
};

class WindowMock extends EventTarget {
  AndroidShell?: Window["AndroidShell"];
}

test("native music bridge", async (t) => {
  const originalWindow = globalThis.window;
  try {
    Object.defineProperty(globalThis, "window", { configurable: true, value: undefined });
    await t.test("returns unsupported without AndroidShell", async () => {
      assert.deepEqual(await isNotificationListenerEnabled(), { supported: false, reason: "unsupported" });
    });

    await t.test("parses a valid JSON state", () => {
      assert.deepEqual(parseNeteaseMediaState(JSON.stringify(activeState)), activeState);
      assert.equal(parseNeteaseMediaState("not-json"), null);
    });

    await t.test("reads state through the asynchronous shell bridge", async () => {
      const mock = new WindowMock();
      mock.AndroidShell = {
        isNotificationListenerEnabled: async () => true,
        getNeteaseMediaState: async () => JSON.stringify(activeState),
      };
      Object.defineProperty(globalThis, "window", { configurable: true, value: mock });
      assert.deepEqual(await getNeteaseMediaState(), { supported: true, value: activeState });
    });

    await t.test("subscribes and unsubscribes from state events", () => {
      const mock = new WindowMock();
      Object.defineProperty(globalThis, "window", { configurable: true, value: mock });
      const received: NeteaseMediaState[] = [];
      const unsubscribe = subscribeNeteaseMediaState((state) => received.push(state));
      const event = new Event(NETEASE_MEDIA_STATE_EVENT) as CustomEvent<NeteaseMediaState>;
      Object.defineProperty(event, "detail", { value: activeState });
      mock.dispatchEvent(event);
      unsubscribe();
      mock.dispatchEvent(event);
      assert.equal(received.length, 1);
    });

    await t.test("estimates and clamps progress", () => {
      assert.equal(estimateNeteasePosition(activeState, 2_000), 6_000);
      assert.equal(estimateNeteasePosition({ ...activeState, speed: 2 }, 2_000), 8_000);
      assert.equal(estimateNeteasePosition({ ...activeState, playbackState: "PAUSED" }, 2_000), 4_000);
      assert.equal(estimateNeteasePosition(activeState, 20_000), 10_000);
      assert.equal(estimateNeteasePosition({ ...activeState, durationMs: 0, estimatedPositionMs: -1 }, 0), 0);
    });
  } finally {
    Object.defineProperty(globalThis, "window", { configurable: true, value: originalWindow });
  }
});
