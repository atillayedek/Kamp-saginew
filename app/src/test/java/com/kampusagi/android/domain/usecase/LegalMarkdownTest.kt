package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.usecase.LegalMarkdown.Block
import com.kampusagi.android.domain.usecase.LegalMarkdown.Span
import com.kampusagi.android.domain.usecase.LegalMarkdown.Style
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalMarkdownTest {

    @Test
    fun `reads the structures the legal texts use`() {
        val blocks = LegalMarkdown.parse(
            """
            # Başlık

            > **TASLAK — AVUKAT ONAYI GEREKLİ.**

            İlk satır
            devamı.

            ## 1. Bölüm

            - Bir
            - İki **kalın**
              devam
            1. Birinci
            2. İkinci

            - [ ] Kutu

            | A | B |
            |---|---|
            | x | `y` |
            """.trimIndent(),
        )
        assertEquals(
            listOf("Heading", "Quote", "Paragraph", "Heading", "ListBlock", "ListBlock", "ListBlock", "Table"),
            blocks.map { it::class.simpleName },
        )
        assertEquals(Block.Paragraph(listOf(Span(Style.TEXT, "İlk satır devamı."))), blocks[2])
        val bullets = blocks[4] as Block.ListBlock
        assertEquals(listOf(Span(Style.TEXT, "İki "), Span(Style.BOLD, "kalın"), Span(Style.TEXT, " devam")), bullets.items[1].text)
        assertEquals(true, (blocks[5] as Block.ListBlock).ordered)
        assertEquals(true, (blocks[6] as Block.ListBlock).items.single().checkbox)
        assertEquals(listOf(Span(Style.CODE, "y")), (blocks[7] as Block.Table).rows.single()[1])
    }

    @Test
    fun `markup stays text`() {
        assertEquals(
            listOf(Span(Style.TEXT, "<script>alert(1)</script> "), Span(Style.BOLD, "x")),
            LegalMarkdown.inline("<script>alert(1)</script> **x**"),
        )
    }

    @Test
    fun `every published draft parses with a title and the draft banner`() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/legal") }
            .first { it.isDirectory }
        val files = dir.listFiles { f -> f.name.endsWith(".md") }!!.sorted()
        assertEquals(13, files.size)
        for (file in files) {
            val blocks = LegalMarkdown.parse(file.readText())
            assertTrue(file.name, blocks[0] is Block.Heading)
            assertTrue(file.name, (blocks[1] as Block.Quote).text.joinToString("") { it.text }.contains("TASLAK — AVUKAT ONAYI GEREKLİ"))
        }
    }
}
