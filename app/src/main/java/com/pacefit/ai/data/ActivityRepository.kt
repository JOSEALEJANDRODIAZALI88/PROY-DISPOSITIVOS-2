package com.pacefit.ai.data

import androidx.room.*
import com.pacefit.ai.domain.DetectedSegment
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activity_sessions")
data class ActivitySessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val state: String = "ACTIVE",
    val durationMillis: Long = 0,
)

@Entity(tableName = "route_points",
    foreignKeys = [ForeignKey(entity = ActivitySessionEntity::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")])
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val speedKmh: Double,
    val altitudeMeters: Double?,
    val pressureHpa: Double?,
)

@Entity(tableName = "session_segments",
    foreignKeys = [
        ForeignKey(entity = ActivitySessionEntity::class, parentColumns = ["id"],
            childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransportSegmentEntity::class, parentColumns = ["id"],
            childColumns = ["segmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")])
data class SessionSegmentEntity(@PrimaryKey val segmentId: String, val sessionId: String)

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_sessions ORDER BY startedAt DESC")
    fun sessions(): Flow<List<ActivitySessionEntity>>
    @Query("SELECT * FROM activity_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun unfinished(): ActivitySessionEntity?
    @Query("SELECT * FROM activity_sessions WHERE id = :id")
    suspend fun find(id: String): ActivitySessionEntity?
    @Upsert suspend fun save(session: ActivitySessionEntity)
    @Insert suspend fun point(point: RoutePointEntity)
    @Insert suspend fun link(link: SessionSegmentEntity)
    @Query("SELECT * FROM route_points WHERE sessionId = :id ORDER BY timeMillis, id")
    fun points(id: String): Flow<List<RoutePointEntity>>
    @Query("SELECT t.* FROM transport_segments t JOIN session_segments s ON t.id = s.segmentId WHERE s.sessionId = :id ORDER BY t.startedAt")
    fun segments(id: String): Flow<List<TransportSegmentEntity>>
    @Query("UPDATE activity_sessions SET state = 'INTERRUPTED' WHERE endedAt IS NULL AND state = 'ACTIVE'")
    suspend fun interrupt()
    @Query("DELETE FROM activity_sessions") suspend fun deleteAll()
}

class ActivityRepository(private val database: HealthDatabase, private val local: LocalRepository) {
    val sessions get() = database.activityDao().sessions()
    fun points(id: String) = database.activityDao().points(id)
    fun segments(id: String) = database.activityDao().segments(id)
    suspend fun recoverInterrupted() = local.access { database.activityDao().interrupt() }
    suspend fun unfinished() = local.access { database.activityDao().unfinished() }
    suspend fun start(): ActivitySessionEntity = local.access {
        check(database.activityDao().unfinished() == null) { "Finaliza o continúa la actividad pendiente." }
        ActivitySessionEntity(UUID.randomUUID().toString(), System.currentTimeMillis()).also { database.activityDao().save(it) }
    }
    suspend fun change(id: String, state: String, extraMillis: Long = 0) = local.access {
        require(state in setOf("ACTIVE", "PAUSED", "FINISHED", "INTERRUPTED"))
        val old = requireNotNull(database.activityDao().find(id))
        check(old.endedAt == null)
        old.copy(state = state, durationMillis = old.durationMillis + extraMillis.coerceAtLeast(0),
            endedAt = if (state == "FINISHED") System.currentTimeMillis() else null)
            .also { database.activityDao().save(it) }
    }
    suspend fun point(point: RoutePointEntity, extraMillis: Long) = local.access {
        database.withTransaction {
            val old = database.activityDao().find(point.sessionId) ?: return@withTransaction
            if (old.state != "ACTIVE") return@withTransaction
            database.activityDao().point(point)
            database.activityDao().save(old.copy(durationMillis = old.durationMillis + extraMillis.coerceAtLeast(0)))
        }
    }
    suspend fun segment(sessionId: String, value: DetectedSegment) = local.access {
        database.withTransaction {
            val old = database.activityDao().find(sessionId) ?: return@withTransaction
            if (old.endedAt != null) return@withTransaction
            val id = UUID.randomUUID().toString()
            database.localDao().insertSegment(TransportSegmentEntity(id, value.startedAt, value.endedAt,
                value.distanceMeters, value.averageSpeedKmh, value.steps, value.activity.name,
                value.classification.name, value.includedInMetrics))
            database.activityDao().link(SessionSegmentEntity(id, sessionId))
        }
    }
}
