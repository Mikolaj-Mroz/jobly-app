package com.example.jobly.data.repository

import com.example.jobly.data.remote.api.AssistantApi
import com.example.jobly.data.remote.dto.AssistantChatRequestDto
import com.example.jobly.data.remote.dto.ChatMessageDto
import com.example.jobly.data.remote.dto.toDomain
import com.example.jobly.model.ChatMessage
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AssistantReply(
    val reply: String,
    val model: String? = null,
    val updatedCv: CvItem? = null,
    val suggestedOffers: List<JobOffer> = emptyList()
)

interface AssistantRepository {
    suspend fun detectActiveModel(): Result<String>
    suspend fun sendMessage(
        message: String,
        history: List<ChatMessage>,
        cvId: String? = null
    ): Result<AssistantReply>
}

class RemoteAssistantRepository(
    private val api: AssistantApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AssistantRepository {

    override suspend fun detectActiveModel(): Result<String> = suspendResultOf {
        withContext(ioDispatcher) {
            val health = api.getHealth()
            val model = health.chatModel?.takeIf { it.isNotBlank() }
            if (!health.reachable || model == null) {
                error(health.error ?: "Assistant LLM server is unreachable")
            }
            model
        }
    }

    override suspend fun sendMessage(
        message: String,
        history: List<ChatMessage>,
        cvId: String?
    ): Result<AssistantReply> = suspendResultOf {
        withContext(ioDispatcher) {
            val apiHistory = history
                .filterNot { it.isError }
                .map { msg ->
                    ChatMessageDto(
                        role = if (msg.isUser) "user" else "assistant",
                        content = msg.text
                    )
                }

            val response = api.chat(
                AssistantChatRequestDto(
                    message = message,
                    cvId = cvId,
                    history = apiHistory
                )
            )
            val content = response.reply.trim()
            if (content.isEmpty()) {
                error("Assistant returned an empty response")
            }
            AssistantReply(
                reply = content,
                model = response.model,
                updatedCv = response.updatedCv?.toDomain(),
                suggestedOffers = response.suggestedOffers.map { it.toDomain() }
            )
        }
    }
}
