package com.example.jobly.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobly.data.repository.OffersRepository
import com.example.jobly.data.repository.UserRepository
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val HOME_OFFERS_LIMIT = 5

data class HomeUiState(
    val searchQuery: String = "",
    val closeOffers: List<JobOffer> = emptyList(),
    val bestOffers: List<JobOffer> = emptyList(),
    val cvItems: List<CvItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val offersRepository: OffersRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(cvItems = userRepository.cvItems.value))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var offersJob: Job? = null

    init {
        viewModelScope.launch {
            userRepository.cvItems.collect { cvs ->
                _uiState.update { it.copy(cvItems = cvs) }
            }
        }
        refreshOffers("")
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshOffers(query)
    }

    fun refreshOffers(query: String = _uiState.value.searchQuery) {
        offersJob?.cancel()
        offersJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (closeResult, bestResult) = coroutineScope {
                val closeDeferred = async { offersRepository.getOffersAroundYou(query) }
                val bestDeferred = async { offersRepository.getBestOffersForYou(query) }
                closeDeferred.await() to bestDeferred.await()
            }

            val firstError = closeResult.exceptionOrNull()?.message
                ?: bestResult.exceptionOrNull()?.message

            _uiState.update { state ->
                state.copy(
                    closeOffers = closeResult.map { it.take(HOME_OFFERS_LIMIT) }.getOrElse { state.closeOffers },
                    bestOffers = bestResult.map { it.take(HOME_OFFERS_LIMIT) }.getOrElse { state.bestOffers },
                    isLoading = false,
                    errorMessage = firstError
                )
            }
        }
    }

    fun applyToOffer(offer: JobOffer, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val activeCvId = _uiState.value.cvItems.firstOrNull()?.id
            val applied = offersRepository.applyToOffer(offer, activeCvId).getOrDefault(false)
            onResult(applied)
        }
    }
}
