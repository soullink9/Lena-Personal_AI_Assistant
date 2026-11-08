package io.github.soullink9.lena

import java.net.URI

/**
 * Where Lena looks for her updates: her public releases on GitHub (the default), or the same releases on
 * your own Gitea, for when the project lives there first and GitHub is a mirror, or can't be reached.
 *
 * The Gitea address is typed in Settings → Software version → Updates, as your Gitea and account
 * ("https://gitea.example.com/Soullink"), or the repository itself
 * ("https://gitea.example.com/Soullink/Lena-Personal_AI_Assistant"). Gitea may sit under a path of its
 * own ("https://example.com/gitea/Soullink"). The repository has to be readable without signing in, as
 * on GitHub: Lena keeps no token for it.
 *
 * Plain Kotlin and java.net only, so it can be checked away from Android.
 */
sealed class UpdateSource {
    /** Shown in messages: "Lena's releases on GitHub", "Couldn't reach Gitea". */
    abstract val name: String

    /** Where the newest release is described (the API's "latest release": not a draft, not a pre-release). */
    abstract val latestUrl: String

    object GitHub : UpdateSource() {
        const val REPO = "soullink9/Lena-Personal_AI_Assistant"
        override val name = "GitHub"
        override val latestUrl = "https://api.github.com/repos/$REPO/releases/latest"
    }

    /** Lena's repository on a Gitea: [api] is its API address, ending in /repos/<owner>/<repo>. */
    data class Gitea(val api: String, val page: String) : UpdateSource() {
        override val name = "Gitea"
        override val latestUrl = "$api/releases/latest"
    }

    companion object {
        /** The repository's name, the same on Gitea as on GitHub (Gitea mirrors it there). */
        val REPO_NAME = GitHub.REPO.substringAfter('/')

        /**
         * A typed Gitea address as the repository it means, or null if Lena can't use it: it must be
         * https (as every address Lena talks to) and name at least the account. The last part of the
         * path is the account, unless it's the repository's own name, when the part before it is.
         */
        fun gitea(typed: String): Gitea? {
            val text = typed.trim().removeSuffix("/").removeSuffix(".git")
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank() || uri.query != null || uri.fragment != null) return null
            val parts = (uri.path ?: "").split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val named = parts.size >= 2 && parts.last().equals(REPO_NAME, ignoreCase = true)
            val owner = if (named) parts[parts.size - 2] else parts.last()
            val repo = if (named) parts.last() else REPO_NAME
            val under = parts.dropLast(if (named) 2 else 1).joinToString("") { "/$it" } // Gitea under a path of its own
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val server = "https://${uri.host}$port$under"
            return Gitea(api = "$server/api/v1/repos/$owner/$repo", page = "$server/$owner/$repo")
        }

        /** What a typed address means: GitHub when it's empty, its Gitea when it's usable, else null. */
        fun from(typed: String): UpdateSource? = if (typed.isBlank()) GitHub else gitea(typed)
    }
}
