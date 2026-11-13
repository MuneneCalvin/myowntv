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
        check(HomeSwitch.isTvHome("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity"), "Google TV's home window");
        check(!HomeSwitch.isTvHome("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.search.SearchActivity"), "other windows of Google TV are left alone");
        check(HomeSwitch.isTvHome("com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity"), "classic Android TV home window");
        check(!HomeSwitch.isTvHome("com.netflix.ninja", "com.netflix.ninja.MainActivity"), "an app's window is not a home");
        check(!HomeSwitch.isTvHome("com.shawnxe7en.myowntv", "com.shawnxe7en.myowntv.HomeActivity"), "our own home is not Google's");
        check(!HomeSwitch.isTvHome(null, "x") && !HomeSwitch.isTvHome("com.google.android.apps.tv.launcherx", null), "nulls");
        System.out.println("PASS");
    }
}
