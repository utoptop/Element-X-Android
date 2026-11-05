/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.CommonDrawables
import timber.log.Timber

/**
 * Persistent Foreground Service ensuring the built-in ntfy WebSocket client stays alive in the background
 * and avoids being killed by aggressive battery optimizations on devices like Motorola, Xiaomi, etc.
 */
class NtfyKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannelExists()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("Task removed from Recents, scheduling restart via AlarmManager")
        try {
            val restartIntent = Intent(applicationContext, NtfyBootReceiver::class.java).apply {
                action = NtfyBootReceiver.ACTION_RESTART_NTFY
                setPackage(packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canScheduleExact = alarmManager?.canScheduleExactAlarms() == true
                if (canScheduleExact) {
                    alarmManager?.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000,
                        pendingIntent
                    )
                } else {
                    alarmManager?.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000,
                        pendingIntent
                    )
                }
            } else {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule restart on task removed")
        }
    }

    private fun ensureNotificationChannelExists() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(io.element.android.libraries.pushproviders.unifiedpush.R.string.ntfy_service_channel_name))
            .setDescription(getString(io.element.android.libraries.pushproviders.unifiedpush.R.string.ntfy_service_channel_description))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun startInForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(getString(io.element.android.libraries.pushproviders.unifiedpush.R.string.ntfy_service_notification_title))
            .setContentText(getString(io.element.android.libraries.pushproviders.unifiedpush.R.string.ntfy_service_notification_content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setSound(null)
            .setVibrate(longArrayOf(0))
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            Timber.d("NtfyKeepAliveService started in foreground")
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Failed to start NtfyKeepAliveService in foreground")
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "ntfy_keepalive_service_channel"

        fun start(context: Context) {
            runCatchingExceptions {
                val intent = Intent(context, NtfyKeepAliveService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Timber.e(it, "Unable to start NtfyKeepAliveService")
            }
        }

        fun stop(context: Context) {
            runCatchingExceptions {
                val intent = Intent(context, NtfyKeepAliveService::class.java)
                context.stopService(intent)
            }.onFailure {
                Timber.e(it, "Unable to stop NtfyKeepAliveService")
            }
        }
    }
}
