package com.familyday.board.sync

import android.content.Context
import androidx.work.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters), KoinComponent {
    private val service: SyncService by inject()
    override suspend fun doWork(): Result = try { service.sync(); Result.success() } catch (_: Exception) { if (runAttemptCount < 8) Result.retry() else Result.failure() }
}
