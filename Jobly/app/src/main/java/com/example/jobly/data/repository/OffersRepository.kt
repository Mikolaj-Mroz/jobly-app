package com.example.jobly.data.repository

import com.example.jobly.data.remote.NetworkConfig
import com.example.jobly.data.remote.api.OffersApi
import com.example.jobly.data.remote.dto.ApplyOfferRequestDto
import com.example.jobly.data.remote.dto.toDomain
import com.example.jobly.model.JobOffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

enum class SearchSort(val apiValue: String, val label: String) {
    RELEVANCE("relevance", "Relevance"),
    BEST_MATCH("best_match", "Best match"),
    SALARY_DESC("salary_desc", "Highest salary"),
    NEAREST("nearest", "Nearest"),
    NEWEST("newest", "Newest")
}

data class SearchOffersParams(
    val query: String = "",
    val level: String? = null,
    val contractType: String? = null,
    val workMode: String? = null,
    val city: String? = null,
    val minSalary: Int? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
    val limit: Int = 50,
    val offset: Int = 0
)

data class SearchOffersResult(
    val total: Int,
    val offers: List<JobOffer>
)

interface OffersRepository {
    suspend fun getOffersAroundYou(query: String = ""): Result<List<JobOffer>>
    suspend fun getBestOffersForYou(query: String = ""): Result<List<JobOffer>>
    suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> =
        getBestOffersForYou(params.query).map { SearchOffersResult(it.size, it) }
    suspend fun applyToOffer(offer: JobOffer, cvId: String? = null): Result<Boolean>
}

class MockOffersRepository(
    private val userRepository: UserRepository
) : OffersRepository {

    override suspend fun getOffersAroundYou(query: String): Result<List<JobOffer>> =
        Result.success(filterOffers(SampleSeedData.sampleCloseOffers, query))

    override suspend fun getBestOffersForYou(query: String): Result<List<JobOffer>> =
        Result.success(filterOffers(SampleSeedData.sampleBestOffers, query))

    override suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> {
        val combined = (SampleSeedData.sampleBestOffers + SampleSeedData.sampleCloseOffers)
            .distinctBy { it.id }
        val textFiltered = filterOffers(combined, params.query)
        val faceted = textFiltered.filter { offer ->
            val levelOk = params.level.isNullOrBlank() ||
                offer.level.equals(params.level.trim(), ignoreCase = true)
            val contractOk = params.contractType.isNullOrBlank() ||
                offer.contractType.equals(params.contractType.trim(), ignoreCase = true)
            val workModeOk = params.workMode.isNullOrBlank() || when (params.workMode.trim().lowercase()) {
                "remote" -> offer.description.contains("remote", ignoreCase = true) ||
                    offer.workSchedule.contains("remote", ignoreCase = true)
                "hybrid" -> offer.description.contains("hybrid", ignoreCase = true) ||
                    offer.workSchedule.contains("hybrid", ignoreCase = true)
                else -> !offer.description.contains("remote", ignoreCase = true) &&
                    !offer.description.contains("hybrid", ignoreCase = true)
            }
            val cityOk = params.city.isNullOrBlank() ||
                offer.description.contains(params.city.trim(), ignoreCase = true)
            levelOk && contractOk && workModeOk && cityOk
        }
        val sliced = faceted.drop(params.offset.coerceAtLeast(0)).take(params.limit.coerceAtLeast(1))
        return Result.success(SearchOffersResult(total = faceted.size, offers = sliced))
    }

    override suspend fun applyToOffer(offer: JobOffer, cvId: String?): Result<Boolean> =
        Result.success(userRepository.recordLocalApplication(offer))

    private fun filterOffers(source: List<JobOffer>, query: String): List<JobOffer> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return source
        return source.filter { offer ->
            offer.title.contains(trimmed, ignoreCase = true) ||
                offer.companyName.contains(trimmed, ignoreCase = true) ||
                offer.skills.any { it.contains(trimmed, ignoreCase = true) } ||
                offer.level.contains(trimmed, ignoreCase = true) ||
                offer.contractType.contains(trimmed, ignoreCase = true) ||
                offer.description.contains(trimmed, ignoreCase = true)
        }
    }
}

class RemoteOffersRepository(
    private val api: OffersApi,
    private val userRepository: UserRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : OffersRepository {

    override suspend fun getOffersAroundYou(query: String): Result<List<JobOffer>> =
        suspendResultOf {
            withContext(ioDispatcher) {
                val q = query.trim().ifEmpty { null }
                api.getOffersAroundYou(q).offers.map { it.toDomain() }
            }
        }

    override suspend fun getBestOffersForYou(query: String): Result<List<JobOffer>> =
        suspendResultOf {
            withContext(ioDispatcher) {
                val q = query.trim().ifEmpty { null }
                api.getBestOffersForYou(q).offers.map { it.toDomain() }
            }
        }

    override suspend fun searchOffers(params: SearchOffersParams): Result<SearchOffersResult> =
        suspendResultOf {
            withContext(ioDispatcher) {
                val response = api.searchOffers(
                    query = params.query.trim().ifEmpty { null },
                    level = params.level?.trim()?.ifEmpty { null },
                    contractType = params.contractType?.trim()?.ifEmpty { null },
                    workMode = params.workMode?.trim()?.ifEmpty { null },
                    city = params.city?.trim()?.ifEmpty { null },
                    minSalary = params.minSalary,
                    sort = params.sort.apiValue,
                    limit = params.limit,
                    offset = params.offset
                )
                SearchOffersResult(
                    total = response.total,
                    offers = response.offers.map { it.toDomain() }
                )
            }
        }

    override suspend fun applyToOffer(offer: JobOffer, cvId: String?): Result<Boolean> =
        suspendResultOf {
            withContext(ioDispatcher) {
                val response = api.applyToOffer(
                    offerId = offer.id,
                    request = ApplyOfferRequestDto(cvId = cvId)
                )
                if (response.applied) {
                    val syncedOffer = if (offer.externalUrl == null && response.externalUrl != null) {
                        offer.copy(externalUrl = response.externalUrl)
                    } else {
                        offer
                    }
                    userRepository.recordLocalApplication(syncedOffer)
                }
                response.applied
            }
        }
}

internal suspend inline fun <T> suspendResultOf(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal fun extractErrorMessage(throwable: Throwable): String {
    if (throwable is HttpException) {
        val rawBody = runCatching { throwable.response()?.errorBody()?.string() }.getOrNull()
        if (!rawBody.isNullOrBlank()) {
            val detail = runCatching {
                NetworkConfig.json.parseToJsonElement(rawBody).jsonObject["detail"]?.jsonPrimitive?.content
            }.getOrNull()
            if (!detail.isNullOrBlank()) return detail
        }
        return "Request failed (${throwable.code()})"
    }
    return throwable.message?.takeIf { it.isNotBlank() } ?: "Unexpected error"
}
