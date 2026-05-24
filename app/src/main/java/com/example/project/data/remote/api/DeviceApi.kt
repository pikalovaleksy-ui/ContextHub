package com.example.project.data.remote.api

import com.example.project.data.remote.dto.DeviceStatusDto
import com.example.project.data.remote.dto.WifiConfigRequest
import com.example.project.data.remote.dto.WifiNetworkDto
import com.example.project.data.remote.dto.ZoneDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DeviceApi {
    @GET("api/v1/devices/{deviceId}/status")
    suspend fun getDeviceStatus(@Path("deviceId") deviceId: String): Response<DeviceStatusDto>

    @POST("api/v1/devices/{deviceId}/wifi")
    suspend fun configureWifi(
        @Path("deviceId") deviceId: String,
        @Body config: WifiConfigRequest
    ): Response<Unit>

    @POST("api/v1/devices/{deviceId}/zones")
    suspend fun saveZone(
        @Path("deviceId") deviceId: String,
        @Body zone: ZoneDto
    ): Response<Unit>

    @GET("api/v1/devices/{deviceId}/zones")
    suspend fun getZones(@Path("deviceId") deviceId: String): Response<List<ZoneDto>>
}
