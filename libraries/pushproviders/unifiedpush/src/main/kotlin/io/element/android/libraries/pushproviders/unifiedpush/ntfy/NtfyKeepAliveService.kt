/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

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
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.CommonDrawables
import timber.log.Timber

/**
 * Persistent Foreground Service ensuring the built-in ntfy WebSocket client stays alive in the background
 * and avoids being killed by aggressive battery optimizations on devices like Motorola, Xiaomi, etc.
 */
class NtfyKeepAliveService : Service() {
    private var isOnForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannelExists()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isOnForeground) {
            startInForeground()
        }
        if (!isOnForeground) {
            Timber.w("NtfyKeepAliveService is not running in foreground, stopping it to avoid crash")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("Task removed from Recents")
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

        runCatchingExceptions {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        }
            .onSuccess {
                isOnForeground = true
                Timber.d("NtfyKeepAliveService started in foreground")
            }
            .onFailure {
                isOnForeground = false
                Timber.e(it, "Failed to start NtfyKeepAliveService in foreground, stopping self to avoid crash")
                stopSelf()
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
