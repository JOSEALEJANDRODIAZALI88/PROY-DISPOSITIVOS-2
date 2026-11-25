package com.pacefit.ai

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pacefit.ai.data.HealthDatabase
import com.pacefit.ai.data.HealthRepository
import com.pacefit.ai.data.RoomHealthRepository

class PaceFitApplication : Application() {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pace_parameters (`key` TEXT NOT NULL, label TEXT NOT NULL, value REAL NOT NULL, unit TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS water_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, milliliters INTEGER NOT NULL, loggedAt INTEGER NOT NULL)")
        }
    }
    val database: HealthDatabase by lazy {
        Room.databaseBuilder(this, HealthDatabase::class.java, "pacefit-health.db").addMigrations(MIGRATION_1_2).build()
    }
    val healthRepository: HealthRepository by lazy { RoomHealthRepository(database.profileDao()) }
}
