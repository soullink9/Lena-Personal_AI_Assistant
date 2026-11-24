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
    check(Compaction.historyTokens(32_768, 16_000) == 16_000 - Compaction.RESERVE, "threshold 16000: room left for tool results")
    check(Compaction.historyTokens(32_768, 8_000) == 8_000 - Compaction.RESERVE, "threshold 8000: room left for tool results")

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

    // Between the rounds of an answer Open WebUI counts the whole prompt the model reported: her character,
    // context, tool definitions and results. With 11000 tokens of that and a 16000 threshold, a request
    // carrying 12000 tokens of conversation went over in every round, and Open WebUI summarized each time.
    check(Compaction.overhead(19_500, 8_500) == 11_000, "overhead: reported prompt less the conversation sent")
    check(Compaction.overhead(3_000, 8_500) == 0, "overhead is never negative")
    val withOverhead = Compaction.historyTokens(32_768, 16_000, 11_000)
    check(withOverhead == Compaction.MIN_HISTORY, "threshold 16000, overhead 11000: only the last few turns")
    check(Compaction.historyTokens(32_768, 30_000, 11_000) == 30_000 - 11_000 - Compaction.RESERVE,
        "threshold 30000, overhead 11000: the conversation fits beside the rest")
    check(11_000 + Compaction.historyTokens(32_768, 30_000, 11_000) + Compaction.RESERVE <= 30_000,
        "threshold 30000: a whole round, tool results included, stays under it")
    check(Compaction.historyTokens(32_768, 80_000, 11_000) == 16_384, "a high threshold: half the context as before")
    check(Compaction.historyTokens(32_768, null, 50_000) == 16_384, "compaction off: overhead doesn't matter")
    check(Compaction.tooLow(16_000, 11_000), "threshold 16000 is too low for an 11000-token prompt")
    check(!Compaction.tooLow(30_000, 11_000), "threshold 30000 is enough for it")
    val suggested = Compaction.suggestedThreshold(11_000, 32_768)
    check(suggested == 24_000, "suggested threshold for 11000: 24000 (was $suggested)")
    check(!Compaction.tooLow(suggested!!, 11_000), "the suggested threshold is enough")
    check(suggested % 1000 == 0, "suggested threshold is a round number")
    // Never above what her context holds beside her reply (num_ctx 32768, num_predict 4096).
    check(Compaction.maxThreshold(32_768) == 28_000, "num_ctx 32768: the threshold tops out at 28000")
    val capped = Compaction.suggestedThreshold(21_591, 32_768)
    check(capped == 28_000, "a 21591-token prompt: 28000, not 34000 past num_ctx (was $capped)")
    check(Compaction.suggestedThreshold(23_498, 32_768) == null, "a 23498-token prompt: no threshold fits in 32768")
    check(Compaction.suggestedThreshold(23_498, 65_536) == 36_000, "with a 65536 context: 36000 fits")
    for (overhead in listOf(0, 5_000, 11_000, 21_591, 23_498, 40_000)) {
        for (context in listOf(8_192, 16_384, 32_768, 65_536)) {
            val s = Compaction.suggestedThreshold(overhead, context) ?: continue
            check(s <= context - Compaction.REPLY, "suggestion $s for $overhead fits in num_ctx $context")
            check(!Compaction.tooLow(s, overhead), "suggestion $s for $overhead is enough")
        }
    }
    val fits = Compaction.tooLowMessage(11_000, 16_000, 32_768)
    check("to 24000." in fits && "smaller" !in fits, "message: a plain raise when it fits ($fits)")
    val tight = Compaction.tooLowMessage(21_591, 24_000, 32_768)
    check("to 28000" in tight && "Builtin Tools" in tight && "34000" !in tight, "message: capped, and make her prompt smaller ($tight)")
    val none = Compaction.tooLowMessage(23_498, 24_000, 32_768)
    check("No threshold can fix that" in none && "36000" !in none, "message: no threshold fits ($none)")

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
