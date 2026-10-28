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
    GROUPS("Rounded groups", "Each day in its own rounded group"),
    FLAT("Flat list", "Plain rows, only the open chat highlighted. Fits the most."),
    SLIM("Slim groups", "Rounded groups, but tighter"),
    TIMED("Title and time", "Plain rows with when you last used each chat"),
    TIMELINE("Timeline", "Your days on a line down the side"),
    FOLDING("Folding days", "Tap a day to open or close it"),
}

/** What talking to Lena looks like over other apps. */
enum class AskStyle(val label: String, val detail: String) {
    SHEET("Sheet", "A panel from the bottom with your conversation"),
    GLOW("Glow", "Light around the edges of the screen that follows your voice, with a small caption"),
    BAR("Bar", "A small bar at the bottom; her answer pops up in a card above it"),
    ORB("Orb", "Her logo pulses in the middle of a darkened screen, with her words underneath"),
    ISLAND("Island", "A small pill at the top; her answer drops down under it"),
    SUBTITLES("Subtitles", "No panel: her words appear at the bottom like film subtitles"),
}

/** A prompt Lena sends on her own every day at [hour]:[minute]. */
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val prompt: String = "",
    val enabled: Boolean = true,
)

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
    val chatList: ChatListStyle = ChatListStyle.GROUPS,
    val askStyle: AskStyle = AskStyle.SHEET,
    val alerts: Boolean = false,
    val wakeWord: Boolean = false,
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

    /** One ongoing conversation for talking to Lena, instead of a new chat each time. */
    fun ongoing(c: Context): Boolean = p(c).getBoolean("ongoing", true)

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
            Schedule(
                id = o.getString("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                prompt = o.getString("prompt"),
                enabled = o.optBoolean("enabled", true),
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
    )

    fun save(c: Context, s: Settings) {
        val schedules = JSONArray()
        s.schedules.forEach {
            schedules.put(
                JSONObject().put("id", it.id).put("hour", it.hour).put("minute", it.minute)
                    .put("prompt", it.prompt).put("enabled", it.enabled)
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

    fun chatList(c: Context): ChatListStyle =
        runCatching { ChatListStyle.valueOf(p(c).getString("chat_list", null) ?: "GROUPS") }.getOrDefault(ChatListStyle.GROUPS)

    fun askStyle(c: Context): AskStyle =
        runCatching { AskStyle.valueOf(p(c).getString("ask_style", null) ?: "SHEET") }.getOrDefault(AskStyle.SHEET)

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
