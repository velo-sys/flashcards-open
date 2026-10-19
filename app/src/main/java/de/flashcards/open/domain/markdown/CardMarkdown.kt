package de.flashcards.open.domain.markdown

import de.flashcards.open.domain.text.TextDecoding
import java.net.URLDecoder

/** A piece of a card side: Markdown text or a standalone image. */
sealed interface CardSegment {
    data class Text(val markdown: String) : CardSegment
    /** [path] is null when the image was not found in `Media`. */
    data class Image(val name: String, val path: String?) : CardSegment
}

object CardMarkdown {
    private const val IMG_EXT = "png|jpe?g|gif|webp|svg|bmp|avif"
    private val OBSIDIAN = Regex("""!?\[\[([^\]|]+\.(?:$IMG_EXT))(?:\|[^\]]*)?\]\]""", RegexOption.IGNORE_CASE)
    private val HTML_IMG = Regex(
        """<img\b[^>]*?\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val SPACED_DEST = Regex("""(!\[[^\]]*]\()([^)<>]*\s[^)<>]*?)(\))""")
    private val TITLED_DEST = Regex("""\s*\S+\s+"[^"]*"\s*""")
    /** A Markdown image after preprocessing: ![alt](<target>) or ![alt](target "title") */
    val IMAGE = Regex("""!\[([^\]]*)]\(\s*(?:<([^>]*)>|([^)\s]+))(?:\s+"[^"]*")?\s*\)""")
    private val FENCE = Regex("""^\s*(```|~~~)""")

    /** Normalises every supported image notation to `![](<name>)` (outside of code blocks). */
    fun preprocess(src: String): String {
        var inFence = false
        return src.replace("\\n", "\n").split('\n').joinToString("\n") { line ->
            val fence = FENCE.containsMatchIn(line)
            if (fence) inFence = !inFence
            if (inFence || fence) line else preprocessLine(line)
        }
    }

    private fun preprocessLine(line: String): String {
        var s = OBSIDIAN.replace(line) { m -> "![](<${m.groupValues[1].trim()}>)" }
        s = HTML_IMG.replace(s) { m ->
            val v = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            "![](<${v.trim()}>)"
        }
        s = SPACED_DEST.replace(s) { m ->
            val dest = m.groupValues[2]
            if (TITLED_DEST.matches(dest)) m.value // ![](image.png "title") stays as it is
            else m.groupValues[1] + "<" + dest.trim() + ">" + m.groupValues[3]
        }
        return s
    }

    /** Image name → key in `Media`: without path, URL-decoded, NFC, lower case. */
    fun normalizeName(name: String): String {
        var n = name.split('/', '\\').last().substringBefore('?')
        n = try {
            URLDecoder.decode(n.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            n
        }
        return TextDecoding.nfc(n.trim()).lowercase()
    }

    /**
     * Splits a card side into text and image blocks. Lines that consist of images only become
     * their own image blocks (tappable). Images in the middle of text are rendered inline through
     * a `file://` URL, missing ones as a warning. Code blocks are left untouched.
     *
     * @param resolve maps a normalised name to its absolute file path, or null
     * @param missingLabel text for missing inline images (from strings.xml)
     */
    fun segments(
        src: String,
        resolve: (String) -> String?,
        missingLabel: (String) -> String = { "Missing image: $it" },
    ): List<CardSegment> {
        val text = preprocess(src)
        val out = ArrayList<CardSegment>()
        val buf = StringBuilder()
        var inFence = false

        fun flush() {
            val t = buf.toString().trim('\n')
            if (t.isNotBlank()) out += CardSegment.Text(t)
            buf.setLength(0)
        }

        for (line in text.split('\n')) {
            if (FENCE.containsMatchIn(line)) inFence = !inFence
            if (inFence || FENCE.containsMatchIn(line)) {
                buf.append(line).append('\n'); continue
            }
            val matches = IMAGE.findAll(line).toList()
            if (matches.isNotEmpty() && IMAGE.replace(line, "").isBlank()) {
                flush()
                for (m in matches) {
                    val name = dest(m)
                    out += CardSegment.Image(name, resolveImage(name, resolve))
                }
                continue
            }
            val rewritten = IMAGE.replace(line) { m ->
                val name = dest(m)
                val path = resolveImage(name, resolve)
                if (path != null) "![${m.groupValues[1]}](file://$path)" else "**⚠ ${missingLabel(name)}**"
            }
            buf.append(rewritten).append('\n')
        }
        flush()
        return out
    }

    private fun dest(m: MatchResult): String = m.groupValues[2].ifEmpty { m.groupValues[3] }

    private fun resolveImage(name: String, resolve: (String) -> String?): String? {
        if (name.isBlank()) return null
        if (name.startsWith("http://", true) || name.startsWith("https://", true)) return null // offline
        return resolve(normalizeName(name))
    }

    /** Short plain text for lists (without Markdown syntax). */
    fun plainPreview(src: String, max: Int = 160): String {
        val s = preprocess(src)
            .replace(IMAGE, "🖼")
            .replace(Regex("""[*_`#>|]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (s.length > max) s.take(max - 1) + "…" else s
    }

    /** Every image name referenced by a card side (normalised). */
    fun referencedImages(src: String): List<String> =
        IMAGE.findAll(preprocess(src)).map { normalizeName(dest(it)) }.toList()
}
