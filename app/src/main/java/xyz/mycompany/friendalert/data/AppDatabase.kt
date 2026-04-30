package xyz.mycompany.friendalert.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import xyz.mycompany.friendalert.models.ContactEntity
@Database(entities = [ContactEntity::class, Setting::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun settingsDao(): SettingsDao
    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }
        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, "userdb")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .build()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the 'notes' column to the 'contacts' table
        database.execSQL("ALTER TABLE contacts ADD COLUMN notes TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the 'lookup_key' column to the 'contacts' table
        database.execSQL("ALTER TABLE contacts ADD COLUMN lookup_key TEXT")
    }
}

// --- NEW MIGRATION FOR UNIQUE CONSTRAINT (Version 3 -> Version 4) ---
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create the unique index on the lookup_key column.
        // This is the SQL command that enforces uniqueness and triggers Room's validation logic.
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lookup ON contacts (lookup_key)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE contacts ADD COLUMN basic_frequency_mode TEXT")
    }
}
