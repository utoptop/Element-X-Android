/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.pushproviders.api.ConfigurableServerPushProvider

interface NtfyStore : ConfigurableServerPushProvider {
    fun getTopic(sessionId: SessionId): String?
    fun setTopic(sessionId: SessionId, topic: String?)
    fun getEndpoint(sessionId: SessionId): String?
    fun setEndpoint(sessionId: SessionId, endpoint: String?)
    fun getLastRegisteredEndpoint(sessionId: SessionId): String?
    fun setLastRegisteredEndpoint(sessionId: SessionId, endpoint: String?)
    fun getGateway(sessionId: SessionId): String?
    fun setGateway(sessionId: SessionId, gateway: String?)
    fun getAllTopics(): Map<String, SessionId>
    fun clear(sessionId: SessionId)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NtfyStore>())
@ContributesBinding(AppScope::class, binding = binding<ConfigurableServerPushProvider>())
class SharedPreferencesNtfyStore(
    @ApplicationContext val context: Context,
    private val sharedPreferences: SharedPreferences,
) : NtfyStore {
    override val serverConfigTitle: String = "ntfy"

    override fun getServerUrl(): String {
        return sharedPreferences.getString(PREFS_SERVER_URL, NtfyConfig.DEFAULT_SERVER_URL)
            ?.filter { it.code in 33..126 }
            ?.takeIf { it.isNotBlank() } ?: NtfyConfig.DEFAULT_SERVER_URL
    }

    override suspend fun setServerUrl(url: String) {
        val sanitized = url.filter { it.code in 33..126 }.trimEnd('/')
        sharedPreferences.edit {
            putString(PREFS_SERVER_URL, sanitized)
        }
    }

    override fun getUsername(): String? {
        return sharedPreferences.getString(PREFS_USERNAME, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun setUsername(username: String?) {
        sharedPreferences.edit {
            putString(PREFS_USERNAME, username?.trim()?.ifBlank { null })
        }
    }

    override fun getPassword(): String? {
        return sharedPreferences.getString(PREFS_PASSWORD, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun setPassword(password: String?) {
        sharedPreferences.edit {
            putString(PREFS_PASSWORD, password?.ifBlank { null })
        }
    }

    override fun getTopic(sessionId: SessionId): String? {
        val topic = sharedPreferences.getString(PREFS_TOPIC_PREFIX + sessionId.value, null)
        if (topic != null && topic.length != 14) {
            val newTopic = "up" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            setTopic(sessionId, newTopic)
            return newTopic
        }
        return topic
    }

    override fun setTopic(sessionId: SessionId, topic: String?) {
        sharedPreferences.edit {
            if (topic == null) {
                remove(PREFS_TOPIC_PREFIX + sessionId.value)
            } else {
                putString(PREFS_TOPIC_PREFIX + sessionId.value, topic)
            }
        }
    }

    override fun getEndpoint(sessionId: SessionId): String? {
        val topic = getTopic(sessionId) ?: return null
        val serverUrl = getServerUrl().trim().trimEnd('/')
        return "$serverUrl/$topic?up=1"
    }

    override fun setEndpoint(sessionId: SessionId, endpoint: String?) {
        setLastRegisteredEndpoint(sessionId, endpoint)
    }

    override fun getLastRegisteredEndpoint(sessionId: SessionId): String? {
        return sharedPreferences.getString(PREFS_ENDPOINT_PREFIX + sessionId.value, null)
            ?.filter { it.code in 33..126 }
    }

    override fun setLastRegisteredEndpoint(sessionId: SessionId, endpoint: String?) {
        sharedPreferences.edit {
            if (endpoint == null) {
                remove(PREFS_ENDPOINT_PREFIX + sessionId.value)
            } else {
                putString(PREFS_ENDPOINT_PREFIX + sessionId.value, endpoint.filter { it.code in 33..126 })
            }
        }
    }

    override fun getGateway(sessionId: SessionId): String? {
        return sharedPreferences.getString(PREFS_GATEWAY_PREFIX + sessionId.value, null)
    }

    override fun setGateway(sessionId: SessionId, gateway: String?) {
        sharedPreferences.edit {
            if (gateway == null) {
                remove(PREFS_GATEWAY_PREFIX + sessionId.value)
            } else {
                putString(PREFS_GATEWAY_PREFIX + sessionId.value, gateway)
            }
        }
    }

    override fun getAllTopics(): Map<String, SessionId> {
        val all = sharedPreferences.all
        val topics = mutableMapOf<String, SessionId>()
        for ((key, value) in all) {
            if (key.startsWith(PREFS_TOPIC_PREFIX) && value is String && value.isNotBlank()) {
                val sessionRaw = key.removePrefix(PREFS_TOPIC_PREFIX)
                val sessionId = SessionId(sessionRaw)
                val validTopic = if (value.length == 14) {
                    value
                } else {
                    val healed = "up" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
                    setTopic(sessionId, healed)
                    healed
                }
                topics[validTopic] = sessionId
            }
        }
        return topics
    }

    override fun clear(sessionId: SessionId) {
        sharedPreferences.edit {
            remove(PREFS_TOPIC_PREFIX + sessionId.value)
            remove(PREFS_ENDPOINT_PREFIX + sessionId.value)
            remove(PREFS_GATEWAY_PREFIX + sessionId.value)
        }
    }

    override fun isKeepAliveEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREFS_KEEP_ALIVE_ENABLED, true)
    }

    override suspend fun setKeepAliveEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(PREFS_KEEP_ALIVE_ENABLED, enabled)
        }
        if (enabled) {
            NtfyKeepAliveService.start(context)
        } else {
            NtfyKeepAliveService.stop(context)
        }
    }

    companion object {
        private const val PREFS_SERVER_URL = "ntfy_server_url"
        private const val PREFS_USERNAME = "ntfy_username"
        private const val PREFS_PASSWORD = "ntfy_password"
        private const val PREFS_TOPIC_PREFIX = "ntfy_topic_"
        private const val PREFS_ENDPOINT_PREFIX = "ntfy_endpoint_"
        private const val PREFS_GATEWAY_PREFIX = "ntfy_gateway_"
        private const val PREFS_KEEP_ALIVE_ENABLED = "ntfy_keepalive_enabled"
    }
}
