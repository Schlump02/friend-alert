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
    override val searchQueryFunction: (String) -> Unit =
        { query -> viewModel.applySearchQueryToContactsWithFrequency(query) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                viewModel.filteredContactsWithFrequency.collect { contactsList ->
                    contactsAdapter.submitList(contactsList)
                }
            }
        }
    }
}