package com.example.project.navigation

object NavRoutes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DEVICE_SCAN = "device_scan"
    const val WIFI_SETUP = "wifi_setup"
    const val CONNECTION = "connection"
    const val MAIN = "main"
    const val ZONE_EDITOR = "zone_editor/{zoneId}"
    const val ADD_FRIEND = "add_friend"
    const val SETTINGS = "settings"

    fun zoneEditor(zoneId: String? = null) = "zone_editor/$zoneId"
}
