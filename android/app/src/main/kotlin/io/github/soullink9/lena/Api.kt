package io.github.soullink9.lena

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Signed out, or the session expired: open Lena and sign in again. */
class AuthException : IOException("Signed out")

/** Signed in, but your account isn't allowed to do this (or the feature is turned off). */
class ForbiddenException : IOException("Not allowed")

/** Someone changed it somewhere else (in the browser, say) since it was opened here: read it again first. */
class ConflictException : IOException("Changed elsewhere")

/**
 * A model on your Open WebUI, with the tools and features (web search and the like) its settings turn on by
 * default, which the sheet and scheduled prompts use the same way the page would.
 */
data class Model(val id: String, val name: String, val toolIds: List<String>, val features: Set<String>)

/**
 * A chat in a list. active means an answer is still being written; folderId is the folder it's in, when
 * the list it came from says so (a folder's own list always does).
 */
data class ChatItem(
    val id: String,
    val title: String,
    val updatedMillis: Long,
    val active: Boolean,
    val folderId: String? = null,
)

/** Her answer so far, and whether it's finished. */
data class Reply(val content: String, val done: Boolean)

/**
 * Talks to your Open WebUI with the sign-in you already made inside Lena.
 *
 * Open WebUI keeps your session in a `token` cookie (password, LDAP and single sign-on alike).
 * Lena reads it from its own web view storage and sends it only to your server's address.
 * If a sign-in proxy such as Authentik sits in front of Open WebUI, its cookie for your server's
 * address is sent along too, exactly as the web view would.
 */
class Api(server: String, private val context: Context? = null) {

    private val base = server.trimEnd('/')

    companion object {
        /** How many chats Open WebUI sends per page of your chat list. */
        const val CHATS_PER_PAGE = 60

        /** How many chats Open WebUI sends per page of a folder's list. */
        const val FOLDER_CHATS_PER_PAGE = 10

        /**
         * An Api for the saved server, or null before one is set. It gets the app's context so it can sign
         * in again by itself.
         */
        fun from(c: Context): Api? = Prefs.server(c)?.let { Api(it, c.applicationContext) }

        /**
         * A conversation that never ends outgrows any model. Lena gives it half of the model's
         * context, filled from the newest message back; the other half stays free for the system
         * prompt, tool definitions, tool results and the reply. About 4 characters per token.
         */
        fun fitToBudget(thread: List<JSONObject>, contextTokens: Int): List<JSONObject> {
            val budgetChars = contextTokens.coerceAtLeast(2048) / 2 * 4
            var used = 0
            var start = thread.size
            while (start > 0) {
                val size = thread[start - 1].optString("content").length + 16
                if (start < thread.size && used + size > budgetChars) break // always keep your new message
                used += size
                start--
            }
            var recent = thread.subList(start, thread.size)
            while (recent.size > 1 && recent.first().optString("role") != "user") recent = recent.subList(1, recent.size)
            return recent
        }
    }

