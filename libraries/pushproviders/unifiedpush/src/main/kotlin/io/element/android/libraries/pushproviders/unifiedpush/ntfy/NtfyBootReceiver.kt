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
import timber.log.Timber

/**
 * Receiver triggered on device boot or app update to restore the embedded ntfy push connection.
 */
class NtfyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.d("NtfyBootReceiver received action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == ACTION_RESTART_NTFY
        ) {
            // Check if there are active ntfy topics configured
            val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            val hasTopics = prefs.all.keys.any { it.startsWith("ntfy_topic_") }
            if (hasTopics) {
                Timber.i("Restoring NtfyKeepAliveService on action: $action")
                NtfyKeepAliveService.start(context)
            }
        }
    }

    companion object {
        const val ACTION_RESTART_NTFY = "io.element.android.ntfy.action.RESTART"
    }
}
