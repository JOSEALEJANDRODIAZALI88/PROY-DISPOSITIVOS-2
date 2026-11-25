package com.pacefit.ai.data

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pacefit.ai.MainActivity
import com.pacefit.ai.PaceFitApplication
import com.pacefit.ai.R
import com.pacefit.ai.domain.ActivityHint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecordingState(val sessionId: String? = null, val running: Boolean = false,
    val speedKmh: Double? = null, val status: String = "Lista para salir", val error: String? = null)

object ActivityRecording {
    internal val mutable = MutableStateFlow(RecordingState())
    val state = mutable.asStateFlow()
    fun command(context: Context, action: String, hint: ActivityHint = ActivityHint.UNKNOWN) {
        val intent = Intent(context, ActivityRecordingService::class.java).setAction(action).putExtra("hint", hint.name)
        if (action == "START" || action == "RESUME") ContextCompat.startForegroundService(context, intent)
        else context.startService(intent)
    }
}

/** Started only by a visible, permitted UI. No auto boot/restart or background-location permission. */
class ActivityRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val app get() = application as PaceFitApplication
    private var session: ActivitySessionEntity? = null
    private var monitor: ForegroundMovementMonitor? = null
    private var hint = ActivityHint.UNKNOWN
    private var lastTick = 0L
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("activity", "Actividad en curso", NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            for (command in commands) {
                try { command() }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    monitor?.stop(); monitor = null; ticker?.cancel()
                    session?.let { runCatching { app.activityRepository.change(it.id, "INTERRUPTED") } }
                    ActivityRecording.mutable.value = RecordingState(session?.id, status = "Actividad interrumpida",
                        error = "No se pudo continuar. Revisa GPS, permisos o almacenamiento; la ruta guardada se conserva.")
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        if (action in setOf("START", "RESUME")) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                stopSelf(); return START_NOT_STICKY
            }
            try {
                ServiceCompat.startForeground(this, 401, notification("Preparando GPS…"),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            } catch (_: RuntimeException) {
                ActivityRecording.mutable.value = RecordingState(error = "Abre la app y concede ubicación precisa para iniciar.")
                stopSelf(); return START_NOT_STICKY
            }
        }
        val requestedHint = runCatching { ActivityHint.valueOf(intent.getStringExtra("hint") ?: "UNKNOWN") }.getOrDefault(ActivityHint.UNKNOWN)
        commands.trySend {
            when (action) {
                "START", "RESUME" -> {
                    hint = requestedHint
                    if (monitor == null) {
                        app.initialization.join()
                        session = if (action == "START") app.activityRepository.start()
                            else app.activityRepository.unfinished()?.let { app.activityRepository.change(it.id, "ACTIVE") }
                        val current = requireNotNull(session)
                        lastTick = SystemClock.elapsedRealtime()
                        monitor = ForegroundMovementMonitor(this, app.localRepository.windowSeconds(), { hint },
                            onReading = { speed, _ -> ActivityRecording.mutable.update { it.copy(speedKmh = speed) } },
                            onSegment = { value -> commands.trySend { app.activityRepository.segment(current.id, value) } },
                            onStatus = { value -> ActivityRecording.mutable.update { it.copy(status = value) } },
                            onFix = { location, speed, pressure ->
                                commands.trySend {
                                    if (monitor != null) app.activityRepository.point(RoutePointEntity(sessionId = current.id,
                                        timeMillis = location.time, latitude = location.latitude, longitude = location.longitude,
                                        accuracy = location.accuracy.toDouble(), speedKmh = speed,
                                        altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                                        pressureHpa = pressure), 0)
                                }
                            })
                        ActivityRecording.mutable.value = RecordingState(current.id, true, status = "Esperando GPS preciso…")
                        monitor!!.start()
                        ticker = scope.launch {
                            while (isActive) {
                                delay(5_000)
                                commands.send { if (monitor != null) persistTime("ACTIVE") }
                            }
                        }
                        notify("Grabando · GPS y transporte")
                    }
                }
                "HINT" -> hint = requestedHint
                "PAUSE" -> {
                    val wasRunning = monitor != null
                    monitor?.stop(); monitor = null; ticker?.cancel()
                    if (wasRunning) persistTime("PAUSED")
                    ActivityRecording.mutable.update { it.copy(running = false, speedKmh = null, status = "En pausa") }
                    notify("En pausa · continúa desde la app")
                }
                "FINISH" -> {
                    val wasRunning = monitor != null
                    monitor?.stop(); monitor = null; ticker?.cancel()
                    if (session == null) session = app.activityRepository.unfinished()
                    session?.let { session = app.activityRepository.change(it.id, "FINISHED",
                        if (wasRunning) elapsed() else 0) }
                    ActivityRecording.mutable.value = RecordingState(status = "Actividad guardada")
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun elapsed(): Long {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastTick).coerceAtLeast(0)
        lastTick = now
        return elapsed
    }
    private suspend fun persistTime(state: String) {
        session?.let { session = app.activityRepository.change(it.id, state, elapsed()) }
    }
    private fun notify(text: String) = getSystemService(NotificationManager::class.java).notify(401, notification(text))
    private fun notification(text: String): Notification {
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
            Intent(this, ActivityRecordingService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "activity").setSmallIcon(R.drawable.ic_pacefit)
            .setContentTitle("PaceFit · Tu actividad").setContentText(text).setContentIntent(open).setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Pausar", action("PAUSE")).addAction(0, "Finalizar", action("FINISH")).build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        monitor?.stop()
        ticker?.cancel()
        if (ActivityRecording.state.value.running) {
            ActivityRecording.mutable.update { it.copy(running = false, speedKmh = null, status = "Actividad interrumpida; continúa desde la app.") }
            session?.let { app.recordInterruption(it.id, elapsed()) }
        }
        scope.cancel()
        super.onDestroy()
    }
}
