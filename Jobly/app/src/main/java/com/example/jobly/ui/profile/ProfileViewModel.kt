package com.example.jobly.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.data.repository.UserRepository
import com.example.jobly.data.repository.extractErrorMessage
import com.example.jobly.model.CvItem
import com.example.jobly.model.FeedbackReceipt
import com.example.jobly.model.JobOffer
import com.example.jobly.model.UserProfile
import com.example.jobly.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile = SampleSeedData.defaultProfile,
    val settings: UserSettings = UserSettings(),
    val appliedOffers: List<JobOffer> = emptyList(),
    val cvItems: List<CvItem> = emptyList(),
    val isLoading: Boolean = false,
    val isUploadingCv: Boolean = false,
    val isSavingSettings: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            profile = userRepository.userProfile.value,
            settings = userRepository.userSettings.value,
            appliedOffers = userRepository.appliedOffers.value,
            cvItems = userRepository.cvItems.value
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.userProfile.collect { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
        viewModelScope.launch {
            userRepository.userSettings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            userRepository.appliedOffers.collect { offers ->
                _uiState.update { it.copy(appliedOffers = offers) }
            }
        }
        viewModelScope.launch {
            userRepository.cvItems.collect { cvs ->
                _uiState.update { it.copy(cvItems = cvs) }
            }
        }
        refreshUserData()
    }

    fun refreshUserData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = userRepository.refreshUserData()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.let(::extractErrorMessage)
                )
            }
        }
    }

    fun updateProfile(name: String, phone: String, email: String) {
        viewModelScope.launch {
            val result = userRepository.updateProfile(name, phone, email)
            result.exceptionOrNull()?.let { err ->
                _uiState.update { it.copy(errorMessage = extractErrorMessage(err)) }
            }
        }
    }

    fun updateSettings(
        request: UpdateUserSettingsRequestDto,
        onResult: (Result<UserSettings>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true, errorMessage = null) }
            val result = userRepository.updateSettings(request)
            _uiState.update {
                it.copy(
                    isSavingSettings = false,
                    errorMessage = result.exceptionOrNull()?.let(::extractErrorMessage)
                )
            }
            onResult(result)
        }
    }

    fun completeOnboarding(
        name: String,
        phone: String,
        email: String,
        settingsRequest: UpdateUserSettingsRequestDto,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true, errorMessage = null) }
            val profileResult = userRepository.updateProfile(name, phone, email)
            if (profileResult.isFailure) {
                val err = profileResult.exceptionOrNull()!!
                _uiState.update {
                    it.copy(isSavingSettings = false, errorMessage = extractErrorMessage(err))
                }
                onResult(Result.failure(err))
                return@launch
            }
            val settingsResult = userRepository.updateSettings(
                settingsRequest.copy(onboardingCompleted = true)
            )
            _uiState.update {
                it.copy(
                    isSavingSettings = false,
                    errorMessage = settingsResult.exceptionOrNull()?.let(::extractErrorMessage)
                )
            }
            onResult(settingsResult.map { })
        }
    }

    fun submitFeedback(
        category: String,
        message: String,
        contactEmail: String?,
        onResult: (Result<FeedbackReceipt>) -> Unit
    ) {
        viewModelScope.launch {
            val result = userRepository.submitFeedback(category, message, contactEmail)
            result.exceptionOrNull()?.let { err ->
                _uiState.update { it.copy(errorMessage = extractErrorMessage(err)) }
            }
            onResult(result)
        }
    }

    fun addCv(fileName: String, onAdded: (CvItem) -> Unit = {}) {
        viewModelScope.launch {
            userRepository.addCv(fileName)
                .onSuccess { onAdded(it) }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = extractErrorMessage(err)) }
                }
        }
    }

    fun uploadCvPdf(
        fileName: String,
        pdfBytes: ByteArray,
        onResult: (Result<CvItem>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingCv = true, errorMessage = null) }
            val result = userRepository.uploadCvPdf(fileName, pdfBytes)
            _uiState.update {
                it.copy(
                    isUploadingCv = false,
                    errorMessage = result.exceptionOrNull()?.let(::extractErrorMessage)
                )
            }
            onResult(result)
        }
    }

    fun deleteCv(cvId: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = userRepository.deleteCv(cvId)
            result.exceptionOrNull()?.let { err ->
                _uiState.update { it.copy(errorMessage = extractErrorMessage(err)) }
            }
            onResult(result)
        }
    }

    fun downloadCvPdf(cvId: String, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.downloadCvPdf(cvId)
            result.exceptionOrNull()?.let { err ->
                _uiState.update { it.copy(errorMessage = extractErrorMessage(err)) }
            }
            onResult(result)
        }
    }
}
