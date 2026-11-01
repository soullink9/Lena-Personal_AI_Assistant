package io.github.soullink9.lena

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/** What the assistant gesture opens: talk (the Ask sheet, listening), type (the Ask sheet, keyboard), a call or a chat. */
enum class AssistAction { ASK, TYPE, CALL, CHAT }

/** Who turns your voice into text in the Ask sheet. */
enum class Hearing { PHONE, SERVER }

/** Who reads Lena's spoken replies aloud. */
enum class Speaking { SERVER, PHONE, OFF }

/** When the mic opens again after Lena answers a spoken question. */
enum class FollowUp { ALWAYS, QUESTIONS, OFF }

/** How the drawer lists your chats. */
enum class ChatListStyle(val label: String, val detail: String) {
    SLIM("Slim groups", "Rounded groups, but tighter"),
    GROUPS("Rounded groups", "Each day in its own rounded group"),
    FLAT("Flat list", "Plain rows, only the open chat highlighted. Fits the most."),
    TIMED("Title and time", "Plain rows with when you last used each chat"),
    TIMELINE("Timeline", "Your days on a line down the side"),
    FOLDING("Folding days", "Tap a day to open or close it"),
}

/** What talking to Lena looks like over other apps. */
enum class AskStyle(val label: String, val detail: String) {
    ORB("Orb", "Her logo pulses in the middle of a darkened screen, with her words underneath"),
    SHEET("Sheet", "A panel from the bottom with your conversation"),
    GLOW("Glow", "Light around the edges of the screen that follows your voice, with a small caption"),
    BAR("Bar", "A small bar at the bottom; her answer pops up in a card above it"),
    ISLAND("Island", "A small pill at the top; her answer drops down under it"),
    SUBTITLES("Subtitles", "No panel: her words appear at the bottom like film subtitles"),
}

/** How one photo gives way to the next in the slideshow. */
enum class SlideTransition(val label: String) { FADE("Fade"), SLIDE("Slide"), ZOOM("Slow zoom and fade"), NONE("None") }

/** How a photo fits the screen. */
enum class Framing(val label: String, val detail: String) {
    SMART("Smart", "Fills the screen when little would be cut off, keeping faces in; portrait and panorama photos show whole"),
    FILL("Fill the screen", "Always fills it, cropping the edges around the faces it finds"),
    WHOLE("Whole photo", "Every photo shown whole, on a blurred copy of itself"),
}

/** The photo slideshow in the screen saver, display mode and dashboard mode's idle screen. */
data class Slides(
    val on: Boolean = false,
    val album: String = "",
    val albumName: String = "",
    val seconds: Int = 15,
    val transition: SlideTransition = SlideTransition.FADE,
    val shuffle: Boolean = true,
    val framing: Framing = Framing.SMART,
    val overlay: Boolean = true,
    val clockAtNight: Boolean = true,
)

/** One of your Home Assistant dashboards, shown as a tab in dashboard mode. */
data class Dashboard(val name: String, val path: String)

/** The word that wakes her in display mode, and the model file the build adds for it. */
enum class WakePhrase(val label: String, val file: String, val detail: String) {
    HEY_LENA("Hey Lena", "hey_lena.tflite", "Two words, so far fewer false starts. A microWakeWord model, like Home Assistant's voice satellites."),
    LENA("Lena", "lena.tflite", "Just her name: quicker to say, but ordinary speech wakes her more often"),
    JARVIS("Hey Jarvis", "hey_jarvis_v0.1.tflite", "openWakeWord's model, free for personal, non-commercial use"),
}

/** How often a scheduled prompt runs. */
enum class Repeat(val label: String) {
    DAILY("Every day"), WEEKDAYS("Weekdays"), WEEKENDS("Weekends"), DAYS("On the days you pick"),
    HOURS("Every few hours"), MONTHLY("Once a month"), ONCE("Once"),
}

