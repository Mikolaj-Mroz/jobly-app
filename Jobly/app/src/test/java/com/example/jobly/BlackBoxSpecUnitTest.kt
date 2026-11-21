package com.example.jobly

import com.example.jobly.data.remote.api.AssistantApi
import com.example.jobly.data.remote.dto.AssistantChatRequestDto
import com.example.jobly.data.remote.dto.AssistantChatResponseDto
import com.example.jobly.data.remote.dto.AssistantHealthDto
import com.example.jobly.data.repository.MockNotificationsRepository
import com.example.jobly.data.repository.MockUserRepository
import com.example.jobly.data.repository.OffersRepository
import com.example.jobly.data.repository.RemoteAssistantRepository
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.assistant.AssistantViewModel
import com.example.jobly.ui.common.MarkdownBlock
import com.example.jobly.ui.common.parseInlineMarkdown
import com.example.jobly.ui.common.parseMarkdownBlocks
import com.example.jobly.ui.home.HOME_OFFERS_LIMIT
import com.example.jobly.ui.home.HomeViewModel
import com.example.jobly.ui.notifications.NotificationsViewModel
import com.example.jobly.ui.offer.formatOfferDescriptionMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Specification-driven (black-box) unit tests for Jobly Android.
 *
 * Tests behavioral invariants, boundary conditions, adversarial/malformed inputs,
 * idempotency, and error recovery without coupling to internal implementation details.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlackBoxSpecUnitTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. Markdown & Role Description Formatting Adversarial / Boundary Tests
    // -------------------------------------------------------------------------

    @Test
    fun markdownParser_neverCrashesOnAdversarialEmptyMalformedOrHugeInputs() {
        val adversarialInputs = listOf(
            "",
            "   \n\t\r\n   ",
            "**unclosed bold and *unclosed italic and `unclosed code",
            "```kotlin\nval x = 1\n// unclosed fenced code block",
            "####### seven hashes\n- \n1. \n> ",
            "[bad link](not a url) [empty]() [](https://example.com)",
            (1..400).joinToString("\n") { i -> "- Item **$i** with `code_$i` and *italic*" }
        )

        adversarialInputs.forEach { input ->
            val blocks = parseMarkdownBlocks(input)
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Paragraph -> parseInlineMarkdown(block.text)
                    is MarkdownBlock.BulletItem -> parseInlineMarkdown(block.text)
                    is MarkdownBlock.NumberedItem -> parseInlineMarkdown(block.text)
                    is MarkdownBlock.Heading -> parseInlineMarkdown(block.text)
                    is MarkdownBlock.Quote -> parseInlineMarkdown(block.text)
                    is MarkdownBlock.CodeBlock -> assertNotNull(block.code)
                    is MarkdownBlock.Divider -> Unit
                }
            }
        }
    }

    @Test
    fun formatOfferDescriptionMarkdown_handlesEmptySingleSegmentReversedOrderAndUrlVisibility() {
        assertEquals("", formatOfferDescriptionMarkdown("", hideOfferUrl = true))
        assertEquals("", formatOfferDescriptionMarkdown("   \n  ", hideOfferUrl = false))

        // Single pipe segment or free-form prose passes through untouched
        val prose = "Join our platform team building cloud-native services."
        assertEquals(prose, formatOfferDescriptionMarkdown(prose, hideOfferUrl = true))

        // Reversed key order & irregular spacing still format cleanly
        val messy = "Offer URL: https://justjoin.it/job-offer/x | Languages: en c1, de b1 | Category: js | Workplace: Remote (Kraków, -)"
        val hiddenUrl = formatOfferDescriptionMarkdown(messy, hideOfferUrl = true)
        assertFalse(hiddenUrl.contains("Offer URL"))
        assertTrue(hiddenUrl.contains("**Languages:** EN (C1), DE (B1)"))
        assertTrue(hiddenUrl.contains("**Category:** JavaScript"))
        assertTrue(hiddenUrl.contains("**Workplace:** Remote (Kraków)"))

        // When hideOfferUrl is false, Offer URL is formatted as a Markdown link
        val visibleUrl = formatOfferDescriptionMarkdown(messy, hideOfferUrl = false)
        assertTrue(visibleUrl.contains("[https://justjoin.it/job-offer/x](https://justjoin.it/job-offer/x)"))
    }

    // -------------------------------------------------------------------------
    // 2. Home Feed Cap Invariant & Network Error Recovery
    // -------------------------------------------------------------------------

    @Test
    fun homeViewModel_alwaysCapsCarouselsAtFiveAcrossAllCorpusSizes() = runTest {
        val sample = SampleSeedData.sampleBestOffers.first()
        for (count in listOf(0, 1, 4, 5, 6, 50)) {
            val offers = (1..count).map { i -> sample.copy(id = "o_$i", title = "Engineer $i") }
            val repo = object : OffersRepository {
                override suspend fun getOffersAroundYou(query: String) = Result.success(offers)
                override suspend fun getBestOffersForYou(query: String) = Result.success(offers)
                override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
            }
            val vm = HomeViewModel(repo, MockUserRepository())
            val expected = minOf(count, HOME_OFFERS_LIMIT)
            assertEquals("Failed close cap for count=$count", expected, vm.uiState.value.closeOffers.size)
            assertEquals("Failed best cap for count=$count", expected, vm.uiState.value.bestOffers.size)
            assertTrue(vm.uiState.value.closeOffers.size <= 5)
            assertTrue(vm.uiState.value.bestOffers.size <= 5)
        }
    }

    @Test
    fun homeViewModel_recoversCleanlyWhenBackendFailsThenRecovers() = runTest {
        var shouldFail = true
        val sample = SampleSeedData.sampleCloseOffers.first()
        val flakyRepo = object : OffersRepository {
            override suspend fun getOffersAroundYou(query: String) =
                if (shouldFail) Result.failure(IOException("Server unreachable")) else Result.success(listOf(sample))

            override suspend fun getBestOffersForYou(query: String) =
                if (shouldFail) Result.failure(IOException("Server unreachable")) else Result.success(listOf(sample))

            override suspend fun applyToOffer(offer: JobOffer, cvId: String?) = Result.success(true)
        }

        val vm = HomeViewModel(flakyRepo, MockUserRepository())
        assertTrue(vm.uiState.value.closeOffers.isEmpty())
        assertFalse(vm.uiState.value.isLoading)

        shouldFail = false
        vm.refreshOffers()

        assertEquals(1, vm.uiState.value.closeOffers.size)
        assertEquals(1, vm.uiState.value.bestOffers.size)
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    // -------------------------------------------------------------------------
    // 3. Assistant State Machine & CV Selection Invariants
    // -------------------------------------------------------------------------

    @Test
    fun assistantViewModel_ignoresBlankPrompts_andPreservesCvSelectionAcrossListUpdates() = runTest {
        var chatCalls = 0
        val fakeApi = object : AssistantApi {
            override suspend fun getHealth() = AssistantHealthDto(
                reachable = true,
                llmBaseUrl = "http://127.0.0.1:1234/v1",
                chatModel = "local-model"
            )

            override suspend fun chat(request: AssistantChatRequestDto): AssistantChatResponseDto {
                chatCalls++
                return AssistantChatResponseDto(reply = "OK #$chatCalls", model = "local-model")
            }
        }

        val userRepo = MockUserRepository()
        val cv2 = userRepo.addCv("Second-CV.pdf").getOrThrow()
        val vm = AssistantViewModel(RemoteAssistantRepository(fakeApi, testDispatcher), userRepo)

        // Blank & whitespace prompts must never hit API or add messages
        vm.sendPrompt("")
        vm.sendPrompt("   \n\t  ")
        assertEquals(0, chatCalls)
        assertTrue(vm.uiState.value.messages.isEmpty())

        // Selecting cv2 must stick even when another CV is later added to repository
        vm.selectActiveCv(cv2)
        assertEquals(cv2.id, vm.uiState.value.activeCvId)

        userRepo.addCv("Third-CV.pdf").getOrThrow()
        assertEquals(cv2.id, vm.uiState.value.activeCvId)
        assertEquals("Second-CV.pdf", vm.uiState.value.activeCvName)

        // Deleting the active CV falls back gracefully to another existing CV
        userRepo.deleteCv(cv2.id)
        assertTrue(vm.uiState.value.activeCvId != cv2.id)
        assertNotNull(vm.uiState.value.activeCvId)
    }

    // -------------------------------------------------------------------------
    // 4. Notifications Invariants (Non-existent ID, Idempotent Read, Unread Count)
    // -------------------------------------------------------------------------

    @Test
    fun notificationsViewModel_unreadCountMatchesExactUnreadItemsAndHandlesUnknownIds() = runTest {
        val repo = MockNotificationsRepository()
        val vm = NotificationsViewModel(repo)

        val initialUnread = vm.uiState.value.notifications.count { !it.isRead }
        assertEquals(initialUnread, vm.uiState.value.unreadCount)

        // Unknown ID does not corrupt state or crash
        vm.markAsRead("nonexistent_id_999")
        assertEquals(initialUnread, vm.uiState.value.unreadCount)

        // Marking every notification read drives unreadCount to 0 and never negative
        vm.uiState.value.notifications.map { it.id }.forEach { id ->
            vm.markAsRead(id)
            vm.markAsRead(id) // idempotent second call
        }
        assertEquals(0, vm.uiState.value.unreadCount)
        assertTrue(vm.uiState.value.notifications.all { it.isRead })
    }
}
