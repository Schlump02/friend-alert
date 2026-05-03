package xyz.mycompany.friendalert.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.mycompany.friendalert.App
import xyz.mycompany.friendalert.models.ContactEntity

private const val DAYS_IN_MONTH = 30

class ContactsViewModel : ViewModel() {
    private val repository = App.contactRepository

    // Contact lists
    private val _contactsWithFrequency = MutableStateFlow<List<ContactEntity>>(emptyList())

    private val _contactsWithoutFrequency = MutableStateFlow<List<ContactEntity>>(emptyList())

    private val _selectedContact = MutableStateFlow<ContactEntity?>(null)
    val selectedContact: StateFlow<ContactEntity?> = _selectedContact

    // Filtered lists
    private val _filteredContactsWithFrequency = MutableStateFlow<List<ContactEntity>>(emptyList())
    val filteredContactsWithFrequency: StateFlow<List<ContactEntity>> =
        _filteredContactsWithFrequency

    private val _filteredContactsWithoutFrequency =
        MutableStateFlow<List<ContactEntity>>(emptyList())
    val filteredContactsWithoutFrequency: StateFlow<List<ContactEntity>> =
        _filteredContactsWithoutFrequency

    val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        fetchContactListWithFrequency()
        fetchContactListWithoutFrequency()
    }


    fun updateContacts() {
        viewModelScope.launch {
            repository.syncDeviceContactsWithDb()
        }
    }

    suspend fun getDeviceContacts(searchText: String?): List<ContactEntity> {
        val deviceContacts = repository.getDeviceContacts(searchText)
        val lookupKeysInCurrentContacts = this._contactsWithFrequency.value.map { it.lookupKey }

        val filteredContacts =
            deviceContacts.filterNot { it.lookupKey in lookupKeysInCurrentContacts }

        return filteredContacts
    }


    fun fetchContactByLookupKey(lookupKey: String) {
        viewModelScope.launch {
            isLoading.value = true
            _selectedContact.value = repository.getContactByLookupKey(lookupKey)
            isLoading.value = false
        }
    }

    private fun applySearchQuery(
        query: String,
        originalList: List<ContactEntity>,
        filteredList: MutableStateFlow<List<ContactEntity>>
    ) {
        viewModelScope.launch {
            val filteredContacts = if (query.isNotBlank()) {
                originalList.filter { contact ->
                    contact.contactName?.contains(query, ignoreCase = true) == true
                }
            } else {
                originalList
            }
            filteredList.value = filteredContacts
        }
    }

    fun applySearchQueryToContactsWithFrequency(query: String) {
        applySearchQuery(
            query,
            _contactsWithFrequency.value,
            _filteredContactsWithFrequency
        )
    }

    fun applySearchQueryToContactsWithoutFrequency(query: String) {
        applySearchQuery(
            query,
            _contactsWithoutFrequency.value,
            _filteredContactsWithoutFrequency
        )
    }


    fun saveContact(contact: ContactEntity) {
        viewModelScope.launch {
            //contact.contactFrequency = frequencyValue
            repository.saveContact(contact)
        }
    }

    private fun fetchContactListWithFrequency() {
        viewModelScope.launch {
            repository.getContactsWithFrequencySet().collect { contacts ->
                val sortedContacts = contacts.sortedBy { it.daysUntilNextContact }
                _contactsWithFrequency.value = sortedContacts
                applySearchQuery("", _contactsWithFrequency.value, _filteredContactsWithFrequency)
            }
        }
    }

    private fun fetchContactListWithoutFrequency() {
        viewModelScope.launch {
            repository.getContactsWithoutFrequencySet().collect { contacts ->
                _contactsWithoutFrequency.value = contacts
                applySearchQuery(
                    "",
                    _contactsWithoutFrequency.value,
                    _filteredContactsWithoutFrequency
                )
            }
        }
    }

}
