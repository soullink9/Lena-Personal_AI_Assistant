package io.github.soullink9.lena

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/**
 * What the assistant gesture opens: talk (the Ask sheet, listening), type (the Ask sheet, keyboard) or a new
 * chat. (A saved choice from before calls were removed reads as the default, talk.)
 */
enum class AssistAction { ASK, TYPE, CHAT }

/**
 * Who answers when you talk to Lena: Open WebUI (her chats, with history you can open) or Home Assistant's
 * Assist (its conversation agent), for people who don't use Open WebUI at all. With Assist, everything
 * that asks her goes there (the sheet, quick prompts, scheduled prompts), the chat screen is Lena's own
 * (AssistChatActivity, kept on this device), and settings that are only about Open WebUI step aside.
 */
enum class Answerer(val label: String, val detail: String) {
    OPEN_WEBUI("Open WebUI", "Your Lena model, with every conversation kept in your chats"),
    ASSIST("Home Assistant Assist", "Home Assistant's conversation agent, with Lena's own chat on this device. No Open WebUI needed."),
}

/** Who turns your voice into text in the Ask sheet. */
enum class Hearing { PHONE, SERVER }

/** Who reads Lena's spoken replies aloud. */
enum class Speaking { SERVER, PHONE, OFF }

/** When the mic opens again after Lena answers a spoken question. */
enum class FollowUp { ALWAYS, QUESTIONS, OFF }

