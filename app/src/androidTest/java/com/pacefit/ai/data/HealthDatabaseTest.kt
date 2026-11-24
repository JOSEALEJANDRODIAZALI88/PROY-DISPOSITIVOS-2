package com.pacefit.ai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthDatabaseTest {
    private lateinit var database: HealthDatabase

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), HealthDatabase::class.java
        ).build()
    }

    @After fun close() { database.close() }

    @Test fun createsEditsAndDeletesSingleProfile() = runBlocking {
        val dao = database.profileDao()
        assertNull(dao.observe().first())
        val original = HealthProfileEntity(weightKg = 70.5, heightCm = 175.0, age = 25, sex = "FEMALE", updatedAt = 10)
        dao.save(original)
        assertEquals(original, dao.observe().first())
        dao.save(original.copy(weightKg = 71.0, updatedAt = 20))
        assertEquals(71.0, dao.observe().first()!!.weightKg, 0.001)
        dao.delete()
        assertNull(dao.observe().first())
    }

    @Test fun diskDatabasePersistsAfterReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "profile-persistence-test.db"
        context.deleteDatabase(name)
        val original = HealthProfileEntity(weightKg = 65.0, heightCm = 168.0, age = 30, sex = "UNSPECIFIED", updatedAt = 15)
        try {
            Room.databaseBuilder(context, HealthDatabase::class.java, name).build().let { first ->
                try { first.profileDao().save(original) } finally { first.close() }
            }
            Room.databaseBuilder(context, HealthDatabase::class.java, name).build().let { reopened ->
                try { assertEquals(original, reopened.profileDao().observe().first()) } finally { reopened.close() }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
