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

    @Query("SELECT longValue FROM settings WHERE key = :key")
    suspend fun getLong(key: String): Long?
}