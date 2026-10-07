package xtr.keymapper.keymap.element;

import android.os.Parcel;
import xtr.keymapper.keymap.KeymapProfileElement;

/** Profile-driven held touch, aimed by relative mouse motion or an absolute controller stick. */
public final class AimKey extends KeymapProfileElement {
    public static final String TAG = "AIM_KEY";
    public String code;
    public boolean stick;
    public float deadZone = 0.15f;
    public boolean invertY;
    public float x, y, radius, xSensitivity, ySensitivity;

    public AimKey(String[] data) {
        stick = data[0].equals("STICK_AIM");
        code = xtr.keymapper.controller.ControllerBindings.canonical(data[1]);
        if (stick) { deadZone = Float.parseFloat(data[7]); invertY = data[8].equals("1"); }
        x = Float.parseFloat(data[2]);
        y = Float.parseFloat(data[3]);
        radius = Float.parseFloat(data[4]);
        xSensitivity = Float.parseFloat(data[5]);
        ySensitivity = Float.parseFloat(data[6]);
    }

    private AimKey(Parcel in) {
        code = in.readString();
        x = in.readFloat(); y = in.readFloat(); radius = in.readFloat();
        xSensitivity = in.readFloat(); ySensitivity = in.readFloat();
        stick = in.readByte() != 0; deadZone = in.readFloat(); invertY = in.readByte() != 0;
    }

    public String getData() {
        return (stick ? "STICK_AIM" : TAG) + " " + code + " " + x + " " + y + " " + radius
                + " " + xSensitivity + " " + ySensitivity
                + (stick ? " " + deadZone + " " + (invertY ? 1 : 0) : "");
    }

    @Override public void scale(float scaleX, float scaleY) {
        x *= scaleX; y *= scaleY;
        radius *= Math.min(scaleX, scaleY);
        if (!stick) { xSensitivity *= scaleX; ySensitivity *= scaleY; }
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(code); dest.writeFloat(x); dest.writeFloat(y); dest.writeFloat(radius);
        dest.writeFloat(xSensitivity); dest.writeFloat(ySensitivity);
        dest.writeByte((byte)(stick ? 1 : 0)); dest.writeFloat(deadZone); dest.writeByte((byte)(invertY ? 1 : 0));
    }

    public static final Creator<AimKey> CREATOR = new Creator<>() {
        @Override public AimKey createFromParcel(Parcel in) { return new AimKey(in); }
        @Override public AimKey[] newArray(int size) { return new AimKey[size]; }
    };
}
