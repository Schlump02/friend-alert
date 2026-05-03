package xyz.mycompany.friendalert.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import xyz.mycompany.friendalert.R
import xyz.mycompany.friendalert.viewmodels.ContactsViewModel

class ContactsSetFragment : ContactsFragment() {
    override val viewModel: ContactsViewModel by activityViewModels()
    // We need a way to pass the default preferences here. The simplest is accessing the context's shared prefs.
    private lateinit var sharedPreferences: android.content.SharedPreferences

    override val searchQueryFunction: (String) -> Unit =
        { query -> viewModel.applySearchQueryToContacts(query) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Initialize shared preferences dependency here
        sharedPreferences = requireContext().getSharedPreferences("default_prefs", 0)

        val fab: FloatingActionButton = binding.fab
        fab.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragment_container, ContactsNotSetFragment())
            }
        }
    }
    override fun observeContacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Initialize adapter with shared preferences instance
                contactsAdapter = ContactsAdapter()
                viewModel.filteredContacts.collect { contactsList ->
                    contactsAdapter.submitList(contactsList)
                }
            }
        }
    }
}
