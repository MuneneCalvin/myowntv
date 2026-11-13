package com.shawnxe7en.myowntv;

import java.util.Arrays;

public class SceneChoiceTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    public static void main(String[] a) {
        String[] all = SceneChoice.ALL;
        check(Arrays.equals(all, new String[] {"night", "golden", "water", "fog", "dawn", "amber-dusk", "amber-ridges", "sea-sunset",
                "golden-peaks", "aurora-coast", "aurora-lake", "mountain-mist", "dunes"}), "the thirteen scenes, in order");
        check(Arrays.equals(SceneChoice.enabled(all, ""), all), "nothing off: all");
        check(Arrays.equals(SceneChoice.enabled(all, "golden,fog"), new String[] {"night", "water", "dawn", "amber-dusk", "amber-ridges", "sea-sunset", "golden-peaks", "aurora-coast", "aurora-lake", "mountain-mist", "dunes"}), "off ones are left out, order kept");
        check(Arrays.equals(SceneChoice.enabled(all, String.join(",", all)), all), "all off: all (never an empty list)");
        check(Arrays.equals(SceneChoice.enabled(all, "nonsense, golden"), new String[] {"night", "water", "fog", "dawn", "amber-dusk", "amber-ridges", "sea-sunset", "golden-peaks", "aurora-coast", "aurora-lake", "mountain-mist", "dunes"}), "unknown names and spaces are ignored");
        check(SceneChoice.toggle("", "night").equals("night"), "turning one off");
        check(SceneChoice.toggle("night", "night").equals(""), "turning it back on");
        String twelveOff = String.join(",", Arrays.copyOfRange(all, 0, all.length - 1));
        check(SceneChoice.toggle(twelveOff, all[all.length - 1]).equals(twelveOff), "the last one on cannot be turned off");
        check(SceneChoice.toggle("fog", "night").equals("fog,night") || SceneChoice.toggle("fog", "night").equals("night,fog"), "a second one off");
        check(SceneChoice.isOff("golden,fog", "fog") && !SceneChoice.isOff("golden,fog", "night"), "isOff");
        check(Arrays.equals(SceneChoice.MINUTES, new int[] {5, 10, 30, 60, 90, 150, 180}), "the minute choices");
        check(SceneChoice.DEFAULT_MINUTES == 30, "default 30 minutes");
        check(SceneChoice.stepMinutes(30, 1) == 60 && SceneChoice.stepMinutes(30, -1) == 10, "next and previous");
        check(SceneChoice.stepMinutes(180, 1) == 5 && SceneChoice.stepMinutes(5, -1) == 180, "wraps around");
        check(SceneChoice.stepMinutes(7, 1) == 60, "an unknown value counts as the default (30), then one step");
        check(SceneChoice.minutesLabel(5).equals("5 min") && SceneChoice.minutesLabel(60).equals("1 h")
                && SceneChoice.minutesLabel(90).equals("1 h 30 min") && SceneChoice.minutesLabel(150).equals("2 h 30 min")
                && SceneChoice.minutesLabel(180).equals("3 h"), "labels");
        System.out.println("PASS");
    }
}
