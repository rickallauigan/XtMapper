package xtr.keymapper.editor.component;

import android.content.Context;
import xtr.keymapper.editor.EditorUiComponent;
import xtr.keymapper.editor.EditorUiComponentCallback;
import xtr.keymapper.floatingkeys.MovableFloatingActionKey;
import xtr.keymapper.keymap.element.Key;

public final class LeftClick extends EditorUiComponent {
    private final MovableFloatingActionKey marker;

    public LeftClick(EditorUiComponentCallback callback, Context context, Key key) {
        super(callback, context, key.x, key.y);
        marker = new MovableFloatingActionKey(context,
                removed -> callback.removeComponent(this, removed.frameView), callback.getKeysContainerView());
        marker.setText("LMB");
        marker.frameView.setX(key.x); marker.frameView.setY(key.y);
    }

    @Override public String getDataLine() {
        return "MOUSE_LEFT " + marker.getX() + " " + marker.getY();
    }
}
