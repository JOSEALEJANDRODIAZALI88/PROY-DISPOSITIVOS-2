package com.pacefit.ai.data

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pacefit.ai.PaceFitApplication
import com.pacefit.ai.domain.Coordinates
import com.pacefit.ai.domain.HealthProfile
import com.pacefit.ai.domain.ProfileSex
import com.pacefit.ai.domain.WasteQuantity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real HTTPS test. Fixture is injected over stdin, never packaged or logged. */
@RunWith(AndroidJUnit4::class)
class CloudConnectionTest {
    @Test fun authenticatedAndroidClientPublishesRestoresAndDeletesRealCloudAccount() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaceFitApplication
        val fixture = File(app.noBackupFilesDir, "pacefit-cloud-test-fixture.json")
        assumeTrue("Opt-in cloud test requires a disposable account from cloud-admin.py.", fixture.exists())
        check(app.databaseName != PaceFitApplication.DATABASE_NAME) { "Isolated test runner required." }
        app.initialization.join()
        val credentials = JSONObject(fixture.readText())
        val remote = app.remoteRepository
        val local = app.localRepository
        val source = File(app.cacheDir, "cloud-test-${UUID.randomUUID()}.jpg")
        try {
            local.eraseAll()
            remote.clearSession()
            assertTrue(remote.configured)
            val otp = credentials.getString("email_otp")
            val wrong = otp.dropLast(1) + ((otp.last() - '0' + 1) % 10)
            try {
                remote.verifyEmail(credentials.getString("email"), wrong)
                fail("Wrong OTP must not create a session.")
            } catch (expected: RemoteError) { assertTrue(expected.status in 400..499) }
            assertNull(remote.session.value)
            remote.verifyEmail(credentials.getString("email"), otp)
            assertEquals(credentials.getString("user_id"), remote.session.value?.userId)
            remote.clearSession()
            assertTrue(remote.signIn(credentials.getString("email"), credentials.getString("password"), false))
            assertEquals(credentials.getString("user_id"), remote.session.value?.userId)
            remote.acceptProfile("Prueba Android")
            app.healthRepository.save(HealthProfile(72.5, 175.0, 25, ProfileSex.UNSPECIFIED, System.currentTimeMillis()))
            val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            bitmap.recycle()
            val id = local.addPlogging(WasteQuantity.MEDIUM, Coordinates(-16.5, -68.15), source.toUri(), true)
            remote.publish(id, plogging = true, consent = true)
            val entry = remote.feed(0).single { it.id == id }
            assertEquals(10, entry.points)
            assertEquals(credentials.getString("user_id"), entry.ownerId)
            assertEquals(id, app.database.publicationDao().find(id)?.remoteId)
            val photoUrl = remote.signedPhoto(entry.photoPath)
            assertTrue(photoUrl.startsWith("https://"))
            val photo = withContext(Dispatchers.IO) { java.net.URL(photoUrl).openStream().use { it.readBytes() } }
            assertTrue(photo.size > 2)
            assertEquals(0xff, photo[0].toInt() and 0xff)
            val reopened = RemoteRepository(app, app.database, local)
            reopened.restore()
            assertEquals(credentials.getString("user_id"), reopened.session.value?.userId)
            assertTrue(reopened.feed(0).any { it.id == id })
            remote.deleteAccount("ELIMINAR")
            assertNull(remote.session.value)
            assertNull(app.healthRepository.observe().first())
            assertTrue(local.plogging.first().isEmpty())
            assertNull(app.database.publicationDao().find(id))
        } finally {
            source.delete()
            fixture.delete()
            remote.clearSession()
            local.eraseAll()
        }
    }
}
