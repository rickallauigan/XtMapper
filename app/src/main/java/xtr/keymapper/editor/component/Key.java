package xtr.keymapper.editor.component;

import android.content.Context;
import android.view.View;

import xtr.keymapper.editor.EditorUiComponent;
import xtr.keymapper.editor.EditorUiComponentCallback;
import xtr.keymapper.floatingkeys.MovableFloatingActionKey;
import xtr.keymapper.keymap.KeymapConfig;

public class Key extends EditorUiComponent {
    private final MovableFloatingActionKey floatingKey;
    private int repeatIntervalMs;
    private float anchorX, anchorY;
    private boolean positioned;

    public Key(EditorUiComponentCallback callback, Context context, float x, float y) {
        super(callback, context, x, y);
        xtr.keymapper.keymap.element.Key key = new xtr.keymapper.keymap.element.Key();
        key.code = "KEY_X";
        key.x = x;
        key.y = y;
        repeatIntervalMs = key.repeatIntervalMs;
        floatingKey = addKey(key);
    }

    @Override
    public String getDataLine() {
        float x = positioned ? floatingKey.frameView.getX() + floatingKey.frameView.getPivotX() : anchorX;
        float y = positioned ? floatingKey.frameView.getY() + floatingKey.frameView.getPivotY() : anchorY;
        return xtr.keymapper.controller.ControllerBindings.editorCode(floatingKey.getCodeText())
                + " " + x + " " + y + " 0" + (repeatIntervalMs > 0 ? " " + repeatIntervalMs : "");
    }


    public Key(EditorUiComponentCallback callback, Context context, xtr.keymapper.keymap.element.Key key) {
        super(callback, context, key.x, key.y);
        repeatIntervalMs = key.repeatIntervalMs;
        floatingKey = addKey(key);
    }

    private MovableFloatingActionKey addKey(xtr.keymapper.keymap.element.Key key) {
        KeymapConfig keymapConfig = new KeymapConfig(getContext());

        MovableFloatingActionKey floatingKey = new MovableFloatingActionKey(getContext(), floatingActionKey -> {
            getCallback().removeComponent(this, floatingActionKey.frameView);
        }, getCallback().getKeysContainerView());

        floatingKey.setText(key.code.startsWith("KEY_") ? key.code.substring(4) : key.code);
        // Profiles may contain a legacy top-left + offset binding. Show its actual
        // injected center, and save canonical center coordinates independently of marker size.
        anchorX = key.x + key.offset;
        anchorY = key.y + key.offset;
        floatingKey.frameView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right <= left || bottom <= top) return;
                view.setX(anchorX - view.getPivotX());
                view.setY(anchorY - view.getPivotY());
                positioned = true;
                view.removeOnLayoutChangeListener(this);
            }
        });
        floatingKey.setOnClickListener(this::onFloatingKeyClick);

        floatingKey.frameView.setScaleX(keymapConfig.floatingKeysSize);
        floatingKey.frameView.setScaleY(keymapConfig.floatingKeysSize);

        return floatingKey;
    }

    private void onFloatingKeyClick(View view) {
        getCallback().setOnKeyListener(floatingKey::setText);
    }

}
