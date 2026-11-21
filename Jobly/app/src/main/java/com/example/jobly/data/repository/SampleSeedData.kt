package com.example.jobly.data.repository

import com.example.jobly.model.CardAccent
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import com.example.jobly.model.NotificationItem
import com.example.jobly.model.UserProfile

object SampleSeedData {

    val defaultProfile = UserProfile(
        name = "John Workman",
        phone = "+1 123 456 789",
        email = "jworkman@mail.com"
    )

    val sampleCloseOffers: List<JobOffer> = listOf(
        JobOffer(
            id = "close_1",
            initial = "A",
            title = "Quality Assurance Engineer",
            salaryRange = "16 800 - 22 680",
            salaryUnit = "PLN/month",
            skills = listOf("Kubernetes", "Elk", "Linux"),
            companyName = "IT DEV COMPANY sp. z o.o.",
            level = "MID",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = CardAccent.YELLOW,
            description = "Automate end-to-end tests across Kubernetes microservices and ELK observability pipelines in Krakow."
        ),
        JobOffer(
            id = "close_2",
            initial = "A",
            title = "Quality Assurance Engineer",
            salaryRange = "16 800 - 22 680",
            salaryUnit = "PLN/month",
            skills = listOf("Kubernetes", "Elk", "Linux"),
            companyName = "IT DEV COMPANY sp. z o.o.",
            level = "MID",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = CardAccent.BLUE,
            description = "Design resilient CI quality gates and integration harnesses for cloud-native Linux services."
        ),
        JobOffer(
            id = "close_3",
            initial = "A",
            title = "Quality Assurance Engineer",
            salaryRange = "16 800 - 22 680",
            salaryUnit = "PLN/month",
            skills = listOf("Kubernetes", "Elk", "Linux"),
            companyName = "IT DEV COMPANY sp. z o.o.",
            level = "MID",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = CardAccent.YELLOW_ALT,
            description = "Lead regression verification and performance profiling for high-throughput enterprise APIs."
        )
    )

    val sampleBestOffers: List<JobOffer> = listOf(
        JobOffer(
            id = "best_1",
            initial = "A",
            title = "Quality Assurance Engineer",
            salaryRange = "16 800 - 22 680",
            salaryUnit = "PLN/month",
            skills = listOf("Kubernetes", "Elk", "Linux"),
            companyName = "IT DEV COMPANY sp. z o.o.",
            level = "MID",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = CardAccent.PINK,
            description = "High-match remote-hybrid QA role aligned with your Kubernetes and Linux testing experience."
        ),
        JobOffer(
            id = "best_2",
            initial = "A",
            title = "Quality Assurance Engineer",
            salaryRange = "16 800 - 22 680",
            salaryUnit = "PLN/month",
            skills = listOf("Kubernetes", "Elk", "Linux"),
            companyName = "IT DEV COMPANY sp. z o.o.",
            level = "MID",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = CardAccent.GREEN,
            description = "Collaborate with platform engineers to validate cluster deployments and log alerting."
        ),
        JobOffer(
            id = "best_3",
            initial = "A",
            title = "Quality Assurance Engineer",
            salaryRange = "16 800 - 22 680",
            salaryUnit = "PLN/month",
            skills = listOf("Kubernetes", "Elk", "Linux"),
            companyName = "IT DEV COMPANY sp. z o.o.",
            level = "MID",
            contractType = "B2B",
            workSchedule = "Full-Time",
            accent = CardAccent.YELLOW_ALT,
            description = "Own release sign-off workflows and automated test suites for core customer products."
        )
    )

    val sampleCvItems: List<CvItem> = listOf(
        CvItem(id = "cv_1", fileName = "My-new-cv.pdf", updatedLabel = "Updated today"),
        CvItem(id = "cv_2", fileName = "My-new-cv.pdf", updatedLabel = "Updated 3 days ago"),
        CvItem(id = "cv_3", fileName = "My-new-cv.pdf", updatedLabel = "Updated last week")
    )

    val sampleNotifications: List<NotificationItem> = listOf(
        NotificationItem(id = "notif_1", heading = "New job offer for you", offer = sampleCloseOffers[0]),
        NotificationItem(id = "notif_2", heading = "New job offer for you", offer = sampleBestOffers[0]),
        NotificationItem(id = "notif_3", heading = "New job offer for you", offer = sampleCloseOffers[1])
    )

    val sampleAppliedOffers: List<JobOffer> = listOf(sampleCloseOffers[0])
}
