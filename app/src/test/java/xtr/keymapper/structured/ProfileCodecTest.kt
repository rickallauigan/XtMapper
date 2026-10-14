package xtr.keymapper.structured

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import xtr.keymapper.keymap.*
import xtr.keymapper.editor.VisualMappingSession

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ProfileCodecTest {
    private fun fixture()=ProfileCodec.fromLegacy("G8",java.io.File("../profiles/mlbb/samsung-s10e/mlbb-gamesir-g8-v0.3-tuned.txt").readLines().toSet())
    @Test fun jsonYamlEquivalentPreserveEveryControllerReferenceAndMetadata() {
        val d=fixture().let{it.copy(profile=it.profile.copy(description="Quotes \" ✓\n中文"),bindings=it.bindings.mapIndexed{i,b->b.copy(name="Action $i ✓",description="Description",enabled=i!=0)})}
        val json=ProfileCodec.encode(d);val parsed=ProfileCodec.decode(json)
        assertEquals(json,ProfileCodec.encode(parsed));assertEquals(parsed,ProfileCodec.decode(ProfileCodec.encode(parsed,true)))
        assertFalse(parsed.bindings[0].enabled);assertEquals(d.bindings[0].name,parsed.bindings[0].name)
        assertEquals(80f,ProfileConfiguration.parse(parsed.runtimeLines().joinToString("\n")).aimKeys.first().radius,0f)
    }
    @Test fun allExistingTypesRoundTripNoMacroFileReadsAndLegacyDefaults() {
        for(line in listOf("KEY_Q 100 200 94.5 150","MOUSE_LEFT 100 200","MOUSE_RIGHT 100 200",
            "AIM_KEY BTN_RIGHT 100 200 80 1 1","STICK_AIM BTN_TR 100 200 80 1 1 .15 0",
            "CAMERA 100 200 1 1 1 KEY_ALT","STICK_CAMERA 100 200 1 1 .15","MOUSE_WALK 100 200 80",
            "MOUSE_AIM 100 200 1 300 400 150 250","SWIPE_KEY KEY_Q 100 200 KEY_E 300 400",
            "MACRO local-id KEY_F","DPAD 10 20 80 100 200 160 160 KEY_W KEY_S KEY_A KEY_D",
            "CHORD BTN_SELECT BTN_TR 100 200 0")) {
            val d=ProfileCodec.fromLegacy("all",setOf("APPLICATION example.game","SCREENSIZE 2280 1080","ENABLED",line))
            val j=ProfileCodec.encode(d);assertEquals(j,ProfileCodec.encode(ProfileCodec.decode(j)))
            assertEquals(ProfileCodec.decode(j),ProfileCodec.decode(ProfileCodec.encode(d,true)))
        }
    }
    @Test fun untrustedSyntaxTypesPathsAliasesDuplicatesAndDepthRejected() {
        val json=ProfileCodec.encode(fixture());val j=JSONObject(json)
        for(mutator in listOf<(JSONObject)->Unit>(
            {it.put("schema_version",2)}, {it.put("unexpected",true)},
            {it.getJSONObject("profile").put("isEnabled","true")},
            {it.getJSONArray("bindings").getJSONObject(0).put("x",1)},
            {it.getJSONArray("bindings").getJSONObject(0).put("action","exec")},
            {it.getJSONArray("bindings").put(it.getJSONArray("bindings").getJSONObject(0))},
            {it.getJSONArray("bindings").getJSONObject(0).getJSONObject("position").put("x",-999999)},
            {it.getJSONArray("bindings").getJSONObject(0).getJSONObject("position").put("x",2279.99999999)})) {
            val bad=JSONObject(json);mutator(bad);assertThrows(Exception::class.java){ProfileCodec.decode(bad.toString())}
        }
        for(bad in listOf("{\"schema_version\":1,\"schema_version\":1}","[".repeat(40)+"]".repeat(40),"!!java.lang.ProcessBuilder [echo, bad]","a: &a [1]\nb: *a","a: 1\na: 2","{schema_version:1}",json+" trailing",json.replace("1981.08","1e999"),"x".repeat(ProfileCodec.MAX_BYTES+1)))
            assertThrows(Exception::class.java){ProfileCodec.decode(bad)}
        val macro=ProfileCodec.fromLegacy("macro",setOf("APPLICATION example.game","SCREENSIZE 1000 1000","MACRO local KEY_F"))
        assertThrows(Exception::class.java){ProfileCodec.decode(ProfileCodec.encode(macro).replace("local","../../secret"))}
    }
    @Test fun pendingImportAtomicStorageDisabledProjectionAndEditorPreservation() {
        val context=RuntimeEnvironment.getApplication();val prefs=KeymapProfiles(context).sharedPref
        val d=fixture().let{it.copy(bindings=it.bindings.mapIndexed{i,b->if(i==0)b.copy(enabled=false,name="Disabled custom ✓") else b})}
        StructuredProfiles.save(context,"G8",d,null,true)
        val original=prefs.getStringSet("G8",null)!!.toSet()
        assertTrue(KeymapProfiles.getProfile(original).disabled);assertTrue(KeymapProfiles.getProfile(original).keys.isEmpty());assertTrue(KeymapProfiles.getProfile(original).aimKeys.isEmpty())
        assertEquals(d.bindings.size,StructuredProfiles.read(context,"G8").bindings.size)
        assertEquals(d.bindings.count{it.enabled},StructuredProfiles.runtime(original).size-2)
        assertThrows(Exception::class.java){StructuredProfiles.save(context,"G8",fixture(),null)};assertEquals(original,prefs.getStringSet("G8",null))
        val session=VisualMappingSession("G8",original)
        session.replace("DPAD_LEFT","DPAD_LEFT 100 200 0");session.save(context)
        val saved=StructuredProfiles.read(context,"G8");assertEquals("Disabled custom ✓",saved.bindings.first{!it.enabled}.name)
        val stored=prefs.getStringSet("G8",null)!!.toSet()
        assertEquals(stored,ProfileConfiguration.validate(stored.joinToString("\n"))) // ZIP/text container preserves metadata.
        StructuredProfiles.save(context,"G8",saved.copy(bindings=saved.bindings.map{it.copy(enabled=true)}),stored,false)
        assertEquals(saved.bindings.size,StructuredProfiles.read(context,"G8").bindings.size)
        assertFalse(KeymapProfiles.getProfile(prefs.getStringSet("G8",null)).disabled)
        assertThrows(Exception::class.java){ProfileConfiguration.validate((stored+"KEY_Z 10 20 0").joinToString("\n"))}
    }
    @Test fun disabledAttackProducesNoTouchAndReenablingKeepsRepeatAndAimTuning() {
        val context=RuntimeEnvironment.getApplication();val base=fixture()
        val disabled=base.copy(bindings=base.bindings.map{if(it.input=="BTN_TL2")it.copy(enabled=false)else it})
        val model=KeymapProfiles.getProfile(StructuredProfiles.pack(disabled))
        var injected=0
        val config=KeymapConfig(context)
        val mouse=arrayOfNulls<xtr.keymapper.server.event.MouseEventHandler>(1)
        val input=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(xtr.keymapper.server.IInputInterface::class.java)){_,m,_->
            when(m.name){"getKeymapProfile"->model;"getKeymapConfig"->config;"getMouseEventHandler"->mouse[0];"injectEvent"->{injected++;null};else->null}
        } as xtr.keymapper.server.IInputInterface
        mouse[0]=xtr.keymapper.server.event.MouseEventHandler(input);mouse[0]!!.init(2280,1080)
        val handler=xtr.keymapper.server.event.KeyEventHandler(input);handler.init()
        handler.handleEvent(" EV_KEY BTN_TL2 DOWN");handler.handleEvent(" EV_KEY BTN_TL2 UP");handler.stop();mouse[0]!!.stop()
        assertEquals(0,injected)
        val restored=KeymapProfiles.getProfile(StructuredProfiles.pack(base))
        assertEquals(150,restored.keys.single{it.code=="BTN_TL2"}.repeatIntervalMs)
        assertTrue(restored.aimKeys.all{it.radius==80f})
    }
    @Test fun yamlExponentAndShallowResourceLimitsAreSafe() {
        val d=fixture().let{it.copy(bindings=it.bindings.map{b->if(b.action=="stick_aim")b.copy(nativeLine=b.nativeLine.replace("80 1 1", "80 1.0E10 1.0E-10"))else b})}
        assertEquals(ProfileCodec.decode(ProfileCodec.encode(d)),ProfileCodec.decode(ProfileCodec.encode(d,true)))
        assertThrows(Exception::class.java){ProfileCodec.decode("["+List(21000){"1"}.joinToString(",")+"]")}
        assertThrows(Exception::class.java){ProfileCodec.decode("values: ["+List(31000){"1"}.joinToString(",")+"]")}
    }
    @Test fun fuzzMalformedDocumentsNeverTouchPreferences() {
        val context=RuntimeEnvironment.getApplication();val prefs=KeymapProfiles(context).sharedPref;val before=prefs.all.toMap()
        val random=java.util.Random(24);repeat(300){val s=(0 until random.nextInt(300)).map{"{}[]:!&*\\\"\nabc012"[random.nextInt(17)]}.joinToString("");try{ProfileCodec.decode(s)}catch(_:Exception){}}
        assertEquals(before,prefs.all)
    }
}
