package com.turtlepaw.smartbattery

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.util.Locale

class BatteryStateManager {
    fun getChargingMode(contentResolver: ContentResolver): BatteryState {
        val adaptiveChargingEnabled = Settings.Secure.getInt(
            contentResolver,
            "adaptive_charging_enabled",
            0
        )
        val chargeOptimizationMode = Settings.Secure.getInt(
            contentResolver,
            "charge_optimization_mode",
            0
        )

        return when {
            adaptiveChargingEnabled == 1 && chargeOptimizationMode == 0 -> BatteryState.Adaptive
            adaptiveChargingEnabled == 0 && chargeOptimizationMode == 1 -> BatteryState.Limited
            else -> BatteryState.Unknown
        }
    }

    fun setChargingMode(contentResolver: ContentResolver, state: BatteryState) {
        when (state) {
            BatteryState.Adaptive -> {
                Settings.Secure.putInt(contentResolver, "adaptive_charging_enabled", 1)
                Settings.Secure.putInt(contentResolver, "charge_optimization_mode", 0)
            }
            BatteryState.Limited -> {
                Settings.Secure.putInt(contentResolver, "adaptive_charging_enabled", 0)
                Settings.Secure.putInt(contentResolver, "charge_optimization_mode", 1)
            }
            BatteryState.Unknown -> {
                throw IllegalArgumentException("Unknown charging state")
            }
        }
    }

    fun setSchedule(context: Context, days: List<Boolean>) {
        val schedule = days
            .mapIndexed { index, b ->
                if (b) "1" else "0"
            }
            .joinToString("")

        // use shared preferences to store the schedule
        val sharedPreferences = getSharerPreferences(context)
        sharedPreferences.edit().putString("schedule", schedule).apply()
    }

    fun getSchedule(context: Context): List<Boolean> {
        val sharedPreferences = getSharerPreferences(context)
        val schedule = sharedPreferences.getString("schedule", null)

        return schedule?.map { it == '1' } ?: List(7) { false }
    }

    fun getDefaultMode(context: Context): BatteryState {
        // use shared preferences to retrieve the default mode
        val sharedPreferences = getSharerPreferences(context)
        val defaultMode = sharedPreferences.getString("default_mode", null)
        return defaultMode?.let { BatteryState.valueOf(it) } ?: BatteryState.Unknown
    }

    fun setDefaultMode(context: Context, state: BatteryState) {
        // use shared preferences to store the default mode
        val sharedPreferences = getSharerPreferences(context)
        sharedPreferences.edit().putString("default_mode", state.name).apply()
    }

    companion object {
        fun getSharerPreferences(context: Context) = context.getSharedPreferences("battery_schedule", Context.MODE_PRIVATE)
    }
}

enum class BatteryState {
    Adaptive,
    Limited,
    Unknown
}

fun getBatteryStateOpposite(state: BatteryState): BatteryState {
    return when (state) {
        BatteryState.Adaptive -> BatteryState.Limited
        BatteryState.Limited -> BatteryState.Adaptive
        BatteryState.Unknown -> BatteryState.Unknown
    }
}

object CombinedSettings {
    private fun getAllSettings(contentResolver: ContentResolver, namespace: String): Map<String, Pair<String, String>> {
        val uri: Uri = when (namespace) {
            "system" -> Settings.System.CONTENT_URI
            "secure" -> Settings.Secure.CONTENT_URI
            "global" -> Settings.Global.CONTENT_URI
            else -> throw IllegalArgumentException("Invalid namespace: $namespace")
        }

        val settingsMap = mutableMapOf<String, Pair<String, String>>()
        val cursor: Cursor? = contentResolver.query(uri, arrayOf("name", "value"), null, null, null)

        cursor?.use {
            val nameIndex = it.getColumnIndex("name")
            val valueIndex = it.getColumnIndex("value")
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val value = Pair(it.getString(valueIndex), namespace)
                settingsMap[name] = value
            }
        }

        return settingsMap
    }

    /**
     * Get Settings.Global and Settings.Secure values
     */
    fun getCombinedSettings(context: Context): Map<String, Pair<String, String>> {
        val settings = mutableMapOf<String, Pair<String, String>>()
        val contentResolver = context.contentResolver
        val globalValues = getAllSettings(contentResolver, "global")
        val secureValues = getAllSettings(contentResolver, "secure")
        val systemValues = getAllSettings(contentResolver, "system")
        settings.putAll(globalValues)
        settings.putAll(secureValues)
        settings.putAll(systemValues)
        return settings
    }

    fun putCombinedSettings(context: Context, settings: List<SettingEntity>){
        val contentResolver = context.contentResolver
        settings.forEach {
            when (it.namespace) {
                "global" -> Settings.Global.putString(contentResolver, it.key, it.value)
                "secure" -> Settings.Secure.putString(contentResolver, it.key, it.value)
                "system" -> Settings.System.putString(contentResolver, it.key, it.value)
            }
        }
    }
}