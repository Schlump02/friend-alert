package xyz.mycompany.friendalert.fragments

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import xyz.mycompany.friendalert.contacts.ContactSettings
import xyz.mycompany.friendalert.databinding.ContactItemBinding
import xyz.mycompany.friendalert.models.ContactEntity

class ContactsAdapter :
    ListAdapter<ContactEntity, ContactsAdapter.ContactViewHolder>(ContactsDiffCallback()) {

    inner class ContactViewHolder(val binding: ContactItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val contact = getItem(adapterPosition)
                val intent = Intent(binding.root.context, ContactSettings::class.java)
                intent.putExtra("LOOKUP_KEY", contact.lookupKey)
                binding.root.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ContactItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)

        holder.binding.contact = contact

        val daysRemaining = contact.daysUntilNextContact

        holder.binding.daysRemainingTextView.text = when {
            daysRemaining == null -> ""
            daysRemaining > 62 -> {
                val months = daysRemaining / 30
                "noch $months Monate"
            }
            daysRemaining > 14 -> {
                val weeks = daysRemaining / 7
                "noch $weeks Wochen"
            }

            daysRemaining > 1 -> "$daysRemaining Tage verbleiben"
            daysRemaining == 1 -> "ab morgen fällig"
            daysRemaining == 0 -> "Heute fällig"
            daysRemaining > -62 -> "Überfällig seit ${-daysRemaining} Tagen"
            else -> {
                val overdueMonths = (-daysRemaining) / 30
                "Überfällig seit $overdueMonths Monaten"
            }
        }
    }

    class ContactsDiffCallback : DiffUtil.ItemCallback<ContactEntity>() {
        override fun areItemsTheSame(oldItem: ContactEntity, newItem: ContactEntity): Boolean {
            return oldItem.lookupKey == newItem.lookupKey
        }

        override fun areContentsTheSame(oldItem: ContactEntity, newItem: ContactEntity): Boolean {
            return oldItem == newItem
        }
    }
}
