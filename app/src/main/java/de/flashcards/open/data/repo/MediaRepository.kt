package de.flashcards.open.data.repo

import de.flashcards.open.data.db.AppDatabase
import de.flashcards.open.data.db.Media
import de.flashcards.open.domain.markdown.CardMarkdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Images live under `filesDir/media/`, name → file mapping via the `Media` table. */
class MediaRepository(
    private val filesDir: File,
    private val db: AppDatabase,
    scope: CoroutineScope,
) {
    val mediaDir: File get() = File(filesDir, DIR).apply { mkdirs() }

    /** normalised name → absolute path */
    val index: StateFlow<Map<String, String>> = db.mediaDao().observeAll()
        .map { list -> list.associate { it.fileName to File(filesDir, it.relativePath).absolutePath } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun isImageName(name: String): Boolean = IMAGE_EXT.matches(name.substringAfterLast('.', "").lowercase())

    /** Stores an image; an existing one with the same name is overwritten. */
    suspend fun save(originalName: String, input: InputStream): Media {
        val key = CardMarkdown.normalizeName(originalName)
        val ext = key.substringAfterLast('.', "bin")
        val stored = sha1(key).take(20) + "." + ext
        val target = File(mediaDir, stored)
        val tmp = File(mediaDir, "$stored.tmp")
        tmp.outputStream().use { input.copyTo(it) }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true); tmp.delete()
        }
        val media = Media(key, "$DIR/$stored", target.length())
        db.mediaDao().upsert(media)
        return media
    }

    suspend fun totalSize(): Pair<Int, Long> {
        val all = db.mediaDao().all()
        return all.size to all.sumOf { it.sizeBytes }
    }

    suspend fun deleteAll() {
        db.mediaDao().deleteAll()
        mediaDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val DIR = "media"
        private val IMAGE_EXT = Regex("png|jpe?g|gif|webp|svg|bmp|avif")

        private fun sha1(s: String) = MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
