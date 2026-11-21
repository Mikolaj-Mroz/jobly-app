package com.example.jobly.data.remote.dto

import com.example.jobly.model.CardAccent
import com.example.jobly.model.JobOffer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JobOfferDto(
    val id: String,
    val initial: String = "A",
    val title: String,
    @SerialName("salary_range") val salaryRange: String,
    @SerialName("salary_unit") val salaryUnit: String = "PLN/month",
    val skills: List<String> = emptyList(),
    @SerialName("company_name") val companyName: String,
    val level: String,
    @SerialName("contract_type") val contractType: String,
    @SerialName("work_schedule") val workSchedule: String,
    val accent: String = "YELLOW",
    val description: String = "",
    @SerialName("external_url") val externalUrl: String? = null,
    @SerialName("match_score") val matchScore: Int? = null,
    @SerialName("match_reasons") val matchReasons: List<String> = emptyList()
)

@Serializable
data class OffersResponseDto(
    val offers: List<JobOfferDto> = emptyList()
)

@Serializable
data class SearchOffersResponseDto(
    val query: String = "",
    val total: Int = 0,
    val offers: List<JobOfferDto> = emptyList()
)

@Serializable
data class ApplyOfferRequestDto(
    @SerialName("cv_id") val cvId: String? = null
)

@Serializable
data class ApplyOfferResponseDto(
    val applied: Boolean = true,
    val message: String? = null,
    @SerialName("external_url") val externalUrl: String? = null
)

fun JobOfferDto.toDomain(): JobOffer = JobOffer(
    id = id,
    initial = initial,
    title = title,
    salaryRange = salaryRange,
    salaryUnit = salaryUnit,
    skills = skills,
    companyName = companyName,
    level = level,
    contractType = contractType,
    workSchedule = workSchedule,
    accent = runCatching { CardAccent.valueOf(accent.uppercase()) }.getOrDefault(CardAccent.YELLOW),
    description = description,
    externalUrl = externalUrl,
    matchScore = matchScore,
    matchReasons = matchReasons
)
