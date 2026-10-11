package xtr.keymapper.profiles;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.annotation.UiContext;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

import xtr.keymapper.R;
import xtr.keymapper.databinding.AppViewBinding;
import xtr.keymapper.databinding.TextFieldBinding;
import xtr.keymapper.keymap.KeymapProfiles;

public class ProfileSelector {

    public interface OnProfileSelectedListener {
        void onProfileSelected(String profile);
    }
    public interface OnAppSelectedListener {
        void onAppSelected(String packageName);
    }
    public interface OnProfileDialogResultListener {
        void onResult(ProfileDialogOperation.Outcome outcome);
    }

    public static AlertDialog select(Context context, OnProfileSelectedListener listener, String packageName) {
        return select(context, listener, packageName, () -> isContextActive(context));
    }

    public static AlertDialog select(Context context, OnProfileSelectedListener listener,
                                     String packageName, BooleanSupplier valid) {
        if (!valid.getAsBoolean()) return null;
        context.setTheme(R.style.Theme_XtMapper);
        ArrayList<String> allProfiles = new ArrayList<>(new KeymapProfiles(context)
                .getAllProfilesForApp(packageName)
                .keySet());
        java.util.Collections.sort(allProfiles);

        if (allProfiles.size() == 1) {
            if (valid.getAsBoolean()) listener.onProfileSelected(allProfiles.get(0));
            return null;
        } else if (allProfiles.isEmpty()) {
            // Selection never provisions, including deletion races.
            return null;
        }
        CharSequence[] items = allProfiles.toArray(new CharSequence[0]);

        PackageManager pm = context.getPackageManager();

        CharSequence appName = "";
        Drawable appIcon = null;
        try {
            appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
            appIcon = pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        // Show dialog to select profile
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        final String[] selection = {null};
        ProfileDialogOperation operation = new ProfileDialogOperation(valid, outcome -> {
            if (outcome == ProfileDialogOperation.Outcome.Accept && selection[0] != null &&
                    new KeymapProfiles(context).getAllProfilesForApp(packageName).containsKey(selection[0]))
                listener.onProfileSelected(selection[0]);
        });
        builder.setTitle(context.getString(R.string.dialog_alert_select_profile) + appName)
                .setItems(items, (d, which) -> {
                    selection[0] = allProfiles.get(which);
                    operation.finish(ProfileDialogOperation.Outcome.Accept);
                })
                .setIcon(appIcon);
        AlertDialog dialog = showDialog(builder);
        dialog.setOnCancelListener(d -> operation.finish(ProfileDialogOperation.Outcome.Dismiss));
        dialog.setOnDismissListener(d -> operation.finish(ProfileDialogOperation.Outcome.Dismiss));
        return dialog;
    }

    public static void createNewProfile(@UiContext Context context, OnProfileSelectedListener listener) {
        showAppSelectionDialog(context, packageName -> createNewProfileForApp(context, packageName, true, listener));
    }

    public static void showEnableProfileDialog(@UiContext Context context, String packageName, OnProfileDialogResultListener listener){
        context.setTheme(R.style.Theme_XtMapper);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        AppViewBinding binding = AppViewBinding.inflate(LayoutInflater.from(context));
        PackageManager pm = context.getPackageManager();
        try {
            binding.appName.setText(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));
            binding.appIcon.setImageDrawable(pm.getApplicationIcon(packageName));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        ProfileDialogOperation operation = new ProfileDialogOperation(
                () -> isContextActive(context), listener::onResult);
        builder.setTitle(R.string.dialog_alert_enable_profile)
                .setPositiveButton(R.string.yes, (d, which) -> operation.finish(ProfileDialogOperation.Outcome.Accept))
                .setNegativeButton(R.string.no, (d, which) -> operation.finish(ProfileDialogOperation.Outcome.Decline))
                .setView(binding.getRoot());
        AlertDialog dialog = showDialog(builder);
        dialog.setOnCancelListener(d -> operation.finish(ProfileDialogOperation.Outcome.Dismiss));
        dialog.setOnDismissListener(d -> operation.finish(ProfileDialogOperation.Outcome.Dismiss));
    }

    public static void createNewProfileForApp(@UiContext Context context, String packageName, boolean enabled, OnProfileSelectedListener listener){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        TextFieldBinding binding = TextFieldBinding.inflate(LayoutInflater.from(context));
        binding.getRoot().setHint(R.string.profile_name);
        binding.editText.setText(packageName);

        ProfileDialogOperation operation = new ProfileDialogOperation(() -> isContextActive(context), outcome -> {
            if (outcome != ProfileDialogOperation.Outcome.Accept) return;
            String name = binding.editText.getText().toString().trim();
            KeymapProfiles profiles = new KeymapProfiles(context);
            if (name.isEmpty() || profiles.sharedPref.contains(name)) return;
            profiles.saveProfile(name, new ArrayList<>(), packageName, enabled, 0, 0);
            listener.onProfileSelected(name);
        });
        builder.setTitle(R.string.dialog_alert_add_profile)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(android.R.string.cancel,
                        (d, which) -> operation.finish(ProfileDialogOperation.Outcome.Decline))
                .setView(binding.getRoot());
        AlertDialog dialog = showDialog(builder);
        dialog.setOnCancelListener(d -> operation.finish(ProfileDialogOperation.Outcome.Dismiss));
        dialog.setOnDismissListener(d -> operation.finish(ProfileDialogOperation.Outcome.Dismiss));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = binding.editText.getText().toString().trim();
            if (name.isEmpty() || new KeymapProfiles(context).sharedPref.contains(name)) {
                binding.editText.setError(context.getString(R.string.profile_name));
                return;
            }
            operation.finish(ProfileDialogOperation.Outcome.Accept);
            dialog.dismiss();
        });
    }

    // Show a dialog with a list of apps and callback when an app is selected
    public static void showAppSelectionDialog(Context context, OnAppSelectedListener listener) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        ProfilesApps.asyncLoadAppsAndThen(context, builder,
                (p, adapter, loadingDialog) -> {
                    p.binding.appsGrid.setAdapter(adapter);
                    loadingDialog.dismiss();
                    AlertDialog dialog = showDialog(builder.setView(p.appsView));
                    dialog.setOnDismissListener(d -> p.onDestroyView());
                    p.setListener(packageName -> {
                        if (isContextActive(context) && dialog.isShowing()) listener.onAppSelected(packageName);
                        p.onDestroyView();
                        dialog.dismiss();
                    });
                });

    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    static boolean isContextActive(Context context) {
        Activity owner = activity(context);
        return owner == null || (!owner.isFinishing() && !owner.isDestroyed() && !owner.isChangingConfigurations());
    }

    protected static AlertDialog showDialog(MaterialAlertDialogBuilder builder) {
        AlertDialog dialog = builder.create();
        if (Settings.canDrawOverlays(dialog.getContext()))
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        Activity owner = activity(dialog.getContext());
        if (owner != null) {
            Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity a, Bundle b) { }
                public void onActivityStarted(Activity a) { }
                public void onActivityResumed(Activity a) { }
                public void onActivityPaused(Activity a) { }
                public void onActivityStopped(Activity a) { }
                public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                public void onActivityDestroyed(Activity a) { if (a == owner) dialog.dismiss(); }
            };
            owner.getApplication().registerActivityLifecycleCallbacks(lifecycle);
            dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(android.view.View v) { }
                public void onViewDetachedFromWindow(android.view.View v) {
                    owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
                }
            });
        }
        dialog.show();
        return dialog;
    }
}