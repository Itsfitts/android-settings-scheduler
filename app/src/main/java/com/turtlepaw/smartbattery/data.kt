package com.turtlepaw.smartbattery

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.compose.runtime.mutableStateListOf
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalTime

data class Mode(
    val id: String,
    val name: String,
    val iconPath: String = "battery",  // Default icon
    val isSecure: Boolean = false,
    val isGlobal: Boolean = false,
    val isSystem: Boolean = false,
    val schedule: Schedule? = null,
    val settings: List<Setting>
)

data class Setting(
    val namespace: String,
    val key: String,
    val value: Any
)

data class Schedule(
    val enabled: Boolean = false,
    val startTime: Int? = null, // 0-1439 (e.g., 8:30 AM = 510)
    val endTime: Int? = null,
    val days: List<DayOfWeek> = emptyList()
)

// Helper extension functions
fun Int.toHoursAndMinutes(): Pair<Int, Int> {
    val hours = this / 60
    val minutes = this % 60
    return Pair(hours, minutes)
}

fun Pair<Int, Int>.toMinutesSinceMidnight(): Int {
    return first * 60 + second
}

@Entity(tableName = "modes")
data class ModeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Auto-generated ID
    val name: String,
    val iconName: String? = null,
    val settings: List<SettingEntity>, // Store settings as JSON string
    val scheduledTime: LocalTime? = null,
    val scheduleDays: List<Boolean>? = emptyList(),
    val enabled: Boolean = true,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val id: String,
    val modeId: Int,
    val key: String,
    val value: String,
    val namespace: String,
    val enabled: Boolean = false,
)

@Dao
interface ModeDao {
    @Query("SELECT * FROM modes")
    suspend fun getAllModes(): List<ModeEntity>

    @Query("SELECT * FROM modes WHERE id = :modeId")
    suspend fun getMode(modeId: String): ModeEntity?

    @Query("SELECT * FROM settings WHERE modeId = :modeId")
    suspend fun getModeSettings(modeId: String): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMode(mode: ModeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)

    @Transaction
    suspend fun saveModeWithSettings(mode: ModeEntity, settings: List<SettingEntity>) {
        insertMode(mode)
        settings.forEach { insertSetting(it) }
    }
}

class Converters {
    @TypeConverter
    fun fromSettingsList(settings: List<SettingEntity>): String {
        val gson = Gson()
        return gson.toJson(settings)
    }

    @TypeConverter
    fun toSettingsList(settingsString: String): List<SettingEntity> {
        val gson = Gson()
        val listType = object : TypeToken<List<SettingEntity>>() {}.type
        return gson.fromJson(settingsString, listType)
    }

    // Local time
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time.toString()
    }

    @TypeConverter
    fun toLocalTime(time: String?): LocalTime? {
        return time?.let {
            try {
                LocalTime.parse(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromBooleanList(list: List<Boolean>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun toBooleanList(value: String?): List<Boolean>? {
        return value?.split(",")?.map { it.toBoolean() }
    }
}

@Database(entities = [ModeEntity::class, SettingEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modeDao(): ModeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "modes_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository class to abstract database operations
class ModesRepository {
    private val _modes = mutableStateListOf<ModeEntity>()
    val modes: List<ModeEntity> = _modes

    suspend fun addMode(mode: ModeEntity, context: Context) {
        _modes.add(mode)
        AppDatabase.getInstance(context).modeDao().insertMode(mode)
    }

    suspend fun editMode(mode: ModeEntity, context: Context) {
        _modes.removeIf { it.id == mode.id }
        _modes.add(mode)
        AppDatabase.getInstance(context).modeDao().insertMode(mode)
    }

    suspend fun getAllModes(context: Context): List<ModeEntity> {
        _modes.clear()
        _modes.addAll(AppDatabase.getInstance(context).modeDao().getAllModes())
        return _modes
    }
}