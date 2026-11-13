package com.shawnxe7en.myowntv;

import java.util.Arrays;

public class SceneChoiceTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        String[] all = SceneChoice.ALL;
        check(Arrays.equals(all, new String[] {"night", "golden", "water", "fog", "dawn"}), "the five scenes, in order");
        check(Arrays.equals(SceneChoice.enabled(all, ""), all), "nothing off: all");
        check(Arrays.equals(SceneChoice.enabled(all, "golden,fog"), new String[] {"night", "water", "dawn"}), "off ones are left out, order kept");
        check(Arrays.equals(SceneChoice.enabled(all, "night,golden,water,fog,dawn"), all), "all off: all (never an empty list)");
        check(Arrays.equals(SceneChoice.enabled(all, "nonsense, golden"), new String[] {"night", "water", "fog", "dawn"}), "unknown names and spaces are ignored");
        check(SceneChoice.toggle("", "night").equals("night"), "turning one off");
        check(SceneChoice.toggle("night", "night").equals(""), "turning it back on");
        check(SceneChoice.toggle("night,golden,water,fog", "dawn").equals("night,golden,water,fog"), "the last one on cannot be turned off");
        check(SceneChoice.toggle("fog", "night").equals("fog,night") || SceneChoice.toggle("fog", "night").equals("night,fog"), "a second one off");
        check(SceneChoice.isOff("golden,fog", "fog") && !SceneChoice.isOff("golden,fog", "night"), "isOff");
        System.out.println("PASS");
    }
}
