package xtr.keymapper.keymap.element;

import android.os.Parcel;
import xtr.keymapper.keymap.KeymapProfileElement;

/** A held keyboard key or mouse button whose touch is aimed using relative mouse motion. */
public final class AimKey extends KeymapProfileElement {
    public static final String TAG = "AIM_KEY";
    public String code;
    public float x, y, radius, xSensitivity, ySensitivity;

    public AimKey(String[] data) {
        code = data[1];
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
    }

    public String getData() {
        return TAG + " " + code + " " + x + " " + y + " " + radius
                + " " + xSensitivity + " " + ySensitivity;
    }

    @Override public void scale(float scaleX, float scaleY) {
        x *= scaleX; y *= scaleY;
        radius *= Math.min(scaleX, scaleY);
        xSensitivity *= scaleX; ySensitivity *= scaleY;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(code); dest.writeFloat(x); dest.writeFloat(y); dest.writeFloat(radius);
        dest.writeFloat(xSensitivity); dest.writeFloat(ySensitivity);
    }

    public static final Creator<AimKey> CREATOR = new Creator<>() {
        @Override public AimKey createFromParcel(Parcel in) { return new AimKey(in); }
        @Override public AimKey[] newArray(int size) { return new AimKey[size]; }
    };
}
