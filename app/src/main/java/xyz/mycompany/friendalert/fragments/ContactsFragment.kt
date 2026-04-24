package xyz.mycompany.friendalert.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import xyz.mycompany.friendalert.databinding.FragmentContactsBinding
import xyz.mycompany.friendalert.viewmodels.ContactsViewModel

abstract class ContactsFragment : Fragment() {

    protected abstract val viewModel: ContactsViewModel
    protected abstract val searchQueryFunction: (String) -> Unit

    lateinit var binding: FragmentContactsBinding
    lateinit var contactsAdapter: ContactsAdapter


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentContactsBinding.inflate(inflater, container, false)
        setupRecyclerView()
        setupSearchEditText()
        viewModel.updateContacts()
        return binding.root
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter()
        binding.contactsSetRecyclerView.adapter = contactsAdapter
        observeContacts()
    }

    abstract fun observeContacts()

    private fun setupSearchEditText() {
        val searchEditText = binding.searchEditText
        searchEditText.addTextChangedListener { editable ->
            searchQueryFunction(editable.toString())
        }

        val searchTextInputLayout = binding.textField
        searchTextInputLayout.setEndIconOnClickListener {
            searchEditText.text?.clear()
        }
    }


    override fun onPause() {
        super.onPause()
        clearSearchEditText()
    }

    private fun clearSearchEditText() {
        binding.searchEditText.text?.clear()
    }
}
