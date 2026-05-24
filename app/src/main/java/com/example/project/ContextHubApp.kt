package com.example.project

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ContextHubApp : Application() {

    companion object {
        const val CHANNEL_ZONES = "channel_zones"
        const val CHANNEL_SOCIAL = "channel_social"
        const val CHANNEL_MQTT = "channel_mqtt"
    }

    override fun onCreate() {
        super.onCreate()
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
