package com.example.jobly

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.data.repository.SearchSort
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import com.example.jobly.model.UserProfile
import com.example.jobly.ui.assistant.AssistantScreen
import com.example.jobly.ui.help.HelpFeedbackScreen
import com.example.jobly.ui.offer.OfferDetailScreen
import com.example.jobly.ui.profile.ProfileScreen
import com.example.jobly.ui.search.SearchScreen
import com.example.jobly.ui.search.SearchUiState
import com.example.jobly.ui.theme.JoblyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box functional UI tests running on a real Android device/emulator.
 *
 * Drives screens exclusively through visible text, content descriptions, taps,
 * text entry, and scrolling—never reaching into internal state.
 */
@RunWith(AndroidJUnit4::class)
class JoblyFunctionalUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offerDetailScreen_formatsAboutThisRoleAndHandlesApplyClick() {
        var applied = false
        val offer = SampleSeedData.sampleBestOffers.first().copy(
            title = "Senior Kotlin Engineer",
            companyName = "CloudWorks",
            externalUrl = "https://justjoin.it/job-offer/cloudworks-kotlin",
            description = "Workplace: Remote (Kraków, -) | Category: js | Required skills: Kotlin (5/5), Compose (4/5) | Languages: en C1 | Offer URL: https://justjoin.it/job-offer/cloudworks-kotlin"
        )

        composeRule.setContent {
            JoblyTheme {
                OfferDetailScreen(
                    offer = offer,
                    alreadyApplied = false,
                    hasWebsiteUrl = true,
                    onBack = {},
                    onApplyClick = { applied = true }
                )
            }
        }

