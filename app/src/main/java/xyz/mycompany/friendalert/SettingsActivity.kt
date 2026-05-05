package xyz.mycompany.friendalert

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import xyz.mycompany.friendalert.repository.ContactRepository
import xyz.mycompany.friendalert.viewmodels.SettingsViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.mycompany.friendalert.App.Companion.contactRepository
import xyz.mycompany.friendalert.utils.GlobalConfigKeys
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>

    /**
     * Safely retrieves the EditText view from a TextInputLayout by its resource ID.
     * This version uses findViewById on a generic View and casts it defensively to prevent ClassCastExceptions.
     */
    private fun getEditTextById(layoutId: Int): EditText? {
        // 1. Find the container first using generics (this is the most stable part of the code)
        val view = findViewById<View>(layoutId)

        // 2. Check if this generic View can actually be treated as a TextInputLayout
        if (view is TextInputLayout) {
            // 3. If it is, access its internal .editText property and cast that result safely.
            return view.editText as? EditText
        }
        return null // Failed to find the correct container type or widget structure.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // --- Setup ViewModel and State Observation ---
        val repository = App.contactRepository
        settingsViewModel = ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T: androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository) as T
            }
        }).get(SettingsViewModel::class.java)

        // Observe global settings flow to initialize UI fields
        lifecycleScope.launch {
            settingsViewModel.globalSettings.collectLatest { settings ->
                populateUiFromGlobalState(settings)
            }
        }

        // --- 1. Setup Activity Result Launcher (Exporting) ---
        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri: Uri? = result.data?.data
                try {
                    uri?.let { contentResolver.openOutputStream(it) }?.use { outputStream ->
                        outputStream.write(this@SettingsActivity.lastExportedBytes)
                    }
                    Toast.makeText(this@SettingsActivity, "Contacts successfully exported!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Failed to save contacts: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            } else if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this@SettingsActivity, "Export canceled.", Toast.LENGTH_SHORT).show()
            }
        }

        // --- 2. Setup UI Listeners ---
        findViewById<com.google.android.material.button.MaterialButton>(R.id.export_contacts_button).setOnClickListener {
            exportContacts()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.save_settings_button).setOnClickListener {
            saveGlobalSettings()
        }

        initializeGlobalDefaultsFromDatabase()
    }

    /**
     * Helper variable to hold the bytes calculated in exportContacts(),
     * making them visible and scoped correctly for the launcher's lambda callback.
     */
    private var lastExportedBytes: ByteArray = byteArrayOf()

    // --- State Syncing Logic ---
    /** Populates the UI fields (EditText) based on the current state read from Room/ViewModel. */
    private fun populateUiFromGlobalState(settings: SettingsViewModel.GlobalFrequencySettings) {
        getEditTextById(R.id.freq_input_frequent_layout)?.setText(settings.frequentDays.toString())
        getEditTextById(R.id.freq_input_occasional_layout)?.setText(settings.occasionalDays.toString())
        getEditTextById(R.id.freq_input_rare_layout)?.setText(settings.rareDays.toString())
    }

    /**
     * Reads the current values from the UI fields, validates them, and saves them to Room.
     */
    private fun saveGlobalSettings() {
        // Use safe retrieval mechanism when reading user input for saving
        val frequentInput = getEditTextById(R.id.freq_input_frequent_layout)?.text?.toString()?.toIntOrNull() ?: GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS
        val occasionalInput = getEditTextById(R.id.freq_input_occasional_layout)?.text?.toString()?.toIntOrNull() ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS
        val rareInput = getEditTextById(R.id.freq_input_rare_layout)?.text?.toString()?.toIntOrNull() ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS

        settingsViewModel.saveSettings(frequentInput, occasionalInput, rareInput)
    }

    private fun exportContacts() {
        val contacts = try {
            runBlocking {
                contactRepository.getContactsForExport().first()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error fetching contacts for export: " + e.message, Toast.LENGTH_SHORT).show()
            return
        }
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts found to export.", Toast.LENGTH_LONG).show()
            return
        }
        val csvContentString = generateCsv(contacts)
        this.lastExportedBytes = csvContentString.toByteArray()

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            putExtra(Intent.EXTRA_TITLE, "FriendAlert Contacts Export ${dateFormat.format(System.currentTimeMillis())}.csv")
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv"))
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/csv"
        createDocumentLauncher.launch(intent)
    }

    private fun generateCsv(contacts: List<xyz.mycompany.friendalert.models.ContactEntity>): String {
        val header = "contactId,lookupKey,contactName,phoneNumber,lastContactedTime,contactFrequency,photoUri,notes\n"
        val lines = contacts.joinToString("\n") { contact ->
            fun escape(s: String?) = "\"${s?.replace("\"", "\"\"") ?: ""}\""
            "${contact.contactId},${escape(contact.lookupKey)},${escape(contact.contactName)},${escape(contact.phoneNumber)},${contact.lastContactedTime ?: ""},${contact.contactFrequency ?: ""},${escape(contact.photoUri)},${escape(contact.notes)}"
        }
        return "$header$lines"
    }


    /** Initializes the global default settings by reading from Room and updating internal state. */
    private fun initializeGlobalDefaultsFromDatabase() {
        lifecycleScope.launch {
            try {
                // Fetch current defaults directly from the repository (Room DB)
                val currentSettings = contactRepository.getGlobalFrequencyDefaults()

                val defaultSettings = SettingsViewModel.GlobalFrequencySettings(
                    frequentDays = currentSettings["FREQUENT"]?.toInt() ?: GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS,
                    occasionalDays = currentSettings["OCCASIONAL"]?.toInt() ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS,
                    rareDays = currentSettings["RARE"]?.toInt() ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS
                )
                // Initialize the UI state by calling the save function (which propagates the default values to the DB/UI)
                settingsViewModel.saveSettings(defaultSettings.frequentDays, defaultSettings.occasionalDays, defaultSettings.rareDays)
            } catch (e: Exception) {
                showToast("Error initializing global settings: ${e.message}")
                Log.e("SettingsActivity", "DB initialization error", e)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
