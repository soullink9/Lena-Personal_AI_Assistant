package io.github.soullink9.lena

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/** What the assistant gesture opens. */
enum class AssistAction { ASK, CALL, CHAT }

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
)

/** Lena's settings, stored privately on the phone (and kept out of backups). */
object Prefs {

    private fun p(c: Context) = c.getSharedPreferences("lena", Context.MODE_PRIVATE)

    fun server(c: Context): String? = p(c).getString("server", null)
    fun model(c: Context): String? = p(c).getString("model", "")?.ifBlank { null }
    fun signIn(c: Context): String = p(c).getString("sign_in", "") ?: ""
    fun notifyReplies(c: Context): Boolean = p(c).getBoolean("notify_replies", true)

    fun assistAction(c: Context): AssistAction =
        runCatching { AssistAction.valueOf(p(c).getString("assist_action", null) ?: "ASK") }
            .getOrDefault(AssistAction.ASK)

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
            .apply()
    }

    fun showOpenWebUiMenus(c: Context): Boolean = p(c).getBoolean("show_owui_menus", false)
    fun setShowOpenWebUiMenus(c: Context, show: Boolean) = p(c).edit().putBoolean("show_owui_menus", show).apply()

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
