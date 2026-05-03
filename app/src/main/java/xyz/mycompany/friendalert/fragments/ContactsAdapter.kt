package xyz.mycompany.friendalert.fragments

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import xyz.mycompany.friendalert.ContactSettings // We need access to the constants/logic here
import xyz.mycompany.friendalert.databinding.ContactItemBinding
import xyz.mycompany.friendalert.models.ContactEntity
import java.util.concurrent.TimeUnit

class ContactsAdapter:
    ListAdapter<ContactEntity, ContactsAdapter.ContactViewHolder>(ContactsDiffCallback()) {
    inner class ContactViewHolder(val binding: ContactItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val contact = getItem(adapterPosition)
                // Ensure context is available for intent creation
                val context = binding.root.context
                val intent = android.content.Intent(context, ContactSettings::class.java)
                intent.putExtra("LOOKUP_KEY", contact.lookupKey)
                context.startActivity(intent)
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

        // --- START MODIFIED LOGIC ---
        var daysRemainingDisplay: String? = null

        // 1. Determine the actual frequency to use (Stored vs Default fallback)
        // This function encapsulates the logic that checks if the model's frequency is set,
        // and if not, tries to determine a default based on basic/advanced mode settings.
        val effectiveFrequencyDays = getEffectiveContactFrequency(contact, holder.binding.root.context as Context)

        // 2. Calculate days remaining using the effective frequency
        if (effectiveFrequencyDays != null && contact.lastContactedTime != null) {
            var daysUntilNext: Int? = null
            val currentTime = contact.currentTime
            val daysSinceLastContact = TimeUnit.MILLISECONDS.toDays(currentTime - contact.lastContactedTime!!)

            // Calculate remaining days: Frequency - Days Passed
            daysUntilNext = effectiveFrequencyDays - daysSinceLastContact.toInt()

            daysRemainingDisplay = calculateDisplayString(daysUntilNext)
        } else if (contact.lastContactedTime != null && effectiveFrequencyDays == null) {
            // Case: No frequency set, but last contact time is available (Show simple overdue status)
            val daysSince = TimeUnit.MILLISECONDS.toDays(contact.currentTime - contact.lastContactedTime!!)
            daysRemainingDisplay = if (daysSince > 0) "Überfällig seit $daysSince Tagen" else "Recently contacted"
        } else {
            // Case: No relevant data
            daysRemainingDisplay = null
        }

        holder.binding.daysRemainingTextView.text = daysRemainingDisplay ?: ""
        // --- END MODIFIED LOGIC ---
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

/**
 * Calculates the effective frequency in days. If contactFrequency is null, it attempts to use
 * default frequencies based on the Basic/Advanced mode setup stored in SharedPreferences.
 */
private fun getEffectiveContactFrequency(contact: ContactEntity, context: Context): Int? {
    if (contact.contactFrequency != null) {
        return contact.contactFrequency
    }

    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    // to ensure consistency with ContactSettings/App module. For this isolated adapter function, we use a placeholder check.

    val basicModeName = contact.basicFrequencyMode
    if (basicModeName != null) {
        return when (basicModeName) {
            "FREQUENT" -> sharedPreferences.getInt(ContactSettings.FREQUENT_KEY, ContactSettings.BASIC_FREQUENCY_DAYS)
            "OCCASIONAL" -> sharedPreferences.getInt(ContactSettings.OCCASIONAL_KEY, ContactSettings.OCCASIONAL_FREQUENCY_DAYS)
            "RARE" -> sharedPreferences.getInt(ContactSettings.RARE_KEY, ContactSettings.RARE_FREQUENCY_DAYS)
            else -> null
        }
    }

    // Advanced mode fallback logic (Simplified for adapter context)
    return null
}

/** Helper function to format the days remaining string for display. */
private fun calculateDisplayString(daysUntilNext: Int): String? {
    return when {
        daysUntilNext > 62 -> {
            val months = (daysUntilNext / 30).toInt()
            "noch $months Monate"
        }
        daysUntilNext > 14 -> {
            val weeks = (daysUntilNext / 7).toInt()
            "noch $weeks Wochen"
        }
        daysUntilNext > 1 -> "$daysUntilNext Tage verbleiben"
        daysUntilNext == 1 -> "ab morgen fällig"
        daysUntilNext == 0 -> "Heute fällig"
        // If negative, it's overdue. Display the absolute days over due.
        else -> if (daysUntilNext < 0) {
            val overdueDays = (-daysUntilNext).toInt()
            if (overdueDays > 30) {
                val monthsOverdue = (overdueDays / 30).toInt()
                "Überfällig seit $monthsOverdue Monaten"
            } else {
                "Überfällig seit $overdueDays Tagen"
            }
        } else {
            null // Should not happen if logic is correct, but safe fallback.
        }
    }
}
