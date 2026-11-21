package com.example.jobly.data.repository

import com.example.jobly.data.remote.api.UserApi
import com.example.jobly.data.remote.dto.CreateCvRequestDto
import com.example.jobly.data.remote.dto.FeedbackRequestDto
import com.example.jobly.data.remote.dto.UpdateProfileRequestDto
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.data.remote.dto.toDomain
import com.example.jobly.model.CvItem
import com.example.jobly.model.FeedbackReceipt
import com.example.jobly.model.JobOffer
import com.example.jobly.model.UserProfile
import com.example.jobly.model.UserSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

const val MAX_CV_PDF_BYTES = 5 * 1024 * 1024

internal fun validateClientPdfBytes(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return "Selected PDF file is empty."
    if (bytes.size > MAX_CV_PDF_BYTES) return "PDF exceeds the 5 MB upload limit."
    val prefix = bytes.copyOfRange(0, minOf(5, bytes.size)).decodeToString()
    if (prefix != "%PDF-") return "Selected file is not a valid PDF document."
    return null
}

interface UserRepository {
    val userProfile: StateFlow<UserProfile>
    val userSettings: StateFlow<UserSettings>
    val appliedOffers: StateFlow<List<JobOffer>>
    val cvItems: StateFlow<List<CvItem>>

    suspend fun refreshUserData(): Result<Unit>
    suspend fun updateProfile(name: String, phone: String, email: String): Result<UserProfile>
    suspend fun updateSettings(request: UpdateUserSettingsRequestDto): Result<UserSettings>
    suspend fun submitFeedback(
        category: String,
        message: String,
        contactEmail: String? = null
    ): Result<FeedbackReceipt>
    suspend fun addCv(fileName: String): Result<CvItem>
    suspend fun uploadCvPdf(fileName: String, pdfBytes: ByteArray): Result<CvItem>
    suspend fun deleteCv(cvId: String): Result<Unit>
    suspend fun downloadCvPdf(cvId: String): Result<ByteArray>
    fun recordLocalApplication(offer: JobOffer): Boolean
    fun upsertLocalCv(cv: CvItem)
}

class MockUserRepository : UserRepository {
    private val _userProfile = MutableStateFlow(SampleSeedData.defaultProfile)
    override val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _userSettings = MutableStateFlow(UserSettings())
    override val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _appliedOffers = MutableStateFlow(SampleSeedData.sampleAppliedOffers)
    override val appliedOffers: StateFlow<List<JobOffer>> = _appliedOffers.asStateFlow()

    private val _cvItems = MutableStateFlow(SampleSeedData.sampleCvItems)
    override val cvItems: StateFlow<List<CvItem>> = _cvItems.asStateFlow()

    private val localPdfStore = mutableMapOf<String, ByteArray>()

    override suspend fun refreshUserData(): Result<Unit> = Result.success(Unit)

    override suspend fun updateProfile(
        name: String,
        phone: String,
        email: String
    ): Result<UserProfile> {
        val current = _userProfile.value
        val updated = UserProfile(
            name = name.trim().ifBlank { current.name },
            phone = phone.trim().ifBlank { current.phone },
            email = email.trim().ifBlank { current.email }
        )
        _userProfile.value = updated
        return Result.success(updated)
    }

    override suspend fun updateSettings(request: UpdateUserSettingsRequestDto): Result<UserSettings> {
        val current = _userSettings.value
        val updated = current.copy(
            jobAlertsEnabled = request.jobAlertsEnabled ?: current.jobAlertsEnabled,
            dailyDigestEnabled = request.dailyDigestEnabled ?: current.dailyDigestEnabled,
            radiusKm = request.radiusKm ?: current.radiusKm,
            currency = request.currency?.trim()?.uppercase()?.ifEmpty { current.currency } ?: current.currency,
            city = request.city?.trim()?.ifEmpty { current.city } ?: current.city,
            latitude = request.latitude ?: current.latitude,
            longitude = request.longitude ?: current.longitude,
            onboardingCompleted = request.onboardingCompleted ?: current.onboardingCompleted
        )
        _userSettings.value = updated
        return Result.success(updated)
    }

    override suspend fun submitFeedback(
        category: String,
        message: String,
        contactEmail: String?
    ): Result<FeedbackReceipt> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Feedback message cannot be empty"))
        }
        return Result.success(
            FeedbackReceipt(
                id = "fb_${System.currentTimeMillis()}",
                category = category,
                message = trimmed,
                status = "received"
            )
        )
    }

    override suspend fun addCv(fileName: String): Result<CvItem> {
        val normalized = fileName.trim().ifEmpty { "My-new-cv.pdf" }
        val item = CvItem(
            id = "cv_${System.currentTimeMillis()}_${_cvItems.value.size}",
            fileName = normalized,
            updatedLabel = "Just added"
        )
        _cvItems.update { list -> upsertCvInList(list, item) }
        return Result.success(item)
    }

    override suspend fun uploadCvPdf(fileName: String, pdfBytes: ByteArray): Result<CvItem> {
        validateClientPdfBytes(pdfBytes)?.let { reason ->
            return Result.failure(IllegalArgumentException(reason))
        }
        val normalized = fileName.trim().ifEmpty { "Uploaded-cv.pdf" }
        val id = "cv_${System.currentTimeMillis()}_${_cvItems.value.size}"
        localPdfStore[id] = pdfBytes
        val item = CvItem(
            id = id,
            fileName = normalized,
            updatedLabel = "Uploaded just now",
            fileSizeBytes = pdfBytes.size
        )
        _cvItems.update { list -> upsertCvInList(list, item) }
        return Result.success(item)
    }

    override suspend fun deleteCv(cvId: String): Result<Unit> {
        localPdfStore.remove(cvId)
        _cvItems.update { list -> list.filterNot { it.id == cvId } }
        return Result.success(Unit)
    }

    override suspend fun downloadCvPdf(cvId: String): Result<ByteArray> {
        val bytes = localPdfStore[cvId]
            ?: return Result.failure(IllegalStateException("No local PDF bytes available for $cvId"))
        return Result.success(bytes)
    }

    override fun recordLocalApplication(offer: JobOffer): Boolean {
        if (_appliedOffers.value.any { it.id == offer.id }) {
            return false
        }
        _appliedOffers.update { listOf(offer) + it }
        return true
    }

    override fun upsertLocalCv(cv: CvItem) {
        _cvItems.update { list -> upsertCvInList(list, cv) }
    }
}

