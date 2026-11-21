package com.example.jobly.data.remote.api

import com.example.jobly.data.remote.dto.AssistantChatRequestDto
import com.example.jobly.data.remote.dto.AssistantChatResponseDto
import com.example.jobly.data.remote.dto.AssistantHealthDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AssistantApi {
    @GET("health")
    suspend fun getHealth(): AssistantHealthDto

    @POST("chat")
    suspend fun chat(
        @Body request: AssistantChatRequestDto
    ): AssistantChatResponseDto
}
