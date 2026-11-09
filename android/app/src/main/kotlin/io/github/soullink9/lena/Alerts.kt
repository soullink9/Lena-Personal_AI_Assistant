package io.github.soullink9.lena

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The backup for house alerts: every 15 minutes (Android's shortest background interval for an
 * app without Google's push service) this phone asks the Lena integration whether she's worried,
 * and tells you once per worry. For instant alerts, the integration sends them itself through the
 * Home Assistant companion app; this is for phones without it.
 */
object Alerts {
    private const val CHANNEL = "alerts"
    private const val NAME = "house-alerts"
    private const val ID = 7_001 // one alert at a time; the all-clear replaces the worry

    /** Starts or stops the checks to match the setting. */
    fun sync(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (Prefs.alerts(c) && HomeAssistant.from(c) != null) {
            wm.enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(NAME)
            Prefs.setLastAlert(c, null)
        }
    }

    /** Looks once, and notifies if something new is wrong, or if what was wrong is sorted. */
    suspend fun check(c: Context) {
        val house = HomeAssistant.from(c) ?: return
        val f = house.feelings() ?: return
        val last = Prefs.lastAlert(c)
        when {
            f.moment == "Worried" && f.momentWhy.isNotBlank() && f.momentWhy != last -> {
                show(c, "Lena's worried", f.momentWhy, urgent = true)
                Logs.i("Alerts", "Told you she's worried: ${f.momentWhy}")
                Prefs.setLastAlert(c, f.momentWhy)
            }
            f.moment != "Worried" && last != null -> {
                if (f.moment == "Relieved" && f.momentWhy.isNotBlank()) show(c, "All clear", f.momentWhy, urgent = false)
                else c.getSystemService(NotificationManager::class.java).cancel(ID)
                Prefs.setLastAlert(c, null)
            }
        }
    }

    /**
     * Shows the alert on its own high-importance channel, so it can be loud while other Lena notifications
     * stay quiet. Tapping it opens Lena listening.
     */
    private fun show(c: Context, title: String, text: String, urgent: Boolean) {
        if (!Compat.notificationsAllowed(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "House alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "When Lena's worried about something at home" }
        )
        // Tap to talk to her about it.
        val tap = PendingIntent.getActivity(
            c, ID, AskActivity.intent(c, talk = true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(c.getColor(R.color.lena_cyan))
            .setLargeIcon(Icon.createWithResource(c, R.drawable.lena_avatar))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(if (urgent) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        nm.notify(ID, n)
    }
}

/** Checks Home Assistant for a worry every 15 minutes (the shortest period Android allows). */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** Checks once. A failure is logged and the next run tries again, so it never retries in a tight loop. */
    override suspend fun doWork(): Result {
        runCatching { Alerts.check(applicationContext) }.onFailure { Logs.w("Alerts", "Couldn't check Home Assistant", it) }
        return Result.success()
    }
}
