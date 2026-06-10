package com.example.project.data.remote.mqtt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MqttManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var client: MqttClient? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val messageCallbacks = mutableMapOf<String, MutableList<(topic: String, message: String) -> Unit>>()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    private fun getStableClientId(): String {
        var id = prefs.getString(KEY_CLIENT_ID, null)
        if (id == null) {
            id = MqttTopics.CLIENT_ID_PREFIX + java.util.UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_CLIENT_ID, id).apply()
        }
        return id
    }

    private fun normalizeBrokerUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return MqttTopics.DEFAULT_BROKER_URL
        if (!trimmed.contains("://")) return "tcp://$trimmed"
        return trimmed
    }

    fun connect(brokerUrl: String = MqttTopics.DEFAULT_BROKER_URL) {
        val normalizedUrl = normalizeBrokerUrl(brokerUrl)
        executor.execute {
            try {
                try {
                    client?.disconnectForcibly(1000, 0)
                    client?.close()
                } catch (_: Exception) {}
                client = null
                _connectionState.value = false

                val clientId = getStableClientId()
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = false
                    connectionTimeout = 10
                    keepAliveInterval = 30
                }

                val newClient = MqttClient(normalizedUrl, clientId, MemoryPersistence())
                client = newClient
                newClient.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        if (this@MqttManager.client !== newClient) return
                        Log.d(TAG, "connectComplete (reconnect=$reconnect) $serverURI")
                        _connectionState.value = true
                        newClient.let { c ->
                            messageCallbacks.forEach { (topic, _) ->
                                try { c.subscribe(topic) } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        if (this@MqttManager.client !== newClient) return
                        Log.w(TAG, "connectionLost: ${cause?.message}")
                        _connectionState.value = false
                    }

                    override fun messageArrived(topic: String, message: MqttMessage?) {
                        val payload = message?.toString() ?: ""
                        Log.d(TAG, "message on $topic")
                        messageCallbacks[topic]?.forEach { it(topic, payload) }
                        messageCallbacks["__global__"]?.forEach { it(topic, payload) }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                newClient.connect(opts)
            } catch (e: Exception) {
                Log.e(TAG, "connect failed: ${e.message}", e)
                _connectionState.value = false
            }
        }
    }

    fun disconnect() {
        _connectionState.value = false
        executor.execute {
            try {
                client?.disconnectForcibly(1000, 0)
                client?.close()
            } catch (_: Exception) {}
            client = null
        }
    }

    fun subscribe(topic: String, callback: (topic: String, message: String) -> Unit) {
        messageCallbacks.getOrPut(topic) { mutableListOf() }.add(callback)
        executor.execute {
            try { client?.subscribe(topic) } catch (_: Exception) {}
        }
    }

    fun unsubscribe(topic: String) {
        messageCallbacks.remove(topic)
        executor.execute {
            try { client?.unsubscribe(topic) } catch (_: Exception) {}
        }
    }

    fun publish(topic: String, message: String, qos: Int = 0) {
        executor.execute {
            try {
                client?.publish(topic, MqttMessage(message.toByteArray()).apply { this.qos = qos })
            } catch (e: MqttException) {
                Log.e(TAG, "publish error: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean = _connectionState.value

    companion object {
        private const val TAG = "MqttManager"
        private const val PREFS_NAME = "contexthub_mqtt"
        private const val KEY_CLIENT_ID = "stable_client_id"
    }
}
