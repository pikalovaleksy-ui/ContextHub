package com.example.project.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val deviceIds: List<String> = emptyList()
)
