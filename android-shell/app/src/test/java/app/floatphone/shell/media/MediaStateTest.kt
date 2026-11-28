package app.floatphone.shell.media

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MediaStateTest {
    private fun state(
        playbackState: String = "PLAYING",
        durationMs: Long = 10_000L,
        basePositionMs: Long = 1_000L,
        updatedAtMs: Long = 2_000L,
        speed: Float = 1f,
    ) = MediaState(
        availability = "active",
        sourcePackage = MediaState.NETEASE_PACKAGE,
        sessionActive = true,
        playbackState = playbackState,
        title = "",
        artist = "",
        album = "",
        durationMs = durationMs,
        basePositionMs = basePositionMs,
        positionUpdatedAtElapsedRealtimeMs = updatedAtMs,
        speed = speed,
        availableActions = 0L,
        observedAtEpochMs = 1L,
        revision = 1L,
    )

    @Test fun playingPositionGrows() = assertEquals(4_000L, state().estimatedPositionAt(5_000L))

    @Test fun pausedPositionDoesNotGrow() =
        assertEquals(1_000L, state(playbackState = "PAUSED").estimatedPositionAt(5_000L))

    @Test fun speedIsApplied() =
        assertEquals(7_000L, state(speed = 2f).estimatedPositionAt(5_000L))

    @Test fun durationClampsPosition() =
        assertEquals(2_000L, state(durationMs = 2_000L).estimatedPositionAt(5_000L))

    @Test fun missingDurationOnlyClampsToZero() =
        assertEquals(0L, state(durationMs = 0L, basePositionMs = -5L).estimatedPositionAt(1_000L))

    @Test fun emptyMetadataSerializesAsEmptyStrings() {
        val json = JSONObject(state().toJson(2_000L))
        assertEquals("", json.getString("title"))
        assertEquals("", json.getString("artist"))
        assertFalse(json.has("mediaId"))
    }

    @Test fun idleStateIsStrictJson() {
        val json = JSONObject(MediaState.idle().toJson(0L))
        assertEquals("idle", json.getString("availability"))
        assertFalse(json.getBoolean("sessionActive"))
        assertEquals("NONE", json.getString("playbackState"))
    }
}
