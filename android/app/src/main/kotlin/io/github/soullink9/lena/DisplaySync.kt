package io.github.soullink9.lena

import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * This display and the Lena integration keeping each other up to date: what she's been asked to do here
 * by her tools (timers, alarms, reminders, the volume, announcements from another room or from
 * lena.announce), and what's set here, so she can say how long is left or cancel the right one.
 *
 * A wall display syncs every few seconds while one of Lena's screens is showing ([watch]); any device
 * syncs right after each answer ([now]), which is when her tools have just run.
 *
 * On a wall display it also watches the Speaking first sensor: someone coming into the room asks the
 * integration whether there's something to say (the morning's briefing, a worry not yet mentioned here),
 * and she says it.
 */
object DisplaySync {
    private const val PREFS = "lena_room"
    private const val POLL_MS = 3_000L
    private const val AFTER_FAILURE_MS = 30_000L
    private const val NOTIFICATION = 7101
    private val lock = Mutex()

    /** While [a] is showing in dashboard mode: syncs every few seconds and watches the Speaking first sensor. */
    fun watch(a: ComponentActivity, from: AskActivity.From? = null) {
        a.lifecycleScope.launch {
            a.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!DashboardActivity.isHomeScreen(a)) return@repeatOnLifecycle
                var sensorWas: Boolean? = null
                while (true) {
                    val ok = runCatching { sync(a, from) }.isSuccess
                    sensorWas = runCatching { watchSensor(a, sensorWas) }.getOrDefault(sensorWas)
                    delay(if (ok) POLL_MS else AFTER_FAILURE_MS)
                }
            }
        }
    }

    /** Syncs once now (after an answer); quietly does nothing without Home Assistant or the integration. */
    suspend fun now(c: Context) {
        runCatching { sync(c, null) }
    }

    /**
     * Reports what's set here and does what's waiting. Throws when Home Assistant can't be reached. Only
     * one sync at a time, so a command is never done twice.
     */
    private suspend fun sync(c: Context, from: AskActivity.From?) = lock.withLock {
        val ha = HomeAssistant.from(c) ?: return@withLock
        if (Room.info(c).value == null) return@withLock
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val countdowns = JSONArray()
        Timers.all(c).forEach {
            countdowns.put(JSONObject().put("kind", it.kind.name.lowercase()).put("label", it.label)
                .put("at", it.at).put("duration", it.durationMs / 1000).put("ha_id", it.haId))
        }
        val body = JSONObject()
            .put("id", Room.id(c))
            .put("after", p.getInt("sync_seq", 0))
            .put("boot", p.getString("sync_boot", "") ?: "")
            .put("countdowns", countdowns)
            .put("volume", runCatching { Loudness.level(c) }.getOrNull() ?: JSONObject.NULL)
            // For its Assist satellite's wake word choice in Home Assistant.
            .put("wake_word", if (Prefs.wakeWord(c)) Prefs.wakePhrase(c).name else JSONObject.NULL)
        val reply = try {
            ha.syncDisplay(body)
        } catch (e: HaHttpException) {
            if (e.code == 404) return@withLock // not registered yet, or an older integration
            throw e
        }
        val commands = reply.optJSONArray("commands") ?: JSONArray()
        for (i in 0 until commands.length()) {
            runCatching { act(c, commands.getJSONObject(i), from) }
                .onFailure { Logs.w("Display", "Couldn't do what Home Assistant asked", it) }
        }
        p.edit().putInt("sync_seq", reply.optInt("seq")).putString("sync_boot", reply.optString("boot")).apply()
    }

    /** Does one thing she asked this display to do. */
    private suspend fun act(c: Context, command: JSONObject, from: AskActivity.From?) {
        when (command.optString("type")) {
            // A Home Assistant timer asked through this display: a copy here that rings on time.
            "timer" -> {
                val haId = command.optString("ha_id")
                if (haId.isBlank() || Timers.all(c).none { it.haId == haId }) {
                    Timers.addTimer(c, command.optLong("duration") * 1000, command.optString("label"), haId)
                }
            }
            "timer_update" -> Timers.updateFromHa(c, command.optString("ha_id"), command.optLong("seconds"), command.optBoolean("active", true))
            "alarm" -> Timers.addAlarm(c, command.optInt("hour"), command.optInt("minute"), command.optString("label"))
            "reminder" -> Timers.addReminder(c, command.optLong("at"), command.optString("message"))
            "cancel" -> cancel(c, command)
            "volume" -> volume(c, command)
            "announce" -> announce(c, command, from)
            "wake_word" -> wakeWord(c, command.optString("phrase"))
        }
    }

    /**
     * An announcement from Home Assistant (its satellite's announce or start_conversation): a chime first
     * if asked, then her voice saying the message, or the sound it sent. To start a conversation she then
     * listens, and the answer carries on Home Assistant's conversation.
     */
    private fun announce(c: Context, command: JSONObject, from: AskActivity.From?) {
        val message = command.optString("message")
        val media = command.optString("media")
        val listen = command.optBoolean("listen")
        command.optString("conversation_id").takeIf { it.isNotBlank() }?.let { Prefs.setAssistConversation(c, it) }
        if (command.optBoolean("chime")) Ringing.chime(c)
        if (media.isNotBlank()) {
            HomeAssistant.from(c)?.let { ha -> Sounds.play(c, ha.absolute(media)) }
            if (message.isBlank() && !listen) return
        }
        say(c, message.ifBlank { "Yes?" }, listen, from)
    }

    /** The wake word chosen on its Assist satellite's page in Home Assistant (none: not listening). */
    private fun wakeWord(c: Context, phrase: String) {
        val chosen = runCatching { WakePhrase.valueOf(phrase) }.getOrNull()
        val s = Prefs.load(c)
        Prefs.save(c, if (chosen == null) s.copy(wakeWord = false) else s.copy(wakeWord = true, wakePhrase = chosen))
        Logs.i("Display", "Wake word set in Home Assistant: ${chosen?.label ?: "off"}")
    }

    /** Cancels what she meant: one of Home Assistant's timers, the ones with that name, all of a kind, or the only one. */
    private fun cancel(c: Context, command: JSONObject) {
        command.optString("ha_id").takeIf { it.isNotBlank() }?.let { haId ->
            Timers.all(c).filter { it.haId == haId }.forEach { Timers.cancel(c, it.id) }
            Ringing.now.value?.let { if (it.haId == haId) Ringing.stop(c) }
            return
        }
        val kind = command.optString("kind").takeIf { it.isNotBlank() }?.uppercase()
        val label = command.optString("label").lowercase()
        val all = command.optBoolean("all")
        val ofKind = Timers.all(c).filter { kind == null || it.kind.name == kind }
        val meant = if (label.isBlank()) ofKind else ofKind.filter {
            it.label.lowercase().let { l -> l.isNotBlank() && (label in l || l in label) }
        }
        if (meant.size > 1 && !all && label.isBlank()) return // she asks which first
        meant.forEach { Timers.cancel(c, it.id) }
        // One ringing now counts too ("stop the timer").
        Ringing.now.value?.let { ringing -> if (kind == null || ringing.kind.name == kind) Ringing.stop(c) }
    }

    /** Her volume: on the Home Assistant speaker she speaks through, or this device. */
    private suspend fun volume(c: Context, command: JSONObject) {
        val change = command.optString("change")
        val ha = HomeAssistant.from(c)
        if (Room.speaksThroughSpeaker(c) && ha != null) {
            val speaker = Room.voiceSpeaker(c)
            val now = ha.speakerVolume(speaker) ?: 50
            val level = if (command.has("level")) command.optInt("level") else now + if (change == "louder") 15 else -15
            ha.setSpeakerVolume(speaker, level)
            return
        }
        when {
            command.has("level") -> Loudness.set(c, command.optInt("level"))
            change == "louder" -> Loudness.change(c, louder = true)
            change == "quieter" -> Loudness.change(c, louder = false)
        }
    }

    /**
     * Says something here: in the sheet when Lena is on screen; otherwise as a notification, since Android
     * doesn't let an app open itself from the background.
     */
    fun say(c: Context, message: String, listen: Boolean, from: AskActivity.From? = null) {
        if (message.isBlank()) return
        Logs.i("Display", "Saying: $message")
        if (AppVisible.now) {
            c.startActivity(AskActivity.announce(c, message, listen, from))
            return
        }
        Ringing.channel(c)
        val notification = NotificationCompat.Builder(c, Ringing.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lena)
            .setContentTitle("Lena")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(android.app.PendingIntent.getActivity(
                c, 0, AskActivity.announce(c, message, listen, from),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            ))
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification) }
    }

    /**
     * Speaking first: when the sensor turns on (someone came in), asks whether there's something to say
     * and says it. The integration decides what and how often, so it never repeats itself. Returns the
     * sensor's state for next time.
     */
    private suspend fun watchSensor(c: Context, was: Boolean?): Boolean? {
        val sensor = Room.briefingSensor(c)
        if (sensor.isBlank() || Busy.talking) return was
        val ha = HomeAssistant.from(c) ?: return was
        val state = ha.state(sensor)
        val on = state == "on" || state == "home" || state == "detected" || state == "occupied"
        if (on && was == false) {
            val text = runCatching { ha.briefing(Room.id(c)) }.getOrDefault("")
            if (text.isNotBlank()) say(c, text, listen = false, AskActivity.From.DASHBOARD.takeIf { c is DashboardActivity })
        }
        return on
    }
}

/** A sound Home Assistant sent to play (an announcement that's a sound, not words): played once, as speech. */
object Sounds {
    fun play(c: Context, url: String) {
        runCatching {
            android.media.MediaPlayer().apply {
                setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { it.release() }
                setOnErrorListener { player, _, _ -> player.release(); true }
                prepareAsync()
            }
        }.onFailure { Logs.w("Display", "Couldn't play the announcement's sound", it) }
    }
}
