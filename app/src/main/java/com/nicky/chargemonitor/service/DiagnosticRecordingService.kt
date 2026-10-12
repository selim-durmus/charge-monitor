package com.nicky.chargemonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nicky.chargemonitor.ChargeMonitorApplication
import com.nicky.chargemonitor.MainActivity
import com.nicky.chargemonitor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class DiagnosticRecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var chargeMonitorApplication: ChargeMonitorApplication
    private var samplingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        chargeMonitorApplication = application as ChargeMonitorApplication
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        acquireWakeLock()
        chargeMonitorApplication.extendedRecordingController.prepareToStart()
        if (samplingJob?.isActive != true) {
            samplingJob = serviceScope.launch { samplingLoop() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val app = chargeMonitorApplication
        val job = samplingJob
        samplingJob = null
        // The sampling loop may be between read() and record() right now. Finishing the
        // session synchronously here could let that in-flight record() open a phantom
        // session that never ends, so wait for the loop to settle first (off the main thread).
        app.applicationScope.launch {
            job?.cancelAndJoin()
            if (!app.extendedRecordingController.status.value.dashboardSamplingActive) {
                app.sessionRecorder.onForegroundStopped(System.currentTimeMillis())
            }
            app.extendedRecordingController.stopped()
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun samplingLoop() {
        while (currentCoroutineContext().isActive) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val reading = chargeMonitorApplication.batteryMonitor.read()
            val accessory = chargeMonitorApplication.accessoryProfileStore.profileFor(reading)
            currentCoroutineContext().ensureActive()
            chargeMonitorApplication.sessionRecorder.record(
                reading = reading,
                timestampMillis = System.currentTimeMillis(),
                accessoryProfile = accessory,
            )
            chargeMonitorApplication.extendedRecordingController.publish(reading)
            updateNotification(reading)
            if (!reading.isPowerConnected) {
                stopSelf()
                return
            }
            val duration = android.os.SystemClock.elapsedRealtime() - startedAt
            delay((SERVICE_SAMPLE_INTERVAL_MILLIS - duration).coerceAtLeast(50L))
        }
    }

    private fun startInForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun updateNotification(reading: com.nicky.chargemonitor.battery.BatteryReading) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(
                content = "${reading.powerSource.label} · " +
                    "${reading.powerWatts.notificationMetric(2, "W")} · " +
                    "${reading.temperatureCelsius.notificationMetric(1, "°C")} · " +
                    (reading.levelPercent?.let { "$it%" } ?: "—"),
            ),
        )
    }

    private fun buildNotification(
        content: String = "Preparing the screen-off diagnostic recorder…",
    ) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(NOTIFICATION_ACCENT)
        .setContentTitle("Charging diagnostic is recording")
        .setContentText(content)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "Stop",
            PendingIntent.getService(
                this,
                1,
                Intent(this, DiagnosticRecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:screenOffDiagnostic",
            )
            .apply { acquire(MAX_WAKE_LOCK_MILLIS) }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen-off charging diagnostics",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent status while a charging trace records with the screen off"
            },
        )
    }

    companion object {
        const val ACTION_START = "com.nicky.chargemonitor.action.START_DIAGNOSTIC"
        const val ACTION_STOP = "com.nicky.chargemonitor.action.STOP_DIAGNOSTIC"
        private const val NOTIFICATION_CHANNEL_ID = "screen_off_diagnostic"
        private const val NOTIFICATION_ID = 401
        private const val SERVICE_SAMPLE_INTERVAL_MILLIS = 1_000L
        private const val MAX_WAKE_LOCK_MILLIS = 12 * 60 * 60_000L
        private const val NOTIFICATION_ACCENT = 0xFF0B8A58.toInt()
    }
}

private fun Double?.notificationMetric(decimals: Int, unit: String): String =
    if (this == null || !isFinite()) {
        "— $unit"
    } else {
        String.format(Locale.US, "%.${decimals}f %s", this, unit)
    }
