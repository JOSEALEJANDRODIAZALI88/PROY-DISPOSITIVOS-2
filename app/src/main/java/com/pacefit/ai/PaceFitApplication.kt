package com.pacefit.ai

import android.app.Application
import androidx.room.Room
import com.pacefit.ai.data.HealthDatabase
import com.pacefit.ai.data.HealthRepository
import com.pacefit.ai.data.RoomHealthRepository

class PaceFitApplication : Application() {
    val database: HealthDatabase by lazy {
        Room.databaseBuilder(this, HealthDatabase::class.java, "pacefit-health.db").build()
    }
    val healthRepository: HealthRepository by lazy { RoomHealthRepository(database.profileDao()) }
}
