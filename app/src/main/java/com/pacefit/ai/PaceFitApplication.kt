package com.pacefit.ai

import android.app.Application
import androidx.room.Room
import com.pacefit.ai.data.HealthDatabase
import com.pacefit.ai.data.HealthRepository
import com.pacefit.ai.data.RoomHealthRepository
import com.pacefit.ai.data.LocalRepository
import com.pacefit.ai.data.PrivatePhotoStore

class PaceFitApplication : Application() {
    val database: HealthDatabase by lazy {
        Room.databaseBuilder(this, HealthDatabase::class.java, DATABASE_NAME)
            .addMigrations(HealthDatabase.MIGRATION_1_2)
            .build()
    }
    val healthRepository: HealthRepository by lazy { RoomHealthRepository(database.profileDao(), localRepository) }
    val localRepository: LocalRepository by lazy {
        LocalRepository(database, PrivatePhotoStore(this), getDatabasePath(DATABASE_NAME))
    }

    companion object { const val DATABASE_NAME = "pacefit-health.db" }
}
