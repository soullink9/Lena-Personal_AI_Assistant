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
 * Updating Lena from her releases: on GitHub (the public repository [REPO], so no token or setup is
 * needed), or on your own Gitea if you've given its address ([UpdateSource]). The APK is checked three
 * ways before it's offered: against the release's SHA256SUMS, that it's Lena and newer, and that it's
 * signed with the same key as the Lena you have (Android checks that last one again itself), so an
 * update can only ever be one you built, wherever it came from. It's kept until it's installed, so you
 * can leave to change a security setting and come back without downloading again, and deleted once the
 * update is in.
 */
object Updates {
    const val REPO = UpdateSource.GitHub.REPO

    /** Where a token for a private repository used to be kept, removed by [cleanUp]. */
    private const val OLD_TOKEN = "github_token"
    private const val TAG = "Updates"

    /**
     * The latest release: its version (the tag without the v), notes, where to download the APK and the
     * SHA256SUMS file (if there is one), and where it came from, for messages.
     */
    data class Release(
        val version: String, val notes: String, val apkUrl: String, val apkName: String, val size: Long,
        val sumsUrl: String?, val from: String,
    )

    /** Where the updater keeps its own state (what's waiting to install, your Gitea's address). */
    private fun p(c: Context) = c.getSharedPreferences("lena-updates", Context.MODE_PRIVATE)

    /** Your Gitea's address as you typed it (empty: updates come from GitHub). */
    fun giteaAddress(c: Context): String = p(c).getString("gitea", "") ?: ""

    /** Saves your Gitea's address as typed; it's only used once it's one Lena can use ([UpdateSource.gitea]). */
    fun setGiteaAddress(c: Context, typed: String) = p(c).edit().putString("gitea", typed.trim()).apply()

    /** Where updates come from now: GitHub, your Gitea, or null when the typed address can't be used. */
    fun source(c: Context): UpdateSource? = UpdateSource.from(giteaAddress(c))

    /** "GitHub" or "Gitea", for messages ("From Lena's releases on Gitea"). */
    fun sourceName(c: Context): String = source(c)?.name ?: "Gitea"

    /** The folder downloaded APKs wait in: private, and deleted once installed. */
    private fun dir(c: Context) = File(c.filesDir, "updates")

    /** This Lena's version name. */
    fun installed(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** The build number Android compares when updating (versionCode); it has to go up, whatever the version name says. */
    fun installedCode(c: Context): Long =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode }.getOrDefault(0L)

