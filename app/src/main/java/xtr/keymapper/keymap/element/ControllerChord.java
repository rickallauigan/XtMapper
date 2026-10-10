package xtr.keymapper.keymap.element;

import android.os.Parcel;
import xtr.keymapper.controller.ControllerBindings;
import xtr.keymapper.keymap.KeymapProfileElement;

/** A controller modifier plus trigger mapped to a fixed touch. */
public final class ControllerChord extends KeymapProfileElement {
    public final String modifier, trigger;
    public float x, y;
    public final float offset;

    public ControllerChord(String[] data) {
        modifier = ControllerBindings.canonical(data[1]);
        trigger = ControllerBindings.canonical(data[2]);
        x = Float.parseFloat(data[3]); y = Float.parseFloat(data[4]);
        offset = Float.parseFloat(data[5]);
    }
    private ControllerChord(Parcel in) {
        modifier = in.readString(); trigger = in.readString();
        x = in.readFloat(); y = in.readFloat(); offset = in.readFloat();
    }
    @Override public void scale(float sx, float sy) { x *= sx; y *= sy; }
    public String getData() { return "CHORD " + modifier + " " + trigger + " " + x + " " + y + " " + offset; }
    public String label() { return ControllerBindings.label(modifier) + " + " + ControllerBindings.label(trigger); }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(modifier); dest.writeString(trigger);
        dest.writeFloat(x); dest.writeFloat(y); dest.writeFloat(offset);
    }
    @Override public int describeContents() { return 0; }
    public static final Creator<ControllerChord> CREATOR = new Creator<>() {
        @Override public ControllerChord createFromParcel(Parcel in) { return new ControllerChord(in); }
        @Override public ControllerChord[] newArray(int size) { return new ControllerChord[size]; }
    };
}
