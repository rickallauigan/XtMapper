package xtr.keymapper.editor.component;

import android.content.Context;
import xtr.keymapper.editor.EditorUiComponent;
import xtr.keymapper.editor.EditorUiComponentCallback;
import xtr.keymapper.floatingkeys.MovableFloatingActionKey;

/** Existing advanced marker; visual chord editing shares the same canonical effective anchor. */
public final class ControllerChord extends EditorUiComponent {
    private final xtr.keymapper.keymap.element.ControllerChord config;
    private final MovableFloatingActionKey marker;
    private final float anchorX, anchorY;
    private boolean positioned;
    public ControllerChord(EditorUiComponentCallback callback, Context context,
                           xtr.keymapper.keymap.element.ControllerChord config) {
        super(callback, context, config.x, config.y);
        this.config = config;
        marker = new MovableFloatingActionKey(context,
            key -> callback.removeComponent(this, key.frameView), callback.getKeysContainerView());
        marker.setText(config.label());
        anchorX = config.x + config.offset; anchorY = config.y + config.offset;
        marker.frameView.addOnLayoutChangeListener(new android.view.View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(android.view.View v, int l, int t, int r, int b,
                    int oldL, int oldT, int oldR, int oldB) {
                if (r <= l || b <= t) return;
                v.setX(anchorX - v.getPivotX()); v.setY(anchorY - v.getPivotY());
                positioned = true; v.removeOnLayoutChangeListener(this);
            }
        });
    }
    @Override public String getDataLine() {
        float x = positioned ? marker.frameView.getX() + marker.frameView.getPivotX() : anchorX;
        float y = positioned ? marker.frameView.getY() + marker.frameView.getPivotY() : anchorY;
        return "CHORD " + config.modifier + " " + config.trigger + " " + x + " " + y + " 0";
    }
}
