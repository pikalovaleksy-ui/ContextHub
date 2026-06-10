package com.example.project

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.project.data.remote.mqtt.MqttTopics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ContextHubApp : Application() {

    companion object {
        const val CHANNEL_ZONES = "channel_zones"
        const val CHANNEL_SOCIAL = "channel_social"
        const val CHANNEL_MQTT = "channel_mqtt"
        private const val PREFS_NAME = "contexthub_settings"
        private const val KEY_BROKER_URL = "broker_url"
        private const val KEY_SERVER_URL = "server_url"
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        MqttTopics.DEFAULT_BROKER_URL = prefs.getString(KEY_BROKER_URL, MqttTopics.DEFAULT_BROKER_URL) ?: MqttTopics.DEFAULT_BROKER_URL
        MqttTopics.DEFAULT_SERVER_URL = prefs.getString(KEY_SERVER_URL, MqttTopics.DEFAULT_SERVER_URL) ?: MqttTopics.DEFAULT_SERVER_URL
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val zonesChannel = NotificationChannel(
                CHANNEL_ZONES,
                "События зон",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о срабатывании умных зон"
            }

            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL,
                "Social Touch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о Social Touch сигналах"
            }

            val mqttChannel = NotificationChannel(
                CHANNEL_MQTT,
                "MQTT Соединение",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Статус MQTT соединения"
            }

            manager.createNotificationChannels(
                listOf(zonesChannel, socialChannel, mqttChannel)
            )
        }
    }
}
