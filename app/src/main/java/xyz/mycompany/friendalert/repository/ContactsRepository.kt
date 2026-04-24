package xyz.mycompany.friendalert.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import xyz.mycompany.friendalert.data.ContactDao
import xyz.mycompany.friendalert.data.DeviceContacts
import xyz.mycompany.friendalert.models.ContactEntity
import java.util.UUID

class ContactRepository(
    private val contactDao: ContactDao,
    private val deviceContacts: DeviceContacts
) {

    suspend fun getContactByLookupKey(lookupKey: String): ContactEntity? {
        return withContext(Dispatchers.IO) {
            val phoneContact = deviceContacts.getContactByLookupKey(lookupKey)

            phoneContact?.let {
                contactDao.updateContactFromDevice(it)
            }
            contactDao.getContactByLookupKey(lookupKey)
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


    suspend fun getContactsWithFrequencySet(): Flow<List<ContactEntity>> {
        return withContext(Dispatchers.IO) {
            contactDao.getContactsWithFrequencySet()
        }
    }

    suspend fun getOverdueContacts(): List<ContactEntity> {
        val contacts = contactDao.getContactsWithFrequencySet().first()
        return contacts.filter { contact ->
            val days = contact.daysUntilNextContact
            days != null && days < 0 && contact.contactFrequency != null
        }
    }


    suspend fun getContactsWithoutFrequencySet(): Flow<List<ContactEntity>> {
        return withContext(Dispatchers.IO) {
            contactDao.getContactsWithoutFrequencySet()
        }
    }

    suspend fun migrateContacts() {
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
