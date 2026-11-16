package com.shawnxe7en.myowntv;

public class UpdateRulesTest {
    static void check(boolean ok, String msg) {
        if (!ok) { System.out.println("FAIL " + msg); System.exit(1); }
    }
    static final String HEX = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";
    public static void main(String[] a) {
        check(UpdateRules.compare("1.10.0", "1.9") > 0, "1.10.0 is newer than 1.9");
        check(UpdateRules.compare("1.1.0", "1.0.1") > 0, "1.1.0 is newer than 1.0.1");
        check(UpdateRules.compare("1.1", "1.1.0") == 0, "1.1 equals 1.1.0");
        check(UpdateRules.compare("1.1.0", "1.1.0") == 0, "equal versions");
        check(UpdateRules.compare("v1.2.0", "1.1.9") > 0, "a leading v is ignored");
        check(UpdateRules.compare("1.0.1", "1.1.0") < 0, "1.0.1 is older than 1.1.0");

        check(UpdateRules.isRelease("1.1.0") && UpdateRules.isRelease("v1.2") && UpdateRules.isRelease("V2"), "plain version tags");
        check(!UpdateRules.isRelease("1.1.0-beta") && !UpdateRules.isRelease("latest") && !UpdateRules.isRelease("")
                && !UpdateRules.isRelease("1..2") && !UpdateRules.isRelease(null), "other tags are not releases");

        check(UpdateRules.apkName("1.1.0").equals("MyOwnTV-1.1.0.apk"), "apk name");
        check(UpdateRules.shaName("1.1.0").equals("MyOwnTV-1.1.0.apk.sha256"), "sha file name");

        check(UpdateRules.digestHex("sha256:" + HEX).equals(HEX.toLowerCase()), "digest hex is lowercased");
        check(UpdateRules.digestHex("sha1:abc").isEmpty() && UpdateRules.digestHex("").isEmpty() && UpdateRules.digestHex(null).isEmpty(), "no digest");
        check(UpdateRules.digestHex("sha256:" + HEX.substring(1)).isEmpty(), "a short digest is refused");
        check(UpdateRules.digestHex("sha256:" + HEX.replace('0', 'g')).isEmpty(), "a non-hex digest is refused");

        check(UpdateRules.shaFileHex(HEX + "  MyOwnTV-1.1.0.apk\n", "MyOwnTV-1.1.0.apk").equals(HEX.toLowerCase()), "sha file line");
        check(UpdateRules.shaFileHex(HEX + "  other.apk\n", "MyOwnTV-1.1.0.apk").isEmpty(), "sha file for another file");
        check(UpdateRules.shaFileHex("", "MyOwnTV-1.1.0.apk").isEmpty() && UpdateRules.shaFileHex(null, "x").isEmpty(), "empty sha file");

        long day = 24 * 3600_000L, now = 100 * day;
        check(UpdateRules.shouldShow("1.2.0", "1.1.0", "", 0, now), "newer and never hidden: shown");
        check(!UpdateRules.shouldShow("1.2.0", "1.1.0", "1.2.0", now - day, now), "same version hidden 1 day ago: not shown");
        check(UpdateRules.shouldShow("1.2.0", "1.1.0", "1.2.0", now - 4 * day, now), "hidden 4 days ago: shown again");
        check(UpdateRules.shouldShow("1.3.0", "1.1.0", "1.2.0", now - day, now), "a newer version than the hidden one: shown at once");
        check(!UpdateRules.shouldShow("1.1.0", "1.1.0", "", 0, now), "ready equals installed: not shown");
        check(!UpdateRules.shouldShow("1.0.1", "1.1.0", "", 0, now), "ready older than installed: not shown");
        check(UpdateRules.retryHttp(500) && UpdateRules.retryHttp(503) && UpdateRules.retryHttp(599), "a GitHub server error is tried again soon");
        check(!UpdateRules.retryHttp(403) && !UpdateRules.retryHttp(429) && !UpdateRules.retryHttp(404) && !UpdateRules.retryHttp(200),
                "a rate limit or a missing file waits for the weekly check");
        System.out.println("PASS UpdateRulesTest");
    }
}
