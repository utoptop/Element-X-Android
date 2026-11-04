/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * Accessibility Service providing OS-level auto-restart and keep-alive protection.
 * When enabled in System Settings -> Accessibility, Android OS framework's AccessibilityManagerService
 * keeps the process alive and automatically relaunches it even if swiped from Recent Tasks.
 */
class NtfyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("NtfyAccessibilityService connected: OS-level keep-alive active")
        NtfyKeepAliveService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: privacy-first, does not monitor any user events or screen content
    }

    override fun onInterrupt() {
        Timber.d("NtfyAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("NtfyAccessibilityService destroyed")
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            return try {
                val expectedComponentName = ComponentName(context, NtfyAccessibilityService::class.java)
                val enabledServicesSetting = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)
                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null && enabledComponent == expectedComponentName) {
                        return true
                    }
                }
                false
            } catch (e: Exception) {
                Timber.e(e, "Error checking accessibility service status")
                false
            }
        }
    }
}
