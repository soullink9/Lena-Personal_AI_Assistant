package io.github.soullink9.lena

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Lena's own log: what went wrong, warnings, and what she was doing, kept in a small file in
 * the app's private storage (two files of at most 256 KB, the older dropped) and readable under
 * Settings → Software version → Logs. It records errors and events only: never messages, answers, tokens or
 * passwords. Everything also goes to Android's log under "Lena/…".
 */
object Logs {
    /**
     * How serious an entry is. The letter is how it's stored in the file; the priority is Android's, for
     * its own log.
     */
    enum class Level(val letter: Char, val priority: Int) {
        INFO('I', Log.INFO), WARN('W', Log.WARN), ERROR('E', Log.ERROR);

        companion object {
            /** The level for a stored letter; anything unknown reads as info. */
            fun of(c: Char) = entries.firstOrNull { it.letter == c } ?: INFO
        }
    }

    /** One log entry, as Settings → Software version → Logs shows it. */
    data class Entry(val time: Long, val level: Level, val tag: String, val message: String)

    private const val MAX = 256 * 1024
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "lena-log").apply { isDaemon = true } }
    @Volatile private var dir: File? = null

    /** Points the log at the app's private storage. Called first thing when the app starts. */
    fun init(c: Context) {
        dir = File(c.filesDir, "logs").apply { mkdirs() }
    }

    /** Something she did, for the Everything view. */
    fun i(tag: String, message: String) = add(Level.INFO, tag, message, null)
    /** Something that went wrong but was handled; shown under Problems. */
    fun w(tag: String, message: String, error: Throwable? = null) = add(Level.WARN, tag, message, error)
    /** Something that failed; shown under Problems. */
    fun e(tag: String, message: String, error: Throwable? = null) = add(Level.ERROR, tag, message, error)

    /** Lena is about to crash: written straight away, not queued, so it's saved before she goes. */
    fun crash(thread: String, error: Throwable) {
        val line = line(Level.ERROR, "Crash", "Lena crashed (thread $thread)", error)
        runCatching { synchronized(this) { append(line) } }
    }

    /**
     * Writes to Android's log at once and to the file on a background thread, so logging never slows the
     * screen down.
     */
    private fun add(level: Level, tag: String, message: String, error: Throwable?) {
        Log.println(level.priority, "Lena/$tag", message + (error?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
        val line = line(level, tag, message, error)
        writer.execute { runCatching { synchronized(this) { append(line) } } }
    }

    /**
     * One line of the file: time, level, tag and message separated by tabs, with the message's own tabs,
     * backslashes and line breaks escaped so one entry is always one line. Credentials are blanked before
     * anything is written.
     */
    private fun line(level: Level, tag: String, message: String, error: Throwable?): String {
        val text = redact(message + (error?.let { "\n" + describe(it) } ?: ""))
        return "${System.currentTimeMillis()}\t${level.letter}\t${tag.replace('\t', ' ')}\t${text.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", " ")}\n"
    }

    /** The exception and where it happened, without pages of framework frames. */
    private fun describe(t: Throwable): String {
        val out = StringBuilder()
        var e: Throwable? = t
        var depth = 0
        while (e != null && depth < 4) {
            out.append(if (depth == 0) "" else "Caused by: ").append(e.javaClass.name).append(": ").append(e.message ?: "").append('\n')
            e.stackTrace.take(if (depth == 0) 14 else 6).forEach { out.append("    at ").append(it).append('\n') }
            e = e.cause
            depth++
        }
        return out.toString().trimEnd()
    }

    /** Belt and braces: anything that looks like a credential is blanked out. */
    private fun redact(s: String): String = s
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+"), "$1•••")
        .replace(Regex("(?i)(token|password|authsig)=[^&\\s]+"), "$1=•••")
        .replace(Regex("\\b(ghp|github_pat|gho)_[A-Za-z0-9_]+"), "•••")

    /**
     * Appends a line. Past 256 KB the file becomes the old one (replacing the previous old one), so the log
     * never takes more than about half a megabyte.
     */
    private fun append(line: String) {
        val d = dir ?: return
        val f = File(d, "lena.log")
        if (f.length() > MAX) {
            File(d, "lena.1.log").delete()
            f.renameTo(File(d, "lena.1.log"))
        }
        File(d, "lena.log").appendText(line)
    }

    /** Everything kept, newest first. */
    fun read(): List<Entry> {
        val d = dir ?: return emptyList()
        val lines = synchronized(this) {
            listOf(File(d, "lena.1.log"), File(d, "lena.log")).filter { it.exists() }.flatMap { it.readLines() }
        }
        return lines.mapNotNull { l ->
            val parts = l.split('\t', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val message = Regex("\\\\(\\\\|n)").replace(parts[3]) { if (it.value == "\\n") "\n" else "\\" }
            Entry(time, Level.of(parts[1].firstOrNull() ?: 'I'), parts[2], message)
        }.reversed()
    }

    /** Deletes both files, for Clear in Settings → Software version → Logs. */
    fun clear() {
        val d = dir ?: return
        synchronized(this) {
            File(d, "lena.log").delete()
            File(d, "lena.1.log").delete()
        }
    }

    /** Android's own log for Lena's current run (an app can only read its own). */
    fun android(): List<String> = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "3000").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readLines().also { p.waitFor() }
    }.getOrDefault(emptyList()).reversed()
}

/**
 * Starts the log, catches crashes into it, tidies away an update once it's installed, readies the ringing
 * and starts watching the network ([Connections.watchNetwork]).
 */
class LenaApp : Application() {
    /**
     * Starts the log before anything else, then records any crash before Android's own handler ends the
     * app, and tidies away an update that has been installed.
     */
    override fun onCreate() {
        super.onCreate()
        Logs.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Logs.crash(thread.name, error)
            previous?.uncaughtException(thread, error)
        }
        Updates.cleanUp(this)
        AppVisible.track(this)
        Ringing.channel(this)
        // Android says when the network comes back, so waiting screens and requests carry on at once.
        Connections.watchNetwork(this)
    }
}
