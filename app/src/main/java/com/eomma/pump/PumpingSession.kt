package com.eomma.pump

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName = "pumping_sessions")
data class PumpingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val duration: Int,  // in seconds
    val volume: Double, // in milliliters
    val side: PumpingSide,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String? = null  // Optional: link to the pump device
)

enum class PumpingSide {
    LEFT, RIGHT;

    companion object {
        fun fromString(value: String): PumpingSide {
            return try {
                valueOf(value.uppercase(Locale.getDefault()))
            } catch (e: IllegalArgumentException) {
                LEFT // Default value if parsing fails
            }
        }
    }
}

// Room Type Converters
class PumpingSessionConverters {
    @TypeConverter
    fun fromPumpingSide(value: PumpingSide): String {
        return value.name
    }

    @TypeConverter
    fun toPumpingSide(value: String): PumpingSide {
        return PumpingSide.fromString(value)
    }
}

// Extension functions for convenient data manipulation
fun PumpingSession.getDurationFormatted(): String {
    val minutes = duration / 60
    val seconds = duration % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun PumpingSession.getFormattedTimestamp(): String {
    return Date(timestamp).toString()
}

// Data class for daily statistics
data class DayStats(
    val sessionCount: Int = 0,
    val totalVolume: Double = 0.0,
    val totalDuration: Int = 0,
    val leftSideVolume: Double = 0.0,
    val rightSideVolume: Double = 0.0
)

// Database operations interface
@Dao
interface PumpingSessionDao {
    @Query("SELECT * FROM pumping_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<PumpingSession>>

    @Query("SELECT * FROM pumping_sessions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getSessionsForTimeRange(startTime: Long, endTime: Long): Flow<List<PumpingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: PumpingSession): Long

    @Update
    suspend fun update(session: PumpingSession)

    @Query("""
        SELECT 
            COUNT(*) as sessionCount,
            COALESCE(SUM(volume), 0.0) as totalVolume,
            COALESCE(SUM(duration), 0) as totalDuration,
            COALESCE(SUM(CASE WHEN side = 'LEFT' THEN volume ELSE 0.0 END), 0.0) as leftSideVolume,
            COALESCE(SUM(CASE WHEN side = 'RIGHT' THEN volume ELSE 0.0 END), 0.0) as rightSideVolume
        FROM pumping_sessions 
        WHERE timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getDayStats(startTime: Long, endTime: Long): DayStats

    @Query("SELECT * FROM pumping_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): PumpingSession?
}

// Database class
@Database(entities = [PumpingSession::class], version = 1, exportSchema = false)
@TypeConverters(PumpingSessionConverters::class)
abstract class PumpingSessionDatabase : RoomDatabase() {
    abstract fun pumpingSessionDao(): PumpingSessionDao

    companion object {
        @Volatile
        private var INSTANCE: PumpingSessionDatabase? = null

        fun getDatabase(context: Context): PumpingSessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PumpingSessionDatabase::class.java,
                    "pumping_session_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository class
class PumpingSessionRepository(private val pumpingSessionDao: PumpingSessionDao) {
    fun getAllSessions() = pumpingSessionDao.getAllSessions()

    fun getSessionsForDate(date: Calendar): Flow<List<PumpingSession>> {
        val startOfDay = date.clone() as Calendar
        startOfDay.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endOfDay = date.clone() as Calendar
        endOfDay.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return pumpingSessionDao.getSessionsForTimeRange(
            startOfDay.timeInMillis,
            endOfDay.timeInMillis
        )
    }

    suspend fun getDayStats(date: Calendar): DayStats {
        val startOfDay = date.clone() as Calendar
        startOfDay.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endOfDay = date.clone() as Calendar
        endOfDay.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return pumpingSessionDao.getDayStats(startOfDay.timeInMillis, endOfDay.timeInMillis)
    }

    suspend fun insert(session: PumpingSession) = pumpingSessionDao.insert(session)

    suspend fun update(session: PumpingSession) = pumpingSessionDao.update(session)

    suspend fun getSessionById(sessionId: Long): PumpingSession? = pumpingSessionDao.getSessionById(sessionId)

    suspend fun updateSessionVolume(
        sessionId: Long,
        leftAmount: Double,
        rightAmount: Double
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false

        // Create two sessions if both amounts are provided
        if (leftAmount > 0 && rightAmount > 0) {
            val leftSession = session.copy(
                volume = leftAmount,
                side = PumpingSide.LEFT
            )
            val rightSession = session.copy(
                id = sessionId + 1, // Create a new ID for the right session
                volume = rightAmount,
                side = PumpingSide.RIGHT
            )

            // Update or insert both sessions
            pumpingSessionDao.update(leftSession)
            pumpingSessionDao.insert(rightSession)
        } else if (leftAmount > 0) {
            // Update as left side session
            val updatedSession = session.copy(
                volume = leftAmount,
                side = PumpingSide.LEFT
            )
            pumpingSessionDao.update(updatedSession)
        } else if (rightAmount > 0) {
            // Update as right side session
            val updatedSession = session.copy(
                volume = rightAmount,
                side = PumpingSide.RIGHT
            )
            pumpingSessionDao.update(updatedSession)
        }

        return true
    }
}