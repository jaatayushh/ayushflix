package android.app;

import android.annotation.Stub;

@Stub
public class UiModeManager {
    public static final int MODE_NIGHT_AUTO = 0;
    public static final int MODE_NIGHT_NO = 1;
    public static final int MODE_NIGHT_YES = 2;
    public static final int MODE_NIGHT_CUSTOM = 3;

    public static final int CONFIGURATION_UI_MODE_TYPE_UNDEFINED = 0;
    public static final int CONFIGURATION_UI_MODE_TYPE_NORMAL = 1;
    public static final int CONFIGURATION_UI_MODE_TYPE_DESK = 2;
    public static final int CONFIGURATION_UI_MODE_TYPE_CAR = 3;
    public static final int CONFIGURATION_UI_MODE_TYPE_TELEVISION = 4;
    public static final int CONFIGURATION_UI_MODE_TYPE_APPLIANCE = 5;
    public static final int CONFIGURATION_UI_MODE_TYPE_WATCH = 6;
    public static final int CONFIGURATION_UI_MODE_TYPE_VR_HEADSET = 7;

    public int getCurrentModeType() {
        return CONFIGURATION_UI_MODE_TYPE_NORMAL;
    }

    public int getNightMode() {
        return MODE_NIGHT_YES;
    }

    public void setNightMode(int mode) {}
}
