package io.github.soullink9.lena

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updating Lena from your own GitHub repository's releases. A private repository needs a GitHub
 * token that can read it (kept encrypted, like the Home Assistant one, and sent only to GitHub's
 * API). The APK is checked three ways before it's offered: against the release's SHA256SUMS,
 * that it's Lena and newer, and that it's signed with the same key as the Lena you have (Android
 * checks that last one again itself). It's kept until it's installed, so you can leave to change
 * a security setting and come back without downloading again, and deleted once the update is in.
 */
object Updates {
    const val DEFAULT_REPO = "soullink9/Lena-Personal_AI_Assistant"
    const val GITHUB_TOKEN = "github_token"
    private const val TAG = "Updates"

    data class Release(val version: String, val notes: String, val apkId: Long, val apkName: String, val size: Long, val sumsId: Long?)

    private fun p(c: Context) = c.getSharedPreferences("lena-updates", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "updates")

    fun repo(c: Context): String = p(c).getString("repo", null)?.ifBlank { null } ?: DEFAULT_REPO
    fun setRepo(c: Context, repo: String) = p(c).edit().putString("repo", repo.trim()).apply()

    fun installed(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** True if [a] is a later version than [b] ("2.14.0" > "2.13.2"). */
    fun newer(a: String, b: String): Boolean {
        val x = a.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    // ---- GitHub ---------------------------------------------------------------------------

    private fun open(url: String, accept: String, auth: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false // the file is handed off to GitHub's storage; follow that without the token
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (auth != null) setRequestProperty("Authorization", "Bearer $auth")
        }

    private fun problem(code: Int): IOException = IOException(
        when (code) {
            401 -> "GitHub didn't accept the token. Check it hasn't expired."
            403 -> "GitHub refused: the token may not have read access to the repository's contents."
            404 -> "No release found. Check the repository name, and that the token can read it."
            else -> "GitHub answered HTTP $code."
        }
    )

    /** The newest release with an APK in it. */
    suspend fun latest(c: Context): Release = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/${repo(c)}/releases/latest", "application/vnd.github+json", Secrets.get(c, GITHUB_TOKEN))
        try {
            val code = conn.responseCode
            if (code != 200) throw problem(code)
            val r = JSONObject(conn.inputStream.use { String(it.readBytes()) })
            val assets = r.optJSONArray("assets")
            var apk: JSONObject? = null
            var sums: JSONObject? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk")) apk = a
                if (name == "SHA256SUMS") sums = a
            }
            if (apk == null) throw IOException("The latest release has no APK.")
            Release(
                version = r.optString("tag_name").removePrefix("v"),
                notes = r.optString("body"),
                apkId = apk.getLong("id"),
                apkName = apk.getString("name"),
                size = apk.optLong("size"),
                sumsId = sums?.getLong("id"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Streams a release file into [to], following GitHub's hand-off to its storage. */
    private fun fetch(c: Context, assetId: Long, to: File, progress: (Long) -> Unit) {
        var conn = open("https://api.github.com/repos/${repo(c)}/releases/assets/$assetId", "application/octet-stream", Secrets.get(c, GITHUB_TOKEN))
        try {
            var code = conn.responseCode
            if (code in 301..308) {
                val next = conn.getHeaderField("Location") ?: throw IOException("GitHub sent no download address.")
                if (!next.startsWith("https://")) throw IOException("GitHub sent an insecure download address.")
                conn.disconnect()
                conn = open(next, "application/octet-stream", null)
                code = conn.responseCode
            }
            if (code != 200) throw problem(code)
            conn.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                        progress(total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads and checks the release's APK; it's then waiting in [pending]. */
    suspend fun download(c: Context, r: Release, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val d = dir(c).apply { deleteRecursively(); mkdirs() }
        val apk = File(d, "lena-${r.version}.apk")
        try {
            fetch(c, r.apkId, apk) { done -> if (r.size > 0) progress(done.toFloat() / r.size) }

            // 1. The checksum published with the release.
            val sumsId = r.sumsId ?: throw IOException("The release has no SHA256SUMS file to check the APK against.")
            val sumsFile = File(d, "SHA256SUMS")
            fetch(c, sumsId, sumsFile) { }
            val expected = sumsFile.readLines().map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size >= 2 && it.last().removePrefix("*") == r.apkName }?.first()?.lowercase()
                ?: throw IOException("SHA256SUMS doesn't list ${r.apkName}.")
            sumsFile.delete()
            val actual = MessageDigest.getInstance("SHA-256").let { md ->
                apk.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            if (actual != expected) throw IOException("The download doesn't match its checksum, so it was thrown away.")

            // 2. It's Lena, newer, and signed with the same key as this one.
            val pm = c.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archive: PackageInfo = pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
                ?: throw IOException("That file isn't an app.")
            if (archive.packageName != c.packageName) throw IOException("That APK isn't Lena.")
            val mine = pm.getPackageInfo(c.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            if (archive.longVersionCode <= mine.longVersionCode) throw IOException("That APK isn't newer than the Lena you have.")
            val sha = { b: ByteArray -> MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) } }
            val theirs = archive.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet() ?: emptySet()
            val ours = mine.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet() ?: emptySet()
            if (theirs.isEmpty() || theirs != ours) throw IOException("That APK isn't signed with your key, so it was thrown away.")

            p(c).edit().putString("pending_version", archive.versionName ?: r.version).putString("pending_file", apk.name).apply()
            Logs.i(TAG, "Downloaded and checked ${r.version}")
        } catch (e: Exception) {
            d.deleteRecursively()
            Logs.w(TAG, "Download of ${r.version} failed", e)
            throw e
        }
    }

    /** A checked update waiting to be installed: its version and file. */
    fun pending(c: Context): Pair<String, File>? {
        val version = p(c).getString("pending_version", null) ?: return null
        val file = File(dir(c), p(c).getString("pending_file", null) ?: return null)
        return if (file.exists() && newer(version, installed(c))) version to file else null
    }

    /** Deletes a downloaded update that's no longer needed (installed, or no longer newer). */
    fun cleanUp(c: Context) {
        val version = p(c).getString("pending_version", null)
        if (version != null && pending(c) == null) {
            dir(c).deleteRecursively()
            p(c).edit().remove("pending_version").remove("pending_file").apply()
            Logs.i(TAG, "Removed the downloaded update ($version is installed or no longer needed)")
        }
    }

    fun discard(c: Context) {
        dir(c).deleteRecursively()
        p(c).edit().remove("pending_version").remove("pending_file").apply()
    }

    // ---- Installing -----------------------------------------------------------------------

    /** Lena may install apps (Android's "Install unknown apps" for Lena). */
    fun mayInstall(c: Context): Boolean = c.packageManager.canRequestPackageInstalls()

    /** Hands the waiting update to Android's installer, which asks you to confirm. */
    fun install(c: Context) {
        val (version, file) = pending(c) ?: return
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.packageName)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("lena.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = PendingIntent.getBroadcast(
                c, id, Intent(c, UpdateReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, // the installer adds its result
            )
            session.commit(result.intentSender)
        }
        Logs.i(TAG, "Installing $version")
    }

    /** Remember to remind you to turn Play Protect back on after the update. */
    fun remindPlayProtect(c: Context, on: Boolean) = p(c).edit().putBoolean("remind_play_protect", on).apply()

    /** After an update: tidy up, and the Play Protect reminder if you paused it. */
    fun afterUpdate(c: Context) {
        cleanUp(c)
        Logs.i(TAG, "Updated to ${installed(c)}")
        if (!p(c).getBoolean("remind_play_protect", false)) return
        remindPlayProtect(c, false)
        if (c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("updates", "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 0, playProtect(), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            7_100,
            Notification.Builder(c, "updates")
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setColor(c.getColor(R.color.lena_cyan))
                .setContentTitle("Lena updated to ${installed(c)}")
                .setContentText("If you paused Play Protect for the update, turn it back on.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Google Play Protect's settings (where scanning can be paused and turned back on). */
    fun playProtect(): Intent = Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Android's installer reports back here: it needs you to confirm, or it failed. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Logs.i("Updates", "Installed")
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                Logs.e("Updates", "The install didn't go through: $why")
                val friendly = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled. Tap Install to try again."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update. Check Play Protect, then tap Install again."
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "That update doesn't fit this Lena (a different signing key?). See Settings → Logs."
                    else -> "The update didn't install. See Settings → Logs."
                }
                Toast.makeText(context, friendly, Toast.LENGTH_LONG).show()
            }
        }
    }
}
