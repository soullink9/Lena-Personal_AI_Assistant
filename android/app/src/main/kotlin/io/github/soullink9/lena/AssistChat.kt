package io.github.soullink9.lena

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One exchange in Lena's own chat (Assist mode): what was asked and her answer. [label] says where it came
 * from when it wasn't you typing or talking ("Scheduled · Morning briefing", "Quick prompt"). A
 * [divider] marks where you started a new conversation.
 */
data class AssistTurn(
    val time: Long,
    val question: String,
    val answer: String,
    val label: String? = null,
    val divider: Boolean = false,
)

/**
 * Lena's own chat, for when Home Assistant's Assist answers instead of Open WebUI (Settings → Who
 * answers): every question and answer, from the sheet, this chat screen, quick prompts and scheduled
 * prompts, kept on this device in one conversation, newest last.
 *
 * It's a file in the app's private storage (never backed up, see the manifest), trimmed to the newest
 * [KEEP] exchanges.
 */
object AssistLog {
    private const val FILE = "assist_chat.json"
    private const val KEEP = 500
    private val lock = Mutex()

    private val changes = MutableStateFlow(0)

    /** Goes up whenever the chat changes, so an open chat screen shows the new exchange. */
    val changed: StateFlow<Int> = changes

    private fun file(c: Context) = File(c.filesDir, FILE)

    /** The whole chat, oldest first. A damaged file reads as empty rather than crashing. */
    suspend fun all(c: Context): List<AssistTurn> = withContext(Dispatchers.IO) {
        lock.withLock { read(c) }
    }

    /** Adds an exchange at the end. */
    suspend fun add(c: Context, question: String, answer: String, label: String? = null) =
        append(c, AssistTurn(System.currentTimeMillis(), question, answer, label))

    /** Marks the start of a new conversation (Assist forgets the earlier one from here). */
    suspend fun newConversation(c: Context) =
        append(c, AssistTurn(System.currentTimeMillis(), "", "", divider = true))

    private suspend fun append(c: Context, turn: AssistTurn) = withContext(Dispatchers.IO) {
        lock.withLock {
            val turns = (read(c) + turn).takeLast(KEEP)
            val array = JSONArray()
            turns.forEach { t ->
                array.put(
                    JSONObject().put("time", t.time).put("question", t.question).put("answer", t.answer)
                        .put("label", t.label ?: JSONObject.NULL).put("divider", t.divider)
                )
            }
            file(c).writeText(array.toString())
        }
        changes.value++
    }

    private fun read(c: Context): List<AssistTurn> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        val array = runCatching { JSONArray(f.readText()) }.getOrElse { return emptyList() }
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            AssistTurn(
                time = o.optLong("time"),
                question = o.optString("question"),
                answer = o.optString("answer"),
                label = if (o.isNull("label")) null else o.optString("label").ifBlank { null },
                divider = o.optBoolean("divider"),
            )
        }
    }
}

/**
 * Asking Home Assistant's Assist, for everything that asks her in Assist mode: the sheet, Lena's own chat
 * screen and scheduled prompts. Every exchange goes into [AssistLog]. Each question says which display
 * it's from ([Room]), so Assist knows the room.
 */
object AssistChat {

    /**
     * Asks Assist and returns its answer. With [conversation], it continues the ongoing Assist
     * conversation (so the agent remembers what was just said); a scheduled prompt asks on its own. Throws
     * IllegalStateException without Home Assistant, HaAuthException if it refuses the token, and an
     * IOException if it can't be reached.
     */
    suspend fun ask(c: Context, question: String, label: String? = null, conversation: Boolean = true): String {
        val ha = HomeAssistant.from(c)
            ?: throw IllegalStateException("Set up Home Assistant in Settings → Connections to talk through Assist.")
        val reply = ha.converse(question, Prefs.assistAgent(c), if (conversation) Prefs.assistConversation(c) else null,
            deviceId = Room.info(c).value?.deviceId)
        if (conversation) Prefs.setAssistConversation(c, reply.conversationId)
        val answer = reply.speech.ifBlank { "(Assist didn't say anything.)" }
        AssistLog.add(c, question, answer, label)
        return answer
    }

    /** Starts a new Assist conversation: the next question doesn't carry the earlier ones. */
    suspend fun newConversation(c: Context) {
        Prefs.setAssistConversation(c, null)
        AssistLog.newConversation(c)
    }
}
