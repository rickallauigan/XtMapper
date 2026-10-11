package xtr.keymapper

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileDetailsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun inspectionAndDismissNeverInvokeSelectOrEdit() {
        var selected = 0; var edited = 0; var dismissed = 0
        val details = SavedProfileDetails("G8 v0.3", "com.mobile.legends", true, ProfileMappingSummary(emptyList()), "2280 × 1080")
        compose.setContent { MaterialTheme { ProfileDetailsDialog(details, "Mobile Legends", false,
            { dismissed++ }, { selected++ }, { edited++ }) } }
        compose.onNodeWithText("G8 v0.3").assertIsDisplayed()
        compose.onNodeWithText("No mappings yet. Use Edit Keymap to add controls.").assertExists()
        compose.onNodeWithContentDescription("Close mapping details").performClick()
        Assert.assertEquals(1, dismissed); Assert.assertEquals(0, selected); Assert.assertEquals(0, edited)
    }
    @Test fun selectingRequiresExplicitAction() {
        var selected = 0
        val details = SavedProfileDetails("KBM", "com.mobile.legends", false, ProfileMappingSummary(emptyList()), "2280 × 1080")
        compose.setContent { MaterialTheme { ProfileDetailsDialog(details, "Mobile Legends", false, {}, { selected++ }, {}) } }
        Assert.assertEquals(0, selected)
        compose.onNodeWithText("Select Profile").performClick(); Assert.assertEquals(1, selected)
    }
    @Test fun eachCardOpensItsOwnSavedProfileWithoutActivatingIt() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = xtr.keymapper.keymap.KeymapProfiles(context).sharedPref
        prefs.edit().clear().putStringSet("GameSir", setOf("APPLICATION com.mobile.legends", "BTN_TL2 10 20 0 150"))
            .putStringSet("Keyboard", setOf("APPLICATION com.mobile.legends", "KEY_F 30 40 0")).commit()
        var activations = 0
        val state = androidx.compose.runtime.mutableStateOf(AppState(profiles = listOf(
            GameProfile("GameSir", "Mobile Legends", "com.mobile.legends"),
            GameProfile("Keyboard", "Mobile Legends", "com.mobile.legends"))))
        compose.setContent { KeymapperApp(state, onSelectProfile = { activations++ }) }
        compose.onNodeWithTag("profiles-list").performScrollToNode(hasText("GameSir"))
        compose.onNodeWithText("GameSir").performClick()
        compose.onNodeWithTag("details-list").performScrollToNode(hasText("Controller"))
        compose.onNodeWithText("Controller").assertExists()
        compose.onNodeWithContentDescription("Close mapping details").performClick()
        compose.onNodeWithText("Keyboard").performClick()
        compose.onNodeWithTag("details-list").performScrollToNode(hasText("Keyboard / mouse"))
        compose.onNodeWithText("Keyboard / mouse").assertExists()
        Assert.assertEquals(0, activations)
        compose.onNodeWithContentDescription("Close mapping details").performClick()
        Assert.assertEquals(0, activations)
    }
    @Test fun nestedLaunchButtonDoesNotOpenDetails() {
        var launches = 0
        val state = androidx.compose.runtime.mutableStateOf(AppState(profiles = listOf(
            GameProfile("GameSir", "Mobile Legends", "com.mobile.legends"))))
        compose.setContent { KeymapperApp(state, onLaunchProfile = { launches++ }) }
        compose.onNodeWithTag("profiles-list").performScrollToNode(hasContentDescription("Launch"))
        compose.onNodeWithContentDescription("Launch").performClick()
        compose.onNodeWithContentDescription("Close mapping details").assertDoesNotExist()
        Assert.assertEquals(1, launches)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h820dp-port")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun portraitDetailsRendersSavedMappingsPreview() {
        val profile = xtr.keymapper.keymap.ProfileConfiguration.parse("APPLICATION com.mobile.legends\nSCREENSIZE 2280 1080\nENABLED\nBTN_TL2 1981.08 946.58 0 150\nSTICK_AIM BTN_GAMEPAD 1477.88 982.97 180 1 1 0.15 0\nSTICK_AIM BTN_TR 1650 965 180 1 1 0.15 0\nSTICK_CAMERA 2140 690 1.6 1.6 0.15")
        val details = SavedProfileDetails("MLBB GameSir G8 S10e v0.2",profile.packageName,true,ProfileMappingSummary.from(profile),"2280 × 1080")
        compose.setContent { MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(primary = KeymapperTheme.Accent, surface = KeymapperTheme.Surface)) { ProfileDetailsContent(details,"Mobile Legends: Bang Bang",true,{}, {}, {}, onVisualEdit = {}) } }
        val root = compose.onRoot()
        val output = java.io.File("build/ui-previews/profile-details.png"); output.parentFile.mkdirs()
        output.outputStream().use { Assert.assertTrue(root.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
        Assert.assertTrue(output.length() > 10000)
    }

}
