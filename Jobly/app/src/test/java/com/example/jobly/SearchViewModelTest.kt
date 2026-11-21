package com.example.jobly

import com.example.jobly.data.repository.OffersRepository
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.data.repository.SearchOffersParams
import com.example.jobly.data.repository.SearchOffersResult
import com.example.jobly.data.repository.SearchSort
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsInitialOffersAndCount() = runTest {
        val sample = SampleSeedData.sampleBestOffers
        val captured = mutableListOf<SearchOffersParams>()
        val repo = object : OffersRepository {
            override suspend fun getOffersAroundYou(query: String) = Result.success(sample)
            override suspend fun getBestOffersForYou(query: String) = Result.success(sample)
            override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
            override suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> {
                captured += params
                return Result.success(SearchOffersResult(total = 42, offers = sample))
            }
        }

        val vm = SearchViewModel(repo, debounceMs = 250L)
        advanceUntilIdle()

        assertEquals(1, captured.size)
        assertEquals("", captured[0].query)
        assertEquals(42, vm.uiState.value.totalCount)
        assertEquals(sample.size, vm.uiState.value.results.size)
        assertTrue(vm.uiState.value.hasSearched)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun debouncedTyping_cancelsIntermediateSearch_andSubmitUpdatesRecentQueriesWithCapAndDedup() = runTest {
        val sample = SampleSeedData.sampleBestOffers
        val captured = mutableListOf<SearchOffersParams>()
        val repo = object : OffersRepository {
            override suspend fun getOffersAroundYou(query: String) = Result.success(sample)
            override suspend fun getBestOffersForYou(query: String) = Result.success(sample)
            override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
            override suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> {
                captured += params
                return Result.success(SearchOffersResult(total = sample.size, offers = sample))
            }
        }

        val vm = SearchViewModel(repo, debounceMs = 250L)
        advanceUntilIdle()
        captured.clear()

        vm.onQueryChange("p")
        advanceTimeBy(100L)
        vm.onQueryChange("py")
        advanceTimeBy(100L)
        vm.onQueryChange("python")
        // Not fired before 250ms settles
        assertEquals(0, captured.size)

        advanceTimeBy(250L)
        advanceUntilIdle()
        assertEquals(1, captured.size)
        assertEquals("python", captured[0].query)

        // Submit queries: verify dedup (case-insensitive) and cap at 6
        listOf("Java", "DevOps", "Kotlin", "Go", "Rust", "Scala", "java").forEach { q ->
            vm.submitSearch(q)
            advanceUntilIdle()
        }
        // Blank query must not enter recentQueries
        vm.submitSearch("   ")
        advanceUntilIdle()

        assertEquals(
            listOf("java", "Scala", "Rust", "Go", "Kotlin", "DevOps"),
            vm.uiState.value.recentQueries
        )
    }

    @Test
    fun filterToggles_clearFilters_andSortSwitchingPassExpectedParams() = runTest {
        val sample = SampleSeedData.sampleCloseOffers
        val captured = mutableListOf<SearchOffersParams>()
        val repo = object : OffersRepository {
            override suspend fun getOffersAroundYou(query: String) = Result.success(sample)
            override suspend fun getBestOffersForYou(query: String) = Result.success(sample)
            override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
            override suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> {
                captured += params
                return Result.success(SearchOffersResult(total = sample.size, offers = sample))
            }
        }

        val vm = SearchViewModel(repo, debounceMs = 0L)
        advanceUntilIdle()

        vm.submitSearch("kotlin")
        advanceUntilIdle()
        vm.toggleWorkMode("remote")
        advanceUntilIdle()
        vm.toggleLevel("SENIOR")
        advanceUntilIdle()
        vm.toggleContract("B2B")
        advanceUntilIdle()
        vm.selectMinSalary(20_000)
        advanceUntilIdle()
        vm.onCityFilterChange("Krakow")
        advanceUntilIdle()
        vm.selectSort(SearchSort.SALARY_DESC)
        advanceUntilIdle()

        val last = captured.last()
        assertEquals("kotlin", last.query)
        assertEquals("remote", last.workMode)
        assertEquals("SENIOR", last.level)
        assertEquals("B2B", last.contractType)
        assertEquals(20_000, last.minSalary)
        assertEquals("Krakow", last.city)
        assertEquals(SearchSort.SALARY_DESC, last.sort)
        assertEquals(5, vm.uiState.value.activeFilterCount)

        // Toggling same filter off removes it
        vm.toggleLevel("SENIOR")
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedLevel)
        assertEquals(4, vm.uiState.value.activeFilterCount)

        // clearFilters preserves query & sort while clearing all facets
        vm.clearFilters()
        advanceUntilIdle()
        assertEquals("kotlin", vm.uiState.value.query)
        assertEquals(SearchSort.SALARY_DESC, vm.uiState.value.sort)
        assertEquals(0, vm.uiState.value.activeFilterCount)

        // clearAll clears query as well
        vm.clearAll()
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.query)
    }

    @Test
    fun errorOnSearch_recoversOnRetry() = runTest {
        var shouldFail = true
        val sample = SampleSeedData.sampleBestOffers.take(1)
        val repo = object : OffersRepository {
            override suspend fun getOffersAroundYou(query: String) = Result.success(sample)
            override suspend fun getBestOffersForYou(query: String) = Result.success(sample)
            override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
            override suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> {
                return if (shouldFail) {
                    Result.failure(IOException("Timeout connecting to backend"))
                } else {
                    Result.success(SearchOffersResult(total = 1, offers = sample))
                }
            }
        }

        val vm = SearchViewModel(repo, debounceMs = 0L)
        advanceUntilIdle()

        assertEquals("Timeout connecting to backend", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.results.isEmpty())

        shouldFail = false
        vm.retry()
        advanceUntilIdle()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.totalCount)
        assertEquals(1, vm.uiState.value.results.size)
    }
}
