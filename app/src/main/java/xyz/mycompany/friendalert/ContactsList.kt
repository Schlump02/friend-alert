package xyz.mycompany.friendalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import xyz.mycompany.friendalert.databinding.ActivityContactsListBinding
import xyz.mycompany.friendalert.fragments.ContactsSetFragment
import xyz.mycompany.friendalert.utils.hasPermission
import xyz.mycompany.friendalert.utils.requestPermission
import xyz.mycompany.friendalert.utils.showDialog

const val PERMISSIONS_REQUEST_READ_CONTACTS = 100

class ContactsList : AppCompatActivity() {
    private lateinit var binding: ActivityContactsListBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            initializeApp()
        } else {
            requestPermission(Manifest.permission.READ_CONTACTS, PERMISSIONS_REQUEST_READ_CONTACTS)
        }
    }

    private fun initializeApp() {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, ContactsSetFragment())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        //switchFragments()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Navigate to your Settings or Preferences screen here
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun checkAndRequestPermissions() {
        if (!this.hasPermission(Manifest.permission.READ_CONTACTS)) {
            requestPermission(Manifest.permission.READ_CONTACTS, PERMISSIONS_REQUEST_READ_CONTACTS)
        } else {
            initializeApp()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS && grantResults.isNotEmpty()) {
            handlePermissionsResult(grantResults[0])
        }
    }

    private fun handlePermissionsResult(result: Int) {
        when {
            result == PackageManager.PERMISSION_GRANTED -> initializeApp()
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> showRationaleDialog()
            else -> showAppSettingsDialog()
        }
    }

    private fun showRationaleDialog() {
        showDialog(
            message = R.string.request_contacts_permission,
            positiveButtonText = R.string.ask_again,
            onPositiveAction = { checkAndRequestPermissions() }
        )
    }

    private fun showAppSettingsDialog() {
        showDialog(
            message = R.string.denied_contacts_permission,
            positiveButtonText = R.string.open_android_settings,
            onPositiveAction = { openAppSettings() },
            negativeButtonText = R.string.exit,
            onNegativeAction = { finish() }
        )
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

}