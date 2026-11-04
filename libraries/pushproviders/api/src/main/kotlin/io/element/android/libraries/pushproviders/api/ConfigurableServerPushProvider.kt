/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.api

/**
 * Interface implemented by push providers or stores that connect to a configurable server
 * and may require user credentials (e.g. built-in ntfy push provider).
 */
interface ConfigurableServerPushProvider {
    /**
     * User-facing title for server configuration, e.g. "ntfy 服务器设置".
     */
    val serverConfigTitle: String

    /**
     * The configured server URL (e.g. "https://ntfy.sh").
     */
    fun getServerUrl(): String

    /**
     * Updates the server URL.
     */
    suspend fun setServerUrl(url: String)

    /**
     * The configured username / ID, or null if unauthenticated.
     */
    fun getUsername(): String?

    /**
     * Updates the username / ID.
     */
    suspend fun setUsername(username: String?)

    /**
     * The configured password, or null if unauthenticated.
     */
    fun getPassword(): String?

    /**
     * Updates the password.
     */
    suspend fun setPassword(password: String?)
}
