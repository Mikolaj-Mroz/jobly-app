package com.example.jobly.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssistantHealthDto(
    val reachable: Boolean = false,
    @SerialName("llm_base_url") val llmBaseUrl: String = "",
    @SerialName("chat_model") val chatModel: String? = null,
    @SerialName("embedding_model") val embeddingModel: String? = null,
    val error: String? = null
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class AssistantChatRequestDto(
    val message: String,
    @SerialName("cv_id") val cvId: String? = null,
    val history: List<ChatMessageDto> = emptyList()
)

@Serializable
data class AssistantChatResponseDto(
    val reply: String,
    val model: String? = null,
    @SerialName("updated_cv") val updatedCv: CvDto? = null,
    @SerialName("suggested_offers") val suggestedOffers: List<JobOfferDto> = emptyList()
)
