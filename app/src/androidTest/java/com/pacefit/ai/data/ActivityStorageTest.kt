package com.pacefit.ai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.pacefit.ai.domain.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "activity-test-${UUID.randomUUID()}.db"
    private val root = File(context.filesDir, "activity-test-${UUID.randomUUID()}")
    private lateinit var db: HealthDatabase
    private lateinit var local: LocalRepository
    private lateinit var activity: ActivityRepository
    private fun open() {
        db = Room.databaseBuilder(context, HealthDatabase::class.java, name).build()
        local = LocalRepository(db, PrivatePhotoStore(context, root), context.getDatabasePath(name))
        activity = ActivityRepository(db, local)
    }
    @Before fun prepare() = open()
    @After fun clean() { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    @Test fun routeAndExcludedVehicleSegmentsSurviveReopen() = runBlocking {
        val session = activity.start()
        activity.point(RoutePointEntity(sessionId = session.id, timeMillis = 1, latitude = -16.5, longitude = -68.1,
            accuracy = 5.0, speedKmh = 36.0, altitudeMeters = 3600.0, pressureHpa = 650.0), 5000)
        activity.segment(session.id, DetectedSegment(1, 30001, 300.0, 36.0, 0, ActivityHint.VEHICLE, TransportClass.VEHICLE))
        activity.change(session.id, "FINISHED", 1000)
        db.close(); open()
        Assert.assertEquals(6000, activity.sessions.first().single().durationMillis)
        Assert.assertEquals(3600.0, activity.points(session.id).first().single().altitudeMeters!!, 0.1)
        Assert.assertFalse(activity.segments(session.id).first().single().includedInMetrics)
    }
    @Test fun pausedAndFinishedSessionsRejectLateGpsWrites() = runBlocking {
        val session = activity.start()
        val point = RoutePointEntity(sessionId = session.id, timeMillis = 1, latitude = 0.0, longitude = 0.0,
            accuracy = 5.0, speedKmh = 6.0, altitudeMeters = null, pressureHpa = null)
        activity.change(session.id, "PAUSED", 5000)
        activity.point(point, 5000)
        Assert.assertTrue(activity.points(session.id).first().isEmpty())
        activity.change(session.id, "FINISHED")
        activity.point(point, 5000)
        Assert.assertTrue(activity.points(session.id).first().isEmpty())
        Assert.assertEquals(5000, activity.sessions.first().single().durationMillis)
    }
    @Test fun processRecoveryDoesNotAutoRestartGpsAndEraseCascadesRouteTables() = runBlocking {
        val session = activity.start()
        activity.point(RoutePointEntity(sessionId = session.id, timeMillis = 1, latitude = 0.0, longitude = 0.0,
            accuracy = 5.0, speedKmh = 6.0, altitudeMeters = null, pressureHpa = null), 1000)
        activity.recoverInterrupted()
        Assert.assertEquals("INTERRUPTED", activity.unfinished()!!.state)
        try { activity.start(); Assert.fail("Only one open activity allowed") } catch (_: IllegalStateException) {}
        local.eraseAll()
        Assert.assertTrue(activity.sessions.first().isEmpty())
        Assert.assertEquals(0, local.inspect("route_points").count)
        Assert.assertEquals(0, local.inspect("session_segments").count)
    }
}
