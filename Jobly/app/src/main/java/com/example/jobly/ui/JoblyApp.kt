package com.example.jobly.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jobly.R
import com.example.jobly.data.remote.dto.UpdateUserSettingsRequestDto
import com.example.jobly.data.repository.MAX_CV_PDF_BYTES
import com.example.jobly.data.repository.SampleSeedData
import com.example.jobly.data.repository.SearchSort
import com.example.jobly.data.repository.extractErrorMessage
import com.example.jobly.di.AppContainer
import com.example.jobly.model.CvItem
import com.example.jobly.model.JobOffer
import com.example.jobly.ui.assistant.AssistantScreen
import com.example.jobly.ui.assistant.AssistantUiState
import com.example.jobly.ui.assistant.AssistantViewModel
import com.example.jobly.ui.common.CvThumbnailCache
import com.example.jobly.ui.common.PdfPreviewDialog
import com.example.jobly.ui.help.HelpFeedbackScreen
import com.example.jobly.ui.home.HomeScreen
import com.example.jobly.ui.home.HomeUiState
import com.example.jobly.ui.home.HomeViewModel
import com.example.jobly.ui.notifications.JoblyNotificationHelper
import com.example.jobly.ui.notifications.NotificationsScreen
import com.example.jobly.ui.notifications.NotificationsUiState
import com.example.jobly.ui.notifications.NotificationsViewModel
import com.example.jobly.ui.offer.OfferDetailScreen
import com.example.jobly.ui.onboarding.OnboardingScreen
import com.example.jobly.ui.profile.ProfileScreen
import com.example.jobly.ui.profile.ProfileUiState
import com.example.jobly.ui.profile.ProfileViewModel
import com.example.jobly.ui.search.SearchScreen
import com.example.jobly.ui.search.SearchUiState
import com.example.jobly.ui.search.SearchViewModel
import com.example.jobly.ui.settings.SettingsScreen
import com.example.jobly.ui.theme.AccentYellow
import com.example.jobly.ui.theme.BadgeError
import com.example.jobly.ui.theme.CanvasGray
import com.example.jobly.ui.theme.DrawerScrollbar
import com.example.jobly.ui.theme.DrawerSurface
import com.example.jobly.ui.theme.JoblyTheme
import com.example.jobly.ui.theme.OnPrimaryContainer
import com.example.jobly.ui.theme.OnSecondaryContainer
import com.example.jobly.ui.theme.OnSurface
import com.example.jobly.ui.theme.OnSurfaceVariant
import com.example.jobly.ui.theme.OutlineVariant
import com.example.jobly.ui.theme.SalaryGreen
import com.example.jobly.ui.theme.SecondaryContainer
import com.example.jobly.ui.theme.SurfaceWhite
import com.example.jobly.ui.theme.TopBarDivider
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

enum class JoblyTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
) {
    HOME(R.string.nav_home, R.drawable.ic_nav_home),
    ASSISTANT(R.string.nav_assistant, R.drawable.ic_nav_assistant),
    NOTIFICATIONS(R.string.nav_notifications, R.drawable.ic_nav_notifications),
    PROFILE(R.string.nav_profile, R.drawable.ic_nav_profile)
}

private enum class SecondaryScreen {
    SETTINGS,
    HELP_FEEDBACK,
    SEARCH
}

private const val JOBLY_PREFS_NAME = "jobly_prefs"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

