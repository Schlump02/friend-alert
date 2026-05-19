package xyz.mycompany.friendalert.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import xyz.mycompany.friendalert.models.ContactEntity

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY last_contacted_time DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMultiple(contacts: List<ContactEntity>)

    @Update
    fun updateContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE contact_id = :contactId")
    fun deleteContactByContactId(contactId: Long)

    @Query("SELECT * FROM contacts WHERE lookup_key = :lookupKey")
    fun getContactByLookupKey(lookupKey: String?): ContactEntity?

    @Query("SELECT * FROM contacts")
    fun getContactsSet(): Flow<List<ContactEntity>>

    @Query("SELECT contact_id FROM contacts")
    fun getAllContactIds(): List<Long>

    @Query("SELECT lookup_key FROM contacts")
    fun getAllLookupKeys(): List<String>

    @Query("SELECT * FROM contacts")
    fun getAllContactsCombined(): Flow<List<ContactEntity>>

    @Query("""
        UPDATE contacts SET 
            contact_name = :contactName, 
            phone_number = :phoneNumber, 
            photo_uri = :photoUri
        WHERE lookup_key = :lookupKey
    """
    )
    fun updateContactFromDevice(
        lookupKey: String?,
        contactName: String?,
        phoneNumber: String?,
        photoUri: String?
    )

    fun updateContactFromDevice(contact: ContactEntity) {
        updateContactFromDevice(
            lookupKey = contact.lookupKey,
            contactName = contact.contactName,
            phoneNumber = contact.phoneNumber,
            photoUri = contact.photoUri
        )
    }

    @Transaction
    fun updateOrInsertContacts(contactsFromDevice: List<ContactEntity>) {
        val existingLookupKeys = getAllLookupKeys().toSet()

        val contactsToInsert = contactsFromDevice.filterNot { it.lookupKey in existingLookupKeys }
        insertMultiple(contactsToInsert)

        val contactsToUpdate = contactsFromDevice.filter { it.lookupKey in existingLookupKeys }
        contactsToUpdate.forEach { updateContactFromDevice(it) }
    }

    @Query("""
        UPDATE contacts
        SET contact_frequency = CASE
            WHEN standard_frequency_mode = 'FREQUENT' THEN :frequentDays
            WHEN standard_frequency_mode = 'OCCASIONAL' THEN :occasionalDays
            WHEN standard_frequency_mode = 'RARE' THEN :rareDays
            ELSE contact_frequency
        END
    """)
    suspend fun updateContactsStandardFrequencies(frequentDays: Int, occasionalDays: Int, rareDays: Int)

    @Query("DELETE FROM contacts WHERE contact_id = :contactId")
    fun deleteContactById(contactId: Long) // ADDED DELETE FUNCTION HERE
}
