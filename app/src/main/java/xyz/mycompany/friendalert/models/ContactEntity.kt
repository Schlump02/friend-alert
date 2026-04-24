package xyz.mycompany.friendalert.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "contact_id") val contactId: Long,
    @ColumnInfo(name = "lookup_key") var lookupKey: String?,
    @ColumnInfo(name = "contact_name") var contactName: String?,
    @ColumnInfo(name = "phone_number") var phoneNumber: String?,
    @ColumnInfo(name = "last_contacted_time") var lastContactedTime: Long?,
    @ColumnInfo(name = "contact_frequency") var contactFrequency: Int?,
    @ColumnInfo(name = "photo_uri") var photoUri: String?,
    @ColumnInfo(name = "notes") var notes: String?
) {
    val daysUntilNextContact: Int?
        get() {
            if (lastContactedTime == null || contactFrequency == null) {
                return null
            }

            val currentTime = Date().time
            val daysSinceLastContact =
                TimeUnit.MILLISECONDS.toDays(currentTime - lastContactedTime!!)
            return contactFrequency!! - daysSinceLastContact.toInt()
        }

    val isSoonOverdue: Boolean
        get() {
            return if (daysUntilNextContact != null) daysUntilNextContact!! < 8 else false
        }

    val isOverdue: Boolean
        get() {
            return if (daysUntilNextContact != null) daysUntilNextContact!! < 0 else false
        }

    val initials: String
        get() {
            if (contactName == null || contactName!!.isEmpty()) {
                return ""
            }
            val parts = contactName!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            return when (parts.size) {
                1 -> parts[0].substring(0, 1).uppercase(Locale.getDefault())
                else -> parts[0].substring(0, 1)
                    .uppercase(Locale.getDefault()) + parts[1].substring(
                    0,
                    1
                ).uppercase(
                    Locale.getDefault()
                )
            }
        }
}