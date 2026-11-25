package com.pacefit.ai

import android.app.Application
import androidx.room.Room
import com.pacefit.ai.data.HealthDatabase
import com.pacefit.ai.data.HealthRepository
import com.pacefit.ai.data.RoomHealthRepository
import com.pacefit.ai.data.LocalRepository
import com.pacefit.ai.data.PrivatePhotoStore
import com.pacefit.ai.data.ActivityRepository
import com.pacefit.ai.data.RemoteRepository
import kotlinx.coroutines.*

open class PaceFitApplication : Application() {
    open val databaseName get() = DATABASE_NAME
    open val dataRoot get() = java.io.File(filesDir, "pacefit")
    val database: HealthDatabase by lazy {
        Room.databaseBuilder(this, HealthDatabase::class.java, databaseName)
            .addMigrations(HealthDatabase.MIGRATION_1_2, HealthDatabase.MIGRATION_2_3)
            .build()
    }
    val healthRepository: HealthRepository by lazy {
        RoomHealthRepository(database.profileDao(), localRepository) { initialization.join() }
    }
    val localRepository: LocalRepository by lazy {
        LocalRepository(database, PrivatePhotoStore(this, dataRoot), getDatabasePath(databaseName))
    }
    val activityRepository by lazy { ActivityRepository(database, localRepository) }
    val remoteRepository by lazy { RemoteRepository(this, database, localRepository) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal fun recordInterruption(id: String, extraMillis: Long) {
        scope.launch { runCatching { activityRepository.change(id, "INTERRUPTED", extraMillis) } }
    }
    lateinit var initialization: Job
        private set
    var accountRecoveryError: Boolean = false
        private set
    override fun onCreate() {
        super.onCreate()
        initialization = scope.launch {
            // Complete acknowledged erasure BEFORE profile drafts can be restored or edited.
            runCatching { remoteRepository.restore() }.onFailure { accountRecoveryError = true }
            runCatching {
                PrivatePhotoStore(this@PaceFitApplication, java.io.File(cacheDir, "ai-preview")).clearPhotos()
                localRepository.initialize()
                activityRepository.recoverInterrupted() // Never silently resumes GPS after process death.
            }
        }
    }

    companion object { const val DATABASE_NAME = "pacefit-health.db" }
}
