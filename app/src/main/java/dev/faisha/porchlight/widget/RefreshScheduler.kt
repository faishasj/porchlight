package dev.faisha.porchlight.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresher.refreshAll(applicationContext)
        return Result.success()
    }
}

/** Clears an expired "Tap again to confirm" and redraws. Local only, no network. */
class DisarmAllOffWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        WidgetRefresher.writeAll(applicationContext) { WidgetRefresher.disarmAllOffIfExpired(it, now) }
        return Result.success()
    }
}

object RefreshScheduler {
    private const val PERIODIC = "porchlight_periodic_refresh"
    private const val ONE_SHOT = "porchlight_refresh_now"
    private const val DISARM_ALL_OFF = "porchlight_disarm_all_off"

    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 15 minutes is WorkManager's minimum period. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.KEEP, request)
    }

    /** Runs just after the "All off" confirm window closes. A newer arm replaces the pending one. */
    fun disarmAllOffLater(context: Context) {
        val request = OneTimeWorkRequestBuilder<DisarmAllOffWorker>()
            .setInitialDelay(ALL_OFF_CONFIRM_WINDOW_MS + 500, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DISARM_ALL_OFF, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(ONE_SHOT)
            cancelUniqueWork(DISARM_ALL_OFF)
        }
    }
}
