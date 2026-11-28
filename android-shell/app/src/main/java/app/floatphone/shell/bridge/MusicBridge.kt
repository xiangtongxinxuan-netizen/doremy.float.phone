package app.floatphone.shell.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import app.floatphone.shell.media.MediaState
import app.floatphone.shell.media.MediaStateStore
import org.json.JSONObject

class MusicBridge(private val context: Context) {
    fun isNotificationListenerEnabled(): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

    fun openNotificationListenerSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun getNeteaseMediaState(): String = runCatching {
        if (isNotificationListenerEnabled()) {
            MediaStateStore.snapshotJson()
        } else {
            MediaState.idle("permission_required").toJson(android.os.SystemClock.elapsedRealtime())
        }
    }.getOrElse {
        MediaState.idle("unavailable").toJson(android.os.SystemClock.elapsedRealtime())
    }

    fun onMessage(message: WebMessageCompat, isMainFrame: Boolean, reply: JavaScriptReplyProxy) {
        if (!isMainFrame) return
        val rawMessage = message.data ?: return
        val request = runCatching { JSONObject(rawMessage) }.getOrNull() ?: return
        val id = request.optString("id")
        if (id.isBlank()) return
        val response = JSONObject().put("id", id)
        runCatching {
            when (request.optString("action")) {
                "isNotificationListenerEnabled" -> isNotificationListenerEnabled()
                "openNotificationListenerSettings" -> {
                    openNotificationListenerSettings()
                    true
                }
                "getNeteaseMediaState" -> getNeteaseMediaState()
                else -> error("unsupported_action")
            }
        }.onSuccess { result ->
            response.put("ok", true).put("result", result)
        }.onFailure {
            response.put("ok", false).put("error", "native_bridge_error")
        }
        reply.postMessage(response.toString())
    }
}
