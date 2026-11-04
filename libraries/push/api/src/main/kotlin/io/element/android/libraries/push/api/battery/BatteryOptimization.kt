/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.api.battery

interface BatteryOptimization {
    /**
     * Tells if the application ignores battery optimizations.
     *
     * @return true if battery optimisations are ignored
     */
    fun isIgnoringBatteryOptimizations(): Boolean

    /**
     * Request the user to disable battery optimizations for this app.
     *
     * @return true if the intent was successfully started, false if the activity was not found
     */
    fun requestDisablingBatteryOptimization(): Boolean

    /**
     * Tells if the accessibility keep-alive service is enabled.
     */
    fun isAccessibilityServiceEnabled(): Boolean

    /**
     * Open system accessibility settings.
     */
    fun openAccessibilitySettings(): Boolean
}
