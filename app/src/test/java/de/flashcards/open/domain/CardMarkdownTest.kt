package de.flashcards.open.domain

import de.flashcards.open.domain.markdown.CardMarkdown
import de.flashcards.open.domain.markdown.CardSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMarkdownTest {
    private val media = mapOf("image.png" to "/m/1.png", "my image.png" to "/m/2.png", "osi.png" to "/m/3.png", "größe.jpg" to "/m/4.jpg")
    private fun seg(src: String) = CardMarkdown.segments(src, { media[it] })

    @Test fun normalizeName() {
        assertEquals("image.png", CardMarkdown.normalizeName("Image.PNG"))
        assertEquals("osi.png", CardMarkdown.normalizeName("images/sub/OSI.png"))
        assertEquals("osi.png", CardMarkdown.normalizeName("C:\\x\\osi.png"))
        assertEquals("my image.png", CardMarkdown.normalizeName("my%20image.png"))
        assertEquals("image.png", CardMarkdown.normalizeName("image.png?v=2"))
        assertEquals("größe.jpg", CardMarkdown.normalizeName("Gro\u0308ße.jpg")) // NFD → NFC
        assertEquals("a+b.png", CardMarkdown.normalizeName("a+b.png"))
    }

    @Test fun allImageSyntaxesResolve() {
        val syntaxes = listOf(
            "![](image.png)", "![](<my image.png>)", "![](my image.png)", "[[image.png]]", "![[image.png]]",
            "![[image.png|200]]", "<img src=\"image.png\">", "<img alt='x' src='my image.png' />", "<IMG SRC=osi.png>",
            "![alt](images/OSI.png \"title\")",
        )
        for (s in syntaxes) {
            val img = seg(s).single() as CardSegment.Image
            assertTrue("$s → ${img.name}", img.path != null)
        }
    }

    @Test fun missingImageHasNoPath() {
        val img = seg("![](missing.png)").single() as CardSegment.Image
        assertEquals("missing.png", img.name)
        assertEquals(null, img.path)
    }

    @Test fun textAndImagesInOrder() {
        val s = seg("Network layer\n\n![](osi.png)\n\n**End**")
        assertEquals(3, s.size)
        assertEquals(CardSegment.Text("Network layer"), s[0])
        assertEquals(CardSegment.Image("osi.png", "/m/3.png"), s[1])
        assertEquals(CardSegment.Text("**End**"), s[2])
    }

    @Test fun inlineImagesUseFileUrlOrWarning() {
        val s = seg("See ![x](image.png) and ![](gone.png) here").single() as CardSegment.Text
        assertTrue(s.markdown.contains("![x](file:///m/1.png)"))
        assertTrue(s.markdown.contains("**⚠ Missing image: gone.png**"))
    }

    @Test fun codeBlocksAreUntouched() {
        val src = "```\n![](image.png)\n[[x.png]]\n```"
        assertEquals(listOf(CardSegment.Text(src)), seg(src))
    }

    @Test fun remoteImagesAreTreatedAsMissing() {
        assertEquals(null, (seg("![](https://example.com/a.png)").single() as CardSegment.Image).path)
    }

    @Test fun plainPreview() {
        assertEquals("What is Größe? 🖼", CardMarkdown.plainPreview("**What is** _Größe_?\n![](x.png)"))
    }
}
