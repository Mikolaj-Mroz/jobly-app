package com.example.jobly.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobly.data.repository.AssistantRepository
import com.example.jobly.data.repository.UserRepository
import com.example.jobly.data.repository.extractErrorMessage
import com.example.jobly.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val activeModel: String? = null,
    val activeCvId: String? = null,
    val activeCvName: String = "My-new-cv.pdf",
    val errorMessage: String? = null
)

class AssistantViewModel(
    private val assistantRepository: AssistantRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val messageIdCounter = AtomicLong(0L)

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            activeCvId = userRepository.cvItems.value.firstOrNull()?.id,
            activeCvName = userRepository.cvItems.value.firstOrNull()?.fileName ?: "My-new-cv.pdf"
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.cvItems.collect { cvs ->
                _uiState.update { current ->
                    val chosen = cvs.firstOrNull { it.id == current.activeCvId }
                        ?: cvs.firstOrNull()
                    current.copy(
                        activeCvId = chosen?.id,
                        activeCvName = chosen?.fileName ?: "My-new-cv.pdf"
                    )
                }
            }
        }
        viewModelScope.launch {
            assistantRepository.detectActiveModel()
                .onSuccess { model -> _uiState.update { it.copy(activeModel = model) } }
        }
    }

    fun selectActiveCv(cv: com.example.jobly.model.CvItem) {
        _uiState.update {
            it.copy(
                activeCvId = cv.id,
                activeCvName = cv.fileName
            )
        }
    }

    fun sendPrompt(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return

        val priorHistory = _uiState.value.messages
        val activeCvId = _uiState.value.activeCvId
        val userMessage = ChatMessage(
            id = "msg_${messageIdCounter.incrementAndGet()}",
            text = trimmed,
            isUser = true
        )
        _uiState.update {
            it.copy(
                messages = priorHistory + userMessage,
                isSending = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            assistantRepository.sendMessage(
                message = trimmed,
                history = priorHistory,
                cvId = activeCvId
            )
                .onSuccess { assistantReply ->
                    assistantReply.updatedCv?.let { updatedCv ->
                        userRepository.upsertLocalCv(updatedCv)
                    }
                    val aiMessage = ChatMessage(
                        id = "msg_${messageIdCounter.incrementAndGet()}",
                        text = assistantReply.reply,
                        isUser = false,
                        updatedCv = assistantReply.updatedCv,
                        suggestedOffers = assistantReply.suggestedOffers
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + aiMessage,
                            isSending = false,
                            activeModel = assistantReply.model ?: it.activeModel,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    val detail = extractErrorMessage(error)
                    val errorMsg = ChatMessage(
                        id = "msg_${messageIdCounter.incrementAndGet()}",
                        text = "Could not reach AI assistant: $detail",
                        isUser = false,
                        isError = true
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + errorMsg,
                            isSending = false,
                            errorMessage = detail
                        )
                    }
                }
        }
    }
}
