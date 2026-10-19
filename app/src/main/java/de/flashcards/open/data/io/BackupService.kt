package de.flashcards.open.data.io

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import de.flashcards.open.data.db.AppDatabase
import de.flashcards.open.data.db.Card
import de.flashcards.open.data.db.Deck
import de.flashcards.open.data.db.Media
import de.flashcards.open.data.db.ReviewLog
import de.flashcards.open.data.db.StudyPreset
import de.flashcards.open.data.db.StudyPresetDeck
import de.flashcards.open.data.db.Topic
import de.flashcards.open.data.repo.MediaRepository
import de.flashcards.open.data.repo.Settings
import de.flashcards.open.data.repo.SettingsRepository
import de.flashcards.open.domain.csv.CsvWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val app: String = APP_ID,
    val version: Int = 1,
    val exportedAt: Long,
    val settings: Settings,
    val topics: List<Topic>,
    val decks: List<Deck>,
    val cards: List<Card>,
    val reviewLogs: List<ReviewLog>,
    val media: List<Media>,
    val presets: List<StudyPreset>,
    val presetDecks: List<StudyPresetDeck>,
) {
    companion object {
        const val APP_ID = "flashcards"

        /** Marker of the backups written under the old app name; still read. */
        const val LEGACY_APP_ID = "karteikarten"
    }
}

class InvalidBackupException : IOException("Not a Flashcards Open backup")

/** ZIP backup (`backup.json` + `media/`) and CSV export. */
class BackupService(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val media: MediaRepository,
    private val filesDir: File,
    private val cacheDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(resolver: ContentResolver, target: Uri) = withContext(Dispatchers.IO) {
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            settings = settings.current(),
            topics = db.topicDao().all(),
            decks = db.deckDao().all(),
            cards = db.cardDao().all(),
            reviewLogs = db.reviewLogDao().all(),
            media = db.mediaDao().all(),
            presets = db.presetDao().all(),
            presetDecks = db.presetDao().allLinks(),
        )
        val out = resolver.openOutputStream(target, "wt") ?: throw IOException("Target is not writable")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (m in data.media) {
                val f = File(filesDir, m.relativePath)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry(m.relativePath))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Replaces all data with the backup. */
    suspend fun import(resolver: ContentResolver, source: Uri) = withContext(Dispatchers.IO) {
        val staging = File(cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var data: BackupData? = null
        val input = resolver.openInputStream(source) ?: throw IOException("File is not readable")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.isDirectory -> Unit
                    e.name == "backup.json" -> data = json.decodeFromString(BackupData.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                    e.name.startsWith("${MediaRepository.DIR}/") && !e.name.contains("..") -> {
                        val f = File(staging, e.name.substringAfter('/'))
                        f.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val d = data?.takeIf { it.app == BackupData.APP_ID || it.app == BackupData.LEGACY_APP_ID }
            ?: throw InvalidBackupException()
        db.withTransaction {
            db.presetDao().deleteAll()
            db.reviewLogDao().deleteAll()
            db.cardDao().deleteAll()
            db.deckDao().deleteAll()
            db.topicDao().deleteAll()
            db.mediaDao().deleteAll()
            db.topicDao().insertAll(d.topics)
            db.deckDao().insertAll(d.decks)
            d.cards.chunked(2000).forEach { db.cardDao().insertAll(it) }
            d.reviewLogs.chunked(2000).forEach { db.reviewLogDao().insertAll(it) }
            db.mediaDao().insertAll(d.media)
            db.presetDao().insertAll(d.presets)
            db.presetDao().insertLinks(d.presetDecks)
        }
        val dir = media.mediaDir
        dir.listFiles()?.forEach { it.delete() }
        staging.listFiles()?.forEach { f -> f.copyTo(File(dir, f.name), overwrite = true) }
        staging.deleteRecursively()
        settings.replaceAll(d.settings)
    }

    suspend fun exportDeckCsv(resolver: ContentResolver, deckId: Long, target: Uri) = withContext(Dispatchers.IO) {
        val rows = db.cardDao().byDeck(deckId).map { listOf(it.frontMd, it.backMd, it.tags, it.type.csvName, it.options) }
        write(resolver, target, CsvWriter.write(listOf("Front", "Back", "Tags", "Type", "Options"), rows))
    }

    suspend fun exportTopicCsv(resolver: ContentResolver, topicId: Long, target: Uri) = withContext(Dispatchers.IO) {
        val rows = db.deckDao().byTopic(topicId).flatMap { deck ->
            db.cardDao().byDeck(deck.id).map {
                listOf(it.frontMd, it.backMd, it.tags, it.type.csvName, it.options, deck.name)
            }
        }
        write(resolver, target, CsvWriter.write(listOf("Front", "Back", "Tags", "Type", "Options", "Deck"), rows))
    }

    private fun write(resolver: ContentResolver, target: Uri, bytes: ByteArray) {
        (resolver.openOutputStream(target, "wt") ?: throw IOException("Target is not writable")).use { it.write(bytes) }
    }
}
