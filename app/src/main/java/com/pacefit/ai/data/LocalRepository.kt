package com.pacefit.ai.data

import android.net.Uri
import androidx.room.withTransaction
import com.pacefit.ai.domain.Coordinates
import com.pacefit.ai.domain.DetectedSegment
import com.pacefit.ai.domain.ReportKind
import com.pacefit.ai.domain.WasteQuantity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TableSnapshot(val name: String, val count: Long, val columns: List<String>, val rows: List<List<String>>)

class LocalRepository(
    private val database: HealthDatabase,
    val photos: PrivatePhotoStore,
    val databaseFile: File,
) {
    private val mutex = Mutex()
    private val dao get() = database.localDao()
    val segments get() = dao.observeSegments()
    val plogging get() = dao.observePlogging()
    val reports get() = dao.observeReports()

    suspend fun initialize() = locked {
        photos.ensureDirectories()
        if (photos.eraseMarker.exists()) finishErase()
        photos.removeOrphans(PhotoDirectory.PLOGGING, dao.ploggingPhotos().toSet())
        photos.removeOrphans(PhotoDirectory.REPORTS, dao.reportPhotos().toSet())
    }

    suspend fun windowSeconds(): Int = locked { dao.settings()?.detectionWindowSeconds ?: 30 }

    suspend fun saveWindow(seconds: Int) = locked {
        require(seconds in 15..120)
        dao.saveSettings(LocalSettingsEntity(detectionWindowSeconds = seconds))
    }

    suspend fun saveSegment(segment: DetectedSegment) = locked {
        dao.insertSegment(TransportSegmentEntity(
            UUID.randomUUID().toString(), segment.startedAt, segment.endedAt, segment.distanceMeters,
            segment.averageSpeedKmh, segment.steps, segment.activity.name, segment.classification.name,
            segment.includedInMetrics,
        ))
    }

    suspend fun addPlogging(quantity: WasteQuantity, coordinates: Coordinates, photo: Uri, consent: Boolean) = locked {
        require(consent) { "Confirma el uso local de la foto y ubicación." }
        validateCoordinates(coordinates)
        val name = photos.importPhoto(photo, PhotoDirectory.PLOGGING)
        val id = UUID.randomUUID().toString()
        try {
            // Local-only scores. Never accepted as trusted competitive/server points.
            dao.insertPlogging(PloggingEntity(
                id, quantity.name, quantity.points, coordinates.latitude,
                coordinates.longitude, name, System.currentTimeMillis(),
            ))
            id
        } catch (error: Exception) {
            withContext(NonCancellable) {
                // A cancellation may arrive just after SQLite committed.
                if (dao.ploggingPhoto(id) == null) photos.delete(PhotoDirectory.PLOGGING, name)
            }
            throw error
        }
    }

    suspend fun addReport(kind: ReportKind, description: String, coordinates: Coordinates, photo: Uri, consent: Boolean) = locked {
        require(consent) { "Confirma el uso local de la foto y ubicación." }
        require(description.trim().length <= 500) { "La descripción admite hasta 500 caracteres." }
        validateCoordinates(coordinates)
        val name = photos.importPhoto(photo, PhotoDirectory.REPORTS)
        val id = UUID.randomUUID().toString()
        try {
            dao.insertReport(UrbanReportEntity(
                id, kind.name, description.trim(), coordinates.latitude,
                coordinates.longitude, name, false, System.currentTimeMillis(),
            ))
            id
        } catch (error: Exception) {
            withContext(NonCancellable) {
                if (dao.reportPhoto(id) == null) photos.delete(PhotoDirectory.REPORTS, name)
            }
            throw error
        }
    }

    suspend fun resolveReport(id: String, resolved: Boolean) = locked { dao.resolveReport(id, resolved) }

    suspend fun deleteReport(id: String) = locked {
        val name = dao.reportPhoto(id) ?: return@locked
        dao.deleteReport(id)
        // If interrupted here, initialize() removes the unreferenced file on next launch.
        photos.delete(PhotoDirectory.REPORTS, name)
    }

    suspend fun deletePlogging(id: String) = locked {
        val name = dao.ploggingPhoto(id) ?: return@locked
        dao.deletePlogging(id)
        photos.delete(PhotoDirectory.PLOGGING, name)
    }

    suspend fun eraseAll() = locked {
        photos.ensureDirectories()
        FileOutputStream(photos.eraseMarker).use { it.write(1); it.fd.sync() }
        finishErase()
    }

    private suspend fun finishErase() {
        // Persistent marker retries an interrupted deletion on the next app launch.
        photos.clearPhotos()
        database.withTransaction {
            database.publicationDao().clear()
            database.activityDao().deleteAll()
            dao.deleteSegments()
            dao.deletePlogging()
            dao.deleteReports()
            dao.deleteSettings()
            database.profileDao().delete()
        }
        check(photos.eraseMarker.delete()) { "El borrado debe completarse al volver a abrir la app." }
    }

    suspend fun inspect(table: String): TableSnapshot = locked {
        require(table in TABLES) { "Tabla no permitida." }
        val connection = database.openHelper.readableDatabase
        val count = connection.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getLong(0) }
        connection.query("SELECT * FROM `$table` ORDER BY rowid DESC LIMIT 50").use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = buildList {
                while (cursor.moveToNext()) {
                    add(columns.indices.map { if (cursor.isNull(it)) "NULL" else cursor.getString(it) })
                }
            }
            TableSnapshot(table, count, columns, rows)
        }
    }

    internal suspend fun <T> access(block: suspend () -> T): T = locked(block)

    private fun validateCoordinates(coordinates: Coordinates) {
        require(coordinates.latitude.isFinite() && coordinates.latitude in -90.0..90.0 &&
            coordinates.longitude.isFinite() && coordinates.longitude in -180.0..180.0) { "Ubicación no válida." }
    }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            // No writes may resurrect records after an interrupted reset.
            if (photos.eraseMarker.exists()) finishErase()
            block()
        }
    }

    companion object {
        val TABLES = listOf("health_profile", "transport_segments", "plogging_entries", "urban_reports",
            "local_settings", "activity_sessions", "route_points", "session_segments", "city_publications")
    }
}
