package com.pacefit.ai.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transport_segments")
data class TransportSegmentEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val steps: Long?,
    val activity: String,
    val classification: String,
    val includedInMetrics: Boolean,
)

@Entity(tableName = "plogging_entries")
data class PloggingEntity(
    @PrimaryKey val id: String,
    val quantity: String,
    val points: Int,
    val latitude: Double,
    val longitude: Double,
    val photoName: String,
    val createdAt: Long,
)

@Entity(tableName = "urban_reports")
data class UrbanReportEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val photoName: String,
    val resolved: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "local_settings")
data class LocalSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val detectionWindowSeconds: Int = 30,
)

@Dao
interface LocalDao {
    @Query("SELECT * FROM transport_segments ORDER BY startedAt DESC LIMIT 200")
    fun observeSegments(): Flow<List<TransportSegmentEntity>>

    @Query("SELECT * FROM plogging_entries ORDER BY createdAt DESC LIMIT 200")
    fun observePlogging(): Flow<List<PloggingEntity>>

    @Query("SELECT * FROM urban_reports ORDER BY createdAt DESC LIMIT 200")
    fun observeReports(): Flow<List<UrbanReportEntity>>

    @Query("SELECT * FROM local_settings WHERE id = 1")
    suspend fun settings(): LocalSettingsEntity?

    @Upsert suspend fun saveSettings(settings: LocalSettingsEntity)
    @Insert suspend fun insertSegment(segment: TransportSegmentEntity)
    @Insert suspend fun insertPlogging(entry: PloggingEntity)
    @Insert suspend fun insertReport(report: UrbanReportEntity)

    @Query("UPDATE urban_reports SET resolved = :resolved WHERE id = :id")
    suspend fun resolveReport(id: String, resolved: Boolean)

    @Query("SELECT photoName FROM urban_reports WHERE id = :id")
    suspend fun reportPhoto(id: String): String?

    @Query("SELECT photoName FROM plogging_entries WHERE id = :id")
    suspend fun ploggingPhoto(id: String): String?

    @Query("DELETE FROM urban_reports WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("DELETE FROM plogging_entries WHERE id = :id")
    suspend fun deletePlogging(id: String)

    @Query("SELECT photoName FROM urban_reports")
    suspend fun reportPhotos(): List<String>

    @Query("SELECT photoName FROM plogging_entries")
    suspend fun ploggingPhotos(): List<String>

    @Query("DELETE FROM transport_segments") suspend fun deleteSegments()
    @Query("DELETE FROM plogging_entries") suspend fun deletePlogging()
    @Query("DELETE FROM urban_reports") suspend fun deleteReports()
    @Query("DELETE FROM local_settings") suspend fun deleteSettings()
}
