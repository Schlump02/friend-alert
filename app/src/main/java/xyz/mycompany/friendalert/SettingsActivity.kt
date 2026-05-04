package xyz.mycompany.friendalert

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import xyz.mycompany.friendalert.repository.ContactRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.mycompany.friendalert.utils.GlobalConfigKeys
import java.text.SimpleDateFormat
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private lateinit var contactRepository: ContactRepository

    // This launcher is initialized in onCreate to fix the IllegalStateException (lifecycle crash).
    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)  // Set the new layout

        // --- 1. Initialization and Setup ---
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        contactRepository = App.contactRepository

        // --- 2. Initialize Activity Result Launcher (MUST run in onCreate for correct lifecycle registration) ---
        createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri: Uri? = result.data?.data
                // The file bytes are implicitly available in this scope block,
                // as we pass them to the export method logic before launch.
                try {
                    uri?.let { contentResolver.openOutputStream(it) }?.use { outputStream ->
                        outputStream.write(this@SettingsActivity.lastExportedBytes) // Use the captured bytes here
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

        // --- 3. Setup UI Listeners ---
        findViewById<com.google.android.material.button.MaterialButton>(R.id.export_contacts_button).setOnClickListener {
            exportContacts()
        }

        // NEW: Set up listener for saving global settings using the dedicated button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.save_settings_button).setOnClickListener {
            saveGlobalSettings()
        }

        // Populate with SettingsFragment
        supportFragmentManager.commit {
            replace(R.id.fragment_container, SettingsFragment())
        }

        // Initialize default global settings on startup by reading from the DB and writing them back (if necessary)
        initializeGlobalDefaultsFromDatabase()
    }

    /**
     * Helper variable to hold the bytes calculated in exportContacts(),
     * making them visible and scoped correctly for the launcher's lambda callback.
     */
    private var lastExportedBytes: ByteArray = byteArrayOf()

    // --- Data Persistence Logic (Database First) ---

    /**
     * Reads global defaults from the database and uses those values to initialize or update settings.
     * This replaces reliance on SharedPreferences for source of truth.
     */
    private fun initializeGlobalDefaultsFromDatabase() {
        lifecycleScope.launch {
            try {
                // Fetch existing data from Room
                val currentDefaults = contactRepository.getGlobalFrequencyDefaults()

                // Use these defaults to set the initial state and propagate them globally if needed.
                saveGlobalSettings(currentDefaults)
                showToast("✅ Global settings initialized successfully using database values.")
            } catch (e: Exception) {
                showToast("Error initializing global settings: ${e.message}")
                Log.e("SettingsActivity", "DB initialization error", e)
            }
        }
    }

    /**
     * Reads the current frequency values from the preference widgets (simulated/placeholder retrieval).
     * In a real app, this logic would involve reading temporary variables or passing data
     * from a dedicated SettingsViewModel. For now, we rely on the fact that the PreferenceWidget
     * has already saved them to SharedPreferences keys matching the DAO constants.
     */
    fun saveGlobalSettings() {
        // NOTE: This function still relies on reading preferences because the custom widget
        // (FrequencyPreference) persists to SharedPreferences. If we want pure DB access,
        // we MUST replace FrequencyPreference entirely with a custom View that reads/writes directly to the DAO.
        // However, given the constraints, we proceed by assuming the preference values are saved correctly under the global keys.

        // We simulate fetching the current state from the UI's persistent storage (SharedPreferences)
        val frequentDays = getPreferenceInt(GlobalConfigKeys.GLOBAL_FREQ_FREQUENT) ?: GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS
        val occasionalDays = getPreferenceInt(GlobalConfigKeys.GLOBAL_FREQ_OCCASIONAL) ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS
        val rareDays = getPreferenceInt(GlobalConfigKeys.GLOBAL_FREQ_RARE) ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS

        // Store the values retrieved from preferences into a map for consistent saving
        val currentSettingsMap = mutableMapOf(
            "FREQUENT" to frequentDays,
            "OCCASIONAL" to occasionalDays,
            "RARE" to rareDays
        )

        saveGlobalSettings(currentSettingsMap)
    }

    /** Overloaded function that saves the settings based on a map of values. */
    private fun saveGlobalSettings(frequencyMap: Map<String, Int>) {
        lifecycleScope.launch {
            try {
                // 1. Update DB for Frequent
                contactRepository.updateGlobalFrequency("FREQUENT", frequencyMap["FREQUENT"]!!)
                showToast("✅ Updated Frequent frequency to ${frequencyMap["FREQUENT"]} days.")

                // 2. Update DB for Occasional
                contactRepository.updateGlobalFrequency("OCCASIONAL", frequencyMap["OCCASIONAL"]!!)
                showToast("✅ Updated Occasional frequency to ${frequencyMap["OCCASIONAL"]} days.")

                // 3. Update DB for Rare
                contactRepository.updateGlobalFrequency("RARE", frequencyMap["RARE"]!!)
                showToast("✅ Updated Rare frequency to ${frequencyMap["RARE"]} days.")

            } catch (e: Exception) {
                showToast("Error saving global defaults: ${e.message}")
                Log.e("SettingsActivity", "Global save error", e)
            }
        }
    }

    /** Helper function to safely read the integer preference value by key using SharedPreferences. */
    private fun getPreferenceInt(key: String): Int? {
        // This remains necessary due to the PreferenceWidget structure, but we must remember
        // that this only reads the UI's saved state, not necessarily the DB's source of truth.
        return try {
            getSharedPreferences("default_settings", MODE_PRIVATE).getInt(key, -1)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error reading preference $key", e)
            null
        }
    }

    // --- Export Functions (Unchanged) ---

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


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
