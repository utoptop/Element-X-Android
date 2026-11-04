/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.pushproviders.api.Config
import io.element.android.libraries.pushproviders.api.Distributor
import io.element.android.libraries.pushproviders.api.PushProvider
import io.element.android.libraries.pushproviders.api.PusherSubscriber
import io.element.android.libraries.pushproviders.unifiedpush.DefaultPushGatewayHttpUrlProvider
import timber.log.Timber
import java.util.UUID

private val loggerTag = LoggerTag("NtfyPushProvider", LoggerTag.PushLoggerTag)

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class NtfyPushProvider(
    private val ntfyStore: NtfyStore,
    private val ntfySubscriber: NtfySubscriber,
    private val pusherSubscriber: PusherSubscriber,
    private val defaultPushGatewayHttpUrlProvider: DefaultPushGatewayHttpUrlProvider,
) : PushProvider {
    override val index: Int = NtfyConfig.INDEX
    override val name: String = NtfyConfig.NAME
    override val supportMultipleDistributors: Boolean = false

    override fun getDistributors(): List<Distributor> {
        return listOf(Distributor(NtfyConfig.DISTRIBUTOR_VALUE, NtfyConfig.DISTRIBUTOR_NAME))
    }

    override suspend fun registerWith(matrixClient: MatrixClient, distributor: Distributor): Result<Unit> {
        Timber.tag(loggerTag.value).d("Registering session ${matrixClient.sessionId} with embedded ntfy")
        val sessionId = matrixClient.sessionId

        var topic = ntfyStore.getTopic(sessionId)
        if (topic.isNullOrEmpty()) {
            topic = "up" + UUID.randomUUID().toString().replace("-", "").take(16)
            ntfyStore.setTopic(sessionId, topic)
        }

        val serverUrl = ntfyStore.getServerUrl().trim().trimEnd('/')
        val endpointUrl = "$serverUrl/$topic?up=1"
        ntfyStore.setEndpoint(sessionId, endpointUrl)

        val gateway = defaultPushGatewayHttpUrlProvider.provide()
        ntfyStore.setGateway(sessionId, gateway)

        val registerResult = pusherSubscriber.registerPusher(matrixClient, endpointUrl, gateway)
        return registerResult
            .onSuccess {
                ntfySubscriber.subscribe(topic, sessionId)
                Timber.tag(loggerTag.value).i("Successfully registered pusher for topic $topic with gateway $gateway")
            }
            .onFailure {
                Timber.tag(loggerTag.value).e(it, "Failed to register pusher with matrix homeserver")
            }
    }

    override suspend fun getCurrentDistributorValue(sessionId: SessionId): String? {
        return if (ntfyStore.getTopic(sessionId) != null) NtfyConfig.DISTRIBUTOR_VALUE else null
    }

    override suspend fun getCurrentDistributor(sessionId: SessionId): Distributor? {
        return if (ntfyStore.getTopic(sessionId) != null) {
            Distributor(NtfyConfig.DISTRIBUTOR_VALUE, NtfyConfig.DISTRIBUTOR_NAME)
        } else {
            null
        }
    }

    override suspend fun unregister(matrixClient: MatrixClient): Result<Unit> {
        val sessionId = matrixClient.sessionId
        Timber.tag(loggerTag.value).d("Unregistering session ${sessionId.value} from embedded ntfy")
        val topic = ntfyStore.getTopic(sessionId)
        if (topic != null) {
            ntfySubscriber.unsubscribe(topic)
        }
        val endpoint = ntfyStore.getEndpoint(sessionId)
        val gateway = ntfyStore.getGateway(sessionId) ?: defaultPushGatewayHttpUrlProvider.provide()
        val result = if (endpoint != null) {
            pusherSubscriber.unregisterPusher(matrixClient, endpoint, gateway)
        } else {
            Result.success(Unit)
        }
        ntfyStore.clear(sessionId)
        return result
    }

    override suspend fun onSessionDeleted(sessionId: SessionId) {
        Timber.tag(loggerTag.value).d("onSessionDeleted: ${sessionId.value}")
        val topic = ntfyStore.getTopic(sessionId)
        if (topic != null) {
            ntfySubscriber.unsubscribe(topic)
        }
        ntfyStore.clear(sessionId)
    }

    override suspend fun getPushConfig(sessionId: SessionId): Config? {
        val endpoint = ntfyStore.getEndpoint(sessionId) ?: return null
        val gateway = ntfyStore.getGateway(sessionId) ?: defaultPushGatewayHttpUrlProvider.provide()
        return Config(
            url = gateway,
            pushKey = endpoint,
        )
    }

    override fun canRotateToken(): Boolean = true

    override suspend fun rotateToken(): Result<Unit> {
        return Result.success(Unit)
    }
}
