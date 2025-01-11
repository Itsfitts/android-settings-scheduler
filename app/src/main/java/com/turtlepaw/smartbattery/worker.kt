package com.turtlepaw.smartbattery

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.turtlepaw.smartbattery.shizuku.ShizukuUtils
import java.util.Calendar

class SecureSettingsWorker(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    /**
     * This Worker class performs the core logic of setting the charging mode based on the day of the week and a predefined schedule.
     *
     * It utilizes the BatteryStateManager to retrieve and set the charging mode.
     */
    override fun doWork(): Result {
        // Check for permissions
        if (!ShizukuUtils.isSecureSettingsGranted(applicationContext)) {
            return Result.failure()
        }

        val manager = BatteryStateManager()
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val schedule = manager.getSchedule(applicationContext)
        val defaultMode = manager.getDefaultMode(applicationContext)
        val oppositeMode = getBatteryStateOpposite(defaultMode)

        Log.d("SecureSettingsWorker", "Day of week: $day\nSchedule: $schedule\nDefault mode: $defaultMode\nOpposite mode: $oppositeMode\nWill switch: ${schedule[day - 1]}")

        // If the day is in the schedule, switch the charging mode
        if (schedule[day - 1] == true) {
            manager.setChargingMode(applicationContext.contentResolver, oppositeMode)
        } else {
            manager.setChargingMode(applicationContext.contentResolver, defaultMode)
        }

        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }
}