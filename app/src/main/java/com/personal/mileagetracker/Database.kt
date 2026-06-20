package com.personal.mileagetracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMiles: Double,
    val purpose: String = "Personal",
    val notes: String = "",
    val routePoints: String = "" // New field to store the full route
)

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip)

    @Update
    suspend fun update(trip: Trip)

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<Trip>>
}

@Database(entities = [Trip::class], version = 2, exportSchema = false) // Updated version to 2
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}
