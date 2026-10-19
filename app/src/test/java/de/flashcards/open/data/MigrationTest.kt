package de.flashcards.open.data

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.flashcards.open.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Version 1 → 2: cards gain `type` and `options`, the study progress is kept. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val cardV1 = """
        CREATE TABLE card (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, deckId INTEGER NOT NULL,
          frontMd TEXT NOT NULL, backMd TEXT NOT NULL, tags TEXT NOT NULL, orderIndex INTEGER NOT NULL,
          contentKey TEXT NOT NULL, state TEXT NOT NULL, dueAt INTEGER NOT NULL, intervalDays INTEGER NOT NULL,
          ease REAL NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, step INTEGER NOT NULL)
    """.trimIndent()

    @Test fun oldCardsBecomeBasicCardsAndKeepTheirProgress() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = File(ctx.cacheDir, "migration-1-2.db").apply { delete() }
        val connection = AndroidSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL(cardV1)
            connection.execSQL(
                """
                INSERT INTO card (deckId, frontMd, backMd, tags, orderIndex, contentKey, state, dueAt,
                                  intervalDays, ease, reps, lapses, step)
                VALUES (1, 'vorne', 'hinten', 'tag', 0, 'key', 'REVIEW', 1234, 7, 2.35, 3, 1, 0)
                """.trimIndent(),
            )

            AppDatabase.MIGRATION_1_2.migrate(connection)

            connection.prepare("SELECT type, options, frontMd, state, intervalDays, ease, reps FROM card").use { st ->
                assertTrue(st.step())
                assertEquals("BASIC", st.getText(0))
                assertEquals("", st.getText(1))
                assertEquals("vorne", st.getText(2))
                assertEquals("REVIEW", st.getText(3))
                assertEquals(7, st.getLong(4).toInt())
                assertEquals(2.35, st.getDouble(5), 0.0001)
                assertEquals(3, st.getLong(6).toInt())
            }
        } finally {
            connection.close()
            file.delete()
        }
    }

    @Test fun migrationCoversExactlyOneStep() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }
}
