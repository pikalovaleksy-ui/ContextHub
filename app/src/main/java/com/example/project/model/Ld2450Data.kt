package com.example.project.model

data class Ld2450Data(
    val targets: List<Target> = emptyList(),
    val zones: List<Ld2450Zone> = emptyList(),
    val personsInZones: List<PersonInZone>? = null
)

data class Target(
    val id: Int,
    val x: Int,
    val y: Int,
    val speed: Int,
    val inZone: Boolean
)

data class Ld2450Zone(
    val name: String,
    val vertices: List<Vertex>,
    val pointCount: Int
)

data class Vertex(
    val x: Int,
    val y: Int
)

data class PersonInZone(
    val targetId: Int,
    val zones: String,
    val x: Int,
    val y: Int,
    val speed: Int
)

data class AddZoneRequest(
    val zoneName: String,
    val vertices: List<Vertex>
)