/**
 * A prompt Lena sends on her own at [hour]:[minute], as often as [repeat] says. With a [chat]
 * name, every run goes into the chat of that name (made the first time), so a morning and an
 * evening briefing can share one history; without one, each run is a new chat.
 */
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val prompt: String = "",
    val enabled: Boolean = true,
    val chat: String = "",
    val repeat: Repeat = Repeat.DAILY,
    /** For [Repeat.DAYS]: ISO days of the week, 1 = Monday ... 7 = Sunday. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** For [Repeat.HOURS]: every this many hours, from [hour]:[minute] until midnight. */
    val everyHours: Int = 3,
    /** For [Repeat.MONTHLY]: the day of the month (the last day, in shorter months). */
    val dayOfMonth: Int = 1,
    /** For [Repeat.ONCE]: the date, as days since 1970-01-01. */
    val date: Long = 0,
    /** The chat shows the prompt itself; otherwise just its [title], with the prompt sent to her quietly. */
    val showPrompt: Boolean = false,
) {
    /** What it's called in Settings: its name, or the start of its prompt. */
    val title: String get() = name.trim().ifBlank { prompt.lineSequence().firstOrNull()?.take(40)?.ifBlank { null } ?: "Scheduled prompt" }
}

/** Signing in to Open WebUI by itself when its session ends (for example when the server restarts). */
enum class AutoSignIn(val label: String) { OFF("Off"), PASSWORD("Email and password"), LDAP("LDAP") }

/** The chat that talking to Lena always uses. */
const val ASSISTANT_CHAT = "Assistant Lena"

/** Everything on the settings screen. */
data class Settings(
    val server: String = "",
    val model: String = "",
    val signIn: String = "",
    val assist: AssistAction = AssistAction.ASK,
    val notifyReplies: Boolean = true,
    val quickPrompts: List<String> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val hearing: Hearing = Hearing.PHONE,
    val speaking: Speaking = Speaking.SERVER,
    val followUp: FollowUp = FollowUp.ALWAYS,
    val ongoing: Boolean = true,
    val contextTokens: Int = 32_768,
    val haServer: String = "",
    val moodVoice: Boolean = false,
    val widgetMood: Boolean = false,
    val lockScreen: Boolean = false,
    val openWebUiMenus: Boolean = false,
    val chatList: ChatListStyle = ChatListStyle.SLIM,
    val askStyle: AskStyle = AskStyle.ORB,
    val alerts: Boolean = false,
    val wakeWord: Boolean = false,
    val wakePhrase: WakePhrase = WakePhrase.HEY_LENA,
    val wakeSensitivity: WakeSensitivity = WakeSensitivity.BALANCED,
    val dashboards: List<Dashboard> = listOf(Dashboard("Home", "lovelace")),
    /** Seconds without a touch before the wall display's screen saver; 0 never. */
    val dimSeconds: Int = 300,
    /** Seconds without a touch in your chats, opened from the wall display, before it goes back; 0 never. */
    val returnSeconds: Int = 60,
    val pinnedShown: Int = 5,
    val autoSignIn: AutoSignIn = AutoSignIn.OFF,
    val motionEntity: String = "",
    val slides: Slides = Slides(),
)

/** Her memory to-do list, created by the Lena integration in Home Assistant. */
const val DEFAULT_MEMORY_LIST = "todo.lena_memory"

/** Lena's settings, stored privately on the phone (and kept out of backups). */
object Prefs {

    private fun p(c: Context) = c.getSharedPreferences("lena", Context.MODE_PRIVATE)

    fun server(c: Context): String? = p(c).getString("server", null)
    fun model(c: Context): String? = p(c).getString("model", "")?.ifBlank { null }

    /** Your chosen model, or the Lena model found on your server when you haven't chosen one. */
    fun effectiveModel(c: Context): String? = model(c) ?: p(c).getString("found_lena", null)

    /** Remembers which of your server's models is Lena, for when no model is chosen. */
    fun rememberLena(c: Context, models: List<Model>) {
        val lena = lenaIn(models)?.id
        if (lena != p(c).getString("found_lena", null)) p(c).edit().putString("found_lena", lena).apply()
    }

    /** The model built from the Modelfile: "lena" (Ollama shows it as "lena:latest"), or one named Lena. */
    fun lenaIn(models: List<Model>): Model? =
        models.firstOrNull { it.id == "lena" || it.id.startsWith("lena:") }
            ?: models.firstOrNull { it.name.equals("lena", ignoreCase = true) }

    /** Her voice gets a little slower, faster, lower or brighter with her mood (needs Home Assistant). */
    fun moodVoice(c: Context): Boolean = p(c).getBoolean("mood_voice", false)

    /** The widget shows how she feels (needs Home Assistant). */
    fun widgetMood(c: Context): Boolean = p(c).getBoolean("widget_mood", false)

    /** The assistant gesture opens the sheet over the lock screen, in its private mode. */
    fun lockScreen(c: Context): Boolean = p(c).getBoolean("lock_screen", false)

