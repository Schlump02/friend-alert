package xyz.mycompany.friendalert.fragments

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import xyz.mycompany.friendalert.ContactSettings
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
            daysRemaining > 30 -> {
                val months = daysRemaining / 30
                "$months months remaining"
            }

            daysRemaining > 0 -> "$daysRemaining days remaining"
            daysRemaining == 0 -> "Contact due today"
            daysRemaining > -30 -> "Contact overdue by ${-daysRemaining} days"
            else -> {
                val overdueMonths = (-daysRemaining) / 30
                "Contact overdue by $overdueMonths months"
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
