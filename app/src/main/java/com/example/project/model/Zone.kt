package com.example.project.model

data class Zone(
    val id: String,
    val name: String,
    val vertices: List<Vertex>,
    val trigger: TriggerType = TriggerType.ENTER,
    val dwellTimeMinutes: Int = 0,
    val enabled: Boolean = true
)

enum class TriggerType {
    ENTER, EXIT, DWELL
}
