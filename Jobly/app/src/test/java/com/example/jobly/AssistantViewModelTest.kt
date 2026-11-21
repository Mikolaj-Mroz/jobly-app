package com.example.jobly

import com.example.jobly.data.remote.api.AssistantApi
import com.example.jobly.data.remote.dto.AssistantChatRequestDto
import com.example.jobly.data.remote.dto.AssistantChatResponseDto
import com.example.jobly.data.remote.dto.AssistantHealthDto
import com.example.jobly.data.remote.dto.CvDto
import com.example.jobly.data.remote.dto.JobOfferDto
import com.example.jobly.data.repository.MockUserRepository
import com.example.jobly.data.repository.RemoteAssistantRepository
import com.example.jobly.ui.assistant.AssistantViewModel
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

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
    fun remoteAssistant_detectsHealthModel_sendsHistoryWithoutDuplicate_andUpdatesCv() = runTest {
        val capturedRequests = mutableListOf<AssistantChatRequestDto>()
        val fakeApi = object : AssistantApi {
            override suspend fun getHealth() = AssistantHealthDto(
                reachable = true,
                llmBaseUrl = "http://127.0.0.1:1234/v1",
                chatModel = "qwen3-coder-30b-a3b-instruct"
            )

            override suspend fun chat(request: AssistantChatRequestDto): AssistantChatResponseDto {
                capturedRequests += request
                return if (capturedRequests.size == 1) {
                    AssistantChatResponseDto(
                        reply = "I tailored your CV PDF.",
                        model = "qwen3-coder-30b-a3b-instruct",
                        updatedCv = CvDto(
                            id = "cv_1",
                            fileName = "Tailored-QA-CV.pdf",
                            updatedLabel = "Updated just now"
                        )
                    )
                } else {
                    AssistantChatResponseDto(
                        reply = "Your top skills are Kubernetes and Python.",
                        model = "qwen3-coder-30b-a3b-instruct",
                        suggestedOffers = listOf(
                            JobOfferDto(
                                id = "ext_rec_1",
                                title = "Senior Python Engineer",
                                salaryRange = "20 000 - 26 000",
                                companyName = "Acme Cloud",
                                level = "SENIOR",
                                contractType = "B2B",
                                workSchedule = "Full-Time",
                                externalUrl = "https://justjoin.it/job-offer/acme-python",
                                matchScore = 91,
                                matchReasons = listOf("Skills: Python, Kubernetes")
                            )
                        )
                    )
                }
            }
        }

        val userRepository = MockUserRepository()
        val expectedInitialCvId = userRepository.cvItems.value.first().id
        val repository = RemoteAssistantRepository(fakeApi, testDispatcher)
        val viewModel = AssistantViewModel(repository, userRepository)

        assertEquals("qwen3-coder-30b-a3b-instruct", viewModel.uiState.value.activeModel)
        assertEquals(expectedInitialCvId, viewModel.uiState.value.activeCvId)

        viewModel.sendPrompt("Edit my CV")

        assertEquals(1, capturedRequests.size)
        assertEquals("Edit my CV", capturedRequests[0].message)
        assertEquals(expectedInitialCvId, capturedRequests[0].cvId)
        assertTrue(capturedRequests[0].history.isEmpty())

        // Updated CV from /assistant/chat must sync back to UserRepository, AssistantUiState, and ChatMessage
        assertEquals("Tailored-QA-CV.pdf", viewModel.uiState.value.activeCvName)
        assertEquals("cv_1", viewModel.uiState.value.activeCvId)
        assertEquals("Tailored-QA-CV.pdf", userRepository.cvItems.value.first().fileName)
        assertEquals("Tailored-QA-CV.pdf", viewModel.uiState.value.messages[1].updatedCv?.fileName)

        viewModel.sendPrompt("What about my skills?")

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertEquals(4, state.messages.size)
        assertEquals(1, state.messages[3].suggestedOffers.size)
        assertEquals(91, state.messages[3].suggestedOffers.first().matchScore)
        assertEquals("https://justjoin.it/job-offer/acme-python", state.messages[3].suggestedOffers.first().externalUrl)
        assertEquals(2, capturedRequests.size)
        assertEquals("What about my skills?", capturedRequests[1].message)
        assertEquals("cv_1", capturedRequests[1].cvId)
        assertEquals(2, capturedRequests[1].history.size)
        assertEquals("user", capturedRequests[1].history[0].role)
        assertEquals("Edit my CV", capturedRequests[1].history[0].content)
        assertEquals("assistant", capturedRequests[1].history[1].role)
        assertEquals("I tailored your CV PDF.", capturedRequests[1].history[1].content)
        assertFalse(capturedRequests[1].history.any { it.content == "What about my skills?" })
    }

    @Test
    fun sendPrompt_handlesNetworkFailureWithoutCrashing() = runTest {
        val failingApi = object : AssistantApi {
            override suspend fun getHealth() = AssistantHealthDto(
                reachable = true,
                llmBaseUrl = "http://127.0.0.1:1234/v1",
                chatModel = "qwen3-coder-30b-a3b-instruct"
            )

            override suspend fun chat(request: AssistantChatRequestDto): AssistantChatResponseDto {
                throw IOException("Connection refused")
            }
        }

        val repository = RemoteAssistantRepository(failingApi, testDispatcher)
        val viewModel = AssistantViewModel(repository, MockUserRepository())

        viewModel.sendPrompt("Find me a job")

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertEquals(2, state.messages.size)
        assertTrue(state.messages[1].isError)
        assertTrue(state.messages[1].text.contains("Connection refused"))
        assertEquals("Connection refused", state.errorMessage)
    }

    @Test
    fun selectActiveCv_switchesActiveCvAndSendsSelectedCvIdInChat() = runTest {
        val capturedRequests = mutableListOf<AssistantChatRequestDto>()
        val fakeApi = object : AssistantApi {
            override suspend fun getHealth() = AssistantHealthDto(
                reachable = true,
                llmBaseUrl = "http://127.0.0.1:1234/v1",
                chatModel = "qwen3-coder-30b-a3b-instruct"
            )

            override suspend fun chat(request: AssistantChatRequestDto): AssistantChatResponseDto {
                capturedRequests += request
                return AssistantChatResponseDto(
                    reply = "Checked selected CV.",
                    model = "qwen3-coder-30b-a3b-instruct"
                )
            }
        }

        val userRepository = MockUserRepository()
        val addedCv = userRepository.addCv("Backend-Resume.pdf").getOrThrow()
        val repository = RemoteAssistantRepository(fakeApi, testDispatcher)
        val viewModel = AssistantViewModel(repository, userRepository)

        viewModel.selectActiveCv(addedCv)

        assertEquals(addedCv.id, viewModel.uiState.value.activeCvId)
        assertEquals("Backend-Resume.pdf", viewModel.uiState.value.activeCvName)

        viewModel.sendPrompt("Review this CV")

        assertEquals(1, capturedRequests.size)
        assertEquals(addedCv.id, capturedRequests[0].cvId)
    }
}
