package xtr.keymapper.keymap;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import xtr.keymapper.structured.StructuredProfiles;

/** Checked, lock-scoped string-set transactions. Never mutate preference-owned sets. */
public final class ProfileStorageTransactions {
    private ProfileStorageTransactions() {}

    private static Set<String> snapshot(Set<String> value) {
        return value == null ? null : Collections.unmodifiableSet(new HashSet<>(value));
    }

    private static void validName(String name) {
        if (name == null || name.trim().isEmpty() || name.length() > 80
                || name.equals(".") || name.equals("..")
                || name.chars().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\'))
            throw new IllegalArgumentException("Invalid profile name");
    }

    public static void rename(SharedPreferences prefs, String oldName, String newName) {
        synchronized (StructuredProfiles.SAVE_LOCK) {
            validName(newName);
            Set<String> old = snapshot(prefs.getStringSet(oldName, null));
            if (old == null) throw new IllegalArgumentException("Profile no longer exists");
            if (oldName.equals(newName)) return;
            if (prefs.contains(newName)) throw new IllegalArgumentException("Profile name already exists");
            Map<String, Set<String>> before = new HashMap<>();
            before.put(oldName, old); before.put(newName, null);
            checkedCommit(prefs, prefs.edit().remove(oldName).putStringSet(newName, new HashSet<>(old)), before);
        }
    }

    /** Import adds new profiles only: existing names must never be partially replaced. */
    public static void importNew(SharedPreferences prefs, Map<String, Set<String>> profiles) {
        synchronized (StructuredProfiles.SAVE_LOCK) {
            Map<String, Set<String>> incoming = new HashMap<>();
            Map<String, Set<String>> before = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : profiles.entrySet()) {
                validName(entry.getKey());
                if (prefs.contains(entry.getKey())) throw new IllegalArgumentException("Profile name already exists");
                if (entry.getValue() == null) throw new IllegalArgumentException("Missing profile data");
                incoming.put(entry.getKey(), snapshot(entry.getValue()));
                before.put(entry.getKey(), null);
            }
            if (incoming.isEmpty()) return;
            SharedPreferences.Editor edit = prefs.edit();
            incoming.forEach((key, value) -> edit.putStringSet(key, new HashSet<>(value)));
            checkedCommit(prefs, edit, before);
        }
    }

    private static void checkedCommit(SharedPreferences prefs, SharedPreferences.Editor edit,
                                      Map<String, Set<String>> before) {
        if (edit.commit()) return;
        // Android can update its in-memory map even when its synchronous disk write fails.
        SharedPreferences.Editor rollback = prefs.edit();
        before.forEach((key, value) -> {
            if (value == null) rollback.remove(key);
            else rollback.putStringSet(key, new HashSet<>(value));
        });
        if (!rollback.commit())
            throw new IllegalStateException("Profile write failed; rollback persistence also failed. Verify storage before retrying.");
        throw new IllegalStateException("Profile write failed; prior state restored.");
    }
}
