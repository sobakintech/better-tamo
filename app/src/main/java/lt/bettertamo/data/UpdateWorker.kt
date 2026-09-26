package lt.bettertamo.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import lt.bettertamo.MainActivity
import lt.bettertamo.R
import java.util.concurrent.TimeUnit

object SchoolUpdates {
    private const val WORK = "school-updates"
    private const val CHANNEL = "school-updates"
    private const val MESSAGES = "school-messages"
    private fun prefs(context: Context) = context.getSharedPreferences("school-updates", Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean("enabled", enabled) }
        val work = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else work.cancelUniqueWork(WORK)
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    private fun <T> unseen(context: Context, key: String, items: List<T>, id: (T) -> String): List<T> {
        val prefs = prefs(context)
        val known = prefs.getStringSet(key, null)
        val ids = items.map(id).toSet()
        prefs.edit { putStringSet(key, ((known ?: emptySet()) + ids).toList().takeLast(500).toSet()) }
        return if (known == null) emptyList() else items.filter { id(it) !in known }
    }

    internal fun unseen(context: Context, scope: String, notices: List<SchoolNotice>): List<SchoolNotice> = unseen(context, "seen:$scope", notices) { it.id }
    internal fun unseenMessages(context: Context, scope: String, messages: List<MessageHeader>): List<MessageHeader> = unseen(context, "messages:$scope", messages.filter { !it.read }) { it.sid }

    fun markMessagesSeen(context: Context, scope: String, messages: List<MessageHeader>) {
        if (enabled(context)) unseenMessages(context, scope, messages)
    }

    internal fun notifyMessages(context: Context, messages: List<MessageHeader>) {
        if (messages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(MESSAGES, "Nauji pranešimai", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN_MESSAGES, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val compat = NotificationManagerCompat.from(context)
        messages.take(5).forEach { message ->
            val notification = NotificationCompat.Builder(context, MESSAGES).setSmallIcon(R.drawable.ic_notification).setContentTitle(message.person.ifBlank { "Naujas pranešimas" })
                .setContentText(message.subject).setContentIntent(open).setAutoCancel(true).setGroup(MESSAGES).build()
            runCatching { compat.notify(message.sid.hashCode(), notification) }
        }
    }

    fun markSeen(context: Context, scope: String, notices: List<SchoolNotice>) {
        if (enabled(context)) unseen(context, scope, notices)
    }

    internal fun notify(context: Context, notices: List<SchoolNotice>) {
        if (notices.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Nauji pažymiai ir įrašai", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN_FEED, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val compat = NotificationManagerCompat.from(context)
        notices.take(5).forEach { notice ->
            val (title, text) = describe(notice)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setGroup(CHANNEL).build()
            runCatching { compat.notify(notice.id.hashCode(), notification) }
        }
    }

    fun describe(notice: SchoolNotice): Pair<String, String> = when (notice.kind) {
        NoticeKind.GRADE -> "Naujas pažymys: ${notice.value}" to notice.subject
        NoticeKind.FORMATIVE -> "Naujas kaupiamasis: ${notice.value}" to listOf(notice.subject, notice.text).filter { it.isNotBlank() }.joinToString(" · ")
        NoticeKind.PRAISE -> "Pagyrimas · ${notice.subject}" to notice.text
        NoticeKind.REMARK -> "Pastaba · ${notice.subject}" to notice.text
        NoticeKind.COMMENT -> "Komentaras · ${notice.subject}" to notice.text
        NoticeKind.HOMEWORK -> "Namų darbai · ${notice.subject}" to notice.text
        NoticeKind.OTHER -> notice.subject.ifBlank { "Naujas įrašas" } to notice.text
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!SchoolUpdates.enabled(applicationContext)) return Result.success()
        val session = runCatching { SessionVault(applicationContext).read() }.getOrNull()?.takeIf { it.selectedRole != null } ?: return Result.success()
        if (TamoPush.due(applicationContext)) {
            val pushed = try { TamoPush.register(applicationContext, session); true }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { TamoPush.active(applicationContext) }
            if (pushed) return Result.success()
        }
        return try {
            val api = TamoApi()
            runCatching { api.messages(session, MessageFolder.RECEIVED, 1, "") }.getOrNull()?.let {
                SchoolUpdates.notifyMessages(applicationContext, SchoolUpdates.unseenMessages(applicationContext, session.scope, it))
            }
            val notices = api.notices(session, false, java.time.YearMonth.now())
            SchoolUpdates.notify(applicationContext, SchoolUpdates.unseen(applicationContext, session.scope, notices))
            Result.success()
        } catch (e: TamoFailure) {
            if (e.expired) Result.success() else Result.retry()
        }
    }
}
