// Checks which failed requests Lena tries again, and how she follows an answer (Retry.kt), away from Android.
// The rule that matters most: nothing that may have reached the server is sent twice unless it's a GET.
// Run by run.sh next to this file.
import io.github.soullink9.lena.AuthException
import io.github.soullink9.lena.ConflictException
import io.github.soullink9.lena.ForbiddenException
import io.github.soullink9.lena.HaAuthException
import io.github.soullink9.lena.HaForbiddenException
import io.github.soullink9.lena.HaHttpException
import io.github.soullink9.lena.Retry
import io.github.soullink9.lena.explained
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

private var failures = 0

private fun check(ok: Boolean, what: String) {
    println((if (ok) "ok    " else "FAIL  ") + what)
    if (!ok) failures++
}

private fun retried(e: IOException, method: String) = Retry.retryable(e, method)

fun main() {
    // Never reached the server: tried again, whatever the method.
    for (e in listOf(ConnectException("refused"), UnknownHostException("ha.example.com"), SocketTimeoutException("connect timed out"),
            EOFException(), IOException("unexpected end of stream on https://ha.example.com/"))) {
        check(retried(e, "POST") && retried(e, "GET"), "${e.javaClass.simpleName} (${e.message}): never reached the server, tried again even for a POST")
    }
    // May have reached the server: only a GET is sent again.
    for (e in listOf(SocketTimeoutException("timeout"), SocketException("Connection reset"), SSLHandshakeException("bad record"))) {
        check(retried(e, "GET") && !retried(e, "POST"), "${e.javaClass.simpleName}: may have reached the server, so a GET is tried again and a POST never")
    }
    // Answers that won't change.
    for (e in listOf(AuthException(), ForbiddenException(), ConflictException(), HaAuthException(), HaForbiddenException())) {
        check(!retried(e, "GET") && !retried(e, "POST"), "${e.javaClass.simpleName}: an answer, not tried again")
    }
    check(!retried(IOException("HTTP 404"), "GET") && !retried(IOException("HTTP 500"), "GET") && !retried(HaHttpException(400, null), "GET"),
        "a 404, a 500 or a 400 is the server's answer, not tried again")
    // A proxy saying the server is restarting.
    for (code in listOf(502, 503, 504)) {
        check(retried(IOException("HTTP $code"), "GET") && retried(HaHttpException(code, null), "GET"), "HTTP $code (a proxy while the server restarts): a GET is tried again")
        check(!retried(IOException("HTTP $code"), "POST") && !retried(HaHttpException(code, "x"), "POST"), "HTTP $code: a POST is not, as the server may have handled it")
    }
    check(Retry.WAITS_MS.size == 2 && Retry.WAITS_MS.all { it in 100..5_000 }, "two more tries at most, each after a short wait (${Retry.WAITS_MS})")
    // An answer whose request got no reply: looked for before it's called a failure, never sent again.
    for (e in listOf(SocketTimeoutException("timeout"), SocketException("Connection reset"), EOFException())) {
        check(Retry.mayBeUnderWay(e), "${e.javaClass.simpleName} after the question was sent: she may be answering, so Lena looks")
    }
    for (e in listOf(AuthException(), ForbiddenException(), IOException("HTTP 500"), ConnectException("refused"),
            UnknownHostException("ow.example.com"), SocketTimeoutException("connect timed out"))) {
        check(!Retry.mayBeUnderWay(e), "${e.javaClass.simpleName} (${e.message}): the server answered or was never reached, so it failed")
    }
    // Following an answer: a chat that's gone or can't be read ends it at once; anything else is looked at again.
    for (e in listOf(AuthException(), ForbiddenException(), IOException("HTTP 404"), IOException("HTTP 400"))) {
        check(Retry.stopsFollowing(e), "${e.javaClass.simpleName} (${e.message}): following the answer stops at once")
    }
    for (e in listOf(IOException("HTTP 502"), IOException("HTTP 429"), IOException("HTTP 408"), SocketTimeoutException("timeout"), ConnectException("refused"))) {
        check(!Retry.stopsFollowing(e), "${e.javaClass.simpleName} (${e.message}): a hiccup, looked at again")
    }
    check(Retry.followWaitMs(5_000) == 800L, "a short chat is looked at often")
    check(Retry.followWaitMs(5_000) < Retry.followWaitMs(500_000) && Retry.followWaitMs(500_000) < Retry.followWaitMs(5_000_000),
        "a longer chat is looked at less often (each look downloads all of it)")
    check(Retry.followWaitMs(50_000_000) <= 5_000L, "even a huge chat is looked at every few seconds")
    // What's said when Home Assistant refuses.
    check(HaAuthException().explained("token turned down") == "token turned down", "a turned-down token says so")
    check(HaForbiddenException().explained("token turned down").contains("ip_bans.yaml"), "a 403 says it's not the token, and how to lift a ban")

    println()
    if (failures > 0) {
        println("$failures check(s) failed.")
        kotlin.system.exitProcess(1)
    }
    println("Lena tries again exactly when she should.")
}
