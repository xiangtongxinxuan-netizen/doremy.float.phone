package app.floatphone.shell.media

import android.content.ComponentName
import android.service.notification.NotificationListenerService

class NeteaseNotificationListenerService : NotificationListenerService() {
    private var observer: MediaSessionObserver? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        observer?.stop()
        observer = MediaSessionObserver(
            applicationContext,
            ComponentName(this, NeteaseNotificationListenerService::class.java),
        ).also { it.start() }
    }

    override fun onListenerDisconnected() {
        cleanup()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        observer?.stop()
        observer = null
    }
}
