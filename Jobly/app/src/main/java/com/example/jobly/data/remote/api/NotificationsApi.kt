package com.example.jobly.data.remote.api

import com.example.jobly.data.remote.dto.DailyDigestResultDto
import com.example.jobly.data.remote.dto.DailyDigestStatusDto
import com.example.jobly.data.remote.dto.NotificationsResponseDto
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationsApi {
    @GET(".")
    suspend fun getNotifications(): NotificationsResponseDto

    @PATCH("{notificationId}/read")
    suspend fun markAsRead(@Path("notificationId") notificationId: String)

    @POST("daily-digest")
    suspend fun triggerDailyDigest(@Query("force") force: Boolean = false): DailyDigestResultDto

    @GET("digest/status")
    suspend fun getDailyDigestStatus(): DailyDigestStatusDto
}
