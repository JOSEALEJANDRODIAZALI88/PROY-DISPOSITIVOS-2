package com.pacefit.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pacefit.ai.domain.ActivityHint
import com.pacefit.ai.domain.Coordinates
import com.pacefit.ai.domain.DetectedSegment
import com.pacefit.ai.domain.ReportKind
import com.pacefit.ai.domain.TransportClass
import com.pacefit.ai.domain.WasteQuantity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "local-test-${UUID.randomUUID()}.db"
    private val root = File(context.filesDir, "local-test-${UUID.randomUUID()}")
    private val source = File(context.cacheDir, "source-${UUID.randomUUID()}.jpg")
    private lateinit var database: HealthDatabase
    private lateinit var photos: PrivatePhotoStore
    private lateinit var repository: LocalRepository

    @get:Rule
    val migration = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HealthDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun open() {
        database = Room.databaseBuilder(context, HealthDatabase::class.java, name)
            .addMigrations(HealthDatabase.MIGRATION_1_2, HealthDatabase.MIGRATION_2_3).build()
        photos = PrivatePhotoStore(context, root)
        repository = LocalRepository(database, photos, context.getDatabasePath(name))
    }

    @Before fun prepare() {
        open()
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "SOURCE-PRIVATE")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "16/1,30/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "68/1,9/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            saveAttributes()
        }
    }

    @After fun clean() {
        database.close()
        context.deleteDatabase(name)
        root.deleteRecursively()
        source.delete()
    }

    @Test fun migrationPreservesVersionOneProfileAndCreatesAllTables() {
        database.close()
        migration.createDatabase(name, 1).apply {
            execSQL("INSERT INTO health_profile VALUES (1, 70.5, 175.0, 25, 'UNSPECIFIED', 123)")
            close()
        }
        migration.runMigrationsAndValidate(name, 3, true, HealthDatabase.MIGRATION_1_2, HealthDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT weightKg FROM health_profile WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals(70.5, it.getDouble(0), 0.001)
            }
            LocalRepository.TABLES.forEach { table -> db.query("SELECT * FROM `$table`").close() }
        }
    }

    @Test fun allModulesPersistAfterDatabaseReopen() = runBlocking {
        repository.initialize()
        database.profileDao().save(HealthProfileEntity(weightKg = 65.0, heightCm = 170.0, age = 20, sex = "UNSPECIFIED", updatedAt = 1))
        repository.saveWindow(60)
        repository.saveSegment(DetectedSegment(1, 30_001, 300.0, 36.0, 0, ActivityHint.UNKNOWN, TransportClass.VEHICLE))
        repository.addPlogging(WasteQuantity.MEDIUM, Coordinates(-16.5, -68.1), Uri.fromFile(source), true)
        repository.addReport(ReportKind.POTHOLE, "Prueba", Coordinates(-16.5, -68.1), Uri.fromFile(source), true)
        database.close()
        open()
        repository.initialize()
        assertNotNull(database.profileDao().observe().first())
        assertEquals(60, repository.windowSeconds())
        assertFalse(repository.segments.first().single().includedInMetrics)
        assertEquals(10, repository.plogging.first().single().points)
        assertTrue(photos.file(PhotoDirectory.PLOGGING, repository.plogging.first().single().photoName).exists())
        assertEquals("Prueba", repository.reports.first().single().description)
        assertEquals(1L, repository.inspect("urban_reports").count)
    }

    @Test fun photoIsReencodedWithoutGpsOrCameraMetadata() {
        val name = photos.importPhoto(Uri.fromFile(source), PhotoDirectory.REPORTS)
        val exif = ExifInterface(photos.file(PhotoDirectory.REPORTS, name).absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertTrue(source.exists())
    }

    @Test fun reportCanBeResolvedAndDeletedWithItsPhoto() = runBlocking {
        repository.initialize()
        repository.addReport(ReportKind.LIGHT, "", Coordinates(0.0, 0.0), Uri.fromFile(source), true)
        val report = repository.reports.first().single()
        repository.resolveReport(report.id, true)
        assertTrue(repository.reports.first().single().resolved)
        val file = photos.file(PhotoDirectory.REPORTS, report.photoName)
        repository.deleteReport(report.id)
        assertTrue(repository.reports.first().isEmpty())
        assertFalse(file.exists())
    }

    @Test fun erasureRemovesRowsPhotosSettingsAndPreservesSource() = runBlocking {
        repository.initialize()
        database.profileDao().save(HealthProfileEntity(weightKg = 65.0, heightCm = 170.0, age = 20, sex = "UNSPECIFIED", updatedAt = 1))
        repository.saveWindow(60)
        repository.addPlogging(WasteQuantity.MUCH, Coordinates(0.0, 0.0), Uri.fromFile(source), true)
        repository.addReport(ReportKind.WASTE, "", Coordinates(0.0, 0.0), Uri.fromFile(source), true)
        repository.eraseAll()
        LocalRepository.TABLES.forEach { assertEquals(0L, repository.inspect(it).count) }
        assertEquals(30, repository.windowSeconds())
        PhotoDirectory.entries.forEach { assertTrue(photos.directory(it).listFiles()!!.isEmpty()) }
        assertFalse(photos.eraseMarker.exists())
        assertTrue(source.exists())
    }

    @Test fun interruptedErasureResumesOnReopen() = runBlocking {
        repository.initialize()
        repository.addPlogging(WasteQuantity.LITTLE, Coordinates(0.0, 0.0), Uri.fromFile(source), true)
        photos.eraseMarker.writeText("pending")
        database.close()
        open()
        repository.initialize()
        assertEquals(0L, repository.inspect("plogging_entries").count)
        assertFalse(photos.eraseMarker.exists())
        assertTrue(photos.directory(PhotoDirectory.PLOGGING).listFiles()!!.isEmpty())
    }

    @Test fun orphanedPhotosAreCleanedOnStartup() = runBlocking {
        val name = photos.importPhoto(Uri.fromFile(source), PhotoDirectory.REPORTS)
        repository.initialize()
        assertFalse(photos.file(PhotoDirectory.REPORTS, name).exists())
    }

    @Test fun tableViewerAndPhotoNamesRejectPathOrSqlInjection() = runBlocking {
        try { repository.inspect("health_profile; DROP TABLE health_profile"); fail() } catch (_: IllegalArgumentException) { }
        assertThrows(IllegalArgumentException::class.java) { photos.file(PhotoDirectory.REPORTS, "../../outside.jpg") }
        assertEquals(0L, repository.inspect("health_profile").count)
    }

    @Test fun noConsentDoesNotSaveDataOrPhotos() = runBlocking {
        repository.initialize()
        try {
            repository.addPlogging(WasteQuantity.MUCH, Coordinates(0.0, 0.0), Uri.fromFile(source), false)
            fail()
        } catch (_: IllegalArgumentException) { }
        assertEquals(0L, repository.inspect("plogging_entries").count)
        assertTrue(photos.directory(PhotoDirectory.PLOGGING).listFiles()!!.isEmpty())
    }
}
