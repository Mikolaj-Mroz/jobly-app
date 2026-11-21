package com.example.jobly

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live end-to-end functional UI test launching the real MainActivity on the emulator.
 *
 * Exercises bottom navigation, Assistant '+' CV selection menu, Profile screen,
 * and Sidebar Drawer -> Settings -> Back navigation as a real user.
 */
@RunWith(AndroidJUnit4::class)
class JoblyLiveE2eUiTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun liveApp_navigatesAcrossAllTabs_opensCvMenu_andSidebarSettings() {
        // Wait for Home screen to finish loading (or skip onboarding if shown on fresh install)
        activityRule.waitUntil(timeoutMillis = 10_000) {
            activityRule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() ||
                activityRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (activityRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            activityRule.onNodeWithText("Skip").performClick()
        }

        // Verify Home tab sections render
        activityRule.waitUntil(timeoutMillis = 10_000) {
            activityRule.onAllNodesWithText("Next to you", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText("Next to you", substring = true).assertIsDisplayed()

        // Open Search screen from Home search bar -> search "Python" -> open offer -> back -> back
        activityRule.onNodeWithText("Search your new job").performClick()
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule.onAllNodesWithText("jobs found", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                activityRule.onAllNodesWithText("job found", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithContentDescription("Search jobs input").performTextInput("Python")
        activityRule.waitUntil(timeoutMillis = 10_000) {
            activityRule.onAllNodesWithText(" • ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onAllNodesWithText(" • ", substring = true).onFirst().performClick()
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule.onAllNodesWithText("Job Offer").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText("Job Offer").assertIsDisplayed()
        activityRule.onNodeWithText("Back").performClick()
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule.onAllNodesWithText("Sorted by", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithContentDescription("Back").performClick()
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule.onAllNodesWithText("Next to you", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Assistant tab and verify '+' CV selection menu opens
        activityRule.onAllNodesWithText("Assistant").onFirst().performClick()
        activityRule.onNodeWithContentDescription("Attach file").performClick()
        activityRule.onNodeWithText("+ New empty CV").assertIsDisplayed()
        activityRule.onNodeWithText("Upload PDF CV...").assertIsDisplayed()
        // Dismiss dropdown by pressing back on the menu or selecting an item; tap Upload/dismiss via Notifications
        activityRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Navigate to Notifications tab
        activityRule.onAllNodesWithText("Notifications").onFirst().performClick()
        activityRule.waitForIdle()

        // Navigate to Profile tab
        activityRule.onAllNodesWithText("Profile").onFirst().performClick()
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule.onAllNodesWithText("My CVs", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText("My CVs", substring = true).assertIsDisplayed()

        // Open Sidebar Drawer -> Settings -> Back
        activityRule.onNodeWithContentDescription("Open navigation menu").performClick()
        activityRule.onNodeWithText("Settings").performClick()
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        activityRule.onNodeWithText("Back").performClick()
    }
}
