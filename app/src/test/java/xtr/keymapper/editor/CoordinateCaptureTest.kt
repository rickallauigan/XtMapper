package xtr.keymapper.editor

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.ProfileMappingSummary

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CoordinateCaptureTest {
    private fun target(id: String="a")=CapturedTarget(id,"Quick Buy","Real HUD notes\nQuoted \"item\" ✓",100f,200f,2280,1080,12345,"G8","com.mobile.legends")
    @Test fun explicitStartPointDragTraceUndoResetAndInterruptedGesture() {
        val s=CoordinateCaptureSession(2280f,1080f)
        s.begin(100f,200f);assertNull(s.point)
        s.start();s.begin(100f,200f);s.move(150f,250f);s.end();assertEquals(150f to 250f,s.point);assertTrue(s.selection.trace.isEmpty())
        s.tracing=true;s.begin(300f,400f);s.move(350f,450f);s.end();assertEquals(listOf(300f to 400f,350f to 450f),s.selection.trace)
        s.undo();assertEquals(150f to 250f,s.point)
        s.begin(800f,900f);s.stop();assertEquals(150f to 250f,s.point);assertFalse(s.capturing)
        s.nudge(1,-10);assertEquals(151f to 240f,s.point);s.reset();assertNull(s.point);s.undo();assertEquals(151f to 240f,s.point)
        s.select(9999f,-10f);assertEquals(2279f to 0f,s.point)
        assertThrows(IllegalArgumentException::class.java){s.select(Float.NaN,0f)}
    }
    @Test fun boundedTraceAndRepeatedStopCancelCyclesDoNotPersistOrLeakGestures() {
        val s=CoordinateCaptureSession(1000f,500f);s.tracing=true
        repeat(80) {
            s.start();s.begin(1f,2f);repeat(1000){i->s.move(i.toFloat(),100f)};s.end()
            assertEquals(512,s.selection.trace.size)
            val prior=s.selection;s.begin(3f,4f);s.cancelGesture();assertEquals(prior,s.selection)
            s.stop();s.stop();assertFalse(s.capturing)
        }
    }
    @Test fun transformsPreserveCrosshairPixelsAndNeverAddCutoutOrDensityPadding() {
        val full=TargetPlane(2280f,1080f,profileWidth=2280f,profileHeight=1080f)
        assertEquals(116f,full.toProfile(116f,0f).first,.001f); assertEquals(0f,full.toProfile(116f,0f).second,0f)
        for(p in listOf(full,TargetPlane(1140f,540f,profileWidth=2280f,profileHeight=1080f),TargetPlane(1200f,800f,40f,20f,2400f,1600f))) {
            val v=p.toViewport(1f,10f);val back=p.toProfile(v.first,v.second)
            assertEquals(1f,back.first,.001f);assertEquals(10f,back.second,.001f)
        }
        assertThrows(IllegalArgumentException::class.java){TargetPlane(0f,1f,profileWidth=1f,profileHeight=1f)}
    }
    @Test fun libraryRoundTripMetadataSnippetsDeletionAndConflictProtection() {
        val context=RuntimeEnvironment.getApplication();val store=CapturedTargetStore(context);val t=target()
        assertEquals(emptyList<CapturedTarget>(),store.read());store.save(emptyList(),listOf(t,target("b")))
        assertEquals(listOf(t,target("b")),CapturedTargetStore(context).read())
        val j=JSONObject(t.snippetJson());assertEquals(t.name,j.getString("name"));assertEquals(t.description,j.getString("description"));assertTrue(j.getBoolean("isEnabled"))
        assertEquals(100.0,j.getJSONObject("position").getDouble("x"),0.0)
        assertTrue(t.snippetYaml().contains("isEnabled: true"));assertTrue(t.snippetYaml().contains("position:\n  x: 100.0"))
        assertEquals(t,CapturedTarget.parse(t.json()))
        assertThrows(IllegalArgumentException::class.java){store.save(emptyList(),listOf(t))}
        assertThrows(IllegalArgumentException::class.java){store.save(store.read(),listOf(t,t))}
        store.save(store.read(),emptyList());assertTrue(store.read().isEmpty())
    }
    @Test fun invalidTargetsCorruptionAndTypeConfusionNeverOverwriteLibrary() {
        val context=RuntimeEnvironment.getApplication();val store=CapturedTargetStore(context)
        for(t in listOf({target().copy(name=" ")},{target().copy(x=Float.NaN)},{target().copy(y=1080f)},
            {target().copy(description="\u0000")},{target().copy(input="KEY_BTN_TR")})) assertThrows(IllegalArgumentException::class.java){t()}
        val j=target().json().put("width","2280")
        assertThrows(IllegalArgumentException::class.java){CapturedTarget.parse(j)}
        context.getSharedPreferences("captured_targets_v1",0).edit().putString("targets","corrupt").commit()
        assertThrows(Exception::class.java){store.save(emptyList(),listOf(target()))}
        assertEquals("corrupt",context.getSharedPreferences("captured_targets_v1",0).getString("targets",null))
    }
    @Test fun savedTargetBindingNeedsConfirmationPreservesOthersAndRoundTrips() {
        val context=RuntimeEnvironment.getApplication()
        val original=setOf("APPLICATION com.mobile.legends","SCREENSIZE 2280 1080","ENABLED","BTN_TL2 10 20 0 150","DPAD_UP 30 40 94.5","DPAD_DOWN 50 60 0")
        val s=VisualMappingSession("G8",original);KeymapProfiles(context).sharedPref.edit().putStringSet("G8",original).commit()
        assertThrows(IllegalArgumentException::class.java){s.applyTarget("DPAD_UP",target())};assertEquals(original,s.lines)
        s.applyTarget("DPAD_UP",target(),true)
        assertEquals(original-"DPAD_UP 30 40 94.5",s.lines-"DPAD_UP 100.0 200.0 0")
        val up=s.profile.keys.single{it.code=="DPAD_UP"};assertEquals(0,up.repeatIntervalMs);assertEquals(0f,up.offset,0f)
        assertThrows(IllegalArgumentException::class.java){s.applyTarget("DPAD_UP",target().copy(width=1140),true)}
        assertThrows(IllegalArgumentException::class.java){s.applyTarget("DPAD_UP",target().copy(gamePackage="other.game"),true)}
        s.save(context);val loaded=VisualMappingSession("G8",KeymapProfiles(context).sharedPref.getStringSet("G8",null)!!)
        assertEquals(s.lines,loaded.lines)
        assertEquals("Quick Buy • Touch",ProfileMappingSummary.from(loaded.profile,listOf(target())).rows.first{it.input=="D-UP"}.action)
        assertFalse(ProfileMappingSummary.from(loaded.profile,listOf(target().copy(x=101f))).rows.any{it.action.contains("Quick Buy")})
    }
    @Test fun savedTargetForAimedSkillChangesOnlyItsCenter() {
        val s=VisualMappingSession("G8",setOf("APPLICATION com.mobile.legends","SCREENSIZE 2280 1080","STICK_AIM BTN_TR 300 400 80 1.2 .8 .15 1"))
        s.applyTarget("BTN_TR",target(),true)
        assertEquals(setOf("APPLICATION com.mobile.legends","SCREENSIZE 2280 1080","STICK_AIM BTN_TR 100.0 200.0 80 1.2 .8 .15 1"),s.lines)
    }
    @Test fun phase2FixturesParsePreserveTunedActionsAndExplicitSelectVariation() {
        val folder=java.io.File("../profiles/mlbb/samsung-s10e")
        fun text(name:String)=java.io.File(folder,name).readText()
        val tuned=text("mlbb-gamesir-g8-v0.3-tuned.txt")
        assertEquals(tuned,text("mlbb-gamesir-g8-phase2.txt"))
        for(name in listOf("mlbb-gamesir-g8-v0.1.txt","mlbb-gamesir-g8-v0.2.txt","mlbb-gamesir-g8-v0.3.txt","mlbb-gamesir-g8-v0.3-tuned.txt","mlbb-gamesir-g8-phase2.txt","mlbb-gamesir-g8-phase2-select-chords.txt")) {
            val p=xtr.keymapper.keymap.ProfileConfiguration.parse(text(name))
            assertEquals(2280,p.xRes);assertEquals(1080,p.yRes)
            assertFalse(p.keys.any{it.code=="DPAD_UP"});assertTrue(p.dpadArray.all{it==null})
        }
        val chord=xtr.keymapper.keymap.ProfileConfiguration.parse(text("mlbb-gamesir-g8-phase2-select-chords.txt"))
        assertEquals(3,chord.chords.size);assertFalse(chord.keys.any{it.code=="BTN_SELECT"})
        assertTrue(chord.keys.any{it.code=="BTN_THUMBR"});assertTrue(chord.keys.any{it.code=="BTN_THUMBL"})
        assertTrue(chord.aimKeys.all{it.radius==80f})
    }
    @Test fun chordEditorPreservesStandaloneSelectUntilExplicitRemovalAndSupportsUndo() {
        val original=setOf("SCREENSIZE 2280 1080","BTN_SELECT 1860 536 0","STICK_AIM BTN_TR 100 200 80 1 1 .15 0","BTN_TL2 10 20 0 150")
        val s=VisualMappingSession("G8",original)
        assertThrows(IllegalArgumentException::class.java){s.chord("BTN_SELECT","BTN_TR",1551f,846.5f)};assertEquals(original,s.lines)
        s.replace("BTN_SELECT",null);s.chord("BTN_SELECT","BTN_TR",1551f,846.5f)
        assertEquals("SELECT + RB",s.profile.chords.single().label())
        assertEquals(80f,s.profile.aimKeys.single().radius,0f)
        s.undo();s.undo();assertEquals(original,s.lines)
    }
}
