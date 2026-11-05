/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.data.tryOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Receiver triggered on device boot or app update to restore the embedded ntfy push connection.
 */
class NtfyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.d("NtfyBootReceiver received action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == "android.intent.action.REBOOT" ||
            action == ACTION_RESTART_NTFY
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val restored = tryOrNull {
                        val bindings = context.bindings<NtfyBindings>()
                        val store = bindings.ntfyStore()
                        val topics = store.getAllTopics()
                        if (topics.isNotEmpty()) {
                            Timber.i("Restoring NtfySubscriber on action: $action")
                            bindings.ntfySubscriber().restart()
                            true
                        } else {
                            false
                        }
                    } ?: false

                    if (!restored) {
                        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        val hasTopics = prefs.all.keys.any { it.startsWith("ntfy_topic_") }
                        if (hasTopics) {
                            Timber.i("Restoring NtfyKeepAliveService on action (fallback): $action")
                            NtfyKeepAliveService.start(context)
                        }
                    }
                } catch (e: Throwable) {
                    Timber.e(e, "Error restoring ntfy on action: $action")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_RESTART_NTFY = "io.element.android.ntfy.action.RESTART"
    }
}
