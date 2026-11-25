package com.pacefit.ai.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.pacefit.ai.domain.HealthProfile
import com.pacefit.ai.domain.ProfileSex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "health_profile")
data class HealthProfileEntity(
    @PrimaryKey val id: Int = 1,
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val sex: String,
    val updatedAt: Long,
)

@Dao
interface HealthProfileDao {
    @Query("SELECT * FROM health_profile WHERE id = 1")
    fun observe(): Flow<HealthProfileEntity?>

    @Upsert
    suspend fun save(profile: HealthProfileEntity)

    @Query("DELETE FROM health_profile")
    suspend fun delete()
}

@Entity(tableName = "pace_parameters")
data class PaceParameterEntity(
    @PrimaryKey val key: String,
    val label: String,
    val value: Double,
    val unit: String,
    val source: String,
    val status: String,
    val updatedAt: Long,
)

@Entity(tableName = "water_logs")
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val milliliters: Int,
    val loggedAt: Long,
)

@Dao
interface PaceFitModulesDao {
    @Query("SELECT * FROM pace_parameters ORDER BY label")
    fun observeParameters(): Flow<List<PaceParameterEntity>>

    @Upsert
    suspend fun saveParameter(parameter: PaceParameterEntity)

    @Query("DELETE FROM pace_parameters WHERE `key` = :key")
    suspend fun deleteParameter(key: String)

    @Query("SELECT * FROM water_logs WHERE loggedAt >= :start AND loggedAt < :end ORDER BY loggedAt DESC")
    fun observeWaterLogs(start: Long, end: Long): Flow<List<WaterLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWaterLog(log: WaterLogEntity)

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteWaterLog(id: Long)
}

@Database(entities = [HealthProfileEntity::class, PaceParameterEntity::class, WaterLogEntity::class], version = 2, exportSchema = true)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun profileDao(): HealthProfileDao
    abstract fun modulesDao(): PaceFitModulesDao
}

interface HealthRepository {
    fun observe(): Flow<HealthProfile?>
    suspend fun save(profile: HealthProfile)
    suspend fun delete()
}

class RoomHealthRepository(private val dao: HealthProfileDao) : HealthRepository {
    override fun observe(): Flow<HealthProfile?> = dao.observe().map { row ->
        row?.let {
            HealthProfile(
                it.weightKg, it.heightCm, it.age,
                ProfileSex.entries.firstOrNull { sex -> sex.name == it.sex } ?: ProfileSex.UNSPECIFIED,
                it.updatedAt,
            )
        }
    }

    override suspend fun save(profile: HealthProfile) = dao.save(
        HealthProfileEntity(
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            age = profile.age,
            sex = profile.sex.name,
            updatedAt = profile.updatedAt,
        )
    )

    override suspend fun delete() = dao.delete()
}
