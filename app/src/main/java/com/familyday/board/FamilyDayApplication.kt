package com.familyday.board

import android.app.Application
import androidx.room.Room
import androidx.work.*
import com.familyday.board.data.*
import com.familyday.board.security.*
import com.familyday.board.reminders.ReminderWorker
import com.familyday.board.ui.BoardViewModel
import com.familyday.board.sync.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class FamilyDayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FamilyDayApplication)
            modules(module {
                single { Room.databaseBuilder(get(), BoardDatabase::class.java, "familyday.db").build() }
                single { SecureVault(get()) }; single { ParentGate(get()) }
                single { Preferences(get()) }; single { BoardRepository(get(), get()) }
                single { BackupService(get(), get()) }
                single { SyncService(get(), get(), get(), get()) }
                viewModelOf(::BoardViewModel)
            })
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("local-reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("household-sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}