class RemoteUserRepository(
    private val api: UserApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserRepository {
    private val _userProfile = MutableStateFlow(SampleSeedData.defaultProfile)
    override val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _userSettings = MutableStateFlow(UserSettings())
    override val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _appliedOffers = MutableStateFlow<List<JobOffer>>(emptyList())
    override val appliedOffers: StateFlow<List<JobOffer>> = _appliedOffers.asStateFlow()

    private val _cvItems = MutableStateFlow<List<CvItem>>(emptyList())
    override val cvItems: StateFlow<List<CvItem>> = _cvItems.asStateFlow()

    override suspend fun refreshUserData(): Result<Unit> = suspendResultOf {
        withContext(ioDispatcher) {
            coroutineScope {
                val profileDeferred = async { api.getProfile().toDomain() }
                val settingsDeferred = async { api.getSettings().toDomain() }
                val appsDeferred = async { api.getApplications().applications.map { it.toDomain() } }
                val cvsDeferred = async { api.getCvs().cvs.map { it.toDomain() } }

                _userProfile.value = profileDeferred.await()
                _userSettings.value = settingsDeferred.await()
                _appliedOffers.value = appsDeferred.await()
                _cvItems.value = cvsDeferred.await()
            }
        }
    }

    override suspend fun updateProfile(
        name: String,
        phone: String,
        email: String
    ): Result<UserProfile> = suspendResultOf {
        withContext(ioDispatcher) {
            val updated = api.updateProfile(
                UpdateProfileRequestDto(
                    name = name.trim(),
                    phone = phone.trim(),
                    email = email.trim()
                )
            ).toDomain()
            _userProfile.value = updated
            updated
        }
    }

    override suspend fun updateSettings(request: UpdateUserSettingsRequestDto): Result<UserSettings> =
        suspendResultOf {
            withContext(ioDispatcher) {
                val updated = api.updateSettings(request).toDomain()
                _userSettings.value = updated
                updated
            }
        }

    override suspend fun submitFeedback(
        category: String,
        message: String,
        contactEmail: String?
    ): Result<FeedbackReceipt> = suspendResultOf {
        withContext(ioDispatcher) {
            api.submitFeedback(
                FeedbackRequestDto(
                    category = category.trim().ifEmpty { "General" },
                    message = message.trim(),
                    contactEmail = contactEmail?.trim()?.ifEmpty { null }
                )
            ).toDomain()
        }
    }

    override suspend fun addCv(fileName: String): Result<CvItem> = suspendResultOf {
        withContext(ioDispatcher) {
            val created = api.addCv(CreateCvRequestDto(fileName = fileName.trim())).toDomain()
            _cvItems.update { list -> upsertCvInList(list, created) }
            created
        }
    }

    override suspend fun uploadCvPdf(fileName: String, pdfBytes: ByteArray): Result<CvItem> =
        suspendResultOf {
            validateClientPdfBytes(pdfBytes)?.let { reason ->
                throw IllegalArgumentException(reason)
            }
            withContext(ioDispatcher) {
                val safeName = fileName.trim().ifEmpty { "Uploaded-cv.pdf" }
                val requestBody = pdfBytes.toRequestBody("application/pdf".toMediaType())
                val part = MultipartBody.Part.createFormData("file", safeName, requestBody)
                val uploaded = api.uploadCv(part).toDomain()
                _cvItems.update { list -> upsertCvInList(list, uploaded) }
                uploaded
            }
        }

    override suspend fun deleteCv(cvId: String): Result<Unit> = suspendResultOf {
        withContext(ioDispatcher) {
            api.deleteCv(cvId)
            _cvItems.update { list -> list.filterNot { it.id == cvId } }
        }
    }

    override suspend fun downloadCvPdf(cvId: String): Result<ByteArray> = suspendResultOf {
        withContext(ioDispatcher) {
            api.downloadCv(cvId).use { body -> body.bytes() }
        }
    }

    override fun recordLocalApplication(offer: JobOffer): Boolean {
        if (_appliedOffers.value.any { it.id == offer.id }) {
            return false
        }
        _appliedOffers.update { listOf(offer) + it }
        return true
    }

    override fun upsertLocalCv(cv: CvItem) {
        _cvItems.update { list -> upsertCvInList(list, cv) }
    }
}

private fun upsertCvInList(current: List<CvItem>, cv: CvItem): List<CvItem> =
    listOf(cv) + current.filterNot { it.id == cv.id }
