package lt.bettertamo.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.tasks.Task
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import lt.bettertamo.MainActivity
import lt.bettertamo.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Registers this app's FCM token (in TAMO's Firebase project) with TAMO, the same way the official app does. */
object TamoPush {
    const val CHANNEL = "tamo-push"
    const val EXTRA = "tamo_push"
    private const val DAY = 24 * 60 * 60 * 1000L
    private fun prefs(context: Context) = context.getSharedPreferences("tamo-push", Context.MODE_PRIVATE)

    fun active(context: Context) = prefs(context).getString("token", null) != null

    /** Whether registration should be tried now; failed attempts are retried at most once a day. */
    fun due(context: Context) = active(context) || System.currentTimeMillis() - prefs(context).getLong("attempt", 0) > DAY

    suspend fun register(context: Context, session: SchoolSession, force: Boolean = false) {
        val prefs = prefs(context)
        prefs.edit { putLong("attempt", System.currentTimeMillis()) }
        channel(context)
        val (installation, token) = withTimeoutOrNull(30_000) {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            FirebaseInstallations.getInstance().id.await() to messaging.token.await()
        } ?: throw TamoFailure("Nepavyko gauti Firebase prieigos rakto.")
        val current = prefs.getString("token", null) == token && prefs.getString("installation", null) == installation && prefs.getString("scope", null) == session.scope
        if (!force && current && System.currentTimeMillis() - prefs.getLong("registered", 0) < DAY) return
        TamoApi().registerDevice(session, installation, token)
        prefs.edit { putString("token", token); putString("installation", installation); putString("scope", session.scope); putLong("registered", System.currentTimeMillis()) }
    }

    suspend fun unregister(context: Context, session: SchoolSession?) {
        val prefs = prefs(context)
        val installation = prefs.getString("installation", null)
        val used = prefs.contains("attempt")
        prefs.edit { clear() }
        if (installation != null && session != null) runCatching { TamoApi().unregisterDevice(session, installation) }
        if (used) runCatching {
            withTimeoutOrNull(15_000) { FirebaseMessaging.getInstance().apply { isAutoInitEnabled = false }.deleteToken().await() }
        }
    }

    /** Queues a registration after Firebase rotates the token. */
    fun refresh(context: Context) {
        prefs(context).edit { remove("registered") }
        val request = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("push-refresh", ExistingWorkPolicy.REPLACE, request)
    }

    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "TAMO pranešimai", NotificationManager.IMPORTANCE_HIGH))
    }

    fun show(context: Context, message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"] ?: message.data["message"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channel(context)
        val id = (message.messageId ?: message.data["id"] ?: System.nanoTime().toString()).hashCode()
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        message.data.forEach { (key, value) -> intent.putExtra(key, value) }
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.orEmpty().ifBlank { "TAMO" }).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Tab to open for a tapped push: 3 for messages, 2 for the feed, null if the intent isn't a push. */
    fun destination(extras: Bundle?): Int? {
        if (extras == null || !(extras.getBoolean(EXTRA) || extras.containsKey("google.message_id"))) return null
        return if (extras.containsKey("messagingFolder") || extras.containsKey("messageTypeId")) 3 else 2
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
        }
    }
}

class TamoPushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (SchoolUpdates.enabled(this) && TamoPush.active(this)) TamoPush.refresh(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (SchoolUpdates.enabled(this)) TamoPush.show(this, message)
    }
}
