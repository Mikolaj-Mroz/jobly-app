package com.example.jobly.data.remote.api

import com.example.jobly.data.remote.dto.ApplyOfferRequestDto
import com.example.jobly.data.remote.dto.ApplyOfferResponseDto
import com.example.jobly.data.remote.dto.OffersResponseDto
import com.example.jobly.data.remote.dto.SearchOffersResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OffersApi {
    @GET("around-you")
    suspend fun getOffersAroundYou(@Query("query") query: String? = null): OffersResponseDto

    @GET("best-for-you")
    suspend fun getBestOffersForYou(@Query("query") query: String? = null): OffersResponseDto

    @GET("search")
    suspend fun searchOffers(
        @Query("q") query: String? = null,
        @Query("level") level: String? = null,
        @Query("contract_type") contractType: String? = null,
        @Query("work_mode") workMode: String? = null,
        @Query("city") city: String? = null,
        @Query("min_salary") minSalary: Int? = null,
        @Query("sort") sort: String = "relevance",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SearchOffersResponseDto

    @POST("{offerId}/apply")
    suspend fun applyToOffer(
        @Path("offerId") offerId: String,
        @Body request: ApplyOfferRequestDto
    ): ApplyOfferResponseDto
}
