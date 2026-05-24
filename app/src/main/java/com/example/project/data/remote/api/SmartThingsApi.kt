package com.example.project.data.remote.api

import com.example.project.data.remote.dto.SmartThingsDeviceDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SmartThingsApi {
    @GET("api/v1/smartthings/devices")
    suspend fun getDevices(): Response<List<SmartThingsDeviceDto>>

    @POST("api/v1/smartthings/devices/{deviceId}/{action}")
    suspend fun sendCommand(
        @Path("deviceId") deviceId: String,
        @Path("action") action: String
    ): Response<Unit>
}
