/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.test.battery

import io.element.android.libraries.push.api.battery.BatteryOptimization

class FakeBatteryOptimization(
    var isIgnoringBatteryOptimizationsResult: Boolean = true,
    private val requestDisablingBatteryOptimizationResult: () -> Boolean = { true },
    var isAccessibilityServiceEnabledResult: Boolean = false,
    private val openAccessibilitySettingsResult: () -> Boolean = { true },
) : BatteryOptimization {
    override fun isIgnoringBatteryOptimizations(): Boolean {
        return isIgnoringBatteryOptimizationsResult
    }

    override fun requestDisablingBatteryOptimization(): Boolean {
        return requestDisablingBatteryOptimizationResult()
    }

    override fun isAccessibilityServiceEnabled(): Boolean {
        return isAccessibilityServiceEnabledResult
    }

    override fun openAccessibilitySettings(): Boolean {
        return openAccessibilitySettingsResult()
    }
}
