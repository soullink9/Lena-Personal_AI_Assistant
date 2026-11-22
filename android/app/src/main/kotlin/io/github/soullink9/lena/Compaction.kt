package io.github.soullink9.lena

// Plain Kotlin, so tests/app/compaction_check can check the rules away from Android.

/**
 * Keeping Open WebUI's context compaction out of the way of her answers.
 *
 * With Context Compaction on (Open WebUI's Admin Settings → Interface), Open WebUI checks every request
 * against a token threshold and, when it's over, has the model summarize the older half of the
 * conversation before it answers. The answer waits for that summary, which on a local model takes far
 * longer than the answer itself. And the app sends the thread itself (trimmed, with notes on time
 * passing), so Open WebUI never saw its own earlier summary and wrote a new one on every request.
 *
 * So the app does three things:
 * - **Never over the threshold.** A request carries at most three quarters of the threshold of history
 *   ([historyTokens]), the rest left for her notes, so Open WebUI never needs to compact while she answers.
 * - **Uses the summary.** A thread starts at the chat's latest compaction and the summary goes in her
 *   notes, the way Open WebUI's own page sends it ([Api.branch]), so older talk isn't simply cut off.
 * - **Compacts when it's quiet.** A few minutes after the last question ([QUIET_MINUTES]), [CompactWorker]
 *   (in Background.kt) asks Open WebUI to compact the chat if it's getting long, so the summary is ready
 *   before it's needed.
 *
 * With compaction off, Open WebUI reports no threshold and all of this steps aside: the thread is trimmed
 * to half the model's context as before.
 */
object Compaction {
    /** How a summary is labelled for her, the same words Open WebUI's own page uses. */
    const val SUMMARY_HEADER = "[CONVERSATION SUMMARY]"

    /**
     * How long a chat must be quiet before it's compacted: long enough that a follow-up question doesn't
     * queue behind the summary on the model, short enough that it's done before the next conversation.
     */
    const val QUIET_MINUTES = 3L

    /**
     * The token count at which Open WebUI compacts a chat, or null when compaction is off. [server] is the
     * threshold Open WebUI reports with the chat ("context_usage", none when compaction is off: see
     * [Api.compactThreshold]), [model] the model's own Context Compaction Threshold, if it has one. The
     * lower one counts, so a request stays under whichever Open WebUI goes by.
     */
    fun threshold(server: Int?, model: Int?): Int? {
        if (server == null || server <= 0) return null
        return if (model != null && model > 0) minOf(model, server) else server
    }

    /**
     * How many tokens of conversation a request carries: half the model's [contextTokens] (the other half
     * is for the system prompt, tools, tool results and the reply) and, with compaction on, no more than
     * three quarters of its [threshold], so Open WebUI never has to compact while she answers.
     */
    fun historyTokens(contextTokens: Int, threshold: Int?): Int {
        val half = contextTokens.coerceAtLeast(2048) / 2
        return if (threshold == null) half else minOf(half, threshold * 3 / 4)
    }

    /**
     * Whether a chat whose conversation since its last compaction is [chars] long is worth compacting:
     * once it fills half of what a request carries, so the oldest part is summarized long before trimming
     * would have to drop it.
     */
    fun worthCompacting(chars: Int, budgetTokens: Int): Boolean = chars / 4 >= budgetTokens / 2
}
