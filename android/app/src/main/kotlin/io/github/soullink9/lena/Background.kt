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
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Lena's notifications. Content stays hidden on a locked screen unless you allow it in Android's settings. */
object Notify {
    const val REPLIES = "replies"
    const val SCHEDULED = "scheduled"

    /**
     * Creates the notification channels (repeating this is harmless), so each kind can be silenced on its
     * own in Android's settings.
     */
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

    /**
     * Shows a notification that opens the chat. One per chat (its id is the chat's), so a newer answer
     * replaces the older one. On the lock screen only "Lena replied" shows, not the text.
     */
    fun show(c: Context, channel: String, title: String, text: String, chatId: String?) {
        if (!Compat.notificationsAllowed(c)) return
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
        /** One job per chat, so leaving the same chat twice doesn't follow it twice. */
        private fun name(chatId: String) = "reply-$chatId"

        /** Starts watching a chat's answer once there's a network. */
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

        /** Back in the chat before the answer finished: no notification needed. */
        fun cancel(c: Context, chatId: String) {
            WorkManager.getInstance(c).cancelUniqueWork(name(chatId))
        }
    }

    /**
     * Waits (up to nine minutes, inside WorkManager's ten-minute limit) for the answer to finish, then
     * notifies with its start. Signed out just stops; other failures retry twice.
     */
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
            Logs.w("Notifications", "Couldn't follow a reply: not signed in")
            Result.success()
        } catch (e: Exception) {
            Logs.w("Notifications", "Couldn't follow a reply (try ${runAttemptCount + 1})", e)
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}

/** Sends a scheduled prompt (to Open WebUI, or to Assist when it answers), waits for the answer and notifies you with it. */
class PromptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        /**
         * Runs a scheduled prompt as soon as there's a network. Expedited, so Android runs it promptly
         * instead of batching it for later, when its quota allows.
         */
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

    /**
     * Sends the prompt (into its named chat if it has one), waits for the answer and notifies you with it.
     * A schedule that was deleted or paused since the alarm was set is skipped. With Assist chosen in
     * Settings → Connections → Who answers, it goes to Assist instead.
     */
    override suspend fun doWork(): Result {
        val c = applicationContext
        val id = inputData.getString("schedule_id") ?: return Result.failure()
        val schedule = Prefs.schedules(c).firstOrNull { it.id == id && it.enabled } ?: return Result.success()
        if (Prefs.answers(c) == Answerer.ASSIST) return throughAssist(c, schedule)
        val api = Api.from(c) ?: return Result.failure()
        val title = schedule.title
        return try {
            val model = api.defaultModel(c) ?: return Result.success()
            // With a chat name, every run goes into that chat (made the first time), so several
            // prompts can share one history; otherwise each run is a chat of its own.
            val chatName = schedule.chat.trim()
            val existing = if (chatName.isEmpty()) null else api.chatTitled(chatName)?.id
            // Unless you chose to show it, the chat shows the prompt's name and her answer; the prompt
            // itself goes to her with this request only.
            val quiet = !schedule.showPrompt
            val started = api.ask(
                if (quiet) title else schedule.prompt, model, chatId = existing,
                newTitle = chatName.ifEmpty { if (quiet) title else null },
                contextTokens = Prefs.contextTokens(c),
                instruction = if (quiet) "This is \u201C$title\u201D, one of the prompts you run on a schedule; no one typed it just now. " +
                    "Your reply is what this asks for, as if you'd been asked directly: ${schedule.prompt}" else null,
            )
            var last = Reply("", false)
            api.follow(started.chatId, started.messageId).collect { last = it }
            // Belt and braces: if Open WebUI renamed the chat after answering, give it its name back,
            // so the next run finds it.
            val wanted = chatName.ifEmpty { if (quiet) title else "" }
            if (wanted.isNotEmpty()) {
                delay(3_000)
                runCatching {
                    if (api.chat(started.chatId).optString("title") != wanted) api.renameChat(started.chatId, wanted)
                }
            }
            val text = Markdown.plain(last.content).take(900).ifBlank { "Lena answered. Tap to read it." }
            Notify.show(c, Notify.SCHEDULED, title, text, started.chatId)
            Result.success()
        } catch (_: AuthException) {
            Logs.w("Scheduled prompts", "Couldn't run one: not signed in")
            Notify.show(c, Notify.SCHEDULED, title, "Open Lena and sign in again so scheduled prompts can run.", null)
            Result.success()
        } catch (e: Exception) {
            Logs.w("Scheduled prompts", "Couldn't run one (try ${runAttemptCount + 1})", e)
            if (runAttemptCount < 2) Result.retry()
            else {
                Notify.show(c, Notify.SCHEDULED, title, "Couldn't reach your server for this scheduled prompt.", null)
                Result.success()
            }
        }
    }

    /**
     * Sends the prompt to Home Assistant's Assist and notifies you with the start of its answer. The whole
     * answer is in Lena's own chat ([AssistLog]), which the notification opens. Each run asks on its own,
     * outside the ongoing conversation, and the schedule's chat name isn't used.
     */
    private suspend fun throughAssist(c: Context, schedule: Schedule): Result {
        val title = schedule.title
        val house = HomeAssistant.from(c)
        if (house == null) {
            Notify.show(c, Notify.SCHEDULED, title, "Set up Home Assistant in Lena's settings so scheduled prompts can go through Assist.", null)
            return Result.success()
        }
        return try {
            val answer = AssistChat.ask(c, schedule.prompt, label = "Scheduled · $title", conversation = false)
            val text = Markdown.plain(answer).take(900).ifBlank { "Assist didn't say anything." }
            Notify.show(c, Notify.SCHEDULED, title, text, null)
            Result.success()
        } catch (e: HaAuthException) {
            Logs.w("Scheduled prompts", "Couldn't run one through Assist: ${e.message}")
            Notify.show(c, Notify.SCHEDULED, title, e.explained("Home Assistant didn't accept Lena's token. Check Settings → Connections → Home Assistant."), null)
            Result.success()
        } catch (e: Exception) {
            Logs.w("Scheduled prompts", "Couldn't run one through Assist (try ${runAttemptCount + 1})", e)
            if (runAttemptCount < 2) Result.retry()
            else {
                Notify.show(c, Notify.SCHEDULED, title, "Couldn't reach Home Assistant for this scheduled prompt.", null)
                Result.success()
            }
        }
    }
}

