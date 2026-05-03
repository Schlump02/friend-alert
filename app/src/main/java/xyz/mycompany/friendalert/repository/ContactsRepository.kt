package xyz.mycompany.friendalert.repository
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import xyz.mycompany.friendalert.data.ContactDao
import xyz.mycompany.friendalert.data.SettingsDao
import xyz.mycompany.friendalert.data.DeviceContacts
import xyz.mycompany.friendalert.models.ContactEntity
import java.util.UUID
import androidx.room.Transaction
import xyz.mycompany.friendalert.data.Setting
import xyz.mycompany.friendalert.utils.GlobalConfigKeys // NEW: Using global keys object

class ContactRepository(
    private val contactDao: ContactDao,
    private val deviceContacts: DeviceContacts,
    private val settingsDao: SettingsDao
) {
    /** Fetches all predefined global frequency defaults from the database. */
    suspend fun getGlobalFrequencyDefaults(): Map<String, Int> = withContext(Dispatchers.IO) {
        val frequentDays = settingsDao.getLong(GlobalConfigKeys.GLOBAL_FREQ_FREQUENT) ?: 30L
        val occasionalDays = settingsDao.getLong(GlobalConfigKeys.GLOBAL_FREQ_OCCASIONAL) ?: 150L
        val rareDays = settingsDao.getLong(GlobalConfigKeys.GLOBAL_FREQ_RARE) ?: 300L

        mapOf(
            "FREQUENT" to frequentDays.toInt(),
            "OCCASIONAL" to occasionalDays.toInt(),
            "RARE" to rareDays.toInt()
        )
    }

    /** Updates a single global frequency setting AND propagates the change across all existing contacts. */
    suspend fun updateGlobalFrequency(modeName: String, newDays: Int) = withContext(Dispatchers.IO) {
        val key = GlobalConfigKeys.mapModeToKey(modeName)

        // 1. Update the global setting first (The Source of Truth)
        settingsDao.updateGlobalSetting(
            Setting(key = key, longValue = newDays.toLong())
        )

        // 2. PROPAGATION STEP: Tell the database to update all records using this mode.
        // We pass a placeholder for oldFrequencyPlaceholder because of SQL query complexity.
        val updateCount = settingsDao.updateAllContactsFrequency(
            targetModeName = modeName,
            newFrequencyDays = newDays
        )
        Log.d("Repo", "Successfully triggered global update for $modeName ($newDays days). ${updateCount} contacts potentially affected.")
        return@withContext
    }

    suspend fun getContactByLookupKey(lookupKey: String): ContactEntity? {
        return withContext(Dispatchers.IO) {
            val databaseContact = contactDao.getContactByLookupKey(lookupKey)
            databaseContact?.let {
                // Only update Uri and Name on the local device copy if necessary, but don't overwrite core settings.
                val phoneContact = deviceContacts.getContactByLookupKey(lookupKey)
                phoneContact?.let { contact ->
                    contactDao.updateContactFromDevice(contact)
                }
                return@withContext databaseContact
            }
            // Fallback to device data if not in DB
            deviceContacts.getContactByLookupKey(lookupKey)
        }
    }

    suspend fun saveContact(contact: ContactEntity) {
        withContext(Dispatchers.IO) {
            val existingContact = contactDao.getContactByLookupKey(contact.lookupKey)
            if (existingContact != null) {
                contactDao.updateContact(contact)
            } else {
                contactDao.insertContact(contact)
            }
        }
    }

    suspend fun syncDeviceContactsWithDb() = withContext(Dispatchers.IO) {
        val contactsFromDevice = deviceContacts.getUpdatedContacts()
        Log.d("ContactsRepository", "Fetched ${contactsFromDevice.size} contacts from device.")
        contactDao.updateOrInsertContacts(contactsFromDevice)
    }
    suspend fun getDeviceContacts(searchText: String?) = withContext(Dispatchers.IO) {
        return@withContext deviceContacts.getAllContacts(searchText)
    }
    fun getContactsForExport(): Flow<List<ContactEntity>> {
        return contactDao.getAllContactsCombined()
    }

    suspend fun getContactsSet(): Flow<List<ContactEntity>> {
        // Use the existing combined flow
        return withContext(Dispatchers.IO) {
            contactDao.getContactsSet()
        }
    }

    /**
     * Gets contacts that are overdue, respecting their individual or global frequency setting.
     */
    suspend fun getOverdueContacts(): List<ContactEntity> {
        // Since the logic relies on daysUntilNextContact getter which is computed on load,
        // we fetch all and filter locally for simplicity in this refactoring step.
        val contacts = contactDao.getContactsSet().first()
        return contacts.filter { contact ->
            val days = contact.daysUntilNextContact
            days != null && days < 0 && (contact.contactFrequency == null || contact.contactFrequency != 0)
        }
    }
    suspend fun migrateContacts() {
        // ... migration logic remains the same
        val contactsToMigrate = contactDao.getContactsWithoutLookupKey()
        contactsToMigrate.forEach { contact ->
            val newLookupKey = deviceContacts.fetchLookupIdForContact(contact.contactId)
            if (newLookupKey != null) {
                contact.lookupKey = newLookupKey
                contactDao.updateContact(contact)
            } else if (contact.lastContactedTime != null || contact.contactFrequency != null) {
                contact.lookupKey = UUID.randomUUID().toString()
                contactDao.updateContact(contact)
            } else {
                contactDao.deleteContactByContactId(contact.contactId)
            }
        }
    }
}
