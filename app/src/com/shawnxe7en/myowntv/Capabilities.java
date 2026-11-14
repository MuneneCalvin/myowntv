package com.shawnxe7en.myowntv;

import android.content.Context;

import java.io.File;

/**
 * What this TV can do, and what an option needs from it. Every option that needs something names it (SettingsActivity's
 * `need`), and the settings show it unavailable, with `missing`'s reason, wherever this TV lacks it: a new option only
 * names its need.
 */
final class Capabilities {
    private Capabilities() {}

    /** An option's needs. */
    static final String NEED_ADB = "adb", NEED_REMOTE_BUTTONS = "remote-buttons",
            NEED_LIVE_TV = "live-tv", NEED_SWITCH = "switch",
            NEED_APP_STORE = "app-store", NEED_SCREENSAVER = "screensaver",
            NEED_INPUTS = "inputs", NEED_HDMI = "hdmi";

    /** null where this TV has what `need` names; else why not, and what would change it, for the person to read. */
    static String missing(Context c, String need) {
        switch (need) {
            case NEED_ADB:
                return adb(c) ? null : Ui.tr("Needs MyOwnTV mode: turn it on in Home Screen.");
            case NEED_REMOTE_BUTTONS:
                return remoteButtons() ? null : Ui.tr("This TV's remote has no app buttons that MyOwnTV knows.");
            case NEED_LIVE_TV:
                return HomeApps.liveTv(c) != null ? null : Ui.tr("This TV has no live TV (antenna or cable) to open.");
            case NEED_INPUTS:   // a streaming stick or box: no Live TV and no HDMI port, so Settings > Inputs has no rows
                return HomeApps.allInputs(c).isEmpty() ? Ui.tr("No inputs") : null;
            case NEED_HDMI:   // Home Settings > Live TV button: a port for the button to open instead of Live TV
                for (HomeApps.Item it : HomeApps.allInputs(c)) if (it.kind == HomeApps.INPUT) return null;
                return Ui.tr("No inputs");
            case NEED_APP_STORE:
                return HomeApps.storePackage(c) != null ? null : Ui.tr("This TV has no supported app store to open.");
            case NEED_SCREENSAVER:
                return screensaver(c) ? null : Ui.tr("This TV stays awake, so no screensaver starts.");
            case NEED_SWITCH:   // where the switch must not run; a TV already in MyOwnTV mode can still go back
                return HomeMode.phone(c) ? HomeMode.phoneText() : HomeMode.BRICKS && !adb(c) ? HomeMode.bricksText() : null;
            default:
                return null;
        }
    }

    private static long stateStamp = Long.MIN_VALUE;
    private static boolean adbNow;

    /**
     * Keys and settings through the TV's own ADB: the switch has it allowed, so in MyOwnTV mode. Read again only when
     * the state file changes, as the settings ask on every frame.
     */
    static synchronized boolean adb(Context c) {
        File f = new File(c.getFilesDir(), "home-state.json");
        long stamp = f.lastModified() * 31 + f.length();
        if (stamp != stateStamp) {
            stateStamp = stamp;
            org.json.JSONObject s = HomeMode.state(c);
            adbNow = HomeMode.FREE.equals(s.optString("mode")) && !s.optBoolean("homeReverted");
        }
        return adbNow;
    }

    /** The remote's app buttons, on the brands whose key codes RemoteKeys knows. */
    static boolean remoteButtons() {
        return RemoteKeys.supported();
    }

    private static Boolean screensaverNow;

    /**
     * Android can start our screensaver here. A TV kept awake (stay_on_while_plugged_in) never idles, so none starts:
     * a Philips TPM171E (Android 8) is set so, and runs its own from its system UI (#451). Read once, as the settings
     * ask on every frame.
     */
    static synchronized boolean screensaver(Context c) {
        if (screensaverNow == null) {
            screensaverNow = Config.SCREENSAVER && android.provider.Settings.Global.getInt(c.getContentResolver(),
                    android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) == 0;
        }
        return screensaverNow;
    }
}
