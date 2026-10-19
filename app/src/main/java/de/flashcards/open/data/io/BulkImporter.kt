package de.flashcards.open.data.io

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.flashcards.open.data.repo.FlashcardRepository
import de.flashcards.open.data.repo.MediaRepository
import de.flashcards.open.domain.csv.CsvCardReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/** One file of an import: path relative to the root (file name included). */
class BulkEntry(val path: List<String>, val open: () -> InputStream) {
    val name get() = path.last()
}

data class BulkProgress(val done: Int, val total: Int, val current: String)

data class BulkResult(
    val topicsCreated: Int = 0,
    val decksCreated: Int = 0,
    val decksUpdated: Int = 0,
    val cardsAdded: Int = 0,
    val cardsChanged: Int = 0,
    val cardsRemoved: Int = 0,
    val images: Int = 0,
    val errors: List<String> = emptyList(),
)

/**
 * Bulk import: subfolder = topic, CSV file = deck, images (from any subfolder) → Media.
 * Existing topics/decks (same name) are updated through a re-import.
 */
class BulkImporter(
    private val repo: FlashcardRepository,
    private val media: MediaRepository,
) {
    suspend fun run(rootName: String, rawEntries: List<BulkEntry>, onProgress: (BulkProgress) -> Unit): BulkResult =
        withContext(Dispatchers.IO) {
            var entries = rawEntries.filter { e -> e.path.none { it.startsWith(".") || it == "__MACOSX" } }
                .filter { UriFiles.isCsvName(it.name) || media.isImageName(it.name) }
            var root = rootName
            // strip a shared root folder (e.g. “Flashcards/” inside a ZIP)
            val first = entries.firstOrNull()?.path?.firstOrNull()
            if (first != null && entries.all { it.path.size > 1 && it.path[0] == first }) {
                root = first
                entries = entries.map { e -> BulkEntry(e.path.drop(1), e.open) }
            }
            // Images first, then decks (sorted for a stable order)
            val images = entries.filter { media.isImageName(it.name) }
            val csvs = entries.filter { UriFiles.isCsvName(it.name) }
                .sortedWith(compareBy({ topicName(it, root).lowercase() }, { it.name.lowercase() }))
            val total = images.size + csvs.size
            var done = 0
            var r = BulkResult()
            val errors = ArrayList<String>()

            for (img in images) {
                coroutineContext.ensureActive()
                onProgress(BulkProgress(done, total, img.path.joinToString("/")))
                runCatching { img.open().use { media.save(img.name, it) } }
                    .onSuccess { r = r.copy(images = r.images + 1) }
                    .onFailure { errors += "${img.path.joinToString("/")}: ${it.message}" }
                done++
            }

            var topicCount = repo.allTopics().size
            for (csv in csvs) {
                coroutineContext.ensureActive()
                onProgress(BulkProgress(done, total, csv.path.joinToString("/")))
                runCatching {
                    val tName = topicName(csv, root)
                    val existingTopic = repo.allTopics().firstOrNull { it.name.equals(tName, ignoreCase = true) }
                    val topic = existingTopic ?: repo.getOrCreateTopic(tName, TopicColors.forIndex(topicCount++))
                    if (existingTopic == null) r = r.copy(topicsCreated = r.topicsCreated + 1)
                    val parsed = csv.open().use { CsvCardReader.read(it.readBytes()) }
                    val deckName = UriFiles.stripCsvExtension(csv.name)
                    val existingDeck = repo.deckByName(topic.id, deckName)
                    val res = repo.importCards(topic.id, deckName, csv.name, parsed.cards, existingDeck?.id)
                    r = r.copy(
                        decksCreated = r.decksCreated + if (existingDeck == null) 1 else 0,
                        decksUpdated = r.decksUpdated + if (existingDeck != null) 1 else 0,
                        cardsAdded = r.cardsAdded + res.added,
                        cardsChanged = r.cardsChanged + res.changed,
                        cardsRemoved = r.cardsRemoved + res.removed,
                    )
                }.onFailure { errors += "${csv.path.joinToString("/")}: ${it.message}" }
                done++
            }
            onProgress(BulkProgress(total, total, ""))
            r.copy(errors = errors)
        }

    private fun topicName(e: BulkEntry, root: String) = if (e.path.size >= 2) e.path[0] else root

    companion object {
        /** List a folder recursively through the Storage Access Framework. */
        fun treeEntries(context: android.content.Context, treeUri: Uri): Pair<String, List<BulkEntry>> {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Folder is not readable")
            val resolver: ContentResolver = context.contentResolver
            val out = ArrayList<BulkEntry>()
            fun walk(dir: DocumentFile, path: List<String>) {
                for (f in dir.listFiles()) {
                    val name = f.name ?: continue
                    if (f.isDirectory) walk(f, path + name)
                    else if (f.isFile) {
                        val uri = f.uri
                        out += BulkEntry(path + name) { resolver.openInputStream(uri) ?: error("File is not readable: $name") }
                    }
                }
            }
            walk(root, emptyList())
            return (root.name ?: "Import") to out
        }

        /** Copy the ZIP into the cache and list its entries (UTF-8, CP437 otherwise). */
        fun zipEntries(resolver: ContentResolver, uri: Uri, cacheDir: File, zipName: String): Triple<String, List<BulkEntry>, ZipFile> {
            val tmp = File(cacheDir, "import.zip")
            resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: error("ZIP is not readable")
            val zip = try {
                ZipFile(tmp, Charsets.UTF_8)
            } catch (_: ZipException) {
                ZipFile(tmp, Charset.forName("IBM437"))
            } catch (_: IllegalArgumentException) {
                ZipFile(tmp, Charset.forName("IBM437"))
            }
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.map { e ->
                BulkEntry(e.name.split('/', '\\').filter { it.isNotEmpty() }) { zip.getInputStream(e) }
            }.toList()
            return Triple(UriFiles.stripZip(zipName), entries, zip)
        }
    }
}

private fun UriFiles.stripZip(name: String) = name.replace(Regex("""\.zip$""", RegexOption.IGNORE_CASE), "")
