package com.example.project.data.remote.mqtt

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MqttManager @Inject constructor() {

    private var client: MqttClient? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val messageCallbacks = mutableMapOf<String, MutableList<(topic: String, message: String) -> Unit>>()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    fun connect(
        brokerUrl: String = MqttTopics.DEFAULT_BROKER_URL,
        clientId: String = MqttTopics.CLIENT_ID_PREFIX + UUID.randomUUID().toString().take(8)
    ) {
        if (_connectionState.value) return

        executor.execute {
            try {
                // Disconnect any previous client first
                try {
                    client?.disconnect()
                    client?.close()
                } catch (_: Exception) {}
                client = null

                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                }

                client = MqttClient(brokerUrl, clientId, MemoryPersistence())
                client?.connect(opts)

                client?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                        _connectionState.value = false
                    }

                    override fun messageArrived(topic: String, message: MqttMessage?) {
                        val payload = message?.toString() ?: ""
                        Log.d(TAG, "MQTT message on $topic: $payload")
                        val globalKey = "__global__"
                        messageCallbacks[topic]?.forEach { it(topic, payload) }
                        messageCallbacks[globalKey]?.forEach { it(topic, payload) }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                Log.d(TAG, "MQTT connected to $brokerUrl")
                _connectionState.value = true

                messageCallbacks.forEach { (topic, _) ->
                    try { client?.subscribe(topic) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "MQTT connection failed: ${e.message}")
                Log.e(TAG, "MQTT connect error", e)
                _connectionState.value = false
            }
        }
    }

    fun disconnect() {
        _connectionState.value = false
        executor.execute {
            try {
                client?.disconnect()
                client?.close()
            } catch (_: Exception) {}
            client = null
        }
    }

    fun subscribe(topic: String, callback: (topic: String, message: String) -> Unit) {
        val callbacks = messageCallbacks.getOrPut(topic) { mutableListOf() }
        callbacks.add(callback)
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
                Log.e(TAG, "MQTT publish error: ${e.message}")
            }
        }
    }

    fun addGlobalCallback(callback: (topic: String, message: String) -> Unit) {
        val globalKey = "__global__"
        messageCallbacks.getOrPut(globalKey) { mutableListOf() }.add(callback)
    }

    fun removeGlobalCallback(callback: (topic: String, message: String) -> Unit) {
        messageCallbacks.forEach { (_, callbacks) -> callbacks.remove(callback) }
    }

    companion object {
        private const val TAG = "MqttManager"
    }
}
