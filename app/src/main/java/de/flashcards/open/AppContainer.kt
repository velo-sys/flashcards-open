package de.flashcards.open

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import de.flashcards.open.data.db.AppDatabase
import de.flashcards.open.data.io.BackupService
import de.flashcards.open.data.io.BulkImporter
import de.flashcards.open.data.repo.FlashcardRepository
import de.flashcards.open.data.repo.MediaRepository
import de.flashcards.open.data.repo.SettingsRepository
import de.flashcards.open.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Messages for the user (snackbar). Errors are never swallowed silently. */
class Messenger(private val context: Context) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    fun show(text: String) {
        _messages.tryEmit(text)
    }

    fun show(@StringRes res: Int, vararg args: Any) = show(context.getString(res, *args))

    fun showPlural(@PluralsRes res: Int, count: Int, vararg args: Any) =
        show(context.resources.getQuantityString(res, count, *args))

    fun error(t: Throwable) = show(R.string.error_generic, t.message ?: t.javaClass.simpleName)
}

/** Manual dependency injection. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob())
    val db = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    val media = MediaRepository(app.filesDir, db, scope)
    val repo = FlashcardRepository(db, settings, Sm2Scheduler, scope)
    val bulkImporter = BulkImporter(repo, media)
    val backup = BackupService(db, settings, media, app.filesDir, app.cacheDir)
    val messenger = Messenger(app)
}
