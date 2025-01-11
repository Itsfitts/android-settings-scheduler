package com.turtlepaw.smartbattery

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.turtlepaw.smartbattery.shizuku.ShizukuUtils
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ModeScheduleManager(
    private val context: Context,
    private val repository: ModesRepository
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleMode(mode: ModeEntity, forceNextDay: Boolean = false) {
        mode?.let { schedule ->
            if (!mode.enabled) {
                cancelSchedule(mode.id)
                return
            }

            val scheduledTime = schedule.scheduledTime ?: return
            val scheduleDays = schedule.scheduleDays ?: return

            // Create work request for start time
            val startWork = createWorkRequest(mode.id, scheduledTime, scheduleDays, forceNextDay)

            // Chain the work requests
            workManager
                .enqueueUniqueWork(
                    "mode_${mode.id}",
                    ExistingWorkPolicy.REPLACE,
                    startWork
                )
        }
    }

    private fun createWorkRequest(
        modeId: String,
        timeMinutes: LocalTime,
        scheduleDays: List<Boolean>,
        forceNextDay: Boolean = false
    ): OneTimeWorkRequest {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val targetTimeToday = now.with(timeMinutes)

        // Ensure scheduleDays contains exactly 7 days
        require(scheduleDays.size == 7) { "scheduleDays must have exactly 7 elements." }

        // Find the next valid scheduled day
        val currentDayIndex = now.dayOfWeek.value % 7 // Adjust to make Sunday = 0
        val daysToNext = (0 until 7).firstOrNull { offset ->
            val checkDayIndex = (currentDayIndex + offset) % 7
            scheduleDays[checkDayIndex] && (offset > 0 || targetTimeToday.isAfter(now) || forceNextDay)
        } ?: 0 // Fallback: no valid days (shouldn't occur if scheduleDays is valid)

        val targetDate = now.plusDays(daysToNext.toLong())
        val targetTime = targetDate.with(timeMinutes)

        // Calculate delay
        val delayMillis = targetTime.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        val delay = delayMillis.coerceAtLeast(0) // Ensure delay is non-negative

        val data = workDataOf(
            "mode_id" to modeId
        )

        return OneTimeWorkRequestBuilder<ModeWorker>()
            .setInputData(data)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                5,
                TimeUnit.MINUTES
            )
            .addTag("mode_$modeId")
            .build()
    }

    fun cancelSchedule(modeId: String) {
        workManager.cancelUniqueWork("mode_$modeId")
    }
}

class ModeWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            if (!ShizukuUtils.isPermissionGranted(context)) {
                Log.e("ModeWorker", "Permission not granted. Failing the worker.")
                return Result.failure()
            }

            val modeId = inputData.getString("mode_id")

            if (modeId == null) {
                Log.e("ModeWorker", "Mode ID not found. Failing the worker.")
                return Result.failure()
            }

            Log.d("ModeWorker", "Running mode: $modeId")

            val modes = ModesRepository().getAllModes(context)
            val mode = modes.find { it.id == modeId }

            if (mode == null) {
                Log.e("ModeWorker", "Mode not found. Failing the worker.");
                return Result.failure();
            }

            // Apply settings
            CombinedSettings.putCombinedSettings(
                context,
                mode.settings.filter { it.enabled }
            )

            // Schedule next
            if (mode.enabled) {
                Log.d("ModeWorker", "Scheduling next run for mode: $modeId")
                ModeScheduleManager(context, ModesRepository()).scheduleMode(mode, true)
            } else {
                Log.d("ModeWorker", "Mode is disabled; not scheduling next run.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("ModeWorker", "Error running mode", e)
            return Result.failure()
        }
    }

}