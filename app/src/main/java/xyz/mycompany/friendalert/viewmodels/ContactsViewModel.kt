package xyz.mycompany.friendalert.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.mycompany.friendalert.App
import xyz.mycompany.friendalert.models.ContactEntity

class ContactsViewModel : ViewModel() {
    private val repository = App.contactRepository

    private val _contactsList = MutableStateFlow<List<ContactEntity>>(emptyList())

    private val _selectedContact = MutableStateFlow<ContactEntity?>(null)
    val selectedContact: StateFlow<ContactEntity?> = _selectedContact

    private val _filteredContactsList = MutableStateFlow<List<ContactEntity>>(emptyList())
    val filteredContacts: StateFlow<List<ContactEntity>> =
        _filteredContactsList

    val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        fetchContactList()
    }


    fun updateContacts() {
        viewModelScope.launch {
            repository.syncDeviceContactsWithDb()
        }
    }

    suspend fun getDeviceContacts(searchText: String?): List<ContactEntity> {
        val deviceContacts = repository.getDeviceContacts(searchText)
        val lookupKeysInCurrentContacts = this._contactsList.value.map { it.lookupKey }

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

    fun applySearchQueryToContacts(query: String) {
        applySearchQuery(
            query,
            _contactsList.value,
            _filteredContactsList
        )
    }

    fun saveContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.saveContact(contact)
        }
    }

    private fun fetchContactList() {
        viewModelScope.launch {
            repository.getContactsSet().collect { contacts ->
                val sortedContacts = contacts.sortedBy { it.daysUntilNextContact }
                _contactsList.value = sortedContacts
                applySearchQuery("", _contactsList.value, _filteredContactsList)
            }
        }
    }
}
