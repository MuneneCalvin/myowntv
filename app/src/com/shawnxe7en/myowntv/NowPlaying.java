package com.shawnxe7en.myowntv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

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

    /** What to show: the live line, else "Last played: …" from before, else null (also null with the setting off). */
    static String display(Context c, Watch w) {
        if (!Prefs.nowPlaying()) return null;
        if (w != null && w.line() != null) return w.line();
        String last = last(c);
        return last == null ? null : Ui.tr("Last played") + ": " + last;
    }

    /** The level bars: three, bouncing while `playing`, resting low otherwise. Drawn left of x, `height` tall on `base`. */
    static float bars(android.graphics.Canvas c, float x, float base, float height, android.graphics.Paint p,
                      boolean playing, long up) {
        float w = height * 0.24f, gap = w * 0.7f, total = 3 * w + 2 * gap;
        for (int i = 0; i < 3; i++) {
            float level = playing ? 0.3f + 0.7f * (float) Math.abs(Math.sin(up / (210.0 + i * 95) + i * 1.7)) : 0.3f;
            float left = x + i * (w + gap), top = base - height * level;
            c.drawRoundRect(left, top, left + w, base, w / 2, w / 2, p);
        }
        return total;
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
            if (changed) onChange.run();
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
