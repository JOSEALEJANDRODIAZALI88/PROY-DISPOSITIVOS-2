package com.pacefit.ai.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
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

@Database(entities = [HealthProfileEntity::class], version = 1, exportSchema = true)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun profileDao(): HealthProfileDao
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
