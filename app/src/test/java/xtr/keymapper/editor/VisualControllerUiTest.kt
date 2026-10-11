package xtr.keymapper.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualControllerUiTest {
    @get:Rule val compose = createComposeRule()
    private fun session() = VisualMappingSession("G8", setOf("APPLICATION com.mobile.legends", "SCREENSIZE 2280 1080",
        "STICK_AIM BTN_TR 100 200 123 2 3 0.2 1", "BTN_TL2 300 400 0 150"))
    private fun show(s: VisualMappingSession, ready: Boolean = true, close: Boolean = false,
                     save: () -> String? = { null }, closed: () -> Unit = {}, place: () -> Unit = {}) {
        compose.setContent { VisualControllerEditor(s, true, ready, close, {}, save, closed, place, "G8 test fixture") }
    }
    private fun selectRB() = compose.onNodeWithTag("control:BTN_TR").performScrollTo().performClick()
    @Test fun selectingAimShowsItsExistingParametersWithoutChangingDraft() {
        val s = session(); var saves = 0
        show(s, save = { saves++; null }); selectRB()
        compose.onNode(hasText("Hold & aim") and hasClickAction()).performScrollTo().assertIsSelected()
        compose.onNodeWithText("123").assertExists(); compose.onNodeWithText("0.2").assertExists()
        Assert.assertFalse(s.dirty); Assert.assertEquals(0, saves)
    }
    @Test fun typeReplacementRequiresConfirmationAndCancelKeepsOriginal() {
        val s = session(); show(s); selectRB()
        compose.onNodeWithText("Repeat").performScrollTo().performClick()
        compose.onNodeWithText("Apply to draft").performScrollTo().performClick()
        compose.onNodeWithText("Confirm mapping change").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        Assert.assertFalse(s.dirty)
    }
    @Test fun measuredTargetIsPreviewedAndAppliedOnlyOnExplicitAction() {
        val s = session(); var placements = 0; show(s, place = { placements++ }); selectRB()
        compose.onNodeWithText("Place Target").performScrollTo().performClick()
        val plane = compose.onNodeWithTag("target-plane")
        val bounds = plane.fetchSemanticsNode().boundsInRoot
        plane.performTouchInput { click(Offset(50f,50f)) }
        Assert.assertFalse(s.dirty); Assert.assertEquals(1, placements)
        compose.onNodeWithText("Review target").performClick()
        compose.onNodeWithText("Apply to draft").performScrollTo().performClick()
        val aim = s.profile.aimKeys.single()
        Assert.assertEquals(50f / bounds.width * 2280f, aim.x, .1f)
        Assert.assertEquals(50f / bounds.height * 1080f, aim.y, .1f)
        Assert.assertEquals(123f, aim.radius, 0f); Assert.assertTrue(aim.invertY)
    }
    @Test fun placementIsDisabledUntilRuntimeReportsAllTouchesReleased() {
        val s = session(); show(s, ready = false); selectRB()
        compose.onNodeWithText("Place Target").performScrollTo().assertIsNotEnabled()
        Assert.assertFalse(s.dirty)
    }
    @Test fun closeGuardCancelHasNoSaveOrCloseSideEffects() {
        val s = session(); s.replace("DPAD_LEFT", "DPAD_LEFT 10 20 0")
        var saves = 0; var closes = 0; show(s, close = true, save = { saves++; null }, closed = { closes++ })
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        Assert.assertEquals(0,saves); Assert.assertEquals(0,closes)
    }
    @Test
    @Config(sdk = [35], qualifiers = "w760dp-h420dp-land")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun landscapeFixtureRendersControllerPreview() {
        show(session())
        compose.onNodeWithTag("controller-diagram").assertExists()
        val image = compose.onRoot().captureToImage()
        val bitmap = image.asAndroidBitmap()
        val output = java.io.File("build/ui-previews/controller-editor.png")
        output.parentFile.mkdirs()
        output.outputStream().use { Assert.assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
        Assert.assertTrue(output.length() > 0)
    }

}