/** How All chats lists your chats (the drawer has one compact look, to fit as much as it can). */
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
    val seconds: Int = Defaults.SLIDE_SECONDS,
    val transition: SlideTransition = Defaults.SLIDE_TRANSITION,
    val shuffle: Boolean = Defaults.SLIDE_SHUFFLE,
    val framing: Framing = Defaults.SLIDE_FRAMING,
    val overlay: Boolean = Defaults.SLIDE_OVERLAY,
    val clockAtNight: Boolean = Defaults.SLIDE_CLOCK_AT_NIGHT,
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
    /** The chat every run goes into: Assistant Lena unless you name another; blank for a chat of its own each time. */
    val chat: String = ASSISTANT_CHAT,
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

    /** How it's kept in the app's settings. Every field has a default when read, so adding one never loses old schedules. */
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("hour", hour).put("minute", minute)
        .put("prompt", prompt).put("enabled", enabled).put("chat", chat.trim())
        .put("repeat", repeat.name).put("days", JSONArray(days.sorted()))
        .put("every_hours", everyHours).put("day_of_month", dayOfMonth).put("date", date)
        .put("show_prompt", showPrompt)

    companion object {
        /** Reads one back, putting anything out of range back in range. */
        fun fromJson(o: JSONObject): Schedule {
            val days = o.optJSONArray("days")
            return Schedule(
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
    val assist: AssistAction = Defaults.ASSIST,
    val notifyReplies: Boolean = Defaults.NOTIFY_REPLIES,
    val quickPrompts: List<String> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val hearing: Hearing = Defaults.HEARING,
    val speaking: Speaking = Defaults.SPEAKING,
    val followUp: FollowUp = Defaults.FOLLOW_UP,
    val contextTokens: Int = Defaults.CONTEXT_TOKENS,
    val haServer: String = "",
    val moodVoice: Boolean = Defaults.MOOD_VOICE,
    val widgetMood: Boolean = Defaults.WIDGET_MOOD,
    val lockScreen: Boolean = Defaults.LOCK_SCREEN,
    val openWebUiMenus: Boolean = Defaults.OPEN_WEBUI_MENUS,
    val chatList: ChatListStyle = Defaults.CHAT_LIST,
    val askStyle: AskStyle = Defaults.ASK_STYLE,
    val alerts: Boolean = Defaults.ALERTS,
    val wakeWord: Boolean = Defaults.WAKE_WORD,
    val wakePhrase: WakePhrase = Defaults.WAKE_PHRASE,
    val wakeSensitivity: WakeSensitivity = Defaults.WAKE_SENSITIVITY,
    val dashboards: List<Dashboard> = listOf(Dashboard("Home", "lovelace")),
    /** Seconds without a touch before the wall display's screen saver; 0 never. */
    val dimSeconds: Int = Defaults.DIM_SECONDS,
    /** Seconds without a touch in your chats, opened from the wall display, before it goes back; 0 never. */
    val returnSeconds: Int = Defaults.RETURN_SECONDS,
    val pinnedShown: Int = Defaults.PINNED_SHOWN,
    /** Lena's warm look for Open WebUI's pages (assets/css/open_webui.css); off shows Open WebUI's own. */
    val lenaLook: Boolean = Defaults.LENA_LOOK,
    /** The Chat | Home switch at the top of the main screen (it shows only with Home Assistant set up). */
    val homeSwitch: Boolean = Defaults.HOME_SWITCH,
    /** Your Open WebUI folders in the menu (All chats always has them). */
    val drawerFolders: Boolean = Defaults.DRAWER_FOLDERS,
    /** The wake word listens whenever Lena is open (her chats too), not only on the wall display. */
    val wakeEverywhere: Boolean = Defaults.WAKE_EVERYWHERE,
    val autoSignIn: AutoSignIn = AutoSignIn.OFF,
    /** Who answers: Open WebUI or Home Assistant's Assist. */
    val answers: Answerer = Defaults.ANSWERS,
    /** Assist's conversation agent (a conversation.* entity), or blank for the one Home Assistant's Assist uses. */
    val assistAgent: String = "",
    val motionEntity: String = "",
    val slides: Slides = Slides(),
)

/** Her memory to-do list, created by the Lena integration in Home Assistant. */
const val DEFAULT_MEMORY_LIST = "todo.lena_memory"

/** Lena's settings, stored privately on the phone (and kept out of backups). */
object Prefs {

    /** The app's private settings file. It's excluded from backups (see data_extraction_rules.xml). */
    private fun p(c: Context) = c.getSharedPreferences("lena", Context.MODE_PRIVATE)

    /**
     * A choice saved by its enum name. An unknown or missing name (a choice that no longer exists)
     * gives [default] rather than a crash.
     */
    private inline fun <reified E : Enum<E>> enumPref(c: Context, key: String, default: E): E =
        p(c).getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    /** Your Open WebUI's address, normalized (https, ending in /), or null before it's set. */
    fun server(c: Context): String? = p(c).getString("server", null)
    /** The model you picked, or null for "Lena, automatically". */
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
    fun moodVoice(c: Context): Boolean = p(c).getBoolean("mood_voice", Defaults.MOOD_VOICE)

    /** The widget shows how she feels (needs Home Assistant). */
    fun widgetMood(c: Context): Boolean = p(c).getBoolean("widget_mood", Defaults.WIDGET_MOOD)

    /** The assistant gesture opens the sheet over the lock screen, in its private mode. */
    fun lockScreen(c: Context): Boolean = p(c).getBoolean("lock_screen", Defaults.LOCK_SCREEN)

    /** Your Home Assistant, for Lena's feelings and shared memory. Null when not set. */
    fun haServer(c: Context): String? = p(c).getString("ha_server", "")?.ifBlank { null }
    /** Extra sign-in addresses (a sign-in proxy) that may load inside the app, space-separated. */
    fun signIn(c: Context): String = p(c).getString("sign_in", "") ?: ""
    /** Notify when an answer you walked away from is finished. */
    fun notifyReplies(c: Context): Boolean = p(c).getBoolean("notify_replies", Defaults.NOTIFY_REPLIES)

    /** What the assistant gesture opens. */
    fun assistAction(c: Context): AssistAction =
        enumPref(c, "assist_action", Defaults.ASSIST)

    /** Who turns your voice into text: the phone or your server. */
    fun hearing(c: Context): Hearing =
        enumPref(c, "hearing", Defaults.HEARING)

    /** Who reads her replies aloud: your server, the phone, or nobody. */
    fun speaking(c: Context): Speaking =
        enumPref(c, "speaking", Defaults.SPEAKING)

    /** When the mic opens again after she answers. */
    fun followUp(c: Context): FollowUp =
        enumPref(c, "follow_up", Defaults.FOLLOW_UP)

    /** The model's context size in tokens; half of it holds the ongoing conversation. */
    fun contextTokens(c: Context): Int = p(c).getInt("context_tokens", Defaults.CONTEXT_TOKENS)

    /** The chat that conversation lives in, once it exists. */
    fun assistChat(c: Context): String? = p(c).getString("assist_chat", null)
    /** Remembers (or forgets, with null) the Assistant Lena chat's id. */
    fun setAssistChat(c: Context, id: String?) = p(c).edit().putString("assist_chat", id).apply()

    /** Who answers when you talk to Lena. */
    fun answers(c: Context): Answerer = enumPref(c, "answers", Defaults.ANSWERS)

    /** Assist's conversation agent, or null for the one Home Assistant's Assist settings pick. */
    fun assistAgent(c: Context): String? = p(c).getString("assist_agent", "")?.ifBlank { null }

    /**
     * The ongoing Assist conversation (Home Assistant's conversation id), so talking to Lena continues it
     * the way the Assistant Lena chat does with Open WebUI. Home Assistant forgets a conversation after a
     * while of quiet; a forgotten id simply starts a new one.
     */
    fun assistConversation(c: Context): String? = p(c).getString("assist_conversation", null)
    fun setAssistConversation(c: Context, id: String?) = p(c).edit().putString("assist_conversation", id).apply()

    /** Your quick prompts, in order, blanks dropped. */
    fun quickPrompts(c: Context): List<String> {
        val a = runCatching { JSONArray(p(c).getString("quick_prompts", "[]")) }.getOrDefault(JSONArray())
        return List(a.length()) { a.getString(it) }.filter { it.isNotBlank() }
    }

    /** Your scheduled prompts. A damaged list reads as empty rather than crashing the app. */
    fun schedules(c: Context): List<Schedule> {
        val a = runCatching { JSONArray(p(c).getString("schedules", "[]")) }.getOrDefault(JSONArray())
        val list = List(a.length()) { Schedule.fromJson(a.getJSONObject(it)) }
        // Once, when scheduled prompts started going into Assistant Lena by default: the ones made before,
        // with no chat named, go there too. Clearing it afterwards still means a chat of its own each time.
        if (!p(c).getBoolean("schedules_assistant_chat", false)) {
            val moved = list.map { if (it.chat.isBlank()) it.copy(chat = ASSISTANT_CHAT) else it }
            p(c).edit()
                .putString("schedules", JSONArray(moved.map { it.toJson() }).toString())
                .putBoolean("schedules_assistant_chat", true)
                .apply()
            return moved
        }
        return list
    }

    /**
     * Settings → Return to defaults: every preference back to how a new install starts, keeping what
     * connects Lena and what's yours. Kept: the Open WebUI and Home Assistant connections (addresses,
     * sign-in, tokens, signing in by herself), who answers and Assist's agent, the model and her context
     * size, the dashboards and motion sensor, the screensaver's album (and whether the photo frame is on),
     * whether she listens for her name (turning the microphone on or off is yours to decide, never a
     * reset's), your quick and scheduled prompts, and your order of pinned chats and folders. Whether this
     * is the home screen is Android's setting, so it isn't touched either.
     */
    fun defaultsKeeping(current: Settings): Settings = Settings(
        server = current.server,
        signIn = current.signIn,
        haServer = current.haServer,
        autoSignIn = current.autoSignIn,
        answers = current.answers,
        assistAgent = current.assistAgent,
        model = current.model,
        contextTokens = current.contextTokens,
        dashboards = current.dashboards,
        motionEntity = current.motionEntity,
        quickPrompts = current.quickPrompts,
        schedules = current.schedules,
        slides = Slides(on = current.slides.on, album = current.slides.album, albumName = current.slides.albumName),
        wakeWord = current.wakeWord,
    )

    /** Every setting at once, for the Settings screen. */
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
        lenaLook = lenaLook(c),
        homeSwitch = homeSwitch(c),
        drawerFolders = drawerFolders(c),
        wakeEverywhere = wakeEverywhere(c),
        autoSignIn = autoSignIn(c),
        answers = answers(c),
        assistAgent = assistAgent(c) ?: "",
        motionEntity = motionEntity(c),
        slides = slides(c),
    )

    /**
     * Saves every setting at once. Settings calls this on each change, so nothing is lost if you leave
     * mid-way.
     */
    fun save(c: Context, s: Settings) {
        val schedules = JSONArray(s.schedules.map { it.toJson() })
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
            .putBoolean("lena_look", s.lenaLook)
            .putBoolean("home_switch", s.homeSwitch)
            .putBoolean("drawer_folders", s.drawerFolders)
            .putBoolean("wake_everywhere", s.wakeEverywhere)
            .putString("auto_sign_in", s.autoSignIn.name)
            .putString("answers", s.answers.name)
            .putString("assist_agent", s.assistAgent)
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

    /** Show Open WebUI's own header and sidebar under Lena's. */
    fun showOpenWebUiMenus(c: Context): Boolean = p(c).getBoolean("show_owui_menus", Defaults.OPEN_WEBUI_MENUS)

    /** This phone checks Home Assistant every 15 minutes and tells you when she's worried. */
    fun alerts(c: Context): Boolean = p(c).getBoolean("alerts", Defaults.ALERTS)

    /** What the last alert was about, so the same worry isn't announced twice. */
    fun lastAlert(c: Context): String? = p(c).getString("last_alert", null)
    /** Remembers the last alert's reason, or clears it once the worry is gone. */
    fun setLastAlert(c: Context, reason: String?) = p(c).edit().putString("last_alert", reason).apply()

    /** Display mode and the screen saver listen for the wake word. */
    fun wakeWord(c: Context): Boolean = p(c).getBoolean("wake_word", Defaults.WAKE_WORD)

    /** Dashboard mode's tabs; Home Assistant's default dashboard until you add your own. */
    fun dashboards(c: Context): List<Dashboard> {
        val a = runCatching { JSONArray(p(c).getString("dashboards", null) ?: "[]") }.getOrDefault(JSONArray())
        val list = List(a.length()) { i -> a.getJSONObject(i).let { Dashboard(it.optString("name"), it.optString("path")) } }
            .filter { it.path.isNotBlank() }
        return list.ifEmpty { listOf(Dashboard("Home", "lovelace")) }
    }

    /** The photo frame's settings. */
    fun slides(c: Context): Slides = Slides(
        on = p(c).getBoolean("slides_on", false),
        album = p(c).getString("slides_album", "") ?: "",
        albumName = p(c).getString("slides_album_name", "") ?: "",
        seconds = p(c).getInt("slides_seconds", Defaults.SLIDE_SECONDS),
        transition = enumPref(c, "slides_transition", Defaults.SLIDE_TRANSITION),
        shuffle = p(c).getBoolean("slides_shuffle", Defaults.SLIDE_SHUFFLE),
        framing = enumPref(c, "slides_framing", Defaults.SLIDE_FRAMING),
        overlay = p(c).getBoolean("slides_overlay", Defaults.SLIDE_OVERLAY),
        clockAtNight = p(c).getBoolean("slides_night", Defaults.SLIDE_CLOCK_AT_NIGHT),
    )

    /** Seconds without a touch before the wall display's screen saver; 0 never. */
    fun dimSeconds(c: Context): Int = p(c).getInt("dim_seconds", Defaults.DIM_SECONDS)

    /** Seconds without a touch in your chats, opened from the wall display, before it goes back to it; 0 never. */
    fun returnSeconds(c: Context): Int = p(c).getInt("return_seconds", Defaults.RETURN_SECONDS)

    /** How many pinned chats the menu shows before "View all". */
    fun pinnedShown(c: Context): Int = p(c).getInt("pinned_shown", Defaults.PINNED_SHOWN)

    /** Lena's look for Open WebUI's pages, rather than Open WebUI's own. On by default. */
    fun lenaLook(c: Context): Boolean = p(c).getBoolean("lena_look", Defaults.LENA_LOOK)

    /** The Chat | Home switch on the main screen (when Home Assistant is set up). On by default. */
    fun homeSwitch(c: Context): Boolean = p(c).getBoolean("home_switch", Defaults.HOME_SWITCH)

    /** Folders in the menu. On by default. */
    fun drawerFolders(c: Context): Boolean = p(c).getBoolean("drawer_folders", Defaults.DRAWER_FOLDERS)

    /**
     * Where the wake word listens: false only on the wall display and its screen saver (the default),
     * true whenever Lena is open, her chats too.
     */
    fun wakeEverywhere(c: Context): Boolean = p(c).getBoolean("wake_everywhere", Defaults.WAKE_EVERYWHERE)

    /**
     * Your order for pinned chats and for folders in the menu, as chat or folder ids, first to last.
     * Open WebUI keeps no order of its own for either, so it's kept here, on this phone.
     */
    fun pinnedOrder(c: Context): List<String> = idList(c, "pinned_order")

    fun setPinnedOrder(c: Context, ids: List<String>) = setIdList(c, "pinned_order", ids)

    fun folderOrder(c: Context): List<String> = idList(c, "folder_order")

    /**
     * Apps left out of Lena's apps list (package names). Only the list: Android isn't changed. Not a
     * setting with a default, so Return to defaults keeps it.
     */
    fun hiddenApps(c: Context): Set<String> = idList(c, "hidden_apps").toSet()

    fun setHiddenApps(c: Context, packages: Set<String>) = setIdList(c, "hidden_apps", packages.sorted())

    fun setFolderOrder(c: Context, ids: List<String>) = setIdList(c, "folder_order", ids)

    /** A list of ids saved as a JSON array; empty when there's none. */
    private fun idList(c: Context, key: String): List<String> = runCatching {
        val a = JSONArray(p(c).getString(key, "[]"))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun setIdList(c: Context, key: String, ids: List<String>) {
        p(c).edit().putString(key, JSONArray(ids).toString()).apply()
    }

    /** Whether, and how, to sign in to Open WebUI by herself. */
    fun autoSignIn(c: Context): AutoSignIn =
        enumPref(c, "auto_sign_in", AutoSignIn.OFF)

    /** How sure the wake word must be. */
    fun wakeSensitivity(c: Context): WakeSensitivity =
        enumPref(c, "wake_sensitivity", Defaults.WAKE_SENSITIVITY)

    /** A motion or presence sensor that wakes dashboard mode, or blank. */
    fun motionEntity(c: Context): String = p(c).getString("motion_entity", "") ?: ""

    /** Which wake word: Hey Lena unless you chose another. */
    fun wakePhrase(c: Context): WakePhrase =
        enumPref(c, "wake_phrase", Defaults.WAKE_PHRASE)

    /** How the drawer lists your chats. */
    fun chatList(c: Context): ChatListStyle =
        enumPref(c, "chat_list", Defaults.CHAT_LIST)

    /** How talking to Lena looks. */
    fun askStyle(c: Context): AskStyle =
        enumPref(c, "ask_style", Defaults.ASK_STYLE)

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

    /**
     * https://host[:port] for an address, or null if it isn't https or has a user name or password in it
     * (never allowed: it would end up in logs and history).
     */
    private fun origin(u: Uri): String? {
        val host = u.host
        if (!"https".equals(u.scheme, ignoreCase = true) || host.isNullOrEmpty() || u.userInfo != null) return null
        val port = if (u.port == -1) "" else ":${u.port}"
        return "https://${host.lowercase(Locale.ROOT)}$port"
    }
}
