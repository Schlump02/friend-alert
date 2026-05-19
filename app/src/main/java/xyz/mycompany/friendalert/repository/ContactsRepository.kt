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
import xyz.mycompany.friendalert.data.Setting
import xyz.mycompany.friendalert.utils.GlobalConfigKeys // NEW: Using global keys object

class ContactRepository(
    private val contactDao: ContactDao,
    private val deviceContacts: DeviceContacts,
    private val settingsDao: SettingsDao
) {
    /** Fetches all predefined global frequency defaults from the database. */
    suspend fun getGlobalFrequencyDefaults(): Map<String, Int> = withContext(Dispatchers.IO) {
        val frequentDays = settingsDao.getLong(GlobalConfigKeys.GLOBAL_FREQ_FREQUENT) ?: GlobalConfigKeys.DEFAULT_STANDARD_FREQUENCY_DAYS
        val occasionalDays = settingsDao.getLong(GlobalConfigKeys.GLOBAL_FREQ_OCCASIONAL) ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS
        val rareDays = settingsDao.getLong(GlobalConfigKeys.GLOBAL_FREQ_RARE) ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS

        mapOf(
            "FREQUENT" to frequentDays.toInt(),
            "OCCASIONAL" to occasionalDays.toInt(),
            "RARE" to rareDays.toInt()
        )
    }

    /** Updates a single global frequency setting AND propagates the change across all existing contacts. */
    suspend fun updateFrequencySetting(modeName: String, newDays: Int) = withContext(Dispatchers.IO) {
        val key = GlobalConfigKeys.mapModeToKey(modeName)

        settingsDao.updateGlobalSetting(
            Setting(key = key, longValue = newDays.toLong())
        )
        return@withContext
    }

    /** Updates a single global frequency setting AND propagates the change across all existing contacts. */
    suspend fun updateContactsStandardFrequencies(frequentDays: Int, occasionalDays: Int, rareDays: Int) = withContext(Dispatchers.IO) {
        val updateCount = contactDao.updateContactsStandardFrequencies(
            frequentDays,
            occasionalDays,
            rareDays
        )
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
                val updatedContact = ContactEntity(
                    contactId = existingContact.contactId,
                    lookupKey = existingContact.lookupKey,
                    contactName = contact.contactName,
                    phoneNumber = contact.phoneNumber,
                    lastContactedTime = contact.lastContactedTime,
                    contactFrequency = contact.contactFrequency,
                    photoUri = contact.photoUri,
                    notes = contact.notes,
                    standardFrequencyMode = contact.standardFrequencyMode
                )
                contactDao.updateContact(updatedContact)
            } else {
                val existingIds = contactDao.getAllContactIds()
                if(contact.contactId in existingIds){// a contact was passed which is not present in the database, but with an ID that is.
                    val maxId = existingIds.maxOrNull() ?: 0L
                    val newIdForUpdate = maxId + contact.contactId // the new id is controllable from the contact entity
                    val newContact = ContactEntity(
                        contactId = newIdForUpdate,
                        lookupKey = contact.lookupKey,
                        contactName = contact.contactName,
                        phoneNumber = contact.phoneNumber,
                        lastContactedTime = contact.lastContactedTime,
                        contactFrequency = contact.contactFrequency,
                        photoUri = contact.photoUri,
                        notes = contact.notes,
                        standardFrequencyMode = contact.standardFrequencyMode
                    )
                    contactDao.insertContact(newContact)
                }else{
                    // a completely new contact is added
                    contactDao.insertContact(contact)
                }
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

    suspend fun deleteContact(contactId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            contactDao.deleteContactById(contactId)
            return@withContext true
        } catch (e: Exception) {
            Log.e("ContactRepository", "Error deleting contact $contactId: ${e.message}")
            return@withContext false
        }
    }
}
