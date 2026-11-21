package com.example.jobly.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobly.data.repository.NotificationsRepository
import com.example.jobly.data.repository.extractErrorMessage
import com.example.jobly.model.DailyDigestPushResult
import com.example.jobly.model.DailyDigestStatus
import com.example.jobly.model.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val digestStatus: DailyDigestStatus? = null,
    val isLoading: Boolean = false,
    val isTriggeringDigest: Boolean = false,
    val errorMessage: String? = null
)

class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NotificationsUiState(
            notifications = notificationsRepository.notifications.value,
            unreadCount = notificationsRepository.unreadCount.value
        )
    )
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            notificationsRepository.notifications.collect { items ->
                _uiState.update { it.copy(notifications = items) }
            }
        }
        viewModelScope.launch {
            notificationsRepository.unreadCount.collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
        refreshNotifications()
        refreshDigestStatus()
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = notificationsRepository.refreshNotifications()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.let(::extractErrorMessage)
                )
            }
        }
    }

    fun refreshDigestStatus() {
        viewModelScope.launch {
            notificationsRepository.getDailyDigestStatus()
                .onSuccess { status ->
                    _uiState.update { it.copy(digestStatus = status) }
                }
        }
    }

    fun triggerDailyDigest(
        force: Boolean = true,
        onResult: (Result<DailyDigestPushResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTriggeringDigest = true, errorMessage = null) }
            val result = notificationsRepository.triggerDailyDigest(force)
            _uiState.update {
                it.copy(
                    isTriggeringDigest = false,
                    errorMessage = result.exceptionOrNull()?.let(::extractErrorMessage)
                )
            }
            if (result.isSuccess) {
                refreshDigestStatus()
            }
            onResult(result)
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationsRepository.markAsRead(notificationId)
        }
    }
}
