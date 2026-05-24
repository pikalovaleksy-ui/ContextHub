package com.example.project.data.remote.api

import com.example.project.model.AddZoneRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface RadarApi {
    @POST("api/management_assistant/addZone/{id}")
    suspend fun addZone(
        @Path("id") deviceId: String,
        @Body request: AddZoneRequest
    ): Response<Unit>
}
