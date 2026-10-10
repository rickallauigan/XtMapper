package xtr.keymapper.editor.component;

import android.content.Context;
import xtr.keymapper.editor.EditorUiComponent;
import xtr.keymapper.editor.EditorUiComponentCallback;
import xtr.keymapper.floatingkeys.MovableFloatingActionKey;

/** Chord identities are edited in configuration text; the HUD anchor is movable. */
public final class ControllerChord extends EditorUiComponent {
    private final xtr.keymapper.keymap.element.ControllerChord config;
    private final MovableFloatingActionKey marker;
    public ControllerChord(EditorUiComponentCallback callback, Context context,
                           xtr.keymapper.keymap.element.ControllerChord config) {
        super(callback, context, config.x, config.y);
        this.config = config;
        marker = new MovableFloatingActionKey(context,
            key -> callback.removeComponent(this, key.frameView), callback.getKeysContainerView());
        marker.setText(config.label());
        marker.frameView.setX(config.x); marker.frameView.setY(config.y);
    }
    @Override public String getDataLine() {
        config.x = marker.getX(); config.y = marker.getY(); return config.getData();
    }
}