    /** Your Home Assistant, for Lena's feelings and shared memory. Null when not set. */
    fun haServer(c: Context): String? = p(c).getString("ha_server", "")?.ifBlank { null }
    fun signIn(c: Context): String = p(c).getString("sign_in", "") ?: ""
    fun notifyReplies(c: Context): Boolean = p(c).getBoolean("notify_replies", true)

    fun assistAction(c: Context): AssistAction =
        runCatching { AssistAction.valueOf(p(c).getString("assist_action", null) ?: "ASK") }
            .getOrDefault(AssistAction.ASK)

    fun hearing(c: Context): Hearing =
        runCatching { Hearing.valueOf(p(c).getString("hearing", null) ?: "PHONE") }.getOrDefault(Hearing.PHONE)

    fun speaking(c: Context): Speaking =
        runCatching { Speaking.valueOf(p(c).getString("speaking", null) ?: "SERVER") }.getOrDefault(Speaking.SERVER)

    fun followUp(c: Context): FollowUp {
        p(c).getString("follow_up", null)?.let { v -> return runCatching { FollowUp.valueOf(v) }.getOrDefault(FollowUp.ALWAYS) }
        return if (p(c).getBoolean("follow_ups", true)) FollowUp.ALWAYS else FollowUp.OFF // 2.1.0 setting
    }

    /** Talking to Lena always continues the "Assistant Lena" chat (a setting before 2.17.0; now always on). */
    @Suppress("UNUSED_PARAMETER")
    fun ongoing(c: Context): Boolean = true

    /** The model's context size in tokens; half of it holds the ongoing conversation. */
    fun contextTokens(c: Context): Int = p(c).getInt("context_tokens", 32_768)

    /** The chat that conversation lives in, once it exists. */
    fun assistChat(c: Context): String? = p(c).getString("assist_chat", null)
    fun setAssistChat(c: Context, id: String?) = p(c).edit().putString("assist_chat", id).apply()

    fun quickPrompts(c: Context): List<String> {
        val a = runCatching { JSONArray(p(c).getString("quick_prompts", "[]")) }.getOrDefault(JSONArray())
        return List(a.length()) { a.getString(it) }.filter { it.isNotBlank() }
    }

    fun schedules(c: Context): List<Schedule> {
        val a = runCatching { JSONArray(p(c).getString("schedules", "[]")) }.getOrDefault(JSONArray())
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val days = o.optJSONArray("days")
            Schedule(
                id = o.getString("id"),
                name = o.optString("name"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                prompt = o.getString("prompt"),
                enabled = o.optBoolean("enabled", true),
                chat = o.optString("chat"),
                repeat = runCatching { Repeat.valueOf(o.optString("repeat", "DAILY")) }.getOrDefault(Repeat.DAILY),
                days = if (days == null) setOf(1, 2, 3, 4, 5) else List(days.length()) { days.optInt(it) }.filter { it in 1..7 }.toSet(),
                everyHours = o.optInt("every_hours", 3).coerceIn(1, 12),
                dayOfMonth = o.optInt("day_of_month", 1).coerceIn(1, 31),
                date = o.optLong("date", 0),
                showPrompt = o.optBoolean("show_prompt", false),
            )
        }
    }

    fun load(c: Context) = Settings(
        server = server(c) ?: "",
        model = model(c) ?: "",
        signIn = signIn(c),
        assist = assistAction(c),
        notifyReplies = notifyReplies(c),
        quickPrompts = quickPrompts(c),
        schedules = schedules(c),
        hearing = hearing(c),
        speaking = speaking(c),
        followUp = followUp(c),
        ongoing = ongoing(c),
        contextTokens = contextTokens(c),
        haServer = haServer(c) ?: "",
        moodVoice = moodVoice(c),
        widgetMood = widgetMood(c),
        lockScreen = lockScreen(c),
        openWebUiMenus = showOpenWebUiMenus(c),
        chatList = chatList(c),
        askStyle = askStyle(c),
        alerts = alerts(c),
        wakeWord = wakeWord(c),
        wakePhrase = wakePhrase(c),
        wakeSensitivity = wakeSensitivity(c),
        dashboards = dashboards(c),
        dimSeconds = dimSeconds(c),
        returnSeconds = returnSeconds(c),
        pinnedShown = pinnedShown(c),
        autoSignIn = autoSignIn(c),
        motionEntity = motionEntity(c),
        slides = slides(c),
    )

