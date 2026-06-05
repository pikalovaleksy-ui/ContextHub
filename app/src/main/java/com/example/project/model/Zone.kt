package com.example.project.model

data class Zone(
    val id: String,
    val name: String,
    val vertices: List<Vertex>,
    val trigger: TriggerType = TriggerType.ENTER,
    val dwellTimeMinutes: Int = 0,
    val enabled: Boolean = true,
    val roomId: String = "",
    val color: Int = 0,
    val smartThingsBindings: List<SmartThingsBinding> = emptyList()
)

data class SmartThingsBinding(
    val deviceId: String,
    val action: String,
    val extraParams: Map<String, String> = emptyMap()
)

enum class TriggerType {
    ENTER, EXIT, DWELL
}

object ZoneColors {
    val palette = listOf(
        0xFF6750A4.toInt(), // purple
        0xFF4CAF50.toInt(), // green
        0xFFE53935.toInt(), // red
        0xFFFB8C00.toInt(), // orange
        0xFF2196F3.toInt(), // blue
        0xFFE91E63.toInt(), // pink
        0xFF00BCD4.toInt(), // cyan
        0xFFFFEB3B.toInt(), // yellow
    )
}
