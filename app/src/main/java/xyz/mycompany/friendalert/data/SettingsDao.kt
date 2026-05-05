package xyz.mycompany.friendalert.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val longValue: Long
)

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(setting: Setting)

    /** Retrieves the long value for a given global configuration key. */
    @Query("SELECT longValue FROM settings WHERE key = :key LIMIT 1")
    suspend fun getLong(key: String): Long?

    /**
     * Updates or inserts a system-wide default frequency setting (e.g., Occasional).
     * Returns true if the update succeeded, false otherwise.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGlobalSetting(setting: Setting)

    /**
     * CRITICAL: Updates the contact_frequency for ALL contacts that match a specific mode and day count.
     * This ensures that when a global default changes, all records are updated.
     */
    @Query("""
        UPDATE contacts SET 
            contact_frequency = :newFrequencyDays
        WHERE basic_frequency_mode = :targetModeName
    """)
    suspend fun updateAllContactsFrequency(targetModeName: String, newFrequencyDays: Int)
}