/**
 * Alarms for scheduled prompts: each one's next time, set again after it runs. They're inexact
 * (Android may run them a few minutes late to save battery), which needs no special permission.
 */
object Schedules {
    private const val ACTION_FIRE = "io.github.soullink9.lena.SCHEDULE"

    /**
     * The alarm for one schedule. Its request code is the schedule's, so setting it again replaces it
     * rather than adding a second.
     */
    private fun pending(c: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        c,
        id.hashCode(),
        Intent(c, ScheduleReceiver::class.java).setAction(ACTION_FIRE).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Sets every schedule's next alarm, and cancels alarms for schedules that were deleted (their ids are
     * remembered from last time, since Android can't list an app's alarms).
     */
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
            val at = if (s.enabled && s.prompt.isNotBlank()) next(s) else null
            if (at != null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        prefs.edit().putStringSet("ids", current.map { it.id }.toSet()).apply()
    }

    /** When [s] runs next, after [from]; null if it won't again (a one-off that's past, or no days picked). */
    fun next(s: Schedule, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        /** That day at the schedule's minute and the given hour, in the phone's time zone. */
        fun at(day: java.time.LocalDate, hour: Int = s.hour) =
            day.atTime(hour, s.minute).atZone(from.zone)
        val today = from.toLocalDate()
        val t: ZonedDateTime? = when (s.repeat) {
            Repeat.ONCE -> at(java.time.LocalDate.ofEpochDay(s.date)).takeIf { it.isAfter(from) }
            Repeat.HOURS -> {
                val step = s.everyHours.coerceIn(1, 12)
                (s.hour until 24 step step).map { at(today, it) }.firstOrNull { it.isAfter(from) }
                    ?: at(today.plusDays(1))
            }
            Repeat.MONTHLY -> (0L..12L).asSequence().map { m ->
                val month = java.time.YearMonth.from(today).plusMonths(m)
                at(month.atDay(minOf(s.dayOfMonth, month.lengthOfMonth())))
            }.firstOrNull { it.isAfter(from) }
            else -> {
                val days = when (s.repeat) {
                    Repeat.WEEKDAYS -> setOf(1, 2, 3, 4, 5)
                    Repeat.WEEKENDS -> setOf(6, 7)
                    Repeat.DAYS -> s.days
                    else -> (1..7).toSet()
                }
                (0L..7L).asSequence().map { today.plusDays(it) }
                    .filter { it.dayOfWeek.value in days }
                    .map { at(it) }.firstOrNull { it.isAfter(from) }
            }
        }
        return t?.toInstant()?.toEpochMilli()
    }

    /** Whether an intent is one of these alarms going off. */
    fun isFire(intent: Intent) = intent.action == ACTION_FIRE
}

/** Receives the alarms: runs the prompt and sets the schedule's next time. */
class ScheduleReceiver : BroadcastReceiver() {
    /** A schedule's alarm went off. */
    override fun onReceive(context: Context, intent: Intent) {
        if (Schedules.isFire(intent)) intent.getStringExtra("id")?.let { PromptWorker.enqueue(context, it) }
        Schedules.rescheduleAll(context) // and set tomorrow's
    }
}

/** Alarms don't survive a restart or an update; this sets them again. */
/** Redraws the widget every half hour while it shows her mood, so it keeps up with her day. */
class WidgetMoodWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val NAME = "widget-mood"

        /** Starts or stops the refresh to match the "Her mood on the widget" setting. */
        fun sync(c: Context) {
            val wm = WorkManager.getInstance(c)
            if (Prefs.widgetMood(c) && HomeAssistant.from(c) != null) {
                wm.enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<WidgetMoodWorker>(30, TimeUnit.MINUTES).setConstraints(online).build(),
                )
            } else {
                wm.cancelUniqueWork(NAME)
            }
        }
    }

    /** Redraws the widget. */
    override suspend fun doWork(): Result {
        runCatching { LenaWidget().updateAll(applicationContext) }
        return Result.success()
    }
}

/**
 * After a restart or an update Android has forgotten the app's alarms, so they're set again; after an
 * update it also tidies away the downloaded APK.
 */
class BootReceiver : BroadcastReceiver() {
    /** The phone started or Lena was updated. */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Schedules.rescheduleAll(context)
            Timers.rearm(context)
        }
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updates.afterUpdate(context)
    }
}

/** Quick prompts on the app icon's long-press menu. */
object Shortcuts {
    /** The first three quick prompts on the app icon's long-press menu, kept in step with Settings. */
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
