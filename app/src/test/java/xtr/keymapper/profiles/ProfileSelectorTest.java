package xtr.keymapper.profiles;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import java.util.ArrayList;
import java.util.List;
import xtr.keymapper.R;
import xtr.keymapper.devices.DeviceMappingStore;
import xtr.keymapper.keymap.KeymapProfiles;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProfileSelectorTest {
    private Context context;
    private KeymapProfiles profiles;
    private List<String> selected;
    @Before public void setup() {
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_XtMapper);
        profiles = new KeymapProfiles(context); profiles.sharedPref.edit().clear().commit();
        context.getSharedPreferences("device_mapping_v1", Context.MODE_PRIVATE).edit()
                .putString("schema_v1", "{\"version\":1,\"groups\":[],\"bindings\":[]}").commit();
        selected = new ArrayList<>();
    }
    private AlertDialog dialog() { return (AlertDialog) ShadowDialog.getLatestDialog(); }
    private void unchanged(String bindings) {
        assertTrue(profiles.sharedPref.getAll().isEmpty()); assertTrue(selected.isEmpty());
        assertEquals(bindings, context.getSharedPreferences("device_mapping_v1", 0).getString("schema_v1", null));
    }
    @Test public void noCancelBackAndOutsideDismissDoNotCreateFollowupDialog() {
        String bindings = context.getSharedPreferences("device_mapping_v1", 0).getString("schema_v1", null);
        for (String action : List.of("no", "cancel", "back", "outside")) {
            var outcomes = new ArrayList<ProfileDialogOperation.Outcome>();
            ProfileSelector.showEnableProfileDialog(context, "unknown.app", outcome -> {
                outcomes.add(outcome);
                if (outcome == ProfileDialogOperation.Outcome.Accept)
                    ProfileSelector.createNewProfileForApp(context, "unknown.app", true, selected::add);
            });
            AlertDialog first = dialog();
            switch (action) {
                case "no": first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); break;
                case "back": first.onBackPressed(); break;
                // Android outside dismissal calls cancel(), while programmatic dismissal uses dismiss().
                case "outside": first.cancel(); break;
                default: first.dismiss();
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(first, dialog()); assertFalse(first.isShowing());
            assertEquals(List.of(action.equals("no") ? ProfileDialogOperation.Outcome.Decline :
                    ProfileDialogOperation.Outcome.Dismiss), outcomes);
            unchanged(bindings);
        }
    }
    @Test public void manualNameCancelAndInvalidNameHaveNoSideEffects() {
        String bindings = context.getSharedPreferences("device_mapping_v1", 0).getString("schema_v1", null);
        for (String action : List.of("cancel", "back", "outside")) {
            ProfileSelector.createNewProfileForApp(context, "unknown.app", true, selected::add);
            AlertDialog form = dialog();
            if (action.equals("cancel")) form.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            else if (action.equals("back")) form.onBackPressed(); else form.cancel();
            Shadows.shadowOf(Looper.getMainLooper()).idle(); unchanged(bindings);
        }
        ProfileSelector.createNewProfileForApp(context, "unknown.app", true, selected::add);
        EditText name = dialog().findViewById(R.id.edit_text);
        name.setText("   "); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        unchanged(bindings); assertTrue(dialog().isShowing()); dialog().cancel();
    }
    @Test public void explicitManualCreationSavesOnceIncludingIntentionallyDisabledProfile() {
        for (boolean enabled : new boolean[]{true, false}) {
            ProfileSelector.createNewProfileForApp(context, "configured.app", enabled, selected::add);
            ((EditText) dialog().findViewById(R.id.edit_text)).setText(enabled ? "Enabled" : "Disabled");
            dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(List.of("Enabled", "Disabled"), selected);
        assertFalse(profiles.getProfile("Enabled", false).disabled);
        assertTrue(profiles.getProfile("Disabled", false).disabled);
        assertEquals(2, profiles.sharedPref.getAll().size());
        assertTrue(new DeviceMappingStore(context).listBindings().isEmpty());
    }
    @Test public void selectionOfMissingOrDeletedProfileNeverCreatesAndCancelledPickerDoesNotMutate() {
        assertNull(ProfileSelector.select(context, selected::add, "unknown.app"));
        assertTrue(selected.isEmpty()); assertTrue(profiles.sharedPref.getAll().isEmpty());
        for (String n : List.of("A", "B")) profiles.saveProfile(n, new ArrayList<>(), "known", true, 0, 0);
        var before = profiles.sharedPref.getAll();
        AlertDialog picker = ProfileSelector.select(context, selected::add, "known"); picker.cancel();
        assertEquals(before, profiles.sharedPref.getAll()); assertTrue(selected.isEmpty());
        picker = ProfileSelector.select(context, selected::add, "known");
        profiles.deleteProfile("A"); picker.getListView().performItemClick(null, 0, 0);
        assertTrue(selected.isEmpty()); assertEquals(1, profiles.sharedPref.getAll().size()); picker.dismiss();
    }
    @Test public void recreationDismissesManualFormAndCannotSaveAfterDestruction() {
        var owner = Robolectric.buildActivity(Activity.class).setup();
        owner.get().setTheme(R.style.Theme_XtMapper);
        ProfileSelector.createNewProfileForApp(owner.get(), "unknown.app", true, selected::add);
        AlertDialog form = dialog(); owner.pause().stop().destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idle(); assertFalse(form.isShowing());
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(selected.isEmpty()); assertTrue(profiles.sharedPref.getAll().isEmpty());
    }
    @Test public void cancelledAsyncAppLoadingCannotShowPickerLater() {
        ProfilesApps apps = org.robolectric.util.ReflectionHelpers.callConstructor(ProfilesApps.class,
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(Context.class, context));
        AlertDialog loading = ProfileSelector.showDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(context));
        loading.cancel();
        apps.deliverLoaded(apps.new AppsGridAdapter(context), loading,
                (p, adapter, d) -> fail("cancelled loading must not reopen picker"));
        assertNull(apps.binding); assertEquals(loading, dialog());
    }
    @Test public void yesThenManualNameConfirmationCreatesExactlyOneEnabledProfile() {
        ProfileSelector.showEnableProfileDialog(context, "configured.app", outcome -> {
            if (outcome == ProfileDialogOperation.Outcome.Accept)
                ProfileSelector.createNewProfileForApp(context, "configured.app", true, selected::add);
        });
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ((EditText) dialog().findViewById(R.id.edit_text)).setText("Manual");
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of("Manual"), selected);
        assertEquals(1, profiles.sharedPref.getAll().size());
        assertFalse(profiles.getProfile("Manual", false).disabled);
    }
    @Test public void cancelledManualOperationPreservesDisabledProfilesAndExistingDeviceBindings() {
        profiles.saveProfile("Disabled", new ArrayList<>(), "known", false, 2280, 1080);
        var store = new DeviceMappingStore(context);
        var group = store.createGroup("G8", List.of(new xtr.keymapper.devices.DeviceMember("g8", "G8", 1, 2)));
        store.setBinding(group.getId(), "known", "Disabled");
        var beforeProfiles = profiles.sharedPref.getAll();
        var beforeBindings = context.getSharedPreferences("device_mapping_v1", 0).getAll();
        ProfileSelector.createNewProfileForApp(context, "unknown.app", true, selected::add);
        dialog().cancel(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(beforeProfiles, profiles.sharedPref.getAll());
        assertEquals(beforeBindings, context.getSharedPreferences("device_mapping_v1", 0).getAll());
        assertTrue(profiles.getProfile("Disabled", false).disabled); assertTrue(selected.isEmpty());
    }
}