    fun save(c: Context, s: Settings) {
        val schedules = JSONArray()
        s.schedules.forEach {
            schedules.put(
                JSONObject().put("id", it.id).put("name", it.name).put("hour", it.hour).put("minute", it.minute)
                    .put("prompt", it.prompt).put("enabled", it.enabled).put("chat", it.chat.trim())
                    .put("repeat", it.repeat.name).put("days", JSONArray(it.days.sorted()))
                    .put("every_hours", it.everyHours).put("day_of_month", it.dayOfMonth).put("date", it.date)
                    .put("show_prompt", it.showPrompt)
            )
        }
        p(c).edit()
            .putString("server", s.server)
            .putString("model", s.model.trim())
            .putString("sign_in", s.signIn)
            .putString("assist_action", s.assist.name)
            .putBoolean("notify_replies", s.notifyReplies)
            .putString("quick_prompts", JSONArray(s.quickPrompts.filter { it.isNotBlank() }).toString())
            .putString("schedules", schedules.toString())
            .putString("hearing", s.hearing.name)
            .putString("speaking", s.speaking.name)
            .putString("follow_up", s.followUp.name)
            .putBoolean("ongoing", s.ongoing)
            .putInt("context_tokens", s.contextTokens.coerceIn(2048, 1_048_576))
            .putString("ha_server", s.haServer)
            .putBoolean("mood_voice", s.moodVoice)
            .putBoolean("widget_mood", s.widgetMood)
            .putBoolean("lock_screen", s.lockScreen)
            .putBoolean("show_owui_menus", s.openWebUiMenus)
            .putString("chat_list", s.chatList.name)
            .putString("ask_style", s.askStyle.name)
            .putBoolean("alerts", s.alerts)
            .putBoolean("wake_word", s.wakeWord)
            .putString("wake_phrase", s.wakePhrase.name)
            .putString("wake_sensitivity", s.wakeSensitivity.name)
            .putInt("return_seconds", s.returnSeconds)
            .putInt("pinned_shown", s.pinnedShown)
            .putString("auto_sign_in", s.autoSignIn.name)
            .putString("dashboards", JSONArray().apply {
                s.dashboards.filter { it.path.isNotBlank() }.forEach { put(JSONObject().put("name", it.name).put("path", it.path)) }
            }.toString())
            .putInt("dim_seconds", s.dimSeconds)
            .putString("motion_entity", s.motionEntity.trim())
            .putBoolean("slides_on", s.slides.on)
            .putString("slides_album", s.slides.album)
            .putString("slides_album_name", s.slides.albumName)
            .putInt("slides_seconds", s.slides.seconds)
            .putString("slides_transition", s.slides.transition.name)
            .putBoolean("slides_shuffle", s.slides.shuffle)
            .putString("slides_framing", s.slides.framing.name)
            .putBoolean("slides_overlay", s.slides.overlay)
            .putBoolean("slides_night", s.slides.clockAtNight)
            .apply()
    }

    fun showOpenWebUiMenus(c: Context): Boolean = p(c).getBoolean("show_owui_menus", false)

    /** This phone checks Home Assistant every 15 minutes and tells you when she's worried. */
    fun alerts(c: Context): Boolean = p(c).getBoolean("alerts", false)

    /** What the last alert was about, so the same worry isn't announced twice. */
    fun lastAlert(c: Context): String? = p(c).getString("last_alert", null)
    fun setLastAlert(c: Context, reason: String?) = p(c).edit().putString("last_alert", reason).apply()

    /** Display mode and the screen saver listen for the wake word. */
    fun wakeWord(c: Context): Boolean = p(c).getBoolean("wake_word", false)

    /** Dashboard mode's tabs; Home Assistant's default dashboard until you add your own. */
    fun dashboards(c: Context): List<Dashboard> {
        val a = runCatching { JSONArray(p(c).getString("dashboards", null) ?: "[]") }.getOrDefault(JSONArray())
        val list = List(a.length()) { i -> a.getJSONObject(i).let { Dashboard(it.optString("name"), it.optString("path")) } }
            .filter { it.path.isNotBlank() }
        return list.ifEmpty { listOf(Dashboard("Home", "lovelace")) }
    }

    fun slides(c: Context): Slides = Slides(
        on = p(c).getBoolean("slides_on", false),
        album = p(c).getString("slides_album", "") ?: "",
        albumName = p(c).getString("slides_album_name", "") ?: "",
        seconds = p(c).getInt("slides_seconds", 15),
        transition = runCatching { SlideTransition.valueOf(p(c).getString("slides_transition", null) ?: "FADE") }
            .getOrDefault(SlideTransition.FADE),
        shuffle = p(c).getBoolean("slides_shuffle", true),
        framing = p(c).getString("slides_framing", null)?.let { v -> runCatching { Framing.valueOf(v) }.getOrNull() }
            ?: if (p(c).getBoolean("slides_fill", true)) Framing.SMART else Framing.WHOLE, // before 2.17.0: a switch
        overlay = p(c).getBoolean("slides_overlay", true),
        clockAtNight = p(c).getBoolean("slides_night", true),
    )

