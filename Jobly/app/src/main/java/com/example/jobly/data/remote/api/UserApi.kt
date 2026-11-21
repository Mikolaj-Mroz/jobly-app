package com.example.jobly.data.remote.api

import com.example.jobly.data.remote.dto.ApplicationsResponseDto
import com.example.jobly.data.remote.dto.CreateCvRequestDto
import com.example.jobly.data.remote.dto.CvDto
import com.example.jobly.data.remote.dto.CvsResponseDto
import com.example.jobly.data.remote.dto.FeedbackRequestDto
import com.example.jobly.data.remote.dto.FeedbackResponseDto
import com.example.jobly.data.remote.dto.UpdateProfileRequestDto
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.data.remote.dto.UserProfileDto
import com.example.jobly.data.remote.dto.UserSettingsDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface UserApi {
    @GET("profile")
    suspend fun getProfile(): UserProfileDto

    @PUT("profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): UserProfileDto

    @GET("settings")
    suspend fun getSettings(): UserSettingsDto

    @PUT("settings")
    suspend fun updateSettings(@Body request: UpdateUserSettingsRequestDto): UserSettingsDto

    @POST("feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequestDto): FeedbackResponseDto

    @GET("applications")
    suspend fun getApplications(): ApplicationsResponseDto

    @GET("cvs")
    suspend fun getCvs(): CvsResponseDto

    @POST("cvs")
    suspend fun addCv(@Body request: CreateCvRequestDto): CvDto

    @Multipart
    @POST("cvs/upload")
    suspend fun uploadCv(@Part file: MultipartBody.Part): CvDto

    @DELETE("cvs/{cvId}")
    suspend fun deleteCv(@Path("cvId") cvId: String)

    @GET("cvs/{cvId}/download")
    suspend fun downloadCv(@Path("cvId") cvId: String): ResponseBody
}
