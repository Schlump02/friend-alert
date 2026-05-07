package xyz.mycompany.friendalert

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import xyz.mycompany.friendalert.DI.appModule
import xyz.mycompany.friendalert.data.AppDatabase
import xyz.mycompany.friendalert.data.Setting
import xyz.mycompany.friendalert.repository.ContactRepository
import java.util.concurrent.TimeUnit

class App : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
        lateinit var contactRepository: ContactRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }

        database = get()
        contactRepository = get()

        initializeSettings()
        /*GlobalScope.launch {
            contactRepository.migrateContacts()
        }*/
        scheduleWork()
    }

    private fun scheduleWork() {
        val repeatIntervalDays = 1L
        val flexIntervalHours = 2L
        val workManager = WorkManager.getInstance(applicationContext)

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ContactNotificationWorker>(
            repeatIntervalDays, TimeUnit.DAYS,
            flexIntervalHours, TimeUnit.HOURS
        )
            .addTag(ContactNotificationWorker.WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "UniqueContactNotificationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    private fun initializeSettings() {
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            val lastSyncTimestamp = database.settingsDao().getLong("last_sync_timestamp")
            Log.d("Debug", lastSyncTimestamp.toString())

            if (lastSyncTimestamp == null) {
                database.settingsDao().insertOrUpdate(Setting("last_sync_timestamp", 0L))
            }
        }
    }
}
