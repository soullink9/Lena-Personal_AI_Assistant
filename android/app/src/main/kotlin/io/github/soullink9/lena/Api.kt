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

data class Model(val id: String, val name: String, val toolIds: List<String>, val features: Set<String>)

data class ChatItem(val id: String, val title: String, val updatedMillis: Long, val active: Boolean)

data class Reply(val content: String, val done: Boolean)

/**
 * Talks to your Open WebUI with the sign-in you already made inside Lena.
 *
 * Open WebUI keeps your session in a `token` cookie (password, LDAP and single sign-on alike).
 * Lena reads it from its own web view storage and sends it only to your server's address.
 * If a sign-in proxy such as Authentik sits in front of Open WebUI, its cookie for your server's
 * address is sent along too, exactly as the web view would.
 */
class Api(server: String) {

    private val base = server.trimEnd('/')

    companion object {
        fun from(c: Context): Api? = Prefs.server(c)?.let { Api(it) }

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

    private suspend fun cookies(): String? = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(base)
    }

    private fun tokenFrom(cookies: String?): String? = cookies?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("token=") }
        ?.substringAfter("token=")
        ?.ifBlank { null }

    /** True when Lena holds a sign-in for your server. */
    suspend fun signedIn(): Boolean = tokenFrom(cookies()) != null

    private suspend fun call(
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        auth: Boolean = true,
        readTimeoutMs: Int = 30_000,
        accept: String = "application/json",
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
                val code = conn.responseCode
                if (code == 401 || code in 300..399) throw AuthException()
                if (code == 403) throw ForbiddenException()
                val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) throw IOException("HTTP $code")
                bytes
            } finally {
                conn.disconnect()
            }
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        readTimeoutMs: Int = 30_000,
    ): String = String(call(method, path, body?.toString()?.toByteArray(), auth = auth, readTimeoutMs = readTimeoutMs))

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

    /** Open WebUI's version, or null if the server can't be reached. */
    suspend fun version(): String? = runCatching {
        JSONObject(request("GET", "/api/version", auth = false)).optString("version").ifBlank { "?" }
    }.getOrNull()

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

    suspend fun chats(): List<ChatItem> = parseChats(request("GET", "/api/v1/chats/?page=1"))

    suspend fun search(text: String): List<ChatItem> =
        parseChats(request("GET", "/api/v1/chats/search?text=${enc(text)}"))

    private fun parseChats(json: String): List<ChatItem> {
        val a = JSONArray(json)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            ChatItem(
                id = o.getString("id"),
                title = o.optString("title").ifBlank { "New chat" },
                updatedMillis = toMillis(o.optLong("updated_at")),
                active = o.optBoolean("active", false),
            )
        }
    }

    private fun toMillis(t: Long) = when {
        t > 1_000_000_000_000_000L -> t / 1_000_000 // nanoseconds
        t > 1_000_000_000_000L -> t                 // milliseconds
        else -> t * 1000                            // seconds
    }

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
    ): Started {
        val userId = UUID.randomUUID().toString()
        val replyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000

        val user = JSONObject()
            .put("id", userId).put("childrenIds", JSONArray().put(replyId))
            .put("role", "user").put("content", prompt).put("timestamp", now)
            .put("models", JSONArray().put(model.id))
        val reply = JSONObject()
            .put("id", replyId).put("parentId", userId).put("childrenIds", JSONArray())
            .put("role", "assistant").put("content", "")
            .put("model", model.id).put("modelName", model.name).put("modelIdx", 0)
            .put("done", false).put("timestamp", now)

        val id: String
        var conversation: JSONArray
        // Continue the chat if it still exists; if it was deleted, start a new one.
        val existing = chatId?.let { chatOrNull(it) }
        if (existing == null) {
            user.put("parentId", JSONObject.NULL)
            val chat = JSONObject()
                .put("title", newTitle ?: prompt.lineSequence().first().take(60))
                .put("models", JSONArray().put(model.id))
                .put("params", JSONObject())
                .put("history", JSONObject().put("currentId", replyId)
                    .put("messages", JSONObject().put(userId, user).put(replyId, reply)))
                .put("messages", JSONArray().put(user).put(reply))
                .put("tags", JSONArray())
                .put("timestamp", now * 1000)
            id = JSONObject(request("POST", "/api/v1/chats/new", JSONObject().put("chat", chat))).getString("id")
            if (pinNew) runCatching { request("POST", "/api/v1/chats/${enc(id)}/pin") }
            conversation = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        } else {
            val chat = existing.optJSONObject("chat") ?: JSONObject()
            val history = chat.optJSONObject("history") ?: JSONObject()
            val messages = history.optJSONObject("messages") ?: JSONObject()
            val parent = parentId ?: history.optString("currentId").takeIf { !history.isNull("currentId") }
            user.put("parentId", parent ?: JSONObject.NULL)
            parent?.let { messages.optJSONObject(it) }?.let { p ->
                (p.optJSONArray("childrenIds") ?: JSONArray().also { p.put("childrenIds", it) }).put(userId)
            }
            messages.put(userId, user).put(replyId, reply)
            history.put("messages", messages).put("currentId", replyId)
            val list = (chat.optJSONArray("messages") ?: JSONArray()).put(user).put(reply)
            id = chatId!!
            request("POST", "/api/v1/chats/${enc(id)}",
                JSONObject().put("chat", JSONObject().put("history", history).put("messages", list)))
            // The model sees the whole thread up to your new message.
            val thread = mutableListOf<JSONObject>()
            var cursor: String? = userId
            while (cursor != null) {
                val m = messages.optJSONObject(cursor) ?: break
                val role = m.optString("role")
                if (role == "user" || role == "assistant") {
                    val content = m.optString("content")
                    thread.add(0, JSONObject().put("role", role)
                        .put("content", if (role == "assistant") Markdown.clean(content) else content))
                }
                cursor = if (m.isNull("parentId")) null else m.optString("parentId")
            }
            conversation = JSONArray(fitToBudget(thread, contextTokens))
        }

        // Spoken turns: tell her the reply will be read aloud, so she uses her spoken style (no lists or
        // formatting, short). Sent with this request only; it isn't saved in the chat.
        if (spoken) {
            val withHint = JSONArray().put(JSONObject().put("role", "system").put("content",
                "You're talking out loud: this reply will be read aloud on the phone, so everything you say is spoken."))
            for (i in 0 until conversation.length()) withHint.put(conversation.get(i))
            conversation = withHint
        }

        val features = JSONObject()
        listOf("web_search", "code_interpreter", "image_generation").forEach { features.put(it, it in model.features) }
        features.put("memory", memoryFeature())

        val body = JSONObject()
            .put("model", model.id)
            .put("messages", conversation)
            .put("stream", true)
            .put("chat_id", id)
            .put("id", replyId)
            // Open WebUI 0.12+ writes the turn into the chat itself and links the reply to this message.
            // Without it, the reply is saved with no parent and the conversation's history breaks.
            .put("user_message", JSONObject()
                .put("id", userId).put("parentId", user.opt("parentId") ?: JSONObject.NULL)
                .put("role", "user").put("content", prompt)
                .put("models", JSONArray().put(model.id)).put("timestamp", now))
            .put("session_id", "lena-${UUID.randomUUID()}")
            .put("tool_ids", JSONArray(model.toolIds))
            .put("features", features)
            .put("background_tasks", JSONObject()
                .put("title_generation", existing == null && newTitle == null)
                .put("tags_generation", false).put("follow_up_generation", false))
        request("POST", "/api/chat/completions", body, readTimeoutMs = 120_000)
        return Started(id, replyId)
    }

    private var memoryOn: Boolean? = null

    /** Same rule as the web page: your memory setting, or the server's default when you haven't set one. */
    suspend fun memoryFeature(): Boolean = memoryOn ?: runCatching {
        val ui = JSONObject(request("GET", "/api/v1/users/user/settings")).optJSONObject("ui")
        if (ui != null && ui.has("memory") && !ui.isNull("memory")) ui.optBoolean("memory")
        else JSONObject(request("GET", "/api/config")).optJSONObject("features")?.optBoolean("enable_memories", false) ?: false
    }.getOrDefault(false).also { memoryOn = it }

    // ---- Memory and knowledge -------------------------------------------------------------

    data class Memory(val id: String, val content: String, val path: String?, val updatedMillis: Long)

    suspend fun memories(): List<Memory> {
        val a = JSONArray(request("GET", "/api/v1/memories/"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Memory(o.getString("id"), o.optString("content"),
                o.optString("path").takeIf { !o.isNull("path") && it.isNotBlank() }, toMillis(o.optLong("updated_at")))
        }.sortedByDescending { it.updatedMillis }
    }

    suspend fun addMemory(content: String) {
        request("POST", "/api/v1/memories/add", JSONObject().put("content", content))
    }

    suspend fun updateMemory(id: String, content: String) {
        request("POST", "/api/v1/memories/${enc(id)}/update", JSONObject().put("content", content))
    }

    suspend fun deleteMemory(id: String) {
        request("DELETE", "/api/v1/memories/${enc(id)}")
    }

    data class Knowledge(val id: String, val name: String, val description: String, val canEdit: Boolean)

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

    suspend fun removeKnowledgeFile(knowledgeId: String, fileId: String) {
        request("POST", "/api/v1/knowledge/${enc(knowledgeId)}/file/remove", JSONObject().put("file_id", fileId))
    }

    suspend fun deleteChat(id: String) {
        request("DELETE", "/api/v1/chats/${enc(id)}")
    }

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
