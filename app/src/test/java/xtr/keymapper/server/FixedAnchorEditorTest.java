package xtr.keymapper.server;

import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import xtr.keymapper.R;
import xtr.keymapper.editor.EditorUiComponentCallback;
import xtr.keymapper.keymap.ProfileConfiguration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, manifest=Config.NONE)
public class FixedAnchorEditorTest {
    private xtr.keymapper.editor.component.Key marker(String line, FrameLayout container) {
        var context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_XtMapper);
        var callback=(EditorUiComponentCallback)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{EditorUiComponentCallback.class},
            (p,m,a)->m.getName().equals("getKeysContainerView")?container:null);
        return new xtr.keymapper.editor.component.Key(callback,context,ProfileConfiguration.parse(line).keys.get(0));
    }
    @Test public void untouchedSavePreservesCenterBeforeAndAfterLayoutAcrossRepeatedEdits() {
        for(String original:new String[]{"BTN_TL2 1981.08 946.58 0 150","BTN_THUMBR 1551 846.5 94.5","KEY_Q 400 500 0"}) {
            var expected=ProfileConfiguration.parse(original).keys.get(0);
            float x=expected.x+expected.offset,y=expected.y+expected.offset;
            String line=original;
            for(int pass=0;pass<3;pass++) {
                var context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_XtMapper);
                var container=new FrameLayout(context);var editor=marker(line,container);
                var before=ProfileConfiguration.parse(editor.getDataLine()).keys.get(0);
                assertEquals(x,before.x+before.offset,.001f);assertEquals(y,before.y+before.offset,.001f);
                container.getChildAt(0).layout(0,0,189,160);
                var after=ProfileConfiguration.parse(editor.getDataLine()).keys.get(0);
                assertEquals(x,after.x,.001f);assertEquals(y,after.y,.001f);assertEquals(0,after.offset,0);
                assertEquals(expected.code,after.code);assertEquals(expected.repeatIntervalMs,after.repeatIntervalMs);
                line=editor.getDataLine();
            }
        }
    }
    @Test public void draggingChangesOnlyAnchorAndKeepsRepeat() {
        var context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(),R.style.Theme_XtMapper);
        var container=new FrameLayout(context);var editor=marker("BTN_TL2 1981.08 946.58 0 150",container);
        var view=container.getChildAt(0);view.layout(0,0,189,160);
        view.setX(view.getX()+20);view.setY(view.getY()-30);
        var saved=ProfileConfiguration.parse(editor.getDataLine()).keys.get(0);
        assertEquals(2001.08f,saved.x,.001f);assertEquals(916.58f,saved.y,.001f);assertEquals(150,saved.repeatIntervalMs);
    }
    @Test public void legacyOffsetScalesAsPartOfEffectiveAnchor() {
        var key=ProfileConfiguration.parse("BTN_THUMBR 1551 846.5 94.5").keys.get(0);
        key.scale(.5f,.75f);
        assertEquals(1645.5f*.5f,key.x+key.offset,.001f);
        assertEquals(941f*.75f,key.y+key.offset,.001f);
    }
}
