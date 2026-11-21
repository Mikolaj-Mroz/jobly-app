package com.example.jobly

import com.example.jobly.data.remote.api.NotificationsApi
import com.example.jobly.data.remote.api.OffersApi
import com.example.jobly.data.remote.api.UserApi
import com.example.jobly.data.remote.dto.ApplicationsResponseDto
import com.example.jobly.data.remote.dto.ApplyOfferRequestDto
import com.example.jobly.data.remote.dto.ApplyOfferResponseDto
import com.example.jobly.data.remote.dto.CreateCvRequestDto
import com.example.jobly.data.remote.dto.CvDto
import com.example.jobly.data.remote.dto.CvsResponseDto
import com.example.jobly.data.remote.dto.DailyDigestResultDto
import com.example.jobly.data.remote.dto.DailyDigestStatusDto
import com.example.jobly.data.remote.dto.FeedbackRequestDto
import com.example.jobly.data.remote.dto.FeedbackResponseDto
import com.example.jobly.data.remote.dto.JobOfferDto
import com.example.jobly.data.remote.dto.NotificationDto
import com.example.jobly.data.remote.dto.NotificationsResponseDto
import com.example.jobly.data.remote.dto.OffersResponseDto
import com.example.jobly.data.remote.dto.SearchOffersResponseDto
import com.example.jobly.data.remote.dto.UpdateProfileRequestDto
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.data.remote.dto.UserProfileDto
import com.example.jobly.data.remote.dto.UserSettingsDto
import com.example.jobly.data.repository.MAX_CV_PDF_BYTES
import com.example.jobly.data.repository.MockNotificationsRepository
import com.example.jobly.data.repository.MockUserRepository
import com.example.jobly.data.repository.RemoteNotificationsRepository
import com.example.jobly.data.repository.RemoteOffersRepository
import com.example.jobly.data.repository.RemoteUserRepository
import com.example.jobly.data.repository.SearchOffersParams
import com.example.jobly.ui.notifications.NotificationsViewModel
import com.example.jobly.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsAndProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notificationsViewModel_marksRead_triggersDailyDigest_andReadsStatus() = runTest {
        val repo = MockNotificationsRepository()
        val vm = NotificationsViewModel(repo)

        assertEquals(3, vm.uiState.value.notifications.size)
        assertEquals(3, vm.uiState.value.unreadCount)
        assertEquals(24, vm.uiState.value.digestStatus?.intervalHours)

        vm.markAsRead("notif_1")
        assertEquals(2, vm.uiState.value.unreadCount)
        assertTrue(vm.uiState.value.notifications.first { it.id == "notif_1" }.isRead)

        vm.markAsRead("notif_1")
        assertEquals(2, vm.uiState.value.unreadCount)

        var pushed = false
        vm.triggerDailyDigest(force = true) { pushed = it.getOrNull()?.pushed == true }
        assertTrue(pushed)
        assertEquals(4, vm.uiState.value.notifications.size)
        assertEquals(3, vm.uiState.value.unreadCount)
    }

    @Test
    fun profileViewModel_updatesProfile_addsCv_andCompletesOnboarding() = runTest {
        val repo = MockUserRepository()
        val vm = ProfileViewModel(repo)

        assertEquals("John Workman", vm.uiState.value.profile.name)

        vm.updateProfile("Jane Doe", "+48 500 600 700", "jane@jobly.app")
        assertEquals("Jane Doe", vm.uiState.value.profile.name)
        assertEquals("+48 500 600 700", vm.uiState.value.profile.phone)
        assertEquals("jane@jobly.app", vm.uiState.value.profile.email)

        var addedName = ""
        vm.addCv("Backend-CV.pdf") { addedName = it.fileName }
        assertEquals("Backend-CV.pdf", addedName)
        assertEquals("Backend-CV.pdf", vm.uiState.value.cvItems.first().fileName)

        var onboardSuccess = false
        vm.completeOnboarding(
            name = "Ola Nowak",
            phone = "+48 600 700 800",
            email = "ola@jobly.app",
            settingsRequest = UpdateUserSettingsRequestDto(
                city = "Warszawa",
                radiusKm = 40.0,
                currency = "EUR"
            )
        ) { onboardSuccess = it.isSuccess }

        assertTrue(onboardSuccess)
        assertEquals("Ola Nowak", vm.uiState.value.profile.name)
        assertEquals("Warszawa", vm.uiState.value.settings.city)
        assertEquals("EUR", vm.uiState.value.settings.currency)
        assertTrue(vm.uiState.value.settings.onboardingCompleted)
    }

    @Test
    fun remoteRepositories_mapBackendDtos_validatePdfUpload_andHandleSettingsDigestFeedback() = runTest {
        val sampleDto = JobOfferDto(
            id = "ext_justjoin_1",
            initial = "A",
            title = "Senior QA Engineer",
            salaryRange = "Undisclosed",
            salaryUnit = "",
            skills = listOf("Python", "Pytest", "Kubernetes"),
            companyName = "Acme Cloud",
            level = "SENIOR",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = "BLUE",
            description = "JustJoin offer",
            externalUrl = "https://justjoin.it/job-offer/acme-senior-qa",
            matchScore = 88,
            matchReasons = listOf("Skills: Python, Kubernetes", "Remote-friendly")
        )
        var appliedRequest: ApplyOfferRequestDto? = null
        val markedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()
        val validPdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".toByteArray()

        val fakeUserApi = object : UserApi {
            override suspend fun getProfile() = UserProfileDto("John Workman", "+1 123", "jworkman@mail.com")
            override suspend fun updateProfile(request: UpdateProfileRequestDto) =
                UserProfileDto(request.name, request.phone, request.email)
            override suspend fun getSettings() = UserSettingsDto(
                jobAlertsEnabled = true,
                dailyDigestEnabled = true,
                radiusKm = 25.0,
                currency = "PLN",
                city = "Krakow",
                onboardingCompleted = false
            )
            override suspend fun updateSettings(request: UpdateUserSettingsRequestDto) = UserSettingsDto(
                jobAlertsEnabled = request.jobAlertsEnabled ?: true,
                dailyDigestEnabled = request.dailyDigestEnabled ?: true,
                radiusKm = request.radiusKm ?: 25.0,
                currency = request.currency ?: "PLN",
                city = request.city ?: "Krakow",
                latitude = request.latitude ?: 50.0647,
                longitude = request.longitude ?: 19.9450,
                onboardingCompleted = request.onboardingCompleted ?: false
            )
            override suspend fun submitFeedback(request: FeedbackRequestDto) = FeedbackResponseDto(
                id = "fb_101",
                category = request.category,
                message = request.message,
                status = "received"
            )
            override suspend fun getApplications() = ApplicationsResponseDto(applications = emptyList())
            override suspend fun getCvs() = CvsResponseDto(
                cvs = listOf(
                    CvDto(
                        id = "cv_1",
                        fileName = "My-new-cv.pdf",
                        updatedLabel = "Updated today",
                        pageCount = 1,
                        fileSizeBytes = 1024
                    )
                )
            )
            override suspend fun addCv(request: CreateCvRequestDto) =
                CvDto(id = "cv_2", fileName = request.fileName, updatedLabel = "Just added")
            override suspend fun uploadCv(file: MultipartBody.Part) =
                CvDto(
                    id = "cv_uploaded",
                    fileName = "Uploaded-Backend.pdf",
                    updatedLabel = "Uploaded just now",
                    pageCount = 2,
                    fileSizeBytes = validPdfBytes.size
                )
            override suspend fun deleteCv(cvId: String) {
                deletedIds += cvId
            }
            override suspend fun downloadCv(cvId: String): ResponseBody =
                validPdfBytes.toResponseBody("application/pdf".toMediaType())
        }

        val fakeOffersApi = object : OffersApi {
            override suspend fun getOffersAroundYou(query: String?) = OffersResponseDto(listOf(sampleDto))
            override suspend fun getBestOffersForYou(query: String?) = OffersResponseDto(listOf(sampleDto))
            override suspend fun searchOffers(
                query: String?,
                level: String?,
                contractType: String?,
                workMode: String?,
                city: String?,
                minSalary: Int?,
                sort: String,
                limit: Int,
                offset: Int
            ) = SearchOffersResponseDto(
                query = query.orEmpty(),
                total = 1,
                offers = listOf(sampleDto)
            )
            override suspend fun applyToOffer(offerId: String, request: ApplyOfferRequestDto): ApplyOfferResponseDto {
                appliedRequest = request
                return ApplyOfferResponseDto(
                    applied = true,
                    message = "Applied",
                    externalUrl = "https://justjoin.it/job-offer/acme-senior-qa"
                )
            }
        }

        val fakeNotificationsApi = object : NotificationsApi {
            override suspend fun getNotifications() = NotificationsResponseDto(
                notifications = listOf(
                    NotificationDto(id = "n_1", heading = "New job offer", offer = sampleDto, isRead = false),
                    NotificationDto(id = "n_2", heading = "Earlier offer", offer = sampleDto, isRead = true)
                )
            )
            override suspend fun markAsRead(notificationId: String) {
                markedIds += notificationId
            }
            override suspend fun triggerDailyDigest(force: Boolean) = DailyDigestResultDto(
                pushed = true,
                message = "Pushed daily best match: Senior QA Engineer",
                notification = NotificationDto(
                    id = "daily_99",
                    heading = "Daily best match (88% match)",
                    offer = sampleDto,
                    isRead = false
                )
            )
            override suspend fun getDailyDigestStatus() = DailyDigestStatusDto(
                schedulerEnabled = true,
                dailyEnabled = true,
                intervalHours = 24,
                nextRunAt = "2026-11-22T11:25:00+00:00"
            )
        }

        val userRepo = RemoteUserRepository(fakeUserApi, testDispatcher)
        val offersRepo = RemoteOffersRepository(fakeOffersApi, userRepo, testDispatcher)
        val notifRepo = RemoteNotificationsRepository(fakeNotificationsApi, testDispatcher)

        assertTrue(userRepo.refreshUserData().isSuccess)
        assertEquals("John Workman", userRepo.userProfile.value.name)
        assertEquals("Krakow", userRepo.userSettings.value.city)
        assertEquals("cv_1", userRepo.cvItems.value.first().id)

        // Settings update & feedback submission
        val savedSettings = userRepo.updateSettings(
            UpdateUserSettingsRequestDto(
                city = "Gdansk",
                radiusKm = 60.0,
                currency = "USD",
                onboardingCompleted = true
            )
        ).getOrThrow()
        assertEquals("Gdansk", savedSettings.city)
        assertEquals("USD", savedSettings.currency)
        assertEquals("Gdansk", userRepo.userSettings.value.city)

        val receipt = userRepo.submitFeedback("Feature", "Great app!", "me@jobly.app").getOrThrow()
        assertEquals("fb_101", receipt.id)
        assertEquals("received", receipt.status)

        // Client-side validation rejects invalid bytes and >5 MB files before hitting network
        assertTrue(userRepo.uploadCvPdf("bad.pdf", "not-a-pdf".toByteArray()).isFailure)
        assertTrue(userRepo.uploadCvPdf("huge.pdf", ByteArray(MAX_CV_PDF_BYTES + 1)).isFailure)

        // Valid upload adds CV to repository
        val uploaded = userRepo.uploadCvPdf("Uploaded-Backend.pdf", validPdfBytes).getOrThrow()
        assertEquals("cv_uploaded", uploaded.id)
        assertEquals(2, uploaded.pageCount)
        assertEquals("cv_uploaded", userRepo.cvItems.value.first().id)

        // Download & Delete round-trip
        val downloaded = userRepo.downloadCvPdf("cv_uploaded").getOrThrow()
        assertArrayEquals(validPdfBytes, downloaded)
        assertTrue(userRepo.deleteCv("cv_uploaded").isSuccess)
        assertEquals(listOf("cv_uploaded"), deletedIds)
        assertEquals(1, userRepo.cvItems.value.size)

        // Offers map matchScore, matchReasons, and externalUrl
        val around = offersRepo.getOffersAroundYou("").getOrThrow()
        val best = offersRepo.getBestOffersForYou("").getOrThrow()
        val searched = offersRepo.searchOffers(SearchOffersParams(query = "QA")).getOrThrow()
        assertEquals(1, around.size)
        assertEquals(1, searched.total)
        assertEquals(88, searched.offers[0].matchScore)
        assertEquals(88, best[0].matchScore)
        assertEquals("https://justjoin.it/job-offer/acme-senior-qa", best[0].externalUrl)
        assertEquals(listOf("Skills: Python, Kubernetes", "Remote-friendly"), best[0].matchReasons)
        assertEquals("Undisclosed", around[0].salaryRange)
        assertEquals("", around[0].salaryUnit)

        assertTrue(offersRepo.applyToOffer(around[0], cvId = "cv_1").getOrThrow())
        assertEquals("cv_1", appliedRequest?.cvId)
        assertEquals("ext_justjoin_1", userRepo.appliedOffers.value.first().id)

        assertTrue(notifRepo.refreshNotifications().isSuccess)
        assertEquals(2, notifRepo.notifications.value.size)
        assertEquals(1, notifRepo.unreadCount.value)

        // Daily digest trigger prepends unread notification & reads status
        val status = notifRepo.getDailyDigestStatus().getOrThrow()
        assertTrue(status.dailyEnabled)
        assertNotNull(status.nextRunAt)

        val pushResult = notifRepo.triggerDailyDigest(force = true).getOrThrow()
        assertTrue(pushResult.pushed)
        assertEquals("daily_99", notifRepo.notifications.value.first().id)
        assertEquals(2, notifRepo.unreadCount.value)

        assertTrue(notifRepo.markAsRead("n_1").isSuccess)
        assertEquals(listOf("n_1"), markedIds)
        assertEquals(1, notifRepo.unreadCount.value)
        assertTrue(notifRepo.notifications.value.first { it.id == "n_1" }.isRead)
    }
}
