package xyz.mycompany.friendalert.DI

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import xyz.mycompany.friendalert.data.AppDatabase
import xyz.mycompany.friendalert.data.DeviceContacts
import xyz.mycompany.friendalert.repository.ContactRepository

val appModule = module {
    single { AppDatabase.getInstance(get()) }
    single { AppDatabase.getInstance(get()).contactDao() }
    single { AppDatabase.getInstance(get()).settingsDao() }
    single { androidContext().contentResolver }
    single { DeviceContacts(get(), get()) }
    single { ContactRepository(get(), get(), get()) }
}