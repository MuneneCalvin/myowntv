package com.shawnxe7en.myowntv;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.PersistableBundle;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Updates from GitHub's releases (Android only). Once a week, and from Home Settings > Updates, the latest release is
 * looked up; a newer one's MyOwnTV.apk is downloaded and checked (GitHub's size and SHA-256, a newer version, the
 * project's key), then handed on OK to Android's installer, which asks before installing. Only the APK from the
 * releases (--generic, the project's key) takes them: a build made on a computer has its own key and config, so it's
 * only told that a new version is out, and only when asked (nothing in the background could come of it).
 *
 * Checks and installs run in the main process (ModeJob, HomeReceiver), one at a time under this class's lock, and only
 * they write update.json; Settings, in :home, reads it, and the home screen keeps what it told in update-told.
 */
final class Updates {
    private Updates() {}

    private static final String LATEST = "https://api.github.com/repos/MuneneCalvin/myowntv/releases/latest";
    // The only place the APK may come from: this repository's releases (github.com redirects to its download host).
    private static final String RELEASES = "https://github.com/MuneneCalvin/myowntv/releases/download/";
    // The release key's certificate, as `apksigner verify --print-certs` shows it: only APKs signed with it come in.
    private static final String RELEASE_CERT = "765d87ae6ae3d45953a89271a902ceeb23f6d79698b2dda14c52abb47b91ac2a";
    private static final long MAX_SIZE = 30L << 20;   // the APK is about 6 MB
    private static final long WEEK = 7 * 24 * 3600_000L;
    static final int JOB = 2900;
    static final String ACTION = "com.shawnxe7en.myowntv.UPDATE_STATUS";

    // What the last check found (update.json's "state"; "at" is when it was written, every few seconds while a
    // download runs).
    static final String CHECKING = "checking", CURRENT = "current", BUILD = "build", DOWNLOADING = "downloading",
            READY = "ready", FAILED = "failed";
    // Why it failed ("error"): no network, or anything else (the log has the details).
    private static final String OFFLINE = "offline", OTHER = "other";

    private static Boolean releaseKey;   // this app is signed with the release key (read once per process)

    /** The APK from the releases: built with --generic and signed with the project's key, so it can take the next. */
    static boolean takesReleases(Context c) {
        if (!Config.GENERIC) return false;
        if (releaseKey == null) {
            boolean is;
            try {
                is = RELEASE_CERT.equals(cert(c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES)));
            } catch (PackageManager.NameNotFoundException e) {
                is = false;
            }
            releaseKey = is;
        }
        return releaseKey;
    }

    /** The week's check, as a job that waits for the network and survives restarts (scheduled once). */
    static void schedule(Context c) {
        if (!takesReleases(c)) return;
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(JOB) != null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("action", ModeJob.UPDATE);
        // A check that failed (no network, or GitHub's hourly limit on an address many homes share) tries again in
        // 30 minutes, then 1 hour, 2…
        scheduler.schedule(new JobInfo.Builder(JOB, new ComponentName(c, ModeJob.class)).setExtras(extras)
                .setPeriodic(WEEK).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setBackoffCriteria(30 * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }

    // MARK: - State

    private static File stateFile(Context c) {
        return new File(c.getFilesDir(), "update.json");
    }

    /** Where downloads wait: not the cache, which Android empties first when storage runs low. */
    private static File dir(Context c) {
        return new File(c.getNoBackupFilesDir(), "update");
    }

    static JSONObject state(Context c) {
        try {
            return new JSONObject(text(stateFile(c)));
        } catch (org.json.JSONException e) {
            return new JSONObject();
        }
    }

    private static void save(Context c, JSONObject o) {
        File f = stateFile(c), tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    private static void set(Context c, String state, String version, String error) {
        set(c, state, version, error, null, null);
    }

    /** The new state, plus one more field (`key`, when not null) in the same write. */
    private static void set(Context c, String state, String version, String error, String key, String value) {
        JSONObject o = state(c);
        try {
            o.put("state", state).put("version", version).put("error", error).put("at", System.currentTimeMillis());
            if (key != null) o.put(key, value);
        } catch (org.json.JSONException ignored) {
        }
        save(c, o);
    }

    /** A check or download under way (one stopped with its process counts as over 2 minutes after its last word). */
    static boolean busy(JSONObject o) {
        String state = o.optString("state");
        return (CHECKING.equals(state) || DOWNLOADING.equals(state)) && System.currentTimeMillis() - o.optLong("at") < 2 * 60_000L;
    }

    /** A download ready to install, still newer than this app (MY_PACKAGE_REPLACED may not have come after one). */
    static boolean ready(JSONObject o, String installed) {
        return READY.equals(o.optString("state")) && UpdateRules.compare(o.optString("version"), installed) > 0;
    }

    /** Home Settings' Updates row: what the last check found, or what to press. */
    static String summary(JSONObject o, String installed) {
        String v = o.optString("version");
        if (READY.equals(o.optString("state")) && !ready(o, installed)) return Ui.tr("Up to date");
        switch (o.optString("state")) {
            case CHECKING: return busy(o) ? Ui.tr("Checking…") : Ui.tr("Check now");
            case DOWNLOADING: return busy(o) ? Ui.tr("Downloading…") : Ui.tr("Check now");
            // No check for over a week (no network for the week's, or a build made on a computer): asks again.
            case CURRENT: return System.currentTimeMillis() - o.optLong("at") < WEEK + 24 * 3600_000L ? Ui.tr("Up to date") : Ui.tr("Check now");
            case BUILD: return Ui.trf("{version} is out: build it again", "version", v);
            case READY: return Ui.trf("Install {version}", "version", v);
            case FAILED: return OFFLINE.equals(o.optString("error")) ? Ui.tr("No connection") : Ui.tr("Couldn't update");
            default: return Ui.tr("Check now");
        }
    }

    /** "Later" on the Home notice: that version stays hidden for 3 days (UpdateRules.shouldShow). */
    static void later(Context c, String version) {
        try (OutputStream out = new FileOutputStream(new File(c.getFilesDir(), "update-later"))) {
            out.write((version + "\n" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    /** The version the person said Later to, or "". */
    static String laterVersion(Context c) {
        return text(new File(c.getFilesDir(), "update-later")).split("\n")[0];
    }

    /** When they said it (wall clock ms), or 0. */
    static long laterAt(Context c) {
        String[] f = text(new File(c.getFilesDir(), "update-later")).split("\n");
        try {
            return f.length > 1 ? Long.parseLong(f[1].trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // MARK: - Check and download

    /**
     * Looks for a newer release and, on the APK from the releases, downloads and checks it. ModeJob's thread, main
     * process. True when it failed in a way worth trying again soon (the network, GitHub).
     */
    static synchronized boolean check(Context c) {
        JSONObject before = state(c);
        String version = "";
        try {
            set(c, CHECKING, "", "");
            JSONObject release = new JSONObject(get(LATEST));
            String tag = release.optString("tag_name");
            if (!UpdateRules.isRelease(tag)) {   // "latest", "1.2.0-beta": not something to install
                AppLog.w("update: the latest release's tag \"" + tag + "\" is not a version");
                set(c, CURRENT, "", "");
                return false;
            }
            version = tag.replaceFirst("^[vV]", "");
            String installed = installedVersion(c);
            AppLog.w("update: latest release " + version + ", installed " + installed);
            if (UpdateRules.compare(version, installed) <= 0) {
                set(c, CURRENT, version, "");
                return false;
            }
            if (!takesReleases(c)) {   // a build made on a computer: its own key and config
                set(c, BUILD, version, "");
                return false;
            }
            String apkName = UpdateRules.apkName(version), shaName = UpdateRules.shaName(version);
            JSONObject asset = null, shaAsset = null;
            JSONArray assets = release.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                String n = assets.getJSONObject(i).optString("name");
                if (apkName.equals(n)) asset = assets.getJSONObject(i);
                if (shaName.equals(n)) shaAsset = assets.getJSONObject(i);
            }
            if (asset == null) throw new IOException(apkName + " isn't in release " + version);
            String url = asset.optString("browser_download_url");
            long size = asset.optLong("size");
            if (!url.startsWith(RELEASES) || size <= 0 || size > MAX_SIZE) {
                throw new IOException("unexpected " + apkName + ": " + url + ", " + size + " bytes");
            }
            // The SHA-256 to hold the download to: GitHub's digest, else the .sha256 file beside it; neither: no install.
            String expected = UpdateRules.digestHex(asset.optString("digest"));
            if (shaAsset != null) {
                String shaUrl = shaAsset.optString("browser_download_url");
                if (!shaUrl.startsWith(RELEASES)) throw new IOException("unexpected " + shaName + ": " + shaUrl);
                String fromFile = UpdateRules.shaFileHex(small(shaUrl), apkName);
                if (!expected.isEmpty() && !fromFile.isEmpty() && !expected.equals(fromFile)) {
                    throw new IOException("GitHub's digest and " + shaName + " disagree");
                }
                if (expected.isEmpty()) expected = fromFile;
            }
            if (expected.isEmpty()) throw new IOException("no SHA-256 for " + apkName + " (neither a digest nor " + shaName + ")");
            // One name per upload: an asset replaced under the same tag is fetched again.
            String name = "MyOwnTV-" + version + "-" + expected.substring(0, 12) + ".apk";
            if (name.equals(before.optString("refused"))) {   // this upload was already found not to fit
                set(c, FAILED, version, OTHER);
                return false;
            }
            File apk = new File(dir(c), name);
            if (!apk.exists()) {
                set(c, DOWNLOADING, version, "");
                download(c, version, url, expected, size, apk);
                AppLog.w("update: downloaded " + version + " (" + apk.length() / 1024 + " KB), size and SHA-256 as GitHub's");
            }
            String wrong = misfit(c, apk);
            if (wrong != null) {
                AppLog.w("update: " + version + "'s " + apkName + " " + wrong);
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                set(c, FAILED, version, OTHER, "refused", name);
                return false;
            }
            deleteDownloads(c, apk);   // older ones, never installed
            set(c, READY, version, "", "file", name);
            set(c, READY, version, "", "notes", firstLine(release.optString("body")));
            AppLog.w("update: " + version + " ready");
            return false;
        } catch (Exception e) {
            AppLog.w("update: " + e);
            if (ready(before, installedVersion(c)) && new File(dir(c), before.optString("file")).isFile()) {
                save(c, before);   // the one downloaded before can still be installed
            } else {
                set(c, FAILED, version, offline(e) ? OFFLINE : OTHER);
            }
            return retrySoon(e);
        }
    }

    /** GitHub answered with something other than 200. */
    private static final class HttpStatus extends IOException {
        final int code;

        HttpStatus(int code) {
            super("GitHub returned HTTP " + code);
            this.code = code;
        }
    }

    /** Worth the system trying again soon: no network, or GitHub itself failing. Anything else waits for the week's check. */
    private static boolean retrySoon(Exception e) {
        return offline(e) || e instanceof HttpStatus && UpdateRules.retryHttp(((HttpStatus) e).code);
    }

    private static boolean offline(Exception e) {
        return e instanceof java.net.UnknownHostException || e instanceof java.net.SocketException
                || e instanceof java.net.SocketTimeoutException || e instanceof javax.net.ssl.SSLException;
    }

    /** Every download but `keep` (null: all of them). */
    private static void deleteDownloads(Context c, File keep) {
        File[] files = dir(c).listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            if (!files[i].equals(keep)) files[i].delete();
        }
    }

    /** Once this app is replaced (MY_PACKAGE_REPLACED): the downloads go, and an update we installed is up to date. */
    static synchronized void cleanUp(Context c) {
        deleteDownloads(c, null);
        JSONObject o = state(c);
        String installed = installedVersion(c);
        if (READY.equals(o.optString("state")) && UpdateRules.compare(o.optString("version"), installed) <= 0) set(c, CURRENT, installed, "");
        else save(c, new JSONObject());   // installed from a computer: unknown until the next check
    }

    static String installedVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The APK into `to`, only if its size and its SHA-256 (`expected`, lowercase hex, never empty) are as the release says. */
    private static void download(Context c, String version, String url, String expected, long size, File to) throws Exception {
        //noinspection ResultOfMethodCallIgnored
        to.getParentFile().mkdirs();
        File part = new File(to.getPath() + ".part");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        long total = 0, said = System.currentTimeMillis();
        HttpURLConnection h = open(url);
        try {
            if (h.getResponseCode() != 200) throw new HttpStatus(h.getResponseCode());
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    total += n;
                    if (total > size) throw new IOException("the download is larger than GitHub says");
                    sha.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    if (System.currentTimeMillis() - said > 5000) {   // still under way (busy), on a slow network
                        said = System.currentTimeMillis();
                        set(c, DOWNLOADING, version, "");
                    }
                }
            }
            if (total != size || expected.isEmpty() || !expected.equals(hex(sha.digest()))) {
                throw new IOException("the download doesn't match the release's size or SHA-256");
            }
            if (!part.renameTo(to)) throw new IOException("couldn't save the update");
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        } finally {
            h.disconnect();
        }
    }

    /**
     * Why the downloaded APK can't update this app, or null if it can: it must be this app, newer (Android refuses a
     * lower versionCode, and an equal versionName would be offered again and again) and signed with the release key.
     * A signature that can't be read as exactly one certificate is refused too: the pin is the one check that does not
     * trust GitHub.
     */
    @SuppressWarnings("deprecation")   // versionCode: getLongVersionCode is Android 9+
    private static String misfit(Context c, File apk) throws PackageManager.NameNotFoundException {
        PackageManager pm = c.getPackageManager();
        PackageInfo theirs = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        PackageInfo ours = pm.getPackageInfo(c.getPackageName(), 0);
        if (theirs == null) return "can't be read";
        if (!c.getPackageName().equals(theirs.packageName)) return "is another app, " + theirs.packageName;
        String name = theirs.versionName == null ? "" : theirs.versionName;
        if (theirs.versionCode < ours.versionCode || UpdateRules.compare(name, ours.versionName) <= 0) {
            return "isn't newer than this app: " + name + " (" + theirs.versionCode + ")";
        }
        String cert = cert(theirs);
        if (cert == null) return "has no single readable signature";
        if (!RELEASE_CERT.equals(cert)) return "isn't signed with the project's key";
        return null;
    }

    /** The SHA-256 of a package's one signing certificate; null when there's none, or more than one. */
    @SuppressWarnings("deprecation")   // GET_SIGNATURES: GET_SIGNING_CERTIFICATES is Android 9+
    private static String cert(PackageInfo p) {
        Signature[] s = p == null ? null : p.signatures;
        if (s == null || s.length != 1) return null;
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s[0].toByteArray()));
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    // MARK: - Install

    /**
     * Hands the downloaded APK to Android's installer, which asks first (and, the first time, to allow it). ModeJob's
     * thread, main process. A download that's gone comes again, and the next OK installs it: Android's question always
     * opens right after OK, never later over something else.
     */
    static synchronized void install(Context c) {
        JSONObject o = state(c);
        String version = o.optString("version"), name = o.optString("file");
        if (!ready(o, installedVersion(c))) return;
        File apk = new File(dir(c), name);
        if (name.isEmpty() || !apk.isFile()) {
            check(c);
            return;
        }
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        try {
            // One at a time: a session an earlier OK left waiting (its question closed unanswered) goes.
            for (PackageInstaller.SessionInfo s : installer.getMySessions()) installer.abandonSession(s.getSessionId());
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(c.getPackageName());
            params.setSize(apk.length());
            int id = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("MyOwnTV.apk", 0, apk.length())) {
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    session.fsync(out);
                }
                Intent done = new Intent(c, HomeReceiver.class).setAction(ACTION).putExtra("version", version);
                // Mutable: the installer adds its status.
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                session.commit(PendingIntent.getBroadcast(c, id, done, flags).getIntentSender());
            }
            AppLog.w("update: installing " + version);
        } catch (Exception e) {
            AppLog.w("update install: " + e);
            set(c, FAILED, version, OTHER);
        }
    }

    /** The installer's answer (HomeReceiver, off its main thread): it asks the person first, or says why it didn't install. */
    static void status(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String version = intent.getStringExtra("version");
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            try {
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                AppLog.w("update: Android's installer didn't open: " + e);
                failed(c, version);
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            AppLog.w("update: not installed (" + status + "): " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            // Cancelled on the installer's screen: still ready, for another try.
            if (status != PackageInstaller.STATUS_FAILURE_ABORTED) failed(c, version);
        }
    }

    private static synchronized void failed(Context c, String version) {
        set(c, FAILED, version == null ? "" : version, OTHER);
    }

    // MARK: - HTTP

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        h.setRequestProperty("User-Agent", "MyOwnTV");
        return h;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection h = open(url);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (h.getResponseCode() != 200) throw new HttpStatus(h.getResponseCode());
            try (InputStream in = h.getInputStream()) {
                return read(in);
            }
        } finally {
            h.disconnect();
        }
    }

    /** A small file from the releases (the .sha256), at most 1 KB: its text. */
    private static String small(String url) throws IOException {
        HttpURLConnection h = open(url);
        try {
            if (h.getResponseCode() != 200) throw new HttpStatus(h.getResponseCode());
            try (InputStream in = h.getInputStream()) {
                byte[] buffer = new byte[1024];
                int n = in.read(buffer);
                return n <= 0 ? "" : new String(buffer, 0, n, StandardCharsets.UTF_8);
            }
        } finally {
            h.disconnect();
        }
    }

    /** The release notes' first line, at most 120 characters ("" when there are none). */
    private static String firstLine(String body) {
        String line = body == null ? "" : body.trim().split("\\r?\\n")[0].trim();
        return line.length() > 120 ? line.substring(0, 119) + "…" : line;
    }

    /** A small file's text; "" when it isn't there. */
    private static String text(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return read(in);
        } catch (IOException e) {
            return "";
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
