package io.github.soullink9.lena

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/** Lena's notifications. Content stays hidden on a locked screen unless you allow it in Android's settings. */
object Notify {
    const val REPLIES = "replies"
    const val SCHEDULED = "scheduled"

    private fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(REPLIES, "Finished replies", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "When Lena finishes a reply you left" }
        )
        nm.createNotificationChannel(
            NotificationChannel(SCHEDULED, "Scheduled prompts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Answers to your scheduled prompts" }
        )
    }

    fun show(c: Context, channel: String, title: String, text: String, chatId: String?) {
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channels(c)
        val id = (chatId ?: title).hashCode()
        val open = Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHAT)
            .putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(c.getColor(R.color.lena_cyan))
            .setContentTitle("Lena replied")
            .build()
        val n = Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(c.getColor(R.color.lena_cyan))
            .setLargeIcon(Icon.createWithResource(c, R.drawable.lena_avatar))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Waits for a reply you left to finish, then notifies you with its start. */
class ReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private fun name(chatId: String) = "reply-$chatId"

        fun enqueue(c: Context, chatId: String) {
            WorkManager.getInstance(c).enqueueUniqueWork(
                name(chatId),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ReplyWorker>()
                    .setInputData(workDataOf("chat_id" to chatId))
                    .setConstraints(online)
                    .build(),
            )
        }

        fun cancel(c: Context, chatId: String) {
            WorkManager.getInstance(c).cancelUniqueWork(name(chatId))
        }
    }

    override suspend fun doWork(): Result {
        val chatId = inputData.getString("chat_id") ?: return Result.failure()
        val api = Api.from(applicationContext) ?: return Result.failure()
        val deadline = System.currentTimeMillis() + 9 * 60_000
        return try {
            while (api.runningTasks(chatId) > 0) {
                if (System.currentTimeMillis() > deadline) return Result.success()
                delay(3000)
            }
            val (title, text) = api.latest(chatId)
            Notify.show(applicationContext, Notify.REPLIES, title, text.take(600).ifBlank { "Your reply is ready." }, chatId)
            Result.success()
        } catch (_: AuthException) {
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}

/** Sends a scheduled prompt, waits for the answer and notifies you with it. */
class PromptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        fun enqueue(c: Context, scheduleId: String) {
            WorkManager.getInstance(c).enqueueUniqueWork(
                "schedule-$scheduleId",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PromptWorker>()
                    .setInputData(workDataOf("schedule_id" to scheduleId))
                    .setConstraints(online)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val c = applicationContext
        val id = inputData.getString("schedule_id") ?: return Result.failure()
        val schedule = Prefs.schedules(c).firstOrNull { it.id == id && it.enabled } ?: return Result.success()
        val api = Api.from(c) ?: return Result.failure()
        val title = schedule.prompt.lineSequence().first().take(60)
        return try {
            val model = api.defaultModel(c) ?: return Result.success()
            val started = api.ask(schedule.prompt, model)
            var last = Reply("", false)
            api.follow(started.chatId, started.messageId).collect { last = it }
            val text = Markdown.plain(last.content).take(900).ifBlank { "Lena answered. Tap to read it." }
            Notify.show(c, Notify.SCHEDULED, title, text, started.chatId)
            Result.success()
        } catch (_: AuthException) {
            Notify.show(c, Notify.SCHEDULED, title, "Open Lena and sign in again so scheduled prompts can run.", null)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry()
            else {
                Notify.show(c, Notify.SCHEDULED, title, "Couldn't reach your server for this scheduled prompt.", null)
                Result.success()
            }
        }
    }
}

/**
 * Daily alarms for scheduled prompts. They're inexact (Android may run them a few minutes late
 * to save battery), which needs no special permission.
 */
object Schedules {
    private const val ACTION_FIRE = "io.github.soullink9.lena.SCHEDULE"

    private fun pending(c: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        c,
        id.hashCode(),
        Intent(c, ScheduleReceiver::class.java).setAction(ACTION_FIRE).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun rescheduleAll(c: Context) {
        val alarms = c.getSystemService(AlarmManager::class.java)
        val prefs = c.getSharedPreferences("lena_alarms", Context.MODE_PRIVATE)
        val current = Prefs.schedules(c)
        // Cancel alarms for schedules that were removed.
        prefs.getStringSet("ids", emptySet())!!.filter { id -> current.none { it.id == id } }
            .forEach { alarms.cancel(pending(c, it)) }
        current.forEach { s ->
            val pi = pending(c, s.id)
            alarms.cancel(pi)
            if (s.enabled && s.prompt.isNotBlank()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(s), pi)
        }
        prefs.edit().putStringSet("ids", current.map { it.id }.toSet()).apply()
    }

    private fun next(s: Schedule): Long {
        val now = ZonedDateTime.now()
        var t = now.withHour(s.hour).withMinute(s.minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    fun isFire(intent: Intent) = intent.action == ACTION_FIRE
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Schedules.isFire(intent)) intent.getStringExtra("id")?.let { PromptWorker.enqueue(context, it) }
        Schedules.rescheduleAll(context) // and set tomorrow's
    }
}

/** Alarms don't survive a restart or an update; this sets them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Schedules.rescheduleAll(context)
        }
    }
}

/** Quick prompts on the app icon's long-press menu. */
object Shortcuts {
    fun update(c: Context) {
        val list = Prefs.quickPrompts(c).take(3).mapIndexed { i, p ->
            ShortcutInfo.Builder(c, "prompt-$i")
                .setShortLabel(p.take(24))
                .setLongLabel(p.take(48))
                .setIcon(Icon.createWithResource(c, R.drawable.ic_shortcut_ask))
                .setIntent(AskActivity.intent(c, p))
                .build()
        }
        c.getSystemService(ShortcutManager::class.java).dynamicShortcuts = list
    }
}
