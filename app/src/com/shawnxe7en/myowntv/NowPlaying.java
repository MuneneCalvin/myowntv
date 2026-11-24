package com.shawnxe7en.myowntv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Now Playing: what the TV's apps are playing, from their media sessions. Android hands the sessions only to an enabled
 * notification listener, so this empty one exists for that and nothing else: it never reads a notification and lets go
 * of the system at once (MyOwnTV grants it through the TV's own ADB only while the setting is on; see HomeMode). The
 * way of it follows Free My TV's NowPlaying (MIT).
 */
public class NowPlaying extends NotificationListenerService {
    @Override public void onListenerConnected() {
        requestUnbind();
    }

    /** Commands for Watch.command. */
    static final int PREVIOUS = 0, TOGGLE = 1, NEXT = 2;
    private static final int ART_MAX = 192;

    /** What the session being shown says, for drawing: the art is null until there is some (the app's icon stands in). */
    static final class Track {
        String title, artist, app, pkg;
        boolean playing;
        long actions, position, duration, updatedAt;
        float speed;
        Bitmap art;
    }

    private static final java.util.concurrent.ExecutorService ART_POOL = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "myowntv-nowplaying-art");
        t.setDaemon(true);
        return t;
    });

    static ComponentName component(Context c) {
        return new ComponentName(c, NowPlaying.class);
    }

    /** Whether Android has this listener switched on (the grant took effect). */
    static boolean allowed(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(component(c).flattenToString());
    }

    /** The last line seen playing, while it's under a day old: what to show after a restart. */
    static String last(Context c) {
        NowPlayingText.Saved s = NowPlayingText.unpack(Prefs.lastPlayed(), System.currentTimeMillis());
        return s == null ? null : s.line;
    }

    static String lastPackage(Context c) {
        NowPlayingText.Saved s = NowPlayingText.unpack(Prefs.lastPlayed(), System.currentTimeMillis());
        return s == null || s.pkg.isEmpty() ? null : s.pkg;
    }

    /** Follows the sessions while a screen that shows them is up (start and stop), and says when the line changes. */
    static final class Watch {
        private final Context ctx;
        private final Runnable onChange;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final List<MediaController> watched = new ArrayList<>();
        private MediaSessionManager manager;
        private String line, pkg;
        private boolean playing;
        private Track track;
        private String trackSignature = "", artKey = "";
        private Bitmap artBitmap;
        private int tries;
        private final java.util.Map<String, String> labels = new java.util.HashMap<>();
        /** The grant takes a few seconds after the switch: look again until it has (a few times only). */
        private final Runnable retry = () -> start();

        private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> follow(list);
        private final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
            @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        };

        Watch(Context ctx, Runnable onChange) {
            this.ctx = ctx.getApplicationContext();
            this.onChange = onChange;
        }

        String line() { return line; }
        String pkg() { return pkg; }
        boolean playing() { return line != null && playing; }
        /** What to draw for the current session (playing, or paused and resumable), or null. */
        Track track() { return track; }

        /** Previous, play or pause, or next, to the session being shown. False when there's none to send it to. */
        boolean command(int cmd) {
            if (track == null) return false;
            for (MediaController c : watched) {
                if (!c.getPackageName().equals(track.pkg)) continue;
                try {
                    MediaController.TransportControls t = c.getTransportControls();
                    if (cmd == PREVIOUS) t.skipToPrevious();
                    else if (cmd == NEXT) t.skipToNext();
                    else if (track.playing) t.pause();
                    else t.play();
                    return true;
                } catch (RuntimeException e) {
                    AppLog.w("now playing command: " + e.getMessage());
                    return false;
                }
            }
            return false;
        }

        void start() {
            if (manager != null || !Prefs.nowPlaying()) return;
            if (!allowed(ctx)) {
                if (tries++ < 6) main.postDelayed(retry, 3_000);
                return;
            }
            MediaSessionManager m = ctx.getSystemService(MediaSessionManager.class);
            if (m == null) return;
            try {
                m.addOnActiveSessionsChangedListener(sessions, component(ctx), main);
                manager = m;
                follow(m.getActiveSessions(component(ctx)));
            } catch (SecurityException e) {   // the grant isn't in effect (yet)
                AppLog.w("now playing: " + e.getMessage());
                stop();
            }
        }

        void stop() {
            main.removeCallbacks(retry);
            tries = 0;
            if (manager == null) return;
            manager.removeOnActiveSessionsChangedListener(sessions);
            for (MediaController c : watched) c.unregisterCallback(callback);
            watched.clear();
            track = null;
            trackSignature = "";
            manager = null;
        }

        private void follow(List<MediaController> list) {
            for (MediaController c : watched) c.unregisterCallback(callback);
            watched.clear();
            if (list != null) {
                for (MediaController c : list) {
                    c.registerCallback(callback, main);
                    watched.add(c);
                }
            }
            refresh();
        }

        private void refresh() {
            List<NowPlayingText.Session> all = new ArrayList<>();
            for (MediaController c : watched) {
                MediaMetadata m = c.getMetadata();
                PlaybackState st = c.getPlaybackState();
                String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null && m != null) title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null && m != null) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                all.add(new NowPlayingText.Session(st == null ? 0 : st.getState(), title, artist, label(c.getPackageName()),
                        c.getPackageName()));
            }
            NowPlayingText.Session top = NowPlayingText.pick(all);
            String now = top == null ? null : NowPlayingText.line(top);
            String nowPkg = now == null ? null : top.pkg;
            boolean changed = !java.util.Objects.equals(now, line) || !java.util.Objects.equals(nowPkg, pkg);
            line = now;
            pkg = nowPkg;
            playing = now != null;
            if (now != null && changed) Prefs.setLastPlayed(NowPlayingText.pack(now, nowPkg, System.currentTimeMillis()));
            if (buildTrack(all)) changed = true;
            if (changed) onChange.run();
        }

        /** Builds the track for the current session; true when anything it shows changed. */
        private boolean buildTrack(List<NowPlayingText.Session> all) {
            NowPlayingText.Session cur = NowPlayingText.pickCurrent(all);
            Track t = null;
            if (cur != null && NowPlayingText.line(cur) != null) {
                MediaController c = watched.get(all.indexOf(cur));
                MediaMetadata m = c.getMetadata();
                PlaybackState st = c.getPlaybackState();
                t = new Track();
                t.title = clean(cur.title);
                t.artist = clean(cur.artist);
                t.app = cur.app;
                t.pkg = cur.pkg;
                t.playing = NowPlayingControls.isPlaying(cur.state);
                t.actions = st == null ? 0 : st.getActions();
                t.position = st == null ? 0 : st.getPosition();
                t.updatedAt = st == null ? 0 : st.getLastPositionUpdateTime();
                t.speed = st == null ? 1f : st.getPlaybackSpeed();
                t.duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                String key = t.pkg + "|" + t.title + "|" + t.artist;
                if (!key.equals(artKey)) {
                    artKey = key;
                    artBitmap = m == null ? null : bitmapOf(m);
                    if (artBitmap == null && m != null) loadArtFromUri(m, key);
                }
                t.art = artBitmap;
            }
            String sig = t == null ? "" : t.pkg + "|" + t.title + "|" + t.artist + "|" + t.playing + "|" + t.actions + "|"
                    + t.position + "|" + t.duration + "|" + (t.art != null);
            boolean changed = !sig.equals(trackSignature);
            trackSignature = sig;
            track = t;
            return changed;
        }

        private String clean(String s) {
            return s == null ? "" : s.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        }

        /** The art the session carries as a bitmap, scaled down once. */
        private Bitmap bitmapOf(MediaMetadata m) {
            for (String k : new String[] {MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART,
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                Bitmap b = m.getBitmap(k);
                if (b != null) return scaled(b);
            }
            return null;
        }

        /** Art given as an address (YouTube Music does this) is fetched off the main thread; the screen redraws when it's here. */
        private void loadArtFromUri(MediaMetadata m, String key) {
            String uri = null;
            for (String k : new String[] {MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                uri = m.getString(k);
                if (uri != null && !uri.isEmpty()) break;
            }
            if (uri == null || uri.isEmpty()) return;
            final String address = uri;
            ART_POOL.execute(() -> {
                Bitmap b = null;
                try {
                    byte[] bytes;
                    if (address.startsWith("http://") || address.startsWith("https://")) {
                        bytes = NetPicture.get(address);
                    } else {
                        try (InputStream in = ctx.getContentResolver().openInputStream(android.net.Uri.parse(address))) {
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            for (int n; in != null && (n = in.read(buf)) > 0 && out.size() < 4_000_000; ) out.write(buf, 0, n);
                            bytes = out.size() == 0 ? null : out.toByteArray();
                        }
                    }
                    if (bytes != null) {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                        int sample = 1;
                        while (o.outWidth / (sample * 2) >= ART_MAX * 2) sample *= 2;
                        o = new BitmapFactory.Options();
                        o.inSampleSize = sample;
                        Bitmap raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                        if (raw != null) b = scaled(raw);
                    }
                } catch (Exception | OutOfMemoryError e) {
                    AppLog.w("now playing art: " + e);
                }
                final Bitmap art = b;
                if (art != null) main.post(() -> {
                    if (key.equals(artKey) && manager != null) {   // still the same track, still watching
                        artBitmap = art;
                        if (track != null) track.art = art;
                        trackSignature = "";
                        refresh();
                    }
                });
            });
        }

        private Bitmap scaled(Bitmap b) {
            int big = Math.max(b.getWidth(), b.getHeight());
            if (big <= ART_MAX) return b;
            float f = ART_MAX / (float) big;
            return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * f)), Math.max(1, Math.round(b.getHeight() * f)), true);
        }

        private String label(String pkgName) {
            String known = labels.get(pkgName);
            if (known != null) return known;
            try {
                PackageManager pm = ctx.getPackageManager();
                known = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)));
            } catch (PackageManager.NameNotFoundException e) {
                known = pkgName;
            }
            labels.put(pkgName, known);
            return known;
        }
    }
}
