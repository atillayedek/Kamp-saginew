package com.kampusagi.android.domain.usecase

/**
 * Reads the Markdown subset the legal texts use (headings, paragraphs, lists with [ ] boxes,
 * block quotes, tables, **bold**, `code`) into blocks the UI draws natively. Nothing in a
 * text is ever interpreted as markup beyond that.
 */
object LegalMarkdown {

    enum class Style { TEXT, BOLD, CODE }

    data class Span(val style: Style, val text: String)

    sealed interface Block {
        data class Heading(val level: Int, val text: List<Span>) : Block
        data class Paragraph(val text: List<Span>) : Block
        data class Quote(val text: List<Span>) : Block
        data class ListBlock(val ordered: Boolean, val items: List<Item>) : Block
        data class Table(val head: List<List<Span>>, val rows: List<List<List<Span>>>) : Block
    }

    data class Item(val checkbox: Boolean, val text: List<Span>)

    private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")
    private val HEADING = Regex("""^(#{1,3})\s+(.*)$""")
    private val LIST_ITEM = Regex("""^\s*(?:([-*])|(\d+)[.)])\s+(\[[ xX]\]\s+)?(.*)$""")
    private val TABLE_RULE = Regex("""^\s*\|?\s*:?-{3,}""")
    private val CONTINUATION = Regex("""^\s{2,}\S""")

    fun inline(text: String): List<Span> {
        val out = mutableListOf<Span>()
        var last = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > last) out += Span(Style.TEXT, text.substring(last, match.range.first))
            val bold = match.groups[1]
            out += if (bold != null) Span(Style.BOLD, bold.value) else Span(Style.CODE, match.groups[2]!!.value)
            last = match.range.last + 1
        }
        if (last < text.length) out += Span(Style.TEXT, text.substring(last))
        return out
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    fun parse(source: String): List<Block> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                blocks += Block.Heading(heading.groupValues[1].length, inline(heading.groupValues[2].trim()))
                i++
                continue
            }
            if (line.trimStart().startsWith(">")) {
                val parts = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    parts += lines[i++].trimStart().removePrefix(">").removePrefix(" ")
                }
                blocks += Block.Quote(inline(parts.joinToString(" ").trim()))
                continue
            }
            if (line.trimStart().startsWith("|") && i + 1 < lines.size && TABLE_RULE.containsMatchIn(lines[i + 1])) {
                val head = cells(line).map(::inline)
                i += 2
                val rows = mutableListOf<List<List<Span>>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) rows += cells(lines[i++]).map(::inline)
                blocks += Block.Table(head, rows)
                continue
            }
            val first = LIST_ITEM.matchEntire(line)
            if (first != null) {
                val ordered = first.groups[2] != null
                val items = mutableListOf<Item>()
                while (i < lines.size) {
                    val item = LIST_ITEM.matchEntire(lines[i]) ?: break
                    if ((item.groups[2] != null) != ordered) break
                    var text = item.groupValues[4]
                    i++
                    while (i < lines.size && CONTINUATION.containsMatchIn(lines[i]) && LIST_ITEM.matchEntire(lines[i]) == null) {
                        text += " " + lines[i++].trim()
                    }
                    items += Item(item.groups[3] != null, inline(text.trim()))
                }
                blocks += Block.ListBlock(ordered, items)
                continue
            }
            val parts = mutableListOf<String>()
            while (
                i < lines.size && lines[i].isNotBlank() && HEADING.matchEntire(lines[i]) == null &&
                !lines[i].trimStart().startsWith(">") && !lines[i].trimStart().startsWith("|") &&
                LIST_ITEM.matchEntire(lines[i]) == null
            ) {
                parts += lines[i++].trim()
            }
            blocks += Block.Paragraph(inline(parts.joinToString(" ")))
        }
        return blocks
    }
}