    /** Seconds without a touch before the wall display's screen saver; 0 never (minutes before 2.17.0). */
    fun dimSeconds(c: Context): Int = if (p(c).contains("dim_seconds")) p(c).getInt("dim_seconds", 300) else p(c).getInt("dim_minutes", 5) * 60

    /** Seconds without a touch in your chats, opened from the wall display, before it goes back to it; 0 never. */
    fun returnSeconds(c: Context): Int = p(c).getInt("return_seconds", 60)

    /** How many pinned chats the menu shows before "View all". */
    fun pinnedShown(c: Context): Int = p(c).getInt("pinned_shown", 5)

    fun autoSignIn(c: Context): AutoSignIn =
        runCatching { AutoSignIn.valueOf(p(c).getString("auto_sign_in", null) ?: "OFF") }.getOrDefault(AutoSignIn.OFF)

    fun wakeSensitivity(c: Context): WakeSensitivity =
        runCatching { WakeSensitivity.valueOf(p(c).getString("wake_sensitivity", null) ?: "BALANCED") }.getOrDefault(WakeSensitivity.BALANCED)

    /**
     * Once, on 2.17.0: talking to Lena now opens as the orb and the chat list as slim groups, so
     * anyone still on the old defaults (the sheet, rounded groups) moves to the new ones.
     */
    fun migrate(c: Context) {
        val prefs = p(c)
        if (prefs.getInt("migrated", 0) >= 1) return
        val e = prefs.edit()
        if ((prefs.getString("ask_style", null) ?: "SHEET") == "SHEET") e.putString("ask_style", "ORB")
        if ((prefs.getString("chat_list", null) ?: "GROUPS") == "GROUPS") e.putString("chat_list", "SLIM")
        e.putInt("migrated", 1).apply()
    }

    /** A motion or presence sensor that wakes dashboard mode, or blank. */
    fun motionEntity(c: Context): String = p(c).getString("motion_entity", "") ?: ""

    fun wakePhrase(c: Context): WakePhrase =
        runCatching { WakePhrase.valueOf(p(c).getString("wake_phrase", null) ?: "HEY_LENA") }.getOrDefault(WakePhrase.HEY_LENA)

    fun chatList(c: Context): ChatListStyle =
        runCatching { ChatListStyle.valueOf(p(c).getString("chat_list", null) ?: "SLIM") }.getOrDefault(ChatListStyle.SLIM)

    fun askStyle(c: Context): AskStyle =
        runCatching { AskStyle.valueOf(p(c).getString("ask_style", null) ?: "ORB") }.getOrDefault(AskStyle.ORB)

    /**
     * A random secret only Lena knows. Lena's own widget and shortcuts include it, so a prompt
     * is only ever sent automatically when Lena herself asked; other apps can't trigger a send.
     */
    fun launchKey(c: Context): String {
        p(c).getString("launch_key", null)?.let { return it }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        p(c).edit().putString("launch_key", key).apply()
        return key
    }

    /** A clean https://host[:port][/path]/ address, or null if it isn't a usable https address. */
    fun normalize(raw: String): String? {
        val u = Uri.parse(raw.trim())
        val origin = origin(u) ?: return null
        val path = (u.path ?: "").trimEnd('/')
        return "$origin$path/"
    }

    /** Sign-in addresses separated by spaces, commas or new lines → "https://a https://b"; null if any isn't https. */
    fun normalizeSignIn(raw: String): String? {
        val out = mutableListOf<String>()
        for (part in raw.trim().split(Regex("[\\s,]+"))) {
            if (part.isEmpty()) continue
            val o = origin(Uri.parse(part)) ?: return null
            if (o !in out) out += o
        }
        return out.joinToString(" ")
    }

    private fun origin(u: Uri): String? {
        val host = u.host
        if (!"https".equals(u.scheme, ignoreCase = true) || host.isNullOrEmpty() || u.userInfo != null) return null
        val port = if (u.port == -1) "" else ":${u.port}"
        return "https://${host.lowercase(Locale.ROOT)}$port"
    }
}
