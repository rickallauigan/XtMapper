package xtr.keymapper.structured

import android.app.Activity
import android.content.Intent
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.devices.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class StructuredUiLifecycleTest {
    @Test fun canceledFilePickerAndDestructionLeaveNineProfilesAndAssociationsUntouched() {
        val context=RuntimeEnvironment.getApplication();val prefs=KeymapProfiles(context).sharedPref
        repeat(9){prefs.edit().putStringSet("Legacy$it",setOf("APPLICATION example.game","SCREENSIZE 2280 1080","ENABLED","BTN_TR 100 200 0")).commit()}
        val store=DeviceMappingStore(context);val group=store.createGroup("Gaming",listOf(DeviceMember("stable-controller","G8")));store.setBinding(group.id,"example.game","Legacy0")
        val profiles=prefs.all.toMap();val associations=store.listBindings()
        val activity=Robolectric.buildActivity(StructuredProfileActivity::class.java,Intent(context,StructuredProfileActivity::class.java).putExtra("profile","Legacy0")).create().start().resume()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(activity.get(),"onActivityResult",org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,1),org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,Activity.RESULT_CANCELED),org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java,null))
        activity.pause().stop().destroy()
        assertEquals(profiles,prefs.all);assertEquals(associations,store.listBindings())
    }
    @Test fun schemaExampleParsesWithoutInventingMeasurementsAndChordLabelsRemainReadable() {
        val d=ProfileCodec.decode(java.io.File("../schema/v1/mlbb-example.json").readText())
        assertEquals(1981.08,org.json.JSONObject(ProfileCodec.encode(d)).getJSONArray("bindings").getJSONObject(0).getJSONObject("position").getDouble("x"),0.0)
        val b=MappingFields.fromLine("CHORD BTN_SELECT BTN_TR 100 200 0")
        assertEquals("SELECT + RB",b.displayInput())
    }
}
