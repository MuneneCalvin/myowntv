package com.shawnxe7en.myowntv;

import android.util.Log;

/** The app's log (adb logcat -s MyOwnTV). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("MyOwnTV", message);
    }
}
