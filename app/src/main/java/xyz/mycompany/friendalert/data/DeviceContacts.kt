package xyz.mycompany.friendalert.data

import android.content.Context
import contacts.core.Contacts
import contacts.core.ContactsFields
import contacts.core.Fields
import contacts.core.contains
import contacts.core.desc
import contacts.core.entities.Contact
import contacts.core.greaterThan
import contacts.core.util.decomposedLookupKeys
import contacts.core.util.noteList
import contacts.core.util.phoneList
import contacts.core.whereOr
import xyz.mycompany.friendalert.models.ContactEntity

class DeviceContacts(
    private val settingsDao: SettingsDao,
    private val context: Context
) {
    private val lastSyncTimestampKey = "last_sync_timestamp"

    fun getContactByLookupKey(lookupKey: String): ContactEntity? {
        val contactDetails = Contacts(context).query()
            .where { decomposedLookupKeys(lookupKey) whereOr { Contact.LookupKey contains it } }
            .include(
                Fields.Contact.DisplayNamePrimary,
                Fields.Contact.LookupKey,
                Fields.Phone.Number,
                Fields.Contact.PhotoUri,
                Fields.Note.Note
            )
            .find().toList()
        // TODO: Handle multiple returned contacts

        return contactDetails.firstOrNull()?.let { contactToEntity(it) }
    }

    private fun contactToEntity(contact: Contact): ContactEntity {
        return ContactEntity(
            contact.id,
            contact.lookupKey,
            contact.displayNamePrimary,
            contact.phoneList().firstOrNull()?.number,
            null,
            null,
            contact.photoUri?.toString(),
            contact.noteList().firstOrNull()?.note,
            standardFrequencyMode = null
        )
    }

    suspend fun getUpdatedContacts(): List<ContactEntity> {
        val lastSynchronizedTime = getLastSyncTimestamp()
        updateLastSyncTimestamp()

        val contacts = Contacts(context).query()
            .where { Contact.LastUpdatedTimestamp.greaterThan(lastSynchronizedTime) }
            .include(
                Fields.Contact.DisplayNamePrimary,
                Fields.Contact.LookupKey,
                Fields.Phone.Number,
                Fields.Contact.PhotoUri,
                Fields.Note.Note
            )
            .find().toList()

        val updatedContacts = emptyList<ContactEntity>()
        for (contact in contacts) {
            updatedContacts.plus(contactToEntity(contact))
        }

        return updatedContacts
    }

    suspend fun getAllContacts(searchText: String?): List<ContactEntity> {
        val contacts = Contacts(context).broadQuery()
            .wherePartiallyMatches(searchText)
            .orderBy(ContactsFields.LastUpdatedTimestamp.desc())
            .include(
                Fields.Contact.DisplayNamePrimary,
                Fields.Contact.LookupKey,
                Fields.Phone.Number,
                Fields.Contact.PhotoUri,
                Fields.Note.Note
            )
            .find().toList()

        val updatedContacts = ArrayList<ContactEntity>()
        for (contact in contacts) {
            updatedContacts.add(contactToEntity(contact))
        }

        return updatedContacts
    }

    private suspend fun updateLastSyncTimestamp() {
        val setting = Setting(key = lastSyncTimestampKey, longValue = System.currentTimeMillis())
        settingsDao.insertOrUpdate(setting)
    }

    private suspend fun getLastSyncTimestamp(): Long {
        return settingsDao.getLong(lastSyncTimestampKey) ?: 0
    }

}