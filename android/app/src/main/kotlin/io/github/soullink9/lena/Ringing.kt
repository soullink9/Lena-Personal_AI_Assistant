package io.github.soullink9.lena

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A timer or alarm ringing: the alarm sound on a loop (at the alarm volume), a notification with Stop
 * (and Snooze for an alarm) that opens [RingActivity] full screen, and that screen straight away when
 * Lena is on screen (the wall display). Saying "stop" on the ringing screen stops it too ([StopWord]). It
 * gives up by itself after [MAX_RING_MS].
 */
object Ringing {
    const val CHANNEL = "alarms"
    private const val MAX_RING_MS = 10 * 60_000L
    const val SNOOZE_MS = 10 * 60_000L

    private val ringing = MutableStateFlow<Countdown?>(null)

    /** What's ringing now, or null. */
    val now: StateFlow<Countdown?> = ringing

    /** How long it may ring before it stops by itself (a reminder's notice: a minute). */
    fun maxRingMs(item: Countdown?): Long = if (item?.kind == CountdownKind.REMINDER) REMINDER_MS else MAX_RING_MS

    /** One short chime, before a reminder is said. */
    fun chime(c: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(c, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                play()
            }
        }
    }

    /** How long a reminder's notification stays up, ringing once, before it goes. */
    private const val REMINDER_MS = 60_000L

    /**
     * Starts ringing for [item] (a newer one takes over from one already ringing). A reminder is said
     * instead: with Lena on screen (the wall display) she says it in the sheet after a chime; otherwise it
     * shows like a timer, with one chime rather than a ringing sound.
     */
    fun start(c: Context, item: Countdown) {
        if (item.kind == CountdownKind.REMINDER && AppVisible.now) {
            Logs.i("Timers", "Reminder: ${item.label}")
            chime(c)
            c.startActivity(AskActivity.announce(c, "Reminder: ${item.label}", listen = false))
            return
        }
        ringing.value = item
        Logs.i("Timers", "Ringing: ${item.name(c)}")
        c.startForegroundService(Intent(c, RingService::class.java))
    }

    /** Stops the ringing (the sound, the notification and the ringing screen). */
    fun stop(c: Context) {
        ringing.value = null
        // The service is ours and running on this same process, so it's told directly rather than through
        // Android, which may refuse to start a service while Lena is in the background.
        Handler(Looper.getMainLooper()).post { RingService.running?.finishRinging() }
    }

    /** Stops the ringing and rings again in ten minutes (an alarm's Snooze). */
    fun snooze(c: Context) {
        val item = ringing.value ?: return
        stop(c)
        Timers.addTimer(c, SNOOZE_MS, item.label.ifBlank { "Snoozed alarm" })
    }

    /** The channel: loud, on the lock screen, allowed through as an alarm. */
    fun channel(c: Context) {
        val channel = NotificationChannel(CHANNEL, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lena's timers and alarms when they ring"
            setSound(null, null) // the service plays the sound itself, looping
            setBypassDnd(true)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Plays the ringing and keeps its notification while it rings. */
class RingService : Service() {
    companion object {
        const val ACTION_STOP = "io.github.soullink9.lena.STOP_RINGING"
        const val ACTION_SNOOZE = "io.github.soullink9.lena.SNOOZE"
        private const val NOTIFICATION = 7001

        /** The service while it rings, so [Ringing.stop] can end it. */
        @Volatile
        var running: RingService? = null
            private set
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { Ringing.stop(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Ringing.stop(this)
                finishRinging()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                Ringing.snooze(this)
                return START_NOT_STICKY
            }
        }
        val item = Ringing.now.value
        if (item == null) {
            finishRinging()
            return START_NOT_STICKY
        }
        running = this
        Ringing.channel(this)
        startForeground(NOTIFICATION, notification(item), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (item.kind == CountdownKind.REMINDER) Ringing.chime(this) else play()
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, Ringing.maxRingMs(item))
        // On screen already (the wall display): the ringing screen comes up at once. Otherwise the
        // notification's full-screen intent brings it.
        if (AppVisible.now) startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return START_NOT_STICKY
    }

    /** The alarm sound on a loop, at the alarm volume. */
    private fun play() {
        if (player != null) return
        val sound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setDataSource(this@RingService, sound)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Logs.e("Timers", "Couldn't play the alarm sound", it) }.getOrNull()
    }

    private fun notification(item: Countdown): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(action: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, RingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Ringing.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lena)
            .setContentTitle(title(item.kind))
            .setContentText(item.name(this))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .addAction(0, "Stop", action(ACTION_STOP, 1))
            .apply { if (item.kind == CountdownKind.ALARM) addAction(0, "Snooze 10 min", action(ACTION_SNOOZE, 2)) }
            .build()
    }

    /** Silence, the notification gone, and the service ended. */
    fun finishRinging() {
        running = null
        handler.removeCallbacks(giveUp)
        player?.runCatching { stop(); release() }
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        finishRinging()
        super.onDestroy()
    }
}

/**
 * The ringing screen: what's ringing, big, with Stop (and Snooze for an alarm). Shows over the lock
 * screen and turns the screen on; closes by itself once the ringing stops.
 */
class RingActivity : ComponentActivity() {

    // Dark on a TV (Tv.darkOnTv).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        Tv.darkOnTv(this, newBase)
    }

    private var stopWord: WakeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LenaTheme {
                val item by Ringing.now.collectAsState()
                LaunchedEffect(item) { if (item == null) finish() }
                val ringing = item ?: return@LenaTheme
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                    ) {
                        Icon(
                            countdownIcon(ringing.kind), null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp),
                        )
                        Text(title(ringing.kind), style = MaterialTheme.typography.displaySmall)
                        Text(if (ringing.kind == CountdownKind.REMINDER) ringing.label else ringing.name(this@RingActivity),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { Ringing.stop(this@RingActivity) }, modifier = Modifier.widthIn(min = 240.dp).height(64.dp)) {
                            Text(if (ringing.kind == CountdownKind.REMINDER) "Done" else "Stop", style = MaterialTheme.typography.titleLarge)
                        }
                        if (ringing.kind == CountdownKind.ALARM) {
                            OutlinedButton(onClick = { Ringing.snooze(this@RingActivity) }, modifier = Modifier.widthIn(min = 240.dp)) {
                                Icon(Icons.Outlined.Snooze, null)
                                Text("  Snooze 10 minutes")
                            }
                        }
                        if (StopWord.available(this@RingActivity)) Text("Or say “stop”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }

    /**
     * Listens for "stop" while this screen shows. The screen behind it lets go of the microphone as it stops
     * listening for her name, so this waits a moment for that first.
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            withTimeoutOrNull(1_000) { while (WakeListener.micsOpen > 0) delay(50) }
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@launch
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@launch
            stopWord = StopWord.listener(this@RingActivity) { Ringing.stop(this@RingActivity) }.also { it.start() }
        }
    }

    override fun onPause() {
        stopWord?.stop()
        stopWord = null
        super.onPause()
    }
}

/** What the ringing screen and notification say at the top. */
private fun title(kind: CountdownKind) = when (kind) {
    CountdownKind.TIMER -> "Time's up"
    CountdownKind.ALARM -> "Alarm"
    CountdownKind.REMINDER -> "Reminder"
}
