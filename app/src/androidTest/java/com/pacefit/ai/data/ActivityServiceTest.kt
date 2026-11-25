package com.pacefit.ai.data

import android.Manifest
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pacefit.ai.MainActivity
import com.pacefit.ai.PaceFitApplication
import com.pacefit.ai.domain.ActivityHint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Simulated provider only: proves FGS/background lifecycle, NOT real GPS field accuracy. */
@RunWith(AndroidJUnit4::class)
class ActivityServiceTest {
    @Suppress("DEPRECATION")
    @Test fun foregroundServiceRecordsWithScreenOffAndStopsOnFinish() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PaceFitApplication>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) { automation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        } }
        shell("pm grant ${app.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        shell("pm grant ${app.packageName} ${Manifest.permission.ACCESS_COARSE_LOCATION}")
        if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        shell("appops set ${app.packageName} android:mock_location allow")
        val manager = app.getSystemService(LocationManager::class.java)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            app.initialization.join()
            app.localRepository.eraseAll()
            app.localRepository.saveWindow(15)
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            scenario.onActivity { ActivityRecording.command(it, "START", ActivityHint.RUN) }
            val recording = withTimeout(20000) { ActivityRecording.state.first { it.running } }
            shell("input keyevent 3") // Real HOME transition rather than a forced lifecycle state.
            shell("input keyevent 223") // sleep: FGS must remain owner when UI stops.
            repeat(6) { index ->
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = -16.5 + index * 0.0001; longitude = -68.15; accuracy = 5f
                    speed = 3f; altitude = 3600.0; time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                })
                delay(5100)
            }
            val id = recording.sessionId!!
            val points = app.activityRepository.points(id).first()
            assertTrue("FGS must persist GPS while screen is off", points.size >= 4)
            assertTrue(app.activityRepository.segments(id).first().any { it.classification == "ACTIVE" })
            ActivityRecording.command(app, "PAUSE")
            withTimeout(10000) { ActivityRecording.state.first { !it.running } }
            val paused = app.activityRepository.unfinished()!!
            delay(1100)
            ActivityRecording.command(app, "PAUSE") // duplicate pause must not count pause time.
            delay(1100)
            assertEquals(paused.durationMillis, app.activityRepository.unfinished()!!.durationMillis)
            ActivityRecording.command(app, "FINISH")
            withTimeout(10000) { app.activityRepository.sessions.first { it.firstOrNull()?.endedAt != null } }
            assertFalse(ActivityRecording.state.value.running)
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            if (app.activityRepository.unfinished() != null) ActivityRecording.command(app, "FINISH")
            withTimeout(10000) { app.activityRepository.sessions.first { rows -> rows.none { it.endedAt == null } } }
            runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            shell("appops set ${app.packageName} android:mock_location default")
            scenario.close()
            app.localRepository.eraseAll()
        }
    }
}
