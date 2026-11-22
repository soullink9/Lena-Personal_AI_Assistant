// Checks how much conversation a request to Open WebUI carries and when a quiet chat is compacted
// (Compaction), away from Android. Run by run.sh next to this file.
import io.github.soullink9.lena.Compaction

private var failures = 0

private fun check(ok: Boolean, what: String) {
    println((if (ok) "ok    " else "FAIL  ") + what)
    if (!ok) failures++
}

/**
 * Open WebUI's own estimate of a request (utils/context_compaction.py): 4 characters a token, plus 4 tokens
 * a message, plus the system message. The app's trimming counts a message as its length plus 16 characters,
 * which is the same thing.
 */
private fun openWebUiEstimate(system: String, messages: List<String>): Int =
    system.length / 4 + messages.sumOf { it.length / 4 + 4 }

/** The newest messages that fit [budgetTokens], as the app's trimming keeps them. */
private fun fit(messages: List<String>, budgetTokens: Int): List<String> {
    var used = 0
    val kept = mutableListOf<String>()
    for (m in messages.reversed()) {
        val size = m.length + 16
        if (kept.isNotEmpty() && used + size > budgetTokens * 4) break
        used += size
        kept.add(0, m)
    }
    return kept
}

fun main() {
    // Compaction off: Open WebUI reports no threshold, and the conversation gets half the context as before.
    check(Compaction.threshold(null, null) == null, "compaction off: no threshold")
    check(Compaction.threshold(null, 8000) == null, "compaction off: a model's own threshold doesn't turn it on")
    check(Compaction.historyTokens(32_768, null) == 16_384, "compaction off: half of 32768")
    check(Compaction.historyTokens(1000, null) == 1024, "a tiny context still carries something")

    // Compaction on: the lower of the server's and the model's own threshold counts.
    check(Compaction.threshold(80_000, null) == 80_000, "server threshold alone")
    check(Compaction.threshold(80_000, 12_000) == 12_000, "a model's lower threshold wins")
    check(Compaction.threshold(10_000, 50_000) == 10_000, "a model's higher threshold can't go over the server's")
    check(Compaction.threshold(80_000, 0) == 80_000, "a model threshold of 0 means none")

    // A high threshold changes nothing; a low one keeps requests under it.
    check(Compaction.historyTokens(32_768, 80_000) == 16_384, "threshold 80000: still half of 32768")
    check(Compaction.historyTokens(32_768, 16_000) == 12_000, "threshold 16000: three quarters of it")
    check(Compaction.historyTokens(32_768, 8_000) == 6_000, "threshold 8000: three quarters of it")

    // The bug this fixes: a long Assistant Lena chat, threshold below half the context. Every request
    // carried 16384 tokens, over the threshold, so Open WebUI summarized before every answer.
    val chat = List(400) { i -> if (i % 2 == 0) "Turn the kitchen lights on please, number $i." else "Done, the kitchen lights are on. ".repeat(8) }
    val notes = "The time on the phone: Sunday, November 22, 3:10 PM (afternoon).\n" +
        "You're talking out loud: this reply will be read aloud on the phone, so everything you say is spoken."
    for (threshold in listOf(4_000, 8_000, 12_000, 16_000, 24_000)) {
        val before = fit(chat, Compaction.historyTokens(32_768, null))
        val after = fit(chat, Compaction.historyTokens(32_768, Compaction.threshold(threshold, null)))
        val wasOver = openWebUiEstimate(notes, before) > threshold
        val isUnder = openWebUiEstimate(notes, after) <= threshold
        check(isUnder, "threshold $threshold: a voice request stays under it (was over: $wasOver)")
    }

    // A summary takes its share of the budget, and the request still stays under.
    val summary = "Soul asked about the lights and the heating most evenings; ".repeat(60)
    val threshold = 8_000
    val budget = Compaction.historyTokens(32_768, threshold) - summary.length / 4
    val kept = fit(chat, budget)
    check(openWebUiEstimate("$notes\n[CONVERSATION SUMMARY]\n$summary", kept) <= threshold, "with a summary, still under the threshold")

    // Compacting while quiet: only once the conversation fills half of what a request carries.
    check(!Compaction.worthCompacting(4 * 2_000, 6_000), "a short chat isn't compacted")
    check(Compaction.worthCompacting(4 * 3_000, 6_000), "half full: compacted while it's quiet")
    check(Compaction.worthCompacting(4 * 20_000, 6_000), "long: compacted while it's quiet")
    check(Compaction.QUIET_MINUTES in 1L..10L, "compacts a few minutes after the last question")

    if (failures > 0) {
        println("$failures check(s) failed")
        kotlin.system.exitProcess(1)
    }
    println("All compaction checks passed.")
}
