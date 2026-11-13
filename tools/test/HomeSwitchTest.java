package com.shawnxe7en.myowntv;

public class HomeSwitchTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        String home = "com.skyworth.launcher";
        check(HomeSwitch.keepOn("com.google.android.apps.tv.launcherx", home), "Google TV's home stays on");
        check(HomeSwitch.keepOn("com.google.android.tungsten.setupwraith", home), "the setup wizard stays on");
        check(HomeSwitch.keepOn("com.google.android.tvlauncher", home), "classic Android TV home stays on");
        check(HomeSwitch.keepOn(home, home), "the recorded home stays on");
        check(!HomeSwitch.keepOn("com.google.android.apps.tv.dreamx", home), "ambient mode may go off");
        check(!HomeSwitch.keepOn("com.cltv.hybrid", home), "other bloat may go off");
        check(HomeSwitch.SAFE, "the safe switch is the default");
        check(HomeSwitch.autoStartCommand("p.q", false).equals("appops set p.q AUTO_START allow"), "first appop");
        check(HomeSwitch.autoStartCommand("p.q", true).equals("appops set p.q APP_AUTO_START allow"), "second appop");
        check(HomeSwitch.failed("Error: Unknown operation string: AUTO_START"), "an unknown op is a failure");
        check(HomeSwitch.failed("Bad argument"), "Bad is a failure");
        check(!HomeSwitch.failed(""), "silence is success");
        System.out.println("PASS");
    }
}
