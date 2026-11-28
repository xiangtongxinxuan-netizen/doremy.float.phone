package app.floatphone.shell.media

import android.media.session.PlaybackState
import org.json.JSONObject
import kotlin.math.roundToLong

data class MediaState(
    val availability: String,
    val sourcePackage: String,
    val sessionActive: Boolean,
    val playbackState: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val basePositionMs: Long,
    val positionUpdatedAtElapsedRealtimeMs: Long,
    val speed: Float,
    val availableActions: Long,
    val observedAtEpochMs: Long,
    val revision: Long,
    val mediaId: String? = null,
    val mediaUri: String? = null,
) {
    fun estimatedPositionAt(elapsedRealtimeMs: Long): Long {
        val estimate = if (playbackState == "PLAYING") {
            basePositionMs +
                ((elapsedRealtimeMs - positionUpdatedAtElapsedRealtimeMs).coerceAtLeast(0L) * speed)
                    .roundToLong()
        } else {
            basePositionMs
        }
        val nonNegative = estimate.coerceAtLeast(0L)
        return if (durationMs > 0L) nonNegative.coerceAtMost(durationMs) else nonNegative
    }

    fun toJson(elapsedRealtimeMs: Long): String = JSONObject().apply {
        put("availability", availability)
        put("sourcePackage", sourcePackage)
        put("sessionActive", sessionActive)
        put("playbackState", playbackState)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("durationMs", durationMs)
        put("basePositionMs", basePositionMs)
        put("positionUpdatedAtElapsedRealtimeMs", positionUpdatedAtElapsedRealtimeMs)
        put("speed", speed.toDouble())
        put("estimatedPositionMs", estimatedPositionAt(elapsedRealtimeMs))
        put("availableActions", availableActions)
        put("observedAtEpochMs", observedAtEpochMs)
        put("revision", revision)
        mediaId?.takeIf { it.isNotBlank() }?.let { put("mediaId", it) }
        mediaUri?.takeIf { it.isNotBlank() }?.let { put("mediaUri", it) }
    }.toString()

    fun withRevision(nextRevision: Long): MediaState = copy(revision = nextRevision)

    companion object {
        const val NETEASE_PACKAGE = "com.netease.cloudmusic"

        fun idle(availability: String = "idle", nowEpochMs: Long = System.currentTimeMillis()) = MediaState(
            availability = availability,
            sourcePackage = NETEASE_PACKAGE,
            sessionActive = false,
            playbackState = "NONE",
            title = "",
            artist = "",
            album = "",
            durationMs = 0L,
            basePositionMs = 0L,
            positionUpdatedAtElapsedRealtimeMs = 0L,
            speed = 0f,
            availableActions = 0L,
            observedAtEpochMs = nowEpochMs,
            revision = 0L,
        )

        fun playbackStateName(state: Int): String = when (state) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_CONNECTING -> "CONNECTING"
            PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
            PlaybackState.STATE_REWINDING -> "REWINDING"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
            PlaybackState.STATE_ERROR -> "ERROR"
            else -> "NONE"
        }
    }
}
