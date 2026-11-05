/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.battery

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.push.api.battery.BatteryOptimization
import io.element.android.services.toolbox.api.intent.ExternalIntentLauncher
import timber.log.Timber

@ContributesBinding(AppScope::class)
class AndroidBatteryOptimization(
    @ApplicationContext
    private val context: Context,
    private val externalIntentLauncher: ExternalIntentLauncher,
) : BatteryOptimization {
    override fun isIgnoringBatteryOptimizations(): Boolean {
        return context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    @SuppressLint("BatteryLife")
    override fun requestDisablingBatteryOptimization(): Boolean {
        val ignoreBatteryOptimizationsResult = launchAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withData = true)
        if (ignoreBatteryOptimizationsResult) {
            return true
        }
        // Open settings as a fallback if the first attempt fails
        return launchAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withData = false)
    }

    override fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent.packageName == context.packageName) {
                    return true
                }
            }
            false
        } catch (exception: Exception) {
            Timber.w(exception, "Cannot check accessibility service status.")
            false
        }
    }

    override fun openAccessibilitySettings(): Boolean {
        return launchAction(Settings.ACTION_ACCESSIBILITY_SETTINGS, withData = false)
    }

    private fun launchAction(
        action: String,
        withData: Boolean,
    ): Boolean {
        val intent = Intent()
        intent.action = action
        if (withData) {
            intent.data = ("package:" + context.packageName).toUri()
        }
        return try {
            externalIntentLauncher.launch(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            Timber.w(exception, "Cannot launch intent with action $action.")
            false
        }
    }
}
