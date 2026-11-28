package app.floatphone.shell.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

class MediaSessionObserver(
    context: Context,
    private val notificationListener: ComponentName,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var started = false

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener {
        refresh(it.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish(controller, metadata)

        override fun onPlaybackStateChanged(state: PlaybackState?) = publish(controller, playbackState = state)

        override fun onSessionDestroyed() {
            refreshActiveSessions()
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            manager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                notificationListener,
                mainHandler,
            )
            refreshActiveSessions()
        }.onFailure {
            MediaStateStore.setIdle("listener_unavailable")
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        replaceController(null)
        MediaStateStore.setIdle("listener_disconnected")
    }

    private fun refreshActiveSessions() {
        val sessions = runCatching { manager.getActiveSessions(notificationListener) }
            .getOrElse {
                MediaStateStore.setIdle("listener_unavailable")
                emptyList()
            }
        refresh(sessions)
    }

    private fun refresh(sessions: List<MediaController>) {
        val selected = sessions
            .filter { it.packageName == MediaState.NETEASE_PACKAGE }
            .maxByOrNull(::sessionPriority)
        replaceController(selected)
        if (selected == null) MediaStateStore.setIdle("idle") else publish(selected)
    }

    private fun sessionPriority(candidate: MediaController): Int = when (candidate.playbackState?.state) {
        PlaybackState.STATE_PLAYING -> 3
        PlaybackState.STATE_PAUSED -> 2
        else -> 1
    }

    private fun replaceController(next: MediaController?) {
        if (controller?.sessionToken == next?.sessionToken) return
        controller?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        controller = next
        next?.registerCallback(controllerCallback, mainHandler)
    }

    private fun publish(
        target: MediaController?,
        metadata: MediaMetadata? = target?.metadata,
        playbackState: PlaybackState? = target?.playbackState,
    ) {
        if (!started || target == null || target.sessionToken != controller?.sessionToken) return
        MediaStateStore.update(
            MediaState(
                availability = "active",
                sourcePackage = target.packageName,
                sessionActive = true,
                playbackState = MediaState.playbackStateName(playbackState?.state ?: PlaybackState.STATE_NONE),
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
                basePositionMs = playbackState?.position?.coerceAtLeast(0L) ?: 0L,
                positionUpdatedAtElapsedRealtimeMs = playbackState?.lastPositionUpdateTime?.coerceAtLeast(0L) ?: 0L,
                speed = playbackState?.playbackSpeed ?: 0f,
                availableActions = playbackState?.actions ?: 0L,
                observedAtEpochMs = System.currentTimeMillis(),
                revision = 0L,
                mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() },
                mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)?.takeIf { it.isNotBlank() },
            ),
        )
    }
}
