package de.flashcards.open.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [Topic::class, Deck::class, Card::class, ReviewLog::class, Media::class, StudyPreset::class, StudyPresetDeck::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun mediaDao(): MediaDao
    abstract fun presetDao(): PresetDao

    companion object {
        const val NAME = "flashcards.db"

        /** Card types: `type` and `options` are added, existing cards stay basic cards. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE card ADD COLUMN type TEXT NOT NULL DEFAULT 'BASIC'")
                connection.execSQL("ALTER TABLE card ADD COLUMN options TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
