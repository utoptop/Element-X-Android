/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.pushproviders.api.Distributor
import io.element.android.libraries.pushproviders.api.PusherSubscriber
import io.element.android.libraries.pushproviders.unifiedpush.DefaultPushGatewayHttpUrlProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NtfyPushProviderTest {
    @Test
    fun `test index and name`() {
        val provider = createNtfyPushProvider()
        assertThat(provider.name).isEqualTo(NtfyConfig.NAME)
        assertThat(provider.index).isEqualTo(NtfyConfig.INDEX)
        assertThat(provider.supportMultipleDistributors).isFalse()
    }

    @Test
    fun `getDistributors returns embedded distributor`() {
        val provider = createNtfyPushProvider()
        val distributors = provider.getDistributors()
        assertThat(distributors).containsExactly(
            Distributor(NtfyConfig.DISTRIBUTOR_VALUE, NtfyConfig.DISTRIBUTOR_NAME)
        )
    }

    @Test
    fun `store getters and setters`() = runTest {
        val store = FakeNtfyStore(serverUrl = "https://ntfy.sh")
        assertThat(store.getServerUrl()).isEqualTo("https://ntfy.sh")
        store.setServerUrl("https://custom.ntfy.org/")
        assertThat(store.getServerUrl()).isEqualTo("https://custom.ntfy.org")
        store.setUsername("alice")
        assertThat(store.getUsername()).isEqualTo("alice")
        store.setPassword("secret")
        assertThat(store.getPassword()).isEqualTo("secret")
    }

    @Test
    fun `register and unregister success`() = runTest {
        val store = FakeNtfyStore()
        val subscriber = FakeNtfySubscriber()
        var registeredPushKey: String? = null
        var registeredGateway: String? = null
        val pusherSubscriber = object : PusherSubscriber {
            override suspend fun registerPusher(matrixClient: MatrixClient, pushKey: String, gateway: String): Result<Unit> {
                registeredPushKey = pushKey
                registeredGateway = gateway
                return Result.success(Unit)
            }

            override suspend fun unregisterPusher(matrixClient: MatrixClient, pushKey: String, gateway: String): Result<Unit> {
                return Result.success(Unit)
            }
        }
        val provider = createNtfyPushProvider(
            ntfyStore = store,
            ntfySubscriber = subscriber,
            pusherSubscriber = pusherSubscriber,
        )
        val matrixClient = FakeMatrixClient(sessionId = A_SESSION_ID)

        val regResult = provider.registerWith(matrixClient, Distributor(NtfyConfig.DISTRIBUTOR_VALUE, NtfyConfig.DISTRIBUTOR_NAME))
        assertThat(regResult.isSuccess).isTrue()
        assertThat(registeredPushKey).startsWith("https://ntfy.sh/up")
        assertThat(registeredGateway).isEqualTo("https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify")
        assertThat(subscriber.subscribedTopics).containsKey(store.getTopic(A_SESSION_ID))
        assertThat(provider.getCurrentDistributorValue(A_SESSION_ID)).isEqualTo(NtfyConfig.DISTRIBUTOR_VALUE)

        val unregResult = provider.unregister(matrixClient)
        assertThat(unregResult.isSuccess).isTrue()
        assertThat(subscriber.subscribedTopics).isEmpty()
        assertThat(provider.getCurrentDistributorValue(A_SESSION_ID)).isNull()
    }

    @Test
    fun `changing server URL unregisters old pusher and registers new pusher`() = runTest {
        val store = FakeNtfyStore()
        val subscriber = FakeNtfySubscriber()
        var unregisteredPushKey: String? = null
        var registeredPushKey: String? = null
        val pusherSubscriber = object : PusherSubscriber {
            override suspend fun registerPusher(matrixClient: MatrixClient, pushKey: String, gateway: String): Result<Unit> {
                registeredPushKey = pushKey
                return Result.success(Unit)
            }

            override suspend fun unregisterPusher(matrixClient: MatrixClient, pushKey: String, gateway: String): Result<Unit> {
                unregisteredPushKey = pushKey
                return Result.success(Unit)
            }
        }
        val provider = createNtfyPushProvider(
            ntfyStore = store,
            ntfySubscriber = subscriber,
            pusherSubscriber = pusherSubscriber,
        )
        val matrixClient = FakeMatrixClient(sessionId = A_SESSION_ID)

        val regResult1 = provider.registerWith(matrixClient, Distributor(NtfyConfig.DISTRIBUTOR_VALUE, NtfyConfig.DISTRIBUTOR_NAME))
        assertThat(regResult1.isSuccess).isTrue()
        assertThat(registeredPushKey).startsWith("https://ntfy.sh/up")
        val oldPushKey = registeredPushKey
        assertThat(subscriber.restartCalled).isTrue()

        subscriber.restartCalled = false
        store.setServerUrl("https://custom.ntfy.org")
        val regResult2 = provider.registerWith(matrixClient, Distributor(NtfyConfig.DISTRIBUTOR_VALUE, NtfyConfig.DISTRIBUTOR_NAME))
        assertThat(regResult2.isSuccess).isTrue()
        assertThat(unregisteredPushKey).isEqualTo(oldPushKey)
        assertThat(registeredPushKey).startsWith("https://custom.ntfy.org/up")
        assertThat(subscriber.restartCalled).isTrue()
    }

    private fun createNtfyPushProvider(
        ntfyStore: NtfyStore = FakeNtfyStore(),
        ntfySubscriber: NtfySubscriber = FakeNtfySubscriber(),
        pusherSubscriber: PusherSubscriber = FakePusherSubscriberImpl(),
        defaultPushGatewayHttpUrlProvider: DefaultPushGatewayHttpUrlProvider = object : DefaultPushGatewayHttpUrlProvider {
            override fun provide(): String = "https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify"
        },
    ): NtfyPushProvider {
        return NtfyPushProvider(
            ntfyStore = ntfyStore,
            ntfySubscriber = ntfySubscriber,
            pusherSubscriber = pusherSubscriber,
            defaultPushGatewayHttpUrlProvider = defaultPushGatewayHttpUrlProvider,
        )
    }

    private class FakeNtfyStore(
        private var serverUrl: String = NtfyConfig.DEFAULT_SERVER_URL,
        private var username: String? = null,
        private var password: String? = null,
    ) : NtfyStore {
        private val topics = mutableMapOf<SessionId, String>()
        private val endpoints = mutableMapOf<SessionId, String>()
        private val gateways = mutableMapOf<SessionId, String>()

        override val serverConfigTitle: String = "ntfy"
        override fun getServerUrl(): String = serverUrl
        override suspend fun setServerUrl(url: String) {
            serverUrl = url.trimEnd('/')
        }
        override fun getUsername(): String? = username
        override suspend fun setUsername(username: String?) {
            this.username = username
        }
        override fun getPassword(): String? = password
        override suspend fun setPassword(password: String?) {
            this.password = password
        }
        override fun getTopic(sessionId: SessionId): String? = topics[sessionId]
        override fun setTopic(sessionId: SessionId, topic: String?) {
            if (topic == null) topics.remove(sessionId) else topics[sessionId] = topic
        }
        override fun getEndpoint(sessionId: SessionId): String? = endpoints[sessionId]
        override fun setEndpoint(sessionId: SessionId, endpoint: String?) {
            if (endpoint == null) endpoints.remove(sessionId) else endpoints[sessionId] = endpoint
        }
        override fun getGateway(sessionId: SessionId): String? = gateways[sessionId]
        override fun setGateway(sessionId: SessionId, gateway: String?) {
            if (gateway == null) gateways.remove(sessionId) else gateways[sessionId] = gateway
        }
        override fun getAllTopics(): Map<String, SessionId> = topics.entries.associate { (k, v) -> v to k }
        override fun clear(sessionId: SessionId) {
            topics.remove(sessionId)
            endpoints.remove(sessionId)
            gateways.remove(sessionId)
        }
    }

    private class FakeNtfySubscriber : NtfySubscriber {
        val subscribedTopics = mutableMapOf<String, SessionId>()
        var restartCalled = false
        override val connectionStatus: StateFlow<NtfyConnectionStatus> = MutableStateFlow(NtfyConnectionStatus.CONNECTED)

        override fun subscribe(topic: String, sessionId: SessionId) {
            subscribedTopics[topic] = sessionId
        }

        override fun unsubscribe(topic: String) {
            subscribedTopics.remove(topic)
        }

        override fun restart() {
            restartCalled = true
        }
    }

    private class FakePusherSubscriberImpl : PusherSubscriber {
        override suspend fun registerPusher(matrixClient: MatrixClient, pushKey: String, gateway: String): Result<Unit> = Result.success(Unit)
        override suspend fun unregisterPusher(matrixClient: MatrixClient, pushKey: String, gateway: String): Result<Unit> = Result.success(Unit)
    }
}