        composeRule.onNodeWithText("Senior Kotlin Engineer").assertIsDisplayed()
        composeRule.onNodeWithText("About this role").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Workplace: Remote (Kraków)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Category: JavaScript", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Kotlin (5/5)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Languages: EN (C1)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Offer URL", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("Apply on website").performClick()
        assertTrue(applied)
    }

    @Test
    fun assistantScreen_plusButtonOpensCvMenu_selectsCv_andCreatesEmptyCv() {
        val cv1 = CvItem(id = "cv_1", fileName = "Primary-CV.pdf", updatedLabel = "Today")
        val cv2 = CvItem(id = "cv_2", fileName = "Backend-CV.pdf", updatedLabel = "Yesterday")
        var selectedCv: CvItem? = null
        var createEmptyClicked = false
        var uploadClicked = false

        composeRule.setContent {
            JoblyTheme {
                AssistantScreen(
                    messages = emptyList(),
                    isSending = false,
                    onSendPrompt = {},
                    activeCvName = cv1.fileName,
                    cvItems = listOf(cv1, cv2),
                    activeCvId = cv1.id,
                    onSelectCv = { selectedCv = it },
                    onCreateEmptyCvClick = { createEmptyClicked = true },
                    onUploadPdfClick = { uploadClicked = true }
                )
            }
        }

        // Open '+' menu and verify available CVs + actions
        composeRule.onNodeWithContentDescription("Attach file").performClick()
        composeRule.onNodeWithText("✓ Primary-CV.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("Backend-CV.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("+ New empty CV").assertIsDisplayed()
        composeRule.onNodeWithText("Upload PDF CV...").assertIsDisplayed()

        // Select second CV
        composeRule.onNodeWithText("Backend-CV.pdf").performClick()
        assertEquals(cv2.id, selectedCv?.id)

        // Open '+' menu again and choose '+ New empty CV'
        composeRule.onNodeWithContentDescription("Attach file").performClick()
        composeRule.onNodeWithText("+ New empty CV").performClick()
        assertTrue(createEmptyClicked)

        // Open '+' menu again and choose 'Upload PDF CV...'
        composeRule.onNodeWithContentDescription("Attach file").performClick()
        composeRule.onNodeWithText("Upload PDF CV...").performClick()
        assertTrue(uploadClicked)
    }

    @Test
    fun profileScreen_expandsAndCollapsesMyApplicationsAndManageCvsInline() {
        val appliedOffer = SampleSeedData.sampleCloseOffers.first().copy(
            title = "Staff Android Architect",
            companyName = "MobileLabs"
        )
        val cv = CvItem(id = "cv_1", fileName = "Android-Lead.pdf", updatedLabel = "Updated today")

        composeRule.setContent {
            JoblyTheme {
                ProfileScreen(
                    profile = UserProfile(
                        name = "Anna Kowalska",
                        phone = "+48 500 100 200",
                        email = "anna@example.com"
                    ),
                    appliedOffers = listOf(appliedOffer),
                    cvItems = listOf(cv),
                    onUpdateProfile = { _, _, _ -> }
                )
            }
        }

        // Collapsed initially
        composeRule.onNodeWithText("Staff Android Architect").assertDoesNotExist()
        composeRule.onNodeWithText("Android-Lead.pdf").assertDoesNotExist()

        // Expand My applications
        composeRule.onNodeWithText("My applications", substring = true).performClick()
        composeRule.onNodeWithText("Staff Android Architect").assertIsDisplayed()

        // Expand My CVs
        composeRule.onNodeWithText("My CVs", substring = true).performClick()
        composeRule.onNodeWithText("Android-Lead.pdf").assertIsDisplayed()
    }

    @Test
    fun helpFeedbackScreen_submitsFeedbackAndDisplaysConfirmation() {
        var submittedMessage: String? = null

        composeRule.setContent {
            JoblyTheme {
                HelpFeedbackScreen(
                    defaultEmail = "user@example.com",
                    onBack = {},
                    onOpenAssistant = {},
                    onSubmitFeedback = { _, message, _, onDone ->
                        submittedMessage = message
                        onDone(Result.success("fb_42"))
                    }
                )
            }
        }

        composeRule.onNodeWithText("Tell us what's working or what to improve", substring = true)
            .performScrollTo()
            .performTextInput("Love the new CV selection menu!")
        composeRule.onNodeWithText("Send feedback").performScrollTo().performClick()

        assertEquals("Love the new CV selection menu!", submittedMessage)
        composeRule.onNodeWithText("fb_42", substring = true).assertIsDisplayed()
    }

    @Test
    fun searchScreen_filtersSortsAndOpensOfferDetail() {
        val sampleOffer = SampleSeedData.sampleBestOffers.first().copy(
            title = "Senior Python Platform Engineer",
            companyName = "NordCloud",
            matchScore = 94,
            matchReasons = listOf("Skills: Python, Kubernetes")
        )
        var typedQuery = ""
        var toggledMode: String? = null
        var selectedSort: SearchSort? = null
        var clickedOffer: JobOffer? = null

        composeRule.setContent {
            JoblyTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = typedQuery,
                        results = listOf(sampleOffer),
                        totalCount = 1,
                        hasSearched = true
                    ),
                    autoFocus = false,
                    onBack = {},
                    onQueryChange = { typedQuery = it },
                    onSubmitSearch = {},
                    onToggleWorkMode = { toggledMode = it },
                    onToggleLevel = {},
                    onToggleContract = {},
                    onSelectMinSalary = {},
                    onSelectSort = { selectedSort = it },
                    onClearFilters = {},
                    onClearAll = {},
                    onRetry = {},
                    onOfferClick = { clickedOffer = it }
                )
            }
        }

        composeRule.onNodeWithText("1 job found").assertIsDisplayed()
        composeRule.onNodeWithText("Senior Python Platform Engineer").assertIsDisplayed()

        // Enter query
        composeRule.onNodeWithContentDescription("Search jobs input").performTextInput("Python")
        assertEquals("Python", typedQuery)

        // Toggle filter chip
        composeRule.onNodeWithText("Remote").performClick()
        assertEquals("remote", toggledMode)

        // Change sort
        composeRule.onNodeWithText("Sort: Relevance ▾").performClick()
        composeRule.onNodeWithText("Highest salary").performClick()
        assertEquals(SearchSort.SALARY_DESC, selectedSort)

        // Open offer detail by tapping result card
        composeRule.onNodeWithText("Senior Python Platform Engineer").performClick()
        assertEquals(sampleOffer.id, clickedOffer?.id)
    }
}
