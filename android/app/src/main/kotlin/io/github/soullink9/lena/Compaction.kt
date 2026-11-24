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
 * Open WebUI checks again between the rounds of an answer that uses tools, and there it counts the whole
 * prompt the model reported: her character, her context, every tool's definition and the tool results, not
 * just the conversation. When that part alone is near the threshold, every round is over it and Open WebUI
 * summarizes before each one, which can take most of the time an answer takes.
 *
 * So the app does three things:
 * - **Never over the threshold.** A request carries only as much conversation as fits under the threshold
 *   beside what the rest of her prompt took last time ([overhead]), with room for this answer's tool
 *   results ([RESERVE]), and never more than three quarters of the threshold ([historyTokens]). When even
 *   that leaves nothing, the threshold is too low for her prompt, and the app's log says what to raise it to
 *   ([suggestedThreshold], never above what her context holds beside her reply), or, when no threshold
 *   fits, that her prompt is too big for her context: nothing the app sends can fix either.
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
     * Tokens kept free under the threshold for what an answer adds while she works: tool results (a whole
     * floor's lights from GetLiveContext runs to a couple of thousand) and her thinking between rounds.
     */
    const val RESERVE = 4096

    /** The least conversation a request carries, however full the rest of the prompt is: the last few turns. */
    const val MIN_HISTORY = 1024

    /**
     * What the rest of her prompt took: [reportedPrompt] is the prompt size the model reported for her last
     * answer (Ollama's prompt_eval_count, kept with the reply), [sentHistory] the conversation sent with it.
     * The difference is her character, her context, the tools' definitions and that answer's tool results.
     */
    fun overhead(reportedPrompt: Int, sentHistory: Int): Int = (reportedPrompt - sentHistory).coerceAtLeast(0)

    /**
     * How many tokens of conversation a request carries: half the model's [contextTokens] (the other half
     * is for the system prompt, tools, tool results and the reply) and, with compaction on, no more than
     * three quarters of its [threshold] nor more than fits beside the rest of her prompt ([overhead]) and
     * [RESERVE], so Open WebUI never has to compact while she answers. Never less than [MIN_HISTORY].
     */
    fun historyTokens(contextTokens: Int, threshold: Int?, overhead: Int = 0): Int {
        val half = contextTokens.coerceAtLeast(2048) / 2
        if (threshold == null) return half
        val room = threshold - overhead - RESERVE
        return minOf(half, threshold * 3 / 4, room).coerceAtLeast(MIN_HISTORY)
    }

    /**
     * Whether [threshold] is too low for her prompt: the rest of it ([overhead]) leaves no room for
     * [MIN_HISTORY] of conversation and [RESERVE], so Open WebUI compacts during every answer whatever the
     * app sends.
     */
    fun tooLow(threshold: Int, overhead: Int): Boolean = threshold - overhead - RESERVE < MIN_HISTORY

    /** Room kept in the model's context for her reply: the Modelfile's num_predict. */
    const val REPLY = 4096

    /**
     * The highest threshold worth having with a [contextTokens] context (num_ctx): above it, her prompt and
     * her reply no longer fit, and Ollama cuts the prompt instead of Open WebUI summarizing it.
     */
    fun maxThreshold(contextTokens: Int): Int = (contextTokens - REPLY) / 1000 * 1000

    /**
     * A threshold that fits her prompt: the rest of it ([overhead]), [RESERVE] and room for a real
     * conversation (8192 tokens), rounded up to a thousand, but never above [maxThreshold]. Null when even
     * that leaves no room for [MIN_HISTORY]: then her prompt is too big for her context and no threshold
     * helps, only a smaller prompt (fewer tools offered) or a larger context.
     */
    fun suggestedThreshold(overhead: Int, contextTokens: Int): Int? {
        val most = maxThreshold(contextTokens)
        if (tooLow(most, overhead)) return null
        return minOf(roomy(overhead), most)
    }

    /** [overhead], [RESERVE] and 8192 tokens of conversation, rounded up to a thousand. */
    private fun roomy(overhead: Int): Int = (overhead + RESERVE + 8192 + 999) / 1000 * 1000

    /**
     * What the app's log says when [threshold] is too low for her prompt ([tooLow]): the threshold to set
     * ([suggestedThreshold]), and when that's all her [contextTokens] context holds, or none fits, that her
     * prompt itself has to get smaller. Raising the threshold past her context would only swap Open WebUI's
     * summaries for Ollama cutting her prompt.
     */
    fun tooLowMessage(overhead: Int, threshold: Int, contextTokens: Int): String {
        val start = "Her prompt without the conversation (her character, her context, the tools and their results) " +
            "was about $overhead tokens, but Context Compaction starts at $threshold, so Open WebUI summarizes " +
            "during every answer."
        val smaller = "make her prompt smaller: in Open WebUI, Workspace → Models → lena → Builtin Tools, turn off " +
            "the built-in tools she has her own of or doesn't use (each one's definition is sent with every " +
            "request; see Context Compaction in docs/open-webui/Setup.md)"
        val suggested = suggestedThreshold(overhead, contextTokens)
            ?: return "$start No threshold can fix that: with her $contextTokens-token context (num_ctx) and " +
                "$REPLY tokens kept for her reply, that leaves no room for the conversation. To fix it, $smaller, " +
                "or give her a larger context."
        val raise = "Raise the threshold (Admin Settings → Interface, or the model's Context Compaction " +
            "Threshold) to $suggested"
        return if (suggested >= roomy(overhead)) "$start $raise." else
            "$start $raise, the most her $contextTokens-token context (num_ctx) holds beside her reply. Even " +
                "then little of the conversation fits, so also $smaller."
    }

    /**
     * Whether a chat whose conversation since its last compaction is [chars] long is worth compacting:
     * once it fills half of what a request carries, so the oldest part is summarized long before trimming
     * would have to drop it.
     */
    fun worthCompacting(chars: Int, budgetTokens: Int): Boolean = chars / 4 >= budgetTokens / 2
}
