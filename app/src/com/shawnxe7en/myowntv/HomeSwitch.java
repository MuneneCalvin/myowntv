package com.shawnxe7en.myowntv;

import java.util.Locale;

/**
 * The safe home switch (Option A of the design): MyOwnTV becomes the home with set-home-activity and the Home-button
 * watch, and the TV's own home and Google TV's setup wizard stay switched ON. Turning those two off is what the
 * upstream switch did; Couch TV's docs link it to boot loops on some Google TVs, and it deletes the Wi-Fi networks the
 * setup wizard saved. To go back to the upstream behaviour, set SAFE to false and rebuild (Option B). No Android imports.
 */
final class HomeSwitch {
    private HomeSwitch() {}

    static final boolean SAFE = true;

    private static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx";
    private static final String CLASSIC_HOME = "com.google.android.tvlauncher";
    private static final String SETUP = "com.google.android.tungsten.setupwraith";

    /** True for a package the safe switch never turns off: the TV's homes and the setup wizard. */
    static boolean keepOn(String pkg, String recordedHomePkg) {
        if (!SAFE) return false;
        return pkg.equals(GOOGLE_TV_HOME) || pkg.equals(CLASSIC_HOME) || pkg.equals(SETUP) || pkg.equals(recordedHomePkg);
    }

    /** Lets the app start in the background on brands that kill background apps (Skyworth, TCL); the op's name varies. */
    static String autoStartCommand(String pkg, boolean alternative) {
        return "appops set " + pkg + (alternative ? " APP_AUTO_START" : " AUTO_START") + " allow";
    }

    /** `appops` printed an error (an unknown op) instead of nothing. */
    static boolean failed(String output) {
        String o = output.toLowerCase(Locale.ROOT);
        return o.contains("error") || o.contains("unknown") || o.contains("bad");
    }
}