    /**
     * The web view's cookies for your server: the sign-in lives there, so the native features use exactly
     * the session the page has. CookieManager is read on the main thread.
     */
    private suspend fun cookies(): String? = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(base)
    }

    /** Open WebUI's session token, from its token cookie. */
    private fun tokenFrom(cookies: String?): String? = cookies?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("token=") }
        ?.substringAfter("token=")
        ?.ifBlank { null }

    /** True when Lena holds a sign-in for your server. */
    suspend fun signedIn(): Boolean = tokenFrom(cookies()) != null

    /**
     * One request. Open WebUI answers "signed out" (HTTP 401) for three different things: an ended
     * session, some missing things, and a feature your account isn't allowed to use (Notes, for one).
     * So on a 401 the session itself is checked first:
     * - still good: it was a permission, and it's reported as ForbiddenException;
     * - really ended (Open WebUI restarted, say) with automatic sign-in on: sign in again and try once more;
     * - otherwise (ended without automatic sign-in, or the check couldn't tell): signed out.
     */
    private suspend fun call(
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        auth: Boolean = true,
        readTimeoutMs: Int = 30_000,
        accept: String = "application/json",
    ): ByteArray = try {
        callOnce(method, path, body, contentType, auth, readTimeoutMs, accept)
    } catch (e: AuthException) {
        if (!auth) throw e
        when (session()) {
            Session.VALID -> throw ForbiddenException()
            Session.UNKNOWN -> throw e
            Session.ENDED -> {
                val c = context
                if (c == null || !OpenWebUiSignIn.configured(c) || OpenWebUiSignIn.signIn(c) == null) throw e
                callOnce(method, path, body, contentType, auth, readTimeoutMs, accept)
            }
        }
    }

    /** What a check of the session found. */
    private enum class Session { VALID, ENDED, UNKNOWN }

    /** Asks Open WebUI who you are, which works exactly when the session is still good. */
    private suspend fun session(): Session {
        if (tokenFrom(cookies()) == null) return Session.ENDED
        val failure = runCatching {
            callOnce("GET", "/api/v1/auths/", null, "application/json", true, 15_000, "application/json")
        }.exceptionOrNull()
        return when (failure) {
            null -> Session.VALID
            is AuthException -> Session.ENDED
            else -> Session.UNKNOWN
        }
    }

    /**
     * One HTTP request with the session. Redirects aren't followed: a redirect means a sign-in proxy wants
     * you, not an answer. Failures are logged by path only (never the query or body, which can hold private
     * text).
     */
    private suspend fun callOnce(
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String,
        auth: Boolean,
        readTimeoutMs: Int,
        accept: String,
    ): ByteArray {
        val cookies = cookies()
        val token = tokenFrom(cookies)
        if (auth && token == null) throw AuthException()
        return withContext(Dispatchers.IO) {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.instanceFollowRedirects = false // a redirect means a sign-in page, not an answer
                conn.connectTimeout = 10_000
                conn.readTimeout = readTimeoutMs
                conn.setRequestProperty("Accept", accept)
                if (cookies != null) conn.setRequestProperty("Cookie", cookies)
                if (auth) conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", contentType)
                    conn.outputStream.use { it.write(body) }
                }
                val code = try {
                    conn.responseCode
                } catch (e: IOException) {
                    Logs.w("Open WebUI", "$method ${path.substringBefore('?')}: couldn't connect", e)
                    throw e
                }
                if (code == 401 || code in 300..399) {
                    val where = "$method ${path.substringBefore('?')}"
                    // Open WebUI answers "that chat doesn't exist" with 401 too; its message tells them apart.
                    val detail = if (code != 401) "" else runCatching {
                        JSONObject(String(conn.errorStream?.use { it.readBytes() } ?: ByteArray(0))).optString("detail")
                    }.getOrDefault("")
                    if (detail.startsWith("We could not find")) {
                        Logs.i("Open WebUI", "$where: not found (it was deleted, or isn't yours)")
                        throw IOException("HTTP 404")
                    }
                    val c = context
                    val hint = if (c != null && !OpenWebUiSignIn.configured(c)) ". Settings → Open WebUI → Sign in by herself can sign in again automatically." else ""
                    Logs.w("Open WebUI", "$where: not signed in (HTTP $code${detail.ifBlank { null }?.let { ": $it" } ?: ""})$hint")
                    throw AuthException()
                }
                if (code == 403) {
                    Logs.w("Open WebUI", "$method ${path.substringBefore('?')}: not allowed (HTTP 403)")
                    throw ForbiddenException()
                }
                if (code == 409) {
                    Logs.i("Open WebUI", "$method ${path.substringBefore('?')}: changed elsewhere (HTTP 409)")
                    throw ConflictException()
                }
                val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) {
                    Logs.w("Open WebUI", "$method ${path.substringBefore('?')}: HTTP $code ${String(bytes).take(200)}")
                    throw IOException("HTTP $code")
                }
                bytes
            } finally {
                conn.disconnect()
            }
        }
    }

    /** A JSON request whose answer is text. */
    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        readTimeoutMs: Int = 30_000,
    ): String = String(call(method, path, body?.toString()?.toByteArray(), auth = auth, readTimeoutMs = readTimeoutMs))

    /** Encodes part of a path, for ids and search text. */
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The non-empty strings in a JSON array; absent arrays are empty. */
    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

    /** Open WebUI's version, or null if the server can't be reached. */
    suspend fun version(): String? = runCatching {
        JSONObject(request("GET", "/api/version", auth = false)).optString("version").ifBlank { "?" }
    }.getOrNull()

    /** Every model you can use on your server, with each one's default tools and features. */
    suspend fun models(): List<Model> {
        val data = JSONObject(request("GET", "/api/models")).optJSONArray("data") ?: JSONArray()
        return List(data.length()) { i ->
            val m = data.getJSONObject(i)
            val meta = m.optJSONObject("info")?.optJSONObject("meta")
            Model(
                id = m.getString("id"),
                name = m.optString("name").ifBlank { m.getString("id") },
                toolIds = meta?.optJSONArray("toolIds").strings(),
                features = meta?.optJSONArray("defaultFeatureIds").strings().toSet(),
            )
        }
    }

    /** The model to use for Lena's own prompts: your chosen default, then Lena herself, then the first one available. */
    suspend fun defaultModel(c: Context): Model? {
        val all = models()
        Prefs.rememberLena(c, all)
        val wanted = Prefs.model(c)
        return all.firstOrNull { it.id == wanted } ?: Prefs.lenaIn(all) ?: all.firstOrNull()
    }

    /**
     * One page of your chats, newest first: not the pinned ones (see [pinned]) or those in folders, as in
     * Open WebUI's sidebar. Open WebUI sends [CHATS_PER_PAGE] a page.
     */
    suspend fun chats(page: Int = 1): List<ChatItem> = parseChats(request("GET", "/api/v1/chats/?page=$page"))

    /** Your pinned chats. */
    suspend fun pinned(): List<ChatItem> = parseChats(request("GET", "/api/v1/chats/pinned"))

    /** Pins a chat, or unpins it if it's pinned. */
    suspend fun togglePin(id: String) {
        request("POST", "/api/v1/chats/${enc(id)}/pin")
    }

    /** Your most recently used chat with exactly this title (pinned ones first), or null. */
    suspend fun chatTitled(title: String): ChatItem? {
        val pinned = runCatching { pinned() }.getOrDefault(emptyList())
        pinned.filter { it.title == title }.maxByOrNull { it.updatedMillis }?.let { return it }
        return search(title).filter { it.title == title }.maxByOrNull { it.updatedMillis }
    }

    /** Chats matching your search, as Open WebUI's search box finds them. */
    suspend fun search(text: String): List<ChatItem> =
        parseChats(request("GET", "/api/v1/chats/search?text=${enc(text)}"))

    /** Reads Open WebUI's chat list format. */
    private fun parseChats(json: String): List<ChatItem> {
        val a = JSONArray(json)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            ChatItem(
                id = o.getString("id"),
                title = o.optString("title").ifBlank { "New chat" },
                updatedMillis = toMillis(o.optLong("updated_at")),
                active = o.optBoolean("active", false),
                folderId = o.optString("folder_id").takeIf { !o.isNull("folder_id") && it.isNotBlank() },
            )
        }
    }

    // ---- Folders ----------------------------------------------------------------------------
    //
    // Open WebUI's folders group chats ("Group" in the app). Folders can sit inside folders; the app
    // lists them as one list in tree order, with the inner ones' names shown as "Outer › Inner".
    // The feature can be turned off for your account, which comes back as ForbiddenException.

    /** A folder of chats: its name as shown (with its parents'), and whether it's open in Open WebUI's sidebar. */
    data class Folder(val id: String, val name: String, val label: String, val expanded: Boolean)

    /** Your folders, in tree order: each folder followed by the ones inside it, alphabetically. */
    suspend fun folders(): List<Folder> {
        val a = JSONArray(request("GET", "/api/v1/folders/"))
        data class Raw(val id: String, val name: String, val parent: String?, val expanded: Boolean)
        val raw = List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Raw(o.getString("id"), o.optString("name"), o.optString("parent_id").takeIf { !o.isNull("parent_id") && it.isNotBlank() },
                o.optBoolean("is_expanded", false))
        }
        val ids = raw.map { it.id }.toSet()
        val children = raw.groupBy { it.parent?.takeIf { p -> p in ids } }
        val out = mutableListOf<Folder>()

        /** Adds a folder's children under it, depth first; seen guards against a loop in the data. */
        fun addChildren(parent: String?, prefix: String, seen: MutableSet<String>) {
            for (f in children[parent].orEmpty().sortedBy { it.name.lowercase() }) {
                if (!seen.add(f.id)) continue
                val label = if (prefix.isEmpty()) f.name else "$prefix › ${f.name}"
                out += Folder(f.id, f.name, label, f.expanded)
                addChildren(f.id, label, seen)
            }
        }
        addChildren(null, "", mutableSetOf())
        return out
    }

    /** One page of the chats directly in a folder, newest first; Open WebUI sends [FOLDER_CHATS_PER_PAGE] a page. */
    suspend fun folderChats(folderId: String, page: Int = 1): List<ChatItem> =
        parseChats(request("GET", "/api/v1/chats/folder/${enc(folderId)}/list?page=$page")).map { it.copy(folderId = folderId) }

    /** Makes a new folder at the top level and returns it. */
    suspend fun createFolder(name: String): Folder {
        val o = JSONObject(request("POST", "/api/v1/folders/", JSONObject().put("name", name)))
        val id = o.optString("id")
        if (id.isBlank()) throw IOException("Open WebUI didn't make the folder")
        return Folder(id, o.optString("name", name), o.optString("name", name), o.optBoolean("is_expanded", false))
    }

    /** Renames a folder. */
    suspend fun renameFolder(id: String, name: String) {
        request("POST", "/api/v1/folders/${enc(id)}/update", JSONObject().put("name", name))
    }

    /** Deletes a folder (and the folders inside it), keeping its chats: they move out to your chat list. */
    suspend fun deleteFolder(id: String) {
        request("DELETE", "/api/v1/folders/${enc(id)}?delete_contents=false")
    }

    /** Opens or closes a folder, remembered by Open WebUI so its sidebar matches. */
    suspend fun setFolderExpanded(id: String, expanded: Boolean) {
        request("POST", "/api/v1/folders/${enc(id)}/update/expanded", JSONObject().put("is_expanded", expanded))
    }

    /** Moves a chat into a folder, or out of any folder when folderId is null. */
    suspend fun moveChat(chatId: String, folderId: String?) {
        request("POST", "/api/v1/chats/${enc(chatId)}/folder", JSONObject().put("folder_id", folderId ?: JSONObject.NULL))
    }

    /**
     * Open WebUI's timestamps have been seconds, milliseconds or nanoseconds in different places; this
     * tells them apart by size.
     */
    private fun toMillis(t: Long) = when {
        t > 1_000_000_000_000_000L -> t / 1_000_000 // nanoseconds
        t > 1_000_000_000_000L -> t                 // milliseconds
        else -> t * 1000                            // seconds
    }

    /** A whole chat: its title, models and message history. */
    suspend fun chat(id: String): JSONObject = JSONObject(request("GET", "/api/v1/chats/${enc(id)}"))

    /**
     * The chat, or null if it no longer exists (for example, you deleted it). Open WebUI answers a
     * missing chat the same way as a signed-out request, so check which it is.
     */
    suspend fun chatOrNull(id: String): JSONObject? = try {
        chat(id)
    } catch (e: AuthException) {
        if (runCatching { request("GET", "/api/v1/auths/") }.isSuccess) null else throw e
    } catch (e: IOException) {
        if (e.message == "HTTP 404") null else throw e
    }

    /** The last [count] question-and-answer pairs of a chat, oldest first. */
    fun recentTurns(chatResponse: JSONObject, count: Int): List<Pair<String, String>> {
        val history = chatResponse.optJSONObject("chat")?.optJSONObject("history") ?: return emptyList()
        val messages = history.optJSONObject("messages") ?: return emptyList()
        val thread = mutableListOf<JSONObject>()
        var cursor: String? = if (history.isNull("currentId")) null else history.optString("currentId")
        while (cursor != null && thread.size < count * 2 + 2) {
            val m = messages.optJSONObject(cursor) ?: break
            thread.add(0, m)
            cursor = if (m.isNull("parentId")) null else m.optString("parentId")
        }
        val pairs = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < thread.size) {
            val m = thread[i]
            if (m.optString("role") == "user") {
                val reply = thread.getOrNull(i + 1)?.takeIf { it.optString("role") == "assistant" }
                pairs += m.optString("content") to (reply?.optString("content") ?: "")
                i += if (reply != null) 2 else 1
            } else i++
        }
        return pairs.takeLast(count)
    }

    /** The first model of a chat, for the title bar. */
    suspend fun chatModel(id: String): String? =
        chat(id).optJSONObject("chat")?.optJSONArray("models")?.optString(0)?.ifBlank { null }

    /** How many answers are still being written in this chat. */
    suspend fun runningTasks(chatId: String): Int =
        JSONObject(request("GET", "/api/tasks/chat/${enc(chatId)}")).optJSONArray("task_ids")?.length() ?: 0

    /** The chat's title and its latest message, for notifications. */
    suspend fun latest(chatId: String): Pair<String, String> {
        val r = chat(chatId)
        val c = r.optJSONObject("chat") ?: JSONObject()
        val history = c.optJSONObject("history")
        val current = history?.optString("currentId")
        val content = current?.let { history.optJSONObject("messages")?.optJSONObject(it)?.optString("content") } ?: ""
        return r.optString("title").ifBlank { "Lena" } to Markdown.plain(content)
    }

    /** A question that's been sent: the chat it's in and the id of the answer to follow. */
    data class Started(val chatId: String, val messageId: String)

    /**
     * Sends [prompt] as a real Open WebUI chat, answered server-side with the model's tools, the
     * same way the web page does. With [chatId] it continues that chat after [parentId] (its
     * latest answer); otherwise it starts a new chat in your history.
     */
    suspend fun ask(
        prompt: String,
        model: Model,
        chatId: String? = null,
        parentId: String? = null,
        newTitle: String? = null,
        pinNew: Boolean = false,
        contextTokens: Int = 32_768,
        spoken: Boolean = false,
        image: String? = null,
        locked: Boolean = false,
        instruction: String? = null,
    ): Started {
        val turn = ChatTurn.create(prompt, model, image)

        // 1. Save your message and an empty reply into the chat, so it shows in Open WebUI straight
        //    away, and work out what the model should see. Continue the chat if it still exists; if it
        //    was deleted (or there isn't one yet), start a new one.
        val existing = chatId?.let { chatOrNull(it) }
        val id: String
        val thread: List<JSONObject>
        if (existing == null) {
            id = startChat(turn, model, newTitle ?: prompt.lineSequence().first().take(60), pinNew)
            thread = listOf(JSONObject().put("role", "user").put("content", prompt))
        } else {
            id = chatId!!
            thread = continueChat(id, existing, turn, parentId, contextTokens)
        }

        // 2. What the model is sent: notes for this request only, then the thread, with the photo
        //    attached to your message as an image part.
        val conversation = JSONArray()
        val notes = requestNotes(spoken, locked, instruction)
        if (notes.isNotEmpty()) conversation.put(JSONObject().put("role", "system").put("content", notes.joinToString("\n")))
        thread.forEachIndexed { i, m ->
            if (i == thread.lastIndex && image != null) {
                m.put("content", JSONArray()
                    .put(JSONObject().put("type", "text").put("text", prompt))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image))))
            }
            conversation.put(m)
        }

        // 3. Ask Open WebUI to answer it server-side, with the model's tools, as its own page does.
        request("POST", "/api/chat/completions", completionBody(id, turn, model, conversation,
            wantTitle = existing == null && newTitle == null), readTimeoutMs = 120_000)
        return Started(id, turn.replyId)
    }

    /** One question and its (still empty) answer, as Open WebUI stores them in a chat's history. */
    private class ChatTurn(val userId: String, val replyId: String, val user: JSONObject, val reply: JSONObject, val files: JSONArray?) {
        companion object {
            /**
             * A new question and its empty answer, linked as parent and child, with the photo attached if
             * there is one.
             */
            fun create(prompt: String, model: Model, image: String?): ChatTurn {
                val userId = UUID.randomUUID().toString()
                val replyId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis() / 1000
                // A photo goes into the chat as an image file, so Open WebUI shows it with your message.
                val files = image?.let { JSONArray().put(JSONObject().put("type", "image").put("url", it)) }
                val user = JSONObject()
                    .put("id", userId).put("childrenIds", JSONArray().put(replyId))
                    .put("role", "user").put("content", prompt).put("timestamp", now)
                    .put("models", JSONArray().put(model.id))
                if (files != null) user.put("files", files)
                val reply = JSONObject()
                    .put("id", replyId).put("parentId", userId).put("childrenIds", JSONArray())
                    .put("role", "assistant").put("content", "")
                    .put("model", model.id).put("modelName", model.name).put("modelIdx", 0)
                    .put("done", false).put("timestamp", now)
                return ChatTurn(userId, replyId, user, reply, files)
            }
        }
    }

    /** Creates a chat holding just this turn, pinned if asked, and returns its id. */
    private suspend fun startChat(turn: ChatTurn, model: Model, title: String, pin: Boolean): String {
        turn.user.put("parentId", JSONObject.NULL)
        val chat = JSONObject()
            .put("title", title)
            .put("models", JSONArray().put(model.id))
            .put("params", JSONObject())
            .put("history", JSONObject().put("currentId", turn.replyId)
                .put("messages", JSONObject().put(turn.userId, turn.user).put(turn.replyId, turn.reply)))
            .put("messages", JSONArray().put(turn.user).put(turn.reply))
            .put("tags", JSONArray())
            .put("timestamp", System.currentTimeMillis())
        val id = JSONObject(request("POST", "/api/v1/chats/new", JSONObject().put("chat", chat))).getString("id")
        if (pin) runCatching { request("POST", "/api/v1/chats/${enc(id)}/pin") }
        return id
    }

    /**
     * Adds this turn to an existing chat after [parentId] (or its latest message), saves it, and
     * returns the thread the model should see: every question and answer from the start of the
     * chat down this branch to your new message, trimmed to fit half the model's context.
     * Open WebUI keeps a chat as a tree (edits and regenerations make branches), so the thread is
     * found by walking parent links back from the new message rather than by list order.
     */
    private suspend fun continueChat(id: String, existing: JSONObject, turn: ChatTurn, parentId: String?, contextTokens: Int): List<JSONObject> {
        val chat = existing.optJSONObject("chat") ?: JSONObject()
        val history = chat.optJSONObject("history") ?: JSONObject()
        val messages = history.optJSONObject("messages") ?: JSONObject()
        val parent = parentId ?: history.optString("currentId").takeIf { !history.isNull("currentId") }
        turn.user.put("parentId", parent ?: JSONObject.NULL)
        parent?.let { messages.optJSONObject(it) }?.let { p ->
            (p.optJSONArray("childrenIds") ?: JSONArray().also { p.put("childrenIds", it) }).put(turn.userId)
        }
        messages.put(turn.userId, turn.user).put(turn.replyId, turn.reply)
        history.put("messages", messages).put("currentId", turn.replyId)
        val list = (chat.optJSONArray("messages") ?: JSONArray()).put(turn.user).put(turn.reply)
        request("POST", "/api/v1/chats/${enc(id)}", JSONObject().put("chat", JSONObject().put("history", history).put("messages", list)))

        val thread = mutableListOf<JSONObject>()
        var cursor: String? = turn.userId
        while (cursor != null) {
            val m = messages.optJSONObject(cursor) ?: break
            val role = m.optString("role")
            if (role == "user" || role == "assistant") {
                // Her earlier answers go back without the markup Open WebUI adds (tool-call details and the like).
                val content = m.optString("content")
                thread.add(0, JSONObject().put("role", role).put("content", if (role == "assistant") Markdown.clean(content) else content))
            }
            cursor = if (m.isNull("parentId")) null else m.optString("parentId")
        }
        return fitToBudget(thread, contextTokens)
    }

    /**
     * Notes for her with this request only (they're never saved in the chat): the phone's time, said
     * plainly so she never greets the evening with sunshine and knows it even without Home Assistant;
     * the [instruction] behind a scheduled prompt whose chat shows only its name; that the reply will
     * be read aloud, so she uses her spoken style; and, on the lock screen, that the person may not be
     * the phone's owner. That last one asks her to be careful; it isn't a lock, which is why lock-screen
     * mode is off unless you turn it on.
     */
    private fun requestNotes(spoken: Boolean, locked: Boolean, instruction: String?): List<String> {
        val local = java.time.ZonedDateTime.now()
        val part = when (local.hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" }
        val clock = local.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, h:mm a", java.util.Locale.US))
        return listOfNotNull(
            "The time on the phone: $clock ($part).",
            instruction,
            if (spoken) "You're talking out loud: this reply will be read aloud on the phone, so everything you say is spoken." else null,
            if (locked) "The phone is locked, so this may not be its owner. Don't unlock, open or disarm anything, " +
                "and don't share personal details, earlier conversations or what you remember about people. " +
                "If asked, say it needs the phone unlocked." else null,
        )
    }

    /** The request for Open WebUI's /api/chat/completions, the same shape its own page sends. */
    private suspend fun completionBody(chatId: String, turn: ChatTurn, model: Model, conversation: JSONArray, wantTitle: Boolean): JSONObject {
        // The model's default features (web search and the like), and memory as your Open WebUI settings have it.
        val features = JSONObject()
        listOf("web_search", "code_interpreter", "image_generation").forEach { features.put(it, it in model.features) }
        features.put("memory", memoryFeature())
        return JSONObject()
            .put("model", model.id)
            .put("messages", conversation)
            .put("stream", true)
            .put("chat_id", chatId)
            .put("id", turn.replyId)
            // Open WebUI writes the turn into the chat itself and links the reply to this message.
            // Without it, the reply is saved with no parent and the conversation's history breaks.
            .put("user_message", JSONObject()
                .put("id", turn.userId).put("parentId", turn.user.opt("parentId") ?: JSONObject.NULL)
                .put("role", "user").put("content", turn.user.optString("content"))
                .put("models", JSONArray().put(model.id)).put("timestamp", turn.user.optLong("timestamp"))
                .apply { if (turn.files != null) put("files", turn.files) })
            .put("session_id", "lena-${UUID.randomUUID()}")
            .put("tool_ids", JSONArray(model.toolIds))
            .put("features", features)
            // Title generation is only mentioned when it's wanted: Open WebUI treats "title_generation: false"
            // on a chat's first answer as "title it after the first message", which renamed chats Lena
            // had named herself ("Assistant Lena", a scheduled prompt's chat) so they couldn't be found again.
            .put("background_tasks", JSONObject()
                .apply { if (wantTitle) put("title_generation", true) }
                .put("tags_generation", false).put("follow_up_generation", false))
    }

    private var memoryOn: Boolean? = null

    /** Same rule as the web page: your memory setting, or the server's default when you haven't set one. */
    suspend fun memoryFeature(): Boolean = memoryOn ?: runCatching {
        val ui = JSONObject(request("GET", "/api/v1/users/user/settings")).optJSONObject("ui")
        if (ui != null && ui.has("memory") && !ui.isNull("memory")) ui.optBoolean("memory")
        else JSONObject(request("GET", "/api/config")).optJSONObject("features")?.optBoolean("enable_memories", false) ?: false
    }.getOrDefault(false).also { memoryOn = it }

    // ---- Memory and knowledge -------------------------------------------------------------

    /** One of Open WebUI's memories (its own feature, separate from Lena's shared memory in Home Assistant). */
    data class Memory(val id: String, val content: String, val path: String?, val updatedMillis: Long)

    /** Your Open WebUI memories, newest first. */
    suspend fun memories(): List<Memory> {
        val a = JSONArray(request("GET", "/api/v1/memories/"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Memory(o.getString("id"), o.optString("content"),
                o.optString("path").takeIf { !o.isNull("path") && it.isNotBlank() }, toMillis(o.optLong("updated_at")))
        }.sortedByDescending { it.updatedMillis }
    }

    /** Adds an Open WebUI memory. */
    suspend fun addMemory(content: String) {
        request("POST", "/api/v1/memories/add", JSONObject().put("content", content))
    }

    /** Changes an Open WebUI memory's text. */
    suspend fun updateMemory(id: String, content: String) {
        request("POST", "/api/v1/memories/${enc(id)}/update", JSONObject().put("content", content))
    }

    /** Deletes an Open WebUI memory. */
    suspend fun deleteMemory(id: String) {
        request("DELETE", "/api/v1/memories/${enc(id)}")
    }

    /** A knowledge collection, and whether you may change it. */
    data class Knowledge(val id: String, val name: String, val description: String, val canEdit: Boolean)

    /**
     * Your knowledge collections, page by page (at most 20 pages, so a server answering oddly can't loop
     * forever).
     */
    suspend fun knowledgeBases(): List<Knowledge> {
        val out = mutableListOf<Knowledge>()
        var page = 1
        while (page <= 20) {
            val r = JSONObject(request("GET", "/api/v1/knowledge/?page=$page"))
            val items = r.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                out += Knowledge(o.getString("id"), o.optString("name"), o.optString("description"),
                    o.optBoolean("write_access", false))
            }
            if (items.length() == 0 || out.size >= r.optInt("total", out.size)) break
            page++
        }
        return out
    }

    /** A document in a knowledge collection. */
    data class KnowledgeFile(val id: String, val name: String, val addedMillis: Long)

    /** One page of a collection's documents, and the total count. */
    suspend fun knowledgeFiles(knowledgeId: String, page: Int): Pair<List<KnowledgeFile>, Int> {
        val r = JSONObject(request("GET", "/api/v1/knowledge/${enc(knowledgeId)}/files?page=$page"))
        val items = r.optJSONArray("items") ?: JSONArray()
        val files = List(items.length()) { i ->
            val o = items.getJSONObject(i)
            val name = o.optJSONObject("meta")?.optString("name")?.ifBlank { null } ?: o.optString("filename")
            KnowledgeFile(o.getString("id"), name, toMillis(o.optLong("created_at")))
        }
        return files to r.optInt("total", files.size)
    }

    /** Removes a document from a collection (the file itself stays in Open WebUI). */
    suspend fun removeKnowledgeFile(knowledgeId: String, fileId: String) {
        request("POST", "/api/v1/knowledge/${enc(knowledgeId)}/file/remove", JSONObject().put("file_id", fileId))
    }

    // ---- Notes ----------------------------------------------------------------------------
    //
    // Open WebUI's Notes keep each note's text as Markdown in `data.content.md` (with the web editor's
    // own HTML and JSON beside it). Newer notes can have several pages (`data.pages`), and the server
    // then keeps `data.content` as all the pages joined, for searching. The app edits notes with one
    // page and shows notes with several read-only, because joining and splitting pages here could
    // lose someone's work.

    /** A note as the list shows it: the start of its text, how many pages it has, and when it changed. */
    data class NoteItem(
        val id: String,
        val title: String,
        val preview: String,
        val pageCount: Int,
        val pinned: Boolean,
        val updatedMillis: Long,
    )

    /**
     * One note in full. For a note with one page, `text` is that page and `pageId` and `revision` are
     * what saving needs, so a change made meanwhile in the browser is noticed instead of overwritten.
     */
    data class Note(
        val id: String,
        val title: String,
        val text: String,
        val pageCount: Int,
        val pageId: String,
        val revision: Int,
        val canEdit: Boolean,
        val pinned: Boolean,
    ) {
        /** Whether the app may change its text: yours to edit, and only one page. */
        val editable: Boolean get() = canEdit && pageCount == 1
    }

    /** Your notes and the ones shared with you, pinned first, then newest (Open WebUI sends 60 a page). */
    suspend fun notes(): List<NoteItem> {
        val out = mutableListOf<NoteItem>()
        var page = 1
        while (page <= 20) { // at most 20 pages, so a server answering oddly can't loop forever
            val items = JSONArray(request("GET", "/api/v1/notes/?page=$page"))
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val data = o.optJSONObject("data")
                out += NoteItem(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    preview = data?.optJSONObject("content")?.optString("md").orEmpty(),
                    pageCount = data?.optInt("page_count", 1) ?: 1,
                    pinned = o.optBoolean("is_pinned", false),
                    updatedMillis = toMillis(o.optLong("updated_at")),
                )
            }
            if (items.length() < 60) break
            page++
        }
        return out.sortedWith(compareByDescending<NoteItem> { it.pinned }.thenByDescending { it.updatedMillis })
    }

    /** One note in full, with whether you may change it. */
    suspend fun note(id: String): Note {
        val o = JSONObject(request("GET", "/api/v1/notes/${enc(id)}"))
        val data = o.optJSONObject("data") ?: JSONObject()
        val pages = data.optJSONArray("pages")
        // A note nobody has edited since pages arrived has none yet; the server treats it as one page, "main".
        val firstPage = pages?.optJSONObject(0)
        val pageCount = if (pages == null || pages.length() == 0) 1 else pages.length()
        val text = if (pageCount == 1 && firstPage != null) {
            firstPage.optJSONObject("content")?.optString("md").orEmpty()
        } else {
            data.optJSONObject("content")?.optString("md").orEmpty()
        }
        return Note(
            id = o.getString("id"),
            title = o.optString("title"),
            text = text,
            pageCount = pageCount,
            pageId = firstPage?.optString("id")?.ifBlank { null } ?: "main",
            revision = firstPage?.optInt("revision", 0) ?: 0,
            canEdit = o.optBoolean("write_access", false),
            pinned = o.optBoolean("is_pinned", false),
        )
    }

    /**
     * The text of a note as Open WebUI stores it. Only the Markdown is sent: the web editor shows
     * Markdown when there's no HTML, and builds its own HTML the next time it's edited there.
     */
    private fun noteContent(text: String): JSONObject =
        JSONObject().put("json", JSONObject.NULL).put("html", "").put("md", text)

    /** Makes a new note, private to you, and returns its id. */
    suspend fun addNote(title: String, text: String): String {
        val body = JSONObject()
            .put("title", title)
            .put("data", JSONObject().put("content", noteContent(text)))
            .put("meta", JSONObject.NULL)
            .put("access_grants", JSONArray())
        return JSONObject(request("POST", "/api/v1/notes/create", body)).optString("id")
    }

    /**
     * Saves a changed title and text. Only what changed is sent, and the text only with the page's
     * revision, so a note changed in the browser since it was opened throws ConflictException rather
     * than losing that change.
     */
    suspend fun updateNote(note: Note, title: String, text: String) {
        val body = JSONObject()
        if (title != note.title) body.put("title", title)
        if (text != note.text) {
            check(note.editable) { "This note can't be edited here" }
            body.put("data", JSONObject().put("content", noteContent(text)))
            body.put("page_id", note.pageId)
            body.put("page_revision", note.revision)
        }
        if (body.length() == 0) return
        request("POST", "/api/v1/notes/${enc(note.id)}/update", body)
    }

    /** Pins a note to the top of your notes, or unpins it. */
    suspend fun toggleNotePin(id: String) {
        request("POST", "/api/v1/notes/${enc(id)}/pin")
    }

    /** Deletes a note from Open WebUI. */
    suspend fun deleteNote(id: String) {
        request("DELETE", "/api/v1/notes/${enc(id)}/delete")
    }

    /** Deletes a chat from Open WebUI. */
    suspend fun deleteChat(id: String) {
        request("DELETE", "/api/v1/chats/${enc(id)}")
    }

    /** Renames a chat. */
    suspend fun renameChat(id: String, title: String) {
        request("POST", "/api/v1/chats/${enc(id)}", JSONObject().put("chat", JSONObject().put("title", title)))
    }

    /** Your voice to text, with Open WebUI's speech-to-text. */
    suspend fun transcribe(wav: ByteArray): String {
        val boundary = "lena-${UUID.randomUUID()}"
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n" +
            "Content-Type: audio/wav\r\n\r\n"
        val body = head.toByteArray() + wav + "\r\n--$boundary--\r\n".toByteArray()
        val json = String(call("POST", "/api/v1/audio/transcriptions", body, "multipart/form-data; boundary=$boundary",
            readTimeoutMs = 60_000))
        return JSONObject(json).optString("text")
    }

    /** Text to audio, in the voice you've set in Open WebUI. */
    suspend fun speech(text: String): ByteArray =
        call("POST", "/api/v1/audio/speech", JSONObject().put("input", text).toString().toByteArray(),
            readTimeoutMs = 60_000, accept = "audio/*")

    /** The answer as it's written, until it's done (or 15 minutes pass). */
    fun follow(chatId: String, messageId: String): Flow<Reply> = flow {
        val deadline = System.currentTimeMillis() + 15 * 60_000
        var idle = 0
        while (System.currentTimeMillis() < deadline) {
            val msg = chat(chatId).optJSONObject("chat")?.optJSONObject("history")
                ?.optJSONObject("messages")?.optJSONObject(messageId)
            val reply = Reply(msg?.optString("content") ?: "", msg?.optBoolean("done", false) ?: false)
            emit(reply)
            if (reply.done) return@flow
            // Some servers don't mark the message done; no running task for a few checks means it is.
            if (runningTasks(chatId) == 0) {
                if (++idle >= 3) {
                    emit(reply.copy(done = true))
                    return@flow
                }
            } else {
                idle = 0
            }
            delay(800)
        }
        throw IOException("Timed out")
    }
}
