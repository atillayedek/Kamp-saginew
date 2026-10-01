package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.usecase.BodyLinks.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyLinksTest {

    private fun links(text: String) = BodyLinks.parse(text).filterNot { it is Segment.Plain }

    @Test
    fun `mentions and tags are found as the database finds them`() {
        val text = "#Kampüs etkinliği için @mt_mehmet ve @MT_ODTU gelsin. e-posta a@mt_mehmet.com #1 #ab #Bahar_Şenliği"
        assertEquals(
            listOf(
                Segment.Tag("#Kampüs", "kampus"),
                Segment.Mention("@mt_mehmet", "mt_mehmet"),
                Segment.Mention("@MT_ODTU", "mt_odtu"),
                Segment.Tag("#ab", "ab"),
                Segment.Tag("#Bahar_Şenliği", "bahar_senligi"),
            ),
            links(text),
        )
    }

    @Test
    fun `segments rebuild the original text`() {
        val text = "Selam @ayse. Bugün #sınav var, C# değil; @ab kısa. Son: @mehmet"
        assertEquals(text, BodyLinks.parse(text).joinToString("") {
            when (it) {
                is Segment.Plain -> it.text
                is Segment.Mention -> it.text
                is Segment.Tag -> it.text
            }
        })
    }

    @Test
    fun `a sentence-ending dot is not underlined but is kept for the lookup`() {
        assertEquals(listOf(Segment.Mention("@ayse", "ayse.")), links("Selam @ayse."))
    }

    @Test
    fun `glued or too short markers are not links`() {
        assertEquals(emptyList<Segment>(), links("C# ve a@b.com ve @ab ve #1 ve ##"))
    }

    @Test
    fun `tag keys fold Turkish letters`() {
        assertEquals("sinav_haftasi", BodyLinks.tagKey("#SINAV_Haftası"))
        assertEquals("cagri", BodyLinks.tagKey("Çağrı"))
        assertNull(BodyLinks.tagKey("#12"))
        assertNull(BodyLinks.tagKey("#a"))
        assertNull(BodyLinks.tagKey("#bu da"))
    }

    @Test
    fun `the mention being typed is the last word after an at sign`() {
        assertEquals("", BodyLinks.mentionBeingTyped("Merhaba @"))
        assertEquals("ays", BodyLinks.mentionBeingTyped("Merhaba @Ays"))
        assertNull(BodyLinks.mentionBeingTyped("Merhaba @ayse "))
        assertNull(BodyLinks.mentionBeingTyped("mail@ays"))
        assertEquals("Merhaba @ayse_y ", BodyLinks.completeMention("Merhaba @ay", "ayse_y"))
        assertEquals("text", BodyLinks.completeMention("text", "ayse_y"))
    }
}
