/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import kotlinx.serialization.Serializable

@Serializable
data class NtfyMessageResponse(
    val id: String? = null,
    val time: Long? = null,
    val event: String? = null,
    val topic: String? = null,
    val message: String? = null,
    val title: String? = null,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val click: String? = null,
    val encoding: String? = null,
)
