/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.unifiedpush.ntfy

import android.content.Context
import android.os.PowerManager
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.push.api.push.FetchPushForegroundServiceManager
import io.element.android.libraries.pushproviders.api.PushHandler
import io.element.android.libraries.pushproviders.unifiedpush.UnifiedPushParser
import io.element.android.libraries.pushstore.api.clientsecret.PushClientSecret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

private val loggerTag = LoggerTag("NtfySubscriber", LoggerTag.PushLoggerTag)

enum class NtfyConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

interface NtfySubscriber {
    val connectionStatus: StateFlow<NtfyConnectionStatus>
    fun subscribe(topic: String, sessionId: SessionId)
    fun unsubscribe(topic: String)
    fun restart()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultNtfySubscriber(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val ntfyStore: NtfyStore,
    private val pushHandler: PushHandler,
    private val pushParser: UnifiedPushParser,
    private val pushClientSecret: PushClientSecret,
    private val fetchPushForegroundServiceManager: FetchPushForegroundServiceManager,
    private val jsonProvider: JsonProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : NtfySubscriber {
    private val _connectionStatus = MutableStateFlow(NtfyConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<NtfyConnectionStatus> = _connectionStatus.asStateFlow()

    private val activeTopics = ConcurrentHashMap<String, SessionId>()
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = 1000L
    private var connectedServerUrl: String? = null
    private var connectedUsername: String? = null
    private var connectedPassword: String? = null

    // Optimized keep-alive: 90s ping interval allows the mobile cellular/Wi-Fi radio
    // to drop into deep low-power sleep (RRC_IDLE) for ~85% of time, while ntfy server keepalive frames (every 45-60s)
    // and socket read wake-ups guarantee 0-delay real-time push reception.
    private val wsOkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(90, TimeUnit.SECONDS)
            .build()
    }

    init {
        // Observe app foreground transitions using ProcessLifecycleOwner to safely start keep-alive service
        runCatchingExceptions {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (ntfyStore.isKeepAliveEnabled() && activeTopics.isNotEmpty()) {
                        NtfyKeepAliveService.start(context)
                    }
                }
            })
        }

