package de.flashcards.open.data.io

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException

object UriFiles {
    fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "import"
    }

    fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw FileNotFoundException(uri.toString())

    fun stripCsvExtension(name: String) = name.replace(Regex("""\.(csv|tsv|txt)$""", RegexOption.IGNORE_CASE), "")

    fun isCsvName(name: String) = Regex(""".*\.(csv|tsv|txt)$""", RegexOption.IGNORE_CASE).matches(name)
}
