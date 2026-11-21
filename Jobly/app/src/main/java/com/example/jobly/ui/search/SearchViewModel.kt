package com.example.jobly.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jobly.data.repository.OffersRepository
import com.example.jobly.data.repository.SearchOffersParams
import com.example.jobly.data.repository.SearchSort
import com.example.jobly.data.repository.extractErrorMessage
import com.example.jobly.model.JobOffer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_RECENT_QUERIES = 6

data class SearchUiState(
    val query: String = "",
    val selectedLevel: String? = null,
    val selectedContract: String? = null,
    val selectedWorkMode: String? = null,
    val cityFilter: String = "",
    val minSalary: Int? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
    val results: List<JobOffer> = emptyList(),
    val totalCount: Int = 0,
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            selectedLevel,
            selectedContract,
            selectedWorkMode,
            cityFilter.trim().takeIf { it.isNotEmpty() },
            minSalary
        ).size
}

class SearchViewModel(
    private val offersRepository: OffersRepository,
    private val debounceMs: Long = 250L
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        executeSearch(immediate = true)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        executeSearch(immediate = false)
    }

    fun submitSearch(queryOverride: String? = null) {
        val targetQuery = queryOverride ?: _uiState.value.query
        val trimmed = targetQuery.trim()
        _uiState.update { state ->
            val updatedRecent = if (trimmed.isNotEmpty()) {
                (listOf(trimmed) + state.recentQueries.filterNot {
                    it.equals(trimmed, ignoreCase = true)
                }).take(MAX_RECENT_QUERIES)
            } else {
                state.recentQueries
            }
            state.copy(
                query = if (queryOverride != null) queryOverride else state.query,
                recentQueries = updatedRecent
            )
        }
        executeSearch(immediate = true)
    }

    fun toggleLevel(level: String) {
        _uiState.update { state ->
            val next = if (state.selectedLevel.equals(level, ignoreCase = true)) null else level
            state.copy(selectedLevel = next)
        }
        executeSearch(immediate = true)
    }

    fun toggleContract(contract: String) {
        _uiState.update { state ->
            val next = if (state.selectedContract.equals(contract, ignoreCase = true)) null else contract
            state.copy(selectedContract = next)
        }
        executeSearch(immediate = true)
    }

    fun toggleWorkMode(mode: String) {
        _uiState.update { state ->
            val next = if (state.selectedWorkMode.equals(mode, ignoreCase = true)) null else mode
            state.copy(selectedWorkMode = next)
        }
        executeSearch(immediate = true)
    }

    fun onCityFilterChange(city: String) {
        _uiState.update { it.copy(cityFilter = city) }
        executeSearch(immediate = false)
    }

    fun selectMinSalary(minSalary: Int?) {
        _uiState.update { it.copy(minSalary = minSalary) }
        executeSearch(immediate = true)
    }

    fun selectSort(sort: SearchSort) {
        _uiState.update { it.copy(sort = sort) }
        executeSearch(immediate = true)
    }

    fun clearFilters() {
        _uiState.update { state ->
            state.copy(
                selectedLevel = null,
                selectedContract = null,
                selectedWorkMode = null,
                cityFilter = "",
                minSalary = null
            )
        }
        executeSearch(immediate = true)
    }

    fun clearAll() {
        _uiState.update { state ->
            state.copy(
                query = "",
                selectedLevel = null,
                selectedContract = null,
                selectedWorkMode = null,
                cityFilter = "",
                minSalary = null
            )
        }
        executeSearch(immediate = true)
    }

    fun retry() {
        executeSearch(immediate = true)
    }

    private fun executeSearch(immediate: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate && debounceMs > 0L) {
                delay(debounceMs)
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val snapshot = _uiState.value
            val params = SearchOffersParams(
                query = snapshot.query,
                level = snapshot.selectedLevel,
                contractType = snapshot.selectedContract,
                workMode = snapshot.selectedWorkMode,
                city = snapshot.cityFilter.trim().ifEmpty { null },
                minSalary = snapshot.minSalary,
                sort = snapshot.sort
            )
            offersRepository.searchOffers(params).onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        results = result.offers,
                        totalCount = result.total,
                        isLoading = false,
                        errorMessage = null,
                        hasSearched = true
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = extractErrorMessage(err),
                        hasSearched = true
                    )
                }
            }
        }
    }
}
