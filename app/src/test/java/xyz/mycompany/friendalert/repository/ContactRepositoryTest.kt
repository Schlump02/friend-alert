import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import xyz.mycompany.friendalert.data.ContactDao
import xyz.mycompany.friendalert.data.DeviceContacts
import xyz.mycompany.friendalert.models.ContactEntity
import xyz.mycompany.friendalert.repository.ContactRepository

class ContactRepositoryTest {

    private lateinit var contactDao: ContactDao
    private lateinit var deviceContacts: DeviceContacts
    private lateinit var contactRepository: ContactRepository

    // Commonly used test data.
    private val lookupKey = "CRC7723"
    private val contactEntity =
        ContactEntity(
            1,
            lookupKey,
            "John Doe",
            "1234567890",
            null,
            67,
            null,
            "Met in London."
        )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        contactDao = mockk(relaxed = true)
        deviceContacts = mockk(relaxed = true)

        contactRepository = ContactRepository(contactDao, deviceContacts)
    }

    @Test
    fun `should return contact by Lookup Key`() = runTest {
        every { deviceContacts.getContactByLookupKey(lookupKey) } returns contactEntity
        every { contactDao.getContactByLookupKey(lookupKey) } returns contactEntity

        val result = contactRepository.getContactByLookupKey(lookupKey)

        assert(result == contactEntity)
    }

    @Test
    fun `should insert new contact when contact not found in DB`() = runTest {
        every { contactDao.getContactByLookupKey(contactEntity.lookupKey) } returns null
        justRun { contactDao.insertContact(contactEntity) }

        contactRepository.saveContact(contactEntity)

        verify { contactDao.insertContact(contactEntity) }
    }

    @Test
    fun `should update contact when contact found in DB`() = runTest {
        every { contactDao.getContactByLookupKey(contactEntity.lookupKey) } returns contactEntity
        justRun { contactDao.updateContact(contactEntity) }

        contactRepository.saveContact(contactEntity)

        verify { contactDao.updateContact(contactEntity) }
    }

    @Test
    fun `should sync device contacts with DB`() = runTest {
        coEvery { deviceContacts.getUpdatedContacts() } returns listOf(contactEntity)
        every { contactDao.getAllContactIds() } returns listOf(1)
        justRun { contactDao.updateContactFromDevice(any()) }

        contactRepository.syncDeviceContactsWithDb()

        verify { contactDao.updateOrInsertContacts(any()) }
    }

    @Test
    fun `getContactById returns null when contact does not exist on device and db`() = runTest {
        every { deviceContacts.getContactByLookupKey(any()) } returns null
        every { contactDao.getContactByLookupKey(any()) } returns null

        val result = contactRepository.getContactByLookupKey(lookupKey)

        assert(result == null)
    }

    @Test
    fun `saveContact updates contact when it already exists in the database`() = runTest {
        every { contactDao.getContactByLookupKey(contactEntity.lookupKey) } returns contactEntity
        justRun { contactDao.updateContact(contactEntity) }

        contactRepository.saveContact(contactEntity)

        verify(exactly = 1) { contactDao.updateContact(contactEntity) }
        verify(exactly = 0) { contactDao.insertContact(contactEntity) }
    }

    @Test
    fun `saveContact inserts contact when it does not exist in the database`() = runTest {
        every { contactDao.getContactByLookupKey(contactEntity.lookupKey) } returns null
        justRun { contactDao.insertContact(contactEntity) }

        contactRepository.saveContact(contactEntity)

        verify(exactly = 1) { contactDao.insertContact(contactEntity) }
        verify(exactly = 0) { contactDao.updateContact(contactEntity) }
    }
}
