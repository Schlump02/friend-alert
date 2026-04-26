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
        setupToggleGroup()
        binding.dateEditText.setOnClickListener { showDatePickerDialog() }
        binding.saveButton.setOnClickListener { saveContactInformation() }
    }

    private fun setupToggleGroup() {
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
        contact?.let {
            binding.contact = it
            binding.lifecycleOwner = this
            setContactedDate(it.lastContactedTime)
            setContactFrequency(it.contactFrequency)
            // Initialize notes EditText with the loaded contact's note data
            if (binding.noteEditText != null && contact.notes != null) {
                binding.noteEditText.setText(contact.notes)
            } else if (binding.noteEditText != null) {
                // If no notes, clear it to allow user input
                binding.noteEditText.setText("")
            }
        } ?: run {
            showToast("Contact not found!")
            finish()
        }
    }

    private fun setContactedDate(lastContactedTime: Long?) {
        lastContactedTime?.let {
            val date = dateFormat.format(Date(it))
            binding.dateEditText.setText("zuletzt $date")
        }
    }

    private fun setContactFrequency(frequencyInDays: Int?) {
        if (frequencyInDays == null) {
            setDefaultUI()
            return
        }
        val frequencies = listOf(
            sharedPreferences.getInt(FREQUENT_KEY, BASIC_FREQUENCY_DAYS),
            sharedPreferences.getInt(OCCASIONAL_KEY, OCCASIONAL_FREQUENCY_DAYS),
            sharedPreferences.getInt(RARE_KEY, RARE_FREQUENCY_DAYS)
        )

        when (frequencyInDays) {
            in frequencies -> setBasicModeUI(frequencyInDays)
            else -> setAdvancedModeUI(frequencyInDays)
        }
    }

    private fun setBasicModeUI(frequencyInDays: Int) {
        val basicFrequencyDays = sharedPreferences.getInt(FREQUENT_KEY, BASIC_FREQUENCY_DAYS)
        val occasionalFrequencyDays =
            sharedPreferences.getInt(OCCASIONAL_KEY, OCCASIONAL_FREQUENCY_DAYS)
        val rareFrequencyDays = sharedPreferences.getInt(RARE_KEY, RARE_FREQUENCY_DAYS)

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

        when {
            frequencyInDays % DAYS_IN_MONTH == 0 -> setFrequencyUi(
                binding.monthsChip,
                (frequencyInDays / DAYS_IN_MONTH).toString()
            )

            frequencyInDays % DAYS_IN_WEEK == 0 -> setFrequencyUi(
                binding.weeksChip,
                (frequencyInDays / DAYS_IN_WEEK).toString()
            )

            frequencyInDays > 0 -> setFrequencyUi(binding.daysChip, frequencyInDays.toString())
        }
    }

    private fun setFrequencyUi(chip: Chip, text: String) {
        chip.isChecked = true
        binding.frequencyEditText.setText(text)
    }

    private fun setBasicFrequencyUi(chip: Chip) {
        setDefaultUI()
        chip.isChecked = true
    }

    private fun setDefaultUI() {
        binding.basicMode.isChecked = true
        binding.basicFrequencyLayout.visibility = View.VISIBLE
        binding.advancedFrequencyLayout.visibility = View.GONE
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
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        //datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }


    private fun saveContactInformation() {
        binding.contact?.let { contact ->
            // 1. Get notes from the editable EditText field
            val newNotes = binding.noteEditText?.text.toString() ?: ""
            // Update the model object
            contact.notes = newNotes

            val frequencyInDays = getFrequencyInDays()
            contact.contactFrequency = frequencyInDays
            viewModel.saveContact(contact, frequencyInDays)
            showToast("Contact information saved!")
            finish()
        }
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
        val frequencyValue = binding.frequencyEditText.text.toString().toIntOrNull()
        return when {
            binding.daysChip.isChecked -> frequencyValue
            binding.weeksChip.isChecked -> frequencyValue?.times(DAYS_IN_WEEK)
            binding.monthsChip.isChecked -> frequencyValue?.times(DAYS_IN_MONTH)
            else -> null
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


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
