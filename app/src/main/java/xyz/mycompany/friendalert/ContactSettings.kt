package xyz.mycompany.friendalert

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.mycompany.friendalert.databinding.ActivityContactSettingsBinding
import xyz.mycompany.friendalert.models.ContactEntity
import xyz.mycompany.friendalert.viewmodels.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.*

class ContactSettings : AppCompatActivity() {
    private lateinit var binding: ActivityContactSettingsBinding
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())
    private val viewModel: ContactsViewModel by viewModels()

    // --- Global Constants for UI/Mode Mapping (Derived from the global keys) ---
    companion object {
        private const val LOOKUP_KEY_EXTRA = "LOOKUP_KEY"

        val BASIC_MODE_ID = R.id.basicMode
        val ADVANCED_MODE_ID = R.id.advancedMode

        // Mode names must match the keys used in GlobalConfigKeys and DAO updates
        private const val MODE_FREQUENT = "FREQUENT"
        private const val MODE_OCCASIONAL = "OCCASIONAL"
        private const val MODE_RARE = "RARE"

        // Frequency chip IDs
        val CHIP_FREQ = R.id.frequentFriend
        val CHIP_OCCA = R.id.occasionalFriend
        val CHIP_RARE = R.id.rareFriend
    }

    private fun setupListeners() {
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

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.deleteButton)?.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // setup frequency change listeners
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

        // 2. Basic Mode Chip Group Listener (Basic Mode)
        val basicChipGroup = findViewById<ChipGroup>(R.id.basicFrequencyChipGroup)
        basicChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = getCheckedChip(group) ?: return@setOnCheckedStateChangeListener
                handleBasicModeSelection(selectedChip.id)
            } else {
                // If nothing is checked, clear mode and validate
                binding.contact?.let { contact ->
                    contact.basicFrequencyMode = null
                }
                validateSaveButtonState()
            }
        }

        // 3. Advanced Mode Chip Group Listener (Advanced Mode)
        val unitGroup = findViewById<ChipGroup>(R.id.frequencyUnitGroup)
        unitGroup.setOnCheckedStateChangeListener { _, _ ->
            validateSaveButtonState() // Validate save state after advanced selection change
        }
    }

    private fun switchVisibility(visibleView: View, goneView: View) {
        visibleView.visibility = View.VISIBLE
        goneView.visibility = View.GONE
    }

    // --- Lifecycle Observation ---
    private fun observeLoadingState() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                if (!loading && !isFinishing()) {
                    observeSelectedContact()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContactSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Check if a LOOKUP_KEY was passed via the Intent (i.e., navigating from ContactsList)
        val lookupKey = intent.getStringExtra("LOOKUP_KEY")

        // 2. Initialize UI components and listeners AFTER handling the initial data load,
        // or ensure that setupListeners() is called only after binding exists.
        setupListeners()

        if (lookupKey != null) {
            // If a key was passed, explicitly tell the ViewModel to fetch this contact immediately.
            viewModel.fetchContactByLookupKey(lookupKey)
        } else {
            // Fallback: Handle cases where we navigate here without an explicit key
            // (e.g., testing/global settings view).
            showToast("Error: No contact ID provided.")
            finish()
        }

        observeLoadingState() // Start observing selected contact once initialized
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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

    // --- Handling Contact Data Flow ---

    private fun handleContact(contact: ContactEntity?) {
        contact?.let { _ ->
            binding.contact = contact
            binding.lifecycleOwner = this
            setContactedDate(contact.lastContactedTime)
            // Pass the frequency to setContactFrequency which handles basic/advanced mode
            setContactFrequency(contact.contactFrequency, contact.basicFrequencyMode)
            if (contact.notes != null) {
                binding.noteEditText.setText(contact.notes)
            } else {
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
            binding.dateEditText.setText("")
        }
        validateSaveButtonState()
    }

    private fun setContactFrequency(initialFrequency: Int?, initialModeName: String?) {
        // If frequency is null, we must load the global default from the database instead of relying on a passed value.
        val currentGlobalDefaults = runBlocking {
            App.contactRepository.getGlobalFrequencyDefaults()
        }

        var effectiveFrequency: Int? = initialFrequency // Use existing contact freq if available
        var effectiveModeName: String? = initialModeName // Use existing basic mode if available

        if (effectiveFrequency == null && effectiveModeName == null) {
            // No frequency data found in the DB -> Default to current global defaults
            Log.d("Settings", "No specific frequency set, loading system default.")
            if (initialModeName == null && initialFrequency == null) {
                val defaultFreq = when(effectiveModeName) {
                    MODE_FREQUENT -> currentGlobalDefaults[MODE_FREQUENT]
                    MODE_OCCASIONAL -> currentGlobalDefaults[MODE_OCCASIONAL]
                    MODE_RARE -> currentGlobalDefaults[MODE_RARE]
                    else -> null
                }
                effectiveFrequency = defaultFreq
            }
        }

        if (effectiveModeName == null) {
            // If we have a frequency but no mode name, try to deduce the mode based on global defaults
            val basicModeMatch = when (initialFrequency) {
                currentGlobalDefaults[MODE_FREQUENT] -> MODE_FREQUENT
                currentGlobalDefaults[MODE_OCCASIONAL] -> MODE_OCCASIONAL
                currentGlobalDefaults[MODE_RARE] -> MODE_RARE
                else -> null
            }
            effectiveModeName = basicModeMatch
        }


        if (effectiveFrequency != null && effectiveModeName != null) {
            setBasicModeUI(effectiveFrequency, effectiveModeName)
        } else if (effectiveFrequency != null && effectiveModeName == null) {
            // Advanced mode set correctly
            setAdvancedModeUI(effectiveFrequency)
        } else {
            // Handle empty/initial state gracefully
            setDefaultUI()
            binding.contact?.let { it.basicFrequencyMode = null }
        }

        validateSaveButtonState()
    }


    // --- Core Logic: Basic Mode Handling ---

    private fun handleBasicModeSelection(selectedId: Int) {
        val contact = binding.contact ?: return
        var newFrequency = 0
        val modeName = when (selectedId) {
            CHIP_FREQ -> MODE_FREQUENT
            CHIP_OCCA -> MODE_OCCASIONAL
            CHIP_RARE -> MODE_RARE
            else -> null
        }

        if (modeName != null) {
            // Use the global default frequency from the database, ignoring hardcoded values.
            val currentGlobalDefaults = runBlocking { App.contactRepository.getGlobalFrequencyDefaults() }
            newFrequency = currentGlobalDefaults[modeName] ?: 0 // Fallback to 0 if lookup fails
        } else {
            return
        }

        // 1. Update the model object with the selected mode name and frequency
        contact.basicFrequencyMode = modeName
        contact.contactFrequency = newFrequency
        setBasicModeUI(newFrequency, modeName)
    }

    /** Sets the basic mode UI and updates the contact model's basicFrequencyMode. */
    private fun setBasicModeUI(frequencyInDays: Int, modeName: String) {
        // 1. Update the model object with the selected mode name
        binding.contact?.let { contact ->
            contact.basicFrequencyMode = modeName // Set the basic mode string
            contact.contactFrequency = frequencyInDays
        }

        // 2. Set the UI chips based on which preference was used to generate this frequency
        val chipIdToSelect = when(modeName) {
            MODE_FREQUENT -> CHIP_FREQ
            MODE_OCCASIONAL -> CHIP_OCCA
            MODE_RARE -> CHIP_RARE
            else -> return
        }

        cleanupChipSelection(findViewById<ChipGroup>(R.id.basicFrequencyChipGroup))
        // Re-select the correct chip based on the currently loaded global default state
        val chipToSet = findViewById<Chip>(chipIdToSelect)
        if (chipToSet != null && !chipToSet.isChecked) {
            chipToSet.isChecked = true
        }
    }

    // --- Core Logic: Advanced Mode Handling ---
    private fun setAdvancedModeUI(frequencyInDays: Int) {
        binding.advancedMode.isChecked = true
        binding.basicFrequencyLayout.visibility = View.GONE
        binding.advancedFrequencyLayout.visibility = View.VISIBLE
        cleanupChipSelection(findViewById<ChipGroup>(R.id.frequencyUnitGroup))

        // Clear the basic frequency mode when switching to advanced mode
        binding.contact?.let { it.basicFrequencyMode = null }

        when {
            frequencyInDays % 30 == 0 -> setFrequencyUi(
                binding.monthsChip,
                "${frequencyInDays / 30}" // Calculate months remaining
            )
            frequencyInDays % 7 == 0 && frequencyInDays > 1 -> setFrequencyUi(
                binding.weeksChip,
                "${frequencyInDays / 7}"
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
    /** Sets basic chip and also updates the contact model's mode string */
    private fun setBasicFrequencyUi(chip: Chip, modeName: String) {
        // Reset all and select the provided one
        cleanupChipSelection(binding.basicFrequencyChipGroup)
        if (!chip.isChecked) {
            chip.isChecked = true
        }
        // Update the model object with the selected basic mode name
        binding.contact?.let { contact ->
            contact.basicFrequencyMode = modeName // <-- NEW: Set the basic mode string
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
    private fun getCheckedChip(group: ChipGroup): Chip? {
        for (i in 0 until group.childCount) {
            val view = group.getChildAt(i)
            if (view is Chip && view.isChecked) {
                return view
            }
        }
        return null
    }
    private fun cleanupChipSelection(chipGroup: ChipGroup) {
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

        // 2. Get Frequency and validate mode name
        val (frequencyInDays, basicModeName) = getFrequencyAndMode()
        if (frequencyInDays == null && basicModeName == null) {
            showToast("Please set a contact frequency or basic mode.")
            return false
        }

        // 3. Update and save model data
        binding.contact?.let { contact ->
            val newNotes = binding.noteEditText.text.toString()
            contact.notes = newNotes
            contact.basicFrequencyMode = basicModeName // Always set mode name, even if advanced
            contact.contactFrequency = frequencyInDays

            viewModel.saveContact(contact)
        }

        // 4. Crucial Step: Update Global Defaults based on the save operation (if needed)
        // If this were a dedicated 'Global Settings' screen, we would trigger the update here.
        // For now, assume basic mode change saves *local* frequency, not global one.

        return true
    }

    fun showDeleteConfirmationDialog() {
        binding.contact?.let { contact ->
            AlertDialog.Builder(this)
                .setTitle("Delete Contact?")
                .setMessage("Are you sure you want to delete ${contact.contactName}?")
                .setPositiveButton("Delete") { _, _ ->
                    // Execute deletion in the repository and notify success
                    lifecycleScope.launch {
                        val success = App.contactRepository.deleteContact(contact.contactId)
                        if (success) {
                            showToast("${contact.contactName} deleted successfully.")
                            // Important: Must signal ContactsList to refresh its data set
                            finish()
                        } else {
                            showToast("Failed to delete contact.")
                        }
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> return@setNegativeButton }
                .show()
        } ?: run {
            showToast("No contact loaded to delete.")
        }
    }

    /** Combines frequency retrieval and determines the required basic mode string. */
    private fun getFrequencyAndMode(): Pair<Int?, String?> {
        val (frequencyInDays, basicModeName) = when {
            binding.advancedMode.isChecked -> Pair(getAdvancedFrequency(), null)
            binding.basicMode.isChecked -> {
                val chip = getCheckedChip(findViewById<ChipGroup>(R.id.basicFrequencyChipGroup))
                val mode = when (chip?.id) {
                    CHIP_FREQ -> MODE_FREQUENT
                    CHIP_OCCA -> MODE_OCCASIONAL
                    CHIP_RARE -> MODE_RARE
                    else -> null
                }
                // Frequency is derived from the global default stored in the DB (which was loaded initially)
                Pair(getGlobalFrequencyFromChip(chip?.id), mode)
            }
            else -> Pair(null, null)
        }

        return if (frequencyInDays != null && basicModeName == null) {
            Pair(frequencyInDays, null) // Advanced Mode set correctly
        } else if (basicModeName != null) {
            Pair(frequencyInDays, basicModeName) // Basic Mode set correctly
        } else {
            Pair(null, null)
        }
    }

    /** Helper to get the frequency value based on the selected basic chip ID. */
    private fun getGlobalFrequencyFromChip(chipId: Int?): Int? {
        if (chipId == null) return null
        // We must fetch the current global default from the database, NOT use hardcoded values.
        val contactRepository = App.contactRepository
        return runBlocking { // Temporary blocking call for simplified demo logic flow
            return@runBlocking when (chipId) {
                CHIP_FREQ -> contactRepository.getGlobalFrequencyDefaults()[MODE_FREQUENT]
                CHIP_OCCA -> contactRepository.getGlobalFrequencyDefaults()[MODE_OCCASIONAL]
                CHIP_RARE -> contactRepository.getGlobalFrequencyDefaults()[MODE_RARE]
                else -> null
            } // Cast required because getGlobalFrequencyDefaults returns Map<String, Int>
        }
    }

    private fun getAdvancedFrequency(): Int? {
        val frequencyValueText = binding.frequencyEditText.text.toString()
        if (!frequencyValueText.isEmpty()) {
            val frequencyValue = frequencyValueText.toIntOrNull() ?: return null
            return when {
                binding.daysChip.isChecked -> frequencyValue
                binding.weeksChip.isChecked -> frequencyValue * 7
                binding.monthsChip.isChecked -> frequencyValue * 30 // Approximation
                else -> null
            }
        } else {
            return null
        }
    }

    private fun getBasicFrequency(): Int? {
        val chip = getCheckedChip(findViewById<ChipGroup>(R.id.basicFrequencyChipGroup)) ?: return null
        // Fetch the value from the DB using the global keys, not hardcoded values.
        return runBlocking {
            App.contactRepository.getGlobalFrequencyDefaults()[when(chip.id) {
                CHIP_FREQ -> MODE_FREQUENT
                CHIP_OCCA -> MODE_OCCASIONAL
                CHIP_RARE -> MODE_RARE
                else -> ""
            }] ?: 0
        }
    }

    /**
     * Determines if the Save button should be enabled based on current UI state.
     */
    private fun validateSaveButtonState() {
        val dateValid = binding.dateEditText.text.toString().isNotEmpty()
        // Check frequency using the new helper function
        val (frequencyValid, _) = getFrequencyAndMode()
        //binding.saveButton.isEnabled = dateValid && frequencyValid != null
    }
    // --- Utility Functions (Kept mostly the same) ---
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
