package xyz.mycompany.friendalert

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
            // The export logic will now calculate and store the bytes before launching.
            exportContacts()
        }

        // Populate with SettingsFragment
        supportFragmentManager.commit {
            replace(R.id.fragment_container, SettingsFragment())
        }

        initializeDummyGlobalDefaults()
    }

    /**
     * Helper variable to hold the bytes calculated in exportContacts(),
     * making them visible and scoped correctly for the launcher's lambda callback.
     */
    private var lastExportedBytes: ByteArray = byteArrayOf()


    private fun exportContacts() {
        // 1. Get all contacts from the database
        val contacts = try {
            runBlocking {
                // This suspends until the Flow emits the list of all contacts
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

        // 2. Calculate the CSV content string and bytes immediately
        val csvContentString = generateCsv(contacts)
        // Store the bytes in the class field so they are available when createDocumentLauncher executes later.
        this.lastExportedBytes = csvContentString.toByteArray()


        // 3. Set up the file save Intent
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            putExtra(Intent.EXTRA_TITLE, "FriendAlert Contacts Export ${dateFormat.format(System.currentTimeMillis())}.csv")
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv"))
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/csv"

        // 4. Launch the document picker result launcher (Uses the pre-registered instance)
        createDocumentLauncher.launch(intent)
    }


    private fun generateCsv(contacts: List<xyz.mycompany.friendalert.models.ContactEntity>): String {
        val header = "contactId,lookupKey,contactName,phoneNumber,lastContactedTime,contactFrequency,photoUri,notes\n"
        val lines = contacts.joinToString("\n") { contact ->
            // Basic CSV sanitization: wrap fields containing commas or newlines in quotes
            fun escape(s: String?) = "\"${s?.replace("\"", "\"\"") ?: ""}\""

            "${contact.contactId},${escape(contact.lookupKey)},${escape(contact.contactName)},${escape(contact.phoneNumber)},${contact.lastContactedTime ?: ""},${contact.contactFrequency ?: ""},${escape(contact.photoUri)},${escape(contact.notes)}"
        }
        return "$header$lines"
    }

    private fun saveGlobalDefaults(frequencyMap: Map<String, Int>) {
        val frequent = frequencyMap["FREQUENT"] ?: GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS
        val occasional = frequencyMap["OCCASIONAL"] ?: GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS
        val rare = frequencyMap["RARE"] ?: GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS

        // The ViewModel needs to be responsible for coordinating this, but we'll call the repo directly here for simplicity.
        // In a real app, you would pass these values via a SettingsViewModel.

        lifecycleScope.launch {
            try {
                contactRepository.updateGlobalFrequency("FREQUENT", frequent)
                showToast("✅ Updated Frequent frequency to $frequent days.")
                contactRepository.updateGlobalFrequency("OCCASIONAL", occasional)
                contactRepository.updateGlobalFrequency("RARE", rare)
            } catch (e: Exception) {
                showToast("Error saving global defaults: ${e.message}")
                Log.e("SettingsActivity", "Global save error", e)
            }
        }
    }

    // Example usage in onCreate or a dedicated setup method:
    fun initializeDummyGlobalDefaults() {
        val dummyFreq = mutableMapOf("FREQUENT" to GlobalConfigKeys.DEFAULT_BASIC_FREQUENCY_DAYS,
            "OCCASIONAL" to GlobalConfigKeys.DEFAULT_OCCASIONAL_FREQUENCY_DAYS,
            "RARE" to GlobalConfigKeys.DEFAULT_RARE_FREQUENCY_DAYS
        )
        saveGlobalDefaults(dummyFreq)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
