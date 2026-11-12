package io.github.soullink9.lena

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// The ways a request to Open WebUI or Home Assistant can fail, and which of them are worth trying again.
// Plain Kotlin, so tools/connection_check can check the rules away from Android.

/** Signed out, or the session expired: open Lena and sign in again. */
class AuthException : IOException("Signed out")

/** Signed in, but your account isn't allowed to do this (or the feature is turned off). */
class ForbiddenException : IOException("Not allowed")

/** Someone changed it somewhere else (in the browser, say) since it was opened here: read it again first. */
class ConflictException : IOException("Changed elsewhere")

/** Home Assistant turned the token down (HTTP 401): it was revoked, or it's wrong. */
open class HaAuthException(message: String = "Home Assistant token rejected") : IOException(message)

/**
 * Home Assistant won't talk to this device at all (HTTP 403), whatever the token. Most often it has banned
 * the device's address after failed sign-ins (ip_bans.yaml); otherwise a proxy or firewall in front of it
 * refuses the device. A kind of [HaAuthException], so every screen that handles a refused token handles
 * this too, but its message says what's really wrong.
 */
class HaForbiddenException : HaAuthException(
    "Home Assistant won't talk to this device (HTTP 403), whatever the token. Most likely it banned this " +
        "device's address after failed sign-ins: remove the address from ip_bans.yaml in Home Assistant's " +
        "config folder and restart Home Assistant (its notifications say which address). Otherwise a proxy " +
        "or firewall in front of Home Assistant is refusing this device."
)

/**
 * What to tell someone when Home Assistant refused: [refused] for a turned-down token, or why it won't talk
 * to this device at all (a 403, [HaForbiddenException]), which a new token wouldn't fix.
 */
fun HaAuthException.explained(refused: String): String = if (this is HaForbiddenException) message ?: refused else refused

/**
 * Home Assistant answered with an error. The message stays "HTTP <code>" (what callers check); [detail] is
 * Home Assistant's own explanation, when it gave one ("There's no tidy to undo").
 */
class HaHttpException(val code: Int, val detail: String?) : IOException("HTTP $code")

/** When to try a failed request again, how long to wait first, and how to follow an answer being written. */
object Retry {
    /** The waits before each new try of a failed request: two more tries in all. */
    val WAITS_MS = listOf(700L, 2_000L)

    /**
     * What a reverse proxy answers while the server behind it restarts (bad gateway, unavailable, gateway
     * timeout): the server didn't handle the request, so a GET is worth trying again.
     */
    private val RESTARTING = setOf(502, 503, 504)

    /**
     * Whether a failed request is worth trying again. A request that may already have reached the server
     * is only repeated if it's a GET, so nothing is ever sent twice. Answers that won't change (signed out,
     * not allowed, a refused token, most HTTP errors) aren't tried again.
     */
    fun retryable(e: IOException, method: String): Boolean {
        val get = method == "GET"
        return when {
            e is AuthException || e is ForbiddenException || e is ConflictException || e is HaAuthException -> false
            e.message?.startsWith("HTTP ") == true -> get && httpCode(e) in RESTARTING
            // Never reached the server: no route, no name, no connection.
            e is ConnectException || e is UnknownHostException || e is NoRouteToHostException -> true
            e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true -> true
            // A reused connection the server had already closed: the request was never read.
            e is EOFException || e.message?.contains("unexpected end of stream", ignoreCase = true) == true -> true
            // Anything else may have reached the server; only a GET is safe to send again.
            get -> e is SocketTimeoutException || e is SocketException || e is SSLException
            else -> false
        }
    }

    /**
     * Whether a failed request might still have reached the server and be under way there: the answer
     * timed out or the connection broke after it was sent. Not when the server answered (signed out, not
     * allowed, an HTTP error), which means it didn't take the request on. Lena then looks before saying it
     * failed, rather than sending it again ([Api.ask]).
     */
    fun mayBeUnderWay(e: IOException): Boolean = when {
        e is AuthException || e is ForbiddenException || e is ConflictException || e is HaAuthException -> false
        e.message?.startsWith("HTTP ") == true -> false
        e is ConnectException || e is UnknownHostException || e is NoRouteToHostException -> false
        e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true -> false
        else -> true
    }

    /**
     * Whether following an answer should give up at once on [e] rather than look again: the answer can't
     * be read (signed out, not allowed), or the chat is gone (deleted while she answered), and looking again
     * won't change that. A request timeout (408) or too many requests (429) are worth another look.
     */
    fun stopsFollowing(e: IOException): Boolean {
        if (e is AuthException || e is ForbiddenException) return true
        val code = httpCode(e) ?: return false
        return code in 400..499 && code != 408 && code != 429
    }

    /**
     * How long to wait between looks at an answer being written, by the size of the chat it's in (in
     * characters): each look downloads the whole chat, so a long chat is looked at less often.
     */
    fun followWaitMs(chatChars: Int): Long = when {
        chatChars < 200_000 -> 800L
        chatChars < 1_000_000 -> 1_500L
        else -> 2_500L
    }

    /** The status code in an "HTTP 503" failure, or null. */
    private fun httpCode(e: IOException): Int? =
        (e as? HaHttpException)?.code ?: e.message?.removePrefix("HTTP ")?.substringBefore(' ')?.toIntOrNull()
}
