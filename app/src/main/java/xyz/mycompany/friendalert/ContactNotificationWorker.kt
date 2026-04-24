package xyz.mycompany.friendalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mycompany.friendalert.repository.ContactRepository

class ContactNotificationWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: ContactRepository = App.contactRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val overdueContacts = repository.getOverdueContacts()
            overdueContacts.forEach {
                showNotification(
                    applicationContext,
                    "Contact Reminder",
                    "It's time to contact ${it.contactName}!"
                )
            }

            if (overdueContacts.isNotEmpty()) {
                showNotification(
                    applicationContext,
                    "Contact Reminder",
                    "You have ${overdueContacts.size} overdue contacts"
                )
            }

            Result.success()
        } catch (exception: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_TAG = "contact_notification_work"
        const val CHANNEL_ID = "contact_reminder_channel"
        const val CHANNEL_NAME = "Contact Reminder Channel"
    }
}

private fun showNotification(context: Context, title: String, message: String) {
    createNotificationChannel(context)
    val notification = buildNotification(context, title, message)

    val notificationManager = ContextCompat.getSystemService(
        context,
        NotificationManager::class.java
    ) as NotificationManager

    notificationManager.notify(1, notification)
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            ContactNotificationWorker.CHANNEL_ID,
            ContactNotificationWorker.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = ContextCompat.getSystemService(
            context,
            NotificationManager::class.java
        ) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

private fun buildNotification(
    context: Context,
    title: String,
    message: String
): Notification {
    val resultIntent = Intent(context, ContactsList::class.java)
    val resultPendingIntent: PendingIntent? = PendingIntent.getActivity(
        context,
        0,
        resultIntent,
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(context, ContactNotificationWorker.CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(message)
        .setSmallIcon(R.drawable.baseline_phone_24)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(resultPendingIntent)
        .setAutoCancel(true)
        .build()
}