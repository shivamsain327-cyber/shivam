package com.example.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.TaskRepository
import com.example.model.WebServiceConfig
import com.example.network.SyncResult
import java.util.concurrent.TimeUnit

/** Background job: sync checklists, then send any due automatic reminders. */
class AutoSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = TaskRepository.get(applicationContext)
        repository.awaitLoaded()
        if (!repository.webServiceConfig.value.autoSyncEnabled) return Result.success()

        val sync = SyncEngine.syncFromConfiguredService(repository)
        // Alerts still go out if the server is briefly down: overdue status is
        // also worked out from the clock on the tasks we already have.
        SyncEngine.sendDueWebhookAlerts(repository)

        return if (sync is SyncResult.Error && runAttemptCount < 2) Result.retry() else Result.success()
    }
}

object AutoSyncScheduler {
    private const val WORK_NAME = "workalert-auto-sync"

    /** WorkManager can't run periodic work more often than every 15 minutes. */
    const val MIN_INTERVAL_MINUTES = 15

    /** Starts, reschedules or cancels the background sync to match the saved settings. */
    fun apply(context: Context, config: WebServiceConfig) {
        val workManager = WorkManager.getInstance(context)
        if (!config.autoSyncEnabled || config.url.isBlank()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val minutes = config.autoSyncMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
