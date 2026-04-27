package xyz.mycompany.friendalert

import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import xyz.mycompany.friendalert.databinding.ActivityContactSettingsBinding
import xyz.mycompany.friendalert.models.ContactEntity
import xyz.mycompany.friendalert.viewmodels.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ContactSettings : AppCompatActivity() {

    private lateinit var binding: ActivityContactSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())
    private val viewModel: ContactsViewModel by viewModels()

    companion object {
        private const val LOOKUP_KEY_EXTRA = "LOOKUP_KEY"
        private const val BASIC_FREQUENCY_DAYS = 30
        private const val OCCASIONAL_FREQUENCY_DAYS = 180
        private const val RARE_FREQUENCY_DAYS = 360
        private const val DAYS_IN_WEEK = 7
        private const val DAYS_IN_MONTH = 30
        private const val FREQUENT_KEY = "notification_frequency_frequent"
        private const val OCCASIONAL_KEY = "notification_frequency_occasional"
        private const val RARE_KEY = "notification_frequency_rare"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_contact_settings)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        setupUi()
        val lookupKey = intent.getStringExtra(LOOKUP_KEY_EXTRA) ?: ""
        viewModel.fetchContactByLookupKey(lookupKey)
        observeLoadingState()
    }
    private fun setupUi() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up click listener for date picker
        binding.dateEditText.setOnClickListener { showDatePickerDialog() }

        // Set up click listener for save button and attach validation logic
        binding.saveButton.setOnClickListener {
            if (validateAndSave()) {
                showToast("Contact information saved!")
                finish()
            }
        }

        // Add listeners to re-evaluate saving status when frequency or date changes
        setupFrequencyChangeListeners()
    }

    private fun setupFrequencyChangeListeners() {
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.basicMode -> switchVisibility(
                        binding.basicFrequencyLayout,
                        binding.advancedFrequencyLayout
                    )
                    R.id.advancedMode -> switchVisibility(
                        binding.advancedFrequencyLayout,
                        binding.basicFrequencyLayout
                    )
                }
            }
        }

        // Listener for chip group changes (Basic Mode)
        findViewById<com.google.android.material.chip.ChipGroup>(R.id.basicFrequencyChipGroup).setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = getCheckedChip(group) ?: return@setOnCheckedStateChangeListener
                // Instead of passing the chip object, calculate the frequency in days based on the selection
                val newFrequency = when (selectedChip.id) {
                    // Assuming we can map IDs to constants or check text/tag if needed
                    R.id.frequentFriend -> BASIC_FREQUENCY_DAYS
                    R.id.occasionalFriend -> OCCASIONAL_FREQUENCY_DAYS
                    R.id.rareFriend -> RARE_FREQUENCY_DAYS
                    else -> null
                }
                if (newFrequency != null) {
                    setBasicModeUI(newFrequency)
                }
            }
            validateSaveButtonState() // Validate save state after chip change
        }

        // Listener for chip group changes (Advanced Mode) - Although selection is handled by setAdvancedModeUI,
        // we need to ensure validation runs when the user clicks a unit chip.
        findViewById<com.google.android.material.chip.ChipGroup>(R.id.frequencyUnitGroup).setOnCheckedStateChangeListener { _, _ ->
            // When advanced mode is active and units are selected, simply validating state is enough
            validateSaveButtonState()
        }
    }


    private fun switchVisibility(visibleView: View, goneView: View) {
        visibleView.visibility = View.VISIBLE
        goneView.visibility = View.GONE
    }

    private fun observeLoadingState() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                if (!loading) {
                    observeSelectedContact()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeSelectedContact() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedContact.collect { contact ->
                    handleContact(contact)
                }
            }
        }
    }

    private fun handleContact(contact: ContactEntity?) {
        contact?.let { _ ->
            binding.contact = contact
            binding.lifecycleOwner = this
            setContactedDate(contact.lastContactedTime)
            setContactFrequency(contact.contactFrequency)
            // Initialize notes EditText with the loaded contact's note data
            if (contact.notes != null) {
                binding.noteEditText.setText(contact.notes)
            } else {
                // If no notes, clear it to allow user input
                binding.noteEditText.setText("")
            }
        } ?: run {
            showToast("Contact not found!")
            finish()
        }
    }

    private fun setContactedDate(lastContactedTime: Long?) {
        if (lastContactedTime != null) {
            val date = dateFormat.format(Date(lastContactedTime))
            binding.dateEditText.setText("$date")
        } else {
            // Clear the field if no time is available, assuming the user needs to enter it
            binding.dateEditText.setText("")
        }
        // Re-evaluate save button state when date changes (or loads)
        validateSaveButtonState()
    }

    private fun setContactFrequency(frequencyInDays: Int?) {
        if (frequencyInDays == null) {
            setDefaultUI()
            binding.saveButton.isEnabled = false // Cannot save without frequency
            return
        }
        when (frequencyInDays) {
            in listOf(BASIC_FREQUENCY_DAYS, OCCASIONAL_FREQUENCY_DAYS, RARE_FREQUENCY_DAYS) -> setBasicModeUI(frequencyInDays)
            else -> setAdvancedModeUI(frequencyInDays)
        }
        // Re-evaluate save button state when frequency changes (or loads)
        validateSaveButtonState()
    }

    private fun setBasicModeUI(frequencyInDays: Int) {
        val basicFrequencyDays = sharedPreferences.getInt(FREQUENT_KEY, BASIC_FREQUENCY_DAYS)
        val occasionalFrequencyDays =
            sharedPreferences.getInt(OCCASIONAL_KEY, OCCASIONAL_FREQUENCY_DAYS)
        val rareFrequencyDays = sharedPreferences.getInt(RARE_KEY, RARE_FREQUENCY_DAYS)

        cleanupChipSelection(findViewById(R.id.basicFrequencyChipGroup))

        when (frequencyInDays) {
            basicFrequencyDays -> setBasicFrequencyUi(binding.frequentFriend)
            occasionalFrequencyDays -> setBasicFrequencyUi(binding.occasionalFriend)
            rareFrequencyDays -> setBasicFrequencyUi(binding.rareFriend)
        }
    }

    private fun setAdvancedModeUI(frequencyInDays: Int) {
        binding.advancedMode.isChecked = true
        binding.basicFrequencyLayout.visibility = View.GONE
        binding.advancedFrequencyLayout.visibility = View.VISIBLE

        // FIX: Replace clearChecked() with manual unchecking logic
        cleanupChipSelection(findViewById(R.id.frequencyUnitGroup))


        when {
            frequencyInDays % DAYS_IN_MONTH == 0 -> setFrequencyUi(
                binding.monthsChip,
                (frequencyInDays / DAYS_IN_MONTH).toString()
                // Pass the chip reference for later listener setup if needed
            )
            frequencyInDays % DAYS_IN_WEEK == 0 -> setFrequencyUi(
                binding.weeksChip,
                (frequencyInDays / DAYS_IN_WEEK).toString()
            )
            frequencyInDays > 0 -> setFrequencyUi(binding.daysChip, frequencyInDays.toString())
        }
    }

    private fun setFrequencyUi(chip: Chip, text: String) {
        if (!chip.isChecked) {
            chip.isChecked = true
        }
        binding.frequencyEditText.setText(text)
    }

    private fun setBasicFrequencyUi(chip: Chip) {
        // Reset all and select the provided one
        cleanupChipSelection(binding.basicFrequencyChipGroup)
        if (!chip.isChecked) {
            chip.isChecked = true
        }
    }

    private fun setDefaultUI() {
        binding.basicMode.isChecked = true
        binding.basicFrequencyLayout.visibility = View.VISIBLE
        binding.advancedFrequencyLayout.visibility = View.GONE
        // Ensure the save button state is updated when switching modes
        validateSaveButtonState()
    }

    /**
     * Helper function to find the currently checked chip in a group of chips.
     */
    private fun getCheckedChip(group: com.google.android.material.chip.ChipGroup): Chip? {
        for (i in 0 until group.childCount) {
            val view = group.getChildAt(i)
            if (view is Chip && view.isChecked) {
                return view
            }
        }
        return null
    }

    private fun cleanupChipSelection(chipGroup: com.google.android.material.chip.ChipGroup) {
        for (i in 0 until chipGroup.childCount) {
            val view = chipGroup.getChildAt(i)
            if (view is Chip) {
                // Set the checked state to false programmatically
                view.isChecked = false
            }
        }
    }

    private fun showDatePickerDialog() {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                binding.dateEditText.setText(dateFormat.format(calendar.time))
                binding.contact?.let {
                    it.lastContactedTime = calendar.timeInMillis
                }
                validateSaveButtonState() // Validate immediately after date change
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    /**
     * Performs validation and saves contact information if valid.
     * Returns true if save was successful, false otherwise.
     */
    private fun validateAndSave(): Boolean {
        // 1. Validate Date
        val dateText = binding.dateEditText.text.toString()
        if (dateText.isEmpty()) {
            showToast("Please set the last contact date.")
            return false
        }

        // 2. Get Frequency and validate
        val frequencyInDays = getFrequencyInDays()
        if (frequencyInDays == null) {
            showToast("Please set a contact frequency.")
            return false
        }

        binding.contact?.let { contact ->
            // Update the model object only if validation passed
            val newNotes = binding.noteEditText.text.toString()
            contact.notes = newNotes
            contact.contactFrequency = frequencyInDays
            viewModel.saveContact(contact, frequencyInDays)
        }
        return true
    }

    private fun getFrequencyInDays(): Int? {
        return if (binding.advancedMode.isChecked) {
            getAdvancedFrequency()
        } else if (binding.basicMode.isChecked) {
            getBasicFrequency()
        } else {
            null
        }
    }

    private fun getAdvancedFrequency(): Int? {
        val frequencyValueText = binding.frequencyEditText.text.toString()
        if (!frequencyValueText.isEmpty()) {
            val frequencyValue = frequencyValueText.toIntOrNull()
            return when {
                binding.daysChip.isChecked -> frequencyValue
                binding.weeksChip.isChecked -> frequencyValue?.times(DAYS_IN_WEEK)
                binding.monthsChip.isChecked -> frequencyValue?.times(DAYS_IN_MONTH)
                else -> null
            }
        } else {
            return null
        }
    }

    private fun getBasicFrequency(): Int? {
        return when {
            binding.frequentFriend.isChecked -> sharedPreferences.getInt(FREQUENT_KEY, 30)
            binding.occasionalFriend.isChecked -> sharedPreferences.getInt(OCCASIONAL_KEY, 180)
            binding.rareFriend.isChecked -> sharedPreferences.getInt(RARE_KEY, 360)
            else -> null
        }
    }

    /**
     * Determines if the Save button should be enabled based on current UI state.
     */
    private fun validateSaveButtonState() {
        val dateValid = binding.dateEditText.text.toString().isNotEmpty()
        val frequencyValid = getFrequencyInDays() != null
        binding.saveButton.isEnabled = dateValid && frequencyValid
    }

    // --- Utility Functions (Kept mostly the same) ---

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
