package com.shawnxe7en.myowntv;

import java.util.Locale;

/** The updater's rules that need no Android: which versions are newer, the release's file names and digests, when to tell. */
final class UpdateRules {
    private UpdateRules() {}

    /** "Later" hides the notice for this long (for that version). */
    static final long LATER_MS = 3 * 24 * 3600_000L;

    /** "1.10.0" vs "1.9": numbers compared one by one, missing ones as 0, a leading v ignored, a part that is not a number as 0. */
    static int compare(String a, String b) {
        String[] x = strip(a).split("\\."), y = strip(b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int d = Long.compare(number(i < x.length ? x[i] : ""), number(i < y.length ? y[i] : ""));
            if (d != 0) return d;
        }
        return 0;
    }

    private static String strip(String v) {
        return v == null ? "" : v.replaceFirst("^[vV]", "");
    }

    private static long number(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A plain version tag ("1.1.0", "v1.2"): digits and dots, no suffix like "-beta", no empty part. */
    static boolean isRelease(String tag) {
        return tag != null && tag.matches("[vV]?[0-9]+(\\.[0-9]+)*");
    }

    static String apkName(String version) {
        return "MyOwnTV-" + version + ".apk";
    }

    static String shaName(String version) {
        return apkName(version) + ".sha256";
    }

    /** The lowercase hex of a GitHub asset digest ("sha256:..."); "" when there is none or it is not 64 hex digits. */
    static String digestHex(String digest) {
        if (digest == null || !digest.startsWith("sha256:")) return "";
        return hex64(digest.substring("sha256:".length()));
    }

    /** The hash in a "shasum" line ("<64 hex>  <name>") for exactly this file; "" otherwise. */
    static String shaFileHex(String text, String apkName) {
        if (text == null) return "";
        String[] f = text.trim().split("\\s+");
        if (f.length != 2 || !f[1].equals(apkName)) return "";
        return hex64(f[0]);
    }

    private static String hex64(String s) {
        return s.matches("[0-9a-fA-F]{64}") ? s.toLowerCase(Locale.ROOT) : "";
    }

    /** A failed request worth trying again soon: GitHub's own errors (5xx). A rate limit (403, 429) or a missing file waits for the weekly check. */
    static boolean retryHttp(int code) {
        return code >= 500 && code < 600;
    }

    /** The notice is due: a newer version is ready, and the person has not said Later to this very version in the last 3 days. */
    static boolean shouldShow(String ready, String installed, String laterVersion, long laterAt, long now) {
        if (compare(ready, installed) <= 0) return false;
        return !(ready.equals(laterVersion) && now - laterAt < LATER_MS);
    }
}
