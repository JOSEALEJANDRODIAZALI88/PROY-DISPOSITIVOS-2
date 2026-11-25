package com.pacefit.ai.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pacefit.ai.domain.HealthProfile
import com.pacefit.ai.domain.ProfileSex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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

@Database(
    entities = [HealthProfileEntity::class, TransportSegmentEntity::class, PloggingEntity::class,
        UrbanReportEntity::class, LocalSettingsEntity::class, ActivitySessionEntity::class,
        RoutePointEntity::class, SessionSegmentEntity::class, PublicationEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun profileDao(): HealthProfileDao
    abstract fun localDao(): LocalDao
    abstract fun activityDao(): ActivityDao
    abstract fun publicationDao(): PublicationDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS activity_sessions (id TEXT NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, endedAt INTEGER, state TEXT NOT NULL, durationMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS route_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, timeMillis INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL NOT NULL, speedKmh REAL NOT NULL, altitudeMeters REAL, pressureHpa REAL, FOREIGN KEY(sessionId) REFERENCES activity_sessions(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_route_points_sessionId ON route_points(sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS session_segments (segmentId TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES activity_sessions(id) ON DELETE CASCADE, FOREIGN KEY(segmentId) REFERENCES transport_segments(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_segments_sessionId ON session_segments(sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS city_publications (localId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, remoteId TEXT NOT NULL, kind TEXT NOT NULL)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS transport_segments (id TEXT NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, distanceMeters REAL NOT NULL, averageSpeedKmh REAL NOT NULL, steps INTEGER, activity TEXT NOT NULL, classification TEXT NOT NULL, includedInMetrics INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plogging_entries (id TEXT NOT NULL PRIMARY KEY, quantity TEXT NOT NULL, points INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, photoName TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS urban_reports (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, description TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, photoName TEXT NOT NULL, resolved INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS local_settings (id INTEGER NOT NULL PRIMARY KEY, detectionWindowSeconds INTEGER NOT NULL)")
            }
        }
    }
}

interface HealthRepository {
    fun observe(): Flow<HealthProfile?>
    suspend fun save(profile: HealthProfile)
    suspend fun delete()
}

class RoomHealthRepository(
    private val dao: HealthProfileDao,
    private val localStorage: LocalRepository? = null,
    private val awaitReady: suspend () -> Unit = {},
) : HealthRepository {
    override fun observe(): Flow<HealthProfile?> = flow {
        awaitReady()
        localStorage?.initialize()
        emitAll(dao.observe().map { row ->
            row?.let {
                HealthProfile(
                    it.weightKg, it.heightCm, it.age,
                    ProfileSex.entries.firstOrNull { sex -> sex.name == it.sex } ?: ProfileSex.UNSPECIFIED,
                    it.updatedAt,
                )
            }
        })
    }

    override suspend fun save(profile: HealthProfile) = access {
        dao.save(
            HealthProfileEntity(
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                age = profile.age,
                sex = profile.sex.name,
                updatedAt = profile.updatedAt,
            )
        )
    }

    override suspend fun delete() = access { dao.delete() }

    private suspend fun <T> access(block: suspend () -> T): T {
        awaitReady()
        return if (localStorage != null) localStorage.access(block) else block()
    }
}
