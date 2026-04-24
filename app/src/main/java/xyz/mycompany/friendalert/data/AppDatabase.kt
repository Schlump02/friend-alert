package xyz.mycompany.friendalert.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import xyz.mycompany.friendalert.models.ContactEntity

@Database(entities = [ContactEntity::class, Setting::class], version = 3, exportSchema = false)
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
