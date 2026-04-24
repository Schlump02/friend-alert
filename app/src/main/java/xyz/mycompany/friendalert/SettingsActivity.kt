package xyz.mycompany.friendalert

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.commit

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)  // Set the new layout

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)  // Add a back button to the Toolbar
        toolbar.setNavigationOnClickListener { finish() }  // Close this activity when the back button is pressed

        // Populate with SettingsFragment
        supportFragmentManager.commit {
            replace(R.id.fragment_container, SettingsFragment())
        }
    }
}
