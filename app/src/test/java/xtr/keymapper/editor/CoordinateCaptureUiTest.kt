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
@Config(sdk=[35],qualifiers="w760dp-h420dp-land")
class CoordinateCaptureUiTest {
    @get:Rule val compose=createComposeRule()
    private fun show(s: CoordinateCaptureSession,ready:Boolean=true) {
        compose.setContent {CoordinateCaptureUi(s,"G8","com.mobile.legends",true,ready,false,{}, {}, {})}
    }
    @Test fun captureOnlyOnExplicitStartShowsCoordinatesAndStopPreventsNewMeasurement() {
        val s=CoordinateCaptureSession(2280f,1080f);show(s)
        val plane=compose.onNodeWithTag("capture-plane")
        val bounds=plane.fetchSemanticsNode().boundsInRoot
        plane.performTouchInput {click(Offset(bounds.width-50f,bounds.height-50f))}
        Assert.assertNull(s.point)
        compose.onNodeWithText("Start Capture").performClick()
        plane.performTouchInput {click(Offset(bounds.width-50f,bounds.height-50f))}
        Assert.assertNotNull(s.point)
        val v=TargetPlane(bounds.width,bounds.height,profileWidth=2280f,profileHeight=1080f).toViewport(s.point!!.first,s.point!!.second)
        Assert.assertEquals(bounds.width-50f,v.first,.01f);Assert.assertEquals(bounds.height-50f,v.second,.01f)
        compose.onNodeWithTag("capture-readout").assertTextContains("X",substring=true)
        compose.onNodeWithText("Stop Capture").performClick()
        val old=s.point;plane.performTouchInput {click(Offset(bounds.width-100f,bounds.height-100f))};Assert.assertEquals(old,s.point)
    }
    @Test fun pauseReadinessAndWrongOrientationDisableStart() {
        val s=CoordinateCaptureSession(2280f,1080f);show(s,false)
        compose.onNodeWithText("Start Capture").assertIsNotEnabled();Assert.assertFalse(s.capturing)
    }
    @Test fun explicitTargetSavePersistsMetadataButClosingUnsavedEditsDoesNot() {
        val s=CoordinateCaptureSession(2280f,1080f);s.select(1900f,800f);show(s)
        compose.onNodeWithText("Target name").performScrollTo().performTextInput("Quick Buy")
        compose.onNodeWithText("Description / HUD notes").performScrollTo().performTextInput("Training HUD fixture")
        compose.onNodeWithText("Save target").performScrollTo().performClick()
        val store=CapturedTargetStore(org.robolectric.RuntimeEnvironment.getApplication())
        Assert.assertEquals("Quick Buy",store.read().single().name)
        Assert.assertEquals("Training HUD fixture",store.read().single().description)
        val saved=store.read()
        compose.onNodeWithText("Target name").performScrollTo().performTextInput(" unsaved")
        compose.onNodeWithText("Cancel / Done").performScrollTo().performClick()
        compose.onNodeWithText("Discard unsaved / Close").performClick()
        Assert.assertEquals(saved,store.read())
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun landscapePaletteAndCrosshairRenderWithoutHidingSelectedTarget() {
        val s=CoordinateCaptureSession(2280f,1080f);s.select(1900f,800f);show(s)
        compose.onNodeWithTag("capture-readout").assertTextContains("1900.0",substring=true)
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val out=java.io.File("build/ui-previews/coordinate-capture.png");out.parentFile.mkdirs()
        out.outputStream().use {Assert.assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        Assert.assertTrue(out.length()>0)
    }
}
