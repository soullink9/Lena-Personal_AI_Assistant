package io.github.soullink9.lena

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Just enough Markdown for short answers in the Ask sheet and notifications. */
object Markdown {

    /** Open WebUI stores tool calls and reasoning as <details> blocks inside the answer. */
    private val details = Regex("<details[\\s\\S]*?(</details>|$)")

    /**
     * Her answer without those blocks: what she actually said, for showing, speaking and sending back as
     * history.
     */
    fun clean(text: String): String = details.replace(text, "").trim()

    /** Plain text for notifications. */
    fun plain(text: String): String = clean(text)
        .replace(Regex("```[a-zA-Z0-9]*\\n?"), "")
        .replace(Regex("[*_`#>]+"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private val inline = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*\\s][^*]*?)\\*|_([^_\\s][^_]*?)_")

    /**
     * Draws an answer with headings, bold, italics, inline code and code blocks, enough for short answers
     * without a full Markdown library.
     */
    fun render(text: String, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
        var inCode = false
        val lines = clean(text).lines()
        lines.forEachIndexed { index, raw ->
            if (raw.trimStart().startsWith("```")) {
                inCode = !inCode
                return@forEachIndexed
            }
            if (inCode) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(raw) }
            } else {
                val heading = Regex("^(#{1,6})\\s+(.*)$").find(raw)
                val bullet = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$").find(raw)
                when {
                    heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) {
                        appendInline(heading.groupValues[2], codeBackground)
                    }
                    bullet != null -> {
                        append(bullet.groupValues[1])
                        val marker = bullet.groupValues[2]
                        append(if (marker[0].isDigit()) "$marker " else "•  ")
                        appendInline(bullet.groupValues[3], codeBackground)
                    }
                    else -> appendInline(raw, codeBackground)
                }
            }
            if (index < lines.lastIndex) append('\n')
        }
    }

    /** Adds one line with its bold, italic and code spans styled. */
    private fun AnnotatedString.Builder.appendInline(line: String, codeBackground: androidx.compose.ui.graphics.Color) {
        var last = 0
        for (m in inline.findAll(line)) {
            append(line.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(g[3]) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[4]) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[5]) }
            }
            last = m.range.last + 1
        }
        append(line.substring(last))
    }
}
