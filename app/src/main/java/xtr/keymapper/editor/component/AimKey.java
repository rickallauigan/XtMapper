package xtr.keymapper.editor.component;

import android.content.Context;
import xtr.keymapper.editor.EditorUiComponent;
import xtr.keymapper.editor.EditorUiComponentCallback;
import xtr.keymapper.floatingkeys.MovableFloatingActionKey;

/** Movable anchor marker; the range/sensitivity remain editable through configuration import. */
public final class AimKey extends EditorUiComponent {
    private final xtr.keymapper.keymap.element.AimKey config;
    private final MovableFloatingActionKey marker;

    public AimKey(EditorUiComponentCallback callback, Context context, xtr.keymapper.keymap.element.AimKey config) {
        super(callback, context, config.x, config.y);
        this.config = config;
        marker = new MovableFloatingActionKey(context,
                key -> callback.removeComponent(this, key.frameView), callback.getKeysContainerView());
        marker.setText("Aim " + config.code);
        marker.frameView.setX(config.x);
        marker.frameView.setY(config.y);
    }

    @Override public String getDataLine() {
        config.x = marker.getX(); config.y = marker.getY();
        return config.getData();
    }
}