    /** True if [a] is a later version than [b] ("0.5.1" > "0.5.0-beta"); anything after the numbers is ignored. */
    fun newer(a: String, b: String): Boolean {
        val x = a.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    // ---- GitHub or Gitea --------------------------------------------------------------------

    /**
     * A connection to GitHub or your Gitea, without any sign-in (the repository is public). Redirects
     * aren't followed automatically, so a hand-off to file storage is checked to be https first.
     */
    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Accept", accept)
            if (url.startsWith("https://api.github.com/")) setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    /** A refusal in words that say what to do. */
    private fun problem(code: Int, source: UpdateSource): IOException = IOException(
        when {
            source is UpdateSource.GitHub && (code == 403 || code == 429) ->
                "GitHub is limiting checks from this network for now (an hour at most). Try again later."
            source is UpdateSource.Gitea && (code == 401 || code == 403 || code == 404) ->
                "No release found at ${source.page}. Check the address, and that the repository can be seen without signing in to Gitea."
            code == 404 -> "No release found on ${source.name}."
            else -> "${source.name} answered HTTP $code."
        }
    )

    /**
     * The newest release with an APK in it, from GitHub or your Gitea. Both describe a release the same
     * way (Gitea copies GitHub's API); a file is downloaded from GitHub through its API, and from Gitea
     * from its download address.
     */
    suspend fun latest(c: Context): Release = withContext(Dispatchers.IO) {
        val source = source(c) ?: throw IOException(
            "Lena can't use the Gitea address \"${giteaAddress(c)}\". Use its https:// address and your account, like https://gitea.example.com/you."
        )
        val conn = open(source.latestUrl, if (source is UpdateSource.GitHub) "application/vnd.github+json" else "application/json")
        try {
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw IOException("Couldn't reach ${source.name}: ${e.message}", e)
            }
            if (code != 200) throw problem(code, source)
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
            if (apk == null) throw IOException("The latest release on ${source.name} has no APK.")
            fun address(asset: JSONObject): String = when (source) {
                is UpdateSource.GitHub -> "https://api.github.com/repos/$REPO/releases/assets/${asset.getLong("id")}"
                is UpdateSource.Gitea -> asset.getString("browser_download_url")
            }
            Release(
                version = r.optString("tag_name").removePrefix("v").removePrefix("V"),
                notes = r.optString("body"),
                apkUrl = address(apk),
                apkName = apk.getString("name"),
                size = apk.optLong("size"),
                sumsUrl = sums?.let { address(it) },
                from = source.name,
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Streams a release file into [to], following a hand-off to file storage (GitHub always does; Gitea
     * does when its files are kept elsewhere). The download address must be https, like the source.
     */
    private fun fetch(r: Release, url: String, to: File, progress: (Long) -> Unit) {
        if (!url.startsWith("https://")) throw IOException("${r.from} gave an insecure download address.")
        var conn = open(url, "application/octet-stream")
        try {
            var code = conn.responseCode
            if (code in 301..308) {
                val next = conn.getHeaderField("Location") ?: throw IOException("${r.from} sent no download address.")
                if (!next.startsWith("https://")) throw IOException("${r.from} sent an insecure download address.")
                conn.disconnect()
                conn = open(next, "application/octet-stream")
                code = conn.responseCode
            }
            if (code != 200) throw IOException("${r.from} answered HTTP $code for ${to.name}.")
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

    /**
     * Downloads and checks the release's APK; it's then waiting in [pending]. With [reinstall] it
     * may be the same build you have (to repair an install, or after the version name was reset);
     * an older build is refused either way, because Android won't install one over a newer one.
     */
    suspend fun download(c: Context, r: Release, reinstall: Boolean = false, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val d = dir(c).apply { deleteRecursively(); mkdirs() }
        val apk = File(d, "lena-${r.version}.apk")
        try {
            fetch(r, r.apkUrl, apk) { done -> if (r.size > 0) progress(done.toFloat() / r.size) }

            // 1. The checksum published with the release.
            val sumsUrl = r.sumsUrl ?: throw IOException("The release has no SHA256SUMS file to check the APK against.")
            val sumsFile = File(d, "SHA256SUMS")
            fetch(r, sumsUrl, sumsFile) { }
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
            if (archive.longVersionCode < mine.longVersionCode) throw IOException(
                "That APK's build number (${archive.longVersionCode}) is lower than this Lena's (${mine.longVersionCode}), and Android won't " +
                    "install an older build over a newer one. Keep versionCode going up when you reset the version name, or uninstall Lena first."
            )
            if (archive.longVersionCode == mine.longVersionCode && !reinstall) throw IOException("That APK isn't newer than the Lena you have.")
            val sha = { b: ByteArray -> MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) } }
            val theirs = archive.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet() ?: emptySet()
            val ours = mine.signingInfo?.apkContentsSigners?.map { sha(it.toByteArray()) }?.toSet() ?: emptySet()
            if (theirs.isEmpty() || theirs != ours) throw IOException("That APK isn't signed with your key, so it was thrown away.")

            p(c).edit().putString("pending_version", archive.versionName ?: r.version).putString("pending_file", apk.name)
                .putLong("pending_code", archive.longVersionCode).putBoolean("pending_reinstall", reinstall).apply()
            Logs.i(TAG, "Downloaded and checked ${r.version} from ${r.from}" + if (reinstall) " (to install again)" else "")
        } catch (e: Exception) {
            d.deleteRecursively()
            Logs.w(TAG, "Download of ${r.version} failed", e)
            throw e
        }
    }

    /** A checked update waiting to be installed: its version and file. Judged by build number, so a reset version name still works. */
    fun pending(c: Context): Pair<String, File>? {
        val version = p(c).getString("pending_version", null) ?: return null
        val file = File(dir(c), p(c).getString("pending_file", null) ?: return null)
        if (!file.exists()) return null
        val code = p(c).getLong("pending_code", -1)
        val still = when {
            p(c).getBoolean("pending_reinstall", false) -> code >= installedCode(c)
            else -> code > installedCode(c)
        }
        return if (still) version to file else null
    }

    /** True if [release] isn't the version you have: newer, or (after a reset) just different. */
    fun different(release: String, c: Context) = release.removePrefix("v") != installed(c).removePrefix("v")

    /** Deletes a downloaded update that's no longer needed (installed, or no longer newer). */
    fun cleanUp(c: Context) {
        // From when releases could come from another or a private GitHub repository: neither is needed now.
        // (Your Gitea's address, "gitea", is kept: it needs no token.)
        if (Secrets.has(c, OLD_TOKEN) || p(c).contains("repo")) {
            Secrets.put(c, OLD_TOKEN, null)
            p(c).edit().remove("repo").apply()
            Logs.i(TAG, "Removed the saved GitHub token and repository: updates come from Lena's public releases")
        }
        val version = p(c).getString("pending_version", null)
        if (version != null && pending(c) == null) {
            dir(c).deleteRecursively()
            p(c).edit().remove("pending_version").remove("pending_file").remove("pending_code").remove("pending_reinstall").apply()
            Logs.i(TAG, "Removed the downloaded update ($version is installed or no longer needed)")
        }
    }

    /** Deletes the waiting download and forgets it. */
    fun discard(c: Context) {
        dir(c).deleteRecursively()
        p(c).edit().remove("pending_version").remove("pending_file").remove("pending_code").remove("pending_reinstall").apply()
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

    /** Remember to remind you to turn Auto Blocker back on after the update. */
    fun remindAutoBlocker(c: Context, on: Boolean) = p(c).edit().putBoolean("remind_auto_blocker", on).apply()

    /** After an update: tidy up, and the Auto Blocker reminder if you turned it off. */
    fun afterUpdate(c: Context) {
        // Whatever was waiting has just been installed (or replaced by another install): it's not needed now.
        if (p(c).contains("pending_version")) discard(c)
        Logs.i(TAG, "Updated to ${installed(c)}")
        if (!p(c).getBoolean("remind_auto_blocker", false)) return
        remindAutoBlocker(c, false)
        if (c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("updates", "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 0, autoBlocker(), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            7_100,
            Notification.Builder(c, "updates")
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setColor(c.getColor(R.color.lena_cyan))
                .setContentTitle("Lena updated to ${installed(c)}")
                .setContentText("If you turned Auto Blocker off for the update, turn it back on (Security and privacy).")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    /**
     * Where Auto Blocker is switched off and on again: Settings → Security and privacy. Samsung
     * doesn't give apps a way to open the Auto Blocker page itself, so this opens the page it's on.
     */
    fun autoBlocker(): Intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Android's installer reports back here: it needs you to confirm, or it failed. */
class UpdateReceiver : BroadcastReceiver() {
    /**
     * Android's installer reports here: when it needs you to confirm, the confirmation screen is shown; a
     * failure becomes a message that says what to check, and goes in the log.
     */
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
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update. Turn off Auto Blocker (Security and privacy), then tap Install again."
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "That update doesn't fit this Lena (a different signing key?). See Settings → Software version → Logs."
                    else -> "The update didn't install. See Settings → Software version → Logs."
                }
                Toast.makeText(context, friendly, Toast.LENGTH_LONG).show()
            }
        }
    }
}
