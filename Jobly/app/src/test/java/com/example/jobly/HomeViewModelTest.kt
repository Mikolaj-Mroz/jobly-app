package com.example.jobly

import com.example.jobly.data.repository.MockOffersRepository
import com.example.jobly.data.repository.MockUserRepository
import com.example.jobly.data.repository.OffersRepository
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.home.HOME_OFFERS_LIMIT
import com.example.jobly.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var userRepository: MockUserRepository
    private lateinit var offersRepository: MockOffersRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = MockUserRepository()
        offersRepository = MockOffersRepository(userRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsCloseOffersBestOffersAndCvs() = runTest {
        val vm = HomeViewModel(offersRepository, userRepository)
        val state = vm.uiState.value

        assertEquals(3, state.closeOffers.size)
        assertEquals(3, state.bestOffers.size)
        assertEquals(3, state.cvItems.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun onSearchQueryChange_filtersOffersAndRestoresOnClear() = runTest {
        val vm = HomeViewModel(offersRepository, userRepository)

        vm.onSearchQueryChange("Kubernetes")
        assertEquals(3, vm.uiState.value.closeOffers.size)

        vm.onSearchQueryChange("nonexistent_skill_xyz")
        assertTrue(vm.uiState.value.closeOffers.isEmpty())
        assertTrue(vm.uiState.value.bestOffers.isEmpty())

        vm.onSearchQueryChange("")
        assertEquals(3, vm.uiState.value.closeOffers.size)
        assertEquals(3, vm.uiState.value.bestOffers.size)
    }

    @Test
    fun addingCvAndApplyingToOffer_updatesStateViaUserRepository() = runTest {
        val vm = HomeViewModel(offersRepository, userRepository)
        userRepository.addCv("Senior-QA.pdf")

        assertEquals("Senior-QA.pdf", vm.uiState.value.cvItems.first().fileName)

        var firstApplyResult = false
        vm.applyToOffer(vm.uiState.value.bestOffers.first()) { firstApplyResult = it }
        assertTrue(firstApplyResult)

        var duplicateApplyResult = true
        vm.applyToOffer(vm.uiState.value.bestOffers.first()) { duplicateApplyResult = it }
        assertFalse(duplicateApplyResult)
    }

    @Test
    fun refreshOffers_limitsCloseAndBestOffersToFive() = runTest {
        val base = SampleSeedData.sampleCloseOffers.first()
        val manyOffers = (1..12).map { i -> base.copy(id = "offer_$i", title = "Role $i") }
        val bigRepo = object : OffersRepository {
            override suspend fun getOffersAroundYou(query: String) = Result.success(manyOffers)
            override suspend fun getBestOffersForYou(query: String) = Result.success(manyOffers)
            override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
        }

        val vm = HomeViewModel(bigRepo, userRepository)
        assertEquals(HOME_OFFERS_LIMIT, vm.uiState.value.closeOffers.size)
        assertEquals(HOME_OFFERS_LIMIT, vm.uiState.value.bestOffers.size)
        assertEquals("offer_5", vm.uiState.value.closeOffers.last().id)
    }
}
