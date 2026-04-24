package xyz.mycompany.friendalert.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

fun Context.hasPermission(permission: String): Boolean {
    return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun AppCompatActivity.requestPermission(permission: String, requestCode: Int) {
    ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
}

fun Context.showDialog(
    message: Int,
    positiveButtonText: Int,
    onPositiveAction: () -> Unit,
    negativeButtonText: Int? = null,
    onNegativeAction: (() -> Unit)? = null
) {
    AlertDialog.Builder(this)
        .setMessage(message)
        .setPositiveButton(positiveButtonText) { _, _ -> onPositiveAction() }
        .apply {
            negativeButtonText?.let {
                setNegativeButton(it) { _, _ -> onNegativeAction?.invoke() }
            }
        }
        .show()
}