        // Restore existing topics and connect asynchronously off the main thread
        coroutineScope.launch(dispatchers.io) {
            val storedTopics = ntfyStore.getAllTopics()
            if (storedTopics.isNotEmpty()) {
                activeTopics.putAll(storedTopics)
                Timber.tag(loggerTag.value).d("Restored ${storedTopics.size} ntfy topics on init")
                connect()
            }
        }
    }

    override fun subscribe(topic: String, sessionId: SessionId) {
        Timber.tag(loggerTag.value).d("Subscribing to topic: $topic for session: ${sessionId.value}")
        val currentServer = ntfyStore.getServerUrl().trim().trimEnd('/')
        val currentUsername = ntfyStore.getUsername()
        val currentPassword = ntfyStore.getPassword()
        val serverConfigChanged = connectedServerUrl != currentServer ||
            connectedUsername != currentUsername ||
            connectedPassword != currentPassword
        val previous = activeTopics.put(topic, sessionId)
        if (previous == null || webSocket == null || serverConfigChanged) {
            coroutineScope.launch(dispatchers.io) {
                reconnect()
            }
        }
    }

    override fun unsubscribe(topic: String) {
        Timber.tag(loggerTag.value).d("Unsubscribing from topic: $topic")
        activeTopics.remove(topic)
        coroutineScope.launch(dispatchers.io) {
            if (activeTopics.isEmpty()) {
                disconnect()
            } else {
                reconnect()
            }
        }
    }

    override fun restart() {
        Timber.tag(loggerTag.value).d("Restarting ntfy connection with current settings")
        coroutineScope.launch(dispatchers.io) {
            if (activeTopics.isEmpty()) {
                val storedTopics = ntfyStore.getAllTopics()
                if (storedTopics.isNotEmpty()) {
                    activeTopics.putAll(storedTopics)
                }
            }
            reconnect()
        }
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        val currentWs = webSocket
        webSocket = null
        tryOrNull { currentWs?.cancel() }
        _connectionStatus.value = NtfyConnectionStatus.DISCONNECTED
        if (activeTopics.isEmpty()) {
            connectedServerUrl = null
            connectedUsername = null
            connectedPassword = null
            NtfyKeepAliveService.stop(context)
        }
    }

    private fun reconnect() {
        disconnect()
        if (activeTopics.isNotEmpty()) {
            reconnectDelayMs = 1000L
            connect()
        }
    }

    private fun connect() {
        if (activeTopics.isEmpty()) return
        if (webSocket != null) {
            Timber.tag(loggerTag.value).d("WebSocket already active, skipping duplicate connect")
            return
        }

        if (ntfyStore.isKeepAliveEnabled()) {
            tryStartKeepAliveService()
        }

        val serverUrl = ntfyStore.getServerUrl().trim().trimEnd('/')
        val topics = activeTopics.keys.joinToString(",")
        if (topics.isBlank()) return

        val wsUrl = when {
            serverUrl.startsWith("https://", ignoreCase = true) -> "wss://${serverUrl.substring(8)}/$topics/ws"
            serverUrl.startsWith("http://", ignoreCase = true) -> "ws://${serverUrl.substring(7)}/$topics/ws"
            else -> "wss://$serverUrl/$topics/ws"
        }

        Timber.tag(loggerTag.value).i("Connecting to ntfy WebSocket: $wsUrl")
        _connectionStatus.value = NtfyConnectionStatus.CONNECTING

        val username = ntfyStore.getUsername()
        val password = ntfyStore.getPassword()
        connectedServerUrl = serverUrl
        connectedUsername = username
        connectedPassword = password

        try {
            val requestBuilder = Request.Builder().url(wsUrl)
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }

            webSocket = wsOkHttpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (this@DefaultNtfySubscriber.webSocket !== webSocket) return
                    Timber.tag(loggerTag.value).i("ntfy WebSocket connected!")
                    _connectionStatus.value = NtfyConnectionStatus.CONNECTED
                    reconnectDelayMs = 1000L
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (this@DefaultNtfySubscriber.webSocket !== webSocket) return
                    Timber.tag(loggerTag.value).d("Received message from ntfy: $text")
                    handleMessageText(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@DefaultNtfySubscriber.webSocket !== webSocket) return
                    Timber.tag(loggerTag.value).d("ntfy WebSocket closing: $code / $reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (this@DefaultNtfySubscriber.webSocket !== webSocket) return
                    Timber.tag(loggerTag.value).i("ntfy WebSocket closed: $code / $reason")
                    _connectionStatus.value = NtfyConnectionStatus.DISCONNECTED
                    this@DefaultNtfySubscriber.webSocket = null
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (this@DefaultNtfySubscriber.webSocket !== webSocket) return
                    Timber.tag(loggerTag.value).e(t, "ntfy WebSocket failure: ${response?.message}")
                    _connectionStatus.value = NtfyConnectionStatus.DISCONNECTED
                    this@DefaultNtfySubscriber.webSocket = null
                    scheduleReconnect()
                }
            })
        } catch (t: Throwable) {
            Timber.tag(loggerTag.value).e(t, "Failed to initiate WebSocket connection to $wsUrl")
            _connectionStatus.value = NtfyConnectionStatus.DISCONNECTED
            this@DefaultNtfySubscriber.webSocket = null
            scheduleReconnect()
        }
    }

    private fun handleMessageText(text: String) {
        val messageResponse = tryOrNull {
            jsonProvider().decodeFromString<NtfyMessageResponse>(text)
        } ?: return

        when (messageResponse.event) {
            "open", "keepalive" -> {
                Timber.tag(loggerTag.value).d("ntfy ${messageResponse.event} received")
            }
            "message" -> {
                val rawMessage = messageResponse.message ?: return
                val topic = messageResponse.topic ?: return
                val sessionId = activeTopics[topic] ?: return

                val payloadBytes = if (messageResponse.encoding == "base64") {
                    tryOrNull { Base64.decode(rawMessage, Base64.DEFAULT) } ?: rawMessage.toByteArray(Charsets.UTF_8)
                } else {
                    rawMessage.toByteArray(Charsets.UTF_8)
                }

                coroutineScope.launch(dispatchers.computation) {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elementx:ntfy_push_wake")
                    wakeLock?.acquire(10_000L)
                    try {
                        val clientSecret = pushClientSecret.getSecretForUser(sessionId)
                        val pushData = pushParser.parse(payloadBytes, clientSecret)
                        if (pushData != null) {
                            fetchPushForegroundServiceManager.start()
                            try {
                                Timber.tag(loggerTag.value).d("Delivering push notification to PushHandler")
                                pushHandler.handle(pushData, providerInfo = "${NtfyConfig.NAME} - $clientSecret")
                            } finally {
                                fetchPushForegroundServiceManager.stop()
                            }
                        } else {
                            Timber.tag(loggerTag.value).w("Failed to parse push payload: $rawMessage")
                            pushHandler.handleInvalid(
                                providerInfo = "${NtfyConfig.NAME} - $clientSecret",
                                data = rawMessage,
                            )
                        }
                    } finally {
                        if (wakeLock?.isHeld == true) {
                            tryOrNull { wakeLock.release() }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (activeTopics.isEmpty()) return
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch(dispatchers.io) {
            Timber.tag(loggerTag.value).d("Scheduling ntfy reconnect in $reconnectDelayMs ms")
            delay(reconnectDelayMs)
            reconnectDelayMs = min(reconnectDelayMs * 2, 30_000L)
            connect()
        }
    }

    private fun tryStartKeepAliveService() {
        runCatchingExceptions {
            val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (isForeground) {
                NtfyKeepAliveService.start(context)
            }
        }.onFailure {
            Timber.tag(loggerTag.value).d("Cannot start keep alive service: ${it.message}")
        }
    }
}
