package com.pacefit.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "-test-${UUID.randomUUID()}"
    private val store = SessionStore(context, namespace)
    private val file = File(context.noBackupFilesDir, "pacefit-session$namespace.enc")
    @After fun clean() {
        store.clear()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("pacefit.account.session.v1$namespace") }
    }
    @Test fun encryptedSessionPersistsWithoutPlaintextTokens() {
        val session = AccountSession("test-user", "test@example.test", "SECRET_ACCESS_TEST", "SECRET_REFRESH_TEST", 999999999)
        store.write(session)
        Assert.assertEquals(session, SessionStore(context, namespace).read())
        Assert.assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("SECRET_"))
        store.clear(); Assert.assertNull(store.read())
    }
    @Test fun corruptCiphertextIsDiscardedInsteadOfTrustingSession() {
        store.write(AccountSession("user", "test@example.test", "access", "refresh", 1))
        file.writeBytes(ByteArray(20))
        Assert.assertNull(store.read())
        Assert.assertFalse(file.exists())
    }
}
