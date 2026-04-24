package xyz.mycompany.friendalert.fragments

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.mycompany.friendalert.R
import xyz.mycompany.friendalert.viewmodels.ContactsViewModel

class ContactsNotSetFragment : ContactsFragment() {

    override val viewModel: ContactsViewModel by activityViewModels()
    override val searchQueryFunction: (String) -> Unit = { query ->
        lifecycleScope.launch {
            contactsAdapter.submitList(viewModel.getDeviceContacts(query))
        }
    }

    private val appCompatActivity: AppCompatActivity
        get() = requireActivity() as AppCompatActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fab.visibility = View.GONE
        setupActionBar()
    }


    override fun observeContacts() {
        lifecycleScope.launch { contactsAdapter.submitList(viewModel.getDeviceContacts(null)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val actionBar = appCompatActivity.supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(false)
        actionBar?.title = getString(R.string.app_name)
    }

    private fun setupActionBar() {
        appCompatActivity.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.add_contact)
        }

        appCompatActivity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            ?.setNavigationOnClickListener {
                parentFragmentManager.commit {
                    replace(R.id.fragment_container, ContactsSetFragment())
                }
            }
    }
}