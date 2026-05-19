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
import xyz.mycompany.friendalert.models.ContactEntity
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : AppCompatActivity() {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private lateinit var settingsViewModel: SettingsViewModel

    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>

    private lateinit var contentFilePickerLauncher: ActivityResultLauncher<Intent>

    /**
     * Helper variable to hold the bytes calculated in exportContacts(),
     * making them visible and scoped correctly for the launcher's lambda callback.
     */
    private var lastExportedBytes: ByteArray = byteArrayOf()

    private fun getEditTextById(layoutId: Int): EditText? {
        val view = findViewById<View>(layoutId)

        if (view is TextInputLayout) {
            return view.editText as? EditText
        }
        return null // Failed to find the correct container type or widget structure.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.settings_toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

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

        // For importing
        contentFilePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri: Uri? = result.data?.getData()
                uri?.let { fileUri ->
                    try {
                        // Pass the URI to the import function
                        handleImport(fileUri)
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "Error processing file: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("SettingsActivity", "Failed to handle imported file.", e)
                    }
                } ?: run {
                    // Explicitly handle case where URI is null despite result being OK
                    Toast.makeText(this@SettingsActivity, "Error: No file URI obtained.", Toast.LENGTH_SHORT).show()
                }
            } else if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this@SettingsActivity, "Import canceled.", Toast.LENGTH_SHORT).show()
            }
        }

        // For exporting
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

        // --- Setup UI Listeners ---
        findViewById<com.google.android.material.button.MaterialButton>(R.id.export_contacts_button).setOnClickListener {
            exportContacts()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.import_contacts_button).setOnClickListener {
            // Use the Content Resolver to pick a file, restricting types to CSV
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                //putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv"))
            }
            contentFilePickerLauncher.launch(intent)
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.save_settings_button).setOnClickListener {
            saveGlobalSettings()
        }

        initializeGlobalDefaultsFromDatabase()
    }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

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
        val frequentInput = getEditTextById(R.id.freq_input_frequent_layout)?.text?.toString()?.toIntOrNull() ?: GlobalConfigKeys.DEFAULT_STANDARD_FREQUENCY_DAYS
        val occasionalInput = getEditTextById(R.id.freq_input_occasional_layout)?.text?.toString()?.toIntOrNull() ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS
        val rareInput = getEditTextById(R.id.freq_input_rare_layout)?.text?.toString()?.toIntOrNull() ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS

        lifecycleScope.launch {
            try {
                settingsViewModel.saveSettings(frequentInput, occasionalInput, rareInput)
                // Success Feedback
                showToast("Global settings saved successfully!")
            } catch (e: IllegalStateException) {
                // Handle the specific exception thrown by SettingsViewModel on failure
                showToast("Failed to save settings: ${e.message}")
                Log.e("SettingsActivity", "Failed to save global settings.", e)
            } catch (e: Exception) {
                // Catch any other unexpected errors during saving
                showToast("An unknown error occurred while saving.")
                Log.e("SettingsActivity", "Unknown error during save operation", e)
            }
        }
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
        val header = "lookupKey,contactName,phoneNumber,lastContactedTime,contactFrequency,photoUri,notes,standardFrequencyMode\n"
        val lines = contacts.joinToString("\n") { contact ->
            fun escape(s: String?) = "\"${s?.replace("\"", "\"\"") ?: ""}\""
            "${escape(contact.lookupKey)},${escape(contact.contactName)},${escape(contact.phoneNumber)},${contact.lastContactedTime ?: ""},${contact.contactFrequency ?: ""},${escape(contact.photoUri)},${escape(contact.notes)},${escape(contact.standardFrequencyMode)}"
        }
        return "$header$lines"
    }
    /** Handles reading and parsing contacts from a given URI. */
    private fun handleImport(uri: Uri) {
        var importedContactCount = 0L
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                // Skip header row (assuming the CSV has a header)
                reader.readLine()

                reader.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach // Skip empty lines
                    try {
                        // Parsing logic based on the structure defined in generateCsv
                        val fields = parseCsvLine(line)
                        fields?.size?.let {
                            if (it >= 8) {
                                val photoUri = fields[5]?.trim()?.takeIf { it.isNotBlank() }
                                val newContact = ContactEntity(
                                    contactId = importedContactCount, // duplicate ID will be handled in saveContact()
                                    lookupKey = fields[0]?.trim(),
                                    contactName = fields[1]?.trim(),
                                    phoneNumber = fields[2]?.trim(),
                                    lastContactedTime = fields[3]?.trim()?.toLongOrNull(),
                                    contactFrequency = fields[4]?.trim()?.toIntOrNull(),
                                    photoUri = photoUri,
                                    notes = fields[6]?.trim(),
                                    standardFrequencyMode = fields[7]?.trim()
                                )
                                // Use the repository to save/update the contact, handling potential conflicts
                                lifecycleScope.launch {
                                    App.contactRepository.saveContact(newContact ?: return@launch)
                                }
                                importedContactCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Import", "Skipping bad line: $line. Error: ${e.message}")
                    }
                }
            }
            showToast("Successfully imported $importedContactCount contacts.")
            // Refresh the entire UI/data set after import
            //findViewById<com.google.android.material.button.MaterialButton>(R.id.save_settings_button)?.performClick() // Trigger state save for refresh
            finish() // Close settings and let ContactsList re-fetch data
        } catch (e: Exception) {
            showToast("Import failed: ${e.message}")
            Log.e("SettingsActivity", "Import failure", e)
        }
    }

    /** Standard CSV parser that handles quoted values and commas within quotes. */
    private fun parseCsvLine(line: String): List<String>? {
        val fields = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when (char) {
                '"' -> {
                    // Check for escaped quote ("")
                    if (currentField.isNotEmpty() && currentField.lastOrNull()?.toString() == "\"") {
                        currentField.append("\"")
                        continue
                    }
                    inQuotes = !inQuotes
                }
                ',' -> {
                    if (inQuotes) {
                        currentField.append(char)
                    } else {
                        fields.add(currentField.toString())
                        currentField = StringBuilder()
                    }
                }
                else -> {
                    currentField.append(char)
                }
            }
        }
        // Add the last field
        fields.add(currentField.toString())
        return fields
    }


    /** Initializes the global default settings by reading from Room and updating internal state. */
    private fun initializeGlobalDefaultsFromDatabase() {
        lifecycleScope.launch {
            try {
                // Fetch current defaults directly from the repository (Room DB)
                val currentSettings = contactRepository.getGlobalFrequencyDefaults()

                val defaultSettings = SettingsViewModel.GlobalFrequencySettings(
                    frequentDays = currentSettings["FREQUENT"]?.toInt() ?: GlobalConfigKeys.DEFAULT_STANDARD_FREQUENCY_DAYS,
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

private fun ActivityResultLauncher<Intent>.launch(input: String) {}
