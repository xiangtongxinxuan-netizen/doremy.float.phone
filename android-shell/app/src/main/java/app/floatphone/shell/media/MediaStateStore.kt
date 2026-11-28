package app.floatphone.shell.media

import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

object MediaStateStore {
    fun interface Listener {
        fun onMediaStateChanged(state: MediaState)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val revision = AtomicLong(0L)

    @Volatile
    private var current = MediaState.idle("listener_disconnected")

    fun snapshot(): MediaState = current

    fun snapshotJson(): String = current.toJson(SystemClock.elapsedRealtime())

    fun update(state: MediaState) {
        val revised = state.withRevision(revision.incrementAndGet())
        current = revised
        listeners.forEach { listener -> runCatching { listener.onMediaStateChanged(revised) } }
    }

    fun setIdle(availability: String) {
        update(MediaState.idle(availability))
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