@Composable
fun JoblyApp(appContainer: AppContainer) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(JOBLY_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var showOnboarding by rememberSaveable {
        mutableStateOf(!prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    }

    val homeViewModel: HomeViewModel = viewModel(factory = appContainer.viewModelFactory)
    val assistantViewModel: AssistantViewModel = viewModel(factory = appContainer.viewModelFactory)
    val notificationsViewModel: NotificationsViewModel = viewModel(factory = appContainer.viewModelFactory)
    val profileViewModel: ProfileViewModel = viewModel(factory = appContainer.viewModelFactory)
    val searchViewModel: SearchViewModel = viewModel(factory = appContainer.viewModelFactory)

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val assistantState by assistantViewModel.uiState.collectAsStateWithLifecycle()
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()

    var previewPdf by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    // Re-rank "Best for you" and refresh settings (for any location parsed from CV) when AI updates CV
    LaunchedEffect(assistantState.messages.size) {
        val lastMsg = assistantState.messages.lastOrNull()
        if (lastMsg != null && !lastMsg.isUser && lastMsg.updatedCv != null) {
            profileViewModel.refreshUserData()
            homeViewModel.refreshOffers()
            searchViewModel.retry()
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = resolveDisplayName(context, uri)
        val bytes = readBoundedPdfBytes(context, uri, MAX_CV_PDF_BYTES)
        if (bytes == null) {
            Toast.makeText(
                context,
                "PDF exceeds the 5 MB upload limit or could not be read",
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        profileViewModel.uploadCvPdf(fileName, bytes) { result ->
            result.onSuccess { uploaded ->
                assistantViewModel.selectActiveCv(uploaded)
                profileViewModel.refreshUserData()
                Toast.makeText(context, "Uploaded ${uploaded.fileName}", Toast.LENGTH_SHORT).show()
                homeViewModel.refreshOffers()
                searchViewModel.retry()
            }.onFailure { err ->
                Toast.makeText(context, extractErrorMessage(err), Toast.LENGTH_LONG).show()
            }
        }
    }

    val requestPdfPreview: (CvItem) -> Unit = { cv ->
        profileViewModel.downloadCvPdf(cv.id) { result ->
            result.onSuccess { bytes ->
                previewPdf = cv.fileName to bytes
            }.onFailure { err ->
                Toast.makeText(context, extractErrorMessage(err), Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showOnboarding) {
        OnboardingScreen(
            initialProfile = profileState.profile,
            initialSettings = profileState.settings,
            cvItems = profileState.cvItems,
            isUploadingCv = profileState.isUploadingCv,
            isSaving = profileState.isSavingSettings,
            onUploadPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
            onComplete = { name, phone, email, settingsRequest ->
                profileViewModel.completeOnboarding(name, phone, email, settingsRequest) { result ->
                    prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                    showOnboarding = false
                    result.onSuccess {
                        homeViewModel.refreshOffers()
                        searchViewModel.retry()
                        Toast.makeText(context, "Account setup complete", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, extractErrorMessage(err), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSkip = {
                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                showOnboarding = false
                profileViewModel.updateSettings(
                    UpdateUserSettingsRequestDto(onboardingCompleted = true)
                )
            }
        )
        return
    }

    JoblyAppContent(
        homeState = homeState,
        assistantState = assistantState,
        notificationsState = notificationsState,
        profileState = profileState,
        searchState = searchState,
        onSearchQueryChange = homeViewModel::onSearchQueryChange,
        onSearchEngineQueryChange = searchViewModel::onQueryChange,
        onSearchSubmit = searchViewModel::submitSearch,
        onSearchToggleWorkMode = searchViewModel::toggleWorkMode,
        onSearchToggleLevel = searchViewModel::toggleLevel,
        onSearchToggleContract = searchViewModel::toggleContract,
        onSearchSelectMinSalary = searchViewModel::selectMinSalary,
        onSearchSelectSort = searchViewModel::selectSort,
        onSearchClearFilters = searchViewModel::clearFilters,
        onSearchClearAll = searchViewModel::clearAll,
        onSearchRetry = searchViewModel::retry,
        onApplyOffer = { offer ->
            homeViewModel.applyToOffer(offer) { applied ->
                if (applied) {
                    Toast.makeText(context, "Applied to ${offer.title}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSendPrompt = assistantViewModel::sendPrompt,
        onMarkNotificationRead = notificationsViewModel::markAsRead,
        onRefreshNotifications = notificationsViewModel::refreshNotifications,
        onUpdateProfile = profileViewModel::updateProfile,
        onAddCv = { fileName ->
            profileViewModel.addCv(fileName) { added ->
                assistantViewModel.selectActiveCv(added)
                Toast.makeText(context, "Added ${added.fileName}", Toast.LENGTH_SHORT).show()
                homeViewModel.refreshOffers()
                searchViewModel.retry()
            }
        },
        onSelectAssistantCv = assistantViewModel::selectActiveCv,
        onUploadPdfClick = {
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        },
        onPreviewCv = requestPdfPreview,
        onDeleteCv = { cv ->
            profileViewModel.deleteCv(cv.id) { result ->
                result.onSuccess {
                    Toast.makeText(context, "Deleted ${cv.fileName}", Toast.LENGTH_SHORT).show()
                    homeViewModel.refreshOffers()
                    searchViewModel.retry()
                }.onFailure { err ->
                    Toast.makeText(context, extractErrorMessage(err), Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSaveSettings = { req ->
            profileViewModel.updateSettings(req) { result ->
                result.onSuccess {
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    homeViewModel.refreshOffers()
                    searchViewModel.retry()
                }.onFailure { err ->
                    Toast.makeText(context, extractErrorMessage(err), Toast.LENGTH_SHORT).show()
                }
            }
        },
        onPushDailyDigestNow = {
            notificationsViewModel.triggerDailyDigest(force = true) { result ->
                result.onSuccess { digest ->
                    digest.notification?.let { item ->
                        JoblyNotificationHelper.showMatchNotification(context, item)
                    }
                    Toast.makeText(context, digest.message, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, extractErrorMessage(err), Toast.LENGTH_SHORT).show()
                }
            }
        },
        onRerunOnboarding = { showOnboarding = true },
        onSubmitFeedback = { category, message, email, onDone ->
            profileViewModel.submitFeedback(category, message, email) { result ->
                onDone(result.map { it.id })
            }
        },
        loadCvThumbnail = { cv ->
            CvThumbnailCache.getOrLoadThumbnail(context, cv) { id ->
                appContainer.userRepository.downloadCvPdf(id)
            }
        }
    )

    previewPdf?.let { (fileName, bytes) ->
        PdfPreviewDialog(
            fileName = fileName,
            pdfBytes = bytes,
            onDismiss = { previewPdf = null }
        )
    }
}

@Composable
fun JoblyAppContent(
    homeState: HomeUiState,
    assistantState: AssistantUiState,
    notificationsState: NotificationsUiState,
    profileState: ProfileUiState,
    searchState: SearchUiState = SearchUiState(),
    onSearchQueryChange: (String) -> Unit = {},
    onSearchEngineQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String?) -> Unit = {},
    onSearchToggleWorkMode: (String) -> Unit = {},
    onSearchToggleLevel: (String) -> Unit = {},
    onSearchToggleContract: (String) -> Unit = {},
    onSearchSelectMinSalary: (Int?) -> Unit = {},
    onSearchSelectSort: (SearchSort) -> Unit = {},
    onSearchClearFilters: () -> Unit = {},
    onSearchClearAll: () -> Unit = {},
    onSearchRetry: () -> Unit = {},
    onApplyOffer: (JobOffer) -> Unit,
    onSendPrompt: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onRefreshNotifications: () -> Unit = {},
    onUpdateProfile: (String, String, String) -> Unit,
    onAddCv: (String) -> Unit,
    onSelectAssistantCv: (CvItem) -> Unit = {},
    onUploadPdfClick: () -> Unit = {},
    onPreviewCv: (CvItem) -> Unit = {},
    onDeleteCv: (CvItem) -> Unit = {},
    onSaveSettings: (UpdateUserSettingsRequestDto) -> Unit = {},
    onPushDailyDigestNow: () -> Unit = {},
    onRerunOnboarding: () -> Unit = {},
    onSubmitFeedback: (String, String, String?, (Result<String>) -> Unit) -> Unit = { _, _, _, _ -> },
    loadCvThumbnail: (suspend (CvItem) -> ImageBitmap?)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var activeTab by rememberSaveable { mutableStateOf(JoblyTab.HOME) }
    var secondaryScreen by rememberSaveable { mutableStateOf<SecondaryScreen?>(null) }
    var selectedOffer by remember { mutableStateOf<JobOffer?>(null) }
    var selectedCv by remember { mutableStateOf<CvItem?>(null) }
    var showAddCv by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        if (activeTab == JoblyTab.NOTIFICATIONS) {
            onRefreshNotifications()
        }
    }

    val unreadCount = notificationsState.unreadCount
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)

    BackHandler(
        enabled = selectedOffer != null ||
            secondaryScreen != null ||
            drawerState.isOpen ||
            activeTab != JoblyTab.HOME
    ) {
        when {
            selectedOffer != null -> selectedOffer = null
            secondaryScreen != null -> secondaryScreen = null
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> activeTab = JoblyTab.HOME
        }
    }

    val activeOffer = selectedOffer
    if (activeOffer != null) {
        val alreadyApplied = profileState.appliedOffers.any { it.id == activeOffer.id }
        val safeUrl = activeOffer.externalUrl?.takeIf(::isValidHttpUrl)
        OfferDetailScreen(
            offer = activeOffer,
            alreadyApplied = alreadyApplied,
            hasWebsiteUrl = safeUrl != null,
            onBack = { selectedOffer = null },
            onApplyClick = {
                onApplyOffer(activeOffer)
                safeUrl?.let { openExternalUrl(context, it) }
            }
        )
        return
    }

    when (secondaryScreen) {
        SecondaryScreen.SETTINGS -> {
            SettingsScreen(
                settings = profileState.settings,
                digestStatus = notificationsState.digestStatus,
                isSavingSettings = profileState.isSavingSettings,
                isTriggeringDigest = notificationsState.isTriggeringDigest,
                onBack = { secondaryScreen = null },
                onSaveSettings = onSaveSettings,
                onPushDailyDigestNow = onPushDailyDigestNow,
                onRerunOnboarding = {
                    secondaryScreen = null
                    onRerunOnboarding()
                }
            )
            return
        }
        SecondaryScreen.HELP_FEEDBACK -> {
            HelpFeedbackScreen(
                defaultEmail = profileState.profile.email,
                onBack = { secondaryScreen = null },
                onOpenAssistant = {
                    secondaryScreen = null
                    activeTab = JoblyTab.ASSISTANT
                },
                onSubmitFeedback = onSubmitFeedback
            )
            return
        }
        SecondaryScreen.SEARCH -> {
            SearchScreen(
                state = searchState,
                appliedOfferIds = profileState.appliedOffers.map { it.id }.toSet(),
                onBack = { secondaryScreen = null },
                onQueryChange = onSearchEngineQueryChange,
                onSubmitSearch = onSearchSubmit,
                onToggleWorkMode = onSearchToggleWorkMode,
                onToggleLevel = onSearchToggleLevel,
                onToggleContract = onSearchToggleContract,
                onSelectMinSalary = onSearchSelectMinSalary,
                onSelectSort = onSearchSelectSort,
                onClearFilters = onSearchClearFilters,
                onClearAll = onSearchClearAll,
                onRetry = onSearchRetry,
                onOfferClick = { selectedOffer = it }
            )
            return
        }
        null -> Unit
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            JoblyDrawerContent(
                userName = profileState.profile.name,
                userEmail = profileState.profile.email,
                unreadCount = unreadCount,
                onNavigateTab = { tab ->
                    activeTab = tab
                    scope.launch { drawerState.close() }
                },
                onOpenSearch = {
                    scope.launch { drawerState.close() }
                    secondaryScreen = SecondaryScreen.SEARCH
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    secondaryScreen = SecondaryScreen.SETTINGS
                },
                onOpenHelp = {
                    scope.launch { drawerState.close() }
                    secondaryScreen = SecondaryScreen.HELP_FEEDBACK
                }
            )
        }
    ) {
        Scaffold(
            containerColor = CanvasGray,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                JoblyTopBar(
                    showSearchBar = activeTab == JoblyTab.HOME,
                    searchQuery = searchState.query,
                    onOpenSearch = { secondaryScreen = SecondaryScreen.SEARCH },
                    unreadCount = unreadCount,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNotificationsClick = { activeTab = JoblyTab.NOTIFICATIONS }
                )
            },
            bottomBar = {
                if (!imeVisible) {
                    JoblyBottomBar(
                        activeTab = activeTab,
                        unreadCount = unreadCount,
                        onSelectTab = { activeTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    JoblyTab.HOME -> {
                        HomeScreen(
                            closeOffers = homeState.closeOffers,
                            bestOffers = homeState.bestOffers,
                            cvItems = homeState.cvItems,
                            onOfferClick = { selectedOffer = it },
                            onCvClick = { selectedCv = it },
                            loadCvThumbnail = loadCvThumbnail
                        )
                    }
                    JoblyTab.ASSISTANT -> {
                        AssistantScreen(
                            messages = assistantState.messages,
                            isSending = assistantState.isSending,
                            onSendPrompt = onSendPrompt,
                            activeCvName = assistantState.activeCvName,
                            cvItems = profileState.cvItems,
                            activeCvId = assistantState.activeCvId,
                            onSelectCv = onSelectAssistantCv,
                            onCreateEmptyCvClick = { showAddCv = true },
                            onUploadPdfClick = onUploadPdfClick,
                            onPreviewCvClick = onPreviewCv,
                            onOfferClick = { selectedOffer = it }
                        )
                    }
                    JoblyTab.NOTIFICATIONS -> {
                        NotificationsScreen(
                            notifications = notificationsState.notifications,
                            onNotificationClick = { item ->
                                onMarkNotificationRead(item.id)
                                selectedOffer = item.offer
                            }
                        )
                    }
                    JoblyTab.PROFILE -> {
                        ProfileScreen(
                            profile = profileState.profile,
                            appliedOffers = profileState.appliedOffers,
                            cvItems = profileState.cvItems,
                            isUploadingCv = profileState.isUploadingCv,
                            onUpdateProfile = onUpdateProfile,
                            onOfferClick = { selectedOffer = it },
                            onCvClick = { selectedCv = it },
                            onPreviewCv = onPreviewCv,
                            onDeleteCv = onDeleteCv,
                            onUploadPdfClick = onUploadPdfClick,
                            onCreateBlankCvClick = { showAddCv = true }
                        )
                    }
                }
            }
        }
    }

    selectedCv?.let { cv ->
        val metaLine = buildList {
            add(cv.updatedLabel)
            cv.pageCount?.let { add("$it page(s)") }
            cv.fileSizeBytes?.let { bytes -> add("${(bytes / 1024).coerceAtLeast(1)} KB") }
        }.joinToString(" • ")

        AlertDialog(
            onDismissRequest = { selectedCv = null },
            title = { Text(cv.fileName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status: $metaLine\n\nReady for AI tailoring and applications.")
                    Text(
                        text = "Edit via AI Assistant",
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedCv = null
                                activeTab = JoblyTab.ASSISTANT
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    Text(
                        text = "Delete this CV",
                        color = BadgeError,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BadgeError.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                onDeleteCv(cv)
                                selectedCv = null
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedCv = null
                        onPreviewCv(cv)
                    }
                ) {
                    Text("Preview PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCv = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddCv) {
        var fileName by remember { mutableStateOf("new-cv.pdf") }
        AlertDialog(
            onDismissRequest = { showAddCv = false },
            title = { Text("Add new CV") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("CV filename") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCv(fileName)
                        showAddCv = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCv = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun JoblyTopBar(
    showSearchBar: Boolean,
    searchQuery: String,
    onOpenSearch: () -> Unit,
    unreadCount: Int,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_nav_menu),
                    contentDescription = stringResource(R.string.cd_open_menu),
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = stringResource(R.string.app_name),
                color = OnSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 48.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        onClickLabel = stringResource(R.string.cd_notifications),
                        onClick = onNotificationsClick
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_nav_notifications),
                    contentDescription = stringResource(R.string.cd_notifications),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )

                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 10.dp)
                            .defaultMinSize(minWidth = 16.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(BadgeError)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = SurfaceWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }

        if (showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceWhite)
                    .border(width = 1.dp, color = OutlineVariant, shape = RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = searchQuery.takeIf { it.isNotBlank() } ?: stringResource(R.string.search_hint),
                    color = if (searchQuery.isNotBlank()) OnSurface else OnSurfaceVariant,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = TopBarDivider)
    }
}

@Composable
private fun JoblyBottomBar(
    activeTab: JoblyTab,
    unreadCount: Int,
    onSelectTab: (JoblyTab) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceWhite,
        tonalElevation = 0.dp
    ) {
        JoblyTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            val selected = activeTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = OnSecondaryContainer,
                    unselectedIconColor = OnSurfaceVariant,
                    selectedTextColor = OnSurface,
                    unselectedTextColor = OnSurfaceVariant,
                    indicatorColor = SecondaryContainer
                ),
                icon = {
                    if (tab == JoblyTab.NOTIFICATIONS && unreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = BadgeError,
                                    contentColor = Color.White
                                ) {
                                    Text(unreadCount.toString())
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                modifier = Modifier.semantics {
                    if (tab == JoblyTab.NOTIFICATIONS && unreadCount > 0) {
                        contentDescription = "$label, $unreadCount new notifications"
                    }
                }
            )
        }
    }
}

@Composable
private fun JoblyDrawerContent(
    userName: String,
    userEmail: String,
    unreadCount: Int,
    onNavigateTab: (JoblyTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = DrawerSurface,
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier.width(278.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = OnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (userName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = userName,
                            color = OnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (userEmail.isNotBlank()) {
                        Text(
                            text = userEmail,
                            color = OnSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(AccentYellow)
                    )
                }

                DrawerMenuRow(
                    iconRes = R.drawable.ic_nav_home,
                    label = stringResource(R.string.nav_home)
                ) { onNavigateTab(JoblyTab.HOME) }

                DrawerMenuRow(
                    iconRes = R.drawable.ic_search,
                    label = "Search jobs",
                    onClick = onOpenSearch
                )

                DrawerMenuRow(
                    iconRes = R.drawable.ic_nav_assistant,
                    label = stringResource(R.string.nav_assistant)
                ) { onNavigateTab(JoblyTab.ASSISTANT) }

                DrawerMenuRow(
                    iconRes = R.drawable.ic_nav_notifications,
                    label = stringResource(R.string.nav_notifications),
                    badgeCount = unreadCount
                ) { onNavigateTab(JoblyTab.NOTIFICATIONS) }

                DrawerMenuRow(
                    iconRes = R.drawable.ic_nav_profile,
                    label = stringResource(R.string.nav_profile)
                ) { onNavigateTab(JoblyTab.PROFILE) }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = OutlineVariant.copy(alpha = 0.6f)
                )

                DrawerMenuRow(
                    iconRes = R.drawable.ic_drawer_settings,
                    label = stringResource(R.string.drawer_settings),
                    onClick = onOpenSettings
                )

                DrawerMenuRow(
                    iconRes = R.drawable.ic_drawer_help,
                    label = stringResource(R.string.drawer_help_feedback),
                    onClick = onOpenHelp
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = 12.dp, end = 6.dp, bottom = 48.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .alpha(0.45f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(DrawerScrollbar)
            )
        }
    }
}

@Composable
private fun DrawerMenuRow(
    @DrawableRes iconRes: Int,
    label: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(BadgeError)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = SurfaceWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Image(
            painter = painterResource(R.drawable.ic_drawer_caret),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun JoblyAppPreview() {
    JoblyTheme {
        JoblyAppContent(
            homeState = HomeUiState(
                closeOffers = SampleSeedData.sampleCloseOffers,
                bestOffers = SampleSeedData.sampleBestOffers,
                cvItems = SampleSeedData.sampleCvItems
            ),
            assistantState = AssistantUiState(),
            notificationsState = NotificationsUiState(
                notifications = SampleSeedData.sampleNotifications,
                unreadCount = 3
            ),
            profileState = ProfileUiState(
                profile = SampleSeedData.defaultProfile,
                appliedOffers = SampleSeedData.sampleAppliedOffers,
                cvItems = SampleSeedData.sampleCvItems
            ),
            onSearchQueryChange = {},
            onApplyOffer = {},
            onSendPrompt = {},
            onMarkNotificationRead = {},
            onUpdateProfile = { _, _, _ -> },
            onAddCv = {}
        )
    }
}

private fun isValidHttpUrl(url: String?): Boolean {
    val trimmed = url?.trim()?.lowercase() ?: return false
    return trimmed.startsWith("https://") || trimmed.startsWith("http://")
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser app installed to open offer link", Toast.LENGTH_SHORT).show()
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)?.trim()
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            }
    }
    val tail = uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
    return if (tail.endsWith(".pdf", ignoreCase = true)) tail else "Uploaded-cv.pdf"
}

private fun readBoundedPdfBytes(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    return null
                }
                out.write(buf, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()
}
